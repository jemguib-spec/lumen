package com.lumen.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * Les films et épisodes en cours que les applis (Netflix, Prime Video, Plex…) publient sur Android TV
 * (« Watch Next »). Lecture seule, sur l'appareil ; nécessite l'autorisation « programmes TV »,
 * demandée une seule fois. Chaque appli décide si elle publie ou non ses « en cours ».
 */
final class WatchNext {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final Uri URI = Uri.parse("content://android.media.tv/watch_next_program");

    static final class Entry {
        String pkg, title, subtitle, poster, intentUri;
        float progress;
        String remaining;
        long engaged;
        boolean portrait;
    }

    private WatchNext() { }

    static boolean granted(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Tous les « en cours », regroupés par appli, du plus récent au plus ancien (8 au plus par appli).
     * Requête sans filtre ni tri : le fournisseur les refuse aux applis tierces.
     */
    static Map<String, List<Entry>> load(Context c) {
        Map<String, List<Entry>> out = new HashMap<>();
        if (Build.VERSION.SDK_INT < 26) return out; // « Watch Next » existe depuis Android 8
        ContentResolver cr = c.getContentResolver();
        List<Entry> all = new ArrayList<>();
        try (Cursor k = cr.query(URI, null, null, null, null)) {
            if (k == null) return out;
            while (k.moveToNext()) {
                if (getInt(k, "browsable", 1) == 0) continue; // retiré par l'utilisateur
                Entry e = new Entry();
                e.pkg = get(k, "package_name");
                e.title = get(k, "title");
                if (e.pkg == null || e.title == null || e.title.trim().isEmpty()) continue;
                e.subtitle = WatchNextFormat.subtitle(get(k, "season_display_number"), get(k, "episode_display_number"), get(k, "episode_title"));
                String poster = get(k, "poster_art_uri");
                String thumb = get(k, "thumbnail_uri");
                e.portrait = getInt(k, "poster_art_aspect_ratio", 0) >= 3; // 2:3 ou 1:1 → affiche verticale
                e.poster = thumb != null && !thumb.isEmpty() ? thumb : poster;
                if (thumb != null && !thumb.isEmpty()) e.portrait = false;
                e.intentUri = get(k, "intent_uri");
                long pos = getLong(k, "last_playback_position_millis"), dur = getLong(k, "duration_millis");
                e.progress = WatchNextFormat.progress(pos, dur);
                e.remaining = WatchNextFormat.remaining(pos, dur);
                e.engaged = getLong(k, "last_engagement_time_utc_millis");
                all.add(e);
            }
        } catch (Exception ignored) {
            return out; // autorisation absente ou fournisseur indisponible : rien à afficher
        }
        Collections.sort(all, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return Long.compare(b.engaged, a.engaged); }
        });
        for (Entry e : all) {
            List<Entry> l = out.get(e.pkg);
            if (l == null) { l = new ArrayList<>(); out.put(e.pkg, l); }
            if (l.size() < 8) l.add(e);
        }
        return out;
    }

    /**
     * Intention de lecture publiée par l'appli. Refusée si elle vise une autre appli que celle qui
     * l'a publiée ; les autorisations d'accès aux fichiers sont retirées.
     */
    static Intent intentFor(Entry e) {
        if (e.intentUri == null) return null;
        try {
            Intent i = Intent.parseUri(e.intentUri, Intent.URI_INTENT_SCHEME);
            i.setSelector(null);
            i.setFlags(i.getFlags() & ~(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));
            String target = i.getComponent() != null ? i.getComponent().getPackageName() : i.getPackage();
            if (target != null && !target.equals(e.pkg)) return null;
            i.setPackage(e.pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return i;
        } catch (Exception ex) { return null; }
    }

    // ------------------------------------------------------------------ affiches

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 << 20) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    /**
     * Affiche d'un « en cours », réduite à 640 px de large. Fichier local d'une appli (content://) ou
     * image HTTPS publiée par l'appli : contrôlée (adresse, taille 3 Mo, type réel), jamais enregistrée.
     */
    static Bitmap poster(Context c, String uri, boolean allowNetwork) {
        if (uri == null || uri.isEmpty()) return null;
        Bitmap cached = CACHE.get(uri);
        if (cached != null) return cached;
        byte[] data = null;
        try {
            if (uri.startsWith("content://") || uri.startsWith("android.resource://")) {
                try (InputStream in = c.getContentResolver().openInputStream(Uri.parse(uri))) { data = readMax(in, 3 << 20); }
            } else if (allowNetwork && WatchNextFormat.posterUrlOk(uri)) {
                HttpURLConnection h = (HttpURLConnection) new URL(uri).openConnection();
                if (!(h instanceof HttpsURLConnection)) { h.disconnect(); return null; }
                h.setInstanceFollowRedirects(false); // pas de redirection : l'adresse vérifiée est celle lue
                h.setUseCaches(false);
                h.setConnectTimeout(8_000);
                h.setReadTimeout(12_000);
                h.setRequestProperty("User-Agent", Net.USER_AGENT);
                try {
                    if (h.getResponseCode() != 200) return null;
                    if (h.getContentLength() > (3 << 20)) return null;
                    try (InputStream in = h.getInputStream()) { data = readMax(in, 3 << 20); }
                } finally { h.disconnect(); }
            }
        } catch (Exception e) { return null; }
        if (data == null || !MediaSniff.sniff(data, Math.min(data.length, 64)).image()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0 || (long) o.outWidth * o.outHeight > 40_000_000L) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 640) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, d);
        if (b != null) CACHE.put(uri, b);
        return b;
    }

    private static byte[] readMax(InputStream in, int max) throws java.io.IOException {
        if (in == null) return null;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (b.size() + n > max) return null;
            b.write(buf, 0, n);
        }
        return b.toByteArray();
    }

    private static String get(Cursor k, String col) {
        int i = k.getColumnIndex(col);
        return i < 0 || k.isNull(i) ? null : k.getString(i);
    }

    private static int getInt(Cursor k, String col, int def) {
        int i = k.getColumnIndex(col);
        return i < 0 || k.isNull(i) ? def : k.getInt(i);
    }

    private static long getLong(Cursor k, String col) {
        int i = k.getColumnIndex(col);
        return i < 0 || k.isNull(i) ? 0 : k.getLong(i);
    }
}

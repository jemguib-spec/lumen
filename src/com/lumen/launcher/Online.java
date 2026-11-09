// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Photos et vidéos libres de droits, téléchargées en arrière-plan puis gardées en cache.
 * Sources : NASA Image and Video Library (domaine public) et Wikimedia Commons
 * (« Featured pictures », licences libres, crédit affiché à l'écran).
 * Aucune autre adresse n'est contactée.
 */
final class Online {
    /** Villes du catalogue vidéo : { nom, titres… }. */
    static final String[][] CITIES = VideoCatalog.CITIES;

    static final String SPACE = "espace", NATURE = "nature", ANIMALS = "animaux", VIDEO = "videos", MINE = "perso", POTD = "potd", COSY = "cosy";

    interface Listener { void onThemeUpdated(String theme); }

    private static final long DAY = 24L * 3600 * 1000;
    private static final int IMG_TARGET = 18, IMG_MAX = 45, VID_TARGET = 6, VID_MAX = 30;
    private long diskCap() { return ((long) prefs.videoDiskGb()) << 30; }


    private static final String[] NATURE_CATS = {"Featured_pictures_of_landscapes",
            "Featured_pictures_of_mountains", "Featured_pictures_of_waterfalls",
            "Featured_pictures_of_forests", "Featured_pictures_of_lakes",
            "Featured_pictures_of_coasts", "Featured_pictures_of_sunsets"};
    private static final String[] ANIMAL_CATS = {"Featured_pictures_of_mammals",
            "Featured_pictures_of_birds", "Featured_pictures_of_animals",
            "Featured_pictures_of_fish", "Featured_pictures_of_reptiles"};
    private static final String[] SPACE_CATS = {"Featured_pictures_of_astronomy",
            "Featured_pictures_of_outer_space"};

    private final Context ctx;
    private final Prefs prefs;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final Set<String> running = new HashSet<>();
    private Listener listener;

    Online(Context c, Prefs p) {
        ctx = c.getApplicationContext();
        prefs = p;
        NetPolicy.setPexelsEnabled(!prefs.pexelsKey().isEmpty());
        // v2.3 : purge des anciennes photos « Espace » (filtrage amélioré)
        android.content.SharedPreferences sp = ctx.getSharedPreferences("lumen_online", Context.MODE_PRIVATE);
        if (!sp.getBoolean("space_v2", false)) {
            File d = new File(ctx.getFilesDir(), "online/" + SPACE);
            File[] fs = d.listFiles();
            if (fs != null) for (File f : fs) f.delete();
            prefs.setLastRefresh(SPACE, 0);
            sp.edit().putBoolean("space_v2", true).apply();
        }
        // v3.6 : photos « Espace » issues des sélections Commons → on efface celles venues de la recherche NASA
        if (!sp.getBoolean("space_v3", false)) {
            JSONArray idx = readIndex(SPACE), keep = new JSONArray();
            for (int i = 0; i < idx.length(); i++) {
                JSONObject o = idx.optJSONObject(i);
                if (o == null) continue;
                if (o.optString("id").startsWith("nasa_")) new File(dir(SPACE), o.optString("file")).delete();
                else keep.put(o);
            }
            writeIndex(SPACE, keep);
            prefs.setLastRefresh(SPACE, 0);
            sp.edit().putBoolean("space_v3", true).apply();
        }
        // v2.5 : nouvelles sources vidéo 4K → on relance un téléchargement
        if (!sp.getBoolean("video_v6", false)) {
            // v3.5 : fonds vidéo issus d'un catalogue fermé → nouveau téléchargement (les anciens sont effacés)
            prefs.setLastRefresh(VIDEO, 0);
            sp.edit().putBoolean("video_v6", true).apply();
        }
    }

    void setListener(Listener l) { listener = l; }

    /** Téléchargements uniquement quand Lumen est à l'écran : jamais pendant Netflix, Plex… */
    private volatile boolean active = true;
    void setActive(boolean a) { active = a; }

    private File dir(String theme) {
        File d = new File(ctx.getFilesDir(), "online/" + theme);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    // ------------------------------------------------------------------ index local

    private synchronized JSONArray readIndex(String theme) {
        try {
            File f = new File(dir(theme), "index.json");
            if (!f.exists()) return new JSONArray();
            if (f.length() > (4 << 20)) return new JSONArray();
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            }
            return new JSONArray(new String(b, "UTF-8"));
        } catch (Exception e) { return new JSONArray(); }
    }

    private synchronized void writeIndex(String theme, JSONArray a) {
        try {
            File tmp = new File(dir(theme), "index.tmp");
            OutputStream o = new FileOutputStream(tmp);
            o.write(a.toString().getBytes("UTF-8"));
            o.close();
            tmp.renameTo(new File(dir(theme), "index.json"));
        } catch (Exception ignored) { }
    }

    /** Fonds déjà téléchargés pour un thème. */
    List<WallItem> items(String theme) {
        List<WallItem> out = new ArrayList<>();
        JSONArray a = readIndex(theme);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            File f = new File(dir(theme), o.optString("file"));
            if (!f.exists()) continue;
            String fn = o.optString("file").toLowerCase();
            boolean isVideo = VIDEO.equals(theme) || fn.endsWith(".mp4") || fn.endsWith(".webm") || fn.endsWith(".mkv")
                    || fn.endsWith(".mov") || fn.endsWith(".m4v");
            int kind = isVideo ? WallItem.VIDEO_FILE : WallItem.IMAGE_FILE;
            out.add(new WallItem(kind, theme + ":" + o.optString("id"), f.getAbsolutePath(),
                    o.optString("title"), o.optString("credit")));
        }
        return out;
    }

    /** Lance un téléchargement si le cache est trop petit ou date de plus d'un jour. */
    void refreshIfNeeded(String theme, boolean force) {
        int have = readIndex(theme).length();
        int target = VIDEO.equals(theme) ? VID_TARGET : POTD.equals(theme) ? 7 : IMG_TARGET;
        boolean stale = System.currentTimeMillis() - prefs.lastRefresh(theme) > DAY;
        if (!force && have >= target && !stale) return;
        synchronized (running) {
            if (running.contains(theme)) return;
            running.add(theme);
        }
        final String t = theme;
        final int want = force || stale ? Math.max(4, target - have + (VIDEO.equals(theme) ? 1 : 6)) : target - have;
        exec.execute(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try { ok = fetch(t, Math.max(1, want)) > 0; }
                catch (Throwable ignored) { }
                finally {
                    synchronized (running) { running.remove(t); }
                    if (ok) prefs.setLastRefresh(t, System.currentTimeMillis());
                }
            }
        });
    }

    boolean isBusy(String theme) { synchronized (running) { return running.contains(theme); } }

    // État visible du téléchargement, par thème (ex. « Vidéo 2 sur 4 · 37 % »)
    private final java.util.Map<String, String> status = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile String lastError = "";

    String status(String theme) {
        String s = status.get(theme);
        return s == null ? "" : s;
    }

    private void setStatus(String theme, String s) { status.put(theme, s); }

    // ------------------------------------------------------------------ téléchargement

    private static final class Candidate {
        String id, url, title, credit, ext, group = "";
        long maxBytes;
        int height;
        double duration;
        String page = "";
        String fallbackUrl;
        String thumb = "";
        boolean pexels;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("url", url); o.put("title", title); o.put("credit", credit); o.put("ext", ext);
            o.put("group", group); o.put("max", maxBytes); o.put("h", height); o.put("dur", duration);
            o.put("page", page); o.put("fb", fallbackUrl == null ? "" : fallbackUrl); o.put("thumb", thumb);
            o.put("pexels", pexels);
            return o;
        }

        static Candidate fromJson(JSONObject o) {
            Candidate c = new Candidate();
            c.id = o.optString("id"); c.url = o.optString("url"); c.title = o.optString("title");
            c.credit = o.optString("credit"); c.ext = o.optString("ext", ".webm"); c.group = o.optString("group");
            c.maxBytes = o.optLong("max"); c.height = o.optInt("h"); c.duration = o.optDouble("dur", 0);
            c.page = o.optString("page"); c.fallbackUrl = o.optString("fb").isEmpty() ? null : o.optString("fb");
            c.thumb = o.optString("thumb"); c.pexels = o.optBoolean("pexels");
            return c;
        }
    }

    /** Une vidéo proposée dans le choix des vidéos (aperçu, titre, durée…). */
    static final class Pick {
        final String key, title, credit, group, thumbUrl;
        final int height;
        final double duration;
        final boolean pexels;
        Pick(Candidate c) {
            key = c.page; title = c.title; credit = c.credit; group = c.group; thumbUrl = c.thumb;
            height = c.height; duration = c.duration; pexels = c.pexels;
        }
    }

    private int fetch(String theme, int want) throws Exception {
        List<Candidate> cands = new ArrayList<>();
        if (POTD.equals(theme)) return fetchPotd();
        if (SPACE.equals(theme)) {
            // photos d'astronomie sélectionnées par la communauté Commons (« Featured pictures »)
            cands.addAll(commons(pick(SPACE_CATS)));
            cands.addAll(commons(pick(SPACE_CATS)));
        } else if (NATURE.equals(theme)) {
            cands.addAll(commons(pick(NATURE_CATS)));
            cands.addAll(commons(pick(NATURE_CATS)));
        } else if (ANIMALS.equals(theme)) {
            cands.addAll(commons(pick(ANIMAL_CATS)));
            cands.addAll(commons(pick(ANIMAL_CATS)));
        } else if (VIDEO.equals(theme)) {
            setStatus(theme, "Préparation…");
            purgeOffCatalog(VIDEO);
            try { cands.addAll(videoCandidates()); } catch (Exception e) { lastError = "Commons : " + e.getMessage(); }
        }
        if (!VIDEO.equals(theme)) java.util.Collections.shuffle(cands, rnd);
        if (cands.isEmpty()) {
            setStatus(theme, lastError.isEmpty() ? "Aucun résultat, nouvel essai plus tard" : "Erreur · " + lastError);
            return 0;
        }

        JSONArray index = readIndex(theme);
        Set<String> known = new HashSet<>();
        for (int i = 0; i < index.length(); i++) known.add(index.optJSONObject(i).optString("id"));
        Set<String> hidden = prefs.hiddenWalls();

        if (VIDEO.equals(theme)) want = cands.size(); // toutes les vidéos cochées (dans la limite d'espace)
        int got = 0;
        String label = VIDEO.equals(theme) ? "Vidéo" : "Photo";
        for (Candidate c : cands) {
            if (got >= want || !active) break;
            if (known.contains(c.id) || hidden.contains(theme + ":" + c.id)) continue;
            String prefix = label + " " + (got + 1) + " sur " + want;
            String file = fetchMedia(c, c.url, theme, prefix);
            if (file == null && c.fallbackUrl != null) file = fetchMedia(c, c.fallbackUrl, theme, prefix);
            if (file == null) continue;
            JSONObject o = new JSONObject();
            o.put("id", c.id); o.put("file", file); o.put("title", c.title); o.put("credit", c.credit);
            o.put("group", c.group);
            o.put("page", c.page);
            o.put("t", System.currentTimeMillis());
            index.put(o);
            known.add(c.id);
            got++;
            writeIndex(theme, prune(theme, index));
            notifyUpdated(theme);
        }
        int total = readIndex(theme).length();
        setStatus(theme, got > 0 ? "À jour · " + total + (VIDEO.equals(theme) ? " vidéos" : " photos")
                : (lastError.isEmpty() ? "Rien de nouveau pour l'instant" : "Erreur · " + lastError));
        return got;
    }

    /** Libère de la place : d'abord les fonds masqués, puis les plus anciens. */
    private JSONArray prune(String theme, JSONArray index) {
        int max = VIDEO.equals(theme) ? VID_MAX : COSY.equals(theme) ? COSY_MAX : IMG_MAX;
        Set<String> hidden = prefs.hiddenWalls();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < index.length(); i++)
            if (hidden.contains(theme + ":" + index.optJSONObject(i).optString("id"))) order.add(i);
        for (int i = 0; i < index.length(); i++) if (!order.contains(i)) order.add(i);
        int drop = Math.max(0, index.length() - max);
        if (VIDEO.equals(theme) || COSY.equals(theme)) {
            long cap = COSY.equals(theme) ? COSY_DISK : diskCap();
            long total = 0;
            for (int i = 0; i < index.length(); i++) total += new File(dir(theme), index.optJSONObject(i).optString("file")).length();
            int k = 0;
            while (total > cap && k < order.size() - 1) {
                total -= new File(dir(theme), index.optJSONObject(order.get(k)).optString("file")).length();
                k++;
            }
            drop = Math.max(drop, k);
        }
        if (drop == 0) return index;
        Set<Integer> gone = new HashSet<>(order.subList(0, Math.min(drop, order.size())));
        JSONArray out = new JSONArray();
        for (int i = 0; i < index.length(); i++) {
            JSONObject o = index.optJSONObject(i);
            if (gone.contains(i)) new File(dir(theme), o.optString("file")).delete();
            else out.put(o);
        }
        return out;
    }

    // ------------------------------------------------------------------ mes photos et vidéos (envoyées du téléphone)

    File mineDir() { return dir(MINE); }

    synchronized void addMine(String id, String file, String title) {
        try {
            JSONArray index = readIndex(MINE);
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("file", file); o.put("title", title); o.put("credit", "");
            o.put("t", System.currentTimeMillis());
            index.put(o);
            writeIndex(MINE, index);
        } catch (Exception ignored) { }
    }

    synchronized void removeMine(String wallId) {
        if (wallId == null) return;
        forget(wallId.startsWith(MINE + ":") ? wallId : MINE + ":" + wallId);
    }

    /** Supprime du disque un fond masqué. */
    void forget(String wallId) {
        int c = wallId.indexOf(':');
        if (c < 0) return;
        String theme = wallId.substring(0, c), id = wallId.substring(c + 1);
        JSONArray index = readIndex(theme), out = new JSONArray();
        for (int i = 0; i < index.length(); i++) {
            JSONObject o = index.optJSONObject(i);
            if (id.equals(o.optString("id"))) new File(dir(theme), o.optString("file")).delete();
            else out.put(o);
        }
        writeIndex(theme, out);
    }

    private void notifyUpdated(final String theme) {
        main.post(new Runnable() {
            @Override public void run() { if (listener != null) listener.onThemeUpdated(theme); }
        });
    }

    private static boolean goodImage(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        // paysage uniquement, assez grand pour une TV
        return o.outWidth >= 1600 && o.outHeight > 0 && o.outWidth >= o.outHeight * 1.2f;
    }

    // ------------------------------------------------------------------ photo du jour (Wikimedia Commons)

    /**
     * « Image du jour » de Wikimedia Commons pour les 10 derniers jours, avec sa légende en
     * français (repli sur l'anglais). Licence libre vérifiée, fichier contrôlé par MediaGuard.
     */
    private int fetchPotd() throws Exception {
        JSONArray index = readIndex(POTD);
        Set<String> known = new HashSet<>();
        for (int i = 0; i < index.length(); i++) known.add(index.optJSONObject(i).optString("id"));
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        int got = 0;
        for (int d = 0; d < 10 && active; d++) {
            String date = f.format(new java.util.Date(System.currentTimeMillis() - d * 86_400_000L));
            String id = "potd_" + date;
            if (known.contains(id) || prefs.hiddenWalls().contains(POTD + ":" + id)) continue;
            setStatus(POTD, "Photo du " + date);
            String file = expand("{{Potd/" + date + "}}");
            if (file.isEmpty() || file.contains("Template:") || file.contains("{{") || file.length() > 240) continue;
            String caption = expand("{{Potd/" + date + " (fr)}}");
            if (caption.isEmpty() || caption.contains("Template:Potd")) caption = expand("{{Potd/" + date + " (en)}}");
            JSONObject q = getJson("https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo"
                    + "&iiprop=url|size|mime|extmetadata&iiurlwidth=3840&iiextmetadatafilter=Artist|LicenseShortName&titles="
                    + enc("File:" + file.trim())).optJSONObject("query");
            if (q == null) continue;
            JSONObject pages = q.getJSONObject("pages");
            Iterator<String> it = pages.keys();
            if (!it.hasNext()) continue;
            JSONArray ii = pages.getJSONObject(it.next()).optJSONArray("imageinfo");
            if (ii == null || ii.length() == 0) continue;
            JSONObject info = ii.getJSONObject(0);
            if (!"image/jpeg".equals(info.optString("mime")) && !"image/png".equals(info.optString("mime"))) continue;
            JSONObject meta = info.optJSONObject("extmetadata");
            String lic = meta != null ? value(meta, "LicenseShortName") : "";
            if (!NetPolicy.freeLicense(lic)) continue;
            String artist = meta != null ? value(meta, "Artist") : "";
            Candidate c = new Candidate();
            c.id = id;
            c.url = info.optString("thumburl", info.optString("url"));
            c.maxBytes = 30L << 20;
            String title = cleanWikitext(caption);
            c.title = title.isEmpty() ? clean(file.replaceAll("\\.[A-Za-z]+$", "")) : title;
            c.credit = clean((artist.isEmpty() ? "Wikimedia Commons" : artist) + " · " + lic + " · Image du jour");
            String saved = fetchMedia(c, c.url, POTD, "Photo du " + date);
            if (saved == null) continue;
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("file", saved); o.put("title", c.title); o.put("credit", c.credit);
            o.put("t", System.currentTimeMillis());
            index = readIndex(POTD);
            index.put(o);
            writeIndex(POTD, prune(POTD, index));
            got++;
            notifyUpdated(POTD);
        }
        setStatus(POTD, got > 0 ? "À jour" : "Rien de nouveau pour l'instant");
        return got;
    }

    /** Développe un modèle Commons (ex. {{Potd/2026-11-09}}) et renvoie le wikitexte. */
    private static String expand(String template) {
        try {
            JSONObject r = getJson("https://commons.wikimedia.org/w/api.php?action=expandtemplates&format=json&prop=wikitext&text="
                    + enc(template));
            JSONObject e = r.optJSONObject("expandtemplates");
            return e == null ? "" : e.optString("wikitext", "").trim();
        } catch (Exception ex) {
            return "";
        }
    }

    /** Wikitexte → texte simple : liens, gras, modèles et balises retirés, 220 caractères au plus. */
    static String cleanWikitext(String w) {
        if (w == null) return "";
        String t = w;
        for (int i = 0; i < 3; i++) t = t.replaceAll("\\{\\{[^{}]*\\}\\}", "");
        t = t.replaceAll("\\[\\[(?:File|Fichier|Image):[^\\]]*\\]\\]", "");
        t = t.replaceAll("\\[\\[[^\\]|]*\\|([^\\]]*)\\]\\]", "$1");
        t = t.replaceAll("\\[\\[([^\\]]*)\\]\\]", "$1");
        t = t.replaceAll("\\[https?://[^\\s\\]]+ ([^\\]]*)\\]", "$1");
        t = t.replaceAll("'{2,}", "").replaceAll("<[^>]*>", "");
        @SuppressWarnings("deprecation") String plain = Html.fromHtml(t).toString();
        t = plain.replaceAll("\\s+", " ").trim();
        return t.length() > 220 ? t.substring(0, 218) + "…" : t;
    }

    // ------------------------------------------------------------------ vidéos 4K


    private boolean want4k() { return prefs.video4k(); }


    /**
     * Requête vidéo Commons. exclude : mots interdits dans le titre ; include (facultatif) : au moins un
     * de ces mots doit figurer dans le titre ; minDur : durée minimale en secondes.
     */
    /**
     * Fichiers vidéo Commons d'un catalogue (generator = « &titles=… »). Choisit le transcodage VP9 le plus
     * net (2160p en mode 4K, sinon 1080p) dont la taille estimée tient sous la limite, avec en secours
     * la définition inférieure.
     */
    private List<Candidate> commonsVideoQuery(String generator, int minH) throws Exception {
        List<Candidate> out = new ArrayList<>();
        String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json" + generator
                + "&prop=videoinfo&viprop=url|size|mime|derivatives|extmetadata&viurlwidth=500" // tailles standard Wikimedia seulement (sinon refus 429)
                + "&viextmetadatafilter=Artist|LicenseShortName|ObjectName";
        JSONObject q = getJson(url).optJSONObject("query");
        if (q == null) return out;
        JSONObject pages = q.getJSONObject("pages");
        Iterator<String> it = pages.keys();
        int targetH = want4k() ? 2160 : 1080;
        while (it.hasNext()) {
            JSONObject p = pages.getJSONObject(it.next());
            JSONArray vi = p.optJSONArray("videoinfo");
            if (vi == null || vi.length() == 0) continue;
            JSONObject info = vi.getJSONObject(0);
            int w = info.optInt("width"), h = info.optInt("height");
            double dur = info.optDouble("duration", 0);
            if (h < minH || w < h * 1.6f) continue; // paysage, assez grand
            long cap = (want4k() ? 900L : 450L) << 20;
            JSONArray der = info.optJSONArray("derivatives");
            String best = null, second = null;
            int bestH = 0, secondH = 0;
            if (der != null) for (int k = 0; k < der.length(); k++) {
                JSONObject d = der.getJSONObject(k);
                String type = d.optString("type");
                int dh = d.optInt("height");
                if (!type.contains("webm") || !type.contains("vp9")) continue;
                if (dh > targetH || dh < minH) continue;
                long bw = d.optLong("bandwidth", 0);
                if (bw > 0 && dur > 0 && (long) (bw * dur / 8) > cap * 95 / 100) continue; // trop lourd
                String src = d.optString("src");
                if (dh > bestH) { second = best; secondH = bestH; bestH = dh; best = src; }
                else if (dh > secondH && dh < bestH) { secondH = dh; second = src; }
            }
            if (best == null) continue;
            JSONObject meta = info.optJSONObject("extmetadata");
            String title = meta != null ? value(meta, "ObjectName") : "";
            if (title.isEmpty()) title = p.optString("title").replaceFirst("^File:", "").replaceAll("\\.[A-Za-z0-9]+$", "");
            String artist = meta != null ? value(meta, "Artist") : "";
            String lic = meta != null ? value(meta, "LicenseShortName") : "";
            if (!NetPolicy.freeLicense(lic)) continue; // licences libres uniquement
            Candidate c = new Candidate();
            c.id = "wmv_" + p.optLong("pageid");
            c.url = best.startsWith("//") ? "https:" + best : best;
            if (second != null) c.fallbackUrl = second.startsWith("//") ? "https:" + second : second;
            c.maxBytes = cap;
            c.title = clean(title);
            c.credit = clean((artist.isEmpty() ? "Wikimedia Commons" : artist) + (lic.isEmpty() ? "" : " · " + lic));
            c.ext = ".webm";
            c.height = bestH;
            c.duration = dur;
            c.page = p.optString("title");
            String th = info.optString("thumburl");
            if (th.startsWith("//")) th = "https:" + th;
            c.thumb = th;
            out.add(c);
        }
        return out;
    }

    // ------------------------------------------------------------------ ambiances (catalogue fermé)

    static final String[] COSY_NAMES = CosyCatalog.NAMES;
    private static final int COSY_PER_KIND = 8, COSY_MAX = 6 * COSY_PER_KIND + 30;
    private static final long COSY_DISK = 4L << 30;
    private final ExecutorService cosyExec = Executors.newSingleThreadExecutor();
    private volatile boolean cosyRunning;

    /** Vidéos d'une ambiance : d'abord celles envoyées du téléphone, puis celles cochées. */
    List<WallItem> cosyItems(int kind) {
        List<WallItem> mine = new ArrayList<>(), chosen = new ArrayList<>();
        JSONArray a = readIndex(COSY);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || !String.valueOf(kind).equals(o.optString("group"))) continue;
            String page = o.optString("page");
            boolean isMine = page.startsWith("mine:");
            if (!isMine && !prefs.videoOk(page)) continue;
            File f = new File(dir(COSY), o.optString("file"));
            if (!f.exists()) continue;
            WallItem w = new WallItem(WallItem.VIDEO_FILE, COSY + ":" + o.optString("id"), f.getAbsolutePath(),
                    o.optString("title"), o.optString("credit"));
            (isMine ? mine : chosen).add(w);
        }
        mine.addAll(chosen);
        return mine;
    }

    /** Vidéos d'ambiance envoyées depuis le téléphone (toutes ambiances). */
    List<WallItem> cosyMine() {
        List<WallItem> out = new ArrayList<>();
        JSONArray a = readIndex(COSY);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || !o.optString("page").startsWith("mine:")) continue;
            File f = new File(dir(COSY), o.optString("file"));
            if (!f.exists()) continue;
            int k = o.optInt("group", 0);
            String amb = k >= 0 && k < COSY_NAMES.length ? COSY_NAMES[k] : "";
            out.add(new WallItem(WallItem.VIDEO_FILE, COSY + ":" + o.optString("id"), f.getAbsolutePath(),
                    o.optString("title") + " · " + amb, ""));
        }
        return out;
    }

    File cosyDir() { return dir(COSY); }

    /** « Ne plus montrer cette vidéo » : décoche la vidéo (ou supprime celle envoyée du téléphone) et l'efface. */
    void dismissCosy(String wallId) {
        if (wallId == null || !wallId.startsWith(COSY + ":")) return;
        String id = wallId.substring(COSY.length() + 1);
        JSONArray a = readIndex(COSY);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || !id.equals(o.optString("id"))) continue;
            String page = o.optString("page");
            if (!page.startsWith("mine:")) prefs.setVideoOk(page, false);
        }
        forget(wallId);
    }

    /** Ajoute une vidéo envoyée du téléphone à une ambiance (elle passe avant les autres). */
    synchronized void addCosyMine(int kind, String id, String file, String title) {
        try {
            JSONArray index = readIndex(COSY);
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("file", file); o.put("title", title); o.put("credit", "");
            o.put("page", "mine:" + id); o.put("group", String.valueOf(kind));
            o.put("t", System.currentTimeMillis());
            index.put(o);
            writeIndex(COSY, index);
        } catch (Exception ignored) { }
        notifyUpdated(COSY);
    }

    boolean cosyBusy() { return cosyRunning; }

    /**
     * Complète chaque ambiance jusqu'à COSY_PER_KIND vidéos, uniquement depuis CosyCatalog.
     * D'abord une vidéo par ambiance (pour que toutes soient vite disponibles), puis la suivante.
     * File séparée des fonds d'écran : une grosse vidéo de ville ne bloque pas les ambiances.
     */
    void refreshCosy() {
        if (cosyRunning) return;
        cosyRunning = true;
        cosyExec.execute(new Runnable() {
            @Override public void run() {
                try {
                    purgeOffCatalog(COSY);
                    setStatus(COSY, "Préparation…");
                    List<Candidate> all = catalog(SCOPE_COSY, false);
                    for (int round = 1; round <= COSY_PER_KIND && active; round++)
                        for (int k = 0; k < COSY_NAMES.length && active; k++)
                            if (cosyItems(k).size() < round) cosyFetchOne(k, all);
                    setStatus(COSY, "");
                } catch (Throwable e) {
                    lastError = "Commons : " + e.getMessage();
                    setStatus(COSY, "");
                } finally { cosyRunning = false; }
            }
        });
    }

    /** Efface les vidéos qui ne sont plus cochées (ou qui viennent d'une version précédente sans choix). */
    private void purgeOffCatalog(String theme) {
        JSONArray idx = readIndex(theme), keep = new JSONArray();
        boolean changed = false;
        for (int i = 0; i < idx.length(); i++) {
            JSONObject o = idx.optJSONObject(i);
            if (o == null) continue;
            String page = o.optString("page");
            boolean ok = page.startsWith("mine:") || prefs.videoOk(page);
            if (!ok) {
                new File(dir(theme), o.optString("file")).delete();
                changed = true;
            } else keep.put(o);
        }
        if (changed) { writeIndex(theme, keep); notifyUpdated(theme); }
    }

    /**
     * Fonds vidéo : uniquement VideoCatalog. Ordre : une vidéo par groupe autorisé à tour de rôle
     * (chaque ville choisie, Nature, Espace et Terre selon le thème réglé), dans l'ordre du catalogue.
     */
    private List<Candidate> videoCandidates() throws Exception {
        List<Candidate> all = catalog(SCOPE_FONDS, false);
        int vt = prefs.videoTheme();
        List<String> groups = new ArrayList<>();
        if (vt == Prefs.VT_ALL || vt == Prefs.VT_CITY) {
            for (String[] city : VideoCatalog.CITIES) if (prefs.cities().contains(city[0])) groups.add(city[0]);
            groups.add(Pexels.CITIES);
        }
        if (vt == Prefs.VT_ALL || vt == Prefs.VT_NATURE) { groups.add(VideoCatalog.NATURE); groups.add(Pexels.NATURE); }
        if (vt == Prefs.VT_ALL || vt == Prefs.VT_SPACE) groups.add(VideoCatalog.SPACE);
        // une vidéo cochée par groupe à tour de rôle, dans l'ordre du catalogue
        java.util.Map<String, List<Candidate>> byGroup = new java.util.LinkedHashMap<>();
        for (String g : groups) byGroup.put(g, new ArrayList<Candidate>());
        for (Candidate c : all) {
            List<Candidate> l = byGroup.get(c.group);
            if (l != null && prefs.videoOk(c.page)) l.add(c);
        }
        List<Candidate> out = new ArrayList<>();
        for (int round = 0; round < 40; round++)
            for (List<Candidate> l : byGroup.values()) if (round < l.size()) out.add(l.get(round));
        return out;
    }

    /** Télécharge la prochaine vidéo cochée pour cette ambiance (pas encore là). */
    private void cosyFetchOne(int kind, List<Candidate> all) {
        String name = COSY_NAMES[kind];
        JSONArray index = readIndex(COSY);
        Set<String> known = new HashSet<>();
        for (int i = 0; i < index.length(); i++) known.add(index.optJSONObject(i).optString("id"));
        for (Candidate c : all) {
            if (!active) return;
            if (!cosyGroup(kind).equals(c.group) || !prefs.videoOk(c.page) || known.contains(c.id)) continue;
            String file = fetchMedia(c, c.url, COSY, name);
            if (file == null && c.fallbackUrl != null) file = fetchMedia(c, c.fallbackUrl, COSY, name);
            if (file == null) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("id", c.id); o.put("file", file); o.put("title", c.title); o.put("credit", c.credit);
                o.put("page", c.page);
                o.put("group", String.valueOf(kind));
                o.put("t", System.currentTimeMillis());
                JSONArray idx = readIndex(COSY);
                idx.put(o);
                writeIndex(COSY, prune(COSY, idx));
            } catch (Exception ignored) { }
            notifyUpdated(COSY);
            return;
        }
    }

    // ------------------------------------------------------------------ choix des vidéos (catalogue + Pexels)

    static final String SCOPE_FONDS = "fonds", SCOPE_COSY = "cosy";
    /** Groupe des vidéos d'ambiance : « cosy:<n° d'ambiance> ». */
    static String cosyGroup(int kind) { return "cosy:" + kind; }

    private File catalogDir() {
        File d = new File(ctx.getFilesDir(), "catalog");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /**
     * Vidéos proposées pour un usage (fonds ou ambiances) : le catalogue fermé de Commons, plus les
     * suggestions Pexels si une clé est réglée. Mis en cache un jour. Ne télécharge aucune vidéo.
     */
    synchronized List<Candidate> catalog(String scope, boolean force) throws Exception {
        File f = new File(catalogDir(), scope + ".json");
        String sig = "v2" + (prefs.pexelsKey().isEmpty() ? "0" : "1") + (want4k() ? "4k" : "hd"); // v2 : aperçus en 500 px
        if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < DAY) {
            try {
                JSONObject o = new JSONObject(new String(readAll(f), "UTF-8"));
                if (sig.equals(o.optString("sig"))) {
                    List<Candidate> out = new ArrayList<>();
                    JSONArray a = o.getJSONArray("items");
                    for (int i = 0; i < a.length(); i++) out.add(Candidate.fromJson(a.getJSONObject(i)));
                    return out;
                }
            } catch (Exception ignored) { }
        }
        List<Candidate> out = new ArrayList<>();
        if (SCOPE_COSY.equals(scope)) {
            for (Candidate c : commonsVideoQuery("&titles=" + enc(CosyCatalog.allTitles()), 720)) {
                int k = CosyCatalog.kindOf(c.page);
                if (k >= 0) { c.group = cosyGroup(k); out.add(c); }
            }
        } else {
            for (Candidate c : commonsVideoQuery("&titles=" + enc(VideoCatalog.allTitles()), 720)) {
                String g = VideoCatalog.groupOf(c.page);
                if (g != null) { c.group = g; out.add(c); }
            }
        }
        // ordre du catalogue (l'API renvoie les pages dans le désordre)
        final List<String> order = new ArrayList<>(java.util.Arrays.asList(
                (SCOPE_COSY.equals(scope) ? CosyCatalog.allTitles() : VideoCatalog.allTitles()).split("\\|")));
        java.util.Collections.sort(out, new java.util.Comparator<Candidate>() {
            @Override public int compare(Candidate x, Candidate y) { return order.indexOf(x.page) - order.indexOf(y.page); }
        });
        if (!prefs.pexelsKey().isEmpty()) {
            String[][] qs = SCOPE_COSY.equals(scope) ? Pexels.COSY_QUERIES : Pexels.FONDS_QUERIES;
            for (String[] q : qs) {
                try { out.addAll(pexelsSearch(q[1], q[0], SCOPE_COSY.equals(scope) ? 10 : 6)); }
                catch (Exception e) { lastError = "Pexels : " + e.getMessage(); }
            }
        }
        try {
            JSONObject o = new JSONObject();
            o.put("sig", sig);
            JSONArray a = new JSONArray();
            for (Candidate c : out) a.put(c.toJson());
            o.put("items", a);
            File tmp = new File(catalogDir(), scope + ".tmp");
            try (OutputStream os = new FileOutputStream(tmp)) { os.write(o.toString().getBytes("UTF-8")); }
            tmp.renameTo(f);
        } catch (Exception ignored) { }
        return out;
    }

    private volatile List<Pick> lastPicks = new ArrayList<>();
    void rememberPicks(List<Pick> p) { lastPicks = p; }
    List<Pick> lastPicks() { return lastPicks; }

    /** Pour l'écran de choix : la liste des vidéos proposées (appel bloquant, hors du fil principal). */
    List<Pick> picks(String scope) throws Exception {
        List<Pick> out = new ArrayList<>();
        for (Candidate c : catalog(scope, false)) out.add(new Pick(c));
        return out;
    }

    /** Aperçu d'une vidéo proposée : petite image JPEG téléchargée, vérifiée et réécrite (cache local). */
    File thumbOf(Pick p) {
        File d = new File(catalogDir(), "thumbs");
        if (!d.exists()) d.mkdirs();
        String name = "t" + Integer.toHexString(p.key.hashCode());
        File done = new File(d, name + ".jpg");
        if (done.exists() && done.length() > 0) return done;
        List<String> urls = p.pexels ? java.util.Collections.singletonList(p.thumbUrl) : CommonsThumb.candidates(p.thumbUrl, p.key);
        String why = "aucune adresse";
        for (String u : urls) {
            if (u == null || u.isEmpty()) continue;
            File raw = new File(d, name + ".raw");
            for (int attempt = 0; ; attempt++) {
                try {
                    politeWait();
                    byte[] b = Net.getBytes(NetPolicy.https(u), 3 << 20);
                    try (OutputStream o = new FileOutputStream(raw)) { o.write(b); }
                    File ok = MediaGuard.accept(raw, d, name, false, true);
                    if (ok != null) return ok;
                    why = "image refusée (" + b.length + " octets)";
                    break;
                } catch (Net.HttpError e) {
                    raw.delete();
                    why = e.getMessage();
                    if (!e.busy()) break;                 // 404… : on passe à la taille suivante
                    if (attempt >= 3) { thumbError = why; return null; } // le serveur sature : on réessaiera plus tard
                    // le serveur demande de ralentir : tout le monde attend, puis on redemande la même image
                    backOff(e.retryAfterSec > 0 ? e.retryAfterSec * 1000L : 2000L << attempt);
                } catch (Exception e) {
                    raw.delete();
                    why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    break;
                }
            }
        }
        thumbError = why;
        CrashLog.note(ctx, "Aperçu indisponible : " + p.key + " → " + why);
        return null;
    }

    /** Aperçus Wikimedia : une demande à la fois, espacées, et pause commune quand le serveur dit « trop vite ». */
    private static final Object THUMB_LOCK = new Object();
    private static long thumbNext = 0;

    private static void politeWait() throws InterruptedException {
        long wait;
        synchronized (THUMB_LOCK) {
            long now = System.currentTimeMillis();
            long at = Math.max(now, thumbNext);
            thumbNext = at + 250;
            wait = at - now;
        }
        if (wait > 0) Thread.sleep(wait);
    }

    private static void backOff(long ms) {
        ms = Math.min(ms, 30_000);
        synchronized (THUMB_LOCK) { thumbNext = Math.max(thumbNext, System.currentTimeMillis() + ms); }
    }

    /** Dernière raison d'échec d'un aperçu (affichée dans l'écran de choix). */
    volatile String thumbError = "";

    // ------------------------------------------------------------------ Pexels (facultatif, clé personnelle)

    /**
     * Recherche Pexels : uniquement si l'utilisateur a réglé sa propre clé. Les résultats ne sont que
     * des suggestions dans l'écran de choix : rien n'est téléchargé sans être coché.
     */
    private List<Candidate> pexelsSearch(String query, String group, int perPage) throws Exception {
        List<Candidate> out = new ArrayList<>();
        String url = "https://api.pexels.com/videos/search?orientation=landscape&size=" + (want4k() ? "large" : "medium")
                + "&per_page=" + perPage + "&query=" + enc(query);
        JSONObject r = Net.getJson(NetPolicy.https(url), prefs.pexelsKey());
        JSONArray vids = r.optJSONArray("videos");
        if (vids == null) return out;
        int targetH = want4k() ? 2160 : 1080;
        long cap = (want4k() ? 900L : 450L) << 20;
        for (int i = 0; i < vids.length(); i++) {
            JSONObject v = vids.getJSONObject(i);
            List<Pexels.File> files = new ArrayList<>();
            JSONArray vf = v.optJSONArray("video_files");
            if (vf != null) for (int k = 0; k < vf.length(); k++) {
                JSONObject f = vf.optJSONObject(k);
                if (f != null) files.add(new Pexels.File(f.optString("link"), f.optString("file_type"), f.optInt("width"), f.optInt("height")));
            }
            Pexels.File best = Pexels.choose(files, targetH, 720);
            if (best == null || v.optInt("width") < v.optInt("height") * 1.6f) continue;
            Pexels.File second = Pexels.choose(files, best.height - 1, 720);
            Candidate c = new Candidate();
            c.id = "px_" + v.optLong("id");
            c.page = "pexels:" + v.optLong("id");
            c.url = best.link;
            c.fallbackUrl = second == null ? null : second.link;
            c.maxBytes = cap;
            c.height = best.height;
            c.duration = v.optDouble("duration", 0);
            c.title = clean(Pexels.titleFromUrl(v.optString("url"), query));
            JSONObject user = v.optJSONObject("user");
            c.credit = clean((user == null ? "" : user.optString("name") + " · ") + "Pexels");
            String img = v.optString("image");
            c.thumb = img.isEmpty() || img.contains("?") ? img : img + "?auto=compress&cs=tinysrgb&w=480";
            c.ext = ".mp4";
            c.group = group;
            c.pexels = true;
            out.add(c);
        }
        return out;
    }

    private static byte[] readAll(File f) throws java.io.IOException {
        if (f.length() > (8 << 20)) throw new java.io.IOException("trop gros");
        byte[] b = new byte[(int) f.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
        }
        return b;
    }

    // ------------------------------------------------------------------ Wikimedia Commons

    private List<Candidate> commons(String category) throws Exception {
        List<Candidate> out = new ArrayList<>();
        String prefix = String.valueOf((char) ('A' + rnd.nextInt(26)));
        String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json"
                + "&generator=categorymembers&gcmtype=file&gcmlimit=40&gcmtitle=Category:" + enc(category)
                + "&gcmstartsortkeyprefix=" + prefix
                + "&prop=imageinfo&iiprop=url|size|mime|extmetadata&iiurlwidth=3840"
                + "&iiextmetadatafilter=Artist|LicenseShortName|ObjectName";
        JSONObject r = getJson(url);
        JSONObject q = r.optJSONObject("query");
        if (q == null) return out;
        JSONObject pages = q.getJSONObject("pages");
        Iterator<String> it = pages.keys();
        while (it.hasNext()) {
            JSONObject p = pages.getJSONObject(it.next());
            JSONArray ii = p.optJSONArray("imageinfo");
            if (ii == null || ii.length() == 0) continue;
            JSONObject info = ii.getJSONObject(0);
            int w = info.optInt("width"), h = info.optInt("height");
            String mime = info.optString("mime");
            if (!"image/jpeg".equals(mime) || w < 2400 || w < h * 1.3) continue;
            if (illustration(p.optString("title"))) continue; // vues d'artiste, schémas, simulations
            String thumb = info.optString("thumburl", info.optString("url"));
            if (thumb.isEmpty()) continue;
            JSONObject meta = info.optJSONObject("extmetadata");
            String title = meta != null ? value(meta, "ObjectName") : "";
            if (title.isEmpty()) title = p.optString("title").replaceFirst("^File:", "").replaceAll("\\.[A-Za-z]+$", "");
            String artist = meta != null ? value(meta, "Artist") : "";
            String lic = meta != null ? value(meta, "LicenseShortName") : "";
            if (!NetPolicy.freeLicense(lic)) continue; // licences libres uniquement
            Candidate c = new Candidate();
            c.id = "wm_" + p.optLong("pageid");
            c.url = thumb;
            c.maxBytes = 25L << 20;
            c.title = clean(title);
            c.credit = clean((artist.isEmpty() ? "Wikimedia Commons" : artist) + (lic.isEmpty() ? "" : " · " + lic));
            c.ext = ".jpg";
            out.add(c);
        }
        return out;
    }

    private static final String[] ILLUSTRATION = {"artist", "impression", "illustration", "concept", "render",
            "diagram", "simulation", "visualization", "visualisation", "infographic", "map of", "chart"};

    static boolean illustration(String title) {
        String t = title == null ? "" : title.toLowerCase(java.util.Locale.ROOT);
        for (String x : ILLUSTRATION) if (t.contains(x)) return true;
        return false;
    }

    private static String value(JSONObject meta, String key) {
        JSONObject o = meta.optJSONObject(key);
        return o == null ? "" : o.optString("value");
    }

    // ------------------------------------------------------------------ réseau (via Net) et contrôle des fichiers

    private static JSONObject getJson(String url) throws Exception {
        return Net.getJson(NetPolicy.https(url));
    }

    /**
     * Télécharge puis fait vérifier le fichier par MediaGuard (type réel, taille, ré-encodage
     * des images). Renvoie le nom du fichier final gardé, ou null si refusé.
     */
    private String fetchMedia(final Candidate c, String url, final String theme, final String prefix) {
        if (url == null) return null;
        File raw = new File(dir(theme), safe(c.id) + ".raw");
        try {
            Net.download(NetPolicy.https(url), raw, c.maxBytes, new Net.Progress() {
                @Override public void onProgress(long done, long total) throws java.io.IOException {
                    if (!active) throw new java.io.IOException("En pause");
                    setStatus(theme, total > 0 ? prefix + " · " + (done * 100 / total) + " %" : prefix + " · " + (done >> 20) + " Mo");
                }
            });
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            raw.delete();
            return null;
        }
        boolean video = VIDEO.equals(theme) || COSY.equals(theme);
        File ok = MediaGuard.accept(raw, dir(theme), safe(c.id), video, !video);
        if (ok == null) { lastError = "Fichier refusé (type non conforme)"; return null; }
        if (!video && !goodImage(ok)) { ok.delete(); return null; }
        return ok.getName();
    }

    private String pick(String[] a) { return a[rnd.nextInt(a.length)]; }
    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private static String encPath(String s) throws Exception { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); }
    private static String safe(String s) { return s.replaceAll("[^A-Za-z0-9_-]", "_"); }

    @SuppressWarnings("deprecation")
    static String clean(String s) {
        if (s == null) return "";
        String t = Html.fromHtml(s).toString().replaceAll("\\s+", " ").trim();
        return t.length() > 90 ? t.substring(0, 88) + "…" : t;
    }
}

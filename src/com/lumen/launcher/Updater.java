// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Mise à jour automatique depuis le dépôt GitHub de Lumen : dist/latest.json décrit la dernière
 * version et renvoie vers son fichier dans les GitHub Releases (ou dist/Lumen.apk, pont pour les
 * anciennes versions).
 * Toutes les 6 heures au plus (quand Lumen est à l'écran), ou dès qu'on ouvre Réglages › Système : lecture de latest.json, téléchargement de Lumen.apk
 * si la version est plus récente, puis contrôles : taille et empreinte SHA-256 annoncées, fichier APK,
 * même nom d'appli, version plus récente et MÊME CLÉ DE SIGNATURE que Lumen installé. Android demande ensuite une confirmation
 * (une appli ne peut pas s'installer seule sans l'accord de l'utilisateur).
 */
final class Updater {
    interface Listener { void onUpdateState(); }

    /** Écart entre deux recherches automatiques. */
    private static final long EVERY = 6L * 3600 * 1000;
    /** Écart minimal quand on ouvre Réglages › Système. */
    private static final long SOON = 5L * 60 * 1000;
    /** Nouvel essai après un fichier pas encore à jour chez GitHub. */
    private static final long RETRY = 10L * 60 * 1000;
    private volatile boolean retrySoon;
    private final Context ctx;
    private final Prefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean busy;
    private volatile String state = "";
    private Listener listener;

    Updater(Context c, Prefs p) { ctx = c.getApplicationContext(); prefs = p; }

    void setListener(Listener l) { listener = l; }

    String currentVersion() {
        try { return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    /** Fichier prêt à installer (vérifié), ou null. */
    File ready() {
        File f = new File(dir(), "Lumen.apk");
        String v = prefs.updateReadyVersion();
        if (!f.exists() || v.isEmpty() || UpdatePolicy.version(v) == null
                || UpdatePolicy.compare(v, currentVersion()) <= 0) return null;
        return f;
    }

    String readyVersion() { return ready() == null ? "" : prefs.updateReadyVersion(); }

    String state() {
        if (busy) return L.t("Recherche…");
        if (ready() != null) return prefs.updateReadyVersion() + L.t(" prête · OK pour installer");
        return state.isEmpty() ? L.t("Version ") + currentVersion() : state;
    }

    private File dir() {
        File d = new File(ctx.getFilesDir(), "update");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Réglages › Système ouvert : on regarde tout de suite, sauf si on vient de le faire. */
    void checkSoon() {
        if (ready() == null && System.currentTimeMillis() - prefs.lastUpdateCheck() >= SOON) check(true);
    }

    /** Vérifie si une mise à jour existe (au plus toutes les 6 heures, sauf demande explicite). */
    void check(final boolean force) {
        if (busy) return;
        if (!force && (!prefs.autoUpdate() || System.currentTimeMillis() - prefs.lastUpdateCheck() < EVERY)) return;
        busy = true;
        notifyState();
        new Thread(new Runnable() {
            @Override public void run() {
                retrySoon = false;
                try { state = run1(); }
                catch (Exception e) { state = L.t("Vérification impossible · réessaie plus tard"); }
                finally {
                    busy = false;
                    // nouvel essai dans 10 min plutôt que dans 6 h si le fichier n'était pas encore à jour
                    prefs.setLastUpdateCheck(System.currentTimeMillis() - (retrySoon ? EVERY - RETRY : 0));
                    notifyState();
                }
            }
        }, "lumen-update").start();
    }

    /** Clé partagée du projet (dist/services.json) ; silencieux si le fichier manque. */
    void refreshServices() {
        try {
            JSONObject r = Net.getJson(UpdatePolicy.SERVICES + "?t=" + (System.currentTimeMillis() / 1000));
            prefs.setTmdbSharedKey(r.optString("tmdb", ""));
        } catch (Exception ignored) { }
    }

    private String run1() throws Exception {
        refreshServices();
        String mine = currentVersion();
        // paramètre unique : contourne le cache de GitHub (sinon une nouvelle version met jusqu'à 5 min à apparaître)
        JSONObject r = Net.getJson(UpdatePolicy.LATEST + "?t=" + (System.currentTimeMillis() / 1000));
        String v = UpdatePolicy.version(r.optString("version"));
        if (v == null || UpdatePolicy.compare(v, mine) <= 0) return L.t("Lumen est à jour (") + mine + ")";
        if (ready() != null && v.equals(prefs.updateReadyVersion())) return "";
        String sha = r.optString("sha256").toLowerCase(java.util.Locale.ROOT);
        long size = r.optLong("size");
        if (!UpdatePolicy.sha256Ok(sha) || size <= 0 || size > UpdatePolicy.MAX_BYTES) return L.t("Mise à jour ") + v + L.t(" mal décrite");
        prefs.setUpdateNotes(v, UpdatePolicy.notes(r.optString("notes")));
        File tmp = new File(dir(), "download.apk");
        Net.download(UpdatePolicy.apkUrl(r.optString("file", null), v, r.optString("url", null)) + "?v=" + v, tmp, UpdatePolicy.MAX_BYTES, null);
        if (tmp.length() != size || !sha.equals(sha256(tmp))) {
            // juste après une publication, GitHub sert parfois encore l'ancien fichier quelques minutes
            tmp.delete();
            retrySoon = true;
            return L.t("Mise à jour ") + v + L.t(" en cours de publication · nouvel essai dans 10 min");
        }
        String why = verify(tmp, v);
        if (why != null) { tmp.delete(); return why; }
        File out = new File(dir(), "Lumen.apk");
        out.delete();
        if (!tmp.renameTo(out)) { tmp.delete(); return L.t("Écriture impossible"); }
        prefs.setUpdateReadyVersion(v);
        return "";
    }

    private static String sha256(File f) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return UpdatePolicy.hex(md.digest());
    }

    /** null si l'APK est sûr à proposer, sinon la raison du refus. */
    @SuppressWarnings("deprecation")
    private String verify(File apk, String v) {
        try (InputStream in = new FileInputStream(apk)) {
            byte[] head = new byte[4];
            if (in.read(head) != 4 || !UpdatePolicy.looksLikeApk(head)) return L.t("Fichier refusé (ce n'est pas une appli)");
        } catch (Exception e) { return L.t("Fichier illisible"); }
        PackageManager pm = ctx.getPackageManager();
        PackageInfo got = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (got == null) return L.t("Fichier refusé (appli illisible)");
        if (!ctx.getPackageName().equals(got.packageName)) return L.t("Fichier refusé (autre appli)");
        try {
            PackageInfo me = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES);
            if (got.versionCode <= me.versionCode) return L.t("Lumen est à jour (") + me.versionName + ")";
            if (got.signatures == null || me.signatures == null || got.signatures.length == 0
                    || !sameSigners(got.signatures, me.signatures)) return L.t("Mise à jour refusée : signature différente");
        } catch (Exception e) { return L.t("Vérification impossible"); }
        return null;
    }

    private static boolean sameSigners(Signature[] a, Signature[] b) {
        if (a.length != b.length) return false;
        String[] x = new String[a.length], y = new String[b.length];
        for (int i = 0; i < a.length; i++) { x[i] = a[i].toCharsString(); y[i] = b[i].toCharsString(); }
        Arrays.sort(x);
        Arrays.sort(y);
        return Arrays.equals(x, y);
    }

    /** Lance l'installation : Android affiche sa fenêtre de confirmation. */
    boolean install() {
        File f = ready();
        if (f == null) return false;
        try {
            PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(ctx.getPackageName());
            int id = pi.createSession(sp);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (InputStream in = new FileInputStream(f); OutputStream out = s.openWrite("lumen", 0, f.length())) {
                    byte[] buf = new byte[256 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    s.fsync(out);
                }
                Intent done = new Intent(ctx, Updater.UpdateReceiver.class);
                PendingIntent p = PendingIntent.getBroadcast(ctx, id, done, PendingIntent.FLAG_UPDATE_CURRENT);
                s.commit(p.getIntentSender());
            }
            return true;
        } catch (Exception e) {
            state = L.t("Installation impossible");
            notifyState();
            return false;
        }
    }

    private void notifyState() {
        main.post(new Runnable() { @Override public void run() { if (listener != null) listener.onUpdateState(); } });
    }

    /** Résultat de l'installation : si Android demande une confirmation, on affiche sa fenêtre. */
    /** Lumen vient d'être remplacé par sa nouvelle version : il revient aussitôt à l'écran. */
    public static final class ReplacedReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) return;
            backHome(c);
        }
    }

    /** Relance l'accueil Lumen (après une mise à jour). */
    static void backHome(Context c) {
        // 1. la touche Accueil, appuyée par le service « Lumen télécommande » : Android ramène alors
        //    l'écran d'accueil par défaut, c'est-à-dire Lumen (aucune restriction d'Android ici)
        if (RemoteService.perform(RemoteService.HOME)) return;
        Intent home = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // 2. ouverture directe (souvent refusée en silence depuis Android 10, d'où la suite)
        try { c.startActivity(home); } catch (Exception ignored) { }
        // 3. réveil programmé : c'est le système qui ouvre Lumen un instant plus tard
        try {
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(c, 9, home,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | 0x04000000 /* FLAG_IMMUTABLE */);
            ((android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE))
                    .setExact(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 400, pi);
        } catch (Exception ignored) { }
    }

    public static final class UpdateReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { c.startActivity(confirm); } catch (Exception ignored) { }
                }
            }
        }
    }
}

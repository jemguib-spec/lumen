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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Mise à jour automatique depuis les « Releases » du dépôt GitHub de Lumen.
 * Une fois par jour (quand Lumen est à l'écran) : lecture de la dernière release, téléchargement de
 * Lumen.apk si la version est plus récente, puis contrôles : fichier APK, même nom d'appli, version
 * plus récente et MÊME CLÉ DE SIGNATURE que Lumen installé. Android demande ensuite une confirmation
 * (une appli ne peut pas s'installer seule sans l'accord de l'utilisateur).
 */
final class Updater {
    interface Listener { void onUpdateState(); }

    private static final long DAY = 24L * 3600 * 1000;
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
        if (busy) return "Recherche…";
        if (ready() != null) return prefs.updateReadyVersion() + " prête · OK pour installer";
        return state.isEmpty() ? "Version " + currentVersion() : state;
    }

    private File dir() {
        File d = new File(ctx.getFilesDir(), "update");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Vérifie si une mise à jour existe (au plus une fois par jour, sauf demande explicite). */
    void check(final boolean force) {
        if (busy) return;
        if (!force && (!prefs.autoUpdate() || System.currentTimeMillis() - prefs.lastUpdateCheck() < DAY)) return;
        busy = true;
        notifyState();
        new Thread(new Runnable() {
            @Override public void run() {
                try { state = run1(); }
                catch (Exception e) { state = "Vérification impossible · réessaie plus tard"; }
                finally {
                    busy = false;
                    prefs.setLastUpdateCheck(System.currentTimeMillis());
                    notifyState();
                }
            }
        }, "lumen-update").start();
    }

    private String run1() throws Exception {
        String mine = currentVersion();
        JSONObject r = Net.getJson(NetPolicy.https(UpdatePolicy.LATEST));
        if (r.optBoolean("draft") || r.optBoolean("prerelease")) return "Lumen est à jour (" + mine + ")";
        String v = UpdatePolicy.version(r.optString("tag_name"));
        if (v == null || UpdatePolicy.compare(v, mine) <= 0) return "Lumen est à jour (" + mine + ")";
        if (ready() != null && v.equals(prefs.updateReadyVersion())) return "";
        JSONArray assets = r.optJSONArray("assets");
        String url = null;
        long size = 0;
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            if (UpdatePolicy.ASSET_NAME.equals(a.optString("name"))) { url = a.optString("browser_download_url"); size = a.optLong("size"); }
        }
        if (!UpdatePolicy.assetUrlOk(url) || size <= 0 || size > UpdatePolicy.MAX_BYTES) return "Mise à jour " + v + " sans fichier valable";
        File tmp = new File(dir(), "download.apk");
        Net.download(url, tmp, UpdatePolicy.MAX_BYTES, null);
        String why = verify(tmp, v);
        if (why != null) { tmp.delete(); return why; }
        File out = new File(dir(), "Lumen.apk");
        out.delete();
        if (!tmp.renameTo(out)) { tmp.delete(); return "Écriture impossible"; }
        prefs.setUpdateReadyVersion(v);
        return "";
    }

    /** null si l'APK est sûr à proposer, sinon la raison du refus. */
    @SuppressWarnings("deprecation")
    private String verify(File apk, String v) {
        try (InputStream in = new FileInputStream(apk)) {
            byte[] head = new byte[4];
            if (in.read(head) != 4 || !UpdatePolicy.looksLikeApk(head)) return "Fichier refusé (ce n'est pas une appli)";
        } catch (Exception e) { return "Fichier illisible"; }
        PackageManager pm = ctx.getPackageManager();
        PackageInfo got = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (got == null) return "Fichier refusé (appli illisible)";
        if (!ctx.getPackageName().equals(got.packageName)) return "Fichier refusé (autre appli)";
        try {
            PackageInfo me = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES);
            if (got.versionCode <= me.versionCode) return "Lumen est à jour (" + me.versionName + ")";
            if (got.signatures == null || me.signatures == null || got.signatures.length == 0
                    || !sameSigners(got.signatures, me.signatures)) return "Mise à jour refusée : signature différente";
        } catch (Exception e) { return "Vérification impossible"; }
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
            state = "Installation impossible";
            notifyState();
            return false;
        }
    }

    private void notifyState() {
        main.post(new Runnable() { @Override public void run() { if (listener != null) listener.onUpdateState(); } });
    }

    /** Résultat de l'installation : si Android demande une confirmation, on affiche sa fenêtre. */
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

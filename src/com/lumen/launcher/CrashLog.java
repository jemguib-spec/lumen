package com.lumen.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Journal d'erreurs local : si Lumen plante, l'erreur est notée dans un fichier sur la TV (jamais
 * envoyée sur Internet), visible depuis la page téléphone et effaçable dans les réglages.
 * Si Lumen plante deux fois de suite peu après son démarrage, il repart en mode sans échec :
 * paysages Lumen fixes, sans vidéo ni animation.
 */
final class CrashLog {
    private CrashLog() { }

    private static final String FILE = "crash.log";
    private static final int MAX_BYTES = 64 * 1024;
    private static final String SEP = "\n────────\n";
    static volatile boolean safeModeJustStarted;
    private static boolean installed;
    private static long startedAt;

    static void install(final Context c) {
        final Context app = c.getApplicationContext();
        startedAt = SystemClock.uptimeMillis();
        SharedPreferences sp = app.getSharedPreferences("lumen_crash", Context.MODE_PRIVATE);
        if (sp.getInt("quick", 0) >= 2) enterSafeMode(app, sp);
        if (installed) return;
        installed = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try { record(app, t, e); } catch (Throwable ignored) { }
                if (previous != null) previous.uncaughtException(t, e);
            }
        });
        // une minute sans plantage : le compteur de plantages rapides repart à zéro
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                app.getSharedPreferences("lumen_crash", Context.MODE_PRIVATE).edit().putInt("quick", 0).apply();
            }
        }, 60_000);
    }

    /** Paysages Lumen fixes, sans vidéo ni animation, jusqu'à ce que l'utilisateur change le réglage. */
    private static void enterSafeMode(Context app, SharedPreferences sp) {
        Prefs p = new Prefs(app);
        p.setSource(Prefs.SRC_LUMEN);
        p.setLivingScenes(false);
        sp.edit().putInt("quick", 0).commit();
        safeModeJustStarted = true;
    }

    private static void record(Context app, Thread t, Throwable e) {
        SharedPreferences sp = app.getSharedPreferences("lumen_crash", Context.MODE_PRIVATE);
        boolean quick = SystemClock.uptimeMillis() - startedAt < 30_000;
        sp.edit().putInt("quick", quick ? sp.getInt("quick", 0) + 1 : 0).putInt("count", sp.getInt("count", 0) + 1).commit();
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String[] lines = sw.toString().split("\n");
        StringBuilder b = new StringBuilder();
        String version = "?";
        try { version = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName; } catch (Exception ignored) { }
        b.append(new SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale.FRANCE).format(new Date()))
                .append(" · Lumen ").append(version).append(" · ").append(t.getName()).append('\n');
        for (int i = 0; i < Math.min(lines.length, 40); i++) b.append(lines[i]).append('\n');
        String old = read(app);
        String all = b + SEP + old;
        if (all.length() > MAX_BYTES) all = all.substring(0, MAX_BYTES);
        try (FileOutputStream o = new FileOutputStream(new File(app.getFilesDir(), FILE))) {
            o.write(all.getBytes("UTF-8"));
        } catch (Exception ignored) { }
    }

    private static final java.util.Set<String> noted = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** Note informative (pas un plantage), une seule fois par message et par démarrage. */
    static void note(Context c, String msg) {
        if (msg == null || !noted.add(msg) || noted.size() > 40) return;
        Context app = c.getApplicationContext();
        String line = new SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale.FRANCE).format(new Date()) + " · info · " + msg + "\n";
        synchronized (CrashLog.class) {
            String all = line + SEP + read(app);
            if (all.length() > MAX_BYTES) all = all.substring(0, MAX_BYTES);
            try (FileOutputStream o = new FileOutputStream(new File(app.getFilesDir(), FILE))) { o.write(all.getBytes("UTF-8")); }
            catch (Exception ignored) { }
        }
    }

    /** Contenu du journal (le plus récent en premier). */
    static String read(Context c) {
        File f = new File(c.getApplicationContext().getFilesDir(), FILE);
        if (!f.exists() || f.length() == 0) return "";
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), MAX_BYTES)];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return new String(b, 0, off, "UTF-8");
        } catch (Exception e) { return ""; }
    }

    static int count(Context c) {
        String s = read(c);
        if (s.isEmpty()) return 0;
        return s.split(SEP, -1).length - (s.endsWith(SEP) ? 1 : 0);
    }

    static void clear(Context c) {
        new File(c.getApplicationContext().getFilesDir(), FILE).delete();
        c.getApplicationContext().getSharedPreferences("lumen_crash", Context.MODE_PRIVATE).edit().clear().apply();
    }
}

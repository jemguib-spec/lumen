package com.lumen.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Choreographer;
import android.widget.TextView;

import java.io.RandomAccessFile;

/**
 * Mesures (Réglages › Système › Mesures) : temps de démarrage, images par seconde, mémoire,
 * processeur utilisé par Lumen et type de fond affiché. Tout reste sur la TV, rien n'est envoyé.
 */
final class Perf {
    private Perf() { }

    /** Démarrage : du lancement du processus à la première image affichée. */
    static long startupMs = -1;
    private static long createdAt;

    static void onCreate() { createdAt = SystemClock.uptimeMillis(); }

    static void onFirstFrame() {
        if (startupMs >= 0) return;
        long start = createdAt;
        try { // Android 7+ : heure exacte de démarrage du processus
            start = (Long) android.os.Process.class.getMethod("getStartUptimeMillis").invoke(null);
        } catch (Throwable ignored) { }
        startupMs = SystemClock.uptimeMillis() - start;
    }

    /** Petit texte en haut à gauche, mis à jour chaque seconde. */
    static final class Overlay extends TextView implements Choreographer.FrameCallback {
        private final Handler h = new Handler(Looper.getMainLooper());
        private final MainActivity a;
        private int frames;
        private long lastCpu = -1, lastWall;
        private boolean on;
        private long pssAt;
        private int pssMb = -1;

        Overlay(MainActivity a) {
            super(a);
            this.a = a;
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * a.unit);
            setTypeface(Fonts.get("sans-serif-medium", Typeface.NORMAL));
            setTextColor(0xF2FFFFFF);
            setBackgroundColor(0xB3000000);
            setPadding(a.u(18), a.u(12), a.u(18), a.u(12));
            setLineSpacing(0, 1.15f);
            setFocusable(false);
        }

        void setOn(boolean v) {
            if (on == v) return;
            on = v;
            setVisibility(v ? VISIBLE : GONE);
            h.removeCallbacksAndMessages(null);
            if (v) { frames = 0; Choreographer.getInstance().postFrameCallback(this); h.post(tick); }
        }

        @Override public void doFrame(long t) { frames++; if (on) Choreographer.getInstance().postFrameCallback(this); }

        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!on) return;
                long now = SystemClock.uptimeMillis();
                Runtime rt = Runtime.getRuntime();
                long javaMb = (rt.totalMemory() - rt.freeMemory()) >> 20, maxMb = rt.maxMemory() >> 20;
                long nativeMb = Debug.getNativeHeapAllocatedSize() >> 20;
                if (now - pssAt > 5000) { pssAt = now; pssMb = (int) (Debug.getPss() >> 10); } // mesure plus lourde : toutes les 5 s
                String cpu = cpu(now);
                WallItem w = a.wall.current();
                String kind = w == null ? "—" : w.kind == WallItem.VIDEO_FILE ? "vidéo" : w.kind == WallItem.ANIM ? "paysage animé"
                        : w.kind == WallItem.IMAGE_ASSET && LivingScene.handles(w.path) && a.prefs.livingScenes() ? "paysage Lumen animé" : "photo";
                setText("Démarrage " + (startupMs < 0 ? "…" : startupMs + " ms")
                        + "\nImages/s " + frames
                        + "\nMémoire " + (pssMb < 0 ? "…" : pssMb + " Mo") + "  (Java " + javaMb + "/" + maxMb + " · natif " + nativeMb + ")"
                        + "\nProcesseur " + cpu
                        + "\nFond : " + kind);
                frames = 0;
                h.postDelayed(this, 1000);
            }
        };

        /** Part du processeur utilisée par Lumen (tous cœurs confondus), d'après /proc/self/stat. */
        private String cpu(long now) {
            try (RandomAccessFile f = new RandomAccessFile("/proc/self/stat", "r")) {
                String line = f.readLine();
                String[] p = line.substring(line.lastIndexOf(')') + 2).split(" ");
                long ticks = Long.parseLong(p[11]) + Long.parseLong(p[12]); // utime + stime
                String out = "…";
                if (lastCpu >= 0 && now > lastWall) {
                    double sec = (ticks - lastCpu) / 100.0, wall = (now - lastWall) / 1000.0;
                    int cores = Runtime.getRuntime().availableProcessors();
                    out = Math.round(100 * sec / wall / cores) + " %";
                }
                lastCpu = ticks;
                lastWall = now;
                return out;
            } catch (Exception e) { return "indisponible"; }
        }
    }

    static Overlay overlay(Context c) { return c instanceof MainActivity ? new Overlay((MainActivity) c) : null; }
}

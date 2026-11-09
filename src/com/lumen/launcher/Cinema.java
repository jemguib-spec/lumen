// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mode cinéma avec Home Assistant : quand une appli de films (Netflix, Plex…) ouverte depuis Lumen
 * se met à jouer, Lumen lance la scène « film » (lumières tamisées) ; en pause prolongée ou au retour
 * sur l'accueil, la scène « pause » (lumières qui reviennent).
 *
 * Détection : Android dit seulement si un son de type média est en cours (aucune autorisation,
 * rien d'autre n'est lu). Vérifié toutes les 2 s, uniquement après l'ouverture d'une appli choisie.
 */
final class Cinema {
    /** Appli de films reconnues par défaut. */
    static final String[] DEFAULT_APPS = {"com.netflix.ninja", "com.plexapp.android", "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom", "com.canal.android.canal", "tv.molotov.app", "com.apple.atve.androidtv.appletv",
            "com.google.android.youtube.tv", "fr.francetv.pluzz", "com.crunchyroll.crunchyroid", "com.google.android.videos"};

    private static final long TICK = 2000, MAX_WATCH = 6 * 3600_000L;
    private static final int START_TICKS = 3, PAUSE_TICKS = 8;

    private final Context ctx;
    private final Prefs prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean watching, filmOn;
    private int playing, silent;
    private long since;

    Cinema(Context c, Prefs p) { ctx = c.getApplicationContext(); prefs = p; }

    boolean enabled() { return prefs.haConfigured() && !prefs.haFilmScene().isEmpty(); }

    /** Une appli vient d'être ouverte depuis Lumen. */
    void onLaunch(String pkg) {
        if (!enabled() || !prefs.haCinemaApps().contains(pkg)) return;
        watching = true;
        filmOn = false;
        playing = silent = 0;
        since = System.currentTimeMillis();
        if (prefs.haCinemaOnOpen()) { filmOn = true; Cinema.this.run(prefs.haFilmScene()); }
        h.removeCallbacks(tick);
        h.postDelayed(tick, TICK);
    }

    /** Retour sur Lumen : fin de la séance. */
    void onHome() {
        if (!watching) return;
        watching = false;
        h.removeCallbacks(tick);
        if (filmOn) run(prefs.haPauseScene());
        filmOn = false;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!watching) return;
            if (System.currentTimeMillis() - since > MAX_WATCH) { watching = false; return; }
            boolean active = false;
            try { active = ((AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE)).isMusicActive(); } catch (Exception ignored) { }
            if (active) { playing++; silent = 0; } else { silent++; playing = 0; }
            if (!filmOn && playing >= START_TICKS) { filmOn = true; Cinema.this.run(prefs.haFilmScene()); }
            else if (filmOn && silent >= PAUSE_TICKS) { filmOn = false; Cinema.this.run(prefs.haPauseScene()); }
            h.postDelayed(this, TICK);
        }
    };

    /** Lance une scène ou un script Home Assistant (en arrière-plan). */
    void run(final String entityId) {
        if (entityId == null || entityId.isEmpty() || !HomeAssistant.validEntityId(entityId)) return;
        final String url = prefs.haUrl(), token = prefs.haToken();
        io.execute(new Runnable() {
            @Override public void run() {
                try { HomeAssistant.call(url, token, new HomeAssistant.Entity(entityId, entityId, "", -1)); }
                catch (Exception e) { CrashLog.note(ctx, "Home Assistant (cinéma) : " + e); }
            }
        });
    }
}

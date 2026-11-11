// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Sons d'interface très discrets, synthétisés à la demande (aucun fichier audio embarqué, voir SoundDesign) :
 * un « tic » feutré au déplacement, un « toc » doux à la validation. Jeu de sons et volume réglables
 * (Réglages › Sons), sans toucher au volume de la TV.
 */
final class Sounds {
    private static Sounds instance;
    private final Context ctx;
    private final SoundPool pool;
    private final int[] ids = new int[SoundDesign.COUNT];
    private final Handler h = new Handler(Looper.getMainLooper());
    private int theme = -1, volume = SoundDesign.DEFAULT_VOLUME;
    private boolean enabled = true;
    private long lastTick;

    static Sounds get(Context c) {
        if (instance == null) instance = new Sounds(c.getApplicationContext());
        return instance;
    }

    static Sounds peek() { return instance; }

    private Sounds(Context c) {
        ctx = c;
        pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
    }

    /** Reprend les réglages du profil actif : sons activés, jeu de sons, volume. */
    void apply(Prefs p) {
        enabled = p.sounds();
        volume = SoundDesign.volume(p.soundVolume());
        setTheme(p.soundTheme());
    }

    void setEnabled(boolean e) { enabled = e; }

    /** Change de jeu de sons : les anciens sont libérés, les nouveaux synthétisés (quelques millisecondes). */
    private void setTheme(int t) {
        t = SoundDesign.theme(t);
        if (t == theme) return;
        for (int i = 0; i < ids.length; i++) if (ids[i] != 0) { pool.unload(ids[i]); ids[i] = 0; }
        theme = t;
        for (int i = 0; i < ids.length; i++) ids[i] = pool.load(write(ctx, SoundDesign.fileName(t, i), SoundDesign.synth(t, i)), 1);
    }

    private void play(int sound) {
        if (theme < 0) setTheme(SoundDesign.CRISTAL); // avant la lecture des réglages
        float g = SoundDesign.gain(sound, volume);
        pool.play(ids[sound], g, g, 0, 0, 1f);
    }

    void tick() {
        long now = System.currentTimeMillis();
        if (!enabled || now - lastTick < 45) return; // pas de crépitement en défilement rapide
        lastTick = now;
        play(SoundDesign.TICK);
    }

    void tock() { if (enabled) play(SoundDesign.TOCK); }
    /** Retour : deux notes qui descendent. */
    void back() { if (enabled) play(SoundDesign.BACK); }
    /** Ouverture d'une appli : deux notes qui montent. */
    void open() { if (enabled) play(SoundDesign.OPEN); }
    /** Réveil du mode veille : accord qui monte lentement et s'éteint. */
    void wake() { if (enabled) play(SoundDesign.WAKE); }

    private final Runnable preview = new Runnable() {
        @Override public void run() { play(SoundDesign.OPEN); }
    };

    /**
     * Fait entendre le jeu et le volume choisis, même sons coupés (on vient de demander à écouter).
     * Un peu après le clic, le temps que les nouveaux sons soient chargés ; des appuis rapprochés n'en jouent qu'un.
     */
    void preview() {
        h.removeCallbacks(preview);
        h.postDelayed(preview, 220);
    }

    private static String write(Context c, String name, short[] pcm) {
        File f = new File(c.getCacheDir(), name);
        try (OutputStream o = new FileOutputStream(f)) {
            int dataLen = pcm.length * 2, rate = 44100;
            byte[] h = new byte[44];
            put(h, 0, "RIFF"); le32(h, 4, 36 + dataLen); put(h, 8, "WAVE"); put(h, 12, "fmt ");
            le32(h, 16, 16); le16(h, 20, 1); le16(h, 22, 1); le32(h, 24, rate); le32(h, 28, rate * 2);
            le16(h, 32, 2); le16(h, 34, 16); put(h, 36, "data"); le32(h, 40, dataLen);
            o.write(h);
            byte[] d = new byte[dataLen];
            for (int i = 0; i < pcm.length; i++) { d[i * 2] = (byte) pcm[i]; d[i * 2 + 1] = (byte) (pcm[i] >> 8); }
            o.write(d);
        } catch (Exception ignored) { }
        return f.getAbsolutePath();
    }

    private static void put(byte[] b, int off, String s) { for (int i = 0; i < 4; i++) b[off + i] = (byte) s.charAt(i); }
    private static void le32(byte[] b, int off, int v) { for (int i = 0; i < 4; i++) b[off + i] = (byte) (v >> (8 * i)); }
    private static void le16(byte[] b, int off, int v) { b[off] = (byte) v; b[off + 1] = (byte) (v >> 8); }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Sons d'interface très discrets, synthétisés au premier lancement (aucun fichier audio embarqué) :
 * un « tic » feutré au déplacement, un « toc » doux à la validation.
 */
final class Sounds {
    private static Sounds instance;
    private final SoundPool pool;
    private final int tick, tock, back, open, wake;
    private boolean enabled = true;
    private long lastTick;

    static Sounds get(Context c) {
        if (instance == null) instance = new Sounds(c.getApplicationContext());
        return instance;
    }

    static Sounds peek() { return instance; }

    private Sounds(Context c) {
        pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        // une seule famille de timbres, accordée en la majeur, très feutrée (versions « v2 » des fichiers)
        tick = pool.load(write(c, "tick2.wav", tone(0.03f, 0.002f, 70f, 0.055f, 1760f)), 1);
        tock = pool.load(write(c, "tock2.wav", tone(0.12f, 0.004f, 30f, 0.10f, 880f, 1320f)), 1);
        back = pool.load(write(c, "back2.wav", concat(tone(0.07f, 0.004f, 40f, 0.07f, 1108.7f), tone(0.11f, 0.004f, 32f, 0.07f, 880f))), 1);
        open = pool.load(write(c, "open2.wav", concat(tone(0.07f, 0.004f, 40f, 0.08f, 880f), tone(0.16f, 0.004f, 22f, 0.08f, 1318.5f, 1760f))), 1);
        wake = pool.load(write(c, "wake2.wav", tone(1.4f, 0.35f, 2.6f, 0.06f, 440f, 554.4f, 659.3f)), 1);
    }

    void setEnabled(boolean e) { enabled = e; }

    void tick() {
        long now = System.currentTimeMillis();
        if (!enabled || now - lastTick < 45) return; // pas de crépitement en défilement rapide
        lastTick = now;
        pool.play(tick, 0.5f, 0.5f, 0, 0, 1f);
    }

    void tock() { if (enabled) pool.play(tock, 0.6f, 0.6f, 0, 0, 1f); }
    /** Retour : deux notes qui descendent. */
    void back() { if (enabled) pool.play(back, 0.5f, 0.5f, 0, 0, 1f); }
    /** Ouverture d'une appli : deux notes qui montent. */
    void open() { if (enabled) pool.play(open, 0.6f, 0.6f, 0, 0, 1f); }
    /** Réveil du mode veille : accord qui monte lentement et s'éteint. */
    void wake() { if (enabled) pool.play(wake, 0.5f, 0.5f, 0, 0, 1f); }

    /** Sons doux : partiels sinusoïdaux (le premier dominant), attaque réglable, décroissance exponentielle. */
    static short[] tone(float seconds, float attack, float decay, float amp, float... freqs) {
        int rate = 44100, n = (int) (rate * seconds);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) rate;
            float env = (float) Math.exp(-t * decay) * Math.min(1f, t / attack);
            env *= Math.min(1f, (n - i) / (rate * 0.004f)); // fin sans clic
            double v = 0, w = 0;
            for (int k = 0; k < freqs.length; k++) { double g = k == 0 ? 1 : 0.35 / k; v += Math.sin(2 * Math.PI * freqs[k] * t) * g; w += g; }
            out[i] = (short) (v / w * env * amp * 32767);
        }
        return out;
    }

    static short[] concat(short[] a, short[] b) {
        short[] o = new short[a.length + b.length];
        System.arraycopy(a, 0, o, 0, a.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
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

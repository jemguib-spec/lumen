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
    private final int tick, tock;
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
        tick = pool.load(write(c, "tick.wav", synth(0.014f, 2300f, 3400f, 0.10f, 380f)), 1);
        tock = pool.load(write(c, "tock.wav", synth(0.06f, 880f, 1320f, 0.16f, 70f)), 1);
    }

    void setEnabled(boolean e) { enabled = e; }

    void tick() {
        long now = System.currentTimeMillis();
        if (!enabled || now - lastTick < 45) return; // pas de crépitement en défilement rapide
        lastTick = now;
        pool.play(tick, 0.5f, 0.5f, 0, 0, 1f);
    }

    void tock() { if (enabled) pool.play(tock, 0.6f, 0.6f, 0, 0, 1f); }

    /** Sinusoïde + harmonique avec attaque douce et décroissance exponentielle. */
    private static short[] synth(float seconds, float f1, float f2, float amp, float decay) {
        int rate = 44100, n = (int) (rate * seconds);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) rate;
            float env = (float) Math.exp(-t * decay) * Math.min(1f, t * 2000f);
            double v = Math.sin(2 * Math.PI * f1 * t) * 0.8 + Math.sin(2 * Math.PI * f2 * t) * 0.2;
            out[i] = (short) (v * env * amp * 32767);
        }
        return out;
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

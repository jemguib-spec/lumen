// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Jeux de sons de l'interface et niveaux de volume. Tout est synthétisé (aucun fichier audio embarqué) :
 * chaque jeu décrit ses cinq sons (déplacement, validation, retour, ouverture, réveil). Java pur, testé.
 */
final class SoundDesign {
    private SoundDesign() { }

    static final int RATE = 44100;
    static final int TICK = 0, TOCK = 1, BACK = 2, OPEN = 3, WAKE = 4, COUNT = 5;

    /** Jeux de sons ; le premier est celui d'origine de Lumen. */
    static final int CRISTAL = 0, BOIS = 1, GOUTTES = 2, OUATE = 3;
    static final String[] THEME_NAMES = {"Cristal", "Bois", "Gouttes", "Ouate"};

    /** Volume des sons de Lumen seulement (celui de la TV, des applis et des vidéos ne change pas). */
    static final String[] VOLUME_NAMES = {"Très bas", "Bas", "Normal", "Fort", "Très fort"};
    static final float[] VOLUME_GAINS = {0.3f, 0.55f, 1f, 1.35f, 1.65f};
    static final int DEFAULT_VOLUME = 2;

    /** Niveau de chaque son au volume « Normal » (le réveil et le déplacement restent en retrait). */
    private static final float[] BASE = {0.5f, 0.6f, 0.5f, 0.6f, 0.5f};

    static int theme(int t) { return t >= 0 && t < THEME_NAMES.length ? t : CRISTAL; }
    static int volume(int v) { return v >= 0 && v < VOLUME_NAMES.length ? v : DEFAULT_VOLUME; }

    /** Niveau de lecture d'un son (0..1) pour un réglage de volume. */
    static float gain(int sound, int volume) { return Math.min(1f, BASE[sound] * VOLUME_GAINS[volume(volume)]); }

    /** Nom du fichier en cache : change avec le jeu (et avec sa version, si on retouche les timbres). */
    static String fileName(int theme, int sound) {
        String[] n = {"tick", "tock", "back", "open", "wake"};
        return "ui-" + theme(theme) + "-" + n[sound] + "-v1.wav";
    }

    /** Échantillons (mono, 16 bits, 44,1 kHz) d'un son d'un jeu. */
    static short[] synth(int theme, int sound) {
        switch (theme(theme)) {
            case BOIS: // marimba : fondamentale et partiel au double octave, attaque sèche, en sol majeur
                switch (sound) {
                    case TICK: return tone(0.035f, 0.001f, 110f, 0.07f, 587.3f, 2349f);
                    case TOCK: return tone(0.14f, 0.002f, 38f, 0.11f, 392f, 1568f);
                    case BACK: return concat(tone(0.08f, 0.002f, 45f, 0.08f, 523.3f, 2093f), tone(0.12f, 0.002f, 36f, 0.08f, 392f, 1568f));
                    case OPEN: return concat(tone(0.08f, 0.002f, 45f, 0.08f, 392f, 1568f), tone(0.18f, 0.002f, 26f, 0.09f, 587.3f, 2349f));
                    default: return tone(1.4f, 0.3f, 2.4f, 0.06f, 196f, 293.7f, 392f);
                }
            case GOUTTES: // gouttes d'eau : une note qui glisse vers l'aigu (ou le grave pour le retour)
                switch (sound) {
                    case TICK: return glide(0.04f, 0.002f, 80f, 0.05f, 900f, 1500f);
                    case TOCK: return glide(0.12f, 0.003f, 32f, 0.09f, 500f, 1100f);
                    case BACK: return concat(glide(0.07f, 0.003f, 45f, 0.07f, 1300f, 900f), glide(0.11f, 0.003f, 32f, 0.07f, 1000f, 650f));
                    case OPEN: return concat(glide(0.07f, 0.003f, 45f, 0.07f, 600f, 900f), glide(0.16f, 0.003f, 24f, 0.08f, 800f, 1400f));
                    default: return tone(1.5f, 0.4f, 2.4f, 0.055f, 523.3f, 659.3f, 784f);
                }
            case OUATE: // très doux : plus grave, attaque lente, presque chuchoté
                switch (sound) {
                    case TICK: return tone(0.04f, 0.008f, 60f, 0.04f, 660f);
                    case TOCK: return tone(0.14f, 0.012f, 26f, 0.07f, 440f, 660f);
                    case BACK: return concat(tone(0.08f, 0.01f, 36f, 0.05f, 554.4f), tone(0.12f, 0.01f, 28f, 0.05f, 440f));
                    case OPEN: return concat(tone(0.08f, 0.01f, 36f, 0.06f, 440f), tone(0.18f, 0.01f, 20f, 0.06f, 659.3f, 880f));
                    default: return tone(1.6f, 0.5f, 2.2f, 0.055f, 220f, 277.2f, 329.6f);
                }
            default: // Cristal : la famille d'origine, accordée en la majeur, très feutrée
                switch (sound) {
                    case TICK: return tone(0.03f, 0.002f, 70f, 0.055f, 1760f);
                    case TOCK: return tone(0.12f, 0.004f, 30f, 0.10f, 880f, 1320f);
                    case BACK: return concat(tone(0.07f, 0.004f, 40f, 0.07f, 1108.7f), tone(0.11f, 0.004f, 32f, 0.07f, 880f));
                    case OPEN: return concat(tone(0.07f, 0.004f, 40f, 0.08f, 880f), tone(0.16f, 0.004f, 22f, 0.08f, 1318.5f, 1760f));
                    default: return tone(1.4f, 0.35f, 2.6f, 0.06f, 440f, 554.4f, 659.3f);
                }
        }
    }

    /** Enveloppe : attaque réglable, décroissance exponentielle, fin sans clic. */
    private static float env(int i, int n, float attack, float decay) {
        float t = i / (float) RATE;
        float e = (float) Math.exp(-t * decay) * Math.min(1f, t / attack);
        return e * Math.min(1f, (n - i) / (RATE * 0.004f));
    }

    /** Sons doux : partiels sinusoïdaux (le premier dominant). */
    static short[] tone(float seconds, float attack, float decay, float amp, float... freqs) {
        int n = (int) (RATE * seconds);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            double v = 0, w = 0;
            for (int k = 0; k < freqs.length; k++) { double g = k == 0 ? 1 : 0.35 / k; v += Math.sin(2 * Math.PI * freqs[k] * t) * g; w += g; }
            out[i] = (short) (v / w * env(i, n, attack, decay) * amp * 32767);
        }
        return out;
    }

    /** Note dont la hauteur glisse de from à to (Hz) sur les trois premiers cinquièmes du son. */
    static short[] glide(float seconds, float attack, float decay, float amp, float from, float to) {
        int n = (int) (RATE * seconds);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float k = Math.min(1f, i / (n * 0.6f));
            phase += 2 * Math.PI * from * Math.pow(to / from, k) / RATE;
            out[i] = (short) (Math.sin(phase) * env(i, n, attack, decay) * amp * 32767);
        }
        return out;
    }

    static short[] concat(short[] a, short[] b) {
        short[] o = new short[a.length + b.length];
        System.arraycopy(a, 0, o, 0, a.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }
}

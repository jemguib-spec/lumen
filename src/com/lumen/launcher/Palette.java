// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Couleurs de l'interface, nommées et en un seul endroit. Les textes secondaires ont un plancher
 * d'opacité (lisibles sur un paysage de jour) ; « Contraste renforcé » remonte tout d'un cran.
 * Les dessins (paysages, météo, verre) gardent leurs propres couleurs : ici, seulement l'interface.
 */
final class Palette {
    private Palette() { }

    /** Texte principal, texte secondaire (valeurs, légendes), texte discret (pieds de page). */
    static int TEXT = 0xF5FFFFFF, TEXT_2 = 0xA6FFFFFF, TEXT_3 = 0x80FFFFFF;
    /** Surface de verre des panneaux et fond pleinement opaque (contraste renforcé). */
    static int SURFACE = 0x2E14161E, SURFACE_SOLID = 0xD90C0D12;
    /** Ligne ou bouton sélectionné : blanc cassé, texte sombre dessus. */
    static final int SELECTED_NEUTRAL = 0xFFF5F5F7, ON_SELECTED = 0xFF111216, ON_SELECTED_2 = 0x99111216;
    /** Suit le fond d'écran quand « Couleurs accordées au fond » est actif (voir tint). */
    static int SELECTED = SELECTED_NEUTRAL;
    /** Séparations fines, voile derrière les panneaux, ombre des textes posés sur le fond. */
    static int LINE = 0x1AFFFFFF, DIM = 0x6604040A;
    static final int SHADOW = 0x55000000;
    /** Accent (interrupteur allumé), même vert que la page du téléphone ; actions qui retirent. */
    static final int ACCENT_NEUTRAL = 0xFF7EE2A8, DANGER = 0xFFFF8A80, DANGER_SELECTED = 0xFFFF6B5E;
    static int ACCENT = ACCENT_NEUTRAL;

    /**
     * Accorde la sélection et l'accent au fond affiché : pixels = le fond (réduit, flouté), ou null
     * pour revenir aux couleurs neutres. La sélection reste un blanc, à peine nuancé.
     */
    static void tint(int[] pixels) {
        int[] t = pixels == null ? null : tintFor(pixels);
        SELECTED = t == null ? SELECTED_NEUTRAL : t[0];
        ACCENT = t == null ? ACCENT_NEUTRAL : t[1];
    }

    /**
     * { sélection, accent } pour une image, ou null si elle n'a pas de couleur dominante (grise, ou
     * trop de teintes qui s'annulent). Teinte moyenne pondérée par la saturation et la luminosité ;
     * plus la dominante est franche, plus la nuance est marquée. L'accent n'est jamais rouge : le
     * rouge reste la couleur des actions qui retirent.
     */
    static int[] tintFor(int[] pixels) {
        double x = 0, y = 0, light = 0;
        for (int c : pixels) {
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            light += max;
            if (max == min) continue;
            double w = max - min, hue = Math.toRadians(hue(r, g, b, max, min)); // max - min = saturation × luminosité
            x += w * Math.cos(hue);
            y += w * Math.sin(hue);
        }
        if (light <= 0) return null;
        double strength = Math.sqrt(x * x + y * y) / light;
        if (strength < 0.05) return null;
        float k = (float) Math.min(1.0, (strength - 0.05) / 0.20);
        float h = (float) Math.toDegrees(Math.atan2(y, x));
        if (h < 0) h += 360f;
        float ha = h >= 315f || h < 25f ? 38f : h; // roses et rouges → doré
        return new int[]{hsv(h, 0.08f * k, 0.97f), mix(ACCENT_NEUTRAL, hsv(ha, 0.45f, 0.93f), k)};
    }

    private static double hue(int r, int g, int b, int max, int min) {
        double d = max - min, h;
        if (max == r) h = ((g - b) / d) % 6;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        h *= 60;
        return h < 0 ? h + 360 : h;
    }

    /** Couleur opaque à partir d'une teinte (degrés), d'une saturation et d'une luminosité (0 à 1). */
    static int hsv(float h, float s, float v) {
        float c = v * s, hp = (h % 360f) / 60f, xx = c * (1 - Math.abs(hp % 2 - 1)), m = v - c;
        float r, g, b;
        switch ((int) hp) {
            case 0: r = c; g = xx; b = 0; break;
            case 1: r = xx; g = c; b = 0; break;
            case 2: r = 0; g = c; b = xx; break;
            case 3: r = 0; g = xx; b = c; break;
            case 4: r = xx; g = 0; b = c; break;
            default: r = c; g = 0; b = xx;
        }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    /** Couleur intermédiaire entre a (t = 0) et b (t = 1), transparence comprise : pour les fondus. */
    static int mix(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int aa = Math.round(((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t), rr = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t),
                gg = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bb = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    /** Contraste renforcé : secondaires presque opaques, panneaux pleins, voile plus sombre. */
    static void apply(boolean highContrast) {
        TEXT_2 = highContrast ? 0xF2FFFFFF : 0xA6FFFFFF;
        TEXT_3 = highContrast ? 0xD9FFFFFF : 0x80FFFFFF;
        LINE = highContrast ? 0x40FFFFFF : 0x1AFFFFFF;
        DIM = highContrast ? 0xB304040A : 0x6604040A;
    }
}

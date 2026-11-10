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
    static final int SELECTED = 0xFFF5F5F7, ON_SELECTED = 0xFF111216, ON_SELECTED_2 = 0x99111216;
    /** Séparations fines, voile derrière les panneaux, ombre des textes posés sur le fond. */
    static int LINE = 0x1AFFFFFF, DIM = 0x6604040A;
    static final int SHADOW = 0x55000000;
    /** Accent (interrupteur allumé), même vert que la page du téléphone ; actions qui retirent. */
    static final int ACCENT = 0xFF7EE2A8, DANGER = 0xFFFF8A80, DANGER_SELECTED = 0xFFFF6B5E;

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

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Arrondis imbriqués : quand un élément arrondi est posé dans un autre, leurs coins restent
 * parallèles. La règle, partout dans l'interface :
 *
 *     rayon externe = rayon interne + marge (padding)
 *
 * Tous les rayons d'éléments imbriqués (cartes et leurs lignes, rails et leurs pastilles,
 * vignettes et leurs coches) viennent d'ici ; un nouvel élément imbriqué passe par {@link #outer}
 * ou {@link #inner} au lieu d'un nombre en dur. Unités : pixels de conception (écran 1920 × 1080),
 * convertis par MainActivity.u(). Les mêmes règles s'appliquent à la page du téléphone
 * (assets/phone.css, calculs « calc(rayon + marge) »).
 */
final class Radius {
    private Radius() { }

    /** Rayon d'un conteneur dont l'élément intérieur, de rayon inner, est posé à pad de ses bords. */
    static float outer(float inner, float pad) { return inner + pad; }

    /** Rayon d'un élément posé à pad des bords d'un conteneur de rayon outer (jamais négatif). */
    static float inner(float outer, float pad) { return Math.max(0f, outer - pad); }

    /** Marge à laisser entre un élément de rayon inner et le bord d'un conteneur de rayon outer. */
    static float pad(float outer, float inner) { return Math.max(0f, outer - inner); }

    // ---- panneaux de verre et leurs lignes

    /** Lignes de menu et de réglage, lignes d'information, champs de saisie, touches du code PIN. */
    static final float ROW = 24;
    /** Espace sous chaque ligne de menu (GlassUi.rowLp) : il compte dans la marge du bas. */
    static final float ROW_GAP = 6;
    /** Marge des menus, réglages, Reprendre et À l'affiche autour de leurs lignes. */
    static final float PAD = 24;
    /** Menus, réglages, Reprendre, À l'affiche : 24 + 24. */
    static final float CARD = outer(ROW, PAD);
    /** Marge des fenêtres de saisie (ville, clé, prénom, code PIN, nouveautés). */
    static final float FORM_PAD = 40;
    /** Fenêtres de saisie : 24 + 40. */
    static final float FORM = outer(ROW, FORM_PAD);
    /** Écart autour de chaque touche du code PIN ; la carte garde FORM_PAD jusqu'aux touches. */
    static final float KEY_GAP = 8;

    // ---- À l'affiche

    /** Rail Tout · Films · Séries : marge autour des pastilles. */
    static final float SEG_PAD = 4;
    /** Pastille du rail. */
    static final float SEG_ITEM = 26;
    /** Rail : 26 + 4. */
    static final float SEG = outer(SEG_ITEM, SEG_PAD);
    /** Marge haute et basse d'une ligne de titre ; l'affiche y est posée. */
    static final float POSTER_PAD = 12;
    /** Affiche dans sa ligne : 24 − 12. */
    static final float POSTER = inner(ROW, POSTER_PAD);

    // ---- vignettes avec un rond dans le coin

    /** Tuile Maison : pastille ronde du pictogramme, en haut à gauche. */
    static final float HA_ICON = 64;
    static final float HA_INSET = 16;
    /** Tuile Maison : 32 + 16. */
    static final float HA_TILE = outer(HA_ICON / 2, HA_INSET);

    /** Vignette à cocher (fonds, vidéos) : coche ronde, en haut à droite. */
    static final float CHECK = 40;
    static final float CHECK_INSET = 12;
    /** Vignette à cocher : 20 + 12. */
    static final float CHECK_CELL = outer(CHECK / 2, CHECK_INSET);

    /** Vignette du haut de l'accueil (programmes) et sa barre de progression, arrondie en entier. */
    static final float SHELF = 16;
    static final float SHELF_BAR = 6;
    /** Barre de progression : posée à 16 − 3 des bords de la vignette. */
    static final float SHELF_BAR_INSET = pad(SHELF, SHELF_BAR / 2);
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Images de profil dessinées par Lumen (Java pur, testé) : des personnages et quelques motifs,
 * tous originaux, monochromes comme le reste de l'interface. Chacun est décrit par quelques formes
 * sur un carré de 100 × 100 (le disque du profil), lues ici et tracées par AvatarDrawable sur la TV.
 *
 * Une forme par ligne (« ; ») : ton, épaisseur de trait facultative, puis
 *   c cx,cy,r — cercle ;  e cx,cy,rx,ry — ellipse ;  p M… L… Q… C… Z — tracé (coordonnées absolues).
 * Tons : F plein, S doux (un voile de la même couleur), H trou (laisse voir le disque derrière).
 * Ex. « F c50,50,20; H/3 p M40,60 Q50,66 60,60 » : un disque plein, un sourire évidé de 3 d'épaisseur.
 */
final class AvatarArt {
    private AvatarArt() { }

    static final int FULL = 0, SOFT = 1, HOLE = 2;
    /** Opacité du ton doux. */
    static final float SOFT_ALPHA = 0.42f;

    /** Aucune image : l'initiale du prénom. */
    static final int NONE = -1;

    static final String[] NAMES = {
            "Chat", "Renard", "Ours", "Lapin", "Hibou", "Panda", "Pingouin", "Grenouille",
            "Souris", "Robot", "Petit monstre", "Fantôme", "Astronaute", "Étoile", "Lune", "Montagne"};

    static final String[] ART = {
            // chat
            "F p M27,50 L29,22 L46,36 Z; F p M73,50 L71,22 L54,36 Z; H p M32,40 L32,29 L41,36 Z; S p M32,40 L32,29 L41,36 Z;"
                    + "H p M68,40 L68,29 L59,36 Z; S p M68,40 L68,29 L59,36 Z; F e50,57,26,22;"
                    + "H e40,54,3.6,5; H e60,54,3.6,5; H p M46.5,63 L53.5,63 L50,67 Z;"
                    + "F/1.6 p M27,62 L12,59 M27,67 L13,70 M73,62 L88,59 M73,67 L87,70",
            // renard
            "F p M20,40 L26,13 L45,32 Z; F p M80,40 L74,13 L55,32 Z; H p M27,33 L29,21 L38,30 Z; S p M27,33 L29,21 L38,30 Z;"
                    + "H p M73,33 L71,21 L62,30 Z; S p M73,33 L71,21 L62,30 Z;"
                    + "F p M18,40 Q50,24 82,40 Q80,58 50,80 Q20,58 18,40 Z;"
                    + "H p M22,48 Q34,58 44,60 Q50,66 56,60 Q66,58 78,48 Q72,62 50,80 Q28,62 22,48 Z;"
                    + "S p M22,48 Q34,58 44,60 Q50,66 56,60 Q66,58 78,48 Q72,62 50,80 Q28,62 22,48 Z;"
                    + "H e39,46,3.4,4.4; H e61,46,3.4,4.4; F e50,74,4.6,3.6",
            // ours
            "F c29,31,11; F c71,31,11; H c29,31,5.5; S c29,31,5.5; H c71,31,5.5; S c71,31,5.5; F c50,56,28;"
                    + "H c40,50,3.6; H c60,50,3.6; H e50,65,11,8.5; S e50,65,11,8.5; F e50,61.5,4.8,3.4; F/1.8 p M50,64 L50,69 M45,70 Q50,73 55,70",
            // lapin
            "F p M33,44 C26,26 30,4 38,4 C46,4 46,26 44,42 Z; F p M67,44 C74,26 70,4 62,4 C54,4 54,26 56,42 Z;"
                    + "H p M36,38 C32,26 34,11 38,11 C42,11 42,26 41,38 Z; S p M36,38 C32,26 34,11 38,11 C42,11 42,26 41,38 Z;"
                    + "H p M64,38 C68,26 66,11 62,11 C58,11 58,26 59,38 Z; S p M64,38 C68,26 66,11 62,11 C58,11 58,26 59,38 Z;"
                    + "F e50,62,25,22; H c41,58,3.6; H c59,58,3.6; H e50,66,3.6,2.8; H/1.8 p M50,68 L50,72 M45,73 Q50,76 55,73",
            // hibou
            "F p M24,26 L38,36 Q50,32 62,36 L76,26 L77,60 Q77,86 50,86 Q23,86 23,60 Z;"
                    + "H c38,52,12; H c62,52,12; S c38,52,12; S c62,52,12; F c38,52,5.5; F c62,52,5.5;"
                    + "H p M45,62 L55,62 L50,71 Z; H/1.8 p M38,76 Q42,79 46,76 M46,76 Q50,79 54,76 M54,76 Q58,79 62,76",
            // panda
            "S c28,30,10; S c72,30,10; F c50,55,28; H p M33,50 Q36,42 44,46 Q47,54 41,60 Q34,62 33,50 Z;"
                    + "H p M67,50 Q64,42 56,46 Q53,54 59,60 Q66,62 67,50 Z; F c40,51,2.4; F c60,51,2.4; H e50,64,4.5,3.2;"
                    + "H/1.8 p M50,66 L50,69 M45,70 Q50,73 55,70",
            // pingouin
            "S p M50,18 Q78,18 78,56 Q78,86 50,86 Q22,86 22,56 Q22,18 50,18 Z;"
                    + "F p M50,36 Q56,28 63,32 Q72,40 70,60 Q68,82 50,82 Q32,82 30,60 Q28,40 37,32 Q44,28 50,36 Z;"
                    + "H c42,44,3.2; H c58,44,3.2; H p M44,51 L56,51 L50,58 Z; F e39,88,8,3.5; F e61,88,8,3.5",
            // grenouille
            "F c34,36,12; F c66,36,12; F e50,60,32,22; H c34,36,6.5; H c66,36,6.5; F c35,35,2.6; F c67,35,2.6;"
                    + "H/3 p M32,64 Q50,76 68,64; H c44,52,1.6; H c56,52,1.6; S c28,66,5; S c72,66,5",
            // souris
            "F c27,34,15; F c73,34,15; H c27,34,9; S c27,34,9; H c73,34,9; S c73,34,9;"
                    + "F p M50,36 Q72,36 72,56 Q72,70 50,82 Q28,70 28,56 Q28,36 50,36 Z;"
                    + "H c42,55,3.3; H c58,55,3.3; H c50,75,3.6;"
                    + "F/1.6 p M34,68 L16,64 M34,72 L17,76 M66,68 L84,64 M66,72 L83,76",
            // robot
            "F/3 p M50,20 L50,32; F c50,18,4.5; F p M30,32 L70,32 Q78,32 78,40 L78,70 Q78,78 70,78 L30,78 Q22,78 22,70 L22,40 Q22,32 30,32 Z;"
                    + "F p M14,46 L22,46 L22,64 L14,64 Z; F p M78,46 L86,46 L86,64 L78,64 Z;"
                    + "H c39,50,7; H c61,50,7; F c39,50,3; F c61,50,3;"
                    + "H p M36,62 L64,62 Q66,62 66,64 L66,68 Q66,70 64,70 L36,70 Q34,70 34,68 L34,64 Q34,62 36,62 Z;"
                    + "S/1.8 p M43,62 L43,70 M50,62 L50,70 M57,62 L57,70",
            // petit monstre
            "F p M30,42 L22,16 L42,32 Z; F p M70,42 L78,16 L58,32 Z; F c50,56,28;"
                    + "H c50,48,11; S c50,48,11; F c50,48,5.5; H c52,46,1.8;"
                    + "H p M36,64 Q50,78 64,64 Z; F p M42,64.5 L45,69 L48,65.5 Z; F p M52,65.5 L55,69 L58,64.5 Z",
            // fantôme
            "F p M26,84 L26,46 Q26,18 50,18 Q74,18 74,46 L74,84 L66,77 L58,84 L50,77 L42,84 L34,77 Z;"
                    + "H e41,44,4.5,6.5; H e59,44,4.5,6.5; H e50,59,4.5,5.5; S e33,53,4,2.5; S e67,53,4,2.5",
            // astronaute
            "F p M24,90 Q24,72 50,72 Q76,72 76,90 Z; F c50,46,29; H e50,46,20,16; S e50,46,20,16;"
                    + "F/2.6 p M37,40 Q40,34 47,33; F c70,22,4; F/2.4 p M70,22 L64,28; H c50,82,3.5; S c50,82,3.5",
            // étoile
            "F p M50,16 L58.8,38 L82,39.5 L64,54.5 L70,77.5 L50,64.5 L30,77.5 L36,54.5 L18,39.5 L41.2,38 Z;"
                    + "S c80,72,3; S c22,72,2.4; S c78,20,2",
            // lune
            "F c46,50,28; H c60,40,24; S c76,68,3.2; S c82,46,2.2; S c64,80,2.4;"
                    + "S c34,58,3.6; S c30,44,2.2",
            // montagne
            "F c66,30,8; S p M14,76 L36,42 L52,62 L62,50 L86,76 Z; F p M14,76 L36,42 L48,58 L40,76 Z;"
                    + "F p M44,76 L62,50 L86,76 Z; S/2 p M14,84 L86,84",
    };

    static int count() { return ART.length; }

    static boolean valid(int index) { return index >= 0 && index < ART.length; }

    /** Reçoit les formes d'une image, dans l'ordre où elles se dessinent. */
    interface Sink {
        /** Nouvelle forme : ton, et épaisseur du trait (0 : forme pleine). */
        void begin(int tone, float stroke);
        void moveTo(float x, float y);
        void lineTo(float x, float y);
        void quadTo(float x1, float y1, float x, float y);
        void cubicTo(float x1, float y1, float x2, float y2, float x, float y);
        void close();
        /** Fin de la forme : la tracer. */
        void end();
    }

    /** Dessine l'image n° index (rien si elle n'existe pas). */
    static void draw(int index, Sink s) {
        if (!valid(index)) return;
        for (String shape : ART[index].split(";")) {
            shape = shape.trim();
            if (shape.isEmpty()) continue;
            shape(shape, s);
        }
    }

    private static final float K = 0.5523f; // arc de cercle en courbe de Bézier

    private static void shape(String def, Sink s) {
        int sp = def.indexOf(' ');
        String head = def.substring(0, sp);
        String body = def.substring(sp + 1).trim();
        int tone = head.charAt(0) == 'S' ? SOFT : head.charAt(0) == 'H' ? HOLE : FULL;
        float stroke = head.length() > 2 && head.charAt(1) == '/' ? Float.parseFloat(head.substring(2)) : 0f;
        char kind = body.charAt(0);
        String args = body.substring(1).trim();
        s.begin(tone, stroke);
        if (kind == 'c' || kind == 'e') {
            float[] v = numbers(args);
            float cx = v[0], cy = v[1], rx = v[2], ry = kind == 'e' ? v[3] : v[2];
            float kx = rx * K, ky = ry * K;
            s.moveTo(cx + rx, cy);
            s.cubicTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry);
            s.cubicTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy);
            s.cubicTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry);
            s.cubicTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy);
            s.close();
        } else if (kind == 'p') {
            path(args, s);
        } else {
            throw new IllegalArgumentException("forme inconnue : " + def);
        }
        s.end();
    }

    /** Tracé : M, L, Q, C et Z, coordonnées absolues. */
    private static void path(String d, Sink s) {
        int i = 0, n = d.length();
        while (i < n) {
            char c = d.charAt(i);
            if (c == ' ' || c == ',') { i++; continue; }
            int j = i + 1;
            while (j < n && "MLQCZ".indexOf(d.charAt(j)) < 0) j++;
            float[] v = numbers(d.substring(i + 1, j));
            switch (c) {
                case 'M': need(v, 2, d); s.moveTo(v[0], v[1]); break;
                case 'L': need(v, 2, d); for (int k = 0; k + 1 < v.length; k += 2) s.lineTo(v[k], v[k + 1]); break;
                case 'Q': need(v, 4, d); s.quadTo(v[0], v[1], v[2], v[3]); break;
                case 'C': need(v, 6, d); s.cubicTo(v[0], v[1], v[2], v[3], v[4], v[5]); break;
                case 'Z': s.close(); break;
                default: throw new IllegalArgumentException("commande inconnue « " + c + " » : " + d);
            }
            i = j;
        }
    }

    private static void need(float[] v, int k, String d) {
        if (v.length != k && !(k == 2 && v.length % 2 == 0 && v.length > 0)) throw new IllegalArgumentException("nombres attendus : " + k + " dans " + d);
    }

    private static float[] numbers(String t) {
        t = t.trim();
        if (t.isEmpty()) return new float[0];
        String[] parts = t.split("[ ,]+");
        float[] out = new float[parts.length];
        for (int k = 0; k < parts.length; k++) out[k] = Float.parseFloat(parts[k]);
        return out;
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Prévision affichée dans le volet de l'accueil : température actuelle, ciel, et les trois jours
 * suivants. Le code météo WMO d'Open-Meteo devient un mot (« Éclaircies ») et une famille de
 * pictogramme (soleil, nuages, pluie…). Rangée en cache sous une forme texte simple. Java pur, testé.
 */
final class Forecast {
    /** Familles de pictogrammes. */
    static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, RAIN = 4, SNOW = 5, STORM = 6;

    /** Mots du ciel (traduits à l'affichage). */
    static final String[] CONDITIONS = {"Ciel dégagé", "Peu nuageux", "Éclaircies", "Couvert", "Brouillard",
            "Bruine", "Pluie", "Neige", "Averses", "Averses de neige", "Orage"};

    final int temp, code;
    /** Jours à venir : aujourd'hui puis les suivants. */
    final int[] max, min, codes;
    final long time;

    Forecast(int temp, int code, int[] max, int[] min, int[] codes, long time) {
        this.temp = temp; this.code = code; this.max = max; this.min = min; this.codes = codes; this.time = time;
    }

    /** Index dans CONDITIONS, -1 si le code est inconnu. */
    static int condition(int c) {
        if (c == 0) return 0;
        if (c == 1) return 1;
        if (c == 2) return 2;
        if (c == 3) return 3;
        if (c == 45 || c == 48) return 4;
        if (c >= 51 && c <= 57) return 5;
        if (c >= 61 && c <= 67) return 6;
        if (c >= 71 && c <= 77) return 7;
        if (c >= 80 && c <= 82) return 8;
        if (c == 85 || c == 86) return 9;
        if (c >= 95) return 10;
        return -1;
    }

    /** Mot du ciel en français (« — » si inconnu). */
    static String describe(int c) { int i = condition(c); return i < 0 ? "—" : CONDITIONS[i]; }

    /** Famille de pictogramme pour un code météo. */
    static int kind(int c) {
        int i = condition(c);
        switch (i) {
            case 0: return CLEAR;
            case 1: case 2: return PARTLY;
            case 4: return FOG;
            case 5: case 6: case 8: return RAIN;
            case 7: case 9: return SNOW;
            case 10: return STORM;
            default: return CLOUDY;
        }
    }

    /** « 14° » : arrondi, sans « -0 ». */
    static String degrees(double t) {
        long r = Math.round(t);
        return (r == 0 ? 0 : r) + "°";
    }

    /** Cache : « temp;code;max,…;min,…;codes,…;heure ». */
    String compact() {
        return temp + ";" + code + ";" + join(max) + ";" + join(min) + ";" + join(codes) + ";" + time;
    }

    /** Relit le cache ; null s'il est absent ou abîmé. */
    static Forecast parse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(";", -1);
            if (p.length != 6) return null;
            int[] max = ints(p[2]), min = ints(p[3]), codes = ints(p[4]);
            if (max.length != min.length || max.length != codes.length) return null;
            return new Forecast(Integer.parseInt(p[0]), Integer.parseInt(p[1]), max, min, codes, Long.parseLong(p[5]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String join(int[] a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) b.append(','); b.append(a[i]); }
        return b.toString();
    }

    private static int[] ints(String s) {
        if (s.isEmpty()) return new int[0];
        String[] p = s.split(",");
        int[] out = new int[p.length];
        for (int i = 0; i < p.length; i++) out[i] = Integer.parseInt(p[i].trim());
        return out;
    }
}

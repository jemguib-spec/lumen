// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Bandeau d'accueil sur une seule ligne : « Bonsoir · Pluie 12° · Reprendre The Bear ».
 * Uniquement ce qui est utile maintenant ; rien d'inventé. Java pur, testé.
 */
final class Greeting {
    private Greeting() { }

    static final String SEP = "   ·   ";
    private static final int MAX_RESUME = 38;

    static String hello(int hour) {
        if (hour >= 5 && hour < 12) return "Bonjour";
        if (hour >= 12 && hour < 18) return "Bon après-midi";
        if (hour >= 18 && hour < 22) return "Bonsoir";
        return "Bonne nuit";
    }

    /** « 12° · Nuageux · Paris » (ligne météo de Lumen) → « Nuageux 12° ». */
    static String weather(String line) {
        if (line == null || line.trim().isEmpty()) return "";
        String[] p = line.split(" · ");
        if (p.length < 2 || "—".equals(p[1].trim())) return p[0].trim();
        return p[1].trim() + " " + p[0].trim();
    }

    /** « Reprendre The Bear » (titre raccourci proprement s'il est long). */
    static String resume(String title) {
        if (title == null || title.trim().isEmpty()) return "";
        String t = title.trim();
        if (t.length() > MAX_RESUME) t = t.substring(0, MAX_RESUME - 1).trim() + "…";
        return "Reprendre " + t;
    }

    /** Écran de nuit du mode veille : de nightHour jusqu'à 7 h (-1 = jamais). */
    static boolean night(int hour, int nightHour) {
        if (nightHour < 0) return false;
        return nightHour == 0 ? hour < 7 : hour >= nightHour || hour < 7;
    }

    /** time peut être vide (l'horloge est déjà affichée en haut à droite). */
    static String line(int hour, String time, String weatherLine, String resumeTitle) {
        StringBuilder b = new StringBuilder(hello(hour));
        for (String part : new String[]{time, weather(weatherLine), resume(resumeTitle)})
            if (part != null && !part.isEmpty()) b.append(SEP).append(part);
        return b.toString();
    }
}

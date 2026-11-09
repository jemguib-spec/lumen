// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Sauvegarde des réglages : liste blanche stricte des clés, de leur type et de leurs bornes.
 * Le code PIN, la clé Pexels, les mises à jour et l'historique ne sont jamais exportés ni importés :
 * un fichier de sauvegarde modifié ne peut donc ni lever une protection ni injecter autre chose.
 * Java pur, testé.
 */
final class Backup {
    private Backup() { }

    static final String FORMAT = "lumen-backup";
    static final int VERSION = 1;
    static final int NONE = 0, BOOL = 1, INT = 2, STRING = 3, SET = 4;
    static final int MAX_STRING = 8000, MAX_SET = 500, MAX_ITEM = 300;

    static final String[] BOOLS = {"ambiance_sound", "app_glow", "auto_update", "clock", "cosy_wanted", "follow_time", "idle",
            "living_scenes", "seasonal", "shelf_posters", "show_icons", "show_perf", "sounds", "tint_tiles", "top_shelf",
            "video_4k", "weather_fx", "high_contrast", "habits_on", "greeting", "ha_button"};
    static final String[] INTS = {"ambiance_kind", "ambient_min", "home_cols", "home_rows", "interval", "source", "tile_style",
            "video_disk_gb", "video_theme", "text_scale", "veille_src", "night_hour"};
    static final String[] STRINGS = {"favorites", "habits"};
    static final String[] SETS = {"cities", "hidden_apps", "hidden_walls", "video_ok", "locked_apps", "ha_pinned"};

    static int typeOf(String key) {
        if (key == null) return NONE;
        for (String k : BOOLS) if (k.equals(key)) return BOOL;
        for (String k : INTS) if (k.equals(key)) return INT;
        for (String k : STRINGS) if (k.equals(key)) return STRING;
        for (String k : SETS) if (k.equals(key)) return SET;
        return NONE;
    }

    /** Bornes raisonnables des entiers (une valeur hors bornes est ignorée). */
    static boolean intOk(String key, long v) {
        switch (key) {
            case "ambiance_kind": return v >= 0 && v < 32;
            case "ambient_min": return v >= 0 && v <= 240;
            case "home_cols": return v >= 3 && v <= 10;
            case "home_rows": return v >= 1 && v <= 4;
            case "interval": return v >= 0 && v <= 86_400;
            case "source": return v >= 0 && v < 32;
            case "tile_style": return v >= 0 && v <= 2;
            case "video_disk_gb": return v >= 0 && v <= 64;
            case "video_theme": return v >= 0 && v < 32;
            case "text_scale": return v >= 0 && v <= 2;
            case "veille_src": return v >= -2 && v < 32;
            case "night_hour": return v >= -1 && v <= 23;
            default: return false;
        }
    }

    /** Texte acceptable : longueur bornée, sans caractère de contrôle. */
    static boolean textOk(String s, int max) {
        if (s == null || s.length() > max) return false;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 0x20) return false;
        return true;
    }
}

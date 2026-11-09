// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * « À l'affiche » : films et séries du moment via TMDB (The Movie Database), avec la clé
 * personnelle de l'utilisateur (sans clé, Lumen ne contacte jamais TMDB). Java pur, testé.
 */
final class TmdbPolicy {
    private TmdbPolicy() { }

    /** Clé API v3 (32 caractères hexadécimaux) ou jeton de lecture v4 (« eyJ… »). */
    static boolean validKey(String k) {
        if (k == null) return false;
        if (k.matches("[0-9a-f]{32}")) return true;
        return k.startsWith("eyJ") && k.length() >= 60 && k.length() <= 600 && k.matches("[A-Za-z0-9._-]+");
    }

    static boolean bearer(String k) { return k != null && k.startsWith("eyJ"); }

    /** Adresse d'API complète ; la clé v3 va dans l'adresse, le jeton v4 dans l'en-tête. */
    static String url(String path, String key) {
        String u = "https://api.themoviedb.org/3" + path + (path.contains("?") ? "&" : "?") + "language=fr-FR";
        return bearer(key) ? u : u + "&api_key=" + key;
    }

    /** Applis de streaming reconnues : paquet Android → identifiant TMDB du service (France). */
    static final String[][] PROVIDERS = {
            {"com.netflix.ninja", "8", "Netflix"},
            {"com.amazon.amazonvideo.livingroom", "119", "Prime Video"},
            {"com.disney.disneyplus", "337", "Disney+"},
            {"com.canal.android.canal", "381", "Canal+"},
            {"com.apple.atve.androidtv.appletv", "350", "Apple TV+"},
            {"com.cbs.ott", "531", "Paramount+"},
            {"com.wbd.stream", "1899", "Max"},
            {"com.crunchyroll.crunchyroid", "283", "Crunchyroll"},
            {"com.plexapp.android", "538", "Plex"},
    };

    /** Services présents sur la TV (parmi les applis installées et visibles), dans l'ordre ci-dessus. */
    static List<String[]> installed(Set<String> pkgs) {
        List<String[]> out = new ArrayList<>();
        for (String[] p : PROVIDERS) if (pkgs.contains(p[0])) out.add(p);
        return out;
    }

    static String[] byProviderId(int id) {
        for (String[] p : PROVIDERS) if (Integer.parseInt(p[1]) == id) return p;
        return null;
    }
}

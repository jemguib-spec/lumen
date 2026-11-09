// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Classement des applis en rangées (page Applications) et recherche. Java pur, testé.
 * La catégorie déclarée par l'appli (Android 8+) est utilisée quand elle existe ; sinon, le nom
 * du paquet et le nom de l'appli suffisent pour les services connus.
 */
final class AppCategory {
    private AppCategory() { }

    static final int VIDEO = 0, MUSIC = 1, GAMES = 2, OTHER = 3, SOURCES = 4;
    static final String[] NAMES = {"Vidéo et TV", "Musique et radio", "Jeux", "Outils et autres", "Sources (HDMI)"};
    /** Ordre des rangées dans la page Applications. */
    static final int[] ORDER = {SOURCES, VIDEO, MUSIC, GAMES, OTHER};

    // valeurs de ApplicationInfo.category (Android 8+)
    private static final int A_GAME = 0, A_AUDIO = 1, A_VIDEO = 2, A_IMAGE = 3;

    private static final String[] VIDEO_HINTS = {"netflix", "primevideo", "amazon.avod", "amazonvideo", "plex", "crunchyroll",
            "canal", "youtube", "disney", "tv.arte", "francetv", "france.tv", "molotov", "vlc", "kodi", "twitch", "mubi",
            "tf1", "m6replay", "6play", "salto", "paramount", "hbo", ".max", "appletv", "apple.atve", "dazn", "rakuten",
            "pluto", "jellyfin", "emby", "infuse", "mx.player", "videoplayer", "video", "iptv", "stremio",
            "dailymotion", "vimeo", "bein", "rmc", "eurosport", "lequipe", "orange.tv", "sfr", "bouygues", "freebox"};
    private static final String[] MUSIC_HINTS = {"spotify", "deezer", "music", "musique", "radio", "tidal", "soundcloud",
            "qobuz", "audible", "podcast", "napster", "shazam", "sonos"};
    private static final String[] GAME_HINTS = {"game", "games", "jeu", "steamlink", "geforce", "xcloud", "xbox", "playstation",
            "retroarch", "emulator"};

    /** Rangée d'une appli. androidCategory : ApplicationInfo.category, ou -1 si inconnue. */
    static int of(String pkg, String label, int androidCategory) {
        if (androidCategory == A_VIDEO || androidCategory == A_IMAGE) return VIDEO;
        if (androidCategory == A_AUDIO) return MUSIC;
        if (androidCategory == A_GAME) return GAMES;
        String s = (pkg == null ? "" : pkg.toLowerCase(Locale.ROOT)) + " " + fold(label);
        for (String h : MUSIC_HINTS) if (s.contains(h)) return MUSIC;   // avant la vidéo : « youtube.music »
        for (String h : GAME_HINTS) if (s.contains(h)) return GAMES;
        for (String h : VIDEO_HINTS) if (s.contains(h)) return VIDEO;
        return OTHER;
    }

    /** Recherche : sans accents ni majuscules, sur le nom ou un mot du nom. */
    static boolean matches(String label, String query) {
        String q = fold(query).trim();
        if (q.isEmpty()) return true;
        String l = fold(label);
        if (l.contains(q)) return true;
        return l.replace(" ", "").replace("+", "plus").contains(q.replace(" ", "").replace("+", "plus"));
    }

    static String fold(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}

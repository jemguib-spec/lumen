package com.lumen.launcher;

import java.net.URL;
import java.util.Locale;

/**
 * Règles pures (testées hors Android) pour « Reprendre » au-dessus des applis :
 * texte des épisodes, progression, et contrôle des adresses d'affiches.
 */
final class WatchNextFormat {
    private WatchNextFormat() { }

    /** « S4 · É3 · Le titre de l'épisode » (parties absentes omises). */
    static String subtitle(String season, String episode, String episodeTitle) {
        StringBuilder b = new StringBuilder();
        if (notEmpty(season)) b.append('S').append(season.trim());
        if (notEmpty(episode)) b.append(b.length() > 0 ? " · " : "").append('É').append(episode.trim());
        if (notEmpty(episodeTitle)) b.append(b.length() > 0 ? " · " : "").append(episodeTitle.trim());
        return b.toString();
    }

    /** Progression entre 0 et 1, ou -1 si inconnue (pas de barre affichée). */
    static float progress(long positionMs, long durationMs) {
        if (positionMs <= 0 || durationMs <= 0) return -1f;
        return Math.max(0.02f, Math.min(1f, positionMs / (float) durationMs));
    }

    /** « Il reste 32 min » quand c'est connu. */
    static String remaining(long positionMs, long durationMs) {
        if (positionMs <= 0 || durationMs <= 0 || positionMs >= durationMs) return "";
        long min = Math.max(1, (durationMs - positionMs) / 60_000);
        return min >= 60 ? "Il reste " + (min / 60) + " h " + (min % 60 < 10 ? "0" : "") + (min % 60) : "Il reste " + min + " min";
    }

    /**
     * Affiche fournie par une appli installée : HTTPS uniquement, port standard, sans identifiants,
     * pas d'adresse locale (le réseau de la maison reste hors d'atteinte).
     */
    static boolean posterUrlOk(String url) {
        try {
            if (url == null || url.length() > 2048) return false;
            URL u = new URL(url);
            if (!"https".equals(u.getProtocol())) return false;
            if (u.getUserInfo() != null) return false;
            if (u.getPort() != -1 && u.getPort() != 443) return false;
            String h = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if (h.isEmpty() || h.equals("localhost") || h.endsWith(".local") || h.endsWith(".lan")) return false;
            if (h.matches("[0-9.]+") || h.contains(":")) return false; // pas d'adresse IP brute
            return h.contains(".");
        } catch (Exception e) { return false; }
    }

    private static boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }
}

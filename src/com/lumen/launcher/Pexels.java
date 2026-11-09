package com.lumen.launcher;

import java.util.List;

/**
 * Pexels (facultatif) : uniquement avec la clé personnelle de l'utilisateur. Les vidéos trouvées sont
 * des suggestions dans l'écran de choix ; rien n'est téléchargé sans être coché.
 * Java pur, testé dans tests/LumenTests.
 */
final class Pexels {
    private Pexels() { }

    static final String CITIES = "Villes · Pexels", NATURE = "Nature · Pexels";

    /** Fonds d'écran : { groupe, recherche }. */
    static final String[][] FONDS_QUERIES = {
            {CITIES, "paris aerial"}, {CITIES, "london aerial"}, {CITIES, "tokyo night aerial"},
            {CITIES, "new york aerial"}, {CITIES, "dubai aerial"}, {CITIES, "hong kong night aerial"},
            {NATURE, "mountains drone"}, {NATURE, "ocean aerial"}, {NATURE, "forest aerial"}};

    /** Ambiances : { groupe, recherche } dans l'ordre de CosyCatalog.NAMES. */
    static final String[][] COSY_QUERIES = {
            {"cosy:0", "fireplace"}, {"cosy:1", "rain window"}, {"cosy:2", "aquarium fish"},
            {"cosy:3", "starry night timelapse"}, {"cosy:4", "ocean waves beach"}, {"cosy:5", "snow falling"}};

    /** Un fichier vidéo proposé par Pexels pour une même vidéo (plusieurs définitions). */
    static final class File {
        final String link, type;
        final int width, height;
        File(String link, String type, int width, int height) { this.link = link; this.type = type; this.width = width; this.height = height; }
    }

    /** Le MP4 en HTTPS le plus net entre minH et maxH (null si aucun). */
    static File choose(List<File> files, int maxH, int minH) {
        File best = null;
        for (File f : files) {
            if (f.link == null || !f.link.startsWith("https://") || !"video/mp4".equals(f.type)) continue;
            if (f.height > maxH || f.height < minH) continue;
            if (best == null || f.height > best.height) best = f;
        }
        return best;
    }

    /** Titre lisible tiré de l'adresse de la page (…/video/vue-aerienne-de-paris-123456/). */
    static String titleFromUrl(String url, String fallback) {
        if (url != null) {
            String u = url.replaceAll("/+$", "");
            int i = u.lastIndexOf('/');
            if (i >= 0) {
                String slug = u.substring(i + 1).replaceAll("-?\\d+$", "").replace('-', ' ').trim();
                if (slug.length() > 2) return Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
            }
        }
        return fallback == null || fallback.isEmpty() ? "Vidéo Pexels"
                : Character.toUpperCase(fallback.charAt(0)) + fallback.substring(1);
    }

    /** Une clé Pexels : lettres et chiffres uniquement, longueur raisonnable. */
    static boolean validKey(String k) {
        return k != null && k.matches("[A-Za-z0-9]{20,100}");
    }
}

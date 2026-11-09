package com.lumen.launcher;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Politique réseau de Lumen (Java pur, testable hors Android).
 * Seules ces adresses peuvent être contactées, uniquement en HTTPS sur le port 443.
 * Toute redirection est revérifiée avec les mêmes règles.
 */
final class NetPolicy {
    private NetPolicy() { }

    static final Set<String> ALLOWED_HOSTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "api.github.com",                // mise à jour : dernière version de Lumen (dépôt du projet)
            "github.com",                    // mise à jour : fichier Lumen.apk de la release
            "objects.githubusercontent.com", // mise à jour : téléchargement (redirection de GitHub)
            "release-assets.githubusercontent.com",
            "commons.wikimedia.org",         // API Wikimedia Commons (licences libres)
            "upload.wikimedia.org",          // fichiers Wikimedia
            "api.open-meteo.com",            // météo (seulement si une ville est réglée)
            "geocoding-api.open-meteo.com"   // recherche de la ville
    )));

    /**
     * Pexels : joignable uniquement si l'utilisateur a réglé sa propre clé (sinon refusé comme
     * n'importe quel serveur inconnu).
     */
    static final Set<String> PEXELS_HOSTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "api.pexels.com", "videos.pexels.com", "images.pexels.com")));
    private static volatile boolean pexels;
    static void setPexelsEnabled(boolean on) { pexels = on; }

    /** Licences acceptées pour les contenus Wikimedia (comparaison en minuscules, préfixe). */
    private static final String[] FREE_LICENSES = {"cc0", "public domain", "pd", "cc by", "cc-by", "cc by-sa", "cc-by-sa"};

    /** Vérifie une URL ; renvoie l'URL ou lève une exception si elle sort de la politique. */
    static URL check(String url) throws MalformedURLException {
        if (url == null || url.length() > 4096) throw new MalformedURLException("URL invalide");
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new MalformedURLException("HTTPS obligatoire");
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(host) && !(pexels && PEXELS_HOSTS.contains(host)))
            throw new MalformedURLException("Hôte non autorisé : " + host);
        if (u.getPort() != -1 && u.getPort() != 443) throw new MalformedURLException("Port non autorisé");
        if (u.getUserInfo() != null) throw new MalformedURLException("Identifiants interdits dans l'URL");
        return u;
    }

    static boolean allowed(String url) {
        try { check(url); return true; } catch (MalformedURLException e) { return false; }
    }

    /** Normalise les liens « //upload… » et « http://… » des API en HTTPS avant contrôle. */
    static String https(String url) {
        if (url == null) return null;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http://")) return "https://" + url.substring(7);
        return url;
    }

    /** Licence libre compatible avec un affichage à l'écran (crédit affiché). */
    static boolean freeLicense(String shortName) {
        if (shortName == null) return false;
        String l = shortName.trim().toLowerCase(Locale.ROOT);
        if (l.contains("nc") && l.startsWith("cc")) return false;   // pas d'usage non commercial restrictif
        if (l.contains("nd") && l.startsWith("cc")) return false;   // pas de « pas de modification » (recadrage)
        for (String f : FREE_LICENSES) if (l.startsWith(f)) return true;
        return false;
    }
}

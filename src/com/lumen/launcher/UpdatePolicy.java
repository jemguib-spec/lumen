// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.Locale;

/**
 * Règles pures (testées) de la mise à jour automatique. Lumen lit dist/latest.json dans la branche
 * main du dépôt du projet, puis dist/Lumen.apk. Contrôles : empreinte SHA-256 annoncée, taille, et
 * surtout la signature : un APK n'est proposé que s'il est signé avec la même clé que Lumen installé
 * (vérifié par Lumen, puis par Android).
 */
final class UpdatePolicy {
    private UpdatePolicy() { }

    static final String REPO = "jemguib-spec/lumen";
    static final String BASE = "https://raw.githubusercontent.com/" + REPO + "/main/dist/";
    static final String LATEST = BASE + "latest.json";
    /** Services partagés par le projet (clé TMDB commune, facultative) : lu avec latest.json. */
    static final String SERVICES = BASE + "services.json";
    static final String APK = BASE + "Lumen.apk";
    /** Fichiers des GitHub Releases du dépôt (la Release « v4.20.0 » porte « Lumen-4.20.0.apk »). */
    static final String RELEASES = "https://github.com/" + REPO + "/releases/download/";
    static final long MAX_BYTES = 40L << 20;
    /** Longueur maximale des nouveautés affichées à l'écran. */
    static final int NOTES_MAX = 400;

    /** Adresse attendue de l'APK d'une version dans les Releases. */
    static String releaseUrl(String version) {
        return version(version) == null ? null : RELEASES + "v" + version + "/Lumen-" + version + ".apk";
    }

    /**
     * Adresse de l'APK annoncé par latest.json, dans l'ordre :
     * 1. « url » : le fichier de la GitHub Release de cette version, et seulement lui (adresse
     *    recomposée par Lumen, jamais reprise telle quelle : le fichier ne peut pas pointer ailleurs) ;
     * 2. « file » : un nom propre à la version dans dist/ (« Lumen-4.18.2.apk »), jamais mis en cache
     *    par GitHub ;
     * 3. sinon dist/Lumen.apk.
     */
    static String apkUrl(String file, String version, String url) {
        String release = releaseUrl(version);
        if (release != null && release.equals(url)) return release;
        if (file != null && version != null && file.equals("Lumen-" + version + ".apk") && version(version) != null) return BASE + file;
        return APK;
    }

    static String apkUrl(String file, String version) { return apkUrl(file, version, null); }

    /**
     * Nouveautés d'une version, telles qu'affichées : texte brut, lignes « - » devenues « · »,
     * sans caractères de contrôle, au plus NOTES_MAX caractères. Vide si rien d'utilisable.
     */
    static String notes(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder();
        for (String line : raw.replace("\r", "").split("\n")) {
            String l = line.trim();
            if (l.startsWith("- ") || l.startsWith("* ")) l = "· " + l.substring(2).trim();
            if (l.startsWith("#")) continue;
            StringBuilder c = new StringBuilder();
            for (int i = 0; i < l.length(); i++) { char ch = l.charAt(i); if (ch >= ' ' || ch == '\t') c.append(ch); }
            l = c.toString().trim();
            if (l.isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(l);
        }
        String s = b.toString();
        if (s.length() > NOTES_MAX) s = s.substring(0, NOTES_MAX - 1).trim() + "…";
        return s;
    }

    /** « v4.1.0 » ou « 4.1.0 » → « 4.1.0 » (null si ce n'est pas un numéro de version). */
    static String version(String tag) {
        if (tag == null) return null;
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        return t.matches("\\d{1,4}(\\.\\d{1,4}){0,3}") ? t : null;
    }

    /** > 0 si a est plus récente que b (« 4.10 » > « 4.9 », « 4.0.1 » > « 4.0 »). */
    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0, q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    /** Empreinte SHA-256 en hexadécimal (64 caractères). */
    static boolean sha256Ok(String h) { return h != null && h.toLowerCase(Locale.ROOT).matches("[0-9a-f]{64}"); }

    static String hex(byte[] d) {
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format(Locale.ROOT, "%02x", x));
        return b.toString();
    }

    /** Signature de fichier ZIP/APK (« PK\3\4 »). */
    static boolean looksLikeApk(byte[] head) {
        return head != null && head.length >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4;
    }
}

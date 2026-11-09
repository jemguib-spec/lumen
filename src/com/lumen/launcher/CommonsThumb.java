// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adresses d'aperçu (image fixe) des vidéos de Wikimedia Commons, construites comme le fait
 * Commons lui-même : …/thumb/a/ab/Nom.webm/500px--Nom.webm.jpg. Uniquement des largeurs
 * standard (Wikimedia refuse les autres). Java pur, testé.
 */
final class CommonsThumb {
    private CommonsThumb() { }

    static final int[] WIDTHS = {500, 330, 960};

    /** « File:Mon fichier.webm » → adresse de l'aperçu à cette largeur. */
    static String url(String pageTitle, int width) {
        if (pageTitle == null) return null;
        String name = pageTitle.replaceFirst("^File:", "").trim().replace(' ', '_');
        if (name.isEmpty()) return null;
        String h = md5(name);
        String enc = encode(name);
        return "https://upload.wikimedia.org/wikipedia/commons/thumb/" + h.charAt(0) + "/" + h.substring(0, 2) + "/"
                + enc + "/" + width + "px--" + enc + ".jpg";
    }

    /** Ordre d'essai : l'adresse donnée par l'API, puis les adresses construites aux tailles standard. */
    static List<String> candidates(String apiUrl, String pageTitle) {
        List<String> out = new ArrayList<>();
        if (apiUrl != null && !apiUrl.isEmpty()) out.add(apiUrl);
        if (pageTitle != null && pageTitle.startsWith("File:"))
            for (int w : WIDTHS) { String u = url(pageTitle, w); if (u != null && !out.contains(u)) out.add(u); }
        return out;
    }

    static String md5(String s) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format(Locale.ROOT, "%02x", x));
            return b.toString();
        } catch (Exception e) { return "00"; }
    }

    /** Encodage d'URL comme Wikimedia : espaces déjà en « _ », caractères spéciaux en %XX (UTF-8). */
    static String encode(String name) {
        StringBuilder b = new StringBuilder();
        try {
            for (byte x : name.getBytes("UTF-8")) {
                int c = x & 0xFF;
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "-_.~!*'(),".indexOf(c) >= 0) b.append((char) c);
                else b.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        } catch (Exception ignored) { }
        return b.toString();
    }
}

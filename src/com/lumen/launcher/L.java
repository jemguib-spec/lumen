// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.HashMap;
import java.util.Map;

/**
 * Textes de l'interface. Le code est écrit en français ; L.t(« texte ») rend la traduction si la
 * langue choisie en a une (assets/lang/<langue>.tsv : « français <tab> traduction », une par
 * ligne), sinon le français tel quel. Java pur, testé : le dictionnaire est vérifié contre le code.
 */
final class L {
    private L() { }

    private static volatile Map<String, String> dict = new HashMap<>();
    private static volatile String lang = "fr";

    /** Langue en vigueur (« fr », « en »…). */
    static String lang() { return lang; }
    static boolean french() { return "fr".equals(lang); }

    /** Charge un dictionnaire (null ou vide : français). */
    static void load(String language, Map<String, String> d) {
        lang = language == null || language.isEmpty() ? "fr" : language;
        dict = d == null ? new HashMap<String, String>() : d;
    }

    static String t(String fr) {
        if (fr == null) return null;
        String v = dict.get(fr);
        return v != null ? v : fr;
    }

    static String[] all(String[] fr) {
        String[] out = new String[fr.length];
        for (int i = 0; i < fr.length; i++) out[i] = t(fr[i]);
        return out;
    }

    /** Lecture d'un fichier « fr<tab>traduction » ; lignes vides et « # » ignorées ; « \\n » = retour à la ligne. */
    static Map<String, String> parse(String tsv) {
        Map<String, String> out = new HashMap<>();
        if (tsv == null) return out;
        for (String line : tsv.split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab == line.length() - 1) continue;
            out.put(unescape(line.substring(0, tab)), unescape(line.substring(tab + 1)));
        }
        return out;
    }

    static String unescape(String s) { return s.replace("\\n", "\n"); }
    static String escape(String s) { return s.replace("\n", "\\n"); }

    /** Langue à utiliser : réglage explicite, sinon celle du système (français → français, le reste → anglais). */
    static String choose(String setting, String systemLanguage) {
        if (setting != null && !setting.isEmpty()) return setting;
        return "fr".equals(systemLanguage) ? "fr" : "en";
    }
}

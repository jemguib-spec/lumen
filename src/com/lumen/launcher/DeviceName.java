// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nom du téléphone affiché sur la TV (« Contrôlée par iPhone de Jem ») : celui choisi sur la page
 * du téléphone, sinon déduit du navigateur. Texte court et propre uniquement. Java pur, testé.
 */
final class DeviceName {
    private DeviceName() { }

    static final int MAX = 32;
    private static final Pattern ANDROID_MODEL = Pattern.compile("Android [0-9.]+; ([^;)]+?)(?: Build/[^;)]*)?\\)");

    /** Nom choisi s'il est valable, sinon le modèle d'après le navigateur. */
    static String pick(String chosen, String userAgent) {
        String c = clean(chosen);
        return c != null ? c : from(userAgent);
    }

    static String clean(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.isEmpty() || t.length() > MAX || !Backup.textOk(t, MAX)) return null;
        return t;
    }

    static String from(String ua) {
        if (ua == null) return "un téléphone";
        if (ua.contains("iPad")) return "iPad";
        if (ua.contains("iPhone")) return "iPhone";
        Matcher m = ANDROID_MODEL.matcher(ua);
        if (m.find()) {
            String model = m.group(1).trim();
            if (model.length() > 2 && !model.startsWith("Linux") && model.length() <= MAX) return model;
            return "un téléphone Android";
        }
        return "un téléphone";
    }
}

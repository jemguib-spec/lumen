// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import java.io.InputStream;
import java.util.Locale;

/** Charge le dictionnaire de la langue choisie depuis assets/lang (côté Android ; les règles sont dans L). */
final class Lang {
    private Lang() { }

    static final String[] CODES = {"", "fr", "en"};
    static final String[] NAMES = {"Système", "Français", "English"};

    static void init(Context c, String setting) {
        String lang = L.choose(setting, Locale.getDefault().getLanguage());
        if ("fr".equals(lang)) { L.load("fr", null); return; }
        try (InputStream in = c.getAssets().open("lang/" + lang + ".tsv")) {
            byte[] b = new byte[64 * 1024];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(b)) > 0) sb.append(new String(b, 0, n, "UTF-8"));
            L.load(lang, L.parse(sb.toString()));
        } catch (Exception e) {
            L.load("fr", null); // dictionnaire absent : français
        }
    }
}

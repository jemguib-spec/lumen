// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/**
 * Police de l'interface : Inter (licence libre OFL, fichiers dans assets/fonts, réduits à l'alphabet
 * latin). La même sur toutes les TV, quelle que soit la police du fabricant. Les caractères absents
 * (autres alphabets) passent automatiquement par la police du système.
 */
final class Fonts {
    private Fonts() { }

    private static Context app;
    private static final Map<String, Typeface> cache = new HashMap<>();

    static void init(Context c) { app = c.getApplicationContext(); }

    /** Même usage que Typeface.create(famille, style) avec les noms Android habituels. */
    static synchronized Typeface get(String family, int style) {
        String file;
        boolean bold = (style & Typeface.BOLD) != 0;
        if (family == null) family = "sans-serif";
        switch (family) {
            case "sans-serif-thin": file = "InterDisplay-Thin.otf"; break;
            case "sans-serif-light": file = bold ? "Inter-Medium.otf" : "InterDisplay-Light.otf"; break;
            case "sans-serif-medium": file = bold ? "Inter-Bold.otf" : "Inter-Medium.otf"; break;
            case "sans-serif-semibold": file = "Inter-SemiBold.otf"; break;
            default: file = bold ? "Inter-Bold.otf" : "Inter-Regular.otf";
        }
        Typeface t = cache.get(file);
        if (t != null) return t;
        if (app != null) {
            try {
                t = Typeface.createFromAsset(app.getAssets(), "fonts/" + file);
                cache.put(file, t);
                return t;
            } catch (Exception ignored) { }
        }
        return Typeface.create(family, style);
    }
}

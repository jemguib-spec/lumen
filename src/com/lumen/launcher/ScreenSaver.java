// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * Lumen comme économiseur d'écran officiel de la TV (service LumenDream).
 * Google TV cache souvent ce choix : si l'autorisation WRITE_SECURE_SETTINGS a été donnée une fois
 * par ADB, Lumen se règle lui-même ; sinon on ouvre l'écran système, ou on explique la marche à suivre.
 */
final class ScreenSaver {
    private ScreenSaver() { }

    private static final String KEY_COMPONENTS = "screensaver_components";
    private static final String KEY_ENABLED = "screensaver_enabled";

    static String component(Context c) {
        return new ComponentName(c, LumenDream.class).flattenToString();
    }

    /** Lumen est-il l'économiseur choisi ? */
    static boolean isLumen(Context c) {
        try {
            String v = Settings.Secure.getString(c.getContentResolver(), KEY_COMPONENTS);
            if (v == null) return false;
            String me = component(c), shortMe = new ComponentName(c, LumenDream.class).flattenToShortString();
            for (String part : v.split(",")) {
                String p = part.trim();
                if (p.equals(me) || p.equals(shortMe)) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /** L'autorisation (donnée par ADB) permet-elle de régler l'économiseur directement ? */
    static boolean canSet(Context c) {
        return c.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    /** Choisit Lumen comme économiseur. false si l'autorisation manque ou si la TV refuse. */
    static boolean setLumen(Context c) {
        if (!canSet(c)) return false;
        try {
            Settings.Secure.putString(c.getContentResolver(), KEY_COMPONENTS, component(c));
            Settings.Secure.putInt(c.getContentResolver(), KEY_ENABLED, 1);
            return isLumen(c);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ouvre l'écran système de l'économiseur, s'il existe sur cette TV. */
    static boolean openSystemScreen(Context c) {
        for (String action : new String[]{Settings.ACTION_DREAM_SETTINGS, "android.settings.SCREENSAVER_SETTINGS"}) {
            try {
                Intent i = new Intent(action);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (i.resolveActivity(c.getPackageManager()) == null) continue;
                c.startActivity(i);
                return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    static String state(Context c) {
        if (isLumen(c)) return "Lumen";
        return canSet(c) ? "Autre · OK pour choisir Lumen" : "Autre";
    }
}

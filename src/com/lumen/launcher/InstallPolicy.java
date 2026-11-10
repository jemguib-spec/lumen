// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.ArrayList;
import java.util.List;

/** Règles pures de l'assistant d'installation (Java pur, testé) : commandes ADB et état de l'accueil. */
final class InstallPolicy {
    private InstallPolicy() { }

    static final String OWN = "com.lumen.launcher";
    /** Accueils d'origine connus, du plus récent (Google TV) au plus ancien (Android TV). */
    static final String[] STOCK_LAUNCHERS = {"com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher"};
    /** Assistant de configuration de Google TV : il relance l'accueil Google s'il reste actif. */
    static final String SETUP_WRAITH = "com.google.android.tungsten.setupwraith";
    static final String GUIDE = "https://github.com/jemguib-spec/lumen/blob/main/INSTALLATION.md";

    // ------------------------------------------------------------------ règles pures (testées)

    /** Lumen est-il l'accueil ? (paquet qui répond à la touche Accueil ; null ou « android » = choix non fait) */
    static boolean isHome(String resolvedPackage) { return OWN.equals(resolvedPackage); }

    /**
     * Commandes ADB, dans l'ordre, pour une TV à l'adresse ip (null si inconnue) dont l'accueil
     * d'origine est stockLauncher (null : aucun connu → on propose Google TV).
     */
    static List<String> commands(String ip, String stockLauncher, boolean setupWraithPresent) {
        List<String> out = new ArrayList<>();
        out.add("adb connect " + (ip == null ? "ADRESSE-IP-DE-LA-TV" : ip) + ":5555");
        String stock = stockLauncher == null ? STOCK_LAUNCHERS[0] : stockLauncher;
        out.add("adb shell pm disable-user --user 0 " + stock);
        if (setupWraithPresent) out.add("adb shell pm disable-user --user 0 " + SETUP_WRAITH);
        out.add("adb shell appops set " + OWN + " GET_USAGE_STATS allow");
        return out;
    }

    /** Commandes pour revenir à l'accueil d'origine. */
    static List<String> undoCommands(String stockLauncher, boolean setupWraithPresent) {
        List<String> out = new ArrayList<>();
        out.add("adb shell pm enable " + (stockLauncher == null ? STOCK_LAUNCHERS[0] : stockLauncher));
        if (setupWraithPresent) out.add("adb shell pm enable " + SETUP_WRAITH);
        return out;
    }

}

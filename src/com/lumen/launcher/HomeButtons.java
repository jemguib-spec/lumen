// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Les boutons ronds du haut de l'accueil, dans leur ordre à l'écran. Chacun s'affiche ou se masque
 * dans Réglages › Accueil et applis › Boutons de l'accueil (par profil). Certains ne s'affichent
 * en plus que s'ils servent : la cloche quand une appli a envoyé une notification, Entrées si la TV
 * en a, Profils dès deux profils. Java pur, testé.
 */
final class HomeButtons {
    private HomeButtons() { }

    static final String NOTIFS = "notifs", SEARCH = "search", AMBIANCES = "ambiances", INPUTS = "inputs", HOUSE = "house",
            PROFILES = "profiles", APPS = "apps", TV = "tv", SOUNDS = "sounds", SETTINGS = "settings";

    /** Ordre à l'écran, de gauche à droite. */
    static final String[] IDS = {NOTIFS, SEARCH, AMBIANCES, INPUTS, HOUSE, PROFILES, APPS, TV, SOUNDS, SETTINGS};
    /** Noms dans le réglage (traduits à l'affichage). */
    static final String[] NAMES = {"Notifications", "Rechercher", "Ambiances", "Entrées", "Maison (Home Assistant)",
            "Profils", "Toutes les applications", "Paramètres de la TV", "Sons", "Réglages"};
    /** Condition en plus du réglage, dite dans la ligne (vide : toujours affiché s'il est activé). */
    static final String[] WHEN = {"quand une appli en envoie", "", "", "si la TV en a", "",
            "dès deux profils", "", "", "", ""};

    static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    /** Réglage où est rangé l'état du bouton (Maison et Sons gardent leur ancien nom : rien n'est perdu). */
    static String prefKey(String id) {
        if (HOUSE.equals(id)) return "ha_button";
        if (SOUNDS.equals(id)) return "sound_button";
        return "btn_" + id;
    }

    /** Maison et Sons sont masqués par défaut ; tous les autres sont affichés. */
    static boolean byDefault(String id) { return !HOUSE.equals(id) && !SOUNDS.equals(id); }

    /** Tous les réglages des boutons (profils, sauvegarde). */
    static String[] prefKeys() {
        String[] out = new String[IDS.length];
        for (int i = 0; i < IDS.length; i++) out[i] = prefKey(IDS[i]);
        return out;
    }

    /** Affiché à l'écran : activé dans le réglage, et utile en ce moment. */
    static boolean shown(boolean enabled, boolean useful) { return enabled && useful; }

    /** « 8 sur 10 » : nombre de boutons activés. */
    static int enabledCount(boolean[] enabled) {
        int n = 0;
        for (boolean b : enabled) if (b) n++;
        return n;
    }
}

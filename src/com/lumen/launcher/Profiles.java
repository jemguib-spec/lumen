// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Profils par personne (Java pur, testé). Chaque profil a ses propres réglages « personnels »
 * (favoris, applis masquées, fonds, affichage, veille, sons, recherches, habitudes…). Tout le
 * reste est partagé par la maison : clés API, Home Assistant, code PIN, applis verrouillées
 * (changer de profil ne doit jamais lever un verrou), téléphones mémorisés, vidéos téléchargées,
 * mises à jour.
 *
 * Le profil principal (n° 0) garde les réglages tels qu'ils étaient avant les profils : rien à
 * migrer. Les autres rangent leurs réglages personnels sous « p<n°>. » + nom du réglage.
 */
final class Profiles {
    private Profiles() { }

    static final int MAIN = 0;
    static final int MAX = 8;
    static final int MAX_NAME = 20;
    /** Réglages communs où sont rangés la liste des profils et le profil actif. */
    static final String LIST_KEY = "profiles", ACTIVE_KEY = "profile_active";

    /** Réglages propres à chaque personne. */
    static final Set<String> PERSONAL = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "favorites", "hidden_apps", "hidden_walls", "source", "follow_time", "interval",
            "tile_style", "tile_word_v1", "show_icons", "tint_tiles", "app_glow",
            "veille_src", "greeting", "night_hour", "ambient_min", "idle", "clock", "seasonal",
            "weather_fx", "living_scenes", "sounds", "text_scale", "high_contrast", "home_cols", "home_rows",
            "recents", "searches", "habits", "habits_on", "ambiance_kind", "ambiance_sound",
            "reco_filter", "reco_mode", "reco_type", "reco_hist", "top_shelf", "shelf_posters",
            "ha_button", "ha_home", "ha_pinned", "inputs_home_v1", "lang")));

    static boolean personal(String key) { return key != null && PERSONAL.contains(key); }

    /** Nom réel d'un réglage pour un profil. */
    static String key(String key, int profile) {
        return profile == MAIN || !personal(key) ? key : prefix(profile) + key;
    }

    static String prefix(int profile) { return "p" + profile + "."; }

    /** Profil auquel appartient un nom réel (-1 : réglage commun ou du profil principal). */
    static int ownerOf(String realKey) {
        if (realKey == null || !realKey.startsWith("p")) return -1;
        int dot = realKey.indexOf('.');
        if (dot < 2) return -1;
        try {
            int n = Integer.parseInt(realKey.substring(1, dot));
            return n > MAIN && personal(realKey.substring(dot + 1)) ? n : -1;
        } catch (NumberFormatException e) { return -1; }
    }

    /**
     * Un profil : numéro, prénom, et « enfant » ou non. Dans un profil enfant, seules les applis
     * de ses favoris existent ; tout le reste (réglages, autres profils, menus, désinstallation)
     * demande le code PIN de la maison.
     */
    static final class Profile {
        final int id;
        final String name;
        final boolean kid;
        Profile(int id, String name) { this(id, name, false); }
        Profile(int id, String name, boolean kid) { this.id = id; this.name = name; this.kid = kid; }
    }

    /** Prénom accepté : lettres, chiffres, espaces et ponctuation simple, 20 caractères au plus. */
    static String cleanName(String s) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'' || c == '.' || c == '’') b.append(c);
        }
        String t = b.toString().trim().replaceAll(" +", " ");
        if (t.length() > MAX_NAME) t = t.substring(0, MAX_NAME).trim();
        return t.isEmpty() ? null : t;
    }

    /** Lecture de la liste enregistrée (« 0:Moi|1:Léa|2:Tom:k », « :k » = enfant) ; le profil principal existe toujours. */
    static List<Profile> parse(String stored) {
        List<Profile> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        if (stored != null) for (String part : stored.split("\\|")) {
            int c = part.indexOf(':');
            if (c <= 0) continue;
            try {
                int id = Integer.parseInt(part.substring(0, c));
                String rest = part.substring(c + 1);
                boolean kid = rest.endsWith(":k");
                if (kid) rest = rest.substring(0, rest.length() - 2);
                String name = cleanName(rest);
                if (id < MAIN || id > 999 || name == null || !seen.add(id)) continue;
                out.add(new Profile(id, name, kid && id != MAIN));
            } catch (NumberFormatException ignored) { }
            if (out.size() >= MAX) break;
        }
        if (!seen.contains(MAIN)) out.add(0, new Profile(MAIN, "Principal"));
        return out;
    }

    static String serialize(List<Profile> l) {
        StringBuilder b = new StringBuilder();
        for (Profile p : l) b.append(b.length() == 0 ? "" : "|").append(p.id).append(':').append(p.name).append(p.kid ? ":k" : "");
        return b.toString();
    }

    static Profile find(List<Profile> l, int id) {
        for (Profile p : l) if (p.id == id) return p;
        return null;
    }

    /** Ajoute un profil ; null si le prénom est refusé, déjà pris ou s'il y en a déjà MAX. */
    static List<Profile> add(List<Profile> l, String name) {
        String n = cleanName(name);
        if (n == null || l.size() >= MAX || taken(l, n, -1)) return null;
        int id = 1;
        for (Profile p : l) id = Math.max(id, p.id + 1);
        List<Profile> out = new ArrayList<>(l);
        out.add(new Profile(id, n));
        return out;
    }

    static List<Profile> rename(List<Profile> l, int id, String name) {
        String n = cleanName(name);
        if (n == null || find(l, id) == null || taken(l, n, id)) return null;
        List<Profile> out = new ArrayList<>();
        for (Profile p : l) out.add(p.id == id ? new Profile(id, n, p.kid) : p);
        return out;
    }

    /** Marque un profil comme enfant (jamais le principal, qui garde les clés de la maison). */
    static List<Profile> setKid(List<Profile> l, int id, boolean kid) {
        if (id == MAIN || find(l, id) == null) return null;
        List<Profile> out = new ArrayList<>();
        for (Profile p : l) out.add(p.id == id ? new Profile(id, p.name, kid) : p);
        return out;
    }

    /** Retire un profil (jamais le principal). */
    static List<Profile> remove(List<Profile> l, int id) {
        if (id == MAIN || find(l, id) == null) return null;
        List<Profile> out = new ArrayList<>();
        for (Profile p : l) if (p.id != id) out.add(p);
        return out;
    }

    private static boolean taken(List<Profile> l, String name, int except) {
        for (Profile p : l) if (p.id != except && p.name.equalsIgnoreCase(name)) return true;
        return false;
    }
}

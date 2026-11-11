// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Touches de la télécommande que Lumen ne connaît pas (Guide, Info, boutons de couleur, chiffres,
 * boutons d'appli…) et la fonction que l'on a choisi de leur relier. Les touches de navigation,
 * de volume, de lecture et celles du clavier ne sont jamais proposées : elles gardent leur rôle.
 * Java pur, testé.
 */
final class RemoteKeys {
    private RemoteKeys() { }

    /** Fonctions que l'on peut relier à une touche ; « app:clé » ouvre une appli. */
    static final String NONE = "none", SETTINGS = "settings", BACK = "back", APPS = "apps", SEARCH = "search",
            RECO = "reco", RESUME = "resume", HOME = "maison", VEILLE = "veille", AMBIANCE = "ambiance", TV_SETTINGS = "tv", SOUNDS = "sons";
    static final String APP_PREFIX = "app:";
    /** Fonctions proposées, dans l'ordre du panneau (« Ouvrir une appli » et « Ne rien faire » sont ajoutées à part). */
    static final String[] ACTIONS = {SETTINGS, BACK, APPS, SEARCH, RESUME, RECO, HOME, VEILLE, AMBIANCE, SOUNDS, TV_SETTINGS};

    static final int MAX_KEYS = 64, MAX_APP_KEY = 300;

    /**
     * Identifiant d'une touche que l'on peut relier, ou null si elle doit garder son rôle habituel.
     * « k176 » pour un code Android ; « s412 » (code matériel) quand Android ne connaît pas la touche.
     */
    static String id(int keyCode, int scanCode) {
        if (keyCode == 0) return scanCode > 0 ? "s" + scanCode : null;
        return mappable(keyCode) ? "k" + keyCode : null;
    }

    /** Codes Android (KeyEvent.KEYCODE_…) d'une télécommande qui n'ont pas de rôle dans Lumen. */
    static boolean mappable(int c) {
        return (c >= 7 && c <= 18)        // chiffres, étoile, dièse
                || (c >= 131 && c <= 142) // F1 à F12
                || (c >= 165 && c <= 176) // Info, chaînes, zoom, TV, fenêtre, guide, enregistreur, favoris, sous-titres, paramètres
                || c == 178 || c == 180 || c == 182 // source de la TV, du décodeur, de l'ampli
                || (c >= 183 && c <= 186) // rouge, vert, jaune, bleu
                || (c >= 188 && c <= 203) // boutons 1 à 16
                || (c >= 206 && c <= 210) // 3D, contacts, agenda, musique, calculatrice
                || c == 222               // piste audio
                || (c >= 226 && c <= 230) // menu du disque, 11, 12, chaîne précédente, services
                || (c >= 232 && c <= 262) // touches TV (télétexte, entrées HDMI, description audio, aide…)
                || (c >= 284 && c <= 304) // toutes les applis, actualiser, pouces, profil, boutons d'appli
                || (c >= 314 && c <= 317); // macros 1 à 4
    }

    /** Nom affiché d'une touche à partir de son identifiant. */
    static String name(String id) {
        int n = number(id);
        if (id == null || n < 0) return L.t("Touche inconnue");
        if (id.charAt(0) == 's') return L.t("Touche spéciale ") + n;
        if (n >= 7 && n <= 16) return L.t("Touche ") + (n - 7);
        if (n >= 131 && n <= 142) return L.t("Touche ") + "F" + (n - 130);
        if (n >= 188 && n <= 203) return L.t("Bouton ") + (n - 187);
        if (n >= 289 && n <= 296) return L.t("Bouton d'appli ") + (n - 288);
        if (n >= 297 && n <= 304) return L.t("Bouton d'appli ") + (n - 288);
        if (n >= 314 && n <= 317) return L.t("Bouton macro ") + (n - 313);
        switch (n) {
            case 17: return L.t("Touche étoile");
            case 18: return L.t("Touche dièse");
            case 165: return L.t("Touche Info");
            case 166: return L.t("Touche Chaîne +");
            case 167: return L.t("Touche Chaîne −");
            case 170: return L.t("Touche TV");
            case 172: return L.t("Touche Guide");
            case 174: return L.t("Touche Favoris");
            case 175: return L.t("Touche Sous-titres");
            case 176: return L.t("Touche Paramètres");
            case 178: return L.t("Touche Source");
            case 183: return L.t("Bouton rouge");
            case 184: return L.t("Bouton vert");
            case 185: return L.t("Bouton jaune");
            case 186: return L.t("Bouton bleu");
            case 222: return L.t("Touche Piste audio");
            case 229: return L.t("Touche Chaîne précédente");
            case 233: return L.t("Touche Télétexte");
            case 262: return L.t("Touche Aide");
            case 284: return L.t("Touche Toutes les applis");
            case 288: return L.t("Touche Profil");
            default: return L.t("Touche n° ") + n;
        }
    }

    /** Nom d'une fonction (hors « ouvrir une appli », dont le nom vient de l'appli). */
    static String actionName(String action) {
        if (action == null) return "";
        switch (action) {
            case SETTINGS: return L.t("Réglages de Lumen");
            case BACK: return L.t("Retour");
            case APPS: return L.t("Toutes les applis");
            case SEARCH: return L.t("Recherche vocale");
            case RESUME: return L.t("Reprendre");
            case RECO: return L.t("À l'affiche");
            case HOME: return L.t("Maison");
            case VEILLE: return L.t("Mode veille");
            case AMBIANCE: return L.t("Ambiances");
            case SOUNDS: return L.t("Couper ou remettre les sons");
            case TV_SETTINGS: return L.t("Paramètres de la TV");
            case NONE: return L.t("Ne rien faire");
            default: return isApp(action) ? L.t("Ouvrir une appli") : "";
        }
    }

    static boolean isApp(String action) { return action != null && action.startsWith(APP_PREFIX) && action.length() > APP_PREFIX.length(); }
    static String appKey(String action) { return isApp(action) ? action.substring(APP_PREFIX.length()) : null; }

    static boolean validAction(String action) {
        if (action == null) return false;
        if (NONE.equals(action)) return true;
        for (String a : ACTIONS) if (a.equals(action)) return true;
        if (!isApp(action) || action.length() > APP_PREFIX.length() + MAX_APP_KEY) return false;
        for (int i = 0; i < action.length(); i++) {
            char ch = action.charAt(i);
            if (ch < 0x21 || ch == ';' || ch == '=') return false; // séparateurs du format, espaces, contrôles
        }
        return true;
    }

    static boolean validId(String id) {
        int n = number(id);
        if (n <= 0) return false;
        return id.charAt(0) == 's' || mappable(n);
    }

    private static int number(String id) {
        if (id == null || id.length() < 2 || id.length() > 8 || (id.charAt(0) != 'k' && id.charAt(0) != 's')) return -1;
        int n = 0;
        for (int i = 1; i < id.length(); i++) {
            char ch = id.charAt(i);
            if (ch < '0' || ch > '9') return -1;
            n = n * 10 + (ch - '0');
        }
        return n;
    }

    /** Lit « k176=settings;k183=app:paquet/activité » ; tout ce qui est mal formé est ignoré. */
    static Map<String, String> parse(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0 || out.size() >= MAX_KEYS) continue;
            String id = part.substring(0, eq), action = part.substring(eq + 1);
            if (validId(id) && validAction(action)) out.put(id, action);
        }
        return out;
    }

    static String serialize(Map<String, String> keys) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : keys.entrySet()) {
            if (!validId(e.getKey()) || !validAction(e.getValue())) continue;
            if (b.length() > 0) b.append(';');
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.toString();
    }

    /** Nouvelle liste avec la touche reliée à « action » (null : liaison retirée, la question reviendra). */
    static String with(String raw, String id, String action) {
        Map<String, String> m = parse(raw);
        if (action == null) m.remove(id);
        else if (validId(id) && validAction(action) && (m.containsKey(id) || m.size() < MAX_KEYS)) m.put(id, action);
        return serialize(m);
    }
}

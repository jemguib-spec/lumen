// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Réglages trouvables depuis la recherche des applis (« veille », « stockage », « pin »…).
 * Chaque entrée mène à sa rubrique. Java pur, testé.
 */
final class SettingsIndex {
    private SettingsIndex() { }

    /** Cibles spéciales (hors rubriques des réglages). */
    static final int PHONE = -2, STORAGE = -3, HOME = -4;
    /** Rubriques (mêmes numéros que SettingsPanel). */
    static final int FOND = 1, ACCUEIL = 2, VEILLE = 3, AMBIANCES = 4, SYSTEME = 5, ABOUT = 6;

    static final class Entry {
        final String label, where, keywords;
        final int page;
        Entry(String label, String where, int page, String keywords) { this.label = label; this.where = where; this.page = page; this.keywords = keywords; }
    }

    private static final String F = "Fond d'écran", A = "Accueil et applis", V = "Mode veille", AM = "Ambiances", S = "Système";
    static final Entry[] ALL = {
            new Entry("Source du fond d'écran", F, FOND, "fond ecran image photo video wallpaper paysage espace nature animaux"),
            new Entry("Changement de fond", F, FOND, "intervalle duree changer fond fixe"),
            new Entry("Galerie des fonds", F, FOND, "galerie masquer photos fonds"),
            new Entry("Style des vignettes", A, ACCUEIL, "vignettes banniere icone nom tuiles"),
            new Entry("Taille du texte", A, ACCUEIL, "texte police grand lisibilite"),
            new Entry("Contraste renforcé", A, ACCUEIL, "contraste lisibilite"),
            new Entry("Bandeau d'accueil", A, ACCUEIL, "bandeau salutation bonjour bonsoir"),
            new Entry("Bouton Maison (Home Assistant)", A, ACCUEIL, "maison home assistant domotique lumiere"),
            new Entry("Applis masquées", A, ACCUEIL, "masquees cachees applis"),
            new Entry("Horloge de l'accueil", A, ACCUEIL, "horloge heure"),
            new Entry("Lancer le mode veille", V, VEILLE, "veille ecran repos"),
            new Entry("Images du mode veille", V, VEILLE, "veille images paysages"),
            new Entry("Écran de nuit", V, VEILLE, "nuit horloge noir sommeil"),
            new Entry("Météo et ville", V, VEILLE, "meteo ville temps temperature"),
            new Entry("Économiseur d'écran de la TV", V, VEILLE, "economiseur ecran screensaver daydream"),
            new Entry("Ambiances", AM, AMBIANCES, "ambiances cheminee feu pluie videos son"),
            new Entry("Stockage", S, STORAGE, "stockage espace memoire plein vider supprimer cache"),
            new Entry("Mise à jour de Lumen", S, SYSTEME, "mise a jour version update"),
            new Entry("Sons de l'interface", S, SYSTEME, "sons bruit clic"),
            new Entry("Code PIN", S, SYSTEME, "code pin verrou enfant parental"),
            new Entry("À propos et licences", S, ABOUT, "a propos version licences"),
            new Entry("Réglages depuis le téléphone", "Réglages", PHONE, "telephone qr code smartphone telecommande"),
            new Entry("Maison", "Home Assistant", HOME, "maison home assistant lumieres scenes volets"),
    };

    /** Réglages qui correspondent à la recherche (au plus max). */
    static List<Entry> search(String q, int max) {
        List<Entry> out = new ArrayList<>();
        String f = AppCategory.fold(q == null ? "" : q).trim();
        if (f.length() < 2) return out;
        for (Entry e : ALL) {
            if (AppCategory.matches(e.label, f) || AppCategory.fold(e.keywords).contains(f)) out.add(e);
            if (out.size() >= max) break;
        }
        return out;
    }
}

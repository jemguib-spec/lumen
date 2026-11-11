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
    static final int PHONE = -2, STORAGE = -3, HOME = -4, RECO = -5;
    /** Rubriques (mêmes numéros que SettingsPanel). */
    static final int FOND = 1, ACCUEIL = 2, VEILLE = 3, AMBIANCES = 4, SYSTEME = 5, ABOUT = 6, INSTALL = 14,
            PAYSAGES = 15, FOND_AVANCE = 16, VIGNETTES = 17, REPRENDRE = 18, LISIBILITE = 19, DIAG = 20, AFFICHE = 21, TOUCHES = 22, SONS = 24, NOTIFS = 25, ROOT = 0, BOUTONS = 27, SYNC = 28;

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
            new Entry("Style des vignettes", A, VIGNETTES, "vignettes banniere icone nom tuiles"),
            new Entry("Taille du texte", A, LISIBILITE, "texte police grand lisibilite"),
            new Entry("Contraste renforcé", A, LISIBILITE, "contraste lisibilite"),
            new Entry("Bandeau d'accueil", A, ACCUEIL, "bandeau salutation bonjour bonsoir"),
            new Entry("Bouton Maison (Home Assistant)", A, BOUTONS, "maison home assistant domotique lumiere"),
            new Entry("Applis masquées", A, ACCUEIL, "masquees cachees applis"),
            new Entry("Horloge de l'accueil", A, ACCUEIL, "horloge heure"),
            new Entry("Lancer le mode veille", V, VEILLE, "veille ecran repos"),
            new Entry("Images du mode veille", V, VEILLE, "veille images paysages"),
            new Entry("Écran de nuit", V, VEILLE, "nuit horloge noir sommeil"),
            new Entry("Météo et ville", V, VEILLE, "meteo ville temps temperature"),
            new Entry("Économiseur d'écran de la TV", V, VEILLE, "economiseur ecran screensaver daydream"),
            new Entry("Ambiances", AM, AMBIANCES, "ambiances cheminee feu pluie videos son"),
            new Entry("Stockage", S, STORAGE, "stockage espace memoire plein vider supprimer cache"),
            new Entry("Rechercher une mise à jour", S, SYSTEME, "mise a jour version update rechercher"),
            new Entry("Notifications des applis", "Réglages", NOTIFS, "notifications notification alerte nouvelle sortie episode message cloche"),
            new Entry("Boutons de l'accueil", A, BOUTONS, "boutons bouton rond ronds masquer afficher cacher icones parametres tv profils ambiances entrees recherche cloche"),
            new Entry("Réglages de Lumen", "Réglages", ROOT, "reglages parametres options menu lumen"),
            new Entry("Bouton Sons sur l'accueil", A, BOUTONS, "bouton sons haut parleur masquer afficher accueil"),
            new Entry("Sons de l'interface", "Réglages", SONS, "sons son bip bips beep bruit bruits clic couper muet silence volume jeu timbre"),
            new Entry("Code PIN", S, SYSTEME, "code pin verrou enfant parental"),
            new Entry("Relier à une autre TV (réglages identiques)", S, SYNC, "synchro synchroniser synchronisation autres autre tv televiseur televiseurs relier lier delier copier envoyer recevoir reglages identiques pareils meme reseau maison"),
            new Entry("À propos et licences", S, ABOUT, "a propos version licences"),
            new Entry("Réglages d'À l'affiche", A, AFFICHE, "affiche tmdb films series plateformes selection type historique pour toi"),
            new Entry("Reprendre au-dessus des applis", A, REPRENDRE, "reprendre episodes en cours affiches historique"),
            new Entry("Paysages Lumen animés et météo", F, PAYSAGES, "paysages animes meteo pluie neige saison decors"),
            new Entry("Qualité et espace des vidéos", F, FOND_AVANCE, "qualite 4k hd espace go videos theme villes telecharger"),
            new Entry("Journal d'erreurs et mesures", S, DIAG, "journal erreurs mesures diagnostic redemarrer parametres tv conseils"),
            new Entry("Assistant de premier démarrage", S, SYSTEME, "assistant premier demarrage bienvenue configuration configurer recommencer profil cles"),
            new Entry("Installation", S, INSTALL, "installation installer adb accueil par defaut google launcher desactiver commandes"),
            new Entry("Nouveautés de la version", S, SYSTEME, "nouveautes version changements quoi de neuf"),
            new Entry("Touches de la télécommande", "Réglages", TOUCHES, "telecommande touche touches bouton boutons relier raccourci guide info couleur chiffre"),
            new Entry("Réglages depuis le téléphone", "Réglages", PHONE, "telephone qr code smartphone telecommande"),
            new Entry("Maison", "Home Assistant", HOME, "maison home assistant lumieres scenes volets"),
            new Entry("À l'affiche", "Films et séries du moment", RECO, "affiche films series tendances populaire recommandations nouveautes top"),
            new Entry("Clé TMDB (films du moment)", A, AFFICHE, "cle tmdb api themoviedb films moment"),
            new Entry("Clé Pexels", F, FOND_AVANCE, "cle pexels api videos"),
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

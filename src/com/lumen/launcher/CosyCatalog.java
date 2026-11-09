// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Catalogue fermé des vidéos d'ambiance : des fichiers précis de Wikimedia Commons, choisis un par un
 * d'après leur description (scène filmée, sans personne qui parle, sans montage ni musique ajoutée quand
 * c'est possible). Aucune recherche par mots-clés : seule cette liste peut être téléchargée.
 * La licence libre de chaque fichier est revérifiée au moment du téléchargement (NetPolicy.freeLicense).
 * Java pur, vérifié dans tests/LumenTests.
 */
final class CosyCatalog {
    private CosyCatalog() { }

    static final String[] NAMES = {"Cheminée", "Pluie", "Aquarium", "Nuit étoilée", "Vagues", "Neige"};

    /** Par ambiance, dans l'ordre de préférence. Titres exacts des pages « File: » de Commons. */
    static final String[][] FILES = {
            { // Cheminée et feu de bois
                    "File:Fantastic-fireplace-fire-chimney-hearth- background - texture - motion graphics - free video library.webm",
                    "File:2018-04-18 22-30-00 Feu.webm",
                    "File:Fire burning.webm"},
            { // Pluie
                    "File:2026-08-20 19 00 00 Heavy rain and thunder in Ewing Township, Mercer County, New Jersey.webm",
                    "File:Sunshowers - Oze National Park - 2021 Aug 4.ogg",
                    "File:October 2021 California rainstorm - Sonoma - 2021-10-24 - Sarah Stierch 17.ogg",
                    "File:Heavy rain in Longhua District, Shenzhen (20250424).webm"},
            { // Aquarium et fonds marins
                    "File:Acanthurus sohal - Arabischer Doktorfisch im Roten Meer DSCF3888.webm",
                    "File:Varna Aquarium video.webm",
                    "File:Black sea underwater life at Sinemorets coast.webm",
                    "File:Nannostomus eques school in aquarium.webm",
                    "File:Fish in aquarium.webm"},
            { // Nuit étoilée
                    "File:JAPAN Milk Way 4K - Beautiful Star and Sky at Night Time Lapse.webm",
                    "File:Night sky time lapse.webm",
                    "File:Following the Milky Way over ALMA.webm",
                    "File:Milky way -route 292 shiga kusatsu road- 1920x1080.webm",
                    "File:May 2024 aurora timelapse near Calgary.webm",
                    "File:Time-lapse of lighthouse and Milky Way.webm"},
            { // Vagues
                    "File:Waves waving at Apollo Beach (Canaveral National Seashore, Florida, GEDC7077).webm",
                    "File:Waves off of Hurricane Point Vista Point, Big Sur.webm",
                    "File:Top view sea waves. Drone footage.webm",
                    "File:Waves of the sea (Video).webm",
                    "File:Pacific Ocean at Arena Cove - April 2023 - Sarah Stierch.webm"},
            { // Neige
                    "File:Beachfront B-Roll- Snow Falling (Free to Use HD Stock Video Special Effects).webm",
                    "File:Snowfall.webm",
                    "File:Heavy snow in Xin County.webm",
                    "File:Berlin Karow lub 2025-01-10 Snowfall.webm",
                    "File:Snowfall in Hockenheim.ogv"}};

    /** Ambiance d'un titre de page Commons (espaces ou soulignés), ou -1 s'il n'est pas au catalogue. */
    static int kindOf(String pageTitle) {
        if (pageTitle == null) return -1;
        String t = pageTitle.replace('_', ' ').trim();
        for (int k = 0; k < FILES.length; k++)
            for (String f : FILES[k]) if (f.equals(t)) return k;
        return -1;
    }

    /** Tous les titres, séparés par « | » (paramètre titles= de l'API, 50 au maximum). */
    static String allTitles() {
        StringBuilder b = new StringBuilder();
        for (String[] kind : FILES) for (String f : kind) { if (b.length() > 0) b.append('|'); b.append(f); }
        return b.toString();
    }
}

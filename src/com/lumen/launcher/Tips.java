// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/** Trois conseils pour apprendre les gestes, un par démarrage, jamais plus. Réglages › Système › Revoir les conseils les remet. */
final class Tips {
    private Tips() { }

    static final String[] ALL = {
            "▼ toutes les applis  ·  ◀ Reprendre  ·  ▶ À l'affiche",
            "Appui long sur une appli : déplacer, favoris, masquer, infos",
            "Touche Menu : réglages  ·  micro de la télécommande : recherche vocale",
    };

    static void showNext(MainActivity a) {
        int n = a.prefs.tipStep();
        if (n >= ALL.length) return;
        a.showNotice(ALL[n], 5000);
        a.prefs.setTipStep(n + 1);
    }

    static void reset(MainActivity a) { a.prefs.setTipStep(0); }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Assistant du premier démarrage : ordre des étapes et décision de l'afficher (Java pur, testé).
 * Accueil (de zéro, ou copier les réglages d'une autre TV du foyer) → profil → accueil → boutons
 * → clés et services via le téléphone → prêt. L'interface est dans Onboarding.
 */
final class SetupFlow {
    private SetupFlow() { }

    static final int WELCOME = 0, PROFILE = 1, LOOK = 2, BUTTONS = 3, KEYS = 4, READY = 5;
    /** Assistant terminé ou laissé pour plus tard. */
    static final int DONE = -1;
    /** Réglage pas encore décidé (rien d'enregistré). */
    static final int UNSET = -2;
    static final int LAST = READY;

    /**
     * Étape où commencer : celle enregistrée si l'assistant est en cours ; sinon l'accueil pour une
     * installation neuve (jamais mise à jour), et rien pour une TV qui avait déjà Lumen avant cette
     * version : on ne dérange pas quelqu'un qui a déjà tout réglé.
     */
    static int start(int stored, long firstInstall, long lastUpdate) {
        if (stored == DONE) return DONE;
        if (stored >= WELCOME && stored <= LAST) return stored;
        return firstInstall > 0 && firstInstall == lastUpdate ? WELCOME : DONE;
    }

    static int next(int step) { return step >= WELCOME && step < LAST ? step + 1 : DONE; }

    /** Étape précédente (Retour) ; l'accueil reste l'accueil. */
    static int prev(int step) { return step > WELCOME && step <= LAST ? step - 1 : WELCOME; }

    /** « Étape 2 sur 5 » : le profil est la première des étapes « de zéro » ; l'accueil et la fin n'en ont pas. */
    static int number(int step) { return step >= PROFILE && step <= KEYS ? step : 0; }
    static int count() { return KEYS; }

    /** Après la copie depuis une autre TV : l'étape des clés, pour voir ce qui est arrivé (ou les taper), puis la fin. */
    static int afterSync() { return KEYS; }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Quand redemander « Qui regarde ? » (voir WhoPage). Une seule fois par séance devant la TV :
 * ni quand Android relance Lumen en cours de route (appli lourde, mise à jour, plantage), ni quand
 * l'écran clignote au réveil (HDMI-CEC) ou que l'avis « écran allumé » arrive en retard, juste
 * après le choix. Sans Android : vérifié par les tests.
 */
final class WhoPolicy {
    private WhoPolicy() { }

    /** Un choix plus récent que cela vaut pour le réveil qui arrive (avis en retard, double avis). */
    static final long RECENT = 2 * 60_000L;
    /** Écran éteint moins longtemps que cela : simple coupure (HDMI, CEC), pas une mise en veille. */
    static final long BLIP = 20_000L;
    /** Au-delà, une séance est finie de toute façon : on redemande au lancement suivant. */
    static final long SESSION = 6 * 3600_000L;
    /** Écart toléré sur l'heure de démarrage de la TV (horloge remise à l'heure par le réseau). */
    static final long SAME_BOOT = 2 * 60_000L;

    /**
     * Lumen vient d'être lancé (nouveau processus) : faut-il demander ?
     * now et bootNow : heure actuelle et heure de démarrage de la TV (ms) ; chosenAt et chosenBoot :
     * les mêmes au dernier choix (0 : jamais) ; offAt : dernière mise en veille vue (0 : jamais).
     */
    static boolean askAtLaunch(long now, long bootNow, long chosenAt, long chosenBoot, long offAt) {
        if (chosenAt <= 0) return true;
        if (Math.abs(bootNow - chosenBoot) > SAME_BOOT) return true; // la TV a redémarré depuis
        long since = now - chosenAt;
        if (since < 0 || since > SESSION) return true;
        return offAt > chosenAt; // mise en veille depuis le choix : nouvelle séance
    }

    /**
     * La TV se rallume : faut-il demander ? offFor : durée d'extinction de l'écran (-1 : inconnue,
     * Lumen n'a pas vu l'écran s'éteindre) ; sinceChosen : temps écoulé depuis le dernier choix
     * (-1 : aucun choix connu).
     */
    static boolean askAtWake(long offFor, long sinceChosen) {
        if (offFor >= 0 && offFor < BLIP) return false;
        if (sinceChosen >= 0 && sinceChosen < RECENT) return false;
        return true;
    }
}

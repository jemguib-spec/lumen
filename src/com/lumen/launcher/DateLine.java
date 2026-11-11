// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.Calendar;

/**
 * Date affichée sous l'horloge et dans le mode veille (« Mercredi 11 novembre »), dans la langue
 * de Lumen et non dans celle d'Android : une TV réglée en anglais avec Lumen en français ne doit
 * plus afficher « Wednesday 11 November » à côté de « Bonjour ». Java pur, testé.
 */
final class DateLine {
    private DateLine() { }

    private static final String[] JOURS = {"dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi"};
    private static final String[] MOIS = {"janvier", "février", "mars", "avril", "mai", "juin", "juillet",
            "août", "septembre", "octobre", "novembre", "décembre"};
    private static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};

    /** « Mercredi 11 novembre » (fr), « Wednesday 11 November » (en, et toute autre langue). */
    static String format(Calendar c, String lang) {
        int dow = c.get(Calendar.DAY_OF_WEEK) - 1, day = c.get(Calendar.DAY_OF_MONTH), month = c.get(Calendar.MONTH);
        if ("fr".equals(lang)) {
            String j = JOURS[dow];
            // « 1er » comme on l'écrit en français ; majuscule au début de la ligne seulement
            return Character.toUpperCase(j.charAt(0)) + j.substring(1) + " " + (day == 1 ? "1er" : String.valueOf(day)) + " " + MOIS[month];
        }
        return DAYS[dow] + " " + day + " " + MONTHS[month];
    }
}

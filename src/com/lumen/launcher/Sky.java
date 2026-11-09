// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Ciel réel calculé sur la TV, sans réseau (Java pur, testable) :
 * lever et coucher du soleil (algorithme de l'almanach nautique, précision ~2 min)
 * et phase de la Lune (mois synodique moyen).
 */
final class Sky {
    private Sky() { }

    private static double lat = 48.8566, lon = 2.3522; // Paris par défaut

    static synchronized void setLocation(double latitude, double longitude) {
        if (latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180) { lat = latitude; lon = longitude; }
    }

    static synchronized double lat() { return lat; }
    static synchronized double lon() { return lon; }

    /**
     * Heure locale (en minutes depuis minuit) du lever (rise=true) ou du coucher du soleil,
     * ou -1 si le soleil ne se lève/couche pas ce jour-là (régions polaires).
     */
    static int sunMinutes(Calendar day, boolean rise, double latitude, double longitude) {
        int n = day.get(Calendar.DAY_OF_YEAR);
        double lngHour = longitude / 15.0;
        double t = n + ((rise ? 6 : 18) - lngHour) / 24.0;
        double m = 0.9856 * t - 3.289;
        double l = norm(m + 1.916 * sin(m) + 0.020 * sin(2 * m) + 282.634);
        double ra = norm(Math.toDegrees(Math.atan(0.91764 * tan(l))));
        ra = (ra + (Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90)) / 15.0;
        double sinDec = 0.39782 * sin(l);
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (cos(90.833) - sinDec * sin(latitude)) / (cosDec * cos(latitude));
        if (cosH > 1 || cosH < -1) return -1;
        double h = rise ? 360 - Math.toDegrees(Math.acos(cosH)) : Math.toDegrees(Math.acos(cosH));
        h /= 15.0;
        double localMean = h + ra - 0.06571 * t - 6.622;
        double ut = ((localMean - lngHour) % 24 + 24) % 24;
        TimeZone tz = day.getTimeZone();
        double offsetH = tz.getOffset(day.getTimeInMillis()) / 3_600_000.0;
        double local = ((ut + offsetH) % 24 + 24) % 24;
        return (int) Math.round(local * 60);
    }

    static int sunrise(Calendar c) { return sunMinutes(c, true, lat(), lon()); }
    static int sunset(Calendar c) { return sunMinutes(c, false, lat(), lon()); }

    /** Âge de la Lune en jours (0 = nouvelle lune, ~14,8 = pleine lune). */
    static double moonAge(long utcMillis) {
        final double synodic = 29.530588853;
        final long knownNew = 947182440000L; // 6 janvier 2000, 18:14 UTC
        double days = (utcMillis - knownNew) / 86_400_000.0;
        double age = days % synodic;
        return age < 0 ? age + synodic : age;
    }

    static String moonPhase(long utcMillis) {
        double a = moonAge(utcMillis);
        if (a < 1.85) return "Nouvelle lune";
        if (a < 5.54) return "Premier croissant";
        if (a < 9.23) return "Premier quartier";
        if (a < 12.92) return "Lune gibbeuse croissante";
        if (a < 16.61) return "Pleine lune";
        if (a < 20.30) return "Lune gibbeuse décroissante";
        if (a < 23.99) return "Dernier quartier";
        if (a < 27.68) return "Dernier croissant";
        return "Nouvelle lune";
    }

    static String hhmm(int minutes) {
        if (minutes < 0) return "—";
        return (minutes / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", minutes % 60);
    }

    /** Moment de la journée d'après le vrai soleil : aube, jour, soir ou nuit. */
    static String period(Calendar now) {
        int rise = sunrise(now), set = sunset(now);
        int m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (rise < 0 || set < 0) { // nuit ou jour polaire : repli sur des heures fixes
            int h = now.get(Calendar.HOUR_OF_DAY);
            return h >= 6 && h < 9 ? "aube" : h >= 9 && h < 17 ? "jour" : h >= 17 && h < 21 ? "soir" : "nuit";
        }
        if (m >= rise - 60 && m < rise + 90) return "aube";
        if (m >= rise + 90 && m < set - 90) return "jour";
        if (m >= set - 90 && m < set + 60) return "soir";
        return "nuit";
    }

    /** Période des fêtes (1er décembre – 6 janvier) : neige douce sur les paysages Lumen. */
    static boolean festive(Calendar c) {
        int month = c.get(Calendar.MONTH), d = c.get(Calendar.DAY_OF_MONTH);
        return month == Calendar.DECEMBER || (month == Calendar.JANUARY && d <= 6);
    }

    private static double norm(double deg) { return ((deg % 360) + 360) % 360; }
    private static double sin(double deg) { return Math.sin(Math.toRadians(deg)); }
    private static double cos(double deg) { return Math.cos(Math.toRadians(deg)); }
    private static double tan(double deg) { return Math.tan(Math.toRadians(deg)); }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

/**
 * Limite de débit « seau à jetons » : une rafale de « burst » actions, puis « perSecond » par
 * seconde. Empêche un script de marteler la TV depuis le téléphone. Java pur, testé.
 */
final class RateLimit {
    private final double perSecond;
    private final int burst;
    private double tokens;
    private long last;

    RateLimit(double perSecond, int burst, long now) {
        this.perSecond = perSecond;
        this.burst = burst;
        this.tokens = burst;
        this.last = now;
    }

    /** true si l'action est permise maintenant (et la compte). */
    synchronized boolean allow(long now) {
        if (now > last) {
            tokens = Math.min(burst, tokens + (now - last) * perSecond / 1000.0);
            last = now;
        }
        if (tokens < 1) return false;
        tokens -= 1;
        return true;
    }
}

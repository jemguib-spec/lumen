// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Règles des notifications affichées sur l'accueil : lesquelles montrer (une nouvelle
 * sortie, un message, un rappel) et lesquelles laisser de côté (lecture en cours, téléchargements,
 * services, recommandations de l'ancien Android TV, mises à jour silencieuses d'une même
 * notification). Rien ne quitte la TV. Java pur, testé.
 */
final class NotifyPolicy {
    private NotifyPolicy() { }

    /** Notifications gardées pour la liste du bouton cloche. */
    static final int MAX_KEPT = 20;
    /** Durées d'affichage proposées (secondes). */
    static final int[] SECONDS = {6, 10, 20};
    static final int DEFAULT_SECONDS = 10;

    /** Catégories qui ne sont pas des nouvelles pour la personne devant la TV. */
    private static final String[] QUIET = {"transport", "progress", "service", "sys", "recommendation", "status", "call"};

    /**
     * À montrer ? Pas Lumen lui-même, pas une notification permanente (lecture, service), pas un
     * résumé de groupe, pas une catégorie calme, et au moins un titre ou un texte.
     */
    static boolean show(String pkg, String ownPkg, String title, String text, String category,
                        boolean ongoing, boolean groupSummary, boolean minPriority) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(ownPkg)) return false;
        if (ongoing || groupSummary || minPriority) return false;
        if (category != null) for (String q : QUIET) if (q.equals(category)) return false;
        if (pkg.equals("android") || pkg.equals("com.android.systemui")) return false;
        return !blank(title) || !blank(text);
    }

    /**
     * Même contenu qu'une notification déjà montrée : une appli qui met à jour sa notification
     * (compteur, heure) ne doit pas la refaire surgir.
     */
    static String fingerprint(String pkg, String title, String text) {
        return pkg + "\n" + clean(title) + "\n" + clean(text);
    }

    /** Durée d'affichage : le réglage, un peu plus pour un texte long (4 s de plus au-delà de 120 caractères). */
    static long displayMs(int seconds, String title, String text) {
        int s = seconds <= 0 ? DEFAULT_SECONDS : seconds;
        int len = clean(title).length() + clean(text).length();
        return (s + (len > 120 ? 4 : 0)) * 1000L;
    }

    /** Index du réglage de durée (0 si la valeur n'est pas proposée). */
    static int secondsIndex(int seconds) {
        for (int i = 0; i < SECONDS.length; i++) if (SECONDS[i] == seconds) return i;
        return 1;
    }

    /** Ajoute en tête (la plus récente d'abord), remplace la même clé, garde au plus MAX_KEPT. */
    static <T> List<T> push(List<T> list, T item, String key, KeyOf<T> keyOf) {
        List<T> out = new ArrayList<>();
        out.add(item);
        for (T t : list) {
            if (out.size() >= MAX_KEPT) break;
            if (!key.equals(keyOf.key(t))) out.add(t);
        }
        return out;
    }

    interface KeyOf<T> { String key(T t); }

    /** « à l'instant », « il y a 5 min », « il y a 2 h », « hier » ; en anglais si la langue l'est. */
    static String ago(long thenMs, long nowMs, boolean french) {
        long m = Math.max(0, (nowMs - thenMs) / 60000);
        if (m < 1) return french ? "à l'instant" : "just now";
        if (m < 60) return french ? "il y a " + m + " min" : m + " min ago";
        long h = m / 60;
        if (h < 24) return french ? "il y a " + h + " h" : h + " h ago";
        return french ? (h < 48 ? "hier" : "il y a " + (h / 24) + " j") : (h < 48 ? "yesterday" : (h / 24) + " d ago");
    }

    static String clean(String s) { return s == null ? "" : s.replaceAll("\\s+", " ").trim(); }

    private static boolean blank(String s) { return clean(s).isEmpty(); }
}

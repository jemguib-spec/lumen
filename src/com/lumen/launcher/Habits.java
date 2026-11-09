// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Habitudes d'ouverture des applis par moment de la journée, calculées et gardées sur la TV
 * uniquement (rien n'est envoyé). Format enregistré : « tranche:clé:compte;… ». Java pur, testé.
 */
final class Habits {
    static final String[] SLOT_NAMES = {"Cette nuit", "Ce matin", "Ce midi", "Cet après-midi", "Ce soir", "Ce soir"};
    private static final int MAX_ENTRIES = 300, DECAY_AT = 60;

    /** Tranche horaire : 0 nuit (0-6 h), 1 matin (6-11), 2 midi (11-14), 3 après-midi (14-18), 4 soirée (18-21), 5 fin de soirée (21-24). */
    static int slot(int hour) {
        if (hour < 6) return 0;
        if (hour < 11) return 1;
        if (hour < 14) return 2;
        if (hour < 18) return 3;
        if (hour < 21) return 4;
        return 5;
    }

    private final Map<String, Integer> counts = new HashMap<>(); // « tranche:clé » → compte

    Habits(String stored) {
        if (stored == null) return;
        for (String e : stored.split(";")) {
            int a = e.indexOf(':'), b = e.lastIndexOf(':');
            if (a <= 0 || b <= a) continue;
            try {
                int s = Integer.parseInt(e.substring(0, a)), n = Integer.parseInt(e.substring(b + 1));
                String key = e.substring(a + 1, b);
                if (s < 0 || s > 5 || n <= 0 || key.isEmpty() || key.indexOf(';') >= 0) continue;
                counts.put(s + ":" + key, Math.min(n, DECAY_AT));
            } catch (NumberFormatException ignored) { }
        }
    }

    /** Une ouverture de l'appli « key » à cette heure. */
    void record(String key, int hour) {
        if (key == null || key.isEmpty() || key.indexOf(';') >= 0) return;
        int s = slot(hour);
        String k = s + ":" + key;
        int n = (counts.containsKey(k) ? counts.get(k) : 0) + 1;
        counts.put(k, n);
        if (n >= DECAY_AT) { // les vieilles habitudes s'effacent peu à peu
            for (Map.Entry<String, Integer> e : new ArrayList<>(counts.entrySet())) {
                if (!e.getKey().startsWith(s + ":")) continue;
                int half = e.getValue() / 2;
                if (half <= 0) counts.remove(e.getKey()); else counts.put(e.getKey(), half);
            }
        }
        while (counts.size() > MAX_ENTRIES) counts.remove(smallest());
    }

    /** Les applis les plus ouvertes à ce moment-là (au moins « minCount » fois), de la plus fréquente à la moins. */
    List<String> top(int hour, int n, int minCount) {
        final String prefix = slot(hour) + ":";
        List<Map.Entry<String, Integer>> l = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet())
            if (e.getKey().startsWith(prefix) && e.getValue() >= minCount) l.add(e);
        Collections.sort(l, new Comparator<Map.Entry<String, Integer>>() {
            @Override public int compare(Map.Entry<String, Integer> x, Map.Entry<String, Integer> y) {
                int c = y.getValue() - x.getValue();
                return c != 0 ? c : x.getKey().compareTo(y.getKey());
            }
        });
        List<String> out = new ArrayList<>();
        for (int i = 0; i < l.size() && out.size() < n; i++) out.add(l.get(i).getKey().substring(prefix.length()));
        return out;
    }

    private String smallest() {
        String k = null;
        int best = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : counts.entrySet())
            if (e.getValue() < best || (e.getValue() == best && e.getKey().compareTo(k) < 0)) { best = e.getValue(); k = e.getKey(); }
        return k;
    }

    String serialize() {
        StringBuilder b = new StringBuilder();
        List<String> keys = new ArrayList<>(counts.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            if (b.length() > 0) b.append(';');
            b.append(k).append(':').append(counts.get(k));
        }
        return b.toString();
    }
}

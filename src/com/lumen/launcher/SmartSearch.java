// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.List;
import java.util.Locale;

/**
 * Recherche « intelligente » de Lumen (Java pur, testé) :
 * - sans accents ni majuscules, « + » = « plus » ;
 * - début du nom ou d'un mot (« mus » → YouTube Music), initiales (« ytm ») ;
 * - fautes de frappe tolérées (« netflx », « disnay ») ;
 * - « plex dune » : le nom d'une appli suivi d'un titre → chercher « dune » dans Plex.
 * Le score sert à classer : plus il est haut, plus le résultat est pertinent ; 0 = rien.
 */
final class SmartSearch {
    private SmartSearch() { }

    static String norm(String s) {
        if (s == null) return "";
        s = s.replaceAll("([a-z])([A-Z])", "$1 $2"); // « YouTube » → « You Tube » (initiales « yt »)
        return AppCategory.fold(s).replace("+", " plus ").replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    static int score(String label, String query) {
        String l = norm(label), q = norm(query);
        if (q.isEmpty() || l.isEmpty()) return 0;
        if (l.equals(q) || l.replace(" ", "").equals(q.replace(" ", ""))) return 1000;
        if (l.startsWith(q)) return 900 - Math.min(80, l.length() - q.length());
        String[] words = l.split(" ");
        for (int i = 1; i < words.length; i++) if (words[i].startsWith(q)) return 800 - i * 5;
        String lc = l.replace(" ", ""), qc = q.replace(" ", "");
        if (lc.startsWith(qc)) return 760;
        if (qc.length() >= 2 && words.length > 1) {
            StringBuilder ini = new StringBuilder();
            for (String w : words) if (!w.isEmpty()) ini.append(w.charAt(0));
            if (ini.toString().startsWith(qc)) return 700;
        }
        if (lc.contains(qc)) return 560;
        // fautes de frappe : on compare au début du nom et de chaque mot
        if (qc.length() >= 3) {
            int allowed = qc.length() <= 4 ? 1 : 2, best = 99;
            best = Math.min(best, distance(qc, lc.substring(0, Math.min(lc.length(), qc.length()))));
            best = Math.min(best, distance(qc, lc.substring(0, Math.min(lc.length(), qc.length() + 1))));
            for (String w : words) if (w.length() >= 3) best = Math.min(best, distance(qc, w.substring(0, Math.min(w.length(), qc.length()))));
            if (best <= allowed) return 420 - best * 60;
            if (qc.length() >= 4 && subsequence(qc, lc)) return 180;
        }
        return 0;
    }

    /** Toutes les lettres de q, dans l'ordre, se retrouvent dans l (« ntflx » → netflix). */
    static boolean subsequence(String q, String l) {
        int j = 0;
        for (int i = 0; i < l.length() && j < q.length(); i++) if (l.charAt(i) == q.charAt(j)) j++;
        return j == q.length();
    }

    /** Distance d'édition (avec inversion de deux lettres voisines). */
    static int distance(String a, String b) {
        int n = a.length(), m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++)
            for (int j = 1; j <= m; j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + c);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
            }
        return d[n][m];
    }

    /**
     * « plex dune », « netflix stranger things », « canal foot » : renvoie {index de l'appli,
     * titre cherché} si la recherche commence par le nom d'une appli, sinon null.
     */
    static Object[] appAndTitle(String query, List<String> appLabels) {
        String q = norm(query);
        int best = -1, bestLen = 0;
        for (int i = 0; i < appLabels.size(); i++) {
            String l = norm(appLabels.get(i));
            if (l.isEmpty()) continue;
            String first = l.split(" ")[0];
            for (String cand : new String[]{l, l.replace(" ", ""), first}) {
                if (cand.length() < 3 || cand.length() <= bestLen) continue;
                if (q.startsWith(cand + " ") && q.length() > cand.length() + 2) { best = i; bestLen = cand.length(); }
            }
        }
        if (best < 0) return null;
        // le titre garde sa forme d'origine (accents, majuscules) : on retire autant de mots que le nom
        String[] orig = query.trim().split("\\s+");
        String prefix = q.substring(0, bestLen);
        int skip = 0;
        StringBuilder acc = new StringBuilder();
        while (skip < orig.length && norm(acc.toString()).length() < prefix.length()) acc.append(orig[skip++]).append(' ');
        if (!norm(acc.toString()).equals(prefix) && !norm(acc.toString()).replace(" ", "").equals(prefix.replace(" ", ""))) return null;
        StringBuilder t = new StringBuilder();
        for (int i = skip; i < orig.length; i++) t.append(i > skip ? " " : "").append(orig[i]);
        String title = t.toString().trim();
        return title.length() < 2 ? null : new Object[]{best, title};
    }

    static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.FRANCE); }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.Locale;

/**
 * Règles pures (testées) de la mise à jour automatique. Lumen lit dist/latest.json dans la branche
 * main du dépôt du projet, puis dist/Lumen.apk. Contrôles : empreinte SHA-256 annoncée, taille, et
 * surtout la signature : un APK n'est proposé que s'il est signé avec la même clé que Lumen installé
 * (vérifié par Lumen, puis par Android).
 */
final class UpdatePolicy {
    private UpdatePolicy() { }

    static final String REPO = "jemguib-spec/lumen";
    static final String BASE = "https://raw.githubusercontent.com/" + REPO + "/main/dist/";
    static final String LATEST = BASE + "latest.json";
    static final String APK = BASE + "Lumen.apk";
    static final long MAX_BYTES = 40L << 20;

    /** « v4.1.0 » ou « 4.1.0 » → « 4.1.0 » (null si ce n'est pas un numéro de version). */
    static String version(String tag) {
        if (tag == null) return null;
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        return t.matches("\\d{1,4}(\\.\\d{1,4}){0,3}") ? t : null;
    }

    /** > 0 si a est plus récente que b (« 4.10 » > « 4.9 », « 4.0.1 » > « 4.0 »). */
    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0, q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    /** Empreinte SHA-256 en hexadécimal (64 caractères). */
    static boolean sha256Ok(String h) { return h != null && h.toLowerCase(Locale.ROOT).matches("[0-9a-f]{64}"); }

    static String hex(byte[] d) {
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format(Locale.ROOT, "%02x", x));
        return b.toString();
    }

    /** Signature de fichier ZIP/APK (« PK\3\4 »). */
    static boolean looksLikeApk(byte[] head) {
        return head != null && head.length >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4;
    }
}

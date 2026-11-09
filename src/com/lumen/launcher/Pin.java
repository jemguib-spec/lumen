// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Code PIN à 4 chiffres. Jamais stocké en clair : sel aléatoire + PBKDF2 (20 000 tours).
 * Après 5 erreurs, attente de 30 s, doublée à chaque nouvelle erreur (max 1 h). Java pur, testé.
 */
final class Pin {
    private Pin() { }

    static final int LENGTH = 4;
    static final int FREE_TRIES = 5;
    private static final int ROUNDS = 20_000;

    static boolean wellFormed(String pin) {
        if (pin == null || pin.length() != LENGTH) return false;
        for (int i = 0; i < pin.length(); i++) if (pin.charAt(i) < '0' || pin.charAt(i) > '9') return false;
        return true;
    }

    /** « sel:empreinte » en hexadécimal, à enregistrer. */
    static String store(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return hex(salt) + ":" + hex(derive(pin, salt));
    }

    /** Vérifie un code contre la valeur enregistrée (comparaison à temps constant). */
    static boolean check(String pin, String stored) {
        if (!wellFormed(pin) || stored == null) return false;
        int sep = stored.indexOf(':');
        if (sep <= 0) return false;
        byte[] salt = unhex(stored.substring(0, sep)), want = unhex(stored.substring(sep + 1));
        if (salt == null || want == null) return false;
        return MessageDigest.isEqual(derive(pin, salt), want);
    }

    /** Délai d'attente imposé après « fails » erreurs consécutives (0 = on peut essayer). */
    static long lockMillis(int fails) {
        if (fails < FREE_TRIES) return 0;
        long ms = 30_000L << Math.min(7, fails - FREE_TRIES);
        return Math.min(ms, 3_600_000L);
    }

    private static byte[] derive(String pin, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ROUNDS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 indisponible", e);
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x));
        return s.toString();
    }

    static byte[] unhex(String s) {
        if (s == null || s.length() % 2 != 0) return null;
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16), lo = Character.digit(s.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}

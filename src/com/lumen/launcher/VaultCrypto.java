// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Format des secrets chiffrés sur la TV (Java pur, testé) : « K1.<nonce hex>.<AES-GCM hex> ».
 * AES-256-GCM, nonce aléatoire de 12 octets, étiquette d'authenticité de 128 bits : un secret
 * modifié ou lu avec une autre clé est refusé. La clé vient de l'Android Keystore (voir Vault).
 */
final class VaultCrypto {
    private VaultCrypto() { }

    static final String PREFIX = "K1.";
    static final int NONCE = 12, TAG_BITS = 128, MAX = 8192;

    static boolean sealed(String s) { return s != null && s.startsWith(PREFIX); }

    static String seal(SecretKey key, String plain) throws Exception {
        byte[] nonce = new byte[NONCE];
        new SecureRandom().nextBytes(nonce);
        return seal(key, plain, nonce);
    }

    static String seal(SecretKey key, String plain, byte[] nonce) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        return PREFIX + Signer.hex(nonce) + "." + Signer.hex(c.doFinal(plain.getBytes("UTF-8")));
    }

    /** Texte en clair, ou null si le secret est mal formé, altéré ou chiffré avec une autre clé. */
    static String open(SecretKey key, String stored) {
        try {
            if (key == null || !sealed(stored)) return null;
            String[] p = stored.substring(PREFIX.length()).split("\\.", -1);
            if (p.length != 2 || !p[0].matches("[0-9a-f]{" + (NONCE * 2) + "}") || !p[1].matches("([0-9a-f]{2}){16," + MAX + "}")) return null;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, unhex(p[0])));
            return new String(c.doFinal(unhex(p[1])), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] unhex(String h) {
        byte[] b = new byte[h.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        return b;
    }
}

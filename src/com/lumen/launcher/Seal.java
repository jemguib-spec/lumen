// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Chiffrement des secrets envoyés par le téléphone (jeton Home Assistant, clés TMDB et Pexels,
 * code PIN, texte tapé) : la page n'est pas en HTTPS, mais le Wi-Fi ne voit plus jamais ces valeurs.
 *
 * Clé : HMAC-SHA256(clé du QR code, « lumen-chiffrement ») ; la clé du QR code n'est jamais envoyée.
 * Chiffrement : flux HMAC-SHA256(clé, nonce ‖ n° de bloc) combiné au texte (mode compteur), nonce
 * aléatoire de 16 octets. Format : « E1.<nonce hex>.<chiffré hex> ».
 * Intégrité : le message chiffré fait partie du contenu signé (Signer), qui refuse toute
 * modification et tout rejeu. Même calcul côté téléphone (phone.js). Java pur, testé.
 */
final class Seal {
    private Seal() { }

    static final String PREFIX = "E1.";
    /** Plus long secret accepté (texte, jeton Home Assistant…), en octets. */
    static final int MAX = 4096;

    /** Clé de chiffrement tirée de la clé du QR code. */
    static byte[] key(String qrKey) throws Exception {
        return hmac(qrKey.getBytes("UTF-8"), "lumen-chiffrement".getBytes("UTF-8"));
    }

    static String seal(byte[] key, byte[] plain) throws Exception {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        return seal(key, plain, nonce);
    }

    static String seal(byte[] key, byte[] plain, byte[] nonce) throws Exception {
        return PREFIX + Signer.hex(nonce) + "." + Signer.hex(xor(key, nonce, plain));
    }

    /** Texte en clair, ou null si le message n'est pas chiffré, mal formé ou trop long. */
    static byte[] open(byte[] key, String sealed) {
        try {
            if (key == null || sealed == null || !sealed.startsWith(PREFIX)) return null;
            String[] p = sealed.substring(PREFIX.length()).split("\\.", -1);
            if (p.length != 2 || !p[0].matches("[0-9a-f]{32}") || !p[1].matches("([0-9a-f]{2}){0," + MAX + "}")) return null;
            return xor(key, unhex(p[0]), unhex(p[1]));
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] xor(byte[] key, byte[] nonce, byte[] in) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] out = new byte[in.length];
        byte[] block = new byte[0];
        for (int i = 0; i < in.length; i++) {
            if (i % 32 == 0) {
                int n = i / 32;
                m.update(nonce);
                m.update(new byte[]{(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n});
                block = m.doFinal();
            }
            out[i] = (byte) (in[i] ^ block[i % 32]);
        }
        return out;
    }

    private static byte[] hmac(byte[] key, byte[] msg) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return m.doFinal(msg);
    }

    private static byte[] unhex(String h) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(h.length() / 2);
        for (int i = 0; i < h.length(); i += 2) b.write(Integer.parseInt(h.substring(i, i + 2), 16));
        return b.toByteArray();
    }
}

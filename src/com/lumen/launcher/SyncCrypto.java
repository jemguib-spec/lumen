// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Chiffrement de la copie des réglages entre TV : échange de clé authentifié par le code à 6
 * chiffres (SPAKE2, dans le groupe MODP de 2048 bits de la RFC 3526), puis AES-256-GCM.
 *
 * Pourquoi pas simplement « chiffrer avec le code » : 6 chiffres, c'est un million de possibilités,
 * qu'un ordinateur essaie en une seconde sur un envoi enregistré. Avec SPAKE2, quelqu'un qui écoute
 * le Wi-Fi ne voit que des nombres au hasard : il ne peut ni lire l'envoi ni retrouver le code ;
 * celui qui se fait passer pour une TV n'a qu'un essai de code par échange (et la TV qui reçoit
 * s'arrête après 5). Java pur, testé ; même calcul des deux côtés.
 *
 * Rôles : la TV qui reçoit publie Y = g^y · N^w ; la TV qui envoie répond X = g^x · M^w et le
 * contenu chiffré. w vient du code et du numéro aléatoire de la réception.
 */
final class SyncCrypto {
    private SyncCrypto() { }

    /** RFC 3526, groupe 14 : p premier sûr de 2048 bits, g = 2 engendre le sous-groupe d'ordre q = (p − 1) / 2. */
    static final BigInteger P = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD"
            + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
            + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
            + "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
            + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
            + "15728E5A8AACAA68FFFFFFFFFFFFFFFF", 16);
    static final BigInteger Q = P.shiftRight(1);
    static final BigInteger G = BigInteger.valueOf(2);
    /** Éléments fixes dont personne ne connaît le logarithme (tirés d'un hachage public). */
    static final BigInteger M = hashToGroup("lumen-sync M"), N = hashToGroup("lumen-sync N");
    /** Taille d'un nombre du groupe écrit en hexadécimal. */
    static final int HEX = 512;

    /** Élément du sous-groupe d'ordre q tiré d'une étiquette publique (hachage étendu, puis carré). */
    static BigInteger hashToGroup(String label) {
        try {
            byte[] out = new byte[320];
            for (int i = 0; i * 32 < out.length; i++) {
                byte[] d = MessageDigest.getInstance("SHA-256").digest((label + "#" + i).getBytes("UTF-8"));
                System.arraycopy(d, 0, out, i * 32, Math.min(32, out.length - i * 32));
            }
            BigInteger h = new BigInteger(1, out).mod(P);
            return h.multiply(h).mod(P);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Secret partagé w, tiré du code et du numéro de la réception. */
    static BigInteger w(String code, String nonce) {
        try {
            return new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(("lumen-sync w\n" + code + "\n" + nonce).getBytes("UTF-8"))).mod(Q);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Exposant secret de 256 bits, jamais nul. */
    static BigInteger secret(SecureRandom r) {
        BigInteger x;
        do { x = new BigInteger(256, r); } while (x.signum() == 0);
        return x;
    }

    /** g^e · mask^w : valeur publiée par chaque TV. */
    static BigInteger blind(BigInteger e, BigInteger w, BigInteger mask) {
        return G.modPow(e, P).multiply(mask.modPow(w, P)).mod(P);
    }

    /** Valeur reçue acceptable : dans le sous-groupe d'ordre q, ni 0, ni 1, ni p − 1. */
    static boolean valid(BigInteger v) {
        return v != null && v.compareTo(BigInteger.ONE) > 0 && v.compareTo(P.subtract(BigInteger.ONE)) < 0
                && v.modPow(Q, P).equals(BigInteger.ONE);
    }

    /** (peer / mask^w)^e : la même valeur des deux côtés si, et seulement si, les deux codes sont égaux. */
    static BigInteger shared(BigInteger peer, BigInteger w, BigInteger mask, BigInteger e) {
        BigInteger unmasked = peer.multiply(mask.modPow(Q.subtract(w).mod(Q), P)).mod(P);
        return unmasked.modPow(e, P);
    }

    static String hex(BigInteger v) {
        String s = v.toString(16);
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < HEX; i++) b.append('0');
        return b.append(s).toString();
    }

    /** Nombre du groupe écrit en hexadécimal (512 chiffres), sinon null. */
    static BigInteger parse(String hex) {
        if (hex == null || hex.length() != HEX || !hex.matches("[0-9a-f]+")) return null;
        return new BigInteger(hex, 16);
    }

    /** Clé AES-256 de l'échange : tout ce qui a été échangé, plus K et w. */
    static byte[] sessionKey(String nonce, BigInteger x, BigInteger y, BigInteger k, BigInteger w) {
        try {
            String t = "lumen-sync 2\n" + nonce + "\n" + hex(x) + "\n" + hex(y) + "\n" + hex(k) + "\n" + w.toString(16);
            return MessageDigest.getInstance("SHA-256").digest(t.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Données associées (non chiffrées mais protégées) : version, réception, valeurs publiques. */
    static byte[] aad(String nonce, BigInteger x, BigInteger y) {
        try { return ("lumen-sync 2\n" + nonce + "\n" + hex(x) + "\n" + hex(y)).getBytes("UTF-8"); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** AES-256-GCM : numéro de 12 octets au hasard, puis le texte chiffré et son étiquette. */
    static byte[] seal(byte[] key, byte[] plain, byte[] aad, SecureRandom r) throws Exception {
        byte[] iv = new byte[12];
        r.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        c.updateAAD(aad);
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[12 + ct.length];
        System.arraycopy(iv, 0, out, 0, 12);
        System.arraycopy(ct, 0, out, 12, ct.length);
        return out;
    }

    /** Déchiffre ; null si la clé n'est pas la même (mauvais code) ou si quoi que ce soit a été modifié. */
    static byte[] open(byte[] key, byte[] sealed, byte[] aad) {
        try {
            if (sealed == null || sealed.length < 12 + 16) return null;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, sealed, 0, 12));
            c.updateAAD(aad);
            return c.doFinal(sealed, 12, sealed.length - 12);
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] unhex(String s) {
        if (s == null || (s.length() & 1) != 0 || !s.matches("[0-9a-f]*")) return null;
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    // ------------------------------------------------------------------ côté qui envoie

    /** Corps de l'envoi : « X en hexadécimal » + « \n » + contenu chiffré en hexadécimal. */
    static byte[] senderBody(String code, String nonce, BigInteger y, byte[] plain, SecureRandom r) throws Exception {
        BigInteger w = w(code, nonce);
        BigInteger x = secret(r);
        BigInteger bx = blind(x, w, M);
        BigInteger k = shared(y, w, N, x);
        byte[] sealed = seal(sessionKey(nonce, bx, y, k, w), plain, aad(nonce, bx, y), r);
        return (hex(bx) + "\n" + Signer.hex(sealed)).getBytes("UTF-8");
    }

    // ------------------------------------------------------------------ côté qui reçoit

    /** Une réception : son exposant secret et sa valeur publique, renouvelés après chaque mauvais code. */
    static final class Receiver {
        final String code, nonce;
        private final BigInteger w;
        private BigInteger y, by;
        private final SecureRandom r;

        Receiver(String code, String nonce, SecureRandom r) {
            this.code = code;
            this.nonce = nonce;
            this.r = r;
            this.w = w(code, nonce);
            renew();
        }

        synchronized void renew() { y = secret(r); by = blind(y, w, N); }
        synchronized BigInteger published() { return by; }

        /** Contenu en clair si le code est le bon et que rien n'a été modifié, sinon null. */
        synchronized byte[] open(byte[] body) {
            try {
                String s = new String(body, "UTF-8");
                int nl = s.indexOf('\n');
                if (nl != HEX) return null;
                BigInteger bx = parse(s.substring(0, nl));
                byte[] sealed = unhex(s.substring(nl + 1));
                if (!valid(bx) || sealed == null) return null;
                BigInteger k = shared(bx, w, M, y);
                return SyncCrypto.open(sessionKey(nonce, bx, by, k, w), sealed, aad(nonce, bx, by));
            } catch (Exception e) {
                return null;
            }
        }
    }
}

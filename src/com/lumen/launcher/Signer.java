// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signature des échanges téléphone ↔ TV (HMAC-SHA256). La clé est dans le QR code, après « # » :
 * le navigateur ne l'envoie jamais sur le réseau. Chaque requête porte un numéro qui ne peut
 * servir qu'une fois (pas de rejeu) et une signature sur méthode, adresse, numéro et contenu.
 * Sans la clé, impossible de fabriquer ou de modifier une requête, même en écoutant le Wi-Fi.
 * Java pur, testé.
 */
final class Signer {
    private final byte[] key;
    private long highest = 0;
    private final Set<Long> recent = new HashSet<>();
    static final int WINDOW = 256;

    Signer(String key) throws Exception { this.key = key.getBytes("UTF-8"); }

    static String hmacHex(byte[] key, String msg) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return hex(m.doFinal(msg.getBytes("UTF-8")));
    }

    static String sha256Hex(byte[] data) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(data)); }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x));
        return s.toString();
    }

    /** Texte signé d'une requête HTTP. body null = contenu non signé (envoi de fichiers). */
    static String httpMessage(String method, String target, long seq, String bodyHash) {
        return method + "\n" + target + "\n" + seq + "\n" + (bodyHash == null ? "-" : bodyHash);
    }

    static String wsMessage(long seq, String payload) { return "ws\n" + seq + "\n" + payload; }

    /** Signature juste et numéro jamais vu (dans une fenêtre, pour les requêtes parallèles). */
    synchronized boolean verify(long seq, String msg, String sigHex) {
        try {
            if (seq <= 0 || sigHex == null || !sigHex.matches("[0-9a-f]{64}")) return false;
            if (!MessageDigest.isEqual(hmacHex(key, msg).getBytes("UTF-8"), sigHex.getBytes("UTF-8"))) return false;
            if (seq <= highest - WINDOW || recent.contains(seq)) return false; // rejeu
            recent.add(seq);
            if (seq > highest) highest = seq;
            if (recent.size() > WINDOW * 2) {
                java.util.Iterator<Long> it = recent.iterator();
                while (it.hasNext()) if (it.next() <= highest - WINDOW) it.remove();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

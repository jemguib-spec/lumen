// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Liaison de deux TV de la maison : règles pures de la mise en relation (Java pur, testé). Le code
 * à 6 chiffres affiché par la TV qui reçoit, les essais permis, le « bonjour » qu'elle répond, les
 * adresses tapées à la main. L'échange est chiffré avec une clé établie grâce au code (SyncCrypto) ;
 * ce qui voyage et la fusion sont dans SyncState, le transport dans SyncLink et PairLink.
 */
final class TvSync {
    private TvSync() { }

    /** Type de service annoncé sur le réseau local (DNS-SD / mDNS). */
    static final String SERVICE_TYPE = "_lumen-sync._tcp";
    static final int PORT = 8766;
    static final int CODE_LENGTH = 6;
    /** Mauvais codes permis avant d'arrêter la réception. */
    static final int MAX_TRIES = 5;
    /** La TV qui reçoit attend au plus ce temps. */
    static final long OPEN_MS = 3 * 60_000L;
    static final int MAX_BODY = 1024 * 1024;
    static final String FORMAT = "lumen-sync";
    /**
     * 3 : liaison permanente (tout l'état, identifiants des TV). 2 (Lumen 4.33) : copie ponctuelle
     * chiffrée. 1 (4.31-4.32) : copie signée seulement. Les anciennes versions sont reconnues.
     */
    static final int VERSION = 3;
    static final String HELLO = "/lumen-sync/hello", SEND = "/lumen-sync/send";

    // ------------------------------------------------------------------ code et signature

    static String newCode(SecureRandom r) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) b.append((char) ('0' + r.nextInt(10)));
        return b.toString();
    }

    /** Numéro aléatoire de la TV qui reçoit (32 chiffres hexadécimaux), mêlé à la clé de signature. */
    static String newNonce(SecureRandom r) {
        byte[] b = new byte[16];
        r.nextBytes(b);
        return Signer.hex(b);
    }

    static boolean codeOk(String code) { return code != null && code.matches("[0-9]{" + CODE_LENGTH + "}"); }
    static boolean nonceOk(String nonce) { return nonce != null && nonce.matches("[0-9a-f]{32}"); }

    /** « 427913 » s'affiche « 427 913 ». */
    static String spaced(String code) {
        return code == null || code.length() != CODE_LENGTH ? String.valueOf(code) : code.substring(0, 3) + " " + code.substring(3);
    }

    /**
     * Essais de code de la TV qui reçoit : au bout de MAX_TRIES mauvais codes, ou après OPEN_MS, elle
     * n'accepte plus rien ; un seul envoi réussi la ferme aussi. Après chaque mauvais code, sa valeur
     * publique change (l'autre TV la relit avant chaque essai).
     */
    static final class Gate {
        private final SyncCrypto.Receiver rx;
        private final long until;
        private int fails;
        private boolean done;
        private byte[] plain;

        Gate(String code, String nonce, long now, SecureRandom r) { this.rx = new SyncCrypto.Receiver(code, nonce, r); this.until = now + OPEN_MS; }

        static final int OK = 0, BAD_CODE = 1, CLOSED = 2;

        synchronized int check(byte[] body, long now) {
            if (done || now > until || fails >= MAX_TRIES) return CLOSED;
            byte[] p = rx.open(body);
            if (p != null) { done = true; plain = p; return OK; }
            fails++;
            rx.renew();
            return BAD_CODE;
        }

        /** Contenu déchiffré après OK. */
        synchronized byte[] plain() { return plain; }
        synchronized boolean closed(long now) { return done || now > until || fails >= MAX_TRIES; }
        synchronized int fails() { return fails; }
        String nonce() { return rx.nonce; }
        String code() { return rx.code; }
        String published() { return SyncCrypto.hex(rx.published()); }
        /** Clé de l'échange réussi (null avant). */
        byte[] key() { return rx.key(); }
    }

    // ------------------------------------------------------------------ « bonjour » de la TV qui reçoit

    /**
     * Réponse à HELLO : « lumen-sync 3 », le numéro aléatoire, la valeur publique (SyncCrypto), le nom
     * de la TV et son identifiant (pour la retrouver ensuite sur le réseau).
     */
    static String helloBody(String nonce, String published, String name, String deviceId) {
        return FORMAT + " " + VERSION + "\n" + nonce + "\n" + published + "\n" + cleanName(name) + "\n" + deviceId;
    }

    /** {numéro, nom, valeur publique, identifiant} ou null si ce n'est pas une TV Lumen compatible. */
    static String[] parseHello(String s) {
        if (s == null || s.length() > 800) return null;
        String[] l = s.split("\n", -1);
        if (l.length != 5 || !l[0].equals(FORMAT + " " + VERSION) || !nonceOk(l[1]) || !SyncCrypto.valid(SyncCrypto.parse(l[2]))
                || !SyncState.deviceIdOk(l[4])) return null;
        return new String[]{l[1], cleanName(l[3]), l[2], l[4]};
    }

    /** Une TV en Lumen 4.31 à 4.33 (copie ponctuelle) : il faut la mettre à jour. */
    static boolean olderPeer(String s) { return s != null && (s.startsWith(FORMAT + " 1\n") || s.startsWith(FORMAT + " 2\n")); }

    // ------------------------------------------------------------------ Home Assistant reçu

    /** Empreinte de certificat Home Assistant : vide (certificat reconnu, ou http) ou SHA-256 en hexadécimal. */
    static boolean certOk(String pin) { return pin != null && (pin.isEmpty() || pin.matches("[0-9a-f]{64}")); }

    /** Nom de TV affichable : court, sans caractère de contrôle. */
    static String cleanName(String s) {
        if (s == null) return "TV";
        String t = s.trim().replaceAll("\\s+", " ");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length() && b.length() < 30; i++) { // nom de service réseau : 63 octets au plus
            char c = t.charAt(i);
            if (c >= 0x20 && c != 0x7F) b.append(c);
        }
        String r = b.toString().trim();
        return r.isEmpty() ? "TV" : r;
    }

    /**
     * Adresse tapée à la main (« 192.168.1.20 », ou « 192.168.1.20:8767 » si la TV l'affiche) :
     * {adresse, port} si c'est une adresse IPv4 du réseau privé, sinon null.
     */
    static Object[] address(String s) {
        if (s == null) return null;
        String t = s.trim().replace(',', '.').replace(' ', '.');
        int port = PORT;
        int c = t.indexOf(':');
        if (c >= 0) {
            String p = t.substring(c + 1);
            if (!p.matches("[0-9]{1,5}")) return null;
            port = Integer.parseInt(p);
            if (port < 1024 || port > 65535) return null;
            t = t.substring(0, c);
        }
        if (!t.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return null;
        String[] p = t.split("\\.");
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) { n[i] = Integer.parseInt(p[i]); if (n[i] > 255) return null; }
        boolean priv = n[0] == 10 || (n[0] == 172 && n[1] >= 16 && n[1] <= 31) || (n[0] == 192 && n[1] == 168);
        return priv ? new Object[]{n[0] + "." + n[1] + "." + n[2] + "." + n[3], port} : null;
    }

    // ------------------------------------------------------------------ réponse HTTP (côté qui envoie)

    /** {code HTTP, corps} d'une réponse courte ; erreur si elle est mal formée ou trop longue. */
    static Object[] readResponse(InputStream in, int max) throws IOException {
        String status = line(in);
        if (status == null || !status.startsWith("HTTP/1.")) throw new IOException("Réponse invalide");
        String[] p = status.split(" ");
        int code;
        try { code = Integer.parseInt(p[1]); } catch (Exception e) { throw new IOException("Réponse invalide"); }
        long len = -1;
        for (int i = 0; ; i++) {
            if (i > 30) throw new IOException("Trop d'en-têtes");
            String h = line(in);
            if (h == null || h.isEmpty()) break;
            int c = h.indexOf(':');
            if (c > 0 && h.substring(0, c).trim().toLowerCase(Locale.ROOT).equals("content-length")) {
                try { len = Long.parseLong(h.substring(c + 1).trim()); } catch (NumberFormatException e) { throw new IOException("Longueur invalide"); }
            }
        }
        if (len < 0 || len > max) throw new IOException("Longueur invalide");
        return new Object[]{code, new String(HttpRequest.readBody(in, len, max), "UTF-8")};
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') return b.toString("UTF-8");
            if (c != '\r') b.write(c);
            if (b.size() > HttpRequest.MAX_LINE) throw new IOException("Ligne trop longue");
        }
        return b.size() == 0 ? null : b.toString("UTF-8");
    }
}

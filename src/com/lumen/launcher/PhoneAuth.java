package com.lumen.launcher;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Authentification de la page téléphone (Java pur, testable).
 * - Le QR code contient un jeton aléatoire de 128 bits, à usage unique, valable 10 minutes.
 * - Il est placé après « # » dans l'adresse : le navigateur ne l'envoie jamais tel quel sur le
 *   réseau ; la page l'échange une seule fois contre une session (cookie HttpOnly).
 * - Sessions : 2 au plus, expirent après 30 minutes sans activité.
 * - 5 échecs d'authentification → verrouillage (le serveur s'arrête).
 */
final class PhoneAuth {
    static final long TOKEN_TTL_MS = 10 * 60_000L;
    static final long SESSION_IDLE_MS = 30 * 60_000L;
    static final int MAX_SESSIONS = 2;
    static final int MAX_FAILURES = 5;

    interface Clock { long now(); }

    private final SecureRandom rnd = new SecureRandom();
    private final Clock clock;
    private String token;
    private long tokenExpiry;
    private final Map<String, Long> sessions = new HashMap<>();
    private int failures;

    PhoneAuth(Clock clock) { this.clock = clock; }

    /** Nouveau jeton (annule l'ancien et toutes les sessions). */
    synchronized String newToken() {
        token = random(22);
        tokenExpiry = clock.now() + TOKEN_TTL_MS;
        sessions.clear();
        failures = 0;
        return token;
    }

    /** Échange le jeton contre une session ; null si refusé. Le jeton ne sert qu'une fois. */
    synchronized String openSession(String presented) {
        if (locked()) return null;
        boolean ok = token != null && presented != null && clock.now() < tokenExpiry && equal(token, presented);
        if (!ok) { failures++; return null; }
        token = null; // usage unique
        purge();
        if (sessions.size() >= MAX_SESSIONS) return null;
        String id = random(32);
        sessions.put(id, clock.now());
        return id;
    }

    /** Session valide ? Prolonge sa durée de vie. */
    synchronized boolean check(String sessionId) {
        if (sessionId == null || locked()) return false;
        purge();
        for (Map.Entry<String, Long> e : sessions.entrySet()) {
            if (equal(e.getKey(), sessionId)) { e.setValue(clock.now()); return true; }
        }
        failures++;
        return false;
    }

    synchronized boolean locked() { return failures >= MAX_FAILURES; }

    synchronized boolean hasSessions() { purge(); return !sessions.isEmpty(); }

    private void purge() {
        long now = clock.now();
        for (Iterator<Map.Entry<String, Long>> it = sessions.entrySet().iterator(); it.hasNext(); )
            if (now - it.next().getValue() > SESSION_IDLE_MS) it.remove();
    }

    /** Comparaison en temps constant (pas de fuite d'information par la durée). */
    static boolean equal(String a, String b) {
        try {
            return MessageDigest.isEqual(a.getBytes("UTF-8"), b.getBytes("UTF-8"));
        } catch (Exception e) {
            return false;
        }
    }

    private String random(int len) {
        String a = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder b = new StringBuilder(len);
        for (int i = 0; i < len; i++) b.append(a.charAt(rnd.nextInt(a.length())));
        return b.toString();
    }
}

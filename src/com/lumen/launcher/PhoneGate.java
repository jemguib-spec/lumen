// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Contrôles d'accès de la page du téléphone, dans l'ordre où PhoneServer les applique
 * (Java pur, testé hors Android) :
 * Host → X-Lumen → session et appareil → acceptée sur la TV → « télécommande seulement »
 * → signature → limite de débit → code PIN pour les actions sensibles.
 * Chaque contrôle renvoie null (accepté) ou le refus à envoyer au téléphone.
 */
final class PhoneGate {
    private PhoneGate() { }

    /** Refus : code HTTP et corps JSON (vide pour les refus sans explication). */
    static final class Refusal {
        final int code;
        final String body;
        Refusal(int code, String body) { this.code = code; this.body = body; }
        @Override public String toString() { return code + " " + body; }
    }

    static final Refusal BAD_HOST = new Refusal(421, "");
    static final Refusal NO_HEADER = new Refusal(403, "");
    static final Refusal NO_SESSION = new Refusal(401, "{\"ok\":false}");
    static final Refusal PENDING = new Refusal(403, "{\"ok\":false,\"pending\":true}");
    static final Refusal LIMITED = new Refusal(403, "{\"ok\":false,\"limited\":true}");
    static final Refusal BAD_SIG = new Refusal(401, "{\"ok\":false,\"sig\":true}");
    static final Refusal SLOW = new Refusal(429, "{\"ok\":false,\"slow\":true}");
    static final Refusal PIN = new Refusal(200, "{\"ok\":false,\"pin\":true}");

    /** Touches acceptées depuis le téléphone (liste fermée). */
    static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(
            "up", "down", "left", "right", "ok", "back", "home", "volup", "voldown", "mute", "play", "sleep"));

    /** Actions permises en « télécommande seulement ». */
    static final Set<String> REMOTE_ONLY = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "/api/state", "/api/icon", "/api/key", "/api/text", "/api/ping", "/api/launch", "/api/bye", "/api/name",
            "/api/closeapp", "/api/next", "/api/veille", "/api/ambiance", "/api/timer", "/api/pin", "/api/pickthumb")));

    /** Images demandées par la page via <img> (pas d'en-tête possible) : lecture seule, sans signature ni limite. */
    static final Set<String> IMAGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "/api/icon", "/api/thumb", "/api/pickthumb", "/api/wallthumb")));

    /** Écritures qui exigent le code PIN de la TV (s'il existe) déjà saisi sur ce téléphone. */
    static final Set<String> PIN_PATHS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "/api/ha", "/api/tmdb", "/api/pexels", "/api/restore", "/api/lock")));

    static boolean post(HttpRequest r) { return "POST".equals(r.method); }

    static boolean isImage(HttpRequest r) { return !post(r) && IMAGES.contains(r.path); }

    /** Touches et frappe en direct : limite plus large (défilement continu). */
    static boolean isFast(HttpRequest r) { return post(r) && (r.path.equals("/api/key") || r.path.equals("/api/text")); }

    /** Fichiers de la page : publics, sans donnée. */
    static boolean isPageAsset(HttpRequest r) {
        String p = r.path;
        return !post(r) && (p.equals("/") || p.equals("/index.html") || p.equals("/app.js") || p.equals("/app.css"));
    }

    /** Anti « DNS rebinding » : la page doit être demandée par l'adresse de la TV. */
    static Refusal host(HttpRequest r, String expectedHost) {
        return expectedHost != null && expectedHost.equals(r.header("host")) ? null : BAD_HOST;
    }

    /** Toute requête d'API exige X-Lumen (un autre site ne peut pas l'ajouter), sauf les images. */
    static Refusal apiHeader(HttpRequest r) {
        return isImage(r) || "1".equals(r.header("x-lumen")) ? null : NO_HEADER;
    }

    /** Session valide, depuis l'appareil qui a scanné le QR code, acceptée sur la TV, et dans les droits. */
    static Refusal access(HttpRequest r, boolean sessionOk, boolean sameDevice, boolean approved, boolean remoteOnly) {
        if (!sessionOk || !sameDevice) return NO_SESSION;
        if (!approved) return PENDING;
        if (remoteOnly && !REMOTE_ONLY.contains(r.path)) return LIMITED;
        return null;
    }

    /** Signature HMAC exigée partout, sauf pour les images. */
    static Refusal signature(HttpRequest r, boolean signedOk) {
        return isImage(r) || signedOk ? null : BAD_SIG;
    }

    /** Limite de débit (les images ne comptent pas). */
    static Refusal rate(HttpRequest r, RateLimit actions, RateLimit keys, long now) {
        if (isImage(r)) return null;
        return (isFast(r) ? keys : actions).allow(now) ? null : SLOW;
    }

    /** Action sensible sans le code PIN saisi sur ce téléphone. */
    static Refusal pin(HttpRequest r, boolean pinNeeded) {
        return pinNeeded && post(r) && PIN_PATHS.contains(r.path) ? PIN : null;
    }

    /**
     * Ouverture de la connexion permanente (WebSocket) : 0 si permise, sinon le code HTTP du refus.
     * Même session que le reste, page servie par la TV uniquement (Origin), 2 connexions au plus.
     */
    static int webSocket(HttpRequest r, String expectedHost, boolean sessionOk, boolean approved, boolean sameDevice, int open) {
        String up = r.header("upgrade");
        boolean upgrade = up != null && up.equalsIgnoreCase("websocket");
        boolean sameOrigin = expectedHost != null && ("http://" + expectedHost).equals(r.header("origin"));
        if (!upgrade || !sameOrigin || !WsFrames.validKey(r.header("sec-websocket-key"))
                || !sessionOk || !approved || !sameDevice) return 403;
        return open >= 2 ? 429 : 0;
    }

    /**
     * Message de la connexion permanente « S <numéro> <signature> <contenu> » : renvoie le contenu
     * si la signature est vraie et le numéro jamais vu, et si c'est une commande connue
     * (« k <touche> » de la liste fermée, « t0 texte » ou « t1 texte ») ; sinon null.
     */
    static String wsCommand(String m, Signer signer) {
        if (signer == null || m == null || !m.startsWith("S ")) return null;
        String[] parts = m.split(" ", 4);
        if (parts.length < 4) return null;
        long seq;
        try { seq = Long.parseLong(parts[1]); } catch (Exception e) { return null; }
        if (!signer.verify(seq, Signer.wsMessage(seq, parts[3]), parts[2])) return null;
        String c = parts[3];
        if (c.startsWith("k ")) return KEYS.contains(c.substring(2).trim()) ? c : null;
        if (c.startsWith("t0 ") || c.startsWith("t1 ") || c.equals("t0") || c.equals("t1")) return c;
        return null;
    }
}

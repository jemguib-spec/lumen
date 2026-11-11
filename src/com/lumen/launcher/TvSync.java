// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Copie des réglages d'une TV à une autre sur le même réseau : règles pures (Java pur, testé).
 * Quels réglages voyagent (par groupe, à cocher), lesquels restent propres à chaque TV, le code à
 * 6 chiffres affiché par la TV qui reçoit, et les essais permis. L'envoi est chiffré avec une clé
 * établie grâce au code (SyncCrypto) ; le transport est dans SyncLink.
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
    static final int MAX_BODY = 256 * 1024;
    static final String FORMAT = "lumen-sync";
    /** 2 : envoi chiffré (SyncCrypto), clés et météo comprises. 1 (Lumen 4.31 et 4.32) : signé seulement. */
    static final int VERSION = 2;
    static final String HELLO = "/lumen-sync/hello", SEND = "/lumen-sync/send";

    // groupes proposés à la TV qui envoie (tous cochés par défaut)
    static final int LOOK = 0, WALL = 1, HOME = 2, APPS = 3, KEYS = 4;
    static final String[] GROUP_NAMES = {"Apparence et sons", "Fond d'écran, veille et météo", "Accueil et boutons", "Favoris et applis masquées",
            "Clés et services (TMDB, Pexels, Home Assistant)"};

    private static final String[][] GROUPS = {
            // apparence et sons
            {"app_glow", "show_icons", "tint_tiles", "tile_style", "text_scale", "high_contrast", "clock", "immersive",
             "sounds", "sound_theme", "sound_volume", "ambiance_sound", "ambiance_kind"},
            // fond d'écran et veille
            {"source", "interval", "follow_time", "living_scenes", "seasonal", "weather_fx", "video_theme", "cities", "hidden_walls",
             "idle", "ambient_min", "veille_src", "night_hour"},
            // accueil et boutons
            {"home_cols", "home_rows", "greeting", "top_shelf", "shelf_posters", "habits_on", "notif_on", "notif_secs", "notif_muted",
             "btn_notifs", "btn_search", "btn_ambiances", "btn_inputs", "btn_profiles", "btn_apps", "btn_tv", "btn_settings", "sound_button",
             "ha_button", "ha_cine_open", "ha_film", "ha_pause", "ha_pinned", "ha_home", "ha_cine_apps"},
            // favoris et applis
            {"favorites", "hidden_apps", "locked_apps"},
            // clés et services : hors de la sauvegarde, envoyés à part (voir SyncPanel), jamais en clair
            {},
    };

    /**
     * Réglages de la sauvegarde qui restent propres à chaque TV : habitudes de cette TV, touches de
     * sa télécommande, vidéos téléchargées et place qu'elles prennent, qualité selon l'écran,
     * mises à jour et mesures de diagnostic.
     */
    static final String[] LOCAL = {"habits", "remote_keys", "remote_ask", "video_ok", "video_disk_gb", "video_4k", "cosy_wanted",
            "auto_update", "show_perf"};

    /** Groupe d'un réglage, ou -1 s'il ne voyage jamais (propre à la TV, secret, inconnu). */
    static int groupOf(String key) {
        if (key == null) return -1;
        for (int g = 0; g < GROUPS.length; g++) for (String k : GROUPS[g]) if (k.equals(key)) return g;
        return -1;
    }

    static boolean local(String key) {
        for (String k : LOCAL) if (k.equals(key)) return true;
        return false;
    }

    private static final Set<String> NEVER_RESET = new java.util.HashSet<>(java.util.Arrays.asList("locked_apps", "source"));

    /** Tous les réglages des groupes choisis (doublons et groupes inconnus ignorés). */
    static Set<String> keysOf(boolean[] groups) {
        Set<String> out = new LinkedHashSet<>();
        if (groups == null) return out;
        for (int g = 0; g < GROUPS.length && g < groups.length; g++) if (groups[g]) Collections.addAll(out, GROUPS[g]);
        return out;
    }

    /**
     * Réglages à remettre par défaut sur la TV qui reçoit : ceux des groupes choisis que la TV qui
     * envoie n'a jamais changés (absents de l'envoi). Sinon un réglage changé ici seulement resterait
     * différent. Jamais remis à zéro : les applis verrouillées (un envoi ajoute des verrous, n'en
     * enlève pas) et la source du fond (toujours envoyée, sauf « Mes photos et vidéos », qui ne sont
     * que sur l'autre TV : la TV qui reçoit garde alors la sienne).
     */
    static List<String> resetKeys(boolean[] groups, Set<String> sent) {
        List<String> out = new ArrayList<>();
        for (String k : keysOf(groups)) if (!sent.contains(k) && !NEVER_RESET.contains(k)) out.add(k);
        return out;
    }

    /** Groupes écrits « 0,1,3 » dans l'envoi. */
    static String groupsText(boolean[] groups) {
        StringBuilder b = new StringBuilder();
        for (int g = 0; g < GROUPS.length && g < groups.length; g++) if (groups[g]) b.append(b.length() == 0 ? "" : ",").append(g);
        return b.toString();
    }

    static boolean[] parseGroups(String s) {
        boolean[] out = new boolean[GROUPS.length];
        if (s == null || s.length() > 20) return out;
        for (String p : s.split(",")) {
            if (p.length() == 1 && p.charAt(0) >= '0' && p.charAt(0) < '0' + GROUPS.length) out[p.charAt(0) - '0'] = true;
        }
        return out;
    }

    static boolean any(boolean[] groups) {
        for (boolean b : groups) if (b) return true;
        return false;
    }

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
    }

    // ------------------------------------------------------------------ « bonjour » de la TV qui reçoit

    /** Réponse à HELLO : « lumen-sync 2 », le numéro aléatoire, la valeur publique (SyncCrypto), le nom de la TV. */
    static String helloBody(String nonce, String published, String name) {
        return FORMAT + " " + VERSION + "\n" + nonce + "\n" + published + "\n" + cleanName(name);
    }

    /** {numéro, nom, valeur publique} ou null si ce n'est pas une TV Lumen compatible. */
    static String[] parseHello(String s) {
        if (s == null || s.length() > 800) return null;
        String[] l = s.split("\n", -1);
        if (l.length != 4 || !l[0].equals(FORMAT + " " + VERSION) || !nonceOk(l[1]) || !SyncCrypto.valid(SyncCrypto.parse(l[2]))) return null;
        return new String[]{l[1], cleanName(l[3]), l[2]};
    }

    /** Une TV en Lumen 4.31 ou 4.32 (envoi non chiffré, sans les clés) : il faut la mettre à jour. */
    static boolean olderPeer(String s) { return s != null && s.startsWith(FORMAT + " 1\n"); }

    // ------------------------------------------------------------------ clés et météo reçues

    /** Ville de la météo reçue : texte court, sans caractère de contrôle ; coordonnées sur la Terre. */
    static boolean cityOk(String city, double lat, double lon) {
        return city != null && !city.trim().isEmpty() && Backup.textOk(city, 80)
                && !Double.isNaN(lat) && !Double.isNaN(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

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

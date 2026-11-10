// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Home Assistant de la maison (API REST officielle, jeton « longue durée » créé par l'utilisateur).
 *
 * Sécurité :
 * - Seule exception à la liste blanche de Lumen : l'adresse de SON Home Assistant, réglée par lui.
 * - En HTTP (réseau local), l'adresse doit être privée (192.168.x, 10.x…) : le jeton ne part jamais
 *   en clair sur Internet. En HTTPS, n'importe quel hôte (ex. accès à distance Nabu Casa).
 * - Jamais de redirection suivie (le jeton n'irait pas ailleurs).
 * - Actions en liste fermée : allumer/éteindre, scène, script, bouton, volet, lecture. Pas de
 *   serrure ni d'alarme depuis la TV.
 * - Le jeton n'est jamais renvoyé au téléphone.
 * Java pur, testé (la lecture JSON des états est dans HaStates).
 */
final class HomeAssistant {
    private HomeAssistant() { }

    static final int TIMEOUT_MS = 6000;
    static final int MAX_BODY = 3 << 20;
    /** Domaines pilotables depuis la TV, dans l'ordre d'affichage. */
    static final List<String> DOMAINS = Collections.unmodifiableList(java.util.Arrays.asList(
            "scene", "light", "switch", "fan", "cover", "media_player", "script", "input_boolean", "button"));
    private static final String[] DOMAIN_NAMES = {"Scènes", "Lumières", "Prises et interrupteurs", "Ventilateurs",
            "Volets", "Lecteurs", "Scripts", "Commutateurs", "Boutons"};

    static String domainName(String d) {
        int i = DOMAINS.indexOf(d);
        return i < 0 ? d : DOMAIN_NAMES[i];
    }

    // ------------------------------------------------------------------ adresse et jeton

    /**
     * « homeassistant.local:8123 », « 192.168.1.20:8123 », « https://maison.ui.nabu.casa » → adresse
     * de base normalisée (« http://192.168.1.20:8123 »), ou null si refusée.
     */
    static String normalizeUrl(String in) {
        if (in == null) return null;
        String s = in.trim();
        if (s.isEmpty() || s.length() > 200) return null;
        if (!s.contains("://")) s = "http://" + s;
        try {
            URL u = new URL(s);
            String scheme = u.getProtocol().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) return null;
            if (u.getUserInfo() != null || u.getQuery() != null || u.getRef() != null) return null;
            String path = u.getPath();
            if (path != null && !path.isEmpty() && !path.equals("/")) return null;
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if (!host.matches("[a-z0-9.-]{1,120}") || host.startsWith(".") || host.endsWith(".")) return null;
            if (scheme.equals("http") && !localHostName(host)) return null; // en clair : réseau local uniquement
            int port = u.getPort();
            if (port == 0 || port > 65535) return null;
            return scheme + "://" + host + (port > 0 ? ":" + port : "");
        } catch (Exception e) {
            return null;
        }
    }

    /** Nom ou adresse qui désigne le réseau local (vérifié à nouveau après résolution). */
    static boolean localHostName(String host) {
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa") || host.endsWith(".home")) return true;
        if (!host.contains(".")) return true; // nom court du réseau (« homeassistant »)
        return privateIpv4(host);
    }

    static boolean privateIpv4(String host) {
        String[] p = host.split("\\.");
        if (p.length != 4) return false;
        int[] b = new int[4];
        try { for (int i = 0; i < 4; i++) { b[i] = Integer.parseInt(p[i]); if (b[i] < 0 || b[i] > 255) return false; } }
        catch (NumberFormatException e) { return false; }
        return b[0] == 10 || (b[0] == 172 && b[1] >= 16 && b[1] <= 31) || (b[0] == 192 && b[1] == 168);
    }

    /** Jeton longue durée de Home Assistant (JWT) : caractères sûrs, longueur raisonnable. */
    static boolean validToken(String t) {
        return t != null && t.length() >= 20 && t.length() <= 600 && t.matches("[A-Za-z0-9._-]+");
    }

    static boolean validEntityId(String id) {
        if (id == null || id.length() > 120 || !id.matches("[a-z0-9_]+\\.[a-z0-9_]+")) return false;
        return DOMAINS.contains(id.substring(0, id.indexOf('.')));
    }

    // ------------------------------------------------------------------ appareils

    static final class Entity {
        final String id, domain, name, state;
        final int brightness; // lumière : 0..100, -1 si inconnu
        /** Niveau réglable, 0..100 (-1 si inconnu) : intensité d'une lumière, position d'un volet, vitesse d'un ventilateur. */
        final int level;
        /** Couleur d'une lumière allumée (opaque), 0 si elle n'en annonce pas. */
        final int color;
        /** Pièce où Home Assistant range l'appareil (« » si aucune). */
        final String area;
        /** Lumière dont l'intensité se règle (pas seulement allumée ou éteinte). */
        final boolean dim;

        Entity(String id, String name, String state, int brightness) { this(id, name, state, brightness, 0, "", brightness >= 0); }

        Entity(String id, String name, String state, int level, int color, String area, boolean dim) {
            this.id = id;
            this.domain = id.substring(0, id.indexOf('.'));
            this.name = name;
            this.state = state;
            this.level = level;
            this.brightness = "light".equals(domain) ? level : -1;
            this.color = color;
            this.area = area == null ? "" : area;
            this.dim = dim;
        }

        /** Scène, script ou bouton : on le lance, il n'a pas d'état allumé ou éteint. */
        boolean trigger() { return "scene".equals(domain) || "script".equals(domain) || "button".equals(domain); }

        boolean on() { return "on".equals(state) || "open".equals(state) || "playing".equals(state); }
        boolean available() { return !"unavailable".equals(state) && !"unknown".equals(state) || "scene".equals(domain) || "script".equals(domain) || "button".equals(domain); }
    }

    /** Le niveau de cet appareil se règle-t-il depuis la TV (intensité, position, vitesse) ? */
    static boolean adjustable(Entity e) {
        if (!e.available() || "unavailable".equals(e.state)) return false;
        if ("light".equals(e.domain)) return e.dim;
        return ("cover".equals(e.domain) || "fan".equals(e.domain)) && e.level >= 0;
    }

    /** { chemin du service, corps JSON } pour amener l'appareil au niveau pct (0 à 100) ; null s'il ne se règle pas. */
    static String[] levelRequest(Entity e, int pct) {
        if (!validEntityId(e.id)) return null;
        pct = Math.max(0, Math.min(100, pct));
        String id = "{\"entity_id\":\"" + e.id + "\"";
        switch (e.domain) {
            case "light": return pct == 0 ? new String[]{"/api/services/light/turn_off", id + "}"}
                    : new String[]{"/api/services/light/turn_on", id + ",\"brightness_pct\":" + pct + "}"};
            case "cover": return new String[]{"/api/services/cover/set_cover_position", id + ",\"position\":" + pct + "}"};
            case "fan": return pct == 0 ? new String[]{"/api/services/fan/turn_off", id + "}"}
                    : new String[]{"/api/services/fan/set_percentage", id + ",\"percentage\":" + pct + "}"};
            default: return null;
        }
    }

    static void setLevel(String base, String token, Entity e, int pct) throws Exception {
        String[] r = levelRequest(e, pct);
        if (r == null) throw new IOException("Appareil refusé");
        request(base, token, "POST", r[0], r[1]);
    }

    /** Couleur à l'écran d'un blanc donné en kelvins : ambré (2000 K), blanc chaud, puis bleuté (6500 K). */
    static int kelvinColor(int k) {
        k = Math.max(2000, Math.min(6500, k));
        return k < 4000 ? Palette.mix(0xFFFFB066, 0xFFFFF1DC, (k - 2000) / 2000f) : Palette.mix(0xFFFFF1DC, 0xFFDCE8FF, (k - 4000) / 2500f);
    }

    /**
     * Demande à Home Assistant la pièce de chaque appareil pilotable (son moteur de modèles sait la
     * donner ; la liste des états ne la contient pas). Réponse : une ligne « appareil|pièce ».
     */
    static final String AREA_BODY = "{\"template\":\"{% for d in ['scene','light','switch','fan','cover','media_player','script','input_boolean','button'] %}"
            + "{% for s in states[d] %}{{ s.entity_id }}|{{ area_name(s.entity_id) or '' }}\\n{% endfor %}{% endfor %}\"}";

    static java.util.Map<String, String> parseAreas(String text) {
        java.util.Map<String, String> out = new java.util.HashMap<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            int bar = line.indexOf('|');
            if (bar <= 0 || out.size() >= 5000) continue;
            String id = line.substring(0, bar).trim(), area = clean(line.substring(bar + 1), 30);
            if (validEntityId(id) && !area.isEmpty() && !"None".equals(area)) out.put(id, area);
        }
        return out;
    }

    /** Appareils à montrer sur la TV : ceux choisis, sinon tout (borné). */
    static List<Entity> shown(List<Entity> all, Set<String> pinned, int max) {
        List<Entity> out = new ArrayList<>();
        boolean any = false;
        for (Entity e : all) if (pinned.contains(e.id)) { any = true; break; }
        for (Entity e : all) {
            if (any && !pinned.contains(e.id)) continue;
            out.add(e);
            if (out.size() >= max) break;
        }
        return out;
    }

    static String clean(String s, int max) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < max; i++) { char c = s.charAt(i); if (c >= 0x20) b.append(c); }
        return b.toString().trim();
    }

    /** Service appelé quand on appuie sur OK. */
    static String service(Entity e) {
        switch (e.domain) {
            case "scene": case "script": return "turn_on";
            case "button": return "press";
            case "media_player": return "media_play_pause";
            default: return "toggle"; // light, switch, fan, cover, input_boolean
        }
    }

    /** État lisible : « Allumée · 60 % », « Fermé », « Activer »… */
    static String label(Entity e) {
        if ("scene".equals(e.domain) || "script".equals(e.domain) || "button".equals(e.domain)) return "Activer";
        switch (e.state) {
            case "on": return "light".equals(e.domain) && e.brightness > 0 ? "Allumée · " + e.brightness + " %" : "light".equals(e.domain) ? "Allumée" : "Allumé";
            case "off": return "light".equals(e.domain) ? "Éteinte" : "Éteint";
            case "open": return "Ouvert";
            case "opening": return "Ouverture…";
            case "closed": return "Fermé";
            case "closing": return "Fermeture…";
            case "playing": return "Lecture";
            case "paused": return "Pause";
            case "idle": case "standby": return "En veille";
            case "unavailable": return "Indisponible";
            case "unknown": return "—";
            default: return e.state;
        }
    }

    // ------------------------------------------------------------------ réseau

    static void call(String base, String token, Entity e) throws Exception {
        if (!validEntityId(e.id)) throw new IOException("Appareil refusé");
        request(base, token, "POST", "/api/services/" + e.domain + "/" + service(e),
                "{\"entity_id\":\"" + e.id + "\"}"); // identifiant déjà vérifié (lettres, chiffres, _ et .)
    }

    /** Vérifie l'adresse et le jeton (« API running. »). */
    static void ping(String base, String token) throws Exception { request(base, token, "GET", "/api/", null); }

    /** Requête vers Home Assistant. Lève une exception claire en cas de refus. */
    static String request(String base, String token, String method, String path, String body) throws Exception {
        String b = normalizeUrl(base);
        if (b == null || !validToken(token)) throw new IOException("Adresse ou jeton invalide");
        if (b.startsWith("https://")) return https(b + path, token, method, body);
        URL u = new URL(b);
        return http(u.getHost(), u.getPort() > 0 ? u.getPort() : 80, path, token, method, body);
    }

    private static String https(String url, String token, String method, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes("UTF-8")); }
        }
        int code = c.getResponseCode();
        checkStatus(code);
        try (InputStream in = c.getInputStream()) { return new String(readAll(in, MAX_BODY), "UTF-8"); }
        finally { c.disconnect(); }
    }

    /**
     * HTTP/1.1 minimal sur le réseau local (le HTTP en clair est désactivé pour le reste de Lumen) :
     * l'adresse résolue doit être privée, sinon rien n'est envoyé.
     */
    private static String http(String host, int port, String path, String token, String method, String body) throws IOException {
        InetAddress ip = InetAddress.getByName(host);
        if (!(ip instanceof Inet4Address) || !(ip.isSiteLocalAddress() || ip.isLinkLocalAddress()))
            throw new IOException("Adresse hors du réseau local");
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), TIMEOUT_MS);
            s.setSoTimeout(TIMEOUT_MS);
            byte[] data = body == null ? new byte[0] : body.getBytes("UTF-8");
            StringBuilder h = new StringBuilder();
            h.append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                    .append("Host: ").append(host).append(port == 80 ? "" : ":" + port).append("\r\n")
                    .append("Authorization: Bearer ").append(token).append("\r\n")
                    .append("Accept: application/json\r\nConnection: close\r\nUser-Agent: LumenTV\r\n");
            if (body != null) h.append("Content-Type: application/json\r\nContent-Length: ").append(data.length).append("\r\n");
            h.append("\r\n");
            OutputStream o = s.getOutputStream();
            o.write(h.toString().getBytes("UTF-8"));
            o.write(data);
            o.flush();
            return parseHttpResponse(s.getInputStream());
        }
    }

    /** Lit une réponse HTTP/1.1 (Content-Length, « chunked » ou jusqu'à la fermeture). */
    static String parseHttpResponse(InputStream in) throws IOException {
        String status = line(in);
        String[] sp = status.split(" ");
        if (sp.length < 2 || !sp[0].startsWith("HTTP/")) throw new IOException("Réponse inattendue");
        int code;
        try { code = Integer.parseInt(sp[1]); } catch (NumberFormatException e) { throw new IOException("Réponse inattendue"); }
        long len = -1;
        boolean chunked = false;
        for (int i = 0; i < 100; i++) {
            String l = line(in);
            if (l.isEmpty()) break;
            int c = l.indexOf(':');
            if (c < 0) continue;
            String k = l.substring(0, c).trim().toLowerCase(Locale.ROOT), v = l.substring(c + 1).trim();
            if (k.equals("content-length")) try { len = Long.parseLong(v); } catch (NumberFormatException ignored) { }
            if (k.equals("transfer-encoding") && v.toLowerCase(Locale.ROOT).contains("chunked")) chunked = true;
        }
        checkStatus(code);
        byte[] body;
        if (chunked) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            while (true) {
                String sz = line(in);
                int semi = sz.indexOf(';');
                int n;
                try { n = Integer.parseInt((semi < 0 ? sz : sz.substring(0, semi)).trim(), 16); } catch (NumberFormatException e) { throw new IOException("Réponse inattendue"); }
                if (n == 0) break;
                if (b.size() + n > MAX_BODY) throw new IOException("Réponse trop grande");
                byte[] chunk = readN(in, n);
                b.write(chunk);
                line(in);
            }
            body = b.toByteArray();
        } else if (len >= 0) {
            if (len > MAX_BODY) throw new IOException("Réponse trop grande");
            body = readN(in, (int) len);
        } else {
            body = readAll(in, MAX_BODY);
        }
        return new String(body, "UTF-8");
    }

    private static void checkStatus(int code) throws IOException {
        if (code == 401 || code == 403) throw new IOException("Jeton refusé par Home Assistant");
        if (code >= 300 && code < 400) throw new IOException("Redirection refusée");
        if (code == 404) throw new IOException("Adresse sans Home Assistant");
        if (code >= 400) throw new IOException("Home Assistant a répondu " + code);
    }

    private static String line(InputStream in) throws IOException {
        StringBuilder b = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.append((char) c);
            if (b.length() > 8192) throw new IOException("Ligne trop longue");
        }
        if (c == -1 && b.length() == 0) throw new IOException("Connexion fermée");
        return b.toString();
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] out = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(out, off, n - off);
            if (r < 0) throw new IOException("Réponse incomplète");
            off += r;
        }
        return out;
    }

    private static byte[] readAll(InputStream in, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) {
            b.write(buf, 0, r);
            if (b.size() > max) throw new IOException("Réponse trop grande");
        }
        return b.toByteArray();
    }

    /** Message pour l'écran (jamais technique). */
    static String human(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.startsWith("Jeton") || m.startsWith("Adresse") || m.startsWith("Redirection") || m.startsWith("Home Assistant")) return m;
        if (e instanceof java.net.UnknownHostException) return "Home Assistant introuvable sur le réseau";
        if (e instanceof java.net.SocketTimeoutException || e instanceof java.net.ConnectException) return "Home Assistant ne répond pas";
        return "Connexion à Home Assistant impossible";
    }
}

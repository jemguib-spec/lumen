package com.lumen.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lecture stricte d'une requête HTTP/1.1 (Java pur, testable hors Android).
 * Limites dures : ligne de requête et en-têtes courts, nombre d'en-têtes borné,
 * méthodes GET/POST uniquement, chemin sans « .. » ni caractères de contrôle.
 */
final class HttpRequest {
    static final int MAX_LINE = 4096;
    static final int MAX_HEADERS = 40;

    final String method;
    final String path;
    final Map<String, String> query;
    final Map<String, String> headers; // noms en minuscules
    final long contentLength;

    private HttpRequest(String method, String path, Map<String, String> query, Map<String, String> headers, long len) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.headers = headers;
        this.contentLength = len;
    }

    String header(String name) { return headers.get(name.toLowerCase(Locale.ROOT)); }

    /** Valeur d'un cookie, ou null. */
    String cookie(String name) {
        String c = header("cookie");
        if (c == null) return null;
        for (String part : c.split(";")) {
            int e = part.indexOf('=');
            if (e > 0 && part.substring(0, e).trim().equals(name)) return part.substring(e + 1).trim();
        }
        return null;
    }

    static HttpRequest read(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null) throw new IOException("Connexion fermée");
        String[] p = line.split(" ");
        if (p.length != 3 || !p[2].startsWith("HTTP/1.")) throw new IOException("Requête invalide");
        String method = p[0];
        if (!method.equals("GET") && !method.equals("POST")) throw new IOException("Méthode refusée");
        String target = p[1];
        if (!target.startsWith("/") || target.contains("..") || hasControl(target)) throw new IOException("Chemin refusé");
        String path = target, q = "";
        int qi = target.indexOf('?');
        if (qi >= 0) { path = target.substring(0, qi); q = target.substring(qi + 1); }

        Map<String, String> headers = new HashMap<>();
        for (int i = 0; ; i++) {
            if (i > MAX_HEADERS) throw new IOException("Trop d'en-têtes");
            String h = readLine(in);
            if (h == null) throw new IOException("En-têtes incomplets");
            if (h.isEmpty()) break;
            int c = h.indexOf(':');
            if (c <= 0) throw new IOException("En-tête invalide");
            headers.put(h.substring(0, c).trim().toLowerCase(Locale.ROOT), h.substring(c + 1).trim());
        }
        if (headers.containsKey("transfer-encoding")) throw new IOException("Transfert fragmenté refusé");
        long len = 0;
        String cl = headers.get("content-length");
        if (cl != null) {
            try { len = Long.parseLong(cl); } catch (NumberFormatException e) { throw new IOException("Longueur invalide"); }
            if (len < 0) throw new IOException("Longueur invalide");
        }
        return new HttpRequest(method, path, parseQuery(q), headers, len);
    }

    /** Lit exactement len octets (au plus max), sinon erreur. */
    static byte[] readBody(InputStream in, long len, int max) throws IOException {
        if (len > max) throw new IOException("Corps trop volumineux");
        byte[] b = new byte[(int) len];
        int off = 0;
        while (off < len) {
            int n = in.read(b, off, (int) len - off);
            if (n < 0) throw new IOException("Corps incomplet");
            off += n;
        }
        return b;
    }

    private static boolean hasControl(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 0x20 || s.charAt(i) == 0x7F) return true;
        return false;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') return b.toString("UTF-8");
            if (c != '\r') b.write(c);
            if (b.size() > MAX_LINE) throw new IOException("Ligne trop longue");
        }
        return b.size() == 0 ? null : b.toString("UTF-8");
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q.isEmpty()) return m;
        String[] parts = q.split("&");
        if (parts.length > 20) return m;
        for (String part : parts) {
            int e = part.indexOf('=');
            if (e <= 0) continue;
            try {
                m.put(URLDecoder.decode(part.substring(0, e), "UTF-8"), URLDecoder.decode(part.substring(e + 1), "UTF-8"));
            } catch (UnsupportedEncodingException | IllegalArgumentException ignored) { }
        }
        return m;
    }
}

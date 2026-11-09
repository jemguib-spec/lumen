package com.lumen.launcher;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Seul point d'accès réseau sortant de Lumen.
 * - HTTPS uniquement, certificats validés par le magasin du système (les certificats
 *   ajoutés par l'utilisateur ne sont pas acceptés : cible Android 29) ;
 * - liste blanche d'hôtes (NetPolicy), revérifiée à chaque redirection (suivies à la main) ;
 * - aucun cookie, aucun identifiant, aucune donnée envoyée : uniquement des GET.
 */
final class Net {
    private Net() { }

    /** Identifiant demandé par la politique de Wikimedia : nom de l'appli et adresse du projet. */
    static final String USER_AGENT = "LumenTV/4 (https://github.com/jemguib-spec/lumen)";
    private static final int MAX_REDIRECTS = 4;

    /** Peut lever une IOException pour interrompre le téléchargement (mise en pause). */
    interface Progress { void onProgress(long done, long total) throws IOException; }

    /** Ouvre une connexion GET conforme à la politique (redirections comprises). */
    static HttpURLConnection open(String url) throws IOException { return open(url, null); }

    /**
     * auth : clé d'API envoyée dans l'en-tête Authorization, uniquement à api.pexels.com et uniquement
     * à la première requête (jamais transmise après une redirection, même vers un serveur autorisé).
     */
    static HttpURLConnection open(String url, String auth) throws IOException {
        String current = url;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            URL u = NetPolicy.check(current);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            if (!(c instanceof HttpsURLConnection)) { c.disconnect(); throw new IOException("Connexion non chiffrée"); }
            c.setInstanceFollowRedirects(false);
            c.setUseCaches(false);
            c.setConnectTimeout(15_000);
            c.setReadTimeout(30_000);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", USER_AGENT);
            c.setRequestProperty("Accept-Encoding", "identity");
            if (auth != null && i == 0 && "api.pexels.com".equals(u.getHost())) c.setRequestProperty("Authorization", auth);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirection sans destination");
                current = new URL(u, loc).toString(); // revérifiée au tour suivant
                continue;
            }
            if (code / 100 != 2) { c.disconnect(); throw new IOException("HTTP " + code); }
            return c;
        }
        throw new IOException("Trop de redirections");
    }

    static byte[] getBytes(String url, int maxBytes) throws IOException { return getBytes(url, maxBytes, null); }

    static byte[] getBytes(String url, int maxBytes, String auth) throws IOException {
        HttpURLConnection c = open(url, auth);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (b.size() + n > maxBytes) throw new IOException("Réponse trop volumineuse");
                b.write(buf, 0, n);
            }
            return b.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    static JSONObject getJson(String url) throws IOException { return getJson(url, null); }

    static JSONObject getJson(String url, String auth) throws IOException {
        try {
            return new JSONObject(new String(getBytes(url, 8 << 20, auth), "UTF-8"));
        } catch (org.json.JSONException e) {
            throw new IOException("Réponse illisible");
        }
    }

    /**
     * Télécharge dans un fichier temporaire puis renomme. Refuse au-delà de maxBytes
     * (annoncé ou réel). Le contenu sera ensuite vérifié par MediaGuard.
     */
    static void download(String url, File out, long maxBytes, Progress p) throws IOException {
        File tmp = new File(out.getPath() + ".part");
        HttpURLConnection c = open(url);
        try {
            long len = c.getContentLength();
            if (len > maxBytes) throw new IOException("Fichier trop lourd");
            try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(tmp)) {
                byte[] buf = new byte[128 * 1024];
                long total = 0, shown = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > maxBytes) throw new IOException("Fichier trop lourd");
                    o.write(buf, 0, n);
                    if (p != null && total - shown > (2 << 20)) { shown = total; p.onProgress(total, len); }
                }
                if (total < 10_000) throw new IOException("Fichier incomplet");
            }
            if (!tmp.renameTo(out)) throw new IOException("Écriture impossible");
        } catch (IOException e) {
            tmp.delete();
            throw e;
        } finally {
            c.disconnect();
        }
    }
}

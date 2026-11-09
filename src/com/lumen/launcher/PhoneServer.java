package com.lumen.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serveur web local pour régler Lumen depuis le téléphone.
 *
 * Sécurité :
 * - écoute uniquement sur l'adresse du réseau local de la TV, n'accepte que des clients du
 *   même réseau privé ; vérifie l'en-tête Host (protection contre le « DNS rebinding ») ;
 * - authentification par jeton à usage unique (PhoneAuth) puis cookie de session
 *   HttpOnly + SameSite=Strict ; toute requête d'écriture exige en plus l'en-tête X-Lumen
 *   (bloque les requêtes forgées depuis un autre site) ;
 * - en-têtes de sécurité stricts (CSP sans script externe ni inline, pas d'iframe) ;
 * - requêtes et corps bornés, 4 connexions simultanées au plus, délais d'attente courts ;
 * - fichiers reçus vérifiés et réécrits par MediaGuard ;
 * - s'arrête dès que Lumen passe en arrière-plan, après 15 min sans activité ou après
 *   5 tentatives d'accès refusées.
 */
final class PhoneServer {
    interface Host {
        AppRepo repo();
        Prefs prefs();
        Online online();
        void onFavoritesChanged();
        void onMineChanged(int received);
    }

    private static final long IDLE_STOP_MS = 15 * 60_000L;
    private static final String COOKIE = "lumen_s";
    private static final int MAX_FAVORITES = 60;

    private final Context ctx;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PhoneAuth auth = new PhoneAuth(new PhoneAuth.Clock() {
        @Override public long now() { return System.currentTimeMillis(); }
    });
    private final Map<String, byte[]> iconCache = Collections.synchronizedMap(new HashMap<String, byte[]>());
    private ThreadPoolExecutor pool;
    private ServerSocket server;
    private String hostHeader;
    private volatile long lastActivity;

    PhoneServer(Context c, Host h) { ctx = c.getApplicationContext(); host = h; }

    synchronized boolean running() { return server != null && !server.isClosed(); }

    /** Démarre avec un nouveau jeton et renvoie l'adresse du QR code, ou null sans réseau. */
    synchronized String start() {
        InetAddress ip = localAddress();
        if (ip == null) return null;
        stop();
        try {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            try { s.bind(new InetSocketAddress(ip, 8765), 8); }
            catch (Exception e) { s.bind(new InetSocketAddress(ip, 0), 8); }
            server = s;
        } catch (Exception e) {
            return null;
        }
        hostHeader = ip.getHostAddress() + ":" + server.getLocalPort();
        pool = new ThreadPoolExecutor(1, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(4));
        final ServerSocket s = server;
        final ThreadPoolExecutor p = pool;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (!s.isClosed()) {
                    try {
                        final Socket c = s.accept();
                        if (!allowedClient(c.getInetAddress())) { c.close(); continue; }
                        try { p.execute(new Runnable() { @Override public void run() { handle(c); } }); }
                        catch (RejectedExecutionException busy) { c.close(); }
                    } catch (Exception ignored) { }
                }
            }
        }, "lumen-phone");
        t.setDaemon(true);
        t.start();
        String token = auth.newToken();
        touch();
        return "http://" + hostHeader + "/#" + token;
    }

    synchronized void stop() {
        main.removeCallbacks(idleStop);
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null;
        if (pool != null) pool.shutdownNow();
        pool = null;
        iconCache.clear();
    }

    private void touch() {
        lastActivity = System.currentTimeMillis();
        main.removeCallbacks(idleStop);
        main.postDelayed(idleStop, IDLE_STOP_MS);
    }

    private final Runnable idleStop = new Runnable() {
        @Override public void run() {
            if (System.currentTimeMillis() - lastActivity >= IDLE_STOP_MS - 1000) stop();
            else main.postDelayed(this, IDLE_STOP_MS);
        }
    };

    // ------------------------------------------------------------------ réseau local

    /** Adresse IPv4 privée de la TV (Wi-Fi ou Ethernet de préférence). */
    static InetAddress localAddress() {
        try {
            InetAddress fallback = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || !a.isSiteLocalAddress()) continue;
                    String n = ni.getName();
                    if (n.startsWith("wlan") || n.startsWith("eth")) return a;
                    if (fallback == null) fallback = a;
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    /** Seuls les appareils du réseau privé (10.x, 172.16-31.x, 192.168.x) sont acceptés. */
    private static boolean allowedClient(InetAddress a) {
        return a instanceof Inet4Address && a.isSiteLocalAddress();
    }

    // ------------------------------------------------------------------ requêtes

    private void handle(Socket sock) {
        try {
            sock.setSoTimeout(20_000);
            InputStream in = new BufferedInputStream(sock.getInputStream());
            OutputStream out = sock.getOutputStream();
            HttpRequest r;
            try { r = HttpRequest.read(in); }
            catch (Exception bad) { send(out, 400, "text/plain", new byte[0], null); sock.close(); return; }

            // anti « DNS rebinding » : la page doit être demandée par l'adresse de la TV
            if (!hostHeader.equals(r.header("host"))) { send(out, 421, "text/plain", new byte[0], null); sock.close(); return; }
            touch();
            route(r, in, out, sock);
            out.flush();
        } catch (Exception ignored) {
        } finally {
            try { sock.close(); } catch (Exception ignored) { }
            if (auth.locked()) main.post(new Runnable() { @Override public void run() { stop(); } });
        }
    }

    private void route(HttpRequest r, InputStream in, OutputStream out, Socket sock) throws Exception {
        String p = r.path;
        boolean post = r.method.equals("POST");

        // ressources de la page (publiques, sans donnée)
        if (!post && (p.equals("/") || p.equals("/index.html"))) { send(out, 200, "text/html; charset=utf-8", asset("phone.html"), null); return; }
        if (!post && p.equals("/app.js")) { send(out, 200, "application/javascript; charset=utf-8", asset("phone.js"), null); return; }
        if (!post && p.equals("/app.css")) { send(out, 200, "text/css; charset=utf-8", asset("phone.css"), null); return; }

        // toute requête d'API exige l'en-tête X-Lumen (une page d'un autre site ne peut pas l'ajouter sans autorisation)
        if (!"1".equals(r.header("x-lumen"))) { send(out, 403, "text/plain", new byte[0], null); return; }

        if (post && p.equals("/api/session")) {
            String sid = auth.openSession(r.header("x-lumen-token"));
            if (sid == null) { send(out, 403, "application/json", "{\"ok\":false}".getBytes("UTF-8"), null); return; }
            String cookie = COOKIE + "=" + sid + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=1800";
            send(out, 200, "application/json", "{\"ok\":true}".getBytes("UTF-8"), cookie);
            return;
        }

        if (!auth.check(r.cookie(COOKIE))) { send(out, 401, "application/json", "{\"ok\":false}".getBytes("UTF-8"), null); return; }

        if (!post && p.equals("/api/state")) { send(out, 200, "application/json; charset=utf-8", state().toString().getBytes("UTF-8"), null); return; }
        if (!post && p.equals("/api/icon")) { byte[] b = icon(r.query.get("key")); send(out, b == null ? 404 : 200, "image/png", b == null ? new byte[0] : b, null); return; }
        if (!post && p.equals("/api/thumb")) { byte[] b = thumb(r.query.get("id")); send(out, b == null ? 404 : 200, "image/jpeg", b == null ? new byte[0] : b, null); return; }
        if (post && p.equals("/api/favorites")) { boolean ok = setFavorites(HttpRequest.readBody(in, r.contentLength, 64 * 1024)); send(out, ok ? 200 : 400, "application/json", ("{\"ok\":" + ok + "}").getBytes("UTF-8"), null); return; }
        if (post && p.equals("/api/upload")) {
            sock.setSoTimeout(60_000);
            boolean ok = upload(in, r.contentLength, r.header("x-lumen-name"), r.header("x-lumen-target"));
            send(out, ok ? 200 : 400, "application/json", ("{\"ok\":" + ok + "}").getBytes("UTF-8"), null);
            return;
        }
        if (!post && p.equals("/api/log")) { send(out, 200, "text/plain; charset=utf-8", CrashLog.read(ctx).getBytes("UTF-8"), null); return; }
        if (post && p.equals("/api/log/clear")) { CrashLog.clear(ctx); send(out, 200, "application/json", "{\"ok\":true}".getBytes("UTF-8"), null); return; }
        if (post && p.equals("/api/pexels")) {
            boolean ok = setPexels(HttpRequest.readBody(in, r.contentLength, 256));
            send(out, ok ? 200 : 400, "application/json", ("{\"ok\":" + ok + "}").getBytes("UTF-8"), null);
            return;
        }
        if (post && p.equals("/api/delete")) { boolean ok = deleteMine(r.query.get("id")); send(out, ok ? 200 : 400, "application/json", ("{\"ok\":" + ok + "}").getBytes("UTF-8"), null); return; }
        send(out, 404, "text/plain", new byte[0], null);
    }

    // ------------------------------------------------------------------ actions

    private JSONObject state() throws Exception {
        final JSONObject o = new JSONObject();
        onMain(new Callable<Void>() {
            @Override public Void call() throws Exception {
                JSONArray apps = new JSONArray();
                for (AppRepo.App a : host.repo().all()) {
                    JSONObject j = new JSONObject();
                    j.put("key", a.key);
                    j.put("label", a.label);
                    apps.put(j);
                }
                o.put("apps", apps);
                o.put("favorites", new JSONArray(host.prefs().favorites()));
                return null;
            }
        });
        JSONArray mine = new JSONArray();
        for (WallItem w : host.online().items(Online.MINE)) {
            JSONObject j = new JSONObject();
            j.put("id", w.id);
            j.put("title", w.title);
            j.put("video", w.kind == WallItem.VIDEO_FILE);
            mine.put(j);
        }
        for (WallItem w : host.online().cosyMine()) {
            JSONObject j = new JSONObject();
            j.put("id", w.id);
            j.put("title", w.title);
            j.put("video", true);
            j.put("cosy", true);
            mine.put(j);
        }
        o.put("mine", mine);
        JSONArray amb = new JSONArray();
        for (String n : Online.COSY_NAMES) amb.put(n);
        o.put("ambiances", amb);
        o.put("pexels", !host.prefs().pexelsKey().isEmpty());
        return o;
    }

    /** Remplace les favoris : uniquement des applis réellement installées, sans doublon. */
    private boolean setFavorites(byte[] body) throws Exception {
        final JSONArray arr;
        try { arr = new JSONArray(new String(body, "UTF-8")); } catch (Exception e) { return false; }
        if (arr.length() > MAX_FAVORITES) return false;
        return onMain(new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                List<String> keys = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    String k = arr.optString(i, null);
                    if (k == null || host.repo().get(k) == null || !seen.add(k)) continue;
                    keys.add(k);
                }
                host.prefs().setFavorites(keys);
                host.onFavoritesChanged();
                return true;
            }
        });
    }

    /** Reçoit un fichier (corps brut), le fait vérifier par MediaGuard, puis l'ajoute. */
    /** target : « fond » (Mes photos et vidéos) ou « cosy:<n° d'ambiance> » (vidéos uniquement). */
    private boolean upload(InputStream in, long len, String name, String target) {
        if (len < 1024 || len > MediaGuard.MAX_VIDEO_BYTES) return false;
        int kind = -1;
        if (target != null && !target.equals("fond")) {
            if (!target.matches("cosy:[0-9]")) return false;
            kind = target.charAt(5) - '0';
            if (kind >= Online.COSY_NAMES.length) return false;
        }
        File dir = kind >= 0 ? host.online().cosyDir() : host.online().mineDir();
        if (dir.getUsableSpace() < len + (500L << 20)) return false; // garde 500 Mo libres
        String id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString(new SecureRandom().nextInt(1 << 24), 36);
        File raw = new File(dir, id + ".upload");
        try (OutputStream o = new FileOutputStream(raw)) {
            byte[] buf = new byte[256 * 1024];
            long left = len;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                o.write(buf, 0, n);
                left -= n;
                lastActivity = System.currentTimeMillis();
            }
            if (left > 0) { raw.delete(); return false; }
        } catch (Exception e) {
            raw.delete();
            return false;
        }
        File ok = MediaGuard.accept(raw, dir, id, true, kind < 0); // une ambiance n'accepte que des vidéos
        if (ok == null) return false;
        if (kind >= 0) host.online().addCosyMine(kind, id, ok.getName(), cleanTitle(name));
        else host.online().addMine(id, ok.getName(), cleanTitle(name));
        main.post(new Runnable() { @Override public void run() { host.onMineChanged(1); } });
        return true;
    }

    /** Titre affiché : nom d'origine sans extension, caractères sûrs uniquement, 60 au plus. */
    private static String cleanTitle(String name) {
        if (name == null) return "Ma photo";
        String t;
        try { t = java.net.URLDecoder.decode(name, "UTF-8"); } catch (Exception e) { t = name; }
        t = t.replaceAll("\\.[A-Za-z0-9]{1,5}$", "").replaceAll("[^\\p{L}\\p{N} _\\-().,']", " ").replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return "Ma photo";
        return t.length() > 60 ? t.substring(0, 60) : t;
    }

    private boolean deleteMine(String id) throws Exception {
        if (id == null || !id.matches("(perso|cosy):[A-Za-z0-9_-]{1,40}")) return false;
        if (id.startsWith("cosy:")) {
            boolean mine = false; // seules les vidéos envoyées du téléphone peuvent être supprimées d'ici
            for (WallItem w : host.online().cosyMine()) if (w.id.equals(id)) mine = true;
            if (!mine) return false;
            host.online().forget(id);
        } else host.online().removeMine(id);
        main.post(new Runnable() { @Override public void run() { host.onMineChanged(0); } });
        return true;
    }

    /** Clé Pexels envoyée du téléphone (vide = retirer). Format contrôlé ; jamais renvoyée. */
    private boolean setPexels(byte[] body) throws Exception {
        final String k = new String(body, "UTF-8").trim();
        if (!k.isEmpty() && !Pexels.validKey(k)) return false;
        return onMain(new Callable<Boolean>() {
            @Override public Boolean call() {
                host.prefs().setPexelsKey(k);
                return true;
            }
        });
    }

    private byte[] icon(final String key) throws Exception {
        if (key == null || key.length() > 300) return null;
        byte[] cached = iconCache.get(key);
        if (cached != null) return cached;
        final AtomicReference<Drawable> d = new AtomicReference<>();
        onMain(new Callable<Void>() {
            @Override public Void call() {
                AppRepo.App a = host.repo().get(key);
                if (a != null) d.set(a.icon(ctx.getPackageManager()));
                return null;
            }
        });
        if (d.get() == null) return null;
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Drawable dr = d.get().getConstantState() != null ? d.get().getConstantState().newDrawable().mutate() : d.get();
        dr.setBounds(0, 0, 96, 96);
        dr.draw(new Canvas(b));
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, o);
        b.recycle();
        byte[] r = o.toByteArray();
        if (iconCache.size() < 300) iconCache.put(key, r);
        return r;
    }

    private byte[] thumb(String id) {
        if (id == null) return null;
        List<WallItem> all = new ArrayList<>(host.online().items(Online.MINE));
        all.addAll(host.online().cosyMine());
        for (WallItem w : all) {
            if (!w.id.equals(id)) continue;
            Bitmap b = null;
            try {
                if (w.kind == WallItem.VIDEO_FILE) {
                    android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                    try {
                        r.setDataSource(w.path);
                        b = r.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    } finally { r.release(); }
                } else {
                    android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                    o.inSampleSize = 8; // les images gardées font 3840 px au plus
                    b = android.graphics.BitmapFactory.decodeFile(w.path, o);
                }
                if (b == null) return null;
                if (b.getWidth() > 480) {
                    Bitmap sc = Bitmap.createScaledBitmap(b, 480, Math.max(1, b.getHeight() * 480 / b.getWidth()), true);
                    if (sc != b) b.recycle();
                    b = sc;
                }
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.JPEG, 80, o);
                return o.toByteArray();
            } catch (Throwable t) {
                return null;
            } finally {
                if (b != null) b.recycle();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ outils

    private <T> T onMain(final Callable<T> c) throws Exception {
        final AtomicReference<T> res = new AtomicReference<>();
        final AtomicReference<Exception> err = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        main.post(new Runnable() {
            @Override public void run() {
                try { res.set(c.call()); } catch (Exception e) { err.set(e); }
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) throw new Exception("Délai dépassé");
        if (err.get() != null) throw err.get();
        return res.get();
    }

    private byte[] asset(String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    private static void send(OutputStream out, int code, String type, byte[] body, String setCookie) throws Exception {
        String status;
        switch (code) {
            case 200: status = "OK"; break;
            case 401: status = "Unauthorized"; break;
            case 403: status = "Forbidden"; break;
            case 404: status = "Not Found"; break;
            case 421: status = "Misdirected Request"; break;
            default: status = "Bad Request";
        }
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n");
        h.append("Content-Type: ").append(type).append("\r\n");
        h.append("Content-Length: ").append(body.length).append("\r\n");
        h.append("Cache-Control: no-store\r\n");
        h.append("X-Content-Type-Options: nosniff\r\n");
        h.append("X-Frame-Options: DENY\r\n");
        h.append("Referrer-Policy: no-referrer\r\n");
        h.append("Cross-Origin-Opener-Policy: same-origin\r\n");
        h.append("Cross-Origin-Resource-Policy: same-origin\r\n");
        h.append("Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; "
                + "connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'\r\n");
        h.append("Permissions-Policy: camera=(), microphone=(), geolocation=()\r\n");
        if (setCookie != null) h.append("Set-Cookie: ").append(setCookie).append("\r\n");
        h.append("Connection: close\r\n\r\n");
        out.write(h.toString().getBytes("UTF-8"));
        out.write(body);
    }
}

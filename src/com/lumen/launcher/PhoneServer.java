// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
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
        void onSettingsRestored(int count);
        /** Lumen est-il à l'écran (accueil, applis, ambiances) ? Sinon, la TV affiche une autre appli. */
        boolean onTvHome();
        void phoneLaunch(AppRepo.App app);
        void phoneText(String text);
        /** n° d'ambiance à afficher, ou -1 pour quitter les ambiances. */
        void phoneAmbiance(int kind);
        boolean phoneTimer(int minutes);
        /** {ambiance affichée ou -1, minutes restantes de la minuterie, durée choisie} */
        int[] ambianceState();
        void phoneVeille(boolean on);
        boolean veilleOn();
        void phoneSource(int source);
        void phoneNextWall();
        void phonePicksChanged(String scope);
        void onPhoneConnected(boolean connected);
        /** Touche de la télécommande du téléphone ; renvoie la réponse JSON. */
        String phoneKey(String key);
        /** Le téléphone a changé de nom (page « Plus »). */
        void onPhoneName(String name);
        /** Home Assistant vient d'être relié : le bouton « Maison » apparaît. */
        void onHaLinked();
    }

    /** Touches acceptées depuis le téléphone (liste fermée). */
    static final java.util.List<String> KEYS = java.util.Arrays.asList(
            "up", "down", "left", "right", "ok", "back", "home", "volup", "voldown", "mute", "play", "sleep");
    /** Sans nouvelle du téléphone depuis ce délai (page fermée, téléphone verrouillé), la pastille s'éteint. */
    private static final long SILENT_MS = 45_000L;

    private static final long IDLE_STOP_MS = 15 * 60_000L;
    private static final String COOKIE = "lumen_s";
    private static final int MAX_FAVORITES = 60;
    private static final int MAX_TEXT = 100;
    /** Actions : rafale de 20, puis 4 par seconde (images exclues). */
    private final RateLimit limiter = new RateLimit(4, 20, System.currentTimeMillis());
    /** Télécommande et frappe en direct : plus rapides (défilement continu), mais bornées aussi. */
    private final RateLimit keyLimiter = new RateLimit(12, 30, System.currentTimeMillis());

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
    /** La TV montre-t-elle « téléphone connecté » ? (indépendant des sessions, pour toujours l'éteindre) */
    private volatile boolean shown;
    private volatile String deviceName = "un téléphone";

    /** Nom du téléphone connecté (« iPhone de Jem », « Pixel 8 »…). */
    String deviceName() { return deviceName; }

    PhoneServer(Context c, Host h) { ctx = c.getApplicationContext(); host = h; }

    synchronized boolean running() { return server != null && !server.isClosed(); }

    /** Un téléphone est-il connecté ? */
    boolean connected() { return running() && auth.hasSessions(); }

    /** Lumen passe en arrière-plan : on garde la connexion seulement si un téléphone est là. */
    void onBackground() { if (!connected()) stop(); }

    /** Déconnecte le téléphone et arrête le serveur (bouton sur la TV, extinction de l'écran). */
    void disconnect() {
        auth.revokeAll();
        stop();
    }

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
        main.removeCallbacks(silentCheck);
        setShown(false); // même si les sessions viennent d'être révoquées (bouton « Déconnecter »)
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null;
        if (pool != null) pool.shutdownNow();
        pool = null;
        iconCache.clear();
    }

    /** Allume ou éteint la pastille sur la TV, une seule fois par changement. */
    private void setShown(final boolean on) {
        if (shown == on) return;
        shown = on;
        main.post(new Runnable() { @Override public void run() { host.onPhoneConnected(on); } });
        main.removeCallbacks(silentCheck);
        if (on) main.postDelayed(silentCheck, SILENT_MS);
    }

    private final Runnable silentCheck = new Runnable() {
        @Override public void run() {
            if (!shown) return;
            long quiet = System.currentTimeMillis() - lastActivity;
            if (quiet >= SILENT_MS - 1000 || !auth.hasSessions()) setShown(false);
            else main.postDelayed(this, SILENT_MS - quiet);
        }
    };

    private static String decode(String s) {
        if (s == null) return null;
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return null; }
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
            deviceName = DeviceName.pick(decode(r.header("x-lumen-device")), r.header("user-agent"));
            String cookie = COOKIE + "=" + sid + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=1800";
            setShown(true);
            send(out, 200, "application/json", "{\"ok\":true}".getBytes("UTF-8"), cookie);
            return;
        }

        final String sid = r.cookie(COOKIE);
        if (!auth.check(sid)) { send(out, 401, "application/json", "{\"ok\":false}".getBytes("UTF-8"), null); return; }
        setShown(true); // le téléphone revient (page rouverte, téléphone déverrouillé)

        // images : nombreuses mais inoffensives ; tout le reste passe par la limite de débit
        boolean image = !post && (p.equals("/api/icon") || p.equals("/api/thumb") || p.equals("/api/pickthumb"));
        boolean fast = post && (p.equals("/api/key") || p.equals("/api/text"));
        if (!image && !(fast ? keyLimiter : limiter).allow(System.currentTimeMillis())) { json(out, 429, "{\"ok\":false,\"slow\":true}"); return; }
        // action sensible avec un code PIN sur la TV : le code doit avoir été saisi sur ce téléphone
        final boolean pinNeeded = host.prefs().hasPin() && !auth.isElevated(sid);

        if (!post && p.equals("/api/state")) { send(out, 200, "application/json; charset=utf-8", state(sid).toString().getBytes("UTF-8"), null); return; }
        if (!post && p.equals("/api/icon")) { byte[] b = icon(r.query.get("key")); send(out, b == null ? 404 : 200, "image/png", b == null ? new byte[0] : b, null); return; }
        if (!post && p.equals("/api/thumb")) { byte[] b = thumb(r.query.get("id")); send(out, b == null ? 404 : 200, "image/jpeg", b == null ? new byte[0] : b, null); return; }
        if (!post && p.equals("/api/pickthumb")) { byte[] b = pickThumb(r.query.get("scope"), r.query.get("key")); send(out, b == null ? 404 : 200, "image/jpeg", b == null ? new byte[0] : b, null); return; }
        if (!post && p.equals("/api/picks")) { json(out, 200, picks(r.query.get("scope")).toString()); return; }
        if (!post && p.equals("/api/log")) { send(out, 200, "text/plain; charset=utf-8", CrashLog.read(ctx).getBytes("UTF-8"), null); return; }
        if (!post && p.equals("/api/ha/entities")) { json(out, 200, haEntities()); return; }
        if (!post && p.equals("/api/backup")) { json(out, 200, host.prefs().exportBackup().toString(1)); return; }
        if (!post) { send(out, 404, "text/plain", new byte[0], null); return; }

        // ---- écritures
        switch (p) {
            case "/api/pin": json(out, 200, pin(HttpRequest.readBody(in, r.contentLength, 16), sid)); return;
            case "/api/ping": json(out, 200, "{\"ok\":true}"); return;
            case "/api/storage/clear": {
                final int what = intParam(r, "what", -1);
                if (what < Online.CLEAR_PHOTOS || what > Online.CLEAR_ALL) { ok(out, false); return; }
                host.online().clear(what);
                main.post(new Runnable() { @Override public void run() { host.onMineChanged(0); } });
                ok(out, true);
                return;
            }
            case "/api/ha/home": {
                final String id = r.query.get("id"), name = DeviceName.clean(r.query.get("name"));
                if (!HomeAssistant.validEntityId(id) || !(id.startsWith("scene.") || id.startsWith("script."))) { ok(out, false); return; }
                host.prefs().setHaHome(id, name == null ? id : name, "1".equals(r.query.get("on")));
                main.post(new Runnable() { @Override public void run() { host.onHaLinked(); host.onFavoritesChanged(); } });
                ok(out, true);
                return;
            }
            case "/api/ha/cinema": {
                JSONObject c;
                try { c = new JSONObject(new String(HttpRequest.readBody(in, r.contentLength, 16 * 1024), "UTF-8")); }
                catch (Exception e) { ok(out, false); return; }
                String film = c.optString("film"), pause = c.optString("pause");
                if (!film.isEmpty() && !HomeAssistant.validEntityId(film)) { ok(out, false); return; }
                if (!pause.isEmpty() && !HomeAssistant.validEntityId(pause)) { ok(out, false); return; }
                java.util.Set<String> apps = new java.util.HashSet<>();
                JSONArray ja = c.optJSONArray("apps");
                if (ja != null) for (int i = 0; i < ja.length() && i < 100; i++) { String pkg = ja.optString(i); if (pkg.matches("[A-Za-z0-9._]{1,150}")) apps.add(pkg); }
                host.prefs().setHaCinema(film, pause, c.optBoolean("onOpen"), apps);
                ok(out, true);
                return;
            }
            case "/api/ha/pin": {
                final String id = r.query.get("id");
                if (!HomeAssistant.validEntityId(id)) { ok(out, false); return; }
                host.prefs().setHaPinned(id, "1".equals(r.query.get("on")));
                ok(out, true);
                return;
            }
            case "/api/name": {
                final String n = DeviceName.clean(new String(HttpRequest.readBody(in, r.contentLength, 4 * DeviceName.MAX), "UTF-8"));
                if (n == null) { ok(out, false); return; }
                deviceName = n;
                main.post(new Runnable() { @Override public void run() { host.onPhoneName(n); } });
                ok(out, true);
                return;
            }
            case "/api/key": {
                final String k = r.query.get("k");
                if (k == null || !KEYS.contains(k)) { json(out, 400, "{\"ok\":false}"); return; }
                json(out, 200, onMain(new Callable<String>() { @Override public String call() { return host.phoneKey(k); } }));
                return;
            }
            case "/api/bye":
                json(out, 200, "{\"ok\":true}");
                main.post(new Runnable() { @Override public void run() { disconnect(); } });
                return;
            case "/api/favorites": { boolean ok = setFavorites(HttpRequest.readBody(in, r.contentLength, 64 * 1024)); ok(out, ok); return; }
            case "/api/upload": {
                sock.setSoTimeout(60_000);
                ok(out, upload(in, r.contentLength, r.header("x-lumen-name"), r.header("x-lumen-target")));
                return;
            }
            case "/api/delete": ok(out, deleteMine(r.query.get("id"))); return;
            case "/api/log/clear": CrashLog.clear(ctx); ok(out, true); return;
            case "/api/pick": ok(out, setPick(r.query.get("scope"), r.query.get("key"), "1".equals(r.query.get("on")))); return;
            case "/api/launch": json(out, 200, launch(r.query.get("key"), pinNeeded)); return;
            case "/api/text": json(out, 200, text(HttpRequest.readBody(in, r.contentLength, 4 * MAX_TEXT),
                    "1".equals(r.query.get("live")), "1".equals(r.query.get("enter")))); return;
            case "/api/ambiance": json(out, 200, ambiance(intParam(r, "kind", -2))); return;
            case "/api/timer": json(out, 200, timer(intParam(r, "min", -1))); return;
            case "/api/veille": json(out, 200, veille("1".equals(r.query.get("on")))); return;
            case "/api/source": json(out, 200, source(intParam(r, "n", -1))); return;
            case "/api/next": json(out, 200, onTv(new Runnable() { @Override public void run() { host.phoneNextWall(); } })); return;
            default: break;
        }
        // ---- actions sensibles : code PIN exigé s'il existe
        if (p.equals("/api/ha")) {
            byte[] body = HttpRequest.readBody(in, r.contentLength, 2048);
            if (pinNeeded) { json(out, 200, "{\"ok\":false,\"pin\":true}"); return; }
            json(out, 200, setHa(body));
            return;
        }
        if (p.equals("/api/pexels") || p.equals("/api/restore") || p.equals("/api/lock")) {
            if (pinNeeded) { HttpRequest.readBody(in, r.contentLength, 256 * 1024); json(out, 200, "{\"ok\":false,\"pin\":true}"); return; }
            if (p.equals("/api/pexels")) { ok(out, setPexels(HttpRequest.readBody(in, r.contentLength, 256))); return; }
            if (p.equals("/api/lock")) { ok(out, setLock(r.query.get("key"), "1".equals(r.query.get("on")))); return; }
            int n = restore(HttpRequest.readBody(in, r.contentLength, 256 * 1024));
            json(out, n >= 0 ? 200 : 400, "{\"ok\":" + (n >= 0) + ",\"count\":" + Math.max(0, n) + "}");
            return;
        }
        send(out, 404, "text/plain", new byte[0], null);
    }

    // ------------------------------------------------------------------ actions

    private JSONObject state(final String sid) throws Exception {
        final JSONObject o = new JSONObject();
        onMain(new Callable<Void>() {
            @Override public Void call() throws Exception {
                Prefs pr = host.prefs();
                Set<String> hidden = pr.hiddenApps();
                JSONArray apps = new JSONArray();
                for (AppRepo.App a : host.repo().all()) {
                    JSONObject j = new JSONObject();
                    j.put("key", a.key);
                    j.put("label", a.label);
                    if (!a.isInput()) j.put("pkg", a.pkg);
                    if (a.isInput()) j.put("input", true);
                    if (hidden.contains(a.key)) j.put("hidden", true);
                    if (pr.isLocked(a.key)) j.put("locked", true);
                    apps.put(j);
                }
                o.put("apps", apps);
                o.put("favorites", new JSONArray(pr.favorites()));
                JSONObject tv = new JSONObject();
                int[] amb = host.ambianceState();
                tv.put("home", host.onTvHome());
                tv.put("veille", host.veilleOn());
                tv.put("ambiance", amb[0]);
                tv.put("timer", amb[1]);
                tv.put("timerSet", amb[2]);
                tv.put("source", pr.source());
                tv.put("sources", new JSONArray(java.util.Arrays.asList(Prefs.SRC_NAMES)));
                JSONArray timers = new JSONArray();
                for (int m : AmbiancePanel.timerChoices()) timers.put(m);
                tv.put("timers", timers);
                tv.put("pin", pr.hasPin());
                tv.put("unlocked", pr.hasPin() && auth.isElevated(sid));
                tv.put("device", deviceName);
                tv.put("remote", RemoteService.connected());
                tv.put("dpadOutside", RemoteService.connected() && RemoteService.dpadSupported());
                o.put("tv", tv);
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
        o.put("ha", host.prefs().haConfigured() ? host.prefs().haUrl() : "");
        o.put("cinema", new JSONObject().put("film", host.prefs().haFilmScene()).put("pause", host.prefs().haPauseScene())
                .put("onOpen", host.prefs().haCinemaOnOpen()).put("apps", new JSONArray(host.prefs().haCinemaApps())));
        long[] st = host.online().storage();
        o.put("storage", new JSONObject().put("photos", Online.human(st[0])).put("videos", Online.human(st[1]))
                .put("cosy", Online.human(st[2])).put("mine", Online.human(st[3]))
                .put("free", Online.human(ctx.getFilesDir().getUsableSpace())));
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

    /** Restaure une sauvegarde de réglages (liste blanche de Backup). -1 si le fichier est refusé. */
    private int restore(byte[] body) throws Exception {
        final JSONObject o;
        try { o = new JSONObject(new String(body, "UTF-8")); } catch (Exception e) { return -1; }
        return onMain(new Callable<Integer>() {
            @Override public Integer call() {
                try {
                    int n = host.prefs().importBackup(o);
                    host.onSettingsRestored(n);
                    return n;
                } catch (Exception e) { return -1; }
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

    /**
     * Home Assistant : {"url":…,"token":…} vérifiés puis testés avant d'être gardés ; corps vide = délier.
     * Le jeton reste sur la TV et n'est jamais renvoyé.
     */
    private String setHa(byte[] body) throws Exception {
        String raw = new String(body, "UTF-8").trim();
        if (raw.isEmpty()) { host.prefs().setHa("", ""); return "{\"ok\":true}"; }
        JSONObject o;
        try { o = new JSONObject(raw); } catch (Exception e) { return "{\"ok\":false}"; }
        String url = HomeAssistant.normalizeUrl(o.optString("url"));
        String token = o.optString("token").trim();
        if (token.isEmpty()) token = host.prefs().haToken(); // changer seulement l'adresse
        if (url == null) return err("Adresse refusée : en http, seulement une adresse du réseau local (ex. 192.168.1.20:8123)");
        if (!HomeAssistant.validToken(token)) return err("Jeton invalide : crée un « jeton d'accès longue durée » dans ton profil Home Assistant");
        try { HomeAssistant.ping(url, token); }
        catch (Exception e) { CrashLog.note(ctx, "Home Assistant : " + e); return err(HomeAssistant.human(e)); }
        host.prefs().setHa(url, token);
        main.post(new Runnable() { @Override public void run() { host.onHaLinked(); } });
        return "{\"ok\":true}";
    }

    private static String err(String m) throws Exception { return new JSONObject().put("ok", false).put("error", m).toString(); }

    /** Appareils pilotables, avec ceux choisis pour la TV (sans aucun jeton). */
    private String haEntities() throws Exception {
        Prefs pr = host.prefs();
        if (!pr.haConfigured()) return "{\"ok\":false,\"configured\":false}";
        try {
            java.util.Set<String> pinned = pr.haPinned();
            java.util.Set<String> home = new java.util.HashSet<>();
            for (String[] h : pr.haHome()) home.add(h[0]);
            JSONArray a = new JSONArray();
            for (HomeAssistant.Entity e : HaStates.load(pr.haUrl(), pr.haToken())) {
                JSONObject j = new JSONObject();
                j.put("id", e.id); j.put("name", e.name); j.put("group", HomeAssistant.domainName(e.domain));
                j.put("state", HomeAssistant.label(e)); j.put("pinned", pinned.contains(e.id));
                j.put("home", home.contains(e.id));
                a.put(j);
            }
            return new JSONObject().put("ok", true).put("entities", a).toString();
        } catch (Exception e) {
            return err(HomeAssistant.human(e));
        }
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

    // ------------------------------------------------------------------ télécommande

    /** Code PIN saisi sur le téléphone : mêmes règles que sur la TV (empreinte, attente croissante). */
    private String pin(byte[] body, final String sid) throws Exception {
        final String pin = new String(body, "UTF-8").trim();
        return onMain(new Callable<String>() {
            @Override public String call() {
                Prefs pr = host.prefs();
                if (!pr.hasPin()) return "{\"ok\":true}";
                long lock = Pin.lockMillis(pr.pinFails());
                long wait = lock <= 0 ? 0 : pr.pinLastFail() + lock - System.currentTimeMillis();
                if (wait > 0) return "{\"ok\":false,\"wait\":" + ((wait + 999) / 1000) + "}";
                if (Pin.check(pin, pr.pinStored())) { pr.pinOk(); auth.elevate(sid); return "{\"ok\":true}"; }
                pr.pinFailed();
                long next = Pin.lockMillis(pr.pinFails());
                return "{\"ok\":false" + (next > 0 ? ",\"wait\":" + (next / 1000) : "") + "}";
            }
        });
    }

    /** Lance une appli (ou une source HDMI) installée et visible ; une appli verrouillée exige le code. */
    private String launch(final String key, final boolean pinNeeded) throws Exception {
        if (key == null || key.length() > 300) return "{\"ok\":false}";
        return onMain(new Callable<String>() {
            @Override public String call() {
                AppRepo.App a = host.repo().get(key);
                if (a == null || host.prefs().hiddenApps().contains(key)) return "{\"ok\":false}";
                if (host.prefs().isLocked(key) && pinNeeded) return "{\"ok\":false,\"pin\":true}";
                // autre appli à l'écran : possible seulement avec « Lumen télécommande » (Android le permet alors)
                if (!host.onTvHome() && !RemoteService.connected()) return "{\"ok\":false,\"busy\":true}";
                host.phoneLaunch(a);
                return "{\"ok\":true}";
            }
        });
    }

    /** Texte tapé sur le téléphone : uniquement vers les champs de Lumen (recherche d'appli, ville). */
    /**
     * Texte du téléphone. Lumen à l'écran : recherche d'appli ou champ ouvert. Autre appli : écrit
     * dans son champ de saisie (recherche…) par le service « Lumen télécommande ».
     * live = frappe en direct (le champ suit le téléphone, vide compris) ; enter = valider.
     */
    private String text(byte[] body, final boolean live, final boolean enter) throws Exception {
        final String t = new String(body, "UTF-8").trim();
        if ((t.isEmpty() && !live) || t.length() > MAX_TEXT || (!t.isEmpty() && !Backup.textOk(t, MAX_TEXT))) return "{\"ok\":false}";
        return onMain(new Callable<String>() {
            @Override public String call() {
                if (host.onTvHome()) {
                    if (!t.isEmpty() || live) host.phoneText(t);
                    return "{\"ok\":true}";
                }
                int r = RemoteService.typeText(t, enter);
                if (r > 0) return "{\"ok\":true}";
                return r == 0 ? "{\"ok\":false,\"nofield\":true}" : "{\"ok\":false,\"service\":false}";
            }
        });
    }

    private String ambiance(final int kind) throws Exception {
        if (kind < -1 || kind >= Online.COSY_NAMES.length) return "{\"ok\":false}";
        return onTv(new Runnable() { @Override public void run() { host.phoneAmbiance(kind); } });
    }

    private String timer(final int minutes) throws Exception {
        return onMain(new Callable<String>() {
            @Override public String call() {
                if (!host.onTvHome()) return "{\"ok\":false,\"busy\":true}";
                return "{\"ok\":" + host.phoneTimer(minutes) + "}";
            }
        });
    }

    private String veille(final boolean on) throws Exception {
        return onTv(new Runnable() { @Override public void run() { host.phoneVeille(on); } });
    }

    private String source(final int n) throws Exception {
        if (n < 0 || n >= Prefs.SRC_NAMES.length) return "{\"ok\":false}";
        return onMain(new Callable<String>() {
            @Override public String call() { host.phoneSource(n); return "{\"ok\":true}"; }
        });
    }

    /** Action visible sur la TV : seulement si Lumen est à l'écran (sinon, on ne vole pas l'écran à l'appli en cours). */
    private String onTv(final Runnable r) throws Exception {
        return onMain(new Callable<String>() {
            @Override public String call() {
                if (!host.onTvHome()) return "{\"ok\":false,\"busy\":true}";
                r.run();
                return "{\"ok\":true}";
            }
        });
    }

    private boolean setLock(final String key, final boolean on) throws Exception {
        if (key == null || key.length() > 300) return false;
        return onMain(new Callable<Boolean>() {
            @Override public Boolean call() {
                if (!host.prefs().hasPin() || host.repo().get(key) == null) return false;
                host.prefs().setLocked(key, on);
                return true;
            }
        });
    }

    // ------------------------------------------------------------------ choix des vidéos

    private static String scopeOf(String s) {
        return Online.SCOPE_COSY.equals(s) ? Online.SCOPE_COSY : Online.SCOPE_FONDS.equals(s) ? Online.SCOPE_FONDS : null;
    }

    /** Liste fermée des vidéos proposées (mêmes catalogues que sur la TV), avec l'état coché. */
    private JSONArray picks(String scope) throws Exception {
        String sc = scopeOf(scope);
        JSONArray arr = new JSONArray();
        if (sc == null) return arr;
        List<Online.Pick> list = host.online().picks(sc);
        Prefs pr = host.prefs();
        for (Online.Pick k : list) {
            JSONObject j = new JSONObject();
            j.put("key", k.key);
            j.put("title", k.title);
            j.put("group", k.group);
            j.put("dur", (int) k.duration);
            j.put("hd", k.height >= 1080 ? (k.height >= 2160 ? "4K" : "HD") : "");
            j.put("ok", pr.videoOk(k.key));
            j.put("img", host.online().thumbCached(k) != null);
            arr.put(j);
        }
        host.online().prefetchThumbs(list); // les aperçus manquants arrivent peu à peu
        return arr;
    }

    private Online.Pick findPick(String scope, String key) throws Exception {
        String sc = scopeOf(scope);
        if (sc == null || key == null || key.length() > 300) return null;
        for (Online.Pick k : host.online().picks(sc)) if (k.key.equals(key)) return k;
        return null;
    }

    /** Aperçu d'une vidéo du catalogue, seulement s'il est déjà sur la TV (jamais de téléchargement ici). */
    private byte[] pickThumb(String scope, String key) throws Exception {
        Online.Pick k = findPick(scope, key);
        File f = k == null ? null : host.online().thumbCached(k);
        if (f == null || f.length() > (2 << 20)) return null;
        try (InputStream in = new java.io.FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    /** Coche ou décoche une vidéo du catalogue (comme sur la TV : décocher l'efface). */
    private boolean setPick(final String scope, String key, final boolean on) throws Exception {
        final Online.Pick k = findPick(scope, key);
        if (k == null) return false;
        return onMain(new Callable<Boolean>() {
            @Override public Boolean call() {
                host.prefs().setVideoOk(k.key, on);
                host.phonePicksChanged(scopeOf(scope));
                return true;
            }
        });
    }

    private static int intParam(HttpRequest r, String name, int def) {
        String v = r.query.get(name);
        if (v == null || !v.matches("-?[0-9]{1,4}")) return def;
        return Integer.parseInt(v);
    }

    private static void json(OutputStream out, int code, String body) throws Exception {
        send(out, code, "application/json; charset=utf-8", body.getBytes("UTF-8"), null);
    }

    private static void ok(OutputStream out, boolean ok) throws Exception {
        json(out, ok ? 200 : 400, "{\"ok\":" + ok + "}");
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
            case 429: status = "Too Many Requests"; break;
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

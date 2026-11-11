// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Deux TV reliées restent identiques. Une fois reliées (code à 6 chiffres, voir SyncPanel), elles
 * partagent une clé de liaison (rangée chiffrée, Vault). Tant que Lumen est à l'écran, chaque TV
 * écoute sur son adresse locale (port 8767, appareils du réseau privé seulement) et s'annonce sur
 * le réseau ; à chaque changement (après 3 s de calme), au retour de Lumen à l'écran et toutes les
 * 3 minutes, elle envoie tout son état à l'autre, qui fusionne (le plus récent gagne, SyncState) et
 * répond avec le sien. Tout est chiffré et authentifié avec la clé de liaison (AES-256-GCM) ; un
 * message qui ne vient pas de l'autre TV est refusé. Si l'adresse de l'autre TV a changé, elle est
 * retrouvée par son annonce sur le réseau.
 */
final class PairLink {
    static final String SERVICE_TYPE = "_lumen-pair._tcp";
    static final int PORT = 8767;
    static final String SYNC = "/lumen-pair/sync", UNPAIR = "/lumen-pair/unpair";
    private static final long PERIOD = 3 * 60_000L, CALM = 3_000L;
    private static final int MAX = 2 * 1024 * 1024, MAX_ENTRIES = 5000;

    /** Ce que l'écran doit savoir (fil principal). */
    interface Listener {
        /** Réglages pris chez l'autre TV ; visible = combien se voient à l'écran (pas l'historique). */
        void applied(int visible, String peerName);
        /** L'autre TV a délié les deux TV. */
        void unpaired(String peerName);
    }

    private final Context app;
    private final SyncStore store;
    private final SharedPreferences pp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final RateLimit refused = new RateLimit(1, 10, System.currentTimeMillis());
    private final SecureRandom random = new SecureRandom();
    private final Listener listener;
    private ServerSocket server;
    private NsdManager.RegistrationListener registration;
    private volatile boolean started;
    /** 0 : pas encore essayé, 1 : dernier échange réussi, 2 : autre TV injoignable. */
    private volatile int lastResult;

    PairLink(Context c, Listener l) {
        app = c.getApplicationContext();
        listener = l;
        pp = app.getSharedPreferences("lumen_pair", Context.MODE_PRIVATE);
        store = new SyncStore(app);
        store.onLocalChange = new Runnable() { @Override public void run() { soon(CALM); } };
    }

    // ------------------------------------------------------------------ identité et liaison

    /** Nom de cette TV (celui choisi dans les paramètres d'Android, sinon le modèle). */
    static String deviceName(Context c) {
        String n = null;
        try { n = android.provider.Settings.Global.getString(c.getContentResolver(), "device_name"); } catch (Exception ignored) { }
        if (n == null || n.trim().isEmpty()) n = Build.MODEL;
        return TvSync.cleanName(n);
    }

    /** Identifiant de cette TV (au hasard, tiré une fois ; ne dit rien d'elle). */
    String myId() {
        String id = pp.getString("me", "");
        if (!SyncState.deviceIdOk(id)) {
            byte[] b = new byte[8];
            random.nextBytes(b);
            id = Signer.hex(b);
            pp.edit().putString("me", id).commit();
        }
        return id;
    }

    boolean paired() { return SyncState.deviceIdOk(pp.getString("peer", "")) && key() != null; }
    String peerName() { return pp.getString("peer_name", ""); }
    long lastOk() { return pp.getLong("last_ok", 0); }
    boolean unreachable() { return lastResult == 2; }

    private byte[] key() {
        byte[] b = SyncCrypto.unhex(Vault.open(pp.getString("key", "")));
        return b != null && b.length == 32 ? b : null;
    }

    /** Relie cette TV à l'autre (clé tirée de l'échange réussi avec le code), puis lance les échanges. */
    void pair(String peerId, String name, String ip, int port, byte[] sessionKey) {
        pp.edit().putString("peer", peerId).putString("peer_name", TvSync.cleanName(name)).putString("peer_ip", ip == null ? "" : ip)
                .putInt("peer_port", port).putString("key", Vault.seal(Signer.hex(SyncCrypto.pairKey(sessionKey)))).putLong("last_ok", 0).commit();
        lastResult = 0;
        if (started) start();
    }

    /** Délie (et prévient l'autre TV si tell, sans attendre). */
    void unpair(boolean tell) {
        if (tell && paired()) {
            final byte[] k = key();
            final String me = myId(), peer = pp.getString("peer", ""), ip = pp.getString("peer_ip", "");
            final int port = pp.getInt("peer_port", PORT);
            worker.execute(new Runnable() {
                @Override public void run() {
                    try {
                        InetAddress a = InetAddress.getByName(ip);
                        byte[] body = SyncCrypto.pairSeal(k, ("{\"from\":\"" + me + "\",\"to\":\"" + peer + "\",\"unpair\":true}").getBytes("UTF-8"), me, peer, random);
                        SyncLink.request(a, port, "POST", UNPAIR, body, 1024);
                    } catch (Exception ignored) { }
                }
            });
        }
        pp.edit().remove("peer").remove("peer_name").remove("peer_ip").remove("peer_port").remove("key").remove("last_ok").commit();
        lastResult = 0;
        main.removeCallbacks(roundNow);
        stopServer();
    }

    /** Liaison : cette TV prend tout l'état de l'autre (et perd ce qui n'existe pas là-bas). */
    int adopt(Map<String, SyncState.Entry> theirs) { return store.apply(theirs, new java.util.ArrayList<>(theirs.keySet()), true); }

    /** Tout l'état de cette TV, pour la liaison (fil principal). */
    JSONObject state() throws Exception { return entriesJson(store.export()); }

    // ------------------------------------------------------------------ cycle de vie (Lumen à l'écran)

    void start() {
        started = true;
        if (!paired()) return;
        startServer();
        soon(2000);
        main.removeCallbacks(periodic);
        main.postDelayed(periodic, PERIOD);
    }

    void stop() {
        started = false;
        main.removeCallbacks(roundNow);
        main.removeCallbacks(periodic);
        stopServer();
    }

    /** L'écran est détruit : plus d'écouteurs ni de fil d'échange. */
    void close() {
        stop();
        store.close();
        worker.shutdown();
    }

    /** Port d'écoute, en démarrant le serveur si besoin (avant la liaison : l'autre TV doit pouvoir répondre). */
    int prepare() {
        startServer();
        synchronized (this) { return server == null ? 0 : server.getLocalPort(); }
    }

    void syncNow() { soon(0); }

    private void soon(long ms) {
        main.removeCallbacks(roundNow);
        if (started && paired()) main.postDelayed(roundNow, ms);
    }

    private final Runnable roundNow = new Runnable() {
        @Override public void run() { try { worker.execute(new Runnable() { @Override public void run() { round(); } }); } catch (Exception ignored) { } }
    };

    private final Runnable periodic = new Runnable() {
        @Override public void run() { soon(0); main.postDelayed(this, PERIOD); }
    };

    // ------------------------------------------------------------------ un échange (fil d'échange)

    private void round() {
        try {
            byte[] k = key();
            final String me = myId(), peer = pp.getString("peer", "");
            if (k == null || !SyncState.deviceIdOk(peer)) return;
            byte[] body = SyncCrypto.pairSeal(k, onMain(new Callable<byte[]>() {
                @Override public byte[] call() throws Exception { return message(me, peer).toString().getBytes("UTF-8"); }
            }).call(), me, peer, random);
            Object[] r = null;
            InetAddress ip = local(pp.getString("peer_ip", ""));
            int port = pp.getInt("peer_port", 0);
            if (ip != null && port > 0) { try { r = SyncLink.request(ip, port, "POST", SYNC, body, MAX); } catch (Exception ignored) { } }
            if (r == null || (Integer) r[0] != 200) {
                InetSocketAddress f = discover(peer, 6000); // nouvelle adresse (box redémarrée…) ou Lumen relancé sur un autre port
                if (f != null) {
                    pp.edit().putString("peer_ip", f.getAddress().getHostAddress()).putInt("peer_port", f.getPort()).apply();
                    try { r = SyncLink.request(f.getAddress(), f.getPort(), "POST", SYNC, body, MAX); } catch (Exception ignored) { }
                }
            }
            if (r == null || (Integer) r[0] != 200) { lastResult = 2; return; }
            byte[] plain = SyncCrypto.pairOpen(k, ((String) r[1]).getBytes("UTF-8"), peer, me);
            if (plain == null) { lastResult = 2; return; }
            final JSONObject o = new JSONObject(new String(plain, "UTF-8"));
            if (!peer.equals(o.optString("from")) || !me.equals(o.optString("to"))) { lastResult = 2; return; }
            main.post(new Runnable() { @Override public void run() { merge(o, null, 0); } });
            pp.edit().putLong("last_ok", System.currentTimeMillis()).apply();
            lastResult = 1;
        } catch (Exception e) {
            lastResult = 2;
        }
    }

    /** Message d'échange : de qui, pour qui, nom, port d'écoute, tout l'état (fil principal). */
    private JSONObject message(String me, String peer) throws Exception {
        int port;
        synchronized (this) { port = server == null ? 0 : server.getLocalPort(); }
        return new JSONObject().put("from", me).put("to", peer).put("name", deviceName(app)).put("port", port).put("entries", entriesJson(store.export()));
    }

    /** Fusionne l'état reçu (fil principal) ; garde aussi le nom et l'adresse de l'autre TV à jour. */
    private void merge(JSONObject o, String fromIp, int fromPort) {
        if (!paired()) return;
        SharedPreferences.Editor ed = pp.edit();
        String name = TvSync.cleanName(o.optString("name", peerName()));
        ed.putString("peer_name", name);
        if (fromIp != null) ed.putString("peer_ip", fromIp);
        if (fromPort > 0) ed.putInt("peer_port", fromPort);
        ed.putLong("last_ok", System.currentTimeMillis()).apply();
        lastResult = 1;
        Map<String, SyncState.Entry> theirs = entriesFrom(o.optJSONObject("entries"));
        if (theirs == null) return;
        List<String> keys = SyncState.toApply(store.export(), theirs, myId(), pp.getString("peer", ""), System.currentTimeMillis());
        if (keys.isEmpty()) return;
        store.apply(theirs, keys, false);
        int visible = 0;
        for (String k : keys) if (!SyncState.quiet(k)) visible++;
        if (listener != null) listener.applied(visible, name);
    }

    // ------------------------------------------------------------------ serveur (Lumen à l'écran)

    private synchronized void startServer() {
        if (server != null && !server.isClosed()) return;
        InetAddress ip = PhoneServer.localAddress();
        if (ip == null) return;
        final ServerSocket s;
        try {
            s = new ServerSocket();
            s.setReuseAddress(true);
            try { s.bind(new InetSocketAddress(ip, PORT), 4); }
            catch (Exception busy) { s.bind(new InetSocketAddress(ip, 0), 4); }
        } catch (Exception e) {
            return;
        }
        server = s;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (!s.isClosed()) {
                    try {
                        Socket sock = s.accept();
                        InetAddress from = sock.getInetAddress();
                        if (!(from instanceof Inet4Address) || !from.isSiteLocalAddress()) { sock.close(); continue; }
                        serve(sock);
                    } catch (Exception ignored) { }
                }
            }
        }, "lumen-pair");
        t.setDaemon(true);
        t.start();
        register(s.getLocalPort());
    }

    private synchronized void stopServer() {
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null;
        if (registration != null) {
            try { nsd().unregisterService(registration); } catch (Exception ignored) { }
            registration = null;
        }
    }

    private void serve(Socket sock) {
        try {
            sock.setSoTimeout(5_000);
            InputStream in = new BufferedInputStream(sock.getInputStream());
            OutputStream out = sock.getOutputStream();
            HttpRequest r;
            try { r = HttpRequest.read(in); } catch (Exception bad) { reply(out, 400, ""); return; }
            if (!r.method.equals("POST") || (!r.path.equals(SYNC) && !r.path.equals(UNPAIR))) { reply(out, 404, ""); return; }
            if (r.contentLength > MAX) { reply(out, 413, ""); return; }
            sock.setSoTimeout(15_000);
            byte[] body = HttpRequest.readBody(in, r.contentLength, MAX);
            final byte[] k = key();
            final String me = myId(), peer = pp.getString("peer", "");
            byte[] plain = k == null ? null : SyncCrypto.pairOpen(k, body, peer, me);
            if (plain == null) {
                // pas l'autre TV : refusé, et au-delà de quelques essais on ne répond même plus vite
                if (!refused.allow(System.currentTimeMillis())) Thread.sleep(1000);
                reply(out, 403, "");
                return;
            }
            final JSONObject o = new JSONObject(new String(plain, "UTF-8"));
            if (!peer.equals(o.optString("from")) || !me.equals(o.optString("to"))) { reply(out, 403, ""); return; }
            if (r.path.equals(UNPAIR)) {
                reply(out, 200, "");
                main.post(new Runnable() {
                    @Override public void run() {
                        String name = peerName();
                        unpair(false);
                        if (listener != null) listener.unpaired(name);
                    }
                });
                return;
            }
            final String fromIp = sock.getInetAddress().getHostAddress();
            final int fromPort = o.optInt("port", 0);
            byte[] answer = onMain(new Callable<byte[]>() {
                @Override public byte[] call() throws Exception {
                    merge(o, fromIp, fromPort > 0 && fromPort <= 65535 ? fromPort : 0);
                    return message(me, peer).toString().getBytes("UTF-8");
                }
            }).call();
            byte[] sealed = SyncCrypto.pairSeal(k, answer, me, peer, random);
            reply(out, 200, new String(sealed, "UTF-8"));
        } catch (Exception ignored) {
        } finally {
            try { sock.close(); } catch (Exception ignored) { }
        }
    }

    private static void reply(OutputStream out, int code, String body) throws Exception {
        byte[] b = body.getBytes("UTF-8");
        String head = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "Error") + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "
                + b.length + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.write(b);
        out.flush();
    }

    // ------------------------------------------------------------------ annonce et recherche sur le réseau

    private NsdManager nsd() { return (NsdManager) app.getSystemService(Context.NSD_SERVICE); }

    private void register(int port) {
        try {
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName("lumen-" + myId());
            info.setServiceType(SERVICE_TYPE);
            info.setPort(port);
            NsdManager.RegistrationListener l = new NsdManager.RegistrationListener() {
                @Override public void onRegistrationFailed(NsdServiceInfo i, int err) { }
                @Override public void onUnregistrationFailed(NsdServiceInfo i, int err) { }
                @Override public void onServiceRegistered(NsdServiceInfo i) { }
                @Override public void onServiceUnregistered(NsdServiceInfo i) { }
            };
            nsd().registerService(info, NsdManager.PROTOCOL_DNS_SD, l);
            registration = l;
        } catch (Exception ignored) { }
    }

    /** Cherche l'annonce de l'autre TV (« lumen-<identifiant> ») ; null si rien dans le délai. */
    private InetSocketAddress discover(final String peerId, long waitMs) {
        final CountDownLatch done = new CountDownLatch(1);
        final InetSocketAddress[] found = {null};
        final NsdManager nsd = nsd();
        final boolean[] resolving = {false};
        NsdManager.DiscoveryListener l = new NsdManager.DiscoveryListener() {
            @Override public void onStartDiscoveryFailed(String type, int err) { done.countDown(); }
            @Override public void onStopDiscoveryFailed(String type, int err) { }
            @Override public void onDiscoveryStarted(String type) { }
            @Override public void onDiscoveryStopped(String type) { }
            @Override public void onServiceLost(NsdServiceInfo info) { }
            @Override public void onServiceFound(NsdServiceInfo info) {
                synchronized (resolving) {
                    if (resolving[0] || !("lumen-" + peerId).equals(info.getServiceName())) return;
                    resolving[0] = true;
                }
                try {
                    nsd.resolveService(info, new NsdManager.ResolveListener() {
                        @Override public void onResolveFailed(NsdServiceInfo i, int err) { done.countDown(); }
                        @Override public void onServiceResolved(NsdServiceInfo i) {
                            InetAddress a = i.getHost();
                            if (a instanceof Inet4Address && a.isSiteLocalAddress() && i.getPort() > 0) found[0] = new InetSocketAddress(a, i.getPort());
                            done.countDown();
                        }
                    });
                } catch (Exception e) { done.countDown(); }
            }
        };
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l);
            done.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        } finally {
            try { nsd.stopServiceDiscovery(l); } catch (Exception ignored) { }
        }
        return found[0];
    }

    // ------------------------------------------------------------------ outils

    private static InetAddress local(String ip) {
        if (ip == null || ip.isEmpty() || TvSync.address(ip) == null) return null;
        try { return InetAddress.getByName(ip); } catch (Exception e) { return null; }
    }

    /** Exécute sur le fil principal et attend le résultat (8 s au plus). */
    private <T> Callable<T> onMain(final Callable<T> c) {
        return new Callable<T>() {
            @Override public T call() throws Exception {
                if (Looper.myLooper() == Looper.getMainLooper()) return c.call();
                FutureTask<T> f = new FutureTask<>(c);
                main.post(f);
                return f.get(8, TimeUnit.SECONDS);
            }
        };
    }

    static JSONObject entriesJson(Map<String, SyncState.Entry> m) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, SyncState.Entry> e : m.entrySet())
            o.put(e.getKey(), new JSONArray().put(e.getValue().value == null ? JSONObject.NULL : e.getValue().value).put(e.getValue().ts));
        return o;
    }

    /** État reçu ; null s'il est mal formé. Les réglages qui ne voyagent pas sont ignorés. */
    static Map<String, SyncState.Entry> entriesFrom(JSONObject o) {
        if (o == null || o.length() > MAX_ENTRIES) return null;
        Map<String, SyncState.Entry> out = new HashMap<>();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONArray a = o.optJSONArray(k);
            if (a == null || a.length() != 2 || !SyncState.synced(k)) continue;
            long ts = a.optLong(1, -1);
            if (ts < 0) continue;
            String v = a.isNull(0) ? null : a.optString(0, null);
            out.put(k, new SyncState.Entry(v, ts));
        }
        return out;
    }
}

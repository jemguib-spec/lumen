// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transport de la copie des réglages entre TV (règles dans TvSync) :
 * - la TV qui reçoit ouvre, à la demande et pour 3 minutes au plus, un petit serveur sur son
 *   adresse du réseau local et s'annonce sur le réseau (DNS-SD) ; un seul envoi est accepté, signé
 *   avec le code qu'elle affiche, et elle demande confirmation avant d'appliquer quoi que ce soit ;
 * - la TV qui envoie cherche les TV annoncées et leur envoie ses réglages (liste blanche).
 * Rien n'écoute en dehors de l'écran « Recevoir » ; adresses du réseau privé uniquement.
 */
final class SyncLink {
    private SyncLink() { }

    private static final Handler main = new Handler(Looper.getMainLooper());

    // ------------------------------------------------------------------ réception

    /** Envoi reçu et signé (fil principal) : nom de l'autre TV et contenu (JSON). */
    interface Received { void on(String from, String json); }
    /** Réception arrêtée d'elle-même (fil principal). */
    interface Stopped { void on(int why); }
    static final int TOO_MANY = 1, TIMEOUT = 2;

    private static ServerSocket server;
    private static TvSync.Gate gate;
    private static NsdManager.RegistrationListener registration;
    private static Runnable timeout;

    /**
     * Ouvre la réception ; renvoie {code, adresse affichable} ou null sans réseau. Les rappels ne
     * viennent plus une fois la réception arrêtée (stopReceiving).
     */
    static synchronized String[] startReceiving(Context c, final String name, final Received received, final Stopped stopped) {
        final Context app = c.getApplicationContext();
        stopReceiving(app);
        InetAddress ip = PhoneServer.localAddress();
        if (ip == null) return null;
        final ServerSocket s;
        try {
            s = new ServerSocket();
            s.setReuseAddress(true);
            try { s.bind(new InetSocketAddress(ip, TvSync.PORT), 4); }
            catch (Exception busy) { s.bind(new InetSocketAddress(ip, 0), 4); }
        } catch (Exception e) {
            return null;
        }
        SecureRandom r = new SecureRandom();
        final TvSync.Gate g = new TvSync.Gate(TvSync.newCode(r), TvSync.newNonce(r), System.currentTimeMillis());
        server = s;
        gate = g;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (!s.isClosed()) {
                    try {
                        Socket sock = s.accept();
                        InetAddress from = sock.getInetAddress();
                        if (!(from instanceof Inet4Address) || !from.isSiteLocalAddress()) { sock.close(); continue; }
                        serve(app, sock, g, name, received, stopped);
                    } catch (Exception ignored) { }
                }
            }
        }, "lumen-sync");
        t.setDaemon(true);
        t.start();
        register(app, name, s.getLocalPort());
        timeout = new Runnable() {
            @Override public void run() { if (stopIfCurrent(app, g)) stopped.on(TIMEOUT); }
        };
        main.postDelayed(timeout, TvSync.OPEN_MS);
        int port = s.getLocalPort();
        return new String[]{g.code(), ip.getHostAddress() + (port == TvSync.PORT ? "" : ":" + port)};
    }

    static synchronized void stopReceiving(Context c) {
        if (timeout != null) main.removeCallbacks(timeout);
        timeout = null;
        gate = null;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null;
        if (registration != null) {
            try { nsd(c).unregisterService(registration); } catch (Exception ignored) { }
            registration = null;
        }
    }

    static synchronized boolean receiving() { return server != null; }

    /** Arrête la réception si c'est toujours celle-là ; true si elle l'était. */
    private static synchronized boolean stopIfCurrent(Context c, TvSync.Gate g) {
        if (gate != g) return false;
        stopReceiving(c);
        return true;
    }

    /** Une connexion : « bonjour » (numéro aléatoire et nom), ou l'envoi signé. Une à la fois. */
    private static void serve(final Context app, Socket sock, final TvSync.Gate g, String name, final Received received, final Stopped stopped) {
        try {
            sock.setSoTimeout(5_000);
            InputStream in = new BufferedInputStream(sock.getInputStream());
            OutputStream out = sock.getOutputStream();
            HttpRequest r;
            try { r = HttpRequest.read(in); } catch (Exception bad) { reply(out, 400, ""); return; }
            if (r.method.equals("GET") && r.path.equals(TvSync.HELLO)) { reply(out, 200, TvSync.helloBody(g.nonce(), name)); return; }
            if (!r.method.equals("POST") || !r.path.equals(TvSync.SEND)) { reply(out, 404, ""); return; }
            if (r.contentLength > TvSync.MAX_BODY) { reply(out, 413, ""); return; }
            sock.setSoTimeout(15_000);
            final byte[] body = HttpRequest.readBody(in, r.contentLength, TvSync.MAX_BODY);
            int verdict = g.check(body, r.header(TvSync.SIG_HEADER), System.currentTimeMillis());
            if (verdict == TvSync.Gate.CLOSED) { reply(out, 410, ""); return; }
            if (verdict == TvSync.Gate.BAD_CODE) {
                reply(out, 403, "code");
                if (g.closed(System.currentTimeMillis())) {
                    main.post(new Runnable() { @Override public void run() { if (stopIfCurrent(app, g)) stopped.on(TOO_MANY); } });
                }
                return;
            }
            reply(out, 200, "ok");
            final String from = sock.getInetAddress().getHostAddress();
            main.post(new Runnable() {
                @Override public void run() {
                    if (!stopIfCurrent(app, g)) return; // réception annulée entre-temps
                    try { received.on(from, new String(body, "UTF-8")); } catch (Exception ignored) { }
                }
            });
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

    private static NsdManager nsd(Context c) { return (NsdManager) c.getApplicationContext().getSystemService(Context.NSD_SERVICE); }

    /** Annonce la TV sur le réseau local (nom de la TV, port) le temps de la réception. */
    private static void register(Context c, String name, int port) {
        try {
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName(name);
            info.setServiceType(TvSync.SERVICE_TYPE);
            info.setPort(port);
            NsdManager.RegistrationListener l = new NsdManager.RegistrationListener() {
                @Override public void onRegistrationFailed(NsdServiceInfo i, int err) { }
                @Override public void onUnregistrationFailed(NsdServiceInfo i, int err) { }
                @Override public void onServiceRegistered(NsdServiceInfo i) { }
                @Override public void onServiceUnregistered(NsdServiceInfo i) { }
            };
            nsd(c).registerService(info, NsdManager.PROTOCOL_DNS_SD, l);
            registration = l;
        } catch (Exception ignored) {
            // sans annonce, l'adresse affichée permet encore l'envoi (« Saisir l'adresse d'une TV »)
        }
    }

    // ------------------------------------------------------------------ recherche des TV

    static final class Tv {
        final String name;
        final InetAddress ip;
        final int port;
        Tv(String name, InetAddress ip, int port) { this.name = name; this.ip = ip; this.port = port; }
    }

    /** Liste des TV trouvées, à chaque changement (fil principal). */
    interface Found { void on(List<Tv> tvs); }

    private static NsdManager.DiscoveryListener discovery;
    private static int finding; // numéro de la recherche en cours : les rappels d'une ancienne sont ignorés
    private static final List<NsdServiceInfo> toResolve = new ArrayList<>();
    private static boolean resolving;
    private static final Map<String, Tv> found = new LinkedHashMap<>();
    private static Found onFound;

    /** Cherche les TV en réception sur le réseau local (fil principal). */
    static void startFinding(Context c, Found cb) {
        final Context app = c.getApplicationContext();
        stopFinding(app);
        final int gen = ++finding;
        onFound = cb;
        NsdManager.DiscoveryListener l = new NsdManager.DiscoveryListener() {
            @Override public void onStartDiscoveryFailed(String type, int err) { }
            @Override public void onStopDiscoveryFailed(String type, int err) { }
            @Override public void onDiscoveryStarted(String type) { }
            @Override public void onDiscoveryStopped(String type) { }
            @Override public void onServiceFound(final NsdServiceInfo info) {
                main.post(new Runnable() {
                    @Override public void run() {
                        if (gen != finding || info.getServiceType() == null || !info.getServiceType().startsWith(TvSync.SERVICE_TYPE)) return;
                        toResolve.add(info);
                        resolveNext(app, gen);
                    }
                });
            }
            @Override public void onServiceLost(final NsdServiceInfo info) {
                main.post(new Runnable() {
                    @Override public void run() {
                        if (gen != finding || found.remove(info.getServiceName()) == null) return;
                        publish();
                    }
                });
            }
        };
        try {
            nsd(app).discoverServices(TvSync.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l);
            discovery = l;
        } catch (Exception ignored) { }
    }

    /** Une résolution à la fois (les anciennes versions d'Android refusent les suivantes). */
    private static void resolveNext(final Context app, final int gen) {
        if (resolving || toResolve.isEmpty() || gen != finding) return;
        resolving = true;
        NsdServiceInfo info = toResolve.remove(0);
        final Runnable next = new Runnable() { @Override public void run() { resolving = false; resolveNext(app, gen); } };
        try {
            nsd(app).resolveService(info, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo i, int err) { main.post(next); }
                @Override public void onServiceResolved(final NsdServiceInfo i) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            InetAddress ip = i.getHost();
                            InetAddress me = PhoneServer.localAddress();
                            if (gen == finding && ip instanceof Inet4Address && ip.isSiteLocalAddress() && !ip.equals(me) && i.getPort() > 0) {
                                found.put(i.getServiceName(), new Tv(TvSync.cleanName(i.getServiceName()), ip, i.getPort()));
                                publish();
                            }
                            next.run();
                        }
                    });
                }
            });
        } catch (Exception e) {
            main.post(next);
        }
    }

    private static void publish() {
        if (onFound != null) onFound.on(new ArrayList<>(found.values()));
    }

    static void stopFinding(Context c) {
        finding++;
        onFound = null;
        toResolve.clear();
        resolving = false;
        found.clear();
        if (discovery != null) {
            try { nsd(c).stopServiceDiscovery(discovery); } catch (Exception ignored) { }
            discovery = null;
        }
    }

    /** Lumen quitte l'écran ou la TV s'éteint : plus rien n'écoute ni ne cherche. */
    static void stopAll(Context c) {
        stopReceiving(c);
        stopFinding(c);
    }

    // ------------------------------------------------------------------ envoi (hors du fil principal)

    static final int SENT = 0, BAD_CODE = 1, CLOSED = 2, UNREACHABLE = 3, NOT_LUMEN = 4;

    /** {numéro, nom} de la TV en réception à cette adresse, ou null. */
    static String[] hello(InetAddress ip, int port) {
        try {
            Object[] r = request(ip, port, "GET", TvSync.HELLO, null, null);
            return (Integer) r[0] == 200 ? TvSync.parseHello((String) r[1]) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Envoie les réglages (JSON) signés avec le code tapé ; renvoie SENT, BAD_CODE… */
    static int send(InetAddress ip, int port, String code, byte[] body) {
        if (!(ip instanceof Inet4Address) || !ip.isSiteLocalAddress()) return UNREACHABLE;
        String[] h;
        try {
            Object[] r = request(ip, port, "GET", TvSync.HELLO, null, null);
            if ((Integer) r[0] == 410) return CLOSED;
            h = (Integer) r[0] == 200 ? TvSync.parseHello((String) r[1]) : null;
        } catch (Exception e) {
            return UNREACHABLE;
        }
        if (h == null) return NOT_LUMEN;
        try {
            Object[] r = request(ip, port, "POST", TvSync.SEND, body, TvSync.sign(code, h[0], body));
            int status = (Integer) r[0];
            return status == 200 ? SENT : status == 403 ? BAD_CODE : status == 410 ? CLOSED : UNREACHABLE;
        } catch (Exception e) {
            return UNREACHABLE;
        }
    }

    /** Requête HTTP minimale, directe (sans proxy), vers une adresse du réseau privé uniquement. */
    private static Object[] request(InetAddress ip, int port, String method, String path, byte[] body, String sig) throws Exception {
        if (!(ip instanceof Inet4Address) || !ip.isSiteLocalAddress()) throw new java.io.IOException("Adresse hors du réseau local");
        Socket s = new Socket(Proxy.NO_PROXY);
        try {
            s.connect(new InetSocketAddress(ip, port), 4_000);
            s.setSoTimeout(8_000);
            StringBuilder h = new StringBuilder(method + " " + path + " HTTP/1.1\r\nHost: " + ip.getHostAddress() + ":" + port + "\r\nConnection: close\r\n");
            h.append("Content-Length: ").append(body == null ? 0 : body.length).append("\r\n");
            if (sig != null) h.append(TvSync.SIG_HEADER).append(": ").append(sig).append("\r\n");
            h.append("\r\n");
            OutputStream out = s.getOutputStream();
            out.write(h.toString().getBytes("UTF-8"));
            if (body != null) out.write(body);
            out.flush();
            return TvSync.readResponse(new BufferedInputStream(s.getInputStream()), 4096);
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }
}

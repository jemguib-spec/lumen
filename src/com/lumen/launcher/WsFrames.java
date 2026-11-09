// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

/**
 * WebSocket minimal (RFC 6455) pour la télécommande du téléphone : une seule connexion qui reste
 * ouverte, chaque appui part aussitôt. Messages texte courts uniquement (pas de fragmentation,
 * 4 Ko au plus, trames du téléphone obligatoirement masquées). Java pur, testé.
 */
final class WsFrames {
    private WsFrames() { }

    static final int TEXT = 1, CLOSE = 8, PING = 9, PONG = 10;
    static final int MAX = 4096;

    /** Valeur de Sec-WebSocket-Accept pour la clé envoyée par le navigateur. */
    static String accept(String key) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest((key.trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8"));
        return base64(d);
    }

    /** Clé de navigateur valable : 16 octets en base64 (24 caractères). */
    static boolean validKey(String key) { return key != null && key.trim().matches("[A-Za-z0-9+/]{22}==") ; }

    static String base64(byte[] b) {
        String t = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < b.length; i += 3) {
            int n = (b[i] & 255) << 16 | (i + 1 < b.length ? (b[i + 1] & 255) << 8 : 0) | (i + 2 < b.length ? b[i + 2] & 255 : 0);
            s.append(t.charAt(n >> 18 & 63)).append(t.charAt(n >> 12 & 63));
            s.append(i + 1 < b.length ? t.charAt(n >> 6 & 63) : '=');
            s.append(i + 2 < b.length ? t.charAt(n & 63) : '=');
        }
        return s.toString();
    }

    /** Trame lue : {opcode, contenu}. Refuse les trames non masquées, fragmentées ou trop grandes. */
    static Object[] read(InputStream in) throws IOException {
        int b0 = in.read(), b1 = in.read();
        if (b0 < 0 || b1 < 0) throw new IOException("fermé");
        if ((b0 & 0x80) == 0) throw new IOException("fragmenté");
        if ((b0 & 0x70) != 0) throw new IOException("extension");
        int op = b0 & 0x0F;
        if ((b1 & 0x80) == 0) throw new IOException("non masqué");
        long len = b1 & 0x7F;
        if (len == 126) len = (readByte(in) << 8) | readByte(in);
        else if (len == 127) { len = 0; for (int i = 0; i < 8; i++) len = (len << 8) | readByte(in); }
        if (len > MAX) throw new IOException("trop grand");
        byte[] mask = new byte[4];
        for (int i = 0; i < 4; i++) mask[i] = (byte) readByte(in);
        byte[] p = new byte[(int) len];
        int off = 0;
        while (off < p.length) { int r = in.read(p, off, p.length - off); if (r < 0) throw new IOException("coupé"); off += r; }
        for (int i = 0; i < p.length; i++) p[i] ^= mask[i & 3];
        return new Object[]{op, p};
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new IOException("coupé");
        return b;
    }

    /** Trame envoyée par la TV (jamais masquée), écrite d'un seul bloc. */
    static byte[] frame(int op, byte[] payload) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(payload.length + 4);
        o.write(0x80 | op);
        if (payload.length < 126) o.write(payload.length);
        else { o.write(126); o.write(payload.length >> 8); o.write(payload.length & 255); }
        o.write(payload, 0, payload.length);
        return o.toByteArray();
    }

    static void send(OutputStream out, int op, byte[] payload) throws IOException {
        synchronized (out) { out.write(frame(op, payload)); out.flush(); }
    }

    static void sendText(OutputStream out, String s) throws IOException { send(out, TEXT, s.getBytes("UTF-8")); }
}

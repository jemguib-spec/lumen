// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.nio.charset.StandardCharsets;

/**
 * Générateur de QR code minimal (mode octet, correction d'erreur L, versions 1 à 10),
 * d'après l'algorithme de référence de la norme ISO/IEC 18004. Aucune dépendance.
 */
final class QrCode {
    private static final int[] ECC_PER_BLOCK = {-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18};
    private static final int[] NUM_BLOCKS = {-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4};

    final int size;
    private final boolean[][] modules;   // [y][x]
    private final boolean[][] isFunction;

    boolean get(int x, int y) { return modules[y][x]; }

    static QrCode encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        for (int ver = 1; ver <= 10; ver++) {
            int capBits = dataCodewords(ver) * 8;
            int ccBits = ver <= 9 ? 8 : 16;
            int used = 4 + ccBits + data.length * 8;
            if (used <= capBits) return new QrCode(ver, data);
        }
        throw new IllegalArgumentException("Texte trop long pour le QR code");
    }

    private QrCode(int ver, byte[] data) {
        size = ver * 4 + 17;
        modules = new boolean[size][size];
        isFunction = new boolean[size][size];

        // --- flux de bits
        int capBytes = dataCodewords(ver);
        BitBuf bb = new BitBuf();
        bb.append(0x4, 4);
        bb.append(data.length, ver <= 9 ? 8 : 16);
        for (byte b : data) bb.append(b & 0xFF, 8);
        bb.append(0, Math.min(4, capBytes * 8 - bb.len));
        bb.append(0, (8 - bb.len % 8) % 8);
        for (int pad = 0xEC; bb.len < capBytes * 8; pad ^= 0xEC ^ 0x11) bb.append(pad, 8);
        byte[] dataCw = new byte[capBytes];
        for (int i = 0; i < bb.len; i++) if (bb.get(i)) dataCw[i >>> 3] |= (byte) (1 << (7 - (i & 7)));

        byte[] all = addEccAndInterleave(ver, dataCw);

        drawFunctionPatterns(ver);
        drawCodewords(all);
        applyMask0();
        drawFormatBits(0);
        if (ver >= 7) drawVersion(ver);
    }

    // ------------------------------------------------------------------ correction d'erreur

    private static int numRawDataModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) result -= 36;
        }
        return result;
    }

    private static int dataCodewords(int ver) {
        return numRawDataModules(ver) / 8 - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver];
    }

    private static byte[] addEccAndInterleave(int ver, byte[] data) {
        int numBlocks = NUM_BLOCKS[ver], eccLen = ECC_PER_BLOCK[ver];
        int rawCodewords = numRawDataModules(ver) / 8;
        int numShort = numBlocks - rawCodewords % numBlocks;
        int shortLen = rawCodewords / numBlocks;
        byte[][] blocks = new byte[numBlocks][];
        byte[] div = rsDivisor(eccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int datLen = shortLen - eccLen + (i < numShort ? 0 : 1);
            byte[] dat = new byte[datLen];
            System.arraycopy(data, k, dat, 0, datLen);
            k += datLen;
            byte[] ecc = rsRemainder(dat, div);
            byte[] block = new byte[shortLen + 1];
            System.arraycopy(dat, 0, block, 0, datLen);
            // les blocs courts ont une case vide avant l'ECC
            System.arraycopy(ecc, 0, block, block.length - eccLen, eccLen);
            blocks[i] = block;
        }
        byte[] result = new byte[rawCodewords];
        int k = 0;
        for (int i = 0; i < blocks[0].length; i++) {
            for (int j = 0; j < blocks.length; j++) {
                if (i != shortLen - eccLen || j >= numShort) result[k++] = blocks[j][i];
            }
        }
        return result;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] r = new byte[degree];
        r[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < r.length; j++) {
                r[j] = (byte) mul(r[j] & 0xFF, root);
                if (j + 1 < r.length) r[j] ^= r[j + 1];
            }
            root = mul(root, 0x02);
        }
        return r;
    }

    private static byte[] rsRemainder(byte[] data, byte[] div) {
        byte[] r = new byte[div.length];
        for (byte b : data) {
            int factor = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= (byte) mul(div[i] & 0xFF, factor);
        }
        return r;
    }

    private static int mul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    // ------------------------------------------------------------------ motifs

    private void set(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private void drawFunctionPatterns(int ver) {
        for (int i = 0; i < size; i++) {
            set(6, i, i % 2 == 0);
            set(i, 6, i % 2 == 0);
        }
        finder(3, 3);
        finder(size - 4, 3);
        finder(3, size - 4);
        int[] pos = alignmentPositions(ver);
        int n = pos.length;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
            for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++)
                set(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
        }
        drawFormatBits(0); // réserve les zones de format
        if (ver >= 7) drawVersion(ver);
    }

    private void finder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
            int d = Math.max(Math.abs(dx), Math.abs(dy)), xx = x + dx, yy = y + dy;
            if (xx >= 0 && xx < size && yy >= 0 && yy < size) set(xx, yy, d != 2 && d != 4);
        }
    }

    private int[] alignmentPositions(int ver) {
        if (ver == 1) return new int[0];
        int num = ver / 7 + 2;
        int step = (ver * 4 + num * 2 + 1) / (num * 2 - 2) * 2;
        int[] r = new int[num];
        r[0] = 6;
        for (int i = num - 1, p = size - 7; i >= 1; i--, p -= step) r[i] = p;
        return r;
    }

    private void drawFormatBits(int mask) {
        int data = (1 << 3) | mask; // niveau L = 01
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6));
        set(8, 8, bit(bits, 7));
        set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true);
    }

    private void drawVersion(int ver) {
        int rem = ver;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = (ver << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3, c = i / 3;
            set(a, c, b);
            set(c, a, b);
        }
    }

    private static boolean bit(int x, int i) { return ((x >>> i) & 1) != 0; }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3] & 0xFF, 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask0() {
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
            if (!isFunction[y][x] && (x + y) % 2 == 0) modules[y][x] = !modules[y][x];
    }

    private static final class BitBuf {
        boolean[] bits = new boolean[4096];
        int len;
        void append(int val, int n) { for (int i = n - 1; i >= 0; i--) bits[len++] = ((val >>> i) & 1) != 0; }
        boolean get(int i) { return bits[i]; }
    }
}

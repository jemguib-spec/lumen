package com.lumen.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Identifie le vrai type d'un fichier par sa signature binaire (Java pur, testable).
 * Le nom ou l'extension fournis par l'expéditeur ne sont jamais pris en compte.
 */
final class MediaSniff {
    private MediaSniff() { }

    enum Type {
        JPEG(false, ".jpg"), PNG(false, ".png"), WEBP(false, ".webp"),
        MP4(true, ".mp4"), WEBM(true, ".webm"), UNKNOWN(false, "");

        final boolean video;
        final String ext;
        Type(boolean video, String ext) { this.video = video; this.ext = ext; }
        boolean image() { return this != UNKNOWN && !video; }
    }

    static Type sniff(byte[] h, int n) {
        if (n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return Type.JPEG;
        if (n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) return Type.PNG;
        if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return Type.WEBP;
        // MP4 / MOV : boîte « ftyp » en position 4, marque connue
        if (n >= 12 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            String brand = new String(h, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (brand.matches("(isom|iso2|iso4|iso5|iso6|mp41|mp42|avc1|M4V |M4VP|qt  |MSNV|dash|hvc1|heic)")
                    && !brand.equals("heic")) return Type.MP4;
        }
        // WebM / Matroska : en-tête EBML
        if (n >= 4 && (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45 && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3)
            return Type.WEBM;
        return Type.UNKNOWN;
    }

    static Type sniff(File f) {
        byte[] h = new byte[16];
        try (InputStream in = new FileInputStream(f)) {
            int n = 0, r;
            while (n < h.length && (r = in.read(h, n, h.length - n)) > 0) n += r;
            return sniff(h, n);
        } catch (IOException e) {
            return Type.UNKNOWN;
        }
    }
}

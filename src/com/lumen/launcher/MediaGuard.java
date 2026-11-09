// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Contrôle de tout fichier média avant qu'il ne soit gardé ou affiché.
 * - type réel vérifié par signature (MediaSniff), extension imposée par Lumen ;
 * - images : dimensions plafonnées (anti « bombe de décompression »), puis ré-encodées en
 *   JPEG neuf : métadonnées, profils et éventuelles données cachées disparaissent ;
 * - vidéos : conteneur MP4 ou WebM uniquement, taille plafonnée ; la lecture se fait dans
 *   le processus média isolé d'Android, jamais dans Lumen.
 * Rien n'est jamais exécuté : les fichiers restent dans le dossier privé de l'appli.
 */
final class MediaGuard {
    private MediaGuard() { }

    static final long MAX_IMAGE_BYTES = 60L << 20;
    static final long MAX_VIDEO_BYTES = 4L << 30;
    private static final long MAX_PIXELS = 120_000_000L; // ~ 12 000 × 10 000
    private static final int MAX_EDGE = 3840;

    /**
     * Vérifie le fichier téléchargé ou reçu et produit le fichier final dans dir.
     * Renvoie le fichier final (nom choisi par Lumen) ou null s'il est refusé.
     */
    static File accept(File raw, File dir, String baseName, boolean videoAllowed, boolean imageAllowed) {
        try {
            MediaSniff.Type t = MediaSniff.sniff(raw);
            if (t.video && videoAllowed && raw.length() <= MAX_VIDEO_BYTES) {
                File out = new File(dir, baseName + t.ext);
                return raw.renameTo(out) ? out : null;
            }
            if (t.image() && imageAllowed && raw.length() <= MAX_IMAGE_BYTES) {
                File out = new File(dir, baseName + ".jpg");
                return reencode(raw, out) ? out : null;
            }
            return null;
        } finally {
            if (raw.exists()) raw.delete();
        }
    }

    /** Décode (avec garde-fous) puis réécrit une image en JPEG propre. */
    static boolean reencode(File in, File out) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(in.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return false;
        if ((long) o.outWidth * o.outHeight > MAX_PIXELS) return false;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = null;
        File tmp = new File(out.getPath() + ".tmp");
        try {
            b = BitmapFactory.decodeFile(in.getPath(), d);
            if (b == null) return false;
            int edge = Math.max(b.getWidth(), b.getHeight());
            if (edge > MAX_EDGE) {
                float k = MAX_EDGE / (float) edge;
                Bitmap s = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true);
                if (s != b) b.recycle();
                b = s;
            }
            try (OutputStream os = new FileOutputStream(tmp)) {
                if (!b.compress(Bitmap.CompressFormat.JPEG, 92, os)) return false;
            }
            return tmp.renameTo(out);
        } catch (Throwable t) {
            return false;
        } finally {
            if (b != null) b.recycle();
            tmp.delete();
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Verre dépoli : chaque surface affiche la portion du fond flouté qui se trouve derrière elle,
 * plus un voile blanc léger et un filet clair. Donne l'effet « frosted glass » sans coût GPU.
 */
final class Glass {
    private static Bitmap backdrop;     // fond flouté, petite résolution
    private static int screenW = 1920, screenH = 1080;

    static void setScreen(int w, int h) { if (w > 0 && h > 0) { screenW = w; screenH = h; } }
    static Bitmap backdrop() { return backdrop; }
    /** Remet un fond de verre déjà préparé (retour du fond immersif au paysage). */
    static void setBackdrop(Bitmap b) { if (b != null && !b.isRecycled()) backdrop = b; }

    /** Floute une image (réduction + flou en boîte) et la mémorise comme fond du verre. */
    static void setSource(Bitmap src) {
        if (src == null || src.isRecycled()) return;
        try {
            int w = 192, h = 108;
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            if (small != src) small.recycle();
            for (int pass = 0; pass < 3; pass++) boxBlur(px, w, h, 3);
            // verre toujours sombre et doux : on compresse la luminosité (les photos très claires
            // donnaient des vignettes blanches illisibles)
            for (int i = 0; i < px.length; i++) {
                int c = px[i];
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                int lum = (r * 3 + g * 6 + b) / 10;
                float k = lum > 0 ? Math.min(1f, (18 + lum * 0.34f) / lum) : 1f;
                r = Math.min(255, (int) (r * k)); g = Math.min(255, (int) (g * k)); b = Math.min(255, (int) (b * k));
                px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            backdrop = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        } catch (Throwable ignored) { }
    }

    /** Version très floue d'une image (transition entre deux fonds). Peut renvoyer null. */
    static Bitmap soft(Bitmap src) { return soft(src, 160, 90); }

    /** Même adoucissement à une taille choisie (plus grande = moins flou une fois en plein écran). */
    static Bitmap soft(Bitmap src, int w, int h) {
        if (src == null || src.isRecycled()) return null;
        try {
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            if (small != src) small.recycle();
            for (int pass = 0; pass < 2; pass++) boxBlur(px, w, h, 3);
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        for (int dir = 0; dir < 2; dir++) {
            int[] in = dir == 0 ? px : tmp, out = dir == 0 ? tmp : px;
            int len = dir == 0 ? w : h, lines = dir == 0 ? h : w;
            for (int l = 0; l < lines; l++) {
                for (int i = 0; i < len; i++) {
                    int rs = 0, gs = 0, bs = 0, n = 0;
                    for (int k = -r; k <= r; k++) {
                        int j = Math.min(len - 1, Math.max(0, i + k));
                        int c = dir == 0 ? in[l * w + j] : in[j * w + l];
                        rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255; n++;
                    }
                    int v = 0xFF000000 | ((rs / n) << 16) | ((gs / n) << 8) | (bs / n);
                    if (dir == 0) out[l * w + i] = v; else out[i * w + l] = v;
                }
            }
        }
    }

    /** Fond de surface en verre pour une vue donnée. */
    static final class Surface extends Drawable {
        private final View host;
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] loc = new int[2];
        private final Rect src = new Rect();
        private final RectF r = new RectF();
        private float radius;
        private int veilColor;
        private float solidLevel;                // 0 = verre, 1 = surface pleine (sélection)
        private int rimColor = 0x29FFFFFF;
        private final android.graphics.Path clip = new android.graphics.Path();
        private float shapeW = -1, shapeH = -1, shapeR = -1;

        Surface(View host, float radius, int veilColor) {
            this.host = host;
            this.radius = radius;
            this.veilColor = veilColor;
            veil.setColor(veilColor);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1f, Tile.dp(host.getContext(), 0.75f)));
        }

        void setSolid(boolean s) { setSolidLevel(s ? 1f : 0f); }

        /** Fondu entre le verre (0) et la surface pleine de la sélection (1). */
        void setSolidLevel(float level) {
            level = Math.max(0f, Math.min(1f, level));
            if (level == solidLevel) return;
            solidLevel = level;
            invalidateSelf();
        }

        /** Teinte le verre avec une couleur (opaque) : voile coloré à ~55 %, filet plus lumineux. */
        void setGlassTint(int color) {
            veilColor = (color & 0x00FFFFFF) | 0x8C000000;
            rimColor = 0x40FFFFFF;
            shapeW = -1; // filet à refaire
            invalidateSelf();
        }
        void setRadius(float rad) { radius = rad; invalidateSelf(); }

        /**
         * Contour d'écrêtage et filet, refaits seulement quand la taille ou le rayon changent (rien
         * n'est alloué pendant un défilement). Le filet est éclairé d'en haut, comme une vraie plaque
         * de verre : arête supérieure lumineuse, flancs discrets, léger retour de lumière en bas.
         */
        private void shape(Rect b) {
            if (b.width() == shapeW && b.height() == shapeH && radius == shapeR) return;
            shapeW = b.width(); shapeH = b.height(); shapeR = radius;
            clip.rewind();
            r.set(b);
            clip.addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW);
            int a = rimColor >>> 24, rgb = rimColor & 0x00FFFFFF;
            rim.setShader(new android.graphics.LinearGradient(0, b.top, 0, b.bottom,
                    new int[]{(Math.min(255, a * 17 / 10) << 24) | rgb, ((a * 6 / 10) << 24) | rgb, ((a * 9 / 10) << 24) | rgb},
                    new float[]{0f, 0.55f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            r.set(b);
            if (solidLevel >= 1f) {
                veil.setColor(Palette.SELECTED);
                c.drawRoundRect(r, radius, radius, veil);
                return;
            }
            shape(b);
            r.set(b);
            Bitmap bd = backdrop;
            if (bd != null && !bd.isRecycled() && host.getWidth() > 0) {
                host.getLocationOnScreen(loc);
                float sx = host.getScaleX(), sy = host.getScaleY();
                float vw = host.getWidth() * sx, vh = host.getHeight() * sy;
                float kx = bd.getWidth() / (float) screenW, ky = bd.getHeight() / (float) screenH;
                // portion du fond derrière la vue, bornée à l'image (sinon bandes vides)
                float sl = loc[0] * kx, st = loc[1] * ky, sr = (loc[0] + vw) * kx, sb = (loc[1] + vh) * ky;
                float cl = Math.max(0, Math.min(sl, bd.getWidth() - 1)), ct = Math.max(0, Math.min(st, bd.getHeight() - 1));
                float cr = Math.max(cl + 1, Math.min(sr, bd.getWidth())), cb = Math.max(ct + 1, Math.min(sb, bd.getHeight()));
                src.set((int) cl, (int) ct, (int) Math.ceil(cr), (int) Math.ceil(cb));
                int save = c.save();
                c.clipPath(clip);
                c.drawBitmap(bd, src, b, bmp); // étire la portion valide sur toute la vue
                c.restoreToCount(save);
            }
            veil.setColor(veilColor);
            c.drawRoundRect(r, radius, radius, veil);
            float inset = rim.getStrokeWidth() / 2f;
            r.inset(inset, inset);
            c.drawRoundRect(r, radius, radius, rim);
            if (solidLevel > 0f) { // la sélection arrive en fondu par-dessus le verre
                r.set(b);
                veil.setColor(Palette.mix(Palette.SELECTED & 0x00FFFFFF, Palette.SELECTED, solidLevel));
                c.drawRoundRect(r, radius, radius, veil);
            }
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

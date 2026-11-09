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
    static Bitmap soft(Bitmap src) {
        if (src == null || src.isRecycled()) return null;
        try {
            int w = 160, h = 90;
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
        private boolean solid;
        private int solidColor = 0xFFF5F5F7;

        Surface(View host, float radius, int veilColor) {
            this.host = host;
            this.radius = radius;
            this.veilColor = veilColor;
            veil.setColor(veilColor);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1f, Tile.dp(host.getContext(), 0.75f)));
            rim.setColor(0x29FFFFFF);
        }

        void setSolid(boolean s) { solid = s; invalidateSelf(); }

        /** Teinte le verre avec une couleur (opaque) : voile coloré à ~55 %, filet plus lumineux. */
        void setGlassTint(int color) {
            veilColor = (color & 0x00FFFFFF) | 0x8C000000;
            rim.setColor(0x40FFFFFF);
            invalidateSelf();
        }
        void setRadius(float rad) { radius = rad; invalidateSelf(); }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            r.set(b);
            if (solid) {
                veil.setColor(solidColor);
                c.drawRoundRect(r, radius, radius, veil);
                return;
            }
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
                android.graphics.Path p = new android.graphics.Path();
                p.addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW);
                c.clipPath(p);
                c.drawBitmap(bd, src, b, bmp); // étire la portion valide sur toute la vue
                c.restoreToCount(save);
            }
            veil.setColor(veilColor);
            c.drawRoundRect(r, radius, radius, veil);
            float inset = rim.getStrokeWidth() / 2f;
            r.inset(inset, inset);
            c.drawRoundRect(r, radius, radius, rim);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

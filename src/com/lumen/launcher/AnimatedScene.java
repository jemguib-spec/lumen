// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.io.InputStream;
import java.util.Random;

/**
 * Paysage vivant : ciel fixe, crêtes qui défilent très lentement en parallaxe,
 * nappe de brume qui glisse, étoiles qui scintillent la nuit.
 */
final class AnimatedScene extends View {

    static final class Data {
        Bitmap sky;
        Bitmap[] layers = new Bitmap[5]; // l0..l3 + brume
        int[] tops = new int[5];
        boolean night;

        /** Chargement (à faire hors du thread principal). */
        static Data load(Context c, String dir) {
            Data d = new Data();
            try {
                d.sky = decode(c, dir + "/sky.jpg");
                String[] names = {"l0", "l1", "l2", "l3", "fog"};
                for (int i = 0; i < names.length; i++) {
                    d.layers[i] = decode(c, dir + "/" + names[i] + ".png");
                    d.tops[i] = Integer.parseInt(readText(c, dir + "/" + names[i] + ".txt").trim());
                }
                d.night = dir.endsWith("nuit");
                return d.sky != null ? d : null;
            } catch (Exception e) {
                return null;
            }
        }

        void recycle() {
            if (sky != null) sky.recycle();
            for (Bitmap b : layers) if (b != null) b.recycle();
        }

        private static Bitmap decode(Context c, String path) throws Exception {
            try (InputStream in = c.getAssets().open(path)) { return BitmapFactory.decodeStream(in); }
        }

        private static String readText(Context c, String path) throws Exception {
            try (InputStream in = c.getAssets().open(path)) {
                byte[] b = new byte[32];
                int n = in.read(b);
                return new String(b, 0, Math.max(0, n), "UTF-8");
            }
        }
    }

    // vitesses en pixels de l'image source par seconde (lointain → proche, puis brume)
    private static final float[] SPEED = {1.6f, 3.2f, 5.5f, 9f, 14f};
    private final Data d;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long start = SystemClock.uptimeMillis();
    private final float[] sx = new float[140], sy = new float[140], sp = new float[140], sph = new float[140];
    private boolean running = true;
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    AnimatedScene(Context c, Data d) {
        super(c);
        this.d = d;
        Random r = new Random(7);
        for (int i = 0; i < sx.length; i++) {
            sx[i] = r.nextFloat();
            sy[i] = (float) Math.pow(r.nextFloat(), 1.5) * 0.5f;
            sp[i] = 0.4f + r.nextFloat() * 1.4f;
            sph[i] = r.nextFloat() * 6.28f;
        }
        star.setColor(0xFFFFFFFF);
    }

    Bitmap sky() { return d.sky; }
    Data data() { return d; }

    void setRunning(boolean r) {
        running = r;
        if (r) postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || d.sky == null) return;
        float sw = d.sky.getWidth(), shh = d.sky.getHeight();
        float scale = Math.max(w / sw, h / shh);
        float ox = (w - sw * scale) / 2f, oy = (h - shh * scale) / 2f;
        float t = (SystemClock.uptimeMillis() - start) / 1000f;

        dst.set(ox, oy, ox + sw * scale, oy + shh * scale);
        c.drawBitmap(d.sky, null, dst, paint);

        if (d.night) {
            for (int i = 0; i < sx.length; i++) {
                float a = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * sp[i] + sph[i]));
                star.setAlpha((int) (a * (i % 7 == 0 ? 230 : 120)));
                c.drawCircle(ox + sx[i] * sw * scale, oy + sy[i] * shh * scale, (i % 7 == 0 ? 1.6f : 1f) * scale, star);
            }
        }

        for (int i = 0; i < d.layers.length; i++) {
            Bitmap b = d.layers[i];
            if (b == null) continue;
            float lw = b.getWidth() * scale, lh = b.getHeight() * scale;
            float off = (t * SPEED[i] * scale) % lw;
            float y = oy + d.tops[i] * scale;
            for (int k = 0; k < 2; k++) {
                float x = ox - off + k * lw;
                dst.set(x, y, x + lw + 1, y + lh);
                c.drawBitmap(b, null, dst, paint);
            }
        }
        if (running) postInvalidateOnAnimation();
    }
}

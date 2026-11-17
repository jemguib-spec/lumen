// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Image de profil (voir AvatarArt) tracée à la taille de la vue, d'une seule couleur : blanche sur
 * le disque de verre, foncée quand le disque sélectionné devient blanc. Les « trous » laissent voir
 * le disque (dessin dans un calque à part, puis effacé là où il faut).
 */
final class AvatarDrawable extends Drawable {
    private final int index;
    private int color;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF layer = new RectF();

    AvatarDrawable(int index, int color) {
        this.index = index;
        this.color = color;
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        for (Paint p : new Paint[]{paint, clear}) {
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
    }

    void setColor(int c) {
        if (c == color) return;
        color = c;
        invalidateSelf();
    }

    @Override public void draw(final Canvas canvas) {
        final Rect b = getBounds();
        if (b.isEmpty()) return;
        final float s = Math.min(b.width(), b.height()) / 100f;
        final float ox = b.left + (b.width() - 100 * s) / 2f, oy = b.top + (b.height() - 100 * s) / 2f;
        layer.set(b);
        int saved = canvas.saveLayer(layer, null);
        AvatarArt.draw(index, new AvatarArt.Sink() {
            Paint p;
            @Override public void begin(int tone, float stroke) {
                path.reset();
                p = tone == AvatarArt.HOLE ? clear : paint;
                if (tone != AvatarArt.HOLE) {
                    int alpha = tone == AvatarArt.SOFT ? Math.round(((color >>> 24) & 0xFF) * AvatarArt.SOFT_ALPHA) : (color >>> 24) & 0xFF;
                    paint.setColor((alpha << 24) | (color & 0xFFFFFF));
                }
                p.setStyle(stroke > 0 ? Paint.Style.STROKE : Paint.Style.FILL);
                p.setStrokeWidth(stroke * s);
            }
            @Override public void moveTo(float x, float y) { path.moveTo(ox + x * s, oy + y * s); }
            @Override public void lineTo(float x, float y) { path.lineTo(ox + x * s, oy + y * s); }
            @Override public void quadTo(float x1, float y1, float x, float y) { path.quadTo(ox + x1 * s, oy + y1 * s, ox + x * s, oy + y * s); }
            @Override public void cubicTo(float x1, float y1, float x2, float y2, float x, float y) {
                path.cubicTo(ox + x1 * s, oy + y1 * s, ox + x2 * s, oy + y2 * s, ox + x * s, oy + y * s);
            }
            @Override public void close() { path.close(); }
            @Override public void end() { canvas.drawPath(path, p); }
        });
        canvas.restoreToCount(saved);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

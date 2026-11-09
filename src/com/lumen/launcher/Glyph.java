package com.lumen.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Pictogrammes dessinés en vectoriel : grille (toutes les applis) et réglages. */
final class Glyph extends Drawable {
    static final int GRID = 0, SLIDERS = 1, INPUT = 2, MOON = 3;
    private final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Glyph(int type, int color) {
        this.type = type;
        p.setColor(color);
    }

    void setColor(int c) { p.setColor(c); invalidateSelf(); }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        if (type == GRID) {
            p.setStyle(Paint.Style.FILL);
            float cell = s * 0.17f, gap = s * 0.08f, r = cell * 0.32f;
            float start = -(cell * 1.5f + gap);
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
                float l = cx + start + j * (cell + gap), t = cy + start + i * (cell + gap);
                c.drawRoundRect(new RectF(l, t, l + cell, t + cell), r, r, p);
            }
        } else if (type == MOON) {
            // croissant de lune : « Ambiances »
            p.setStyle(Paint.Style.FILL);
            android.graphics.Path moon = new android.graphics.Path();
            moon.addCircle(cx, cy, s * 0.30f, android.graphics.Path.Direction.CW);
            android.graphics.Path cut = new android.graphics.Path();
            cut.addCircle(cx + s * 0.14f, cy - s * 0.10f, s * 0.26f, android.graphics.Path.Direction.CW);
            moon.op(cut, android.graphics.Path.Op.DIFFERENCE);
            c.drawPath(moon, p);
        } else if (type == INPUT) {
            // écran + flèche entrante : « Entrées »
            float stroke = s * 0.05f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            RectF scr = new RectF(cx - s * 0.30f, cy - s * 0.24f, cx + s * 0.34f, cy + s * 0.18f);
            c.drawRoundRect(scr, s * 0.05f, s * 0.05f, p);
            c.drawLine(cx - s * 0.10f, cy + s * 0.30f, cx + s * 0.14f, cy + s * 0.30f, p);
            c.drawLine(cx - s * 0.40f, cy - s * 0.03f, cx + s * 0.04f, cy - s * 0.03f, p);
            c.drawLine(cx + s * 0.04f, cy - s * 0.03f, cx - s * 0.06f, cy - s * 0.12f, p);
            c.drawLine(cx + s * 0.04f, cy - s * 0.03f, cx - s * 0.06f, cy + s * 0.06f, p);
        } else {
            float w = s * 0.56f, stroke = s * 0.045f;
            float[] knob = {0.30f, 0.70f, 0.45f};
            for (int i = 0; i < 3; i++) {
                float y = cy + (i - 1) * s * 0.19f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke);
                p.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(cx - w / 2, y, cx + w / 2, y, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - w / 2 + w * knob[i], y, s * 0.075f, p);
            }
        }
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

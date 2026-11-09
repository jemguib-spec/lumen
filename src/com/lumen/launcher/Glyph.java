// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Pictogrammes dessinés en vectoriel : boutons de l'accueil et rubriques des réglages. */
final class Glyph extends Drawable {
    static final int GRID = 0, SLIDERS = 1, INPUT = 2, MOON = 3, IMAGE = 4, HOME = 5, PHONE = 6, GEAR = 7, INFO = 8, DOC = 9, SPARK = 10,
            MOVE = 11, STAR = 12, STAR_OFF = 13, HIDE = 14, LOCK = 15, TRASH = 16, UNLOCK = 17, CLOSE = 18, RESTART = 19, SEARCH = 20, PERSON = 21;
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
        } else if (type >= IMAGE) {
            drawLine(c, type, s, cx, cy);
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

    /** Pictogrammes au trait (rubriques des réglages). */
    private void drawLine(Canvas c, int t, float s, float cx, float cy) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.075f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        android.graphics.Path q = new android.graphics.Path();
        switch (t) {
            case IMAGE: { // cadre avec montagnes et soleil : fond d'écran
                c.drawRoundRect(new RectF(cx - s * .38f, cy - s * .30f, cx + s * .38f, cy + s * .30f), s * .08f, s * .08f, p);
                q.moveTo(cx - s * .30f, cy + s * .20f); q.lineTo(cx - s * .08f, cy - s * .04f); q.lineTo(cx + s * .06f, cy + s * .10f);
                q.lineTo(cx + s * .16f, cy + s * .02f); q.lineTo(cx + s * .30f, cy + s * .20f);
                c.drawPath(q, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx + s * .17f, cy - s * .13f, s * .06f, p);
                break;
            }
            case HOME: { // maison : accueil
                q.moveTo(cx - s * .34f, cy - s * .02f); q.lineTo(cx, cy - s * .34f); q.lineTo(cx + s * .34f, cy - s * .02f);
                c.drawPath(q, p);
                c.drawRoundRect(new RectF(cx - s * .25f, cy - s * .10f, cx + s * .25f, cy + s * .32f), s * .05f, s * .05f, p);
                c.drawLine(cx, cy + s * .14f, cx, cy + s * .32f, p);
                break;
            }
            case PHONE: { // téléphone
                c.drawRoundRect(new RectF(cx - s * .20f, cy - s * .36f, cx + s * .20f, cy + s * .36f), s * .08f, s * .08f, p);
                c.drawLine(cx - s * .06f, cy + s * .25f, cx + s * .06f, cy + s * .25f, p);
                break;
            }
            case GEAR: { // roue dentée : système
                p.setStrokeWidth(s * .13f);
                p.setStrokeCap(Paint.Cap.BUTT);
                for (int i = 0; i < 8; i++) { // dents courtes et larges
                    double ang = i * Math.PI / 4;
                    c.drawLine(cx + (float) Math.cos(ang) * s * .25f, cy + (float) Math.sin(ang) * s * .25f,
                            cx + (float) Math.cos(ang) * s * .37f, cy + (float) Math.sin(ang) * s * .37f, p);
                }
                p.setStrokeWidth(s * 0.075f);
                p.setStrokeCap(Paint.Cap.ROUND);
                c.drawCircle(cx, cy, s * .24f, p);
                c.drawCircle(cx, cy, s * .08f, p);
                break;
            }
            case INFO: { // « i » dans un cercle : à propos
                c.drawCircle(cx, cy, s * .36f, p);
                c.drawLine(cx, cy - s * .02f, cx, cy + s * .18f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy - s * .16f, s * .045f, p);
                break;
            }
            case DOC: { // page avec lignes : licences
                q.moveTo(cx - s * .26f, cy - s * .36f); q.lineTo(cx + s * .12f, cy - s * .36f); q.lineTo(cx + s * .26f, cy - s * .22f);
                q.lineTo(cx + s * .26f, cy + s * .36f); q.lineTo(cx - s * .26f, cy + s * .36f); q.close();
                c.drawPath(q, p);
                for (int i = 0; i < 3; i++) c.drawLine(cx - s * .14f, cy - s * .08f + i * s * .14f, cx + s * .14f, cy - s * .08f + i * s * .14f, p);
                break;
            }
            case MOVE: { // flèches gauche-droite : déplacer
                c.drawLine(cx - s * .34f, cy, cx + s * .34f, cy, p);
                q.moveTo(cx - s * .20f, cy - s * .14f); q.lineTo(cx - s * .34f, cy); q.lineTo(cx - s * .20f, cy + s * .14f);
                q.moveTo(cx + s * .20f, cy - s * .14f); q.lineTo(cx + s * .34f, cy); q.lineTo(cx + s * .20f, cy + s * .14f);
                c.drawPath(q, p);
                break;
            }
            case STAR: case STAR_OFF: { // étoile : favori (barrée : retirer)
                for (int i = 0; i < 10; i++) {
                    double ang = -Math.PI / 2 + i * Math.PI / 5;
                    float r = i % 2 == 0 ? s * .38f : s * .16f;
                    float x = cx + (float) Math.cos(ang) * r, y = cy + s * .03f + (float) Math.sin(ang) * r;
                    if (i == 0) q.moveTo(x, y); else q.lineTo(x, y);
                }
                q.close();
                c.drawPath(q, p);
                if (t == STAR_OFF) c.drawLine(cx - s * .36f, cy - s * .32f, cx + s * .36f, cy + s * .36f, p);
                break;
            }
            case HIDE: { // œil barré : masquer
                q.moveTo(cx - s * .38f, cy); q.quadTo(cx, cy - s * .36f, cx + s * .38f, cy); q.quadTo(cx, cy + s * .36f, cx - s * .38f, cy);
                c.drawPath(q, p);
                c.drawCircle(cx, cy, s * .09f, p);
                c.drawLine(cx - s * .30f, cy - s * .30f, cx + s * .30f, cy + s * .30f, p);
                break;
            }
            case LOCK: case UNLOCK: { // cadenas (ouvert : déverrouiller)
                c.drawRoundRect(new RectF(cx - s * .26f, cy - s * .04f, cx + s * .26f, cy + s * .34f), s * .06f, s * .06f, p);
                float lift = t == UNLOCK ? s * .10f : 0;
                q.moveTo(cx - s * .16f, cy - s * .04f - lift); q.lineTo(cx - s * .16f, cy - s * .16f - lift);
                q.quadTo(cx - s * .16f, cy - s * .36f - lift, cx, cy - s * .36f - lift);
                q.quadTo(cx + s * .16f, cy - s * .36f - lift, cx + s * .16f, cy - s * .16f - lift);
                if (t == LOCK) q.lineTo(cx + s * .16f, cy - s * .04f);
                c.drawPath(q, p);
                break;
            }
            case PERSON: { // tête et épaules : profils
                c.drawCircle(cx, cy - s * .14f, s * .15f, p);
                c.drawArc(new RectF(cx - s * .30f, cy + s * .08f, cx + s * .30f, cy + s * .62f), 200, 140, false, p);
                break;
            }
            case SEARCH: { // loupe : rechercher
                c.drawCircle(cx - s * .06f, cy - s * .06f, s * .24f, p);
                c.drawLine(cx + s * .12f, cy + s * .12f, cx + s * .32f, cy + s * .32f, p);
                break;
            }
            case CLOSE: { // croix dans un cercle : fermer l'appli
                c.drawCircle(cx, cy, s * .36f, p);
                c.drawLine(cx - s * .13f, cy - s * .13f, cx + s * .13f, cy + s * .13f, p);
                c.drawLine(cx + s * .13f, cy - s * .13f, cx - s * .13f, cy + s * .13f, p);
                break;
            }
            case RESTART: { // flèche circulaire : redémarrer
                c.drawArc(new RectF(cx - s * .32f, cy - s * .32f, cx + s * .32f, cy + s * .32f), -60, 300, false, p);
                q.moveTo(cx + s * .06f, cy - s * .40f); q.lineTo(cx + s * .19f, cy - s * .28f); q.lineTo(cx + s * .03f, cy - s * .20f);
                c.drawPath(q, p);
                break;
            }
            case TRASH: { // corbeille : désinstaller
                c.drawLine(cx - s * .32f, cy - s * .22f, cx + s * .32f, cy - s * .22f, p);
                c.drawLine(cx - s * .10f, cy - s * .32f, cx + s * .10f, cy - s * .32f, p);
                q.moveTo(cx - s * .24f, cy - s * .22f); q.lineTo(cx - s * .19f, cy + s * .34f); q.lineTo(cx + s * .19f, cy + s * .34f); q.lineTo(cx + s * .24f, cy - s * .22f);
                c.drawPath(q, p);
                c.drawLine(cx - s * .06f, cy - s * .08f, cx - s * .06f, cy + s * .20f, p);
                c.drawLine(cx + s * .06f, cy - s * .08f, cx + s * .06f, cy + s * .20f, p);
                break;
            }
            default: { // étincelle : ambiances
                q.moveTo(cx, cy - s * .38f); q.quadTo(cx + s * .04f, cy - s * .04f, cx + s * .38f, cy);
                q.quadTo(cx + s * .04f, cy + s * .04f, cx, cy + s * .38f);
                q.quadTo(cx - s * .04f, cy + s * .04f, cx - s * .38f, cy);
                q.quadTo(cx - s * .04f, cy - s * .04f, cx, cy - s * .38f);
                p.setStyle(Paint.Style.FILL);
                c.drawPath(q, p);
            }
        }
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

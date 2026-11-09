package com.lumen.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Lueur colorée qui monte doucement du bas de l'écran derrière la vignette sélectionnée,
 * dans la couleur de l'appli, et la suit quand on se déplace.
 */
final class Glow extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cx = -1, level;
    private int color = 0xFF000000;
    private ValueAnimator anim;

    Glow(Context c) { super(c); }

    /** Lueur centrée sur x (pixels écran), dans la couleur donnée. */
    void focus(float x, int newColor) {
        final float fromX = cx < 0 ? x : cx, fromLevel = level;
        final int fromColor = color;
        final int toColor = brighten(newColor);
        final float toX = x;
        start(new ValueAnimator.AnimatorUpdateListener() {
            final ArgbEvaluator ev = new ArgbEvaluator();
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float f = (float) a.getAnimatedValue();
                cx = fromX + (toX - fromX) * f;
                level = fromLevel + (1f - fromLevel) * f;
                color = (int) ev.evaluate(f, fromColor, toColor);
                invalidate();
            }
        });
    }

    void fade() {
        final float from = level;
        start(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                level = from * (1f - (float) a.getAnimatedValue());
                invalidate();
            }
        });
    }

    private void start(ValueAnimator.AnimatorUpdateListener l) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(700);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(l);
        anim.start();
    }

    private static int brighten(int c) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(c, hsv);
        hsv[2] = Math.max(hsv[2], 0.8f);
        return android.graphics.Color.HSVToColor(hsv);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || level <= 0.01f || cx < 0) return;
        float r = w * 0.42f;
        float cy = h * 1.02f;
        int a = (int) (0x70 * level);
        int core = (a << 24) | (color & 0xFFFFFF);
        int mid = ((a / 3) << 24) | (color & 0xFFFFFF);
        p.setShader(new RadialGradient(cx, cy, r, new int[]{core, mid, 0x00000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.save();
        c.scale(1f, 0.62f, cx, cy); // ellipse aplatie, plus naturelle
        c.drawCircle(cx, cy, r, p);
        c.restore();
    }
}

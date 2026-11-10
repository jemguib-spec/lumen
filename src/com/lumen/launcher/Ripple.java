// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;

/** Onde lumineuse qui part d'un bouton quand son action est lancée (scène Home Assistant) : on voit que c'est parti. */
final class Ripple {
    private Ripple() { }

    static void play(final MainActivity a, View from) {
        if (from == null || !from.isAttachedToWindow() || a.root.getWidth() == 0) return;
        int[] l = new int[2], r = new int[2];
        from.getLocationOnScreen(l);
        a.root.getLocationOnScreen(r);
        float cx = l[0] - r[0] + from.getWidth() * from.getScaleX() / 2f, cy = l[1] - r[1] + from.getHeight() * from.getScaleY() / 2f;
        int size = a.u(140);
        final View ring = new View(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Palette.SELECTED & 0x1FFFFFFF);
        g.setStroke(Math.max(2, a.u(3)), Palette.SELECTED & 0xB3FFFFFF);
        ring.setBackground(g);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.leftMargin = Math.round(cx - size / 2f);
        lp.topMargin = Math.round(cy - size / 2f);
        ring.setScaleX(0.3f);
        ring.setScaleY(0.3f);
        ring.setElevation(a.u(50));
        a.root.addView(ring, lp);
        ring.animate().scaleX(5f).scaleY(5f).alpha(0f).setDuration(Motion.SLOW).setInterpolator(Motion.EASE).withEndAction(new Runnable() {
            @Override public void run() { a.root.removeView(ring); }
        }).start();
    }
}

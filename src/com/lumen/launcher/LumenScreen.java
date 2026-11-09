// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Écran plein « lumen » affiché pendant une mise à jour ou un redémarrage. */
final class LumenScreen {
    private LumenScreen() { }

    /** Écran noir « lumen » avec une ligne dessous et une fine barre animée (mise à jour, redémarrage). */
    static FrameLayout show(MainActivity a, String line) {
        FrameLayout z = new FrameLayout(a);
        z.setBackgroundColor(Color.BLACK);
        z.setClickable(true);
        z.setElevation(a.u(80));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView word = a.text(84, "sans-serif-thin", 0xFFFFFFFF);
        word.setText("lumen");
        word.setLetterSpacing(0.35f);
        col.addView(word, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sub = a.text(24, "sans-serif-light", 0x99FFFFFF);
        sub.setText(line.toUpperCase(java.util.Locale.FRANCE));
        sub.setLetterSpacing(0.3f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = a.u(18);
        col.addView(sub, sl);
        // fine barre qui va et vient : quelque chose se passe
        FrameLayout track = new FrameLayout(a);
        track.setBackgroundColor(0x1FFFFFFF);
        final View bar = new View(a);
        bar.setBackgroundColor(0xB3FFFFFF);
        track.addView(bar, new FrameLayout.LayoutParams(a.u(90), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(a.u(360), Math.max(2, a.u(3)));
        tl.topMargin = a.u(46);
        col.addView(track, tl);
        android.animation.ObjectAnimator slide = android.animation.ObjectAnimator.ofFloat(bar, "translationX", 0, a.u(270));
        slide.setDuration(1100);
        slide.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        slide.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        slide.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        slide.start();
        z.addView(col, MainActivity.wrap(Gravity.CENTER));
        z.setAlpha(0f);
        a.root.addView(z, MainActivity.match());
        z.animate().alpha(1f).setStartDelay(0).setDuration(400).start();
        return z;
    }
}

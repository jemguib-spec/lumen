// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Petit indicateur en bas à droite tant que Lumen télécharge photos ou vidéos (mis à jour chaque seconde). */
final class DownloadPill {
    private final MainActivity a;

    DownloadPill(MainActivity a) { this.a = a; }

    /** (Re)lance la mise à jour de l'indicateur, un peu après l'arrivée de Lumen à l'écran. */
    void start() {
        a.h.removeCallbacks(dlTick);
        a.h.postDelayed(dlTick, 1500);
    }

    private TextView dlPill;

    private final Runnable dlTick = new Runnable() {
        @Override public void run() {
            String s = a.online == null ? "" : a.online.activity();
            if (dlPill == null) {
                dlPill = a.text(18, "sans-serif-medium", Palette.TEXT);
                dlPill.setSingleLine(true);
                dlPill.setPadding(a.u(22), a.u(10), a.u(22), a.u(10));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(a.u(30));
                bg.setColor(0x80101218);
                bg.setStroke(Math.max(1, a.u(1.5f)), 0x26FFFFFF);
                dlPill.setBackground(bg);
                dlPill.setAlpha(0f);
                FrameLayout.LayoutParams lp = MainActivity.wrap(Gravity.BOTTOM | Gravity.END);
                lp.rightMargin = a.u(48);
                lp.bottomMargin = a.u(28);
                a.root.addView(dlPill, a.root.indexOfChild(a.notice), lp);
            }
            boolean show = !s.isEmpty() && !a.ambientOn;
            if (show) dlPill.setText("↓  " + s);
            float want = show ? 1f : 0f;
            if (dlPill.getAlpha() != want && dlPill.getTag() != (Object) Boolean.valueOf(show)) {
                dlPill.setTag(show);
                dlPill.animate().alpha(want).setStartDelay(0).setDuration(Motion.MEDIUM).start();
            }
            if (a.resumedNow) a.h.postDelayed(this, 1000);
        }
    };
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Vignette cochable des grilles (fonds de la galerie, choix des vidéos) : aperçu aux coins
 * arrondis, pastille de coche, titre dessous, agrandissement et anneau à la sélection.
 * Usage : new CheckCell(...), ajouter ses calques dans {@link #card}, puis {@link #finish}.
 */
final class CheckCell {
    final FrameLayout cell, card;
    final ImageView img;
    final TextView check;
    final GradientDrawable checkBg = new GradientDrawable();
    final float radius;
    private final MainActivity a;
    private final int tw, th;

    CheckCell(final MainActivity a, int tw, int th) {
        this.a = a;
        this.tw = tw;
        this.th = th;
        cell = new FrameLayout(a);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setSoundEffectsEnabled(false);
        cell.setClipChildren(false);
        card = new FrameLayout(a);
        radius = a.u(18);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        card.setClipToOutline(true);
        card.setBackgroundColor(0xFF15161B);
        img = new ImageView(a);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(img, MainActivity.match());
        check = a.text(28, "sans-serif-medium", Palette.ON_SELECTED);
        check.setGravity(Gravity.CENTER);
        checkBg.setShape(GradientDrawable.OVAL);
        checkBg.setColor(Palette.SELECTED);
        check.setBackground(checkBg);
    }

    /** Pose la pastille (au-dessus des calques ajoutés), le titre et les réactions ; renvoie la vignette. */
    View finish(String title, final View.OnClickListener onClick) {
        FrameLayout.LayoutParams ckl = new FrameLayout.LayoutParams(a.u(48), a.u(48), Gravity.TOP | Gravity.END);
        ckl.topMargin = ckl.rightMargin = a.u(14);
        card.addView(check, ckl);
        cell.addView(card, new FrameLayout.LayoutParams(tw, th));
        TextView name = a.text(22, "sans-serif", 0xB3FFFFFF);
        name.setText(title);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(tw, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        nl.topMargin = th + a.u(12);
        cell.addView(name, nl);
        cell.setLayoutParams(new LinearLayout.LayoutParams(tw, th + a.u(48)));
        cell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick();
                cell.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(Motion.FAST)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
                card.setElevation(f ? a.u(30) : 0);
                card.setForeground(f ? ring(a, radius) : null);
            }
        });
        cell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Sounds.get(a).tock();
                onClick.onClick(v);
            }
        });
        return cell;
    }

    /** Anneau blanc de la vignette sélectionnée. */
    static GradientDrawable ring(MainActivity a, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radius);
        g.setStroke(a.u(4), Palette.SELECTED);
        return g;
    }
}

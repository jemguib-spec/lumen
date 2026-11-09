// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * « Reprendre » au-dessus de l'appli sélectionnée, façon Apple TV : quand une vignette de l'accueil
 * est sélectionnée, ses films et épisodes en cours (publiés par l'appli) apparaissent juste au-dessus.
 * ▲ pour y aller, OK pour reprendre la lecture.
 */
final class TopShelf {
    private final MainActivity a;
    final LinearLayout view;
    private final LinearLayout row;
    private final HorizontalScrollView scroll;
    private final TextView label;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newFixedThreadPool(2);
    private volatile Map<String, List<WatchNext.Entry>> data = new HashMap<>();
    private AppRepo.App wanted, shown;
    private final Runnable apply = new Runnable() { @Override public void run() { show(wanted); } };

    TopShelf(MainActivity a) {
        this.a = a;
        view = new LinearLayout(a);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setClipChildren(false);
        view.setClipToPadding(false);
        view.setVisibility(View.GONE);
        label = a.text(22, "sans-serif-medium", 0xB3FFFFFF);
        label.setShadowLayer(a.u(8), 0, 0, 0x66000000);
        label.setLetterSpacing(0.06f);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        view.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(a.u(112), a.u(22), a.u(112), a.u(14));
        scroll = new HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        view.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Relit les « en cours » (au retour sur l'accueil : on vient peut-être de regarder un épisode). */
    void reload() {
        if (!a.prefs.topShelf() || !WatchNext.granted(a)) { data = new HashMap<>(); a.h.post(apply); return; }
        io.execute(new Runnable() {
            @Override public void run() {
                data = WatchNext.load(a);
                a.h.post(new Runnable() { @Override public void run() { shown = null; show(wanted); a.refreshGreeting(); } });
            }
        });
    }

    /** Programmes en cours dont le titre (ou l'épisode) correspond à la recherche. */
    List<WatchNext.Entry> search(String q, int max) {
        List<WatchNext.Entry> out = new java.util.ArrayList<>();
        for (List<WatchNext.Entry> l : data.values())
            for (WatchNext.Entry e : l) {
                if (out.size() >= max) return out;
                if (AppCategory.matches(e.title, q) || (e.subtitle != null && AppCategory.matches(e.subtitle, q))) out.add(e);
            }
        return out;
    }

    /** Le programme en cours regardé le plus récemment, toutes applis confondues (ou null). */
    WatchNext.Entry latest() {
        WatchNext.Entry best = null;
        for (List<WatchNext.Entry> l : data.values())
            for (WatchNext.Entry e : l) if (best == null || e.engaged > best.engaged) best = e;
        return best;
    }

    /** Une vignette de l'accueil vient d'être sélectionnée (null = aucune appli). */
    void focusApp(AppRepo.App app) {
        wanted = app;
        a.h.removeCallbacks(apply);
        a.h.postDelayed(apply, 260); // évite le clignotement quand on fait défiler les vignettes
    }

    boolean hasFocusInside() { View f = a.getCurrentFocus(); return f != null && MainActivity.isInside(f, view); }

    private void show(final AppRepo.App app) {
        if (hasFocusInside()) return; // ne jamais retirer la rangée sous la sélection
        List<WatchNext.Entry> list = app == null || !a.prefs.topShelf() ? null : data.get(app.pkg);
        if (list == null || list.isEmpty()) { hide(); return; }
        if (app == shown && view.getVisibility() == View.VISIBLE) return;
        shown = app;
        label.setText("Reprendre · " + app.label);
        row.removeAllViews();
        int h = a.u(212);
        for (int i = 0; i < list.size(); i++) {
            View c = card(app, list.get(i), h);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < list.size() - 1) lp.rightMargin = a.u(28);
            row.addView(c, lp);
        }
        scroll.scrollTo(0, 0);
        if (view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.setTranslationY(a.u(16));
        }
        view.animate().alpha(1f).translationY(0).setDuration(240).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    void hide() {
        if (view.getVisibility() != View.VISIBLE || hasFocusInside()) return;
        shown = null;
        view.animate().alpha(0f).setDuration(160).withEndAction(new Runnable() {
            @Override public void run() { if (shown == null) view.setVisibility(View.GONE); }
        }).start();
    }

    private View card(final AppRepo.App app, final WatchNext.Entry e, int h) {
        final int w = e.portrait ? h * 2 / 3 : h * 16 / 9;
        final FrameLayout cell = new FrameLayout(a);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setSoundEffectsEnabled(false);
        cell.setClipChildren(false);
        final FrameLayout card = new FrameLayout(a);
        final float radius = a.u(16);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        card.setClipToOutline(true);
        card.setBackgroundColor(0xFF000000 | (app.color(a.getPackageManager()) & 0x00FFFFFF));
        final ImageView img = new ImageView(a);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(img, MainActivity.match());
        // dégradé bas pour la lisibilité du titre
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xE6000000, 0x00000000});
        View sh = new View(a);
        sh.setBackground(shade);
        card.addView(sh, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h * 2 / 3, Gravity.BOTTOM));
        LinearLayout txt = new LinearLayout(a);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(a.u(16), 0, a.u(16), a.u(e.progress >= 0 ? 22 : 14));
        TextView t = a.text(22, "sans-serif-medium", 0xFFFFFFFF);
        t.setText(e.title);
        t.setMaxLines(e.portrait ? 2 : 1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(t);
        String sub = !e.subtitle.isEmpty() ? e.subtitle : e.remaining;
        if (!sub.isEmpty() && !e.portrait) {
            TextView s = a.text(18, "sans-serif", 0xB3FFFFFF);
            s.setText(sub);
            s.setMaxLines(1);
            s.setEllipsize(TextUtils.TruncateAt.END);
            txt.addView(s);
        }
        card.addView(txt, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        if (e.progress >= 0) { // barre de progression, comme sur Apple TV
            FrameLayout bar = new FrameLayout(a);
            GradientDrawable track = new GradientDrawable();
            track.setColor(0x4DFFFFFF);
            track.setCornerRadius(a.u(3));
            bar.setBackground(track);
            View fill = new View(a);
            GradientDrawable fd = new GradientDrawable();
            fd.setColor(0xFFF5F5F7);
            fd.setCornerRadius(a.u(3));
            fill.setBackground(fd);
            int bw = w - a.u(32);
            bar.addView(fill, new FrameLayout.LayoutParams(Math.round(bw * e.progress), a.u(5)));
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(bw, a.u(5), Gravity.BOTTOM | Gravity.START);
            bl.leftMargin = a.u(16);
            bl.bottomMargin = a.u(12);
            card.addView(bar, bl);
        }
        cell.addView(card, new FrameLayout.LayoutParams(w, h));
        cell.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        cell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick();
                cell.animate().scaleX(f ? 1.07f : 1f).scaleY(f ? 1.07f : 1f).setDuration(220)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
                card.setElevation(f ? a.u(30) : 0);
                card.setForeground(f ? CheckCell.ring(a, radius) : null);
            }
        });
        cell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Sounds.get(a).tock();
                a.whenUnlocked(app, new Runnable() {
                    @Override public void run() {
                        a.prefs.addRecent(app.key);
                        Intent i = WatchNext.intentFor(e);
                        try {
                            if (i == null) throw new IllegalStateException();
                            a.startActivity(i);
                        } catch (Exception ex) {
                            a.launch(app); // l'appli s'ouvre, à défaut de reprendre directement
                        }
                    }
                });
            }
        });
        if (a.prefs.shelfPosters() || (e.poster != null && e.poster.startsWith("content://"))) {
            images.execute(new Runnable() {
                @Override public void run() {
                    final Bitmap b = WatchNext.poster(a, e.poster, a.prefs.shelfPosters());
                    if (b != null) a.h.post(new Runnable() { @Override public void run() { img.setImageBitmap(b); } });
                }
            });
        }
        return cell;
    }
}

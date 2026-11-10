// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Choix des vidéos avec aperçus : chaque vidéo proposée (catalogue Commons, et Pexels si une clé est
 * réglée) s'affiche avec une image, sa durée et sa définition. OK coche ou décoche.
 * Seules les vidéos cochées sont téléchargées ; décocher une vidéo l'efface de la TV.
 */
final class VideoPicker {
    private VideoPicker() { }

    private static java.util.concurrent.ExecutorService pool;
    private static synchronized java.util.concurrent.ExecutorService pool() {
        if (pool == null) pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        return pool;
    }

    /** scope : Online.SCOPE_FONDS ou Online.SCOPE_COSY. onClose est appelé après la fermeture. */
    static void open(final MainActivity a, final String scope, final Runnable onClose) {
        final boolean cosy = Online.SCOPE_COSY.equals(scope);
        final boolean[] changed = {false};
        FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() {
                if (changed[0]) {
                    if (cosy) a.online.refreshCosy();
                    else { a.online.refreshIfNeeded(Online.VIDEO, true); a.wall.restart(); }
                }
                if (onClose != null) onClose.run();
            }
        });
        ImageView bg = new ImageView(a);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (Glass.backdrop() != null) bg.setImageBitmap(Glass.backdrop());
        o.addView(bg, MainActivity.match());
        View tint = new View(a);
        tint.setBackgroundColor(0xB805060A);
        o.addView(tint, MainActivity.match());

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(80));
        final LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(a.u(112), a.u(72), a.u(112), a.u(140));
        sv.addView(col);
        o.addView(sv, MainActivity.match());

        TextView title = a.text(52, "sans-serif-light", Palette.TEXT);
        title.setText(cosy ? "Vidéos des ambiances" : "Fonds vidéo");
        col.addView(title);
        final TextView state = a.text(24, "sans-serif", Palette.TEXT_2);
        state.setPadding(0, a.u(12), 0, a.u(30));
        state.setText("Chargement des aperçus…");
        stateView = state;
        lastWhy = "";
        col.addView(state);

        final LinearLayout back = a.listRow("Terminé", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(true); }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(a.u(400), a.u(88));
        bl.bottomMargin = a.u(20);
        col.addView(back, bl);
        back.requestFocus();

        pool().execute(new Runnable() {
            @Override public void run() {
                List<Online.Pick> picks;
                String err = null;
                try { picks = a.online.picks(scope); }
                catch (Exception e) { picks = new ArrayList<>(); err = e.getMessage(); }
                final List<Online.Pick> list = picks;
                final String error = err;
                a.h.post(new Runnable() {
                    @Override public void run() {
                        if (col.getParent() == null || !col.isAttachedToWindow()) return;
                        if (list.isEmpty()) {
                            state.setText(error == null ? "Aucune vidéo proposée pour l'instant." : "Connexion impossible · réessaie plus tard");
                            return;
                        }
                        fill(a, col, state, list, cosy, changed);
                    }
                });
            }
        });
    }

    private static void fill(final MainActivity a, LinearLayout col, final TextView state, List<Online.Pick> picks,
                             boolean cosy, final boolean[] changed) {
        // regroupe par ville, thème ou ambiance, dans l'ordre d'arrivée
        Map<String, List<Online.Pick>> groups = new LinkedHashMap<>();
        for (Online.Pick p : picks) {
            List<Online.Pick> l = groups.get(p.group);
            if (l == null) { l = new ArrayList<>(); groups.put(p.group, l); }
            l.add(p);
        }
        if (cosy) { // ordre des ambiances, catalogue puis Pexels mélangés par ambiance
            Map<String, List<Online.Pick>> ordered = new LinkedHashMap<>();
            for (int k = 0; k < Online.COSY_NAMES.length; k++) {
                List<Online.Pick> l = groups.get(Online.cosyGroup(k));
                if (l != null) ordered.put(Online.cosyGroup(k), l);
            }
            groups = ordered;
        }
        final Runnable count = new Runnable() {
            @Override public void run() {
                int n = 0;
                for (Online.Pick p : a.online.lastPicks()) if (a.prefs.videoOk(p.key)) n++;
                state.setText((n == 0 ? "Coche les vidéos à garder : seules celles-ci seront téléchargées."
                        : n + (n > 1 ? " vidéos cochées" : " vidéo cochée") + " · décocher efface la vidéo de la TV")
                        + (lastWhy.isEmpty() ? "" : "\nAperçus indisponibles : " + lastWhy));
            }
        };
        a.online.rememberPicks(picks);
        count.run();

        int screenW = a.root.getWidth() > 0 ? a.root.getWidth() : a.u(1920);
        final int cols = 4, gap = a.u(36), tw = (screenW - a.u(224) - gap * (cols - 1)) / cols, th = tw * 9 / 16;
        for (Map.Entry<String, List<Online.Pick>> g : groups.entrySet()) {
            String name = g.getKey();
            if (name.startsWith("cosy:")) {
                try { name = Online.COSY_NAMES[Integer.parseInt(name.substring(5))]; } catch (Exception ignored) { }
            }
            TextView h = a.text(28, "sans-serif-medium", 0xE6FFFFFF);
            h.setText(name);
            h.setPadding(0, a.u(36), 0, a.u(4));
            col.addView(h);
            LinearLayout line = null;
            List<Online.Pick> l = g.getValue();
            for (int i = 0; i < l.size(); i++) {
                if (i % cols == 0) {
                    line = new LinearLayout(a);
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    line.setClipChildren(false);
                    line.setClipToPadding(false);
                    line.setPadding(0, a.u(18), 0, a.u(18));
                    col.addView(line);
                }
                View cell = cell(a, l.get(i), tw, th, changed, count);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i % cols != cols - 1) cl.rightMargin = gap;
                line.addView(cell, cl);
            }
        }
    }

    private static View cell(final MainActivity a, final Online.Pick p, int tw, int th, final boolean[] changed, final Runnable count) {
        final CheckCell c = new CheckCell(a, tw, th);
        final FrameLayout card = c.card;
        final ImageView img = c.img;
        // en attendant l'aperçu (ou s'il est indisponible) : un pictogramme plutôt qu'une case vide
        final ImageView ph = new ImageView(a);
        ph.setImageDrawable(new Glyph(Glyph.IMAGE, 0x59FFFFFF));
        card.addView(ph, new FrameLayout.LayoutParams(a.u(64), a.u(64), Gravity.CENTER));
        // dégradé léger en bas pour lire la durée, sans assombrir l'aperçu
        View shade = new View(a);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x99000000, 0x00000000}));
        card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, th / 2, Gravity.BOTTOM));
        // durée · définition · source, en bas de l'aperçu
        TextView info = a.text(18, "sans-serif-medium", Palette.TEXT);
        info.setText(describe(p));
        info.setShadowLayer(a.u(6), 0, 0, 0xCC000000);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        il.leftMargin = a.u(14);
        il.bottomMargin = a.u(10);
        card.addView(info, il);
        final Runnable paint = new Runnable() {
            @Override public void run() {
                // cochée : pastille blanche avec ✓ ; pas cochée : simple cercle, l'aperçu reste bien visible
                boolean on = a.prefs.videoOk(p.key);
                c.check.setText(on ? "✓" : "");
                c.checkBg.setColor(on ? Palette.SELECTED : 0x33000000);
                c.checkBg.setStroke(Math.max(1, a.u(3)), on ? Palette.SELECTED : 0xE6FFFFFF);
            }
        };
        paint.run();
        final View cell = c.finish(p.title, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.prefs.setVideoOk(p.key, !a.prefs.videoOk(p.key));
                changed[0] = true;
                paint.run();
                count.run();
            }
        });
        final int[] tries = {0};
        pool().execute(new Runnable() {
            @Override public void run() {
                final Runnable self = this;
                File f = a.online.thumbOf(p);
                if (f == null) {
                    final String why = a.online.thumbError;
                    a.h.post(new Runnable() { @Override public void run() {
                        // pas de panique : on redemande un peu plus tard tant que l'écran est ouvert
                        if (++tries[0] <= 3 && cell.isAttachedToWindow()) {
                            a.h.postDelayed(new Runnable() { @Override public void run() {
                                if (cell.isAttachedToWindow()) pool().execute(self);
                            } }, 4000L * tries[0]);
                        } else failed(a, why);
                    } });
                    return;
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 1;
                final Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (b == null) return;
                final float lum = luminance(b);
                a.h.post(new Runnable() {
                    @Override public void run() {
                        img.setImageBitmap(b);
                        // image de nuit ou fondu au noir : on l'éclaircit pour qu'on voie de quoi il s'agit
                        if (lum < 0.14f) {
                            float k = Math.min(3.5f, 0.22f / Math.max(0.02f, lum));
                            android.graphics.ColorMatrix m = new android.graphics.ColorMatrix(new float[]{
                                    k, 0, 0, 0, 8, 0, k, 0, 0, 8, 0, 0, k, 0, 8, 0, 0, 0, 1, 0});
                            img.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));
                        }
                        ph.setVisibility(lum < 0.01f ? View.VISIBLE : View.GONE); // image vraiment noire : on garde le pictogramme
                    }
                });
            }
        });
        return cell;
    }

    private static TextView stateView;
    private static String lastWhy = "";

    /** Un aperçu n'a pas pu être chargé : la raison s'affiche sous le titre (une capture suffit pour comprendre). */
    private static void failed(MainActivity a, String why) {
        if (stateView == null || !stateView.isAttachedToWindow()) return;
        String t = stateView.getText().toString();
        lastWhy = why == null ? "" : why;
        if (!t.contains("Aperçus indisponibles")) stateView.setText(t + "\nAperçus indisponibles : " + lastWhy);
    }

    /** Luminosité moyenne d'une image (0 = noir, 1 = blanc), sur un échantillon de points. */
    static float luminance(Bitmap b) {
        long sum = 0;
        int n = 0, w = b.getWidth(), h = b.getHeight();
        for (int y = h / 16; y < h; y += Math.max(1, h / 16))
            for (int x = w / 16; x < w; x += Math.max(1, w / 16)) {
                int c = b.getPixel(x, y);
                sum += (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10;
                n++;
            }
        return n == 0 ? 0 : sum / (n * 255f);
    }

    /** « 4 min 24 · 4K · Pexels » */
    static String describe(Online.Pick p) {
        StringBuilder b = new StringBuilder();
        int s = (int) Math.round(p.duration);
        if (s > 0) b.append(s >= 60 ? (s / 60) + " min" + (s % 60 > 0 ? " " + (s % 60 < 10 ? "0" : "") + (s % 60) : "") : s + " s");
        String def = p.height >= 2000 ? "4K" : p.height >= 1400 ? "1440p" : p.height >= 1000 ? "HD" : p.height > 0 ? p.height + "p" : "";
        if (!def.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(def);
        if (p.pexels) b.append(b.length() > 0 ? " · " : "").append("Pexels");
        return b.toString();
    }
}

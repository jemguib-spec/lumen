// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Page « Maison », en plein écran sur le fond flouté : les appareils Home Assistant choisis, en
 * tuiles de verre (une lumière allumée éclaire sa tuile dans sa couleur, une jauge montre son
 * niveau), les scènes en pastilles, un onglet par pièce. OK allume ou éteint, appui long règle.
 * L'état se met à jour tout seul tant que la page est ouverte.
 */
final class HomePanel {
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final int MAX_SHOWN = 60, COLS = 5, PILLS = 5;
    private static final long REFRESH_MS = 8000;
    /** Pièce affichée (« » = toute la maison) : gardée d'une ouverture à l'autre. */
    private static String room = "";

    private final MainActivity a;
    private FrameLayout page;
    private LinearLayout tabs, body;
    private TextView status;
    private List<HomeAssistant.Entity> shown = new ArrayList<>();
    private final List<HaTile> tiles = new ArrayList<>();
    private final Map<String, GlassUi.Pill> pills = new HashMap<>();
    private final List<TextView> chips = new ArrayList<>();

    private HomePanel(MainActivity a) { this.a = a; }

    static void open(final MainActivity a) { new HomePanel(a).show(); }

    private void show() {
        FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() { a.refreshHouse(); a.markScenes(); }
        });
        page = new FrameLayout(a);
        ImageView bg = new ImageView(a);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (Glass.backdrop() != null) bg.setImageBitmap(Glass.backdrop());
        page.addView(bg, MainActivity.match());
        View tint = new View(a);
        tint.setBackgroundColor(a.highContrast ? 0xF005060A : 0xA605060A);
        page.addView(tint, MainActivity.match());
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(a.u(112), a.u(64), a.u(112), 0);
        page.addView(col, MainActivity.match());
        o.addView(page, MainActivity.match());

        TextView title = a.text(48, "sans-serif-light", Palette.TEXT);
        title.setText(L.t("Maison"));
        col.addView(title);
        status = a.text(22, "sans-serif", Palette.TEXT_2);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = a.u(8);
        col.addView(status, sl);

        HorizontalScrollView ts = new HorizontalScrollView(a);
        ts.setHorizontalScrollBarEnabled(false);
        ts.setOverScrollMode(View.OVER_SCROLL_NEVER);
        ts.setClipChildren(false);
        ts.setClipToPadding(false);
        tabs = new LinearLayout(a);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, a.u(22), 0, a.u(4));
        tabs.setVisibility(View.GONE);
        ts.addView(tabs);
        col.addView(ts, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(60));
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        body.setPadding(0, a.u(8), 0, a.u(72));
        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!a.prefs.haConfigured()) {
            status.setText(L.t("Relie Lumen à ton Home Assistant depuis le téléphone :\nonglet Plus › Home Assistant (adresse et jeton)."));
            LinearLayout r = a.listRow(L.t("Ouvrir les réglages du téléphone"), null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(false); PhonePanel.openPhonePanel(a); }
            });
            addRow(r);
            r.requestFocus();
            return;
        }
        status.setText(L.t("Connexion à Home Assistant…"));
        // focus posé tout de suite pour que Retour ferme la page même pendant le chargement
        page.setFocusable(true);
        page.requestFocus();
        load(true);
    }

    /** Ligne d'action (réessayer, ouvrir le téléphone) : étroite, à gauche, sous le message. */
    private void addRow(View r) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.u(640), a.u(88 * a.textScale));
        lp.topMargin = a.u(24);
        body.addView(r, lp);
    }

    /** Lit les états ; construit la page la première fois, sinon met seulement les tuiles à jour. */
    private void load(final boolean build) {
        final String url = a.prefs.haUrl(), token = a.prefs.haToken();
        io.execute(new Runnable() {
            @Override public void run() {
                List<HomeAssistant.Entity> all = null;
                String err = null;
                try { all = HaStates.load(url, token); }
                catch (Exception e) {
                    err = HomeAssistant.human(e);
                    CrashLog.note(a, "Home Assistant : " + e);
                }
                final List<HomeAssistant.Entity> got = all;
                final String error = err;
                a.h.post(new Runnable() {
                    @Override public void run() {
                        if (!page.isAttachedToWindow()) return;
                        if (got == null) {
                            if (build) { status.setText(error); retryRow(); }
                            return;
                        }
                        if (build) {
                            shown = HomeAssistant.shown(got, a.prefs.haPinned(), MAX_SHOWN);
                            boolean pinned = !a.prefs.haPinned().isEmpty() && shown.size() < got.size();
                            status.setText(shown.isEmpty() ? L.t("Aucun appareil pilotable trouvé (lumières, prises, scènes, volets…).")
                                    : pinned ? L.t("Tes appareils · choisis-les sur le téléphone (Plus › Home Assistant)")
                                    : shown.size() + L.t(" appareils · choisis ceux à garder ici depuis le téléphone"));
                            buildTabs();
                            buildBody(true);
                            // tant que la page est ouverte, l'état suit la maison (une seule boucle)
                            a.h.postDelayed(new Runnable() {
                                @Override public void run() {
                                    if (!page.isAttachedToWindow()) return;
                                    load(false);
                                    a.h.postDelayed(this, REFRESH_MS);
                                }
                            }, REFRESH_MS);
                        } else {
                            Map<String, HomeAssistant.Entity> live = new HashMap<>();
                            for (HomeAssistant.Entity e : got) live.put(e.id, e);
                            for (HaTile t : tiles) t.update(live.get(t.entityId()));
                        }
                    }
                });
            }
        });
    }

    private void retryRow() {
        body.removeAllViews();
        final LinearLayout r = a.listRow(L.t("Réessayer"), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                body.removeAllViews();
                status.setText(L.t("Connexion à Home Assistant…"));
                page.requestFocus();
                load(true);
            }
        });
        addRow(r);
        r.requestFocus();
    }

    // ------------------------------------------------------------------ onglets des pièces

    private void buildTabs() {
        tabs.removeAllViews();
        chips.clear();
        TreeSet<String> rooms = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (HomeAssistant.Entity e : shown) if (!e.area.isEmpty()) rooms.add(e.area);
        if (rooms.size() < 2) { room = ""; tabs.setVisibility(View.GONE); return; }
        if (!rooms.contains(room)) room = "";
        tabs.setVisibility(View.VISIBLE);
        chip(L.t("Toute la maison"), "");
        for (String r : rooms) chip(r, r);
    }

    private void chip(String label, final String value) {
        final TextView c = a.text(22, "sans-serif-medium", Palette.TEXT);
        c.setText(label);
        c.setSingleLine(true);
        c.setFocusable(true);
        c.setTag(value);
        c.setPadding(a.u(26), a.u(12), a.u(26), a.u(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.u(30));
        c.setBackground(bg);
        styleChip(c, false);
        c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick();
                // l'onglet sélectionné s'ouvre tout seul, comme on feuillette
                if (f && !value.equals(room)) { room = value; buildBody(false); }
                for (TextView x : chips) styleChip(x, x.isFocused());
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = a.u(12);
        tabs.addView(c, lp);
        chips.add(c);
    }

    private void styleChip(TextView c, boolean focused) {
        boolean selected = room.equals(c.getTag());
        GradientDrawable bg = (GradientDrawable) c.getBackground();
        bg.setColor(focused ? Palette.SELECTED : selected ? 0x40FFFFFF : 0x14FFFFFF);
        bg.setStroke(Math.max(1, a.u(1.5f)), selected && !focused ? Palette.TEXT_3 : 0x1FFFFFFF);
        c.setTextColor(focused ? Palette.ON_SELECTED : selected ? 0xFFFFFFFF : Palette.TEXT_2);
    }

    // ------------------------------------------------------------------ scènes et tuiles

    private void buildBody(boolean focusFirst) {
        body.removeAllViews();
        tiles.clear();
        pills.clear();
        List<HomeAssistant.Entity> scenes = new ArrayList<>(), devices = new ArrayList<>();
        for (HomeAssistant.Entity e : shown) {
            if (!room.isEmpty() && !room.equals(e.area)) continue;
            if (e.trigger()) scenes.add(e); else devices.add(e);
        }
        View first = null;
        String last = a.prefs.haLastScene();
        LinearLayout line = null;
        for (int i = 0; i < scenes.size(); i++) {
            if (i % PILLS == 0) {
                if (i == 0) body.addView(section(L.t("Scènes")));
                line = line(a.u(10), a.u(10));
            }
            final HomeAssistant.Entity e = scenes.get(i);
            GlassUi.Pill p = new GlassUi.Pill(a, e.name, e.id.equals(last), new View.OnClickListener() {
                @Override public void onClick(View v) { launch(e, v); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = a.u(18);
            line.addView(p, lp);
            pills.put(e.id, p);
            if (first == null) first = p;
        }
        int gap = a.u(28);
        int screenW = a.root.getWidth() > 0 ? a.root.getWidth() : a.u(1920);
        int tw = (screenW - a.u(224) - gap * (COLS - 1)) / COLS, th = Math.round(tw * 0.56f);
        String domain = null;
        int inLine = 0;
        View firstTile = null;
        for (HomeAssistant.Entity e : devices) {
            if (!e.domain.equals(domain)) {
                domain = e.domain;
                body.addView(section(L.t(HomeAssistant.domainName(domain))));
                inLine = COLS;
            }
            if (inLine == COLS) { line = line(a.u(14), a.u(14)); inLine = 0; }
            HaTile t = new HaTile(a, e, tw, th, actions);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (inLine < COLS - 1) lp.rightMargin = gap;
            line.addView(t, lp);
            tiles.add(t);
            inLine++;
            if (firstTile == null) firstTile = t;
        }
        if (!devices.isEmpty()) {
            TextView hint = a.text(20, "sans-serif", Palette.TEXT_3);
            hint.setText(L.t("OK : allumer ou éteindre   ·   OK maintenu : régler, puis ◀ ▶"));
            hint.setPadding(0, a.u(26), 0, 0);
            body.addView(hint);
        }
        // d'abord un appareil (le geste le plus courant), sinon une scène
        if (focusFirst) { if (firstTile != null) firstTile.requestFocus(); else if (first != null) first.requestFocus(); }
        body.setAlpha(0f);
        body.animate().alpha(1f).setStartDelay(0).setDuration(Motion.FAST).setInterpolator(Motion.EASE).start();
    }

    private TextView section(String s) {
        TextView t = a.text(20, "sans-serif-medium", Palette.TEXT_2);
        t.setText(s.toUpperCase(java.util.Locale.FRANCE));
        t.setLetterSpacing(0.1f);
        t.setPadding(0, a.u(30), 0, a.u(8));
        return t;
    }

    /** Rangée de tuiles ou de pastilles, avec la place qu'il faut pour qu'elles grandissent à la sélection. */
    private LinearLayout line(int top, int bottom) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        l.setPadding(0, top, 0, bottom);
        body.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    // ------------------------------------------------------------------ actions

    private final HaTile.Listener actions = new HaTile.Listener() {
        @Override public void toggle(final HomeAssistant.Entity e) {
            send(new Call() { @Override public void run(String url, String token) throws Exception { HomeAssistant.call(url, token, e); } }, 450);
        }
        @Override public void level(final HomeAssistant.Entity e, final int pct) {
            send(new Call() { @Override public void run(String url, String token) throws Exception { HomeAssistant.setLevel(url, token, e, pct); } }, 900);
        }
    };

    private interface Call { void run(String url, String token) throws Exception; }

    /** Envoie une commande, puis relit la maison un instant plus tard (le temps que l'appareil réagisse). */
    private void send(final Call call, final long thenMs) {
        final String url = a.prefs.haUrl(), token = a.prefs.haToken();
        if (!a.prefs.haLastScene().isEmpty()) { // un appareil change : la scène n'est plus forcément en place
            a.prefs.setHaLastScene("");
            for (GlassUi.Pill p : pills.values()) p.setActive(false);
        }
        io.execute(new Runnable() {
            @Override public void run() {
                String err = null;
                try { call.run(url, token); }
                catch (Exception ex) { err = HomeAssistant.human(ex); CrashLog.note(a, "Home Assistant : " + ex); }
                final String error = err;
                a.h.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (error != null) a.showNotice(error);
                        if (page.isAttachedToWindow()) load(false);
                    }
                }, error == null ? thenMs : 0);
            }
        });
    }

    private void launch(final HomeAssistant.Entity e, View from) {
        Ripple.play(a, from);
        if ("scene".equals(e.domain)) {
            a.prefs.setHaLastScene(e.id);
            for (Map.Entry<String, GlassUi.Pill> p : pills.entrySet()) p.getValue().setActive(p.getKey().equals(e.id));
        }
        final String url = a.prefs.haUrl(), token = a.prefs.haToken();
        io.execute(new Runnable() {
            @Override public void run() {
                String err = null;
                try { HomeAssistant.call(url, token, e); }
                catch (Exception ex) { err = HomeAssistant.human(ex); CrashLog.note(a, "Home Assistant : " + ex); }
                final String error = err;
                a.h.postDelayed(new Runnable() {
                    @Override public void run() {
                        a.showNotice(error != null ? error : e.name);
                        if (page.isAttachedToWindow()) load(false);
                    }
                }, error == null ? 450 : 0);
            }
        });
    }
}

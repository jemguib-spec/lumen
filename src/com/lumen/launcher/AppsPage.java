// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Page Applications : recherche en haut, puis une rangée par catégorie (Favoris, Vidéo et TV,
 * Musique et radio, Jeux, Outils et autres). Construite d'avance par MainActivity pour s'ouvrir
 * instantanément ; l'ouverture, la fermeture et les gestes restent dans MainActivity.
 */
final class AppsPage {
    private final MainActivity a;
    ScrollView scroll;
    LinearLayout sections;
    android.widget.EditText search;

    AppsPage(MainActivity a) { this.a = a; }

    /** Retour pendant une recherche : efface la recherche (true si c'était le cas). */
    boolean clearSearch() {
        if (search == null || search.length() == 0) return false;
        search.setText("");
        a.focusFirstApp();
        return true;
    }

    /** À la fermeture de la page. */
    void reset() {
        if (search != null) { hideKeyboard(search); if (search.length() > 0) search.setText(""); }
    }

    /** À l'ouverture : la page et ses rangées repartent du début. */
    void rewind() {
        if (scroll != null) scroll.scrollTo(0, 0);
        if (sections != null) for (int i = 0; i < sections.getChildCount(); i++) {
            View v = sections.getChildAt(i);
            if (v instanceof HorizontalScrollView) v.scrollTo(0, 0);
        }
    }

    boolean built() { return scroll != null; }


    /**
     * Page Applications : recherche en haut, puis une rangée par catégorie (Favoris, Vidéo et TV,
     * Musique et radio, Jeux, Outils et autres). Pendant une recherche, une seule grille de résultats.
     */
    /** Tranche horaire de la dernière construction (la rangée « Ce soir… » change avec l'heure). */
    private int builtSlot = -1;

    boolean stale() { return builtSlot != Habits.slot(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)); }

    void build(String focusKey) {
        a.dirtyApps = false;
        builtSlot = Habits.slot(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY));
        if (scroll != null) a.appsLayer.removeView(scroll);
        scroll = new ScrollView(a) {
            @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
                super.onScrollChanged(l, t, ol, ot);
                a.refreshGlass(this);
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(a.u(60));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(0, a.u(64), 0, a.u(120));
        scroll.addView(col);

        // en-tête : titre à gauche, recherche à droite
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(a.u(112), 0, a.u(112), a.u(8));
        TextView title = a.text(52, "sans-serif-light", 0xF5FFFFFF);
        title.setText("Applications");
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String keep = search != null ? search.getText().toString() : "";
        search = searchField();
        search.setText(keep);
        head.addView(search, new LinearLayout.LayoutParams(a.u(520), a.u(76)));
        col.addView(head);

        sections = new LinearLayout(a);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setClipChildren(false);
        sections.setClipToPadding(false);
        col.addView(sections);
        Tile toFocus = fillApps(keep, focusKey);

        a.appsLayer.addView(scroll, MainActivity.match());
        if (a.appsOpen && a.overlay == null) {
            if (toFocus != null) toFocus.requestFocus(); else a.focusFirstApp();
        }
    }

    /** Champ de recherche en verre ; la liste se filtre à chaque lettre. */
    private android.widget.EditText searchField() {
        final android.widget.EditText e = new android.widget.EditText(a);
        e.setSingleLine(true);
        e.setHint("Rechercher une appli");
        e.setHintTextColor(0x80FFFFFF);
        e.setTextColor(0xF5FFFFFF);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * a.unit);
        e.setTypeface(Fonts.get("sans-serif", Typeface.NORMAL));
        e.setPadding(a.u(30), 0, a.u(30), 0);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.u(38));
        bg.setColor(0x26FFFFFF);
        bg.setStroke(Math.max(1, a.u(1.5f)), 0x33FFFFFF);
        e.setBackground(bg);
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                bg.setColor(f ? 0xFFF5F5F7 : 0x26FFFFFF);
                e.setTextColor(f ? 0xFF111216 : 0xF5FFFFFF);
                e.setHintTextColor(f ? 0x80111216 : 0x80FFFFFF);
                if (f) Sounds.get(a).tick();
            }
        });
        e.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(android.text.Editable ed) { if (sections != null) fillApps(ed.toString(), null); }
        });
        e.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent ev) {
                hideKeyboard(e);
                a.focusFirstApp(); // OK sur le clavier : on va directement au premier résultat
                return true;
            }
        });
        return e;
    }

    private void hideKeyboard(View v) {
        try {
            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    /** Remplit les rangées (ou les résultats d'une recherche). Renvoie la vignette à sélectionner. */
    private Tile fillApps(String query, String focusKey) {
        sections.removeAllViews();
        Set<String> hidden = a.prefs.hiddenApps();
        final List<String> favs = a.prefs.favorites();
        List<AppRepo.App> apps = new ArrayList<>();
        for (AppRepo.App x : a.repo.all()) if (!hidden.contains(x.key)) apps.add(x);
        final boolean bn = a.banner();
        int screenW = a.root.getWidth() > 0 ? a.root.getWidth() : a.u(1920);
        final int cols = 6, gap = a.u(30), tw = (screenW - a.u(224) - gap * (cols - 1)) / cols, th = tw * 9 / 16;
        Tile[] focus = {null};
        boolean[] first = {true};

        String q = query == null ? "" : query.trim();
        if (!q.isEmpty()) {
            List<AppRepo.App> found = new ArrayList<>();
            for (AppRepo.App x : apps) if (AppCategory.matches(x.label, q)) found.add(x);
            sections.addView(sectionLabel(found.isEmpty() ? "Aucune appli ne correspond" : found.size() + (found.size() > 1 ? " résultats" : " résultat")));
            LinearLayout line = null;
            for (int i = 0; i < found.size(); i++) {
                if (i % cols == 0) { line = appsLine(); sections.addView(line); }
                addAppCell(line, found.get(i), tw, th, bn, favs, i % cols != cols - 1 && i < found.size() - 1 ? gap : 0, focusKey, focus, first);
            }
            return focus[0];
        }

        // Habitudes : ce que tu ouvres le plus à ce moment de la journée (calculé sur la TV)
        if (a.prefs.habitsOn()) {
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            List<AppRepo.App> usual = new ArrayList<>();
            for (String k : a.prefs.habits().top(hour, 6, 2)) { AppRepo.App x = a.repo.get(k); if (x != null && !hidden.contains(k)) usual.add(x); }
            if (usual.size() >= 2) addAppRow(Habits.SLOT_NAMES[Habits.slot(hour)], usual, tw, th, bn, favs, gap, focusKey, focus, first);
        }
        // Favoris d'abord, dans l'ordre de l'accueil
        List<AppRepo.App> favApps = new ArrayList<>();
        for (String k : favs) { AppRepo.App x = a.repo.get(k); if (x != null && !hidden.contains(k)) favApps.add(x); }
        addAppRow("Favoris", favApps, tw, th, bn, favs, gap, focusKey, focus, first);
        List<List<AppRepo.App>> groups = new ArrayList<>();
        for (int i = 0; i < AppCategory.NAMES.length; i++) groups.add(new ArrayList<AppRepo.App>());
        for (AppRepo.App x : apps) if (!favs.contains(x.key)) groups.get(x.section()).add(x); // pas de doublon avec les favoris
        for (int i : AppCategory.ORDER)
            addAppRow(AppCategory.NAMES[i], groups.get(i), tw, th, bn, favs, gap, focusKey, focus, first);
        return focus[0];
    }

    private TextView sectionLabel(String s) {
        TextView t = a.text(21, "sans-serif-medium", 0x99FFFFFF);
        t.setText(s.toUpperCase());
        t.setLetterSpacing(0.08f);
        t.setPadding(a.u(112), a.u(34), 0, a.u(2));
        return t;
    }

    private LinearLayout appsLine() {
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setClipChildren(false);
        line.setClipToPadding(false);
        line.setPadding(a.u(112), a.u(22), a.u(112), a.u(a.banner() ? 10 : 22));
        return line;
    }

    /** Une rangée qui défile horizontalement (toutes les applis d'une catégorie). */
    private void addAppRow(String name, List<AppRepo.App> list, int tw, int th, boolean bn, List<String> favs, int gap,
                           String focusKey, Tile[] focus, boolean[] first) {
        if (list.isEmpty()) return;
        sections.addView(sectionLabel(name));
        LinearLayout line = appsLine();
        HorizontalScrollView hs = new HorizontalScrollView(a) {
            @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
                super.onScrollChanged(l, t, ol, ot);
                a.refreshGlass(this);
            }
        };
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.addView(line);
        sections.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < list.size(); i++)
            addAppCell(line, list.get(i), tw, th, bn, favs, i < list.size() - 1 ? gap : 0, focusKey, focus, first);
    }

    private void addAppCell(LinearLayout line, final AppRepo.App app, int tw, int th, boolean bn, List<String> favs, int rightGap,
                            String focusKey, Tile[] focus, boolean[] first) {
        LinearLayout cell = new LinearLayout(a);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        final Tile t = Tile.app(a, app, tw, th, bn, a.prefs.showIcons(), a.prefs.tintTiles(), bn, a.wordTiles());
        if (first[0]) { t.setTag("first"); first[0] = false; }
        t.setFavoriteMark(favs.contains(app.key));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.launchAnimated(app, t); } });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { a.openAppMenu(t, false); return true; }
        });
        cell.addView(t);
        if (bn) {
            final TextView name = a.text(22, "sans-serif", 0x99FFFFFF);
            name.setText(app.label);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(tw, ViewGroup.LayoutParams.WRAP_CONTENT);
            nl.topMargin = a.u(16);
            cell.addView(name, nl);
            t.setFocusListener(new Tile.FocusListener() {
                @Override public void onTileFocus(Tile tile, boolean f) {
                    name.setTextColor(f ? 0xFFFFFFFF : 0x99FFFFFF);
                    name.animate().translationY(f ? a.u(12) : 0).setDuration(200).start();
                }
            });
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.rightMargin = rightGap;
        line.addView(cell, cl);
        if (focus[0] == null && app.key.equals(focusKey)) focus[0] = t;
    }

}

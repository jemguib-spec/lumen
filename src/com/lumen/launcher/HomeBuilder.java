// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/** Construction de l'écran d'accueil : calques, fond, rangées, boutons du haut, légende, horloge, page des applis, mode veille ; et l'écran de démarrage. */
final class HomeBuilder {
    private final MainActivity a;

    HomeBuilder(MainActivity a) { this.a = a; }

    void buildUi() {
        a.root = new FrameLayout(a);
        a.root.setBackgroundColor(Color.BLACK);
        a.root.setClipChildren(false);

        a.wallLayer = new FrameLayout(a);
        a.wallLayer.setScaleX(1.04f); // marge pour l'effet de profondeur (le fond glisse sans montrer ses bords)
        a.wallLayer.setScaleY(1.04f);
        a.root.addView(a.wallLayer, MainActivity.match());
        a.wall = new Wall(a, a.prefs, a.online, a.wallLayer);
        a.wall.setListener(a);
        a.fx = new WeatherFx(a, a.unit);
        a.fx.setAlpha(0f);
        a.root.addView(a.fx, MainActivity.match());
        a.glow = new Glow(a);
        a.root.addView(a.glow, MainActivity.match());

        // ---- accueil
        a.homeLayer = new FrameLayout(a);
        a.homeLayer.setClipChildren(false);
        a.root.addView(a.homeLayer, MainActivity.match());

        a.scrim = new View(a);
        a.scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xB804040A, 0x4004040A, 0x0004040A}));
        a.homeLayer.addView(a.scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.u(520), Gravity.BOTTOM));

        // lignes de favoris, ancrées en bas et centrées
        a.rowsBox = new LinearLayout(a);
        a.rowsBox.setOrientation(LinearLayout.VERTICAL);
        a.rowsBox.setClipChildren(false);
        a.rowsBox.setClipToPadding(false);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        rl.bottomMargin = a.u(82);
        a.homeLayer.addView(a.rowsBox, rl);
        a.shelf = new TopShelf(a);

        a.focusLabel = a.text(28, "sans-serif-medium", 0xF2FFFFFF);
        a.focusLabel.setShadowLayer(a.u(10), 0, 0, 0x66000000);
        a.focusLabel.setGravity(Gravity.CENTER_HORIZONTAL);

        // boutons ronds en haut à droite, à gauche de l'horloge
        a.topButtons = new LinearLayout(a);
        a.topButtons.setOrientation(LinearLayout.HORIZONTAL);
        a.topButtons.setClipChildren(false);
        a.topButtons.setClipToPadding(false);
        a.topButtons.setPadding(a.u(12), a.u(12), a.u(12), a.u(12));
        int bs = a.u(84);
        Tile amb = Tile.button(a, Glyph.SPARK, "Ambiances", bs); // la lune reste celle du mode veille
        amb.setFocusListener(a.homeFocus);
        amb.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.openAmbiance(); } });
        Tile inputs = Tile.button(a, Glyph.INPUT, "Entrées", bs);
        inputs.setFocusListener(a.homeFocus);
        inputs.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { InputsPanel.openInputs(a); } });
        Tile find = Tile.button(a, Glyph.SEARCH, "Rechercher", bs);
        find.setFocusListener(a.homeFocus);
        find.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.openSearch(); } });
        Tile house = a.houseButton = Tile.button(a, Glyph.HOME, "Maison", bs);
        house.setFocusListener(a.homeFocus);
        house.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { HomePanel.open(a); } });
        Tile who = a.profileButton = Tile.button(a, Glyph.PERSON, "Profils", bs);
        who.setFocusListener(a.homeFocus);
        who.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { WhoPage.show(a, true); } });
        Tile all = Tile.button(a, Glyph.GRID, "Toutes les applications", bs);
        all.setFocusListener(a.homeFocus);
        all.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.openApps(); } });
        Tile set = Tile.button(a, Glyph.SLIDERS, "Réglages", bs);
        set.setFocusListener(a.homeFocus);
        set.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { SettingsPanel.openSettings(a); } });
        for (Tile b : new Tile[]{find, amb, inputs, house, who, all, set}) {
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.leftMargin = a.u(18);
            a.topButtons.addView(b, bl);
        }
        house.setVisibility(a.prefs.haButton() ? View.VISIBLE : View.GONE);
        a.updateProfileButton();
        FrameLayout.LayoutParams tbl = MainActivity.wrap(Gravity.TOP | Gravity.END);
        tbl.topMargin = a.u(56);
        tbl.rightMargin = a.u(420);
        a.homeLayer.addView(a.topButtons, tbl);

        // ---- légende du fond, en haut à gauche
        a.captionBox = new LinearLayout(a);
        a.captionBox.setOrientation(LinearLayout.VERTICAL);
        a.captionTitle = a.text(22, "sans-serif-medium", 0xC8FFFFFF);
        a.captionTitle.setShadowLayer(a.u(8), 0, 0, 0x55000000);
        a.captionTitle.setMaxLines(1);
        a.captionTitle.setEllipsize(TextUtils.TruncateAt.END);
        a.captionBox.addView(a.captionTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        a.captionCredit = a.text(18, "sans-serif", 0x8CFFFFFF);
        a.captionCredit.setShadowLayer(a.u(8), 0, 0, 0x55000000);
        a.captionCredit.setMaxLines(1);
        a.captionCredit.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams cl2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl2.topMargin = a.u(8);
        a.captionBox.addView(a.captionCredit, cl2);
        a.dots = new LinearLayout(a);
        a.dots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = a.u(14);
        a.captionBox.addView(a.dots, dl);
        // bandeau d'accueil (« Bonsoir · Pluie 12° · Reprendre … ») : sous le nom de la photo
        a.greet = a.text(22, "sans-serif", 0xD9FFFFFF);
        a.greet.setShadowLayer(a.u(8), 0, 0, 0x55000000);
        a.greet.setSingleLine(true);
        a.greet.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.topMargin = a.u(18);
        a.captionBox.addView(a.greet, gl);
        FrameLayout.LayoutParams capl = new FrameLayout.LayoutParams(a.u(880), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        capl.leftMargin = a.u(112);
        capl.topMargin = a.u(72);
        a.root.addView(a.captionBox, capl);

        // ---- horloge, en haut à droite
        a.clockBox = new LinearLayout(a);
        a.clockBox.setOrientation(LinearLayout.VERTICAL);
        a.clockBox.setGravity(Gravity.END);
        TextClock time = new TextClock(a);
        time.setFormat24Hour("H:mm");
        time.setFormat12Hour("h:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, 56 * a.unit);
        time.setTypeface(Fonts.get("sans-serif-light", Typeface.NORMAL));
        time.setTextColor(0xF5FFFFFF);
        time.setIncludeFontPadding(false);
        time.setShadowLayer(a.u(12), 0, 0, 0x55000000);
        TextClock date = new TextClock(a);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * a.unit);
        date.setTypeface(Fonts.get("sans-serif", Typeface.NORMAL));
        date.setTextColor(0xB3FFFFFF);
        date.setShadowLayer(a.u(8), 0, 0, 0x55000000);
        a.clockBox.addView(time);
        LinearLayout.LayoutParams dtl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dtl.topMargin = a.u(8);
        a.clockBox.addView(date, dtl);
        FrameLayout.LayoutParams ckl = MainActivity.wrap(Gravity.TOP | Gravity.END);
        ckl.topMargin = a.u(64);
        ckl.rightMargin = a.u(112);
        a.root.addView(a.clockBox, ckl);
        a.clockBox.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) a.topButtons.getLayoutParams();
                int want = a.clockBox.getVisibility() == View.VISIBLE ? a.u(112) + (r - l) + a.u(36) : a.u(100);
                if (p.rightMargin != want) { p.rightMargin = want; a.topButtons.setLayoutParams(p); }
            }
        });

        // ---- page « Toutes les applications »
        a.appsLayer = new FrameLayout(a);
        a.appsLayer.setVisibility(View.GONE);
        a.appsBg = new ImageView(a);
        a.appsBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        a.appsLayer.addView(a.appsBg, MainActivity.match());
        a.appsTint = new View(a);
        a.appsTint.setBackgroundColor(0x9905060A);
        a.appsLayer.addView(a.appsTint, MainActivity.match());
        a.root.addView(a.appsLayer, a.root.indexOfChild(a.captionBox), MainActivity.match());

        // ---- mode veille
        a.ambient = new AmbientView(a, a.unit, a.weather);
        a.ambient.setVisibility(View.GONE);
        a.root.addView(a.ambient, MainActivity.match());
        a.veilleWall = new Wall(a, a.prefs, a.online, a.ambient.wallLayer());
        a.veilleWall.setVeilleMode(true);
        a.veilleWall.setListener(new Wall.Listener() {
            @Override public void onWallShown(WallItem item, Bitmap backdrop) { if (a.ambientOn) a.ambient.setCaption(item); }
        });

        // ---- notification flottante
        a.notice = a.text(24, "sans-serif-medium", 0xFF111216);
        GradientDrawable nb = new GradientDrawable();
        nb.setColor(0xF5F5F5F7);
        nb.setCornerRadius(a.u(40));
        a.notice.setBackground(nb);
        a.notice.setPadding(a.u(36), a.u(18), a.u(36), a.u(18));
        a.notice.setAlpha(0f);
        a.notice.setElevation(a.u(40));
        FrameLayout.LayoutParams nl = MainActivity.wrap(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        nl.bottomMargin = a.u(40);
        a.root.addView(a.notice, nl);

        a.perf = new Perf.Overlay(a);
        a.perf.setVisibility(View.GONE);
        FrameLayout.LayoutParams pl = MainActivity.wrap(Gravity.TOP | Gravity.START);
        pl.leftMargin = a.u(24);
        pl.topMargin = a.u(24);
        a.root.addView(a.perf, pl);

        a.setContentView(a.root);
        a.intro();
        // première image affichée : on mesure le démarrage, puis on lance le reste
        a.root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                a.root.getViewTreeObserver().removeOnPreDrawListener(this);
                a.root.post(new Runnable() { @Override public void run() { Perf.onFirstFrame(); } });
                return true;
            }
        });
        a.root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                Glass.setScreen(loc[0] + v.getWidth(), loc[1] + v.getHeight());
                if (r - l != or - ol || b - t != ob - ot) a.refreshGlass(a.root);
            }
        });
        showSplash();
    }

    /** Écran de démarrage : le mot « lumen » apparaît puis s'efface pendant que le fond arrive. */
    void showSplash() {
        final FrameLayout splash = new FrameLayout(a);
        splash.setBackgroundColor(Color.BLACK);
        final TextView word = a.text(84, "sans-serif-thin", 0xFFFFFFFF);
        word.setText("lumen");
        word.setLetterSpacing(0.35f);
        word.setAlpha(0f);
        // après « Redémarrer Lumen » : la même ligne qu'avant l'arrêt, pour enchaîner sans coupure
        final boolean restarted = a.prefs.restarting();
        if (restarted) {
            a.prefs.setRestarting(false);
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(word, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            word.setAlpha(1f); // il était déjà à l'écran juste avant l'arrêt
            TextView sub = a.text(24, "sans-serif-light", 0x99FFFFFF);
            sub.setText("REDÉMARRAGE");
            sub.setLetterSpacing(0.3f);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.topMargin = a.u(18);
            col.addView(sub, sl);
            sub.animate().alpha(0f).setStartDelay(700).setDuration(500).start();
            splash.addView(col, MainActivity.wrap(Gravity.CENTER));
            a.h.postDelayed(new Runnable() { @Override public void run() { a.showNotice("Lumen a redémarré"); } }, 2200);
        } else splash.addView(word, MainActivity.wrap(Gravity.CENTER));
        a.root.addView(splash, MainActivity.match());
        word.setScaleX(0.96f);
        word.setScaleY(0.96f);
        word.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(new DecelerateInterpolator()).withEndAction(new Runnable() {
            @Override public void run() {
                word.animate().alpha(0f).setStartDelay(500).setDuration(600).start();
                splash.animate().alpha(0f).setStartDelay(800).setDuration(900).withEndAction(new Runnable() {
                    @Override public void run() { a.root.removeView(splash); }
                }).start();
            }
        }).start();
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Économiseur d'écran système : le mode veille de Lumen s'affiche aussi quand la TV
 * est inactive dans n'importe quelle appli (si Lumen est choisi comme économiseur).
 */
public class LumenDream extends DreamService implements Wall.Listener {
    private Wall wall;
    private AmbientView ambient;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        CrashLog.install(this);
        Fonts.init(this);
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        float unit = Math.max(dm.widthPixels, dm.heightPixels) / 1920f;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        FrameLayout wallLayer = new FrameLayout(this);
        root.addView(wallLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Weather w = new Weather(this);
        double[] c = w.coords();
        if (c != null) Sky.setLocation(c[0], c[1]);
        ambient = new AmbientView(this, unit, w);
        root.addView(ambient, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        Prefs prefs = new Prefs(this);
        wall = new Wall(this, prefs, new Online(this, prefs), wallLayer);
        wall.setVeilleMode(prefs.veilleSource() != Prefs.VEILLE_LIVE); // images seulement, comme le mode veille
        wall.setListener(this);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        wall.start();
        ambient.start();
    }

    @Override public void onDreamingStopped() {
        wall.stop();
        ambient.stop();
        super.onDreamingStopped();
    }

    @Override public void onWallShown(WallItem item, Bitmap backdrop) {
        ambient.setCaption(item);
    }
}

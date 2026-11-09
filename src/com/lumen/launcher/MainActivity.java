// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity implements Wall.Listener, Online.Listener, PhoneServer.Host {

    // Favoris proposés au premier lancement, dans cet ordre, s'ils sont installés
    private static final String[] DEFAULT_FAVORITES = {
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.plexapp.android",
            "com.crunchyroll.crunchyroid", "com.canal.android.canal",
            "com.disney.disneyplus", "com.google.android.youtube.tv"
    };
    private static final long IDLE_MS = 45_000;

    Prefs prefs;
    AppRepo repo;
    Online online;
    Wall wall;
    final Handler h = new Handler(Looper.getMainLooper());
    float unit = 1f; // 1 px de la maquette 1920×1080

    FrameLayout root, homeLayer, appsLayer;
    LinearLayout captionBox, clockBox, dots;
    private TextView captionTitle, captionCredit, focusLabel, notice;
    private LinearLayout rowsBox, topButtons;
    private FrameLayout wallLayer;
    private TextView greet;
    private final List<LinearLayout> rows = new ArrayList<>();
    private final List<HorizontalScrollView> rowScrolls = new ArrayList<>();
    private ImageView appsBg;
    FrameLayout overlay;
    private Runnable onOverlayClosed;
    View focusBeforeOverlay;
    private View focusBeforeApps;
    private Tile movingTile;
    boolean appsOpen, idle, dirtyRow, dirtyApps, ambientOn;
    private AmbientView ambient;
    Weather weather;
    private View scrim;
    WeatherFx fx;
    Glow glow;
    private FrameLayout zoomView;
    PhoneServer phone;
    boolean resumeOpen;
    private AmbiancePanel ambiance;
    TopShelf shelf;
    Perf.Overlay perf;
    private boolean firstResume = true;
    final AppsPage appsPage = new AppsPage(this);
    Updater updater;

    // ------------------------------------------------------------------ cycle de vie

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Perf.onCreate();
        CrashLog.install(this);
        Fonts.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
        unit = sw / 1920f;
        Glass.setScreen(sw, sh);

        prefs = new Prefs(this);
        textScale = Prefs.TEXT_SCALES[prefs.textScale()];
        highContrast = prefs.highContrast();
        repo = new AppRepo(this);
        repo.reload();
        online = new Online(this, prefs);
        online.setListener(this);
        weather = new Weather(this);
        phone = new PhoneServer(this, this);
        double[] c = weather.coords();
        if (c != null) Sky.setLocation(c[0], c[1]);
        Sounds.get(this).setEnabled(prefs.sounds());
        ensureDefaultFavorites();
        addInputsOnce();
        buildUi();
        buildRow(null);
        // icônes et couleurs : en arrière-plan, une fois l'accueil affiché
        h.postDelayed(new Runnable() { @Override public void run() { repo.warmUp(); } }, 400);
        updater = new Updater(this, prefs);
        updater.setListener(new Updater.Listener() {
            @Override public void onUpdateState() { updateStateChanged(); }
        });
        h.postDelayed(prebuildApps, 2500);   // page Applications construite d'avance, à l'abri des regards

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        if (SettingsPanel.reopenPage >= 0) h.post(new Runnable() { @Override public void run() { SettingsPanel.reopen(MainActivity.this); } });
    }

    /** Taille du texte ou contraste changés : on reconstruit l'interface, puis on revient dans les réglages. */
    void restyle() {
        SettingsPanel.reopenPage = SettingsPanel.ACCUEIL;
        recreate();
    }

    /** Lumen est à l'écran (au premier plan). */
    boolean resumedNow;

    @Override protected void onResume() {
        super.onResume();
        resumedNow = true;
        wall.start();
        if (appsOpen) LivingScene.paused = true;
        // tout ce qui n'est pas visible tout de suite attend la première image (démarrage plus rapide)
        h.removeCallbacks(afterShown);
        h.postDelayed(afterShown, firstResume ? 900 : 150);
        firstResume = false;
        perf.setOn(prefs.showPerf());
        fx.setRunning(true);
        updateWeatherFx();
        h.postDelayed(weatherTick, 30 * 60_000L);
        if (zoomView != null) {
            final View z = zoomView;
            zoomView = null;
            z.animate().alpha(0f).setDuration(350).withEndAction(new Runnable() {
                @Override public void run() { root.removeView(z); }
            }).start();
        }
        clockBox.setVisibility(prefs.showClock() ? View.VISIBLE : View.GONE);
        wake();
        if (overlay == null && getCurrentFocus() == null) {
            if (appsOpen) focusFirstApp(); else focusFirst();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        resumedNow = false;
        h.removeCallbacks(afterShown);
        perf.setOn(false);
        wall.stop();
        fx.setRunning(false);
        h.removeCallbacks(weatherTick);
        h.removeCallbacks(idleRun);
        h.removeCallbacks(ambientRun);
        if (ambientOn) exitAmbient(false);
    }

    /**
     * Lumen n'est plus affiché : le serveur téléphone s'arrête, sauf si un téléphone est connecté
     * (on peut alors revenir à l'accueil sans rescanner). Il s'arrête de toute façon quand l'écran
     * s'éteint, après 15 min sans activité, ou sur demande.
     */
    @Override protected void onStop() {
        super.onStop();
        phone.onBackground();
        online.setActive(false);
        closeAmbiance();
    }

    @Override protected void onStart() {
        super.onStart();
        online.setActive(true);
        if (prefs.cosyWanted()) online.refreshCosy();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(packageReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(screenOff); } catch (Exception ignored) { }
        phone.stop();
        super.onDestroy();
    }

    /** Touche Accueil alors que Lumen est affiché : retour à l'état initial. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        stopMoving();
        closeOverlay(false);
        if (appsOpen) closeApps(false);
        for (HorizontalScrollView hs : rowScrolls) hs.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override public void onBackPressed() {
        if (overlay != null || appsOpen) Sounds.get(this).back();
        if (movingTile != null) { stopMoving(); return; }
        if (overlay != null && SettingsPanel.back(this)) return; // sous-page des réglages : on remonte d'un niveau
        if (overlay != null) { closeOverlay(true); return; }
        if (appsOpen && appsPage.clearSearch()) return; // Retour efface d'abord la recherche
        if (appsOpen) { closeApps(true); return; }
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            repo.reload();
            repo.warmUp();
            if (Intent.ACTION_PACKAGE_ADDED.equals(i.getAction())
                    && !i.getBooleanExtra(Intent.EXTRA_REPLACING, false) && i.getData() != null) {
                AppRepo.App a = repo.findByPackage(i.getData().getSchemeSpecificPart());
                if (a != null) showNotice(a.label + " installée");
            }
            if (overlay != null) { dirtyRow = true; markAppsDirty(); return; }
            String key = focusedKey();
            buildRow(appsOpen ? null : key);
            if (appsOpen) buildApps(key); else markAppsDirty();
        }
    };

    // ------------------------------------------------------------------ fonds d'écran

    @Override public void onWallShown(WallItem item, Bitmap backdrop) {
        Glass.setSource(backdrop);
        refreshGlass(root);
        if (appsBg != null && Glass.backdrop() != null) appsBg.setImageBitmap(Glass.backdrop());
        updateCaption(item);
        if (ambientOn) ambient.setCaption(item);
        adaptScrim();
    }

    /** Voile plus dense quand le bas de l'image est clair, pour garder les vignettes lisibles. */
    private void adaptScrim() {
        Bitmap b = Glass.backdrop();
        if (b == null || scrim == null) return;
        long sum = 0;
        int n = 0;
        for (int y = b.getHeight() / 2; y < b.getHeight(); y += 4) {
            for (int x = 0; x < b.getWidth(); x += 4) {
                int c = b.getPixel(x, y);
                sum += (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10;
                n++;
            }
        }
        float lum = n == 0 ? 0.3f : sum / (n * 255f);
        scrim.animate().alpha(Math.min(1f, 0.7f + lum * 0.6f)).setDuration(1200).start();
    }

    @Override public void onThemeUpdated(String theme) {
        if (Online.COSY.equals(theme)) { if (ambiance != null) ambiance.onVideosUpdated(); return; }
        boolean wasFallback = wall.usingFallback();
        wall.refreshList();
        if (wasFallback && !wall.usingFallback()) showNotice("Nouvelles photos prêtes");
    }

    private void updateCaption(final WallItem item) {
        captionBox.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
            @Override public void run() {
                captionTitle.setText(item.title);
                captionCredit.setText(item.credit);
                captionCredit.setVisibility(TextUtils.isEmpty(item.credit) ? View.GONE : View.VISIBLE);
                updateDots();
                captionBox.animate().alpha(appsOpen || ambientOn ? 0f : 1f).setDuration(600).start();
            }
        }).start();
    }

    /** Petits points de progression du diaporama (6 au plus). */
    private void updateDots() {
        dots.removeAllViews();
        int n = Math.min(wall.count(), 6);
        if (n < 2) return;
        int active = Math.max(0, wall.position()) % n;
        for (int i = 0; i < n; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(u(3));
            g.setColor(i == active ? 0xE6FFFFFF : 0x59FFFFFF);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(i == active ? u(22) : u(6), u(6));
            if (i > 0) lp.leftMargin = u(8);
            dots.addView(d, lp);
        }
    }

    void refreshGlass(View v) {
        if (v instanceof Tile) ((Tile) v).refreshGlass();
        v.invalidate();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) refreshGlass(g.getChildAt(i));
        }
    }

    // ------------------------------------------------------------------ météo dans le paysage

    private final Runnable weatherTick = new Runnable() {
        @Override public void run() { updateWeatherFx(); refreshGreeting(); h.postDelayed(this, 30 * 60_000L); }
    };

    void updateWeatherFx() {
        int src = prefs.source();
        boolean landscape = src == Prefs.SRC_LUMEN || src == Prefs.SRC_ANIM || wall.usingFallback();
        // décor de saison : neige douce sur les paysages pendant les fêtes
        final int seasonal = landscape && prefs.seasonal() && Sky.festive(java.util.Calendar.getInstance())
                ? WeatherFx.SNOW : WeatherFx.NONE;
        if (!prefs.weatherFx() || !weather.hasCity() || !landscape) { fx.setKind(seasonal); return; }
        weather.current(new Weather.Callback() {
            @Override public void onWeather(String line) {
                int k = WeatherFx.kindFor(weather.lastCode());
                fx.setKind(k != WeatherFx.NONE ? k : seasonal);
            }
        });
    }

    // ------------------------------------------------------------------ construction

    int u(float px) { return Math.round(px * unit); }

    /** Facteur de taille du texte choisi dans Réglages › Accueil › Lisibilité. */
    float textScale = 1f;
    boolean highContrast;

    TextView text(float px, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * unit * textScale);
        // contraste renforcé : les textes secondaires (gris translucides) deviennent presque opaques
        if (highContrast && (color >>> 24) < 0xE0 && (color & 0xFFFFFF) == 0xFFFFFF) color = 0xF2FFFFFF;
        t.setTypeface(Fonts.get(family, Typeface.NORMAL));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    static FrameLayout.LayoutParams wrap(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);

        wallLayer = new FrameLayout(this);
        wallLayer.setScaleX(1.04f); // marge pour l'effet de profondeur (le fond glisse sans montrer ses bords)
        wallLayer.setScaleY(1.04f);
        root.addView(wallLayer, match());
        wall = new Wall(this, prefs, online, wallLayer);
        wall.setListener(this);
        fx = new WeatherFx(this, unit);
        fx.setAlpha(0f);
        root.addView(fx, match());
        glow = new Glow(this);
        root.addView(glow, match());

        // ---- accueil
        homeLayer = new FrameLayout(this);
        homeLayer.setClipChildren(false);
        root.addView(homeLayer, match());

        scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xB804040A, 0x4004040A, 0x0004040A}));
        homeLayer.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u(520), Gravity.BOTTOM));

        // lignes de favoris, ancrées en bas et centrées
        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsBox.setClipChildren(false);
        rowsBox.setClipToPadding(false);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        rl.bottomMargin = u(82);
        homeLayer.addView(rowsBox, rl);
        shelf = new TopShelf(this);

        focusLabel = text(28, "sans-serif-medium", 0xF2FFFFFF);
        focusLabel.setShadowLayer(u(10), 0, 0, 0x66000000);
        focusLabel.setGravity(Gravity.CENTER_HORIZONTAL);

        // bandeau d'accueil (une ligne) au-dessus des favoris
        greet = text(24, "sans-serif", 0xD9FFFFFF);
        greet.setShadowLayer(u(10), 0, 0, 0x66000000);
        greet.setGravity(Gravity.CENTER_HORIZONTAL);
        greet.setSingleLine(true);
        greet.setEllipsize(TextUtils.TruncateAt.END);
        greet.setPadding(u(112), 0, u(112), 0);

        // boutons ronds en haut à droite, à gauche de l'horloge
        topButtons = new LinearLayout(this);
        topButtons.setOrientation(LinearLayout.HORIZONTAL);
        topButtons.setClipChildren(false);
        topButtons.setClipToPadding(false);
        topButtons.setPadding(u(12), u(12), u(12), u(12));
        int bs = u(84);
        Tile amb = Tile.button(this, Glyph.MOON, "Ambiances", bs);
        amb.setFocusListener(homeFocus);
        amb.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openAmbiance(); } });
        Tile inputs = Tile.button(this, Glyph.INPUT, "Entrées", bs);
        inputs.setFocusListener(homeFocus);
        inputs.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { InputsPanel.openInputs(MainActivity.this); } });
        Tile house = Tile.button(this, Glyph.HOME, "Maison", bs);
        house.setFocusListener(homeFocus);
        house.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { HomePanel.open(MainActivity.this); } });
        Tile all = Tile.button(this, Glyph.GRID, "Toutes les applications", bs);
        all.setFocusListener(homeFocus);
        all.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openApps(); } });
        Tile set = Tile.button(this, Glyph.SLIDERS, "Réglages", bs);
        set.setFocusListener(homeFocus);
        set.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { SettingsPanel.openSettings(MainActivity.this); } });
        for (Tile b : new Tile[]{amb, inputs, house, all, set}) {
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.leftMargin = u(18);
            topButtons.addView(b, bl);
        }
        FrameLayout.LayoutParams tbl = wrap(Gravity.TOP | Gravity.END);
        tbl.topMargin = u(56);
        tbl.rightMargin = u(420);
        homeLayer.addView(topButtons, tbl);

        // ---- légende du fond, en haut à gauche
        captionBox = new LinearLayout(this);
        captionBox.setOrientation(LinearLayout.VERTICAL);
        captionTitle = text(22, "sans-serif-medium", 0xC8FFFFFF);
        captionTitle.setShadowLayer(u(8), 0, 0, 0x55000000);
        captionTitle.setMaxLines(1);
        captionTitle.setEllipsize(TextUtils.TruncateAt.END);
        captionBox.addView(captionTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        captionCredit = text(18, "sans-serif", 0x8CFFFFFF);
        captionCredit.setShadowLayer(u(8), 0, 0, 0x55000000);
        captionCredit.setMaxLines(1);
        captionCredit.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams cl2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl2.topMargin = u(8);
        captionBox.addView(captionCredit, cl2);
        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = u(14);
        captionBox.addView(dots, dl);
        FrameLayout.LayoutParams capl = new FrameLayout.LayoutParams(u(880), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        capl.leftMargin = u(112);
        capl.topMargin = u(72);
        root.addView(captionBox, capl);

        // ---- horloge, en haut à droite
        clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("H:mm");
        time.setFormat12Hour("h:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, 56 * unit);
        time.setTypeface(Fonts.get("sans-serif-light", Typeface.NORMAL));
        time.setTextColor(0xF5FFFFFF);
        time.setIncludeFontPadding(false);
        time.setShadowLayer(u(12), 0, 0, 0x55000000);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * unit);
        date.setTypeface(Fonts.get("sans-serif", Typeface.NORMAL));
        date.setTextColor(0xB3FFFFFF);
        date.setShadowLayer(u(8), 0, 0, 0x55000000);
        clockBox.addView(time);
        LinearLayout.LayoutParams dtl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dtl.topMargin = u(8);
        clockBox.addView(date, dtl);
        FrameLayout.LayoutParams ckl = wrap(Gravity.TOP | Gravity.END);
        ckl.topMargin = u(64);
        ckl.rightMargin = u(112);
        root.addView(clockBox, ckl);
        clockBox.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) topButtons.getLayoutParams();
                int want = clockBox.getVisibility() == View.VISIBLE ? u(112) + (r - l) + u(36) : u(100);
                if (p.rightMargin != want) { p.rightMargin = want; topButtons.setLayoutParams(p); }
            }
        });

        // ---- page « Toutes les applications »
        appsLayer = new FrameLayout(this);
        appsLayer.setVisibility(View.GONE);
        appsBg = new ImageView(this);
        appsBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        appsLayer.addView(appsBg, match());
        View tint = new View(this);
        tint.setBackgroundColor(0x9905060A);
        appsLayer.addView(tint, match());
        root.addView(appsLayer, root.indexOfChild(captionBox), match());

        // ---- mode veille
        ambient = new AmbientView(this, unit, weather);
        ambient.setVisibility(View.GONE);
        root.addView(ambient, match());
        veilleWall = new Wall(this, prefs, online, ambient.wallLayer());
        veilleWall.setVeilleMode(true);
        veilleWall.setListener(new Wall.Listener() {
            @Override public void onWallShown(WallItem item, Bitmap backdrop) { if (ambientOn) ambient.setCaption(item); }
        });

        // ---- notification flottante
        notice = text(24, "sans-serif-medium", 0xFF111216);
        GradientDrawable nb = new GradientDrawable();
        nb.setColor(0xF5F5F5F7);
        nb.setCornerRadius(u(40));
        notice.setBackground(nb);
        notice.setPadding(u(36), u(18), u(36), u(18));
        notice.setAlpha(0f);
        notice.setElevation(u(40));
        FrameLayout.LayoutParams nl = wrap(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        nl.bottomMargin = u(40);
        root.addView(notice, nl);

        perf = new Perf.Overlay(this);
        perf.setVisibility(View.GONE);
        FrameLayout.LayoutParams pl = wrap(Gravity.TOP | Gravity.START);
        pl.leftMargin = u(24);
        pl.topMargin = u(24);
        root.addView(perf, pl);

        setContentView(root);
        intro();
        // première image affichée : on mesure le démarrage, puis on lance le reste
        root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                root.post(new Runnable() { @Override public void run() { Perf.onFirstFrame(); } });
                return true;
            }
        });
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                Glass.setScreen(loc[0] + v.getWidth(), loc[1] + v.getHeight());
                if (r - l != or - ol || b - t != ob - ot) refreshGlass(root);
            }
        });
        showSplash();
    }

    /** Écran de démarrage : le mot « lumen » apparaît puis s'efface pendant que le fond arrive. */
    private void showSplash() {
        final FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(Color.BLACK);
        final TextView word = text(84, "sans-serif-thin", 0xFFFFFFFF);
        word.setText("lumen");
        word.setLetterSpacing(0.35f);
        word.setAlpha(0f);
        splash.addView(word, wrap(Gravity.CENTER));
        root.addView(splash, match());
        word.setScaleX(0.96f);
        word.setScaleY(0.96f);
        word.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(new DecelerateInterpolator()).withEndAction(new Runnable() {
            @Override public void run() {
                word.animate().alpha(0f).setStartDelay(500).setDuration(600).start();
                splash.animate().alpha(0f).setStartDelay(800).setDuration(900).withEndAction(new Runnable() {
                    @Override public void run() { root.removeView(splash); }
                }).start();
            }
        }).start();
    }

    // ------------------------------------------------------------------ favoris (accueil)

    private void ensureDefaultFavorites() {
        if (prefs.hasFavorites()) return;
        List<String> keys = new ArrayList<>();
        for (String p : DEFAULT_FAVORITES) {
            AppRepo.App a = repo.findByPackage(p);
            if (a != null && keys.size() < 7) keys.add(a.key);
        }
        for (AppRepo.App a : repo.all()) {
            if (keys.size() >= 5) break;
            if (a.leanback && !a.isInput() && !keys.contains(a.key)) keys.add(a.key);
        }
        prefs.setFavorites(keys);
    }

    /** Une seule fois : les entrées HDMI de la TV rejoignent l'accueil (on peut les retirer ensuite). */
    private void addInputsOnce() {
        if (prefs.flag("inputs_home_v1")) return;
        prefs.setFlag("inputs_home_v1");
        List<String> f = prefs.favorites();
        boolean changed = false;
        for (AppRepo.App a : repo.all())
            if (a.isInput() && !f.contains(a.key)) { f.add(a.key); changed = true; }
        if (changed) prefs.setFavorites(f);
    }

    private List<String> visibleFavorites() {
        Set<String> hidden = prefs.hiddenApps();
        List<String> keys = prefs.favorites(), ok = new ArrayList<>();
        for (String k : keys) if (repo.get(k) != null && !hidden.contains(k)) ok.add(k);
        return ok;
    }

    boolean banner() { return prefs.tileStyle() == Prefs.TILE_BANNER; }
    boolean wordTiles() { return prefs.tileStyle() == Prefs.TILE_WORD; }

    private void buildRow(String focusKey) {
        dirtyRow = false;
        rowsBox.removeAllViews();
        rows.clear();
        rowScrolls.clear();
        if (focusLabel.getParent() != null) ((ViewGroup) focusLabel.getParent()).removeView(focusLabel);
        if (shelf.view.getParent() != null) ((ViewGroup) shelf.view.getParent()).removeView(shelf.view);
        if (greet.getParent() != null) ((ViewGroup) greet.getParent()).removeView(greet);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.bottomMargin = u(18);
        rowsBox.addView(greet, gl);
        refreshGreeting();
        LinearLayout.LayoutParams shl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shl.bottomMargin = u(6);
        rowsBox.addView(shelf.view, shl); // « Reprendre » au-dessus des applis
        LinearLayout.LayoutParams fll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fll.bottomMargin = u(4);
        rowsBox.addView(focusLabel, fll);
        focusLabel.setVisibility(banner() ? View.VISIBLE : View.GONE);

        // taille des vignettes calculée pour que N applis tiennent exactement sur une ligne
        int cols = prefs.homeCols(), maxRows = prefs.homeRows();
        int screenW = root.getWidth() > 0 ? root.getWidth() : u(1920);
        int gap = u(cols >= 7 ? 28 : 36);
        int tw = (screenW - u(224) - gap * (cols - 1)) / cols, th = tw * 9 / 16;

        List<String> favs = visibleFavorites();
        int nRows = Math.max(1, Math.min(maxRows, (favs.size() + cols - 1) / cols));
        Tile toFocus = null;
        for (int r = 0; r < nRows; r++) {
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setPadding(u(112), u(26), u(112), u(26));
            HorizontalScrollView hs = new HorizontalScrollView(this) {
                @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
                    super.onScrollChanged(l, t, ol, ot);
                    refreshRowsGlass();
                }
            };
            hs.setHorizontalScrollBarEnabled(false);
            hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
            hs.setClipChildren(false);
            hs.setClipToPadding(false);
            hs.setFillViewport(true); // permet de centrer une ligne incomplète
            hs.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            rowsBox.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            rows.add(row);
            rowScrolls.add(hs);
            // la dernière ligne reçoit le surplus éventuel (elle défile)
            int from = r * cols, to = r == nRows - 1 ? favs.size() : Math.min(favs.size(), from + cols);
            for (int i = from; i < to; i++) {
                String k = favs.get(i);
                final AppRepo.App app = repo.get(k);
                final Tile t = Tile.app(this, app, tw, th, banner(), prefs.showIcons(), prefs.tintTiles(), false, wordTiles());
                t.setFocusListener(homeFocus);
                t.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { if (movingTile == null) launchAnimated(app, t); else stopMoving(); }
                });
                t.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { if (movingTile == null) openAppMenu(t, true); return true; }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i < to - 1) lp.rightMargin = gap;
                row.addView(t, lp);
                if (k.equals(focusKey)) toFocus = t;
            }
        }
        if (toFocus != null) toFocus.requestFocus();
        else if (overlay == null && !appsOpen) focusFirst();
    }

    private void focusFirst() {
        Tile usual = usualNow();
        for (LinearLayout r : rows) for (int i = 0; i < r.getChildCount(); i++)
            if (r.getChildAt(i) instanceof Tile) ((Tile) r.getChildAt(i)).setUsual(r.getChildAt(i) == usual);
        if (usual != null) { usual.requestFocus(); return; }
        if (!rows.isEmpty() && rows.get(0).getChildCount() > 0) rows.get(0).getChildAt(0).requestFocus();
        else if (topButtons.getChildCount() > 0) topButtons.getChildAt(0).requestFocus();
    }

    /** Le favori le plus ouvert à cette heure-ci (habitudes locales), pour le sélectionner d'emblée. */
    private Tile usualNow() {
        if (!prefs.habitsOn()) return null;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        List<String> top = prefs.habits().top(hour, 1, 3);
        if (top.isEmpty()) return null;
        for (LinearLayout r : rows)
            for (int i = 0; i < r.getChildCount(); i++) {
                View v = r.getChildAt(i);
                if (v instanceof Tile && ((Tile) v).tag instanceof AppRepo.App && top.get(0).equals(((AppRepo.App) ((Tile) v).tag).key)) return (Tile) v;
            }
        return null;
    }

    private void refreshRowsGlass() { for (LinearLayout r : rows) refreshGlass(r); }

    private final Tile.FocusListener homeFocus = new Tile.FocusListener() {
        @Override public void onTileFocus(final Tile t, boolean focused) {
            if (focused) glowOn(t);
            if (focused) parallax(t);
            if (focused) shelf.focusApp(!appsOpen && t.tag instanceof AppRepo.App ? (AppRepo.App) t.tag : null);
            if (!focused || !banner()) return;
            final String s = t.title;
            if (TextUtils.equals(focusLabel.getText(), s)) return;
            // le texte change tout de suite : il ne peut plus rester celui d'une autre appli
            focusLabel.animate().cancel();
            focusLabel.setText(s);
            focusLabel.setAlpha(0.35f);
            focusLabel.animate().alpha(1f).setDuration(200).start();
        }
    };

    /**
     * Effet de profondeur : le fond glisse de quelques pixels à l'opposé de la sélection,
     * comme un paysage vu à travers une vitre.
     */
    private void parallax(final View t) {
        t.post(new Runnable() {
            @Override public void run() {
                if (ambientOn || root.getWidth() == 0) return;
                int[] loc = new int[2];
                t.getLocationOnScreen(loc);
                float fx = (loc[0] + t.getWidth() / 2f) / root.getWidth() - 0.5f;
                float fy = (loc[1] + t.getHeight() / 2f) / root.getHeight() - 0.5f;
                wallLayer.animate().translationX(-fx * u(34)).translationY(-fy * u(18)).setDuration(1100)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
            }
        });
    }

    /** Lueur de la couleur de l'appli sous la vignette sélectionnée. */
    private void glowOn(final Tile t) {
        if (!prefs.appGlow() || appsOpen || ambientOn || !(t.tag instanceof AppRepo.App)) { glow.fade(); return; }
        final int color = ((AppRepo.App) t.tag).color(getPackageManager());
        t.postDelayed(new Runnable() {
            @Override public void run() {
                if (!t.isFocused()) return;
                int[] loc = new int[2];
                t.getLocationOnScreen(loc);
                glow.focus(loc[0] + t.getWidth() / 2f, color);
            }
        }, 120); // laisse la rangée finir de défiler
    }

    private String focusedKey() {
        View f = getCurrentFocus();
        if (f instanceof Tile && ((Tile) f).tag instanceof AppRepo.App) return ((AppRepo.App) ((Tile) f).tag).key;
        return null;
    }

    /** La vignette s'agrandit jusqu'à remplir l'écran, puis l'appli s'ouvre. */
    void launchAnimated(final AppRepo.App app, final Tile from) {
        whenUnlocked(app, new Runnable() { @Override public void run() { launchUnlocked(app, from); } });
    }

    /** Lance « r » tout de suite, ou après le code si l'appli est verrouillée. */
    void whenUnlocked(AppRepo.App app, Runnable r) {
        if (prefs.isLocked(app.key)) PinDialog.ask(this, "Code pour " + app.label, r);
        else r.run();
    }

    private void launchUnlocked(final AppRepo.App app, Tile from) {
        Sounds.get(this).open();
        if (zoomView != null) { root.removeView(zoomView); zoomView = null; }
        int sw = root.getWidth(), sh = root.getHeight();
        if (from == null || sw == 0) { launch(app); return; }
        int[] loc = new int[2];
        from.card.getLocationOnScreen(loc);
        float fw = from.card.getWidth() * from.getScaleX(), fh = from.card.getHeight() * from.getScaleY();
        FrameLayout z = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        int color = app.color(getPackageManager());
        bg.setColor(color);
        z.setBackground(bg);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon(getPackageManager()));
        icon.setAlpha(0f);
        z.addView(icon, new FrameLayout.LayoutParams(u(150), u(150), Gravity.CENTER));
        root.addView(z, match());
        z.setPivotX(0);
        z.setPivotY(0);
        z.setScaleX(fw / sw);
        z.setScaleY(fh / sh);
        z.setTranslationX(loc[0]);
        z.setTranslationY(loc[1]);
        z.setElevation(u(60));
        z.animate().scaleX(1f).scaleY(1f).translationX(0).translationY(0).setDuration(380)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();
        icon.setScaleX(0.6f);
        icon.setScaleY(0.6f);
        icon.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(300).start();
        zoomView = z;
        h.postDelayed(new Runnable() {
            @Override public void run() {
                launch(app);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        }, 420);
        // filet de sécurité si l'appli ne s'ouvre pas
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (zoomView != null && hasWindowFocus()) { root.removeView(zoomView); zoomView = null; }
            }
        }, 4000);
    }

    void launch(AppRepo.App app) {
        prefs.addRecent(app.key);
        prefs.recordHabit(app.key);
        try {
            startActivity(app.launchIntent());
        } catch (ActivityNotFoundException | SecurityException e) {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(app.pkg);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(app.pkg);
            try {
                if (i == null) throw new ActivityNotFoundException();
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e2) {
                showNotice("Impossible d'ouvrir " + app.label);
            }
        }
    }

    /** Travaux lancés juste après l'affichage : autorisations, mise à jour, « Reprendre », téléchargements. */
    private final Runnable afterShown = new Runnable() {
        @Override public void run() {
            if (isFinishing()) return;
            askWatchNextOnce();
            updater.check(false);                // une fois par jour au plus
            shelf.reload();                      // on revient peut-être d'un épisode
            for (String t : Library.themesFor(prefs.source())) online.refreshIfNeeded(t, false);
            refreshGreeting();
            if (prefs.veilleSource() >= 0) for (String t : Library.themesFor(Library.veilleSource(prefs))) online.refreshIfNeeded(t, false);
            if (CrashLog.safeModeJustStarted) {
                CrashLog.safeModeJustStarted = false;
                showNotice("Lumen a redémarré en mode sans échec après des erreurs");
            }
        }
    };

    // ------------------------------------------------------------------ mise à jour automatique

    /** Une mise à jour vérifiée est prête : on le dit, puis Android demande confirmation (une fois par version). */
    private void updateStateChanged() {
        for (Runnable r : new ArrayList<>(rowRefreshers)) r.run();
        final String v = updater.readyVersion();
        if (v.isEmpty() || v.equals(prefs.updatePrompted())) return;
        showNotice("Mise à jour " + v + " prête");
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (overlay != null || ambientOn || ambiance != null || !hasWindowFocus()) return; // on redemandera au prochain retour
                prefs.setUpdatePrompted(v);
                updater.install();
            }
        }, 2500);
    }

    // ------------------------------------------------------------------ « Reprendre » : autorisation

    private static final int REQ_WATCH_NEXT = 41;

    /** Demande une seule fois l'accès aux « en cours » des applis (autorisation Android « programmes TV »). */
    private void askWatchNextOnce() {
        if (Build.VERSION.SDK_INT < 26 || !prefs.topShelf() || WatchNext.granted(this) || prefs.watchNextAsked()) return;
        prefs.setWatchNextAsked(true);
        try { requestPermissions(new String[]{WatchNext.PERMISSION}, REQ_WATCH_NEXT); } catch (Exception ignored) { }
    }

    /** Depuis les réglages : redemande, ou ouvre la page d'autorisations de Lumen si Android ne redemande plus. */
    void requestWatchNext() {
        if (Build.VERSION.SDK_INT < 26) { showNotice("Les épisodes en cours demandent Android 8 ou plus récent"); return; }
        if (WatchNext.granted(this)) { shelf.reload(); return; }
        if (Build.VERSION.SDK_INT >= 23 && shouldShowRequestPermissionRationale(WatchNext.PERMISSION) || !prefs.watchNextAsked()) {
            prefs.setWatchNextAsked(true);
            try { requestPermissions(new String[]{WatchNext.PERMISSION}, REQ_WATCH_NEXT); return; } catch (Exception ignored) { }
        }
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) { showNotice("Autorisation impossible à demander sur cette TV"); }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_WATCH_NEXT) shelf.reload();
    }

    void showNotice(String s) {
        notice.setText(s);
        notice.animate().cancel();
        notice.setTranslationY(u(20));
        notice.animate().alpha(1f).translationY(0).setStartDelay(0).setDuration(240).withEndAction(new Runnable() {
            @Override public void run() { notice.animate().alpha(0f).setStartDelay(2200).setDuration(400).start(); }
        }).start();
    }

    // ------------------------------------------------------------------ déplacement d'un favori

    void startMoving(Tile t) {
        movingTile = t;
        t.setMoving(true);
        t.requestFocus();
    }

    private void stopMoving() {
        if (movingTile == null) return;
        movingTile.setMoving(false);
        movingTile = null;
    }

    /** Déplace le favori dans l'ordre global (il peut changer de ligne). */
    private void moveBy(int delta) {
        if (movingTile == null || !(movingTile.tag instanceof AppRepo.App)) return;
        String key = ((AppRepo.App) movingTile.tag).key;
        List<String> visible = visibleFavorites();
        int idx = visible.indexOf(key), target = idx + delta;
        if (idx < 0 || target < 0 || target >= visible.size()) return;
        String other = visible.get(target);
        List<String> all = prefs.favorites();
        int a = all.indexOf(key), b = all.indexOf(other);
        if (a < 0 || b < 0) return;
        all.set(a, other);
        all.set(b, key);
        prefs.setFavorites(all);
        buildRow(key);
        View f = getCurrentFocus();
        if (f instanceof Tile) { movingTile = (Tile) f; movingTile.setMoving(true); }
    }

    // ------------------------------------------------------------------ télécommande : gestes, veille

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (ambiance != null) {
            if (ambiance.onKey(e)) return true;
            return super.dispatchKeyEvent(e); // navigation dans le menu des ambiances
        }
        if (ambientOn) {
            if (!down) exitAmbient(true);
            return true; // le premier appui quitte seulement le mode veille
        }
        if (idle) {
            if (!down) wake();
            return true; // le premier appui réveille seulement l'interface
        }
        if (down) wake();
        if (movingTile != null && down) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: moveBy(-1); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: moveBy(1); return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN: return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER: stopMoving(); return true;
            }
        }
        if (overlay == null && down && e.getRepeatCount() == 0) {
            View f = getCurrentFocus();
            // Geste bas sur l'accueil → toutes les applis
            if (!appsOpen && code == KeyEvent.KEYCODE_DPAD_DOWN && f != null && isInside(f, homeLayer)) {
                View next = f.focusSearch(View.FOCUS_DOWN);
                if (next == null || !isInside(next, homeLayer)) { openApps(); return true; }
            }
            // Geste gauche depuis la première vignette d'une ligne → panneau « Reprendre »
            if (!appsOpen && code == KeyEvent.KEYCODE_DPAD_LEFT && f instanceof Tile && isInside(f, rowsBox)) {
                View next = f.focusSearch(View.FOCUS_LEFT);
                if (next == null || !isInside(next, rowsBox)) { ResumePanel.openResume(MainActivity.this); return true; }
            }
            // Geste haut depuis la première ligne des applis → accueil
            if (appsOpen && code == KeyEvent.KEYCODE_DPAD_UP && f != null) {
                View next = f.focusSearch(View.FOCUS_UP);
                if (next == null || !isInside(next, appsLayer)) { closeApps(true); return true; }
            }
        }
        if (resumeOpen && down && code == KeyEvent.KEYCODE_DPAD_RIGHT) { closeOverlay(true); return true; }
        // ◀ dans une sous-page des réglages : retour à la page précédente
        if (down && code == KeyEvent.KEYCODE_DPAD_LEFT && overlay != null && e.getRepeatCount() == 0
                && SettingsPanel.inSubPage() && !(getCurrentFocus() instanceof android.widget.EditText)) { SettingsPanel.back(this); return true; }
        if (!down && code == KeyEvent.KEYCODE_MENU && overlay == null) { SettingsPanel.openSettings(MainActivity.this); return true; }
        return super.dispatchKeyEvent(e);
    }

    static boolean isInside(View v, View parent) {
        while (v != null) {
            if (v == parent) return true;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    private final Runnable idleRun = new Runnable() {
        @Override public void run() {
            if (overlay != null || movingTile != null || appsOpen || ambiance != null || !prefs.idleFade()) return;
            idle = true;
            homeLayer.animate().alpha(0f).setDuration(1800).start();
            clockBox.animate().alpha(0.6f).setDuration(1800).start();
            glow.fade();
        }
    };

    /** Bandeau d'accueil : salutation, météo si une ville est réglée, dernier épisode en cours. */
    void refreshGreeting() {
        if (greet == null) return;
        if (!prefs.greeting()) { greet.setVisibility(View.GONE); return; }
        greet.setVisibility(View.VISIBLE);
        weather.current(new Weather.Callback() {
            @Override public void onWeather(String line) {
                java.util.Calendar now = java.util.Calendar.getInstance();
                String time = prefs.showClock() ? "" : android.text.format.DateFormat.format("H:mm", now).toString();
                WatchNext.Entry e = shelf.latest();
                String s = Greeting.line(now.get(java.util.Calendar.HOUR_OF_DAY), time, line, e == null ? null : e.title);
                if (s.contentEquals(greet.getText())) return;
                greet.setText(s);
                greet.setAlpha(0f);
                greet.animate().alpha(1f).setDuration(600).start();
            }
        });
    }

    /** Démarrage en cascade, en moins d'une seconde : l'heure, puis le fond, puis la légende et les applis. */
    private void intro() {
        final android.view.animation.DecelerateInterpolator di = new android.view.animation.DecelerateInterpolator(1.6f);
        clockBox.setAlpha(0f);
        wallLayer.setAlpha(0f);
        captionBox.setAlpha(0f);
        homeLayer.setAlpha(0f);
        homeLayer.setTranslationY(u(28));
        clockBox.animate().alpha(1f).setStartDelay(60).setDuration(320).setInterpolator(di).start();
        wallLayer.animate().alpha(1f).setStartDelay(180).setDuration(520).setInterpolator(di).start();
        captionBox.animate().alpha(1f).setStartDelay(380).setDuration(420).setInterpolator(di).start();
        homeLayer.animate().alpha(1f).translationY(0).setStartDelay(420).setDuration(480).setInterpolator(di).start();
    }

    private void wake() {
        h.removeCallbacks(idleRun);
        if (idle) {
            idle = false;
            homeLayer.animate().alpha(1f).setDuration(350).start();
            clockBox.animate().alpha(appsOpen ? 0f : 1f).setDuration(350).start();
        }
        h.postDelayed(idleRun, IDLE_MS);
        h.removeCallbacks(ambientRun);
        int m = prefs.ambientMinutes();
        if (m > 0) h.postDelayed(ambientRun, m * 60_000L);
    }

    // ------------------------------------------------------------------ ambiances

    void openAmbiance() {
        if (ambiance != null) return;
        closeOverlay(false);
        h.removeCallbacks(idleRun);
        h.removeCallbacks(ambientRun);
        glow.fade();
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        appsLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        ambiance = AmbiancePanel.open(this);
    }

    /** Ferme les ambiances, ouvre le choix des vidéos, puis revient aux ambiances. */
    void pickCosyVideos() {
        closeAmbiance();
        VideoPicker.open(this, Online.SCOPE_COSY, new Runnable() {
            @Override public void run() { h.post(new Runnable() { @Override public void run() { openAmbiance(); } }); }
        });
    }

    void closeAmbiance() {
        if (ambiance == null) return;
        ambiance.close();
        ambiance = null;
        appsLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (!appsOpen) homeLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (appsOpen) focusFirstApp(); else focusFirst();
        wake();
    }

    // ------------------------------------------------------------------ mode veille

    private final Runnable ambientRun = new Runnable() {
        @Override public void run() {
            if (overlay != null || movingTile != null || ambientOn || ambiance != null) return;
            enterAmbient();
        }
    };

    /** Fond propre au mode veille (images, sans vidéo) ; sinon le fond de l'accueil continue derrière. */
    private Wall veilleWall;
    private boolean veilleOwn;

    void enterAmbient() {
        ambientOn = true;
        h.removeCallbacks(idleRun);
        WallItem cur = wall.current();
        ambient.setCaption(cur);
        veilleOwn = prefs.veilleSource() != Prefs.VEILLE_LIVE;
        if (veilleOwn) {
            wall.stop();               // plus de vidéo derrière la veille
            veilleWall.startWith(cur); // même image que l'accueil si c'en est une
        }
        ambient.start();
        ambient.setVisibility(View.VISIBLE);
        ambient.setAlpha(0f);
        ambient.animate().alpha(1f).setDuration(2000).start();
        for (View v : new View[]{homeLayer, appsLayer, captionBox, clockBox}) v.animate().alpha(0f).setDuration(1500).start();
        glow.fade();
    }

    private void exitAmbient(boolean animate) {
        ambientOn = false;
        if (animate) Sounds.get(this).wake(); // léger fondu sonore au réveil
        idle = false;
        ambient.stop();
        if (veilleOwn) {
            veilleOwn = false;
            veilleWall.stop();
            if (resumedNow) wall.start();
        }
        long d = animate ? 400 : 0;
        ambient.animate().alpha(0f).setDuration(d).withEndAction(new Runnable() {
            @Override public void run() {
                if (ambientOn) return;
                ambient.setVisibility(View.GONE);
                veilleWall.release();
            }
        }).start();
        clockBox.animate().alpha(appsOpen ? 0f : 1f).setDuration(d).start();
        if (appsOpen) appsLayer.animate().alpha(1f).setDuration(d).start();
        else {
            homeLayer.animate().alpha(1f).setDuration(d).start();
            captionBox.animate().alpha(1f).setDuration(d).start();
        }
        wake();
    }

    // ------------------------------------------------------------------ page « Toutes les applications »


    /** Signale que la page Applications doit être refaite ; elle l'est en arrière-plan, pas à l'ouverture. */
    void markAppsDirty() {
        dirtyApps = true;
        if (!appsOpen) { h.removeCallbacks(prebuildApps); h.postDelayed(prebuildApps, 700); }
    }

    /** Construit la page Applications pendant qu'elle est cachée : l'ouverture est alors immédiate. */
    private final Runnable prebuildApps = new Runnable() {
        @Override public void run() {
            if (appsOpen || (appsPage.built() && !dirtyApps)) return;
            if (overlay != null || ambientOn) { h.postDelayed(this, 1500); return; }
            buildApps(null);
            appsLayer.setVisibility(View.INVISIBLE); // mesurée et dessinée une fois, sans être vue
            appsLayer.setAlpha(0f);
            h.postDelayed(new Runnable() {
                @Override public void run() { if (!appsOpen) appsLayer.setVisibility(View.GONE); }
            }, 400);
        }
    };

    private void openApps() {
        if (appsOpen) return;
        appsOpen = true;
        focusBeforeApps = getCurrentFocus();
        h.removeCallbacks(prebuildApps);
        if (Glass.backdrop() != null) appsBg.setImageBitmap(Glass.backdrop());
        LivingScene.paused = true;  // le fond est caché par la page : on n'anime plus rien derrière
        fx.setRunning(false);
        if (!appsPage.built() || dirtyApps || appsPage.stale()) buildApps(null);  // sinon : déjà prête
        else appsPage.rewind();
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        homeLayer.animate().translationY(-u(60)).alpha(0f).setDuration(180).start();
        glow.fade();
        captionBox.animate().alpha(0f).setDuration(150).start();
        clockBox.animate().alpha(0f).setDuration(150).start(); // l'horloge de l'accueil ne doit pas chevaucher la recherche
        appsLayer.setVisibility(View.VISIBLE);
        appsLayer.setAlpha(0f);
        appsLayer.setTranslationY(u(60));
        appsLayer.animate().alpha(1f).translationY(0).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        focusFirstApp();
    }

    private void closeApps(boolean animate) {
        if (!appsOpen) return;
        appsOpen = false;
        appsPage.reset();
        LivingScene.paused = false;
        wall.resumeLiving();
        fx.setRunning(true);
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        homeLayer.animate().translationY(0).alpha(1f).setDuration(animate ? 300 : 0).start();
        captionBox.animate().alpha(1f).setDuration(300).start();
        clockBox.animate().alpha(1f).setDuration(300).start();
        appsLayer.animate().alpha(0f).translationY(u(120)).setDuration(animate ? 260 : 0).withEndAction(new Runnable() {
            @Override public void run() { if (!appsOpen) appsLayer.setVisibility(View.GONE); }
        }).start();
        if (dirtyRow) buildRow(null);
        if (focusBeforeApps != null && focusBeforeApps.isAttachedToWindow()) focusBeforeApps.requestFocus();
        else focusFirst();
        wake();
    }

    void focusFirstApp() {
        View v = appsLayer.findViewWithTag("first");
        if (v != null) v.requestFocus();
    }

    /** Page Applications (rangées par catégorie, recherche) : voir AppsPage. */
    void buildApps(String focusKey) { appsPage.build(focusKey); }

    // ------------------------------------------------------------------ panneaux (verre)

    FrameLayout openOverlay(Runnable onClosed) {
        closeOverlay(false);
        focusBeforeOverlay = getCurrentFocus();
        onOverlayClosed = onClosed;
        h.removeCallbacks(idleRun);
        FrameLayout o = new FrameLayout(this);
        o.setClipChildren(false);
        View dim = new View(this);
        dim.setBackgroundColor(highContrast ? 0xB304040A : 0x6604040A);
        o.addView(dim, match());
        o.setAlpha(0f);
        root.addView(o, root.indexOfChild(notice), match());
        o.animate().alpha(1f).setDuration(220).start();
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        appsLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        overlay = o;
        return o;
    }

    void closeOverlay(boolean animate) {
        if (overlay == null) return;
        final FrameLayout o = overlay;
        overlay = null;
        appsLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (!appsOpen) homeLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        Runnable cb = onOverlayClosed;
        onOverlayClosed = null;
        if (cb != null) cb.run();
        String key = focusBeforeOverlay instanceof Tile && ((Tile) focusBeforeOverlay).tag instanceof AppRepo.App
                ? ((AppRepo.App) ((Tile) focusBeforeOverlay).tag).key : null;
        boolean rebuilt = false;
        if (dirtyRow) { buildRow(appsOpen ? null : key); rebuilt = !appsOpen; }
        if (dirtyApps && appsOpen) { buildApps(key); rebuilt = true; }
        if (!rebuilt) {
            if (focusBeforeOverlay != null && focusBeforeOverlay.isAttachedToWindow()) focusBeforeOverlay.requestFocus();
            else if (appsOpen) focusFirstApp(); else focusFirst();
        }
        if (animate) {
            o.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                @Override public void run() { root.removeView(o); }
            }).start();
        } else {
            root.removeView(o);
        }
        wake();
    }

    /** Carte de verre flottante. */
    LinearLayout glassCard(float radiusPx) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        final float radius = u(radiusPx);
        card.setBackground(new Glass.Surface(card, radius, highContrast ? 0xD90C0D12 : 0x2E14161E));
        card.setElevation(u(40));
        // le contenu (listes qui défilent) reste dans les bords arrondis
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        return card;
    }

    interface Value { String get(); }

    /** Rafraîchit toutes les valeurs affichées d'un panneau (un réglage peut en influencer un autre). */
    final List<Runnable> rowRefreshers = new ArrayList<>();

    LinearLayout listRow(String label, final Value value, final View.OnClickListener click) {
        final LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setSoundEffectsEnabled(false);
        r.setPadding(u(32), 0, u(32), 0);
        final GradientDrawable bg = new GradientDrawable();
        // arrondi concentrique avec les cartes (rayon 48, marge 24) : les coins suivent ceux du panneau
        bg.setCornerRadius(u(24));
        bg.setColor(0x00FFFFFF);
        r.setBackground(bg);
        final TextView l = text(28, "sans-serif", 0xEBFFFFFF);
        l.setText(label);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView v = text(24, "sans-serif", 0x8CFFFFFF);
        if (value != null) {
            v.setText(value.get());
            rowRefreshers.add(new Runnable() { @Override public void run() { v.setText(value.get()); } });
        }
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.leftMargin = u(20);
        r.addView(v, vl);
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean f) {
                if (f) Sounds.get(MainActivity.this).tick();
                bg.setColor(f ? 0xFFF5F5F7 : 0x00FFFFFF);
                l.setTextColor(f ? 0xFF111216 : 0xEBFFFFFF);
                v.setTextColor(f ? 0x99111216 : 0x8CFFFFFF);
                // ni agrandissement ni ombre : dans un panneau arrondi, ils seraient coupés aux bords
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Sounds.get(MainActivity.this).tock();
                click.onClick(view);
                for (Runnable rr : new ArrayList<>(rowRefreshers)) rr.run();
            }
        });
        return r;
    }

    TextView section(String s) {
        TextView t = text(18, "sans-serif-medium", 0x80FFFFFF);
        t.setText(s.toUpperCase());
        t.setLetterSpacing(0.12f);
        t.setPadding(u(32), u(40), 0, u(14));
        return t;
    }

    LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u(88 * textScale));
        lp.bottomMargin = u(6);
        return lp;
    }

    // ------------------------------------------------------------------ appui long sur une appli

    void openAppMenu(Tile t, boolean fromHome) { AppMenu.open(this, t, fromHome); }

    // ------------------------------------------------------------------ réglages
    // ------------------------------------------------------------------ téléphone

    @Override public AppRepo repo() { return repo; }
    @Override public Prefs prefs() { return prefs; }
    @Override public Online online() { return online; }

    @Override public void onFavoritesChanged() {
        if (overlay == null && !appsOpen) buildRow(focusedKey()); else dirtyRow = true;
        markAppsDirty();
        showNotice("Favoris mis à jour");
    }

    /** Écran éteint (veille de la TV) : le téléphone est déconnecté. */
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { phone.disconnect(); }
    };

    // ------------------------------------------------------------------ télécommande (page du téléphone)

    @Override public boolean onTvHome() { return resumedNow; }

    /**
     * Télécommande du téléphone. Lumen à l'écran : la touche est rejouée dans Lumen (toutes versions).
     * Autre appli : service d'accessibilité « Lumen télécommande » s'il est activé. Volume et lecture :
     * toujours, par le système audio (aucune autorisation nécessaire).
     */
    @Override public String phoneKey(String k) {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        switch (k) {
            case "sleep":
                if (RemoteService.perform(RemoteService.LOCK_SCREEN)) return "{\"ok\":true}";
                return "{\"ok\":false,\"service\":" + RemoteService.connected() + ",\"sleep\":false}";
            case "volup": case "voldown": case "mute":
                int dir = "volup".equals(k) ? android.media.AudioManager.ADJUST_RAISE
                        : "voldown".equals(k) ? android.media.AudioManager.ADJUST_LOWER : android.media.AudioManager.ADJUST_TOGGLE_MUTE;
                try { am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, android.media.AudioManager.FLAG_SHOW_UI); }
                catch (Exception e) { return "{\"ok\":false}"; }
                return "{\"ok\":true}";
            case "play": {
                long t = android.os.SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                return "{\"ok\":true}";
            }
            default: break;
        }
        if (resumedNow) {
            if ("home".equals(k)) {
                if (ambiance != null) closeAmbiance();
                if (ambientOn) exitAmbient(true);
                onNewIntent(getIntent()); // comme la touche Accueil : tout se referme, premier favori
                return "{\"ok\":true}";
            }
            int code = "up".equals(k) ? KeyEvent.KEYCODE_DPAD_UP : "down".equals(k) ? KeyEvent.KEYCODE_DPAD_DOWN
                    : "left".equals(k) ? KeyEvent.KEYCODE_DPAD_LEFT : "right".equals(k) ? KeyEvent.KEYCODE_DPAD_RIGHT
                    : "ok".equals(k) ? KeyEvent.KEYCODE_DPAD_CENTER : KeyEvent.KEYCODE_BACK;
            long t = android.os.SystemClock.uptimeMillis();
            dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
            dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
            return "{\"ok\":true}";
        }
        int action = "back".equals(k) ? RemoteService.BACK : "home".equals(k) ? RemoteService.HOME
                : "up".equals(k) ? RemoteService.DPAD_UP : "down".equals(k) ? RemoteService.DPAD_DOWN
                : "left".equals(k) ? RemoteService.DPAD_LEFT : "right".equals(k) ? RemoteService.DPAD_RIGHT : RemoteService.DPAD_CENTER;
        if (RemoteService.perform(action)) return "{\"ok\":true}";
        if ("home".equals(k)) {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setPackage(getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return "{\"ok\":true}";
            } catch (Exception ignored) { }
        }
        boolean svc = RemoteService.connected();
        return "{\"ok\":false,\"service\":" + svc + ",\"dpad\":" + RemoteService.dpadSupported() + "}";
    }

    @Override public void phoneLaunch(AppRepo.App app) {
        if (ambiance != null) closeAmbiance();
        if (ambientOn) exitAmbient(false);
        closeOverlay(false);
        launch(app);
    }

    @Override public void phoneText(String text) {
        if (ambientOn) exitAmbient(false);
        View f = getCurrentFocus();
        if (text.isEmpty() && !(f instanceof android.widget.EditText)) { if (appsOpen) appsPage.setQuery(""); return; }
        if (f instanceof android.widget.EditText) { // champ de Lumen déjà ouvert (recherche, ville)
            android.widget.EditText e = (android.widget.EditText) f;
            e.setText(text);
            e.setSelection(e.length());
            return;
        }
        if (ambiance != null) closeAmbiance();
        closeOverlay(false);
        openApps();
        appsPage.typeFromPhone(text);
    }

    @Override public void phoneAmbiance(int kind) {
        if (kind < 0) { closeAmbiance(); return; }
        if (ambientOn) exitAmbient(false);
        prefs.setAmbianceKind(kind);
        if (ambiance == null) openAmbiance(); else ambiance.selectKind(kind);
    }

    @Override public boolean phoneTimer(int minutes) {
        if (ambiance == null) return false;
        return ambiance.setTimerMinutes(minutes);
    }

    @Override public int[] ambianceState() {
        return ambiance == null ? new int[]{-1, 0, 0} : new int[]{ambiance.kind(), ambiance.timerLeftMinutes(), ambiance.timerChoice()};
    }

    @Override public void phoneVeille(boolean on) {
        if (on && !ambientOn) { if (ambiance != null) closeAmbiance(); closeOverlay(false); enterAmbient(); }
        else if (!on && ambientOn) exitAmbient(true);
    }

    @Override public boolean veilleOn() { return ambientOn; }

    @Override public void phoneSource(int source) {
        prefs.setSource(source);
        SettingsPanel.applyWallChange(this);
    }

    @Override public void phoneNextWall() { if (ambientOn && veilleOwn) veilleWall.next(); else wall.next(); }

    /** Plusieurs vidéos cochées d'affilée sur le téléphone : un seul rafraîchissement, 3 s après la dernière. */
    private String picksScope;
    private final Runnable picksRefresh = new Runnable() {
        @Override public void run() {
            if (Online.SCOPE_COSY.equals(picksScope)) online.refreshCosy();
            else { online.refreshIfNeeded(Online.VIDEO, true); wall.restart(); }
        }
    };

    @Override public void phonePicksChanged(String scope) {
        picksScope = scope;
        h.removeCallbacks(picksRefresh);
        h.postDelayed(picksRefresh, 3000);
    }

    private View phonePill;

    /**
     * Simple pastille verte tout en haut à gauche tant qu'un téléphone est connecté,
     * au-dessus du nom du fond (sans texte, pour ne rien chevaucher).
     */
    @Override public void onPhoneConnected(boolean connected) {
        if (connected && phonePill == null) {
            phonePill = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xFF34C759);
            bg.setStroke(Math.max(1, u(2)), 0x66FFFFFF);
            phonePill.setBackground(bg);
            phonePill.setElevation(u(4));
            phonePill.setContentDescription("Téléphone connecté");
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(u(16), u(16), Gravity.TOP | Gravity.START);
            pl.leftMargin = u(112);
            pl.topMargin = u(36);
            root.addView(phonePill, root.indexOfChild(notice), pl);
            phonePill.setAlpha(0f);
            phonePill.setScaleX(0.3f);
            phonePill.setScaleY(0.3f);
            phonePill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
            pulse(pl, 0);
            pulse(pl, 520);
            showNotice("Contrôlée par " + phone.deviceName());
        } else if (!connected && phonePill != null) {
            final View pp = phonePill;
            phonePill = null;
            pp.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                @Override public void run() { root.removeView(pp); }
            }).start();
        }
    }

    @Override public void onPhoneName(String name) { if (phonePill != null) showNotice("Contrôlée par " + name); }

    /** Onde verte qui s'élargit autour de la pastille (connexion du téléphone). */
    private void pulse(FrameLayout.LayoutParams at, long delay) {
        final View ring = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(Math.max(1, u(2)), 0xFF34C759);
        ring.setBackground(g);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(at.width, at.height, at.gravity);
        rl.leftMargin = at.leftMargin;
        rl.topMargin = at.topMargin;
        root.addView(ring, root.indexOfChild(notice), rl);
        ring.setAlpha(0.9f);
        ring.animate().scaleX(3.4f).scaleY(3.4f).alpha(0f).setStartDelay(delay).setDuration(900)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .withEndAction(new Runnable() { @Override public void run() { root.removeView(ring); } }).start();
    }

    @Override public void onSettingsRestored(int count) {
        showNotice(count + " réglages restaurés");
        h.postDelayed(new Runnable() { @Override public void run() { recreate(); } }, 1200); // tout est relu proprement
    }

    @Override public void onMineChanged(int received) {
        if (received > 0 && prefs.source() != Prefs.SRC_MINE) {
            prefs.setSource(Prefs.SRC_MINE);
            wall.restart();
        } else {
            wall.refreshList();
            if (prefs.source() == Prefs.SRC_MINE && wall.usingFallback()) wall.restart();
        }
        if (received > 0) showNotice("Reçu depuis le téléphone");
    }


}

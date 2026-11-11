// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity implements Wall.Listener, Online.Listener {

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
    TextView captionTitle, captionCredit, focusLabel, notice;
    LinearLayout rowsBox, topButtons;
    FrameLayout wallLayer;
    View appsTint;
    Cinema cinema;
    private LinearLayout sceneBar;
    Tile houseButton, profileButton;

    /** Affiche ou masque le bouton « Maison » (Réglages › Accueil et applis). */
    /** Bouton « profil » de l'accueil : seulement quand il y a plusieurs profils. */
    void updateProfileButton() {
        if (profileButton != null) profileButton.setVisibility(prefs.hasProfiles() ? View.VISIBLE : View.GONE);
    }

    void setHouseButton(boolean on) {
        prefs.setHaButton(on);
        if (houseButton != null) houseButton.setVisibility(on ? View.VISIBLE : View.GONE);
        refreshHouse();
    }
    TextView greet;
    TvStatus status;
    final List<LinearLayout> rows = new ArrayList<>();
    private final List<HorizontalScrollView> rowScrolls = new ArrayList<>();
    ImageView appsBg;
    FrameLayout overlay;
    private Runnable onOverlayClosed;
    View focusBeforeOverlay;
    private View focusBeforeApps;
    private Tile movingTile;
    boolean appsOpen, idle, dirtyRow, dirtyApps, ambientOn;
    AmbientView ambient;
    Weather weather;
    View scrim;
    WeatherFx fx;
    Glow glow;
    Immersive immersive;
    FrameLayout zoomView;
    PhoneServer phone;
    /** Ce que la TV fait à la demande du téléphone. */
    final PhoneBridge phoneHost = new PhoneBridge(this);
    final AppLauncher launcher = new AppLauncher(this);
    final HomeBuilder builder = new HomeBuilder(this);
    final DownloadPill downloads = new DownloadPill(this);
    final GlassUi ui = new GlassUi(this);
    final UpdateFlow updates = new UpdateFlow(this);
    final Veille veille = new Veille(this);
    boolean resumeOpen, recoOpen;
    AmbiancePanel ambiance;
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
        Lang.init(this, prefs.lang());
        textScale = Prefs.TEXT_SCALES[prefs.textScale()];
        highContrast = prefs.highContrast();
        Palette.apply(highContrast);
        repo = new AppRepo(this);
        repo.reload();
        online = new Online(this, prefs);
        online.setListener(this);
        weather = new Weather(this);
        phone = new PhoneServer(this, phoneHost);
        double[] c = weather.coords();
        if (c != null) Sky.setLocation(c[0], c[1]);
        Sounds.get(this).apply(prefs);
        ensureDefaultFavorites();
        addInputsOnce();
        builder.buildUi();
        buildRow(null);
        // icônes et couleurs : en arrière-plan, une fois l'accueil affiché
        h.postDelayed(new Runnable() { @Override public void run() { repo.warmUp(); } }, 400);
        updater = new Updater(this, prefs);
        updater.setListener(new Updater.Listener() {
            @Override public void onUpdateState() { updates.updateStateChanged(); }
        });
        h.postDelayed(prebuildApps, 2500);   // page Applications construite d'avance, à l'abri des regards

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        if (SettingsPanel.reopenPage >= 0) h.post(new Runnable() { @Override public void run() { SettingsPanel.reopen(MainActivity.this); } });
    }

    /** Taille du texte ou contraste changés : on reconstruit l'interface, puis on revient dans les réglages. */
    void restyle() {
        SettingsPanel.reopenPage = SettingsPanel.LISIBILITE;
        recreate();
    }

    /** Lumen est à l'écran (au premier plan). */
    boolean resumedNow;
    /** Écran « lumen » du démarrage en cours. */
    boolean splashing;

    @Override protected void onResume() {
        super.onResume();
        resumedNow = true;
        wall.start();
        updates.announceUpdate();
        downloads.start();
        if (cinema == null) cinema = new Cinema(this, prefs);
        cinema.onHome(); // retour sur l'accueil : fin de la séance (lumières qui reviennent)
        if (appsOpen) LivingScene.paused = true;
        // tout ce qui n'est pas visible tout de suite attend la première image (démarrage plus rapide)
        h.removeCallbacks(afterShown);
        h.postDelayed(afterShown, firstResume ? 900 : 150);
        h.postDelayed(new Runnable() { @Override public void run() { WhoPage.tryShow(MainActivity.this); } }, 400);
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
        if (status != null) status.stop();
        resumedNow = false;
        h.removeCallbacks(houseTick);
        immersive.clear(false);
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
        try { unregisterReceiver(screenOn); } catch (Exception ignored) { }
        phone.stop();
        super.onDestroy();
    }

    /** Touche Accueil alors que Lumen est affiché : retour à l'état initial. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        stopMoving();
        // « Qui regarde ? » reste affichée : au démarrage, Android rouvre souvent Lumen plusieurs fois
        if (WhoPage.isShowing(this)) return;
        closeOverlay(false);
        if (appsOpen) closeApps(false);
        for (HorizontalScrollView hs : rowScrolls) hs.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override public void onBackPressed() {
        if (overlay != null || appsOpen) Sounds.get(this).back();
        if (recoOpen && RecoPanel.back()) return; // fiche ou « similaires » → retour d'un niveau
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
                if (a != null) showNotice(a.label + L.t(" installée"));
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
        if (ambientOn) ambient.setCaption(item);
        if (immersive.keepOver()) return; // l'image d'un épisode couvre le fond : il s'appliquera à son départ
        backdropChanged();
        setCaption(item.title, item.credit, true);
    }

    /** Le fond visible a changé (diaporama, fond immersif) : couleurs accordées, verre, voile du bas. */
    void backdropChanged() {
        Bitmap b = Glass.backdrop();
        int[] px = null;
        if (b != null && !b.isRecycled() && prefs.tintUi() && !highContrast) {
            px = new int[b.getWidth() * b.getHeight()];
            b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        }
        Palette.tint(px);
        refreshGlass(root);
        if (appsBg != null && b != null) appsBg.setImageBitmap(b);
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
        if (wasFallback && !wall.usingFallback()) showNotice(L.t("Nouvelles photos prêtes"));
    }

    /** Légende en haut à gauche : le fond du diaporama (avec ses points), ou l'épisode du fond immersif. */
    void setCaption(final String title, final String credit, final boolean showDots) {
        captionBox.animate().alpha(0f).setDuration(Motion.MEDIUM).withEndAction(new Runnable() {
            @Override public void run() {
                captionTitle.setText(title);
                captionCredit.setText(credit);
                captionCredit.setVisibility(TextUtils.isEmpty(credit) ? View.GONE : View.VISIBLE);
                if (showDots) updateDots(); else dots.removeAllViews();
                captionBox.animate().alpha(appsOpen || ambientOn ? 0f : 1f).setDuration(Motion.SLOW).start();
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

    // ------------------------------------------------------------------ construction (voir HomeBuilder)

    int u(float px) { return Math.round(px * unit); }

    /** Facteur de taille du texte choisi dans Réglages › Accueil › Lisibilité. */
    float textScale = 1f;
    boolean highContrast;

    TextView text(float px, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * unit * textScale);
        // contraste renforcé : les textes secondaires (gris translucides) deviennent presque opaques
        if (highContrast && (color >>> 24) < 0xE0 && (color & 0xFFFFFF) == 0xFFFFFF) color = Palette.TEXT;
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

    // ------------------------------------------------------------------ favoris (accueil)

    void ensureDefaultFavorites() {
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
    void addInputsOnce() {
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

    void buildRow(String focusKey) {
        dirtyRow = false;
        rowsBox.removeAllViews();
        rows.clear();
        rowScrolls.clear();
        if (focusLabel.getParent() != null) ((ViewGroup) focusLabel.getParent()).removeView(focusLabel);
        if (shelf.view.getParent() != null) ((ViewGroup) shelf.view.getParent()).removeView(shelf.view);
        refreshGreeting();
        buildSceneBar();
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

    final Tile.FocusListener homeFocus = new Tile.FocusListener() {
        @Override public void onTileFocus(final Tile t, boolean focused) {
            if (focused) glowOn(t);
            if (focused) parallax(t);
            if (focused) shelf.focusApp(!appsOpen && t.tag instanceof AppRepo.App ? (AppRepo.App) t.tag : null);
            if (focused) immersive.focusApp(!appsOpen && t.tag instanceof AppRepo.App ? (AppRepo.App) t.tag : null);
            if (!focused || !banner()) return;
            final String s = t.title;
            if (TextUtils.equals(focusLabel.getText(), s)) return;
            // le texte change tout de suite : il ne peut plus rester celui d'une autre appli
            focusLabel.animate().cancel();
            focusLabel.setText(s);
            focusLabel.setAlpha(0.35f);
            focusLabel.animate().alpha(1f).setDuration(Motion.FAST).start();
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

    String focusedKey() {
        View f = getCurrentFocus();
        if (f instanceof Tile && ((Tile) f).tag instanceof AppRepo.App) return ((AppRepo.App) ((Tile) f).tag).key;
        return null;
    }

    // ouverture et fermeture des applis : voir AppLauncher
    void launchAnimated(final AppRepo.App app, final Tile from) { launcher.launchAnimated(app, from); }
    void whenUnlocked(AppRepo.App app, Runnable r) { launcher.whenUnlocked(app, r); }
    boolean closeApp(AppRepo.App app) { return launcher.closeApp(app); }
    void restartLumen() { launcher.restartLumen(); }
    void launch(AppRepo.App app) { launcher.launch(app); }

    /** Dernière appli ouverte depuis Lumen (pour « Fermer l'appli » depuis le téléphone). */
    AppRepo.App lastLaunched;

    private boolean lowSpaceSaid;

    /** Travaux lancés juste après l'affichage : autorisations, mise à jour, « Reprendre », téléchargements. */
    private final Runnable afterShown = new Runnable() {
        @Override public void run() {
            if (isFinishing()) return;
            askWatchNextOnce();
            updater.check(false);                // toutes les 6 heures au plus
            shelf.reload();                      // on revient peut-être d'un épisode
            for (String t : Library.themesFor(prefs.source())) online.refreshIfNeeded(t, false);
            // fond qui ne peut pas se télécharger faute de place : on le dit (une fois par démarrage)
            if (!lowSpaceSaid && wall.usingFallback()) {
                String low = SettingsPanel.lowSpaceNotice(MainActivity.this);
                if (low != null) { lowSpaceSaid = true; showNotice(low); }
            }
            refreshGreeting();
            refreshHouse();
            if (status != null) status.start();
            if (prefs.veilleSource() >= 0) for (String t : Library.themesFor(Library.veilleSource(prefs))) online.refreshIfNeeded(t, false);
            if (CrashLog.safeModeJustStarted) {
                CrashLog.safeModeJustStarted = false;
                showNotice(L.t("Lumen a redémarré en mode sans échec après des erreurs"));
            }
            // trois conseils, un par démarrage, puis plus rien
            h.postDelayed(new Runnable() { @Override public void run() { if (!isFinishing() && overlay == null) Tips.showNext(MainActivity.this); } }, 4000);
            // premier démarrage sans être l'écran d'accueil : l'assistant d'installation s'ouvre (une fois)
            h.postDelayed(new Runnable() { @Override public void run() { if (!isFinishing()) Install.maybeShowAtStart(MainActivity.this); } }, 1500);
        }
    };

    // ------------------------------------------------------------------ mise à jour et redémarrage (voir UpdateFlow)

    void restartLumenNicely() { updates.restartLumenNicely(); }
    void installUpdate() { updates.installUpdate(); }

    /** Écran noir « lumen » (mise à jour, redémarrage) : voir LumenScreen. */
    FrameLayout lumenScreen(String line) { return LumenScreen.show(this, line); }

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
        if (Build.VERSION.SDK_INT < 26) { showNotice(L.t("Les épisodes en cours demandent Android 8 ou plus récent")); return; }
        if (WatchNext.granted(this)) { shelf.reload(); return; }
        if (Build.VERSION.SDK_INT >= 23 && shouldShowRequestPermissionRationale(WatchNext.PERMISSION) || !prefs.watchNextAsked()) {
            prefs.setWatchNextAsked(true);
            try { requestPermissions(new String[]{WatchNext.PERMISSION}, REQ_WATCH_NEXT); return; } catch (Exception ignored) { }
        }
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) { showNotice(L.t("Autorisation impossible à demander sur cette TV")); }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_WATCH_NEXT) shelf.reload();
    }

    void showNotice(String s) { showNotice(s, 2200); }

    /** Message en bas de l'écran ; holdMs = temps de lecture avant qu'il s'efface. */
    void showNotice(String s, final long holdMs) {
        notice.setText(s);
        notice.animate().cancel();
        notice.setTranslationY(u(20));
        notice.animate().alpha(1f).translationY(0).setStartDelay(0).setDuration(Motion.FAST).setInterpolator(Motion.EASE).withEndAction(new Runnable() {
            @Override public void run() { notice.animate().alpha(0f).setStartDelay(holdMs).setDuration(Motion.MEDIUM).start(); }
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
                // au bout de la ligne : une carte de « Reprendre » ou une scène, plus haut, ne compte pas comme voisine
                if (next == null || !(f.getParent() instanceof View) || !isInside(next, (View) f.getParent())) { ResumePanel.openResume(MainActivity.this); return true; }
            }
            // Geste droite depuis la dernière vignette d'une ligne → « À l'affiche »
            if (!appsOpen && code == KeyEvent.KEYCODE_DPAD_RIGHT && f instanceof Tile && isInside(f, rowsBox)) {
                View next = f.focusSearch(View.FOCUS_RIGHT);
                // au bout de la ligne : une carte de « Reprendre » ou une scène, plus haut, ne compte pas comme voisine
                if (next == null || !(f.getParent() instanceof View) || !isInside(next, (View) f.getParent())) { RecoPanel.open(MainActivity.this); return true; }
            }
            // Geste haut depuis la première ligne des applis → accueil
            if (appsOpen && code == KeyEvent.KEYCODE_DPAD_UP && f != null) {
                View next = f.focusSearch(View.FOCUS_UP);
                if (next == null || !isInside(next, appsLayer)) { closeApps(true); return true; }
            }
        }
        if (resumeOpen && down && code == KeyEvent.KEYCODE_DPAD_RIGHT) { closeOverlay(true); return true; }
        // ◀ dans « À l'affiche » : on ferme seulement si rien n'est à gauche (les filtres défilent)
        if (recoOpen && down && code == KeyEvent.KEYCODE_DPAD_LEFT) {
            View f = getCurrentFocus();
            View n0 = f == null ? null : f.focusSearch(View.FOCUS_LEFT);
            if ((n0 == null || !isInside(n0, overlay)) && RecoPanel.back()) return true; // fiche → liste
            View next = f == null ? null : f.focusSearch(View.FOCUS_LEFT);
            if (next == null || !isInside(next, overlay)) { closeOverlay(true); return true; }
        }
        // ◀ dans une sous-page des réglages : retour à la page précédente
        if (down && code == KeyEvent.KEYCODE_DPAD_LEFT && overlay != null && e.getRepeatCount() == 0
                && SettingsPanel.inSubPage() && !(getCurrentFocus() instanceof android.widget.EditText)
                && !(getCurrentFocus() != null && "choice".equals(getCurrentFocus().getTag()))) { SettingsPanel.back(this); return true; }
        // touche sans rôle dans Lumen (Guide, couleurs, chiffres…) : sa fonction reliée, ou la proposition d'en relier une
        String remoteKey = getCurrentFocus() instanceof android.widget.EditText ? null : RemoteKeys.id(code, e.getScanCode());
        if (remoteKey != null) return onRemoteKey(remoteKey, e, down);
        if (!down && code == KeyEvent.KEYCODE_MENU && overlay == null) { SettingsPanel.openSettings(MainActivity.this); return true; }
        // micro de la télécommande : recherche vocale (si Android laisse la touche arriver jusqu'à Lumen)
        if (!down && (code == KeyEvent.KEYCODE_SEARCH || code == KeyEvent.KEYCODE_VOICE_ASSIST || code == KeyEvent.KEYCODE_ASSIST)
                && !(getCurrentFocus() instanceof android.widget.EditText)) { startVoiceSearch(); return true; }
        return super.dispatchKeyEvent(e);
    }

    /** Touche enfoncée dans Lumen dont on attend le relâchement (un relâchement seul, venu d'une autre appli, ne compte pas). */
    private String pendingRemoteKey;

    private boolean onRemoteKey(String id, KeyEvent e, boolean down) {
        String action = prefs.remoteKeys().get(id);
        boolean linked = action != null && !RemoteKeys.NONE.equals(action);
        // touche non reliée, ou panneau ouvert (le code PIN se tape aux touches chiffrées) : l'écran en cours passe d'abord
        if ((!linked || overlay != null) && super.dispatchKeyEvent(e)) {
            if (down) pendingRemoteKey = null;
            return true;
        }
        if (down) {
            if (e.getRepeatCount() == 0) pendingRemoteKey = id;
            return linked;
        }
        if (!id.equals(pendingRemoteKey)) return false;
        pendingRemoteKey = null;
        if (linked) {
            // par-dessus un panneau, seule « Retour » a un sens
            if (overlay == null || RemoteKeys.BACK.equals(action)) RemoteKeyPanel.run(this, action);
            return true;
        }
        // relier une touche est un réglage : jamais proposé dans un profil enfant
        if (action == null && overlay == null && movingTile == null && prefs.remoteAsk() && !prefs.isKidProfile()) {
            RemoteKeyPanel.ask(this, id);
            return true;
        }
        return false;
    }

    static boolean isInside(View v, View parent) {
        while (v != null) {
            if (v == parent) return true;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    final Runnable idleRun = new Runnable() {
        @Override public void run() {
            if (overlay != null || movingTile != null || appsOpen || ambiance != null || !prefs.idleFade()) return;
            idle = true;
            immersive.clear(true); // l'interface s'efface : le paysage revient
            homeLayer.animate().alpha(0f).setDuration(1800).start();
            clockBox.animate().alpha(0.6f).setDuration(1800).start();
            glow.fade();
        }
    };

    private final java.util.Map<String, GlassUi.Pill> scenePills = new java.util.HashMap<>();

    /** La dernière scène lancée garde son étincelle colorée sur l'accueil (aucune si un appareil a changé depuis). */
    void markScenes() {
        String last = prefs.haLastScene();
        for (java.util.Map.Entry<String, GlassUi.Pill> p : scenePills.entrySet()) p.getValue().setActive(p.getKey().equals(last));
    }

    /**
     * Bouton Maison « vivant » : le nombre de lumières allumées (parmi les appareils choisis) s'affiche
     * dessus et son verre s'éclaire. Relu à l'arrivée sur l'accueil puis chaque minute, seulement si
     * Home Assistant est relié et le bouton affiché.
     */
    void refreshHouse() {
        h.removeCallbacks(houseTick);
        if (houseButton == null) return;
        if (!prefs.haConfigured() || houseButton.getVisibility() != View.VISIBLE) { houseButton.setBadge(0); return; }
        if (!resumedNow) return;
        HaStates.refresh(prefs.haUrl(), prefs.haToken(), new Runnable() {
            @Override public void run() {
                List<HomeAssistant.Entity> all = HaStates.cached();
                int n = 0;
                if (all != null) for (HomeAssistant.Entity e : HomeAssistant.shown(all, prefs.haPinned(), 500))
                    if ("light".equals(e.domain) && e.on()) n++;
                final int on = n;
                h.post(new Runnable() { @Override public void run() { if (houseButton != null) houseButton.setBadge(on); } });
            }
        });
        h.postDelayed(houseTick, 60_000L);
    }

    private final Runnable houseTick = new Runnable() { @Override public void run() { refreshHouse(); } };

    /** Raccourcis de scènes Home Assistant (choisies sur le téléphone), sous le bandeau d'accueil. */
    private void buildSceneBar() {
        if (sceneBar != null && sceneBar.getParent() != null) ((ViewGroup) sceneBar.getParent()).removeView(sceneBar);
        List<String[]> scenes = prefs.haConfigured() ? prefs.haHome() : new ArrayList<String[]>();
        if (scenes.isEmpty()) return;
        sceneBar = new LinearLayout(this);
        sceneBar.setOrientation(LinearLayout.HORIZONTAL);
        sceneBar.setGravity(Gravity.CENTER);
        sceneBar.setClipChildren(false);
        scenePills.clear();
        String last = prefs.haLastScene();
        for (final String[] sc : scenes) {
            GlassUi.Pill pill = new GlassUi.Pill(this, sc[1], sc[0].equals(last), new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (cinema == null) cinema = new Cinema(MainActivity.this, prefs);
                    cinema.run(sc[0]);
                    if (sc[0].startsWith("scene.")) { prefs.setHaLastScene(sc[0]); markScenes(); }
                    Ripple.play(MainActivity.this, v);
                    showNotice(sc[1]);
                    h.postDelayed(houseTick, 1500); // la scène a changé des lumières : le bouton Maison suit
                }
            });
            scenePills.put(sc[0], pill);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pl.leftMargin = pl.rightMargin = u(10);
            sceneBar.addView(pill, pl);
        }
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.bottomMargin = u(14);
        rowsBox.addView(sceneBar, 0, bl);
    }

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
                greet.animate().alpha(1f).setDuration(Motion.SLOW).start();
            }
        });
    }

    // ------------------------------------------------------------------ désinstallation

    private static final int REQ_UNINSTALL = 43;
    private AppRepo.App uninstalling;

    /**
     * Android impose sa confirmation (« Voulez-vous désinstaller cette application ? ») : aucune appli
     * ordinaire ne peut s'en passer. En demandant le résultat, la suite se fait en arrière-plan :
     * plus de fenêtre « Désinstallation… » ni de message système, Lumen affiche le sien.
     */
    void uninstall(AppRepo.App app) {
        try {
            Intent i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + app.pkg));
            i.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            uninstalling = app;
            showSystemVeil(app);
            startActivityForResult(i, REQ_UNINSTALL);
        } catch (Exception e) {
            uninstalling = null;
            hideSystemVeil(false);
            showNotice(L.t("Cette appli ne peut pas être désinstallée"));
        }
    }

    // ------------------------------------------------------------------ recherche vocale

    private static final int REQ_VOICE = 45;

    /** La TV sait-elle reconnaître la voix ? (Google TV : oui ; certaines box : non) */
    boolean voiceAvailable() {
        try {
            return !getPackageManager().queryIntentActivities(new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isEmpty();
        } catch (Exception e) { return false; }
    }

    /**
     * Micro de la télécommande (touche Recherche ou Assistant) ou bouton micro de la recherche :
     * la fenêtre de reconnaissance d'Android s'ouvre, le texte reconnu arrive dans la recherche de Lumen.
     * Rien n'est envoyé par Lumen : c'est le service vocal de la TV qui écoute, comme pour toute appli.
     */
    void startVoiceSearch() {
        if (!voiceAvailable()) { showNotice(L.t("Pas de reconnaissance vocale sur cette TV")); return; }
        Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Une appli, un film, un réglage…");
        i.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try { startActivityForResult(i, REQ_VOICE); } catch (Exception e) { showNotice(L.t("Reconnaissance vocale indisponible")); }
    }

    private void onVoiceResult(Intent data) {
        java.util.ArrayList<String> r = data == null ? null : data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
        final String q = r == null || r.isEmpty() || r.get(0) == null ? "" : r.get(0).trim();
        if (q.isEmpty()) { showNotice(L.t("Je n'ai pas compris")); return; }
        if (overlay != null) closeOverlay(false);
        openApps();
        h.postDelayed(new Runnable() { @Override public void run() { if (appsOpen) appsPage.typeFromPhone(q); } }, 350);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_VOICE) { if (result == RESULT_OK) onVoiceResult(data); return; }
        if (req != REQ_UNINSTALL) return;
        AppRepo.App app = uninstalling;
        uninstalling = null;
        veilApp = null; // le message vient d'ici, pas du voile
        hideSystemVeil(true);
        if (app == null) return;
        if (result == RESULT_OK) showNotice(app.label + L.t(" désinstallée"));
        else if (result != RESULT_CANCELED) showNotice(app.label + L.t(" n'a pas pu être désinstallée"));
    }

    // ------------------------------------------------------------------ fenêtres du système par-dessus Lumen

    View systemVeil;
    AppRepo.App veilApp;

    /**
     * Les fenêtres d'Android (désinstallation…) sont transparentes : sans voile, elles se mêlent
     * aux vignettes. On assombrit tout l'écran, avec la bannière de l'appli en filigrane, le temps
     * qu'elles s'affichent ; le voile part quand Lumen reprend la main pour de bon.
     */
    void showSystemVeil(AppRepo.App app) {
        hideSystemVeil(false);
        veilApp = app;
        FrameLayout v = new FrameLayout(this);
        v.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF505060A, 0xFA0A0B12}));
        v.setClickable(true);
        v.setElevation(u(70));
        v.setAlpha(0f);
        root.addView(v, match());
        v.animate().alpha(1f).setStartDelay(0).setDuration(250).start();
        systemVeil = v;
        // filet de sécurité : si aucune fenêtre du système ne s'est ouverte, le voile repart
        h.postDelayed(new Runnable() {
            @Override public void run() { if (systemVeil == v && hasWindowFocus()) hideSystemVeil(true); }
        }, 3000);
    }

    void hideSystemVeil(boolean animate) {
        if (systemVeil == null) return;
        final View v = systemVeil;
        systemVeil = null;
        final AppRepo.App app = veilApp;
        veilApp = null;
        if (app != null && !app.isInput()) {
            boolean gone;
            try { getPackageManager().getPackageInfo(app.pkg, 0); gone = false; } catch (Exception e) { gone = true; }
            if (gone) showNotice(app.label + L.t(" désinstallée"));
        }
        if (!animate) { root.removeView(v); return; }
        v.animate().alpha(0f).setDuration(Motion.MEDIUM).withEndAction(new Runnable() {
            @Override public void run() { root.removeView(v); }
        }).start();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && systemVeil != null) {
            // Android enchaîne parfois deux fenêtres (confirmation, puis progression) : on attend un peu
            h.postDelayed(new Runnable() {
                @Override public void run() { if (hasWindowFocus()) hideSystemVeil(true); }
            }, 700);
        }
    }

    /** Démarrage en cascade, en moins d'une seconde : l'heure, puis le fond, puis la légende et les applis. */
    void intro() {
        final android.view.animation.DecelerateInterpolator di = new android.view.animation.DecelerateInterpolator(1.6f);
        clockBox.setAlpha(0f);
        wallLayer.setAlpha(0f);
        captionBox.setAlpha(0f);
        homeLayer.setAlpha(0f);
        homeLayer.setTranslationY(u(28));
        clockBox.animate().alpha(1f).setStartDelay(60).setDuration(Motion.MEDIUM).setInterpolator(di).withEndAction(noDelay(clockBox)).start();
        wallLayer.animate().alpha(1f).setStartDelay(180).setDuration(Motion.MEDIUM).setInterpolator(di).withEndAction(noDelay(wallLayer)).start();
        captionBox.animate().alpha(1f).setStartDelay(380).setDuration(Motion.MEDIUM).setInterpolator(di).withEndAction(noDelay(captionBox)).start();
        homeLayer.animate().alpha(1f).translationY(0).setStartDelay(420).setDuration(Motion.MEDIUM).setInterpolator(di).withEndAction(noDelay(homeLayer)).start();
    }

    /** Le délai d'une animation reste attaché à la vue : on le remet à zéro (sinon tout s'ouvrirait en retard). */
    static Runnable noDelay(final View v) {
        return new Runnable() { @Override public void run() { v.animate().setStartDelay(0); } };
    }

    void wake() {
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

    // ------------------------------------------------------------------ mode veille (voir Veille)

    void enterAmbient() { immersive.clear(false); veille.enterAmbient(); }
    void exitAmbient(boolean animate) { veille.exitAmbient(animate); }

    private final Runnable ambientRun = new Runnable() {
        @Override public void run() {
            if (overlay != null || movingTile != null || ambientOn || ambiance != null) return;
            enterAmbient();
        }
    };

    /** Fond propre au mode veille (images, sans vidéo) ; sinon le fond de l'accueil continue derrière. */
    Wall veilleWall;
    boolean veilleOwn;

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

    /** Bouton loupe de l'accueil : la page des applis s'ouvre, recherche prête à taper. */
    void openSearch() {
        openApps();
        h.postDelayed(new Runnable() { @Override public void run() { if (appsOpen) appsPage.focusSearch(); } }, 350);
    }

    void openApps() {
        if (appsOpen) return;
        appsOpen = true;
        immersive.clear(true);
        focusBeforeApps = getCurrentFocus();
        h.removeCallbacks(prebuildApps);
        if (Glass.backdrop() != null) appsBg.setImageBitmap(Glass.backdrop());
        LivingScene.paused = true;  // le fond est caché par la page : on n'anime plus rien derrière
        fx.setRunning(false);
        if (!appsPage.built() || dirtyApps || appsPage.stale()) buildApps(null);  // sinon : déjà prête
        else appsPage.rewind();
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        // l'accueil s'éloigne vers le haut ; le fond se floute et s'assombrit sur place (profondeur) ;
        // puis les rangées des applis montent l'une après l'autre
        DecelerateInterpolator soft = new DecelerateInterpolator(2f);
        homeLayer.animate().translationY(-u(36)).scaleX(0.97f).scaleY(0.97f).alpha(0f).setStartDelay(0).setDuration(260).setInterpolator(soft).start();
        glow.fade();
        captionBox.animate().alpha(0f).setStartDelay(0).setDuration(Motion.FAST).start();
        clockBox.animate().alpha(0f).setStartDelay(0).setDuration(Motion.FAST).start(); // l'horloge de l'accueil ne doit pas chevaucher la recherche
        wallLayer.animate().scaleX(1.08f).scaleY(1.08f).setStartDelay(0).setDuration(Motion.SLOW).setInterpolator(soft).start();
        appsLayer.animate().cancel();
        appsLayer.setVisibility(View.VISIBLE);
        appsLayer.setAlpha(1f);
        appsLayer.setTranslationY(0);
        appsBg.setAlpha(0f);
        appsTint.setAlpha(0f);
        appsBg.animate().alpha(1f).setStartDelay(0).setDuration(Motion.MEDIUM).setInterpolator(soft).start();
        appsTint.animate().alpha(1f).setStartDelay(0).setDuration(Motion.MEDIUM).setInterpolator(soft).start();
        appsPage.animateIn();
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
        // l'inverse de l'ouverture : la page s'efface en descendant un peu, le fond redevient net,
        // l'accueil revient de derrière
        DecelerateInterpolator soft = new DecelerateInterpolator(2f);
        homeLayer.animate().translationY(0).scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(animate ? 90 : 0)
                .setDuration(animate ? 380 : 0).setInterpolator(soft).start();
        captionBox.animate().alpha(1f).setStartDelay(0).setDuration(Motion.MEDIUM).start();
        clockBox.animate().alpha(1f).setStartDelay(0).setDuration(Motion.MEDIUM).start();
        wallLayer.animate().scaleX(1.04f).scaleY(1.04f).setStartDelay(0).setDuration(animate ? 600 : 0).setInterpolator(soft).start();
        appsLayer.animate().alpha(0f).translationY(u(40)).setStartDelay(0).setDuration(animate ? 280 : 0).setInterpolator(soft)
                .withEndAction(new Runnable() {
                    @Override public void run() { if (!appsOpen) { appsLayer.setVisibility(View.GONE); appsLayer.setTranslationY(0); } }
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

    // briques des panneaux : voir GlassUi
    LinearLayout glassCard(float radiusPx) { return ui.glassCard(radiusPx); }
    LinearLayout listRow(String label, final Value value, final View.OnClickListener click) { return ui.listRow(label, value, click); }
    LinearLayout iconRow(int glyphType, String label, Value value, View.OnClickListener click, final boolean danger) { return ui.iconRow(glyphType, label, value, click, danger); }
    LinearLayout menuHeader(android.graphics.drawable.Drawable art, boolean wide, String title, String sub) { return ui.menuHeader(art, wide, title, sub); }
    View menuDivider() { return ui.menuDivider(); }
    TextView section(String s) { return ui.section(s); }
    LinearLayout.LayoutParams rowLp() { return ui.rowLp(); }

    FrameLayout openOverlay(Runnable onClosed) {
        closeOverlay(false);
        focusBeforeOverlay = getCurrentFocus();
        onOverlayClosed = onClosed;
        h.removeCallbacks(idleRun);
        FrameLayout o = new FrameLayout(this);
        o.setClipChildren(false);
        View dim = new View(this);
        dim.setBackgroundColor(Palette.DIM);
        o.addView(dim, match());
        o.setAlpha(0f);
        root.addView(o, root.indexOfChild(notice), match());
        o.animate().alpha(1f).setDuration(Motion.FAST).start();
        growFrom(o, focusBeforeOverlay);
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        appsLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        overlay = o;
        return o;
    }

    /** Centre (à l'écran) du bouton qui a ouvert le panneau ; le panneau y retourne en se fermant. */
    private float[] overlayOrigin;

    /**
     * Le panneau en verre (ajouté par l'appelant juste après) grandit depuis le bouton choisi :
     * il part petit, centré sur ce bouton, et s'installe à sa place en douceur.
     */
    private void growFrom(final FrameLayout o, View from) {
        overlayOrigin = null;
        if (from != null && from.isAttachedToWindow()) {
            int[] l = new int[2];
            from.getLocationOnScreen(l);
            overlayOrigin = new float[]{l[0] + from.getWidth() * from.getScaleX() / 2f, l[1] + from.getHeight() * from.getScaleY() / 2f};
        }
        final float[] origin = overlayOrigin;
        o.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                o.getViewTreeObserver().removeOnPreDrawListener(this);
                if (o.getChildCount() < 2) return true;
                View card = o.getChildAt(o.getChildCount() - 1);
                if (card.getWidth() == 0) return true;
                int[] c = new int[2];
                card.getLocationOnScreen(c);
                float px = card.getWidth() / 2f, py = card.getHeight() / 2f;
                if (origin != null) {
                    // pivot ramené dans le panneau, du côté du bouton
                    px = Math.max(0, Math.min(card.getWidth(), origin[0] - c[0]));
                    py = Math.max(0, Math.min(card.getHeight(), origin[1] - c[1]));
                }
                card.setPivotX(px);
                card.setPivotY(py);
                card.setScaleX(0.72f);
                card.setScaleY(0.72f);
                card.setAlpha(0f);
                card.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(0).setDuration(340)
                        .setInterpolator(new DecelerateInterpolator(2.4f)).start();
                return true;
            }
        });
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
            // le panneau rapetisse vers son bouton en s'effaçant
            if (o.getChildCount() >= 2) {
                View card = o.getChildAt(o.getChildCount() - 1);
                card.animate().scaleX(0.86f).scaleY(0.86f).setStartDelay(0).setDuration(Motion.FAST)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f)).start();
            }
            o.animate().alpha(0f).setDuration(Motion.FAST).withEndAction(new Runnable() {
                @Override public void run() { root.removeView(o); }
            }).start();
        } else {
            root.removeView(o);
        }
        wake();
    }

    interface Value { String get(); }

    /** Rafraîchit toutes les valeurs affichées d'un panneau (un réglage peut en influencer un autre). */
    final List<Runnable> rowRefreshers = new ArrayList<>();

    // ------------------------------------------------------------------ appui long sur une appli

    void openAppMenu(Tile t, boolean fromHome) { AppMenu.open(this, t, fromHome); }

    // ------------------------------------------------------------------ téléphone (voir PhoneBridge)

    /** Écran éteint (veille de la TV) : le téléphone est déconnecté. */
    /** TV rallumée : « Qui regarde ? » au retour de Lumen à l'écran (s'il y a plusieurs profils). */
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            WhoPage.tvWoke();
            WhoPage.tryShow(MainActivity.this);
        }
    };

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { phone.disconnect(); }
    };

}

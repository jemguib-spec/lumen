// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fond immersif : quand la sélection s'arrête une seconde sur une appli de l'accueil qui a un film
 * ou un épisode en cours, le paysage laisse place à l'image de ce programme, adoucie et assombrie ;
 * il revient dès que la sélection passe ailleurs. Avec plusieurs programmes en cours, le fond suit
 * celui qui est sélectionné dans « Reprendre ». Mêmes images et mêmes règles que « Reprendre »
 * (rien de plus n'est téléchargé si « Affiches des épisodes » est désactivé), rien n'est enregistré.
 */
final class Immersive {
    private static final long DWELL_MS = 1000, ENTRY_MS = 350;

    private final MainActivity a;
    final FrameLayout view;
    private final ImageView image;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppRepo.App wanted;
    /** Le programme sélectionné dans « Reprendre » (null = le plus récent de l'appli). */
    private WatchNext.Entry wantedEntry;
    private String shownPkg, shownPoster;
    private int generation;
    /** Verre du vrai fond (rendu au départ) et verre de l'image affichée. */
    private Bitmap wallGlass, posterGlass;

    Immersive(MainActivity a) {
        this.a = a;
        view = new FrameLayout(a);
        image = new ImageView(a);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.addView(image, MainActivity.match());
        View veil = new View(a); // l'image reste un fond : les vignettes et l'heure doivent rester lisibles
        veil.setBackgroundColor(0x5204040A);
        view.addView(veil, MainActivity.match());
        view.setAlpha(0f);
        view.setVisibility(View.GONE);
    }

    boolean showing() { return shownPkg != null; }

    /** Une vignette de l'accueil vient d'être sélectionnée (null = autre chose qu'une appli). */
    void focusApp(AppRepo.App app) { focus(app, null, DWELL_MS); }

    /** Un programme de « Reprendre » vient d'être sélectionné : le fond devient le sien. */
    void focusEntry(AppRepo.App app, WatchNext.Entry e) { focus(app, e, ENTRY_MS); }

    private void focus(AppRepo.App app, WatchNext.Entry e, long delay) {
        wanted = app;
        wantedEntry = e;
        a.h.removeCallbacks(arm);
        generation++; // une image encore en chargement pour l'ancienne sélection ne s'affichera pas
        if (showing() && (app == null || !app.pkg.equals(shownPkg))) clear(true);
        if (app != null) a.h.postDelayed(arm, delay);
    }

    private final Runnable arm = new Runnable() {
        @Override public void run() {
            final AppRepo.App app = wanted;
            if (app == null || !a.prefs.immersive() || !a.prefs.topShelf()
                    || a.appsOpen || a.ambientOn || a.idle || !a.resumedNow) return;
            WatchNext.Entry pick = wantedEntry;
            if (pick == null || pick.poster == null || pick.poster.isEmpty()) pick = a.shelf.firstWithArt(app.pkg);
            final WatchNext.Entry e = pick;
            if (e == null) return;
            if (app.pkg.equals(shownPkg) && e.poster.equals(shownPoster)) return; // déjà à l'écran
            final boolean network = a.prefs.shelfPosters();
            if (!network && !e.poster.startsWith("content://")) return;
            final int gen = ++generation;
            io.execute(new Runnable() {
                @Override public void run() {
                    Bitmap p = WatchNext.poster(a, e.poster, network);
                    final Bitmap big = p == null ? null : backdropOf(p, e.portrait);
                    if (big == null) return;
                    a.h.post(new Runnable() {
                        @Override public void run() { if (gen == generation && wanted == app) show(app, e, big); }
                    });
                }
            });
        }
    };

    /** Image recadrée en 16:9 et adoucie : agrandie en plein écran, elle ne montre ni pixels ni texte net. */
    private static Bitmap backdropOf(Bitmap p, boolean portrait) {
        try {
            int w = p.getWidth(), h = p.getHeight();
            if (w < 16 || h < 16) return null;
            int cw = w, ch = w * 9 / 16;
            if (ch > h) { ch = h; cw = h * 16 / 9; }
            // affiche verticale : on garde le haut (visages, titre), pas le milieu
            Bitmap crop = Bitmap.createBitmap(p, (w - cw) / 2, portrait ? (h - ch) / 4 : (h - ch) / 2, cw, ch);
            Bitmap out = portrait ? Glass.soft(crop, 240, 135) : Glass.soft(crop, 480, 270);
            if (crop != p) crop.recycle(); // p reste dans le cache des affiches
            return out;
        } catch (Throwable t) { return null; }
    }

    private void show(AppRepo.App app, WatchNext.Entry e, Bitmap big) {
        if (a.appsOpen || a.ambientOn || a.idle || !a.resumedNow) return;
        boolean swap = showing() && image.getDrawable() != null; // d'un programme à l'autre : fondu entre les deux images
        if (!showing()) wallGlass = Glass.backdrop();
        shownPkg = app.pkg;
        shownPoster = e.poster;
        if (swap) {
            android.graphics.drawable.Drawable old = image.getDrawable();
            if (old instanceof android.graphics.drawable.TransitionDrawable) old = ((android.graphics.drawable.TransitionDrawable) old).getDrawable(1);
            android.graphics.drawable.TransitionDrawable td = new android.graphics.drawable.TransitionDrawable(
                    new android.graphics.drawable.Drawable[]{old, new android.graphics.drawable.BitmapDrawable(a.getResources(), big)});
            image.setImageDrawable(td);
            td.startTransition(Motion.MEDIUM);
        } else image.setImageBitmap(big);
        view.animate().cancel();
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setStartDelay(0).setDuration(Motion.SLOW).setInterpolator(Motion.EASE).start();
        Glass.setSource(big);
        posterGlass = Glass.backdrop();
        a.backdropChanged();
        String sub = e.subtitle != null && !e.subtitle.isEmpty() ? e.subtitle : e.remaining;
        a.setCaption(e.title, sub == null || sub.isEmpty() ? app.label : sub + " · " + app.label, false);
    }

    /**
     * Le diaporama a changé de fond derrière l'image : on retient son verre pour plus tard et on
     * garde celui de l'image. false si rien n'est affiché (le nouveau fond s'applique normalement).
     */
    boolean keepOver() {
        if (!showing()) return false;
        wallGlass = Glass.backdrop();
        Glass.setBackdrop(posterGlass);
        return true;
    }

    /** Retour au paysage. */
    void clear(boolean animate) {
        a.h.removeCallbacks(arm);
        generation++;
        if (!showing()) return;
        shownPkg = null;
        shownPoster = null;
        posterGlass = null;
        view.animate().cancel();
        if (animate) {
            view.animate().alpha(0f).setStartDelay(0).setDuration(Motion.SLOW).setInterpolator(Motion.EASE).withEndAction(new Runnable() {
                @Override public void run() { if (!showing()) { view.setVisibility(View.GONE); image.setImageDrawable(null); } }
            }).start();
        } else {
            view.setAlpha(0f);
            view.setVisibility(View.GONE);
            image.setImageDrawable(null);
        }
        Glass.setBackdrop(wallGlass);
        wallGlass = null;
        a.backdropChanged();
        WallItem cur = a.wall.current();
        if (cur != null) a.setCaption(cur.title, cur.credit, true);
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/** Mode veille : l'accueil s'efface en cascade derrière l'horloge et le fond, puis revient dans le même ordre. */
final class Veille {
    private final MainActivity a;

    Veille(MainActivity a) { this.a = a; }

    void enterAmbient() {
        a.ambientOn = true;
        a.h.removeCallbacks(a.idleRun);
        WallItem cur = a.wall.current();
        a.ambient.setCaption(cur);
        a.veilleOwn = a.prefs.veilleSource() != Prefs.VEILLE_LIVE;
        if (a.veilleOwn) {
            a.wall.stop();               // plus de vidéo derrière la veille
            a.veilleWall.startWith(cur); // même image que l'accueil si c'en est une
        }
        a.ambient.start();
        a.ambient.setVisibility(View.VISIBLE);
        a.ambient.setAlpha(0f);
        boolean cascade = !a.appsOpen && a.homeLayer.getAlpha() > 0.5f;
        a.ambient.animate().alpha(1f).setStartDelay(cascade ? 500 : 0).setDuration(2000).withEndAction(MainActivity.noDelay(a.ambient)).start();
        if (cascade) {
            // les éléments s'en vont un à un : légende, boutons, bandeau, puis les vignettes de gauche à droite
            final List<View> parts = homeParts();
            long step = 70, last = 0;
            for (int i = 0; i < parts.size(); i++) {
                View v = parts.get(i);
                v.animate().cancel();
                last = i * step;
                v.animate().alpha(0f).translationY(a.u(16)).setStartDelay(last).setDuration(650)
                        .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).start();
            }
            a.clockBox.animate().alpha(0f).setStartDelay(last + 200).setDuration(900).withEndAction(MainActivity.noDelay(a.clockBox)).start();
            a.h.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!a.ambientOn) return;
                    a.homeLayer.setAlpha(0f); // tout est caché : on remet chaque élément en place, invisible
                    for (View v : parts) { v.animate().cancel(); v.animate().setStartDelay(0); v.setTranslationY(0); v.setAlpha(1f); }
                    a.captionBox.setAlpha(0f);
                }
            }, last + 750);
        } else {
            for (View v : new View[]{a.homeLayer, a.appsLayer, a.captionBox, a.clockBox}) v.animate().alpha(0f).setStartDelay(0).setDuration(1500).start();
        }
        a.glow.fade();
    }

    /** Éléments de l'accueil, dans l'ordre de la cascade du mode veille. */
    List<View> homeParts() {
        List<View> out = new ArrayList<>();
        out.add(a.captionBox);
        for (int i = a.topButtons.getChildCount() - 1; i >= 0; i--) if (a.topButtons.getChildAt(i).getVisibility() == View.VISIBLE) out.add(a.topButtons.getChildAt(i));
        if (a.greet != null && a.greet.getVisibility() == View.VISIBLE) out.add(a.greet);
        for (LinearLayout r : a.rows) for (int i = 0; i < r.getChildCount(); i++) out.add(r.getChildAt(i));
        return out;
    }

    void exitAmbient(boolean animate) {
        a.ambientOn = false;
        if (animate) Sounds.get(a).wake(); // léger fondu sonore au réveil
        a.idle = false;
        a.ambient.stop();
        if (a.veilleOwn) {
            a.veilleOwn = false;
            a.veilleWall.stop();
            if (a.resumedNow) a.wall.start();
        }
        long d = animate ? 400 : 0;
        a.ambient.animate().alpha(0f).setDuration(d).withEndAction(new Runnable() {
            @Override public void run() {
                if (a.ambientOn) return;
                a.ambient.setVisibility(View.GONE);
                a.veilleWall.release();
            }
        }).start();
        a.clockBox.animate().alpha(a.appsOpen ? 0f : 1f).setStartDelay(0).setDuration(d).start();
        if (a.appsOpen) a.appsLayer.animate().alpha(1f).setStartDelay(0).setDuration(d).start();
        else if (!animate) {
            a.homeLayer.animate().alpha(1f).setStartDelay(0).setDuration(0).start();
            a.captionBox.animate().alpha(1f).setStartDelay(0).setDuration(0).start();
        } else {
            // retour : les éléments reviennent un à un, dans le même ordre
            a.homeLayer.animate().cancel();
            a.homeLayer.setAlpha(1f);
            List<View> parts = homeParts();
            for (int i = 0; i < parts.size(); i++) {
                final View v = parts.get(i);
                float rest = v instanceof Tile ? ((Tile) v).restingAlpha() : 1f;
                v.animate().cancel();
                v.setAlpha(0f);
                v.setTranslationY(a.u(14));
                v.animate().alpha(rest).translationY(0).setStartDelay(120 + i * 40L).setDuration(420)
                        .setInterpolator(new DecelerateInterpolator(2f)).withEndAction(MainActivity.noDelay(v)).start();
            }
        }
        a.wake();
    }
}

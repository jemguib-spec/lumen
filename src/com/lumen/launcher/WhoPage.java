// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * « Qui regarde ? » en plein écran : au démarrage et au réveil de la TV (seulement s'il y a
 * plusieurs profils), et depuis le bouton « Profils » de l'accueil. Une pastille ronde par
 * personne avec son initiale ; une fois choisie, « Bonjour Léa » (ou Bonsoir…) puis l'accueil.
 * Retour : on garde le profil actuel.
 */
final class WhoPage {
    private WhoPage() { }

    /**
     * À proposer dès que possible : vrai au lancement de Lumen (nouveau processus), et quand la TV
     * se rallume. Reste en attente tant que la page n'a pas pu s'afficher : au démarrage, Android
     * rouvre souvent Lumen plusieurs fois de suite, et Lumen n'est pas toujours à l'écran à l'instant voulu.
     */
    private static boolean pending = true;
    /** Réveil de la TV (0 : lancement de Lumen) ; au-delà de 10 min sans retour sur Lumen, on oublie. */
    private static long pendingSince;
    private static final long WAKE_WINDOW = 10 * 60_000L;
    private static FrameLayout showing;
    private static int tries;

    /** La TV vient de se rallumer. */
    static void tvWoke() {
        pending = true;
        pendingSince = android.os.SystemClock.elapsedRealtime();
    }

    /** La page « Qui regarde ? » est-elle à l'écran ? (la touche Accueil ne la ferme pas) */
    static boolean isShowing(MainActivity a) { return showing != null && a.overlay == showing; }

    /** Affiche la page si elle est attendue et que Lumen est prêt ; sinon réessaie un peu plus tard. */
    static void tryShow(final MainActivity a) {
        if (!pending) return;
        if (!a.prefs.hasProfiles() || !a.prefs.askProfileAtStart()
                || (pendingSince > 0 && android.os.SystemClock.elapsedRealtime() - pendingSince > WAKE_WINDOW)) {
            pending = false;
            return;
        }
        if (a.isFinishing() || !a.resumedNow) return; // onResume la redemandera
        if (a.splashing || a.overlay != null || a.ambiance != null) {
            // écran « lumen », panneau ou ambiance en cours : on regarde de nouveau dans un instant
            a.h.removeCallbacks(retry);
            retryOn = a;
            if (tries++ < 40) a.h.postDelayed(retry, 500);
            return;
        }
        pending = false;
        tries = 0;
        if (a.ambientOn) a.exitAmbient(false);
        show(a, false);
    }

    private static MainActivity retryOn;
    private static final Runnable retry = new Runnable() {
        @Override public void run() { if (retryOn != null) tryShow(retryOn); }
    };

    static void show(final MainActivity a, boolean withManage) {
        final FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() { showing = null; }
        });
        showing = o;
        // fond presque opaque : le fond d'écran transparaît à peine
        View veil = new View(a);
        veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xE6050608, 0xF20A0B12}));
        o.addView(veil, MainActivity.match());

        final LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        TextView title = a.text(60, "sans-serif-thin", Palette.TEXT);
        title.setText(L.t("Qui regarde ?"));
        title.setLetterSpacing(0.02f);
        col.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(a.u(40), a.u(40), a.u(40), a.u(20));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = a.u(56);
        col.addView(row, rl);

        final List<Profiles.Profile> list = a.prefs.profiles();
        View focus = null;
        for (int i = 0; i < list.size(); i++) {
            final Profiles.Profile p = list.get(i);
            View card = person(a, p, new Runnable() {
                @Override public void run() {
                    ProfilePanel.switchGuarded(a, p.id, new Runnable() {
                        @Override public void run() { if (showing == o) chosen(a, o, col, p); else { a.closeOverlay(false); ProfilePanel.switchTo(a, p.id); } }
                    });
                }
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) cl.leftMargin = a.u(8);
            row.addView(card, cl);
            if (focus == null || p.id == a.prefs.activeProfile()) focus = card;
            // entrée en douceur, l'une après l'autre
            card.setAlpha(0f);
            card.setTranslationY(a.u(24));
            card.animate().alpha(1f).translationY(0).setStartDelay(120 + i * 70L).setDuration(Motion.MEDIUM)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }

        if (withManage) {
            TextView manage = a.text(22, "sans-serif-medium", Palette.TEXT_2);
            manage.setText(L.t("Gérer les profils"));
            manage.setPadding(a.u(28), a.u(14), a.u(28), a.u(14));
            manage.setFocusable(true);
            manage.setClickable(true);
            final GradientDrawable mb = new GradientDrawable();
            mb.setCornerRadius(a.u(Radius.PILL));
            mb.setColor(0x00FFFFFF);
            manage.setBackground(mb);
            manage.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean f) {
                    mb.setColor(f ? Palette.SELECTED : 0x00FFFFFF);
                    ((TextView) v).setTextColor(f ? Palette.ON_SELECTED : Palette.TEXT_2);
                    if (f) Sounds.get(a).tick();
                }
            });
            manage.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(false); ProfilePanel.openManage(a); }
            });
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ml.topMargin = a.u(36);
            col.addView(manage, ml);
        }

        o.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        title.setAlpha(0f);
        title.animate().alpha(1f).setDuration(Motion.SLOW).start();
        if (focus != null) focus.requestFocus();
    }

    /** Une personne : grande pastille ronde avec l'initiale, prénom dessous. */
    private static View person(final MainActivity a, Profiles.Profile p, final Runnable onPick) {
        // sans calque intermédiaire pendant le fondu d'entrée : sinon la pastille agrandie (et son
        // anneau, son ombre) était coupée aux bords du bloc le temps de l'animation
        final LinearLayout box = new LinearLayout(a) {
            @Override public boolean hasOverlappingRendering() { return false; }
        };
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setFocusable(true);
        box.setClickable(true);
        box.setSoundEffectsEnabled(false);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setPadding(a.u(18), a.u(18), a.u(18), 0); // place pour l'agrandissement et l'anneau

        final int size = a.u(176);
        // monochrome comme le reste de Lumen : pastille de verre, initiale blanche ; sélectionnée,
        // elle devient blanche avec l'initiale foncée (comme les boutons de l'accueil)
        final TextView disc = a.text(68, "sans-serif-light", Palette.TEXT);
        disc.setText(initial(p.name));
        disc.setGravity(Gravity.CENTER);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x1FFFFFFF);
        bg.setStroke(Math.max(1, a.u(2)), 0x40FFFFFF);
        disc.setBackground(bg);
        box.addView(disc, new LinearLayout.LayoutParams(size, size));

        final TextView name = a.text(28, "sans-serif", Palette.TEXT_2);
        name.setText(p.name);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = a.u(22);
        box.addView(name, nl);

        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick();
                disc.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).setDuration(Motion.FAST)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
                bg.setColor(f ? Palette.SELECTED : 0x1FFFFFFF);
                bg.setStroke(Math.max(1, a.u(2)), f ? Palette.SELECTED : 0x40FFFFFF);
                disc.setTextColor(f ? Palette.ON_SELECTED : Palette.TEXT);
                disc.setElevation(f ? a.u(20) : 0);
                name.setTextColor(f ? 0xFFFFFFFF : Palette.TEXT_2);
            }
        });
        box.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Sounds.get(a).tock(); onPick.run(); }
        });
        return box;
    }

    /** Choisi : la page s'efface, « Bonjour Léa » apparaît, puis l'accueil de ce profil. */
    private static void chosen(final MainActivity a, final FrameLayout o, View page, final Profiles.Profile p) {
        // plus de choix possible pendant le message
        page.setFocusable(false);
        o.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        page.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(Motion.MEDIUM).start();

        final TextView hello = a.text(84, "sans-serif-thin", 0xFFFFFFFF);
        hello.setText(Greeting.hello(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) + " " + p.name);
        hello.setGravity(Gravity.CENTER);
        hello.setLetterSpacing(0.02f);
        hello.setAlpha(0f);
        hello.setScaleX(0.94f);
        hello.setScaleY(0.94f);
        o.addView(hello, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        hello.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(220).setDuration(Motion.SLOW)
                .setInterpolator(new DecelerateInterpolator(2f)).start();

        // le profil change derrière le message ; si l'écran doit être reconstruit, à la fin seulement
        final boolean rebuild = ProfilePanel.needsRebuild(a, p.id);
        if (!rebuild) ProfilePanel.switchTo(a, p.id, true);
        a.h.postDelayed(new Runnable() {
            @Override public void run() {
                if (rebuild) { ProfilePanel.switchTo(a, p.id, true); return; }
                if (a.overlay == o) a.closeOverlay(true);
            }
        }, 2000);
    }

    static String initial(String name) {
        if (name == null || name.isEmpty()) return "?";
        int cp = name.codePointAt(0);
        return new String(Character.toChars(cp)).toUpperCase(Locale.FRANCE);
    }
}

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

    /** Couleurs douces des pastilles, une par profil (selon son numéro). */
    private static final int[] COLORS = {0xFF5E8BFF, 0xFFFF7A6B, 0xFF4CC9A6, 0xFFF5B04C, 0xFFB57BFF, 0xFFFF6FAE, 0xFF5BC0EB, 0xFFA3C46A};

    /** Déjà proposée depuis le démarrage de Lumen (une reconstruction de l'écran ne la remontre pas). */
    private static boolean shownSinceStart;
    /** Heure du dernier réveil de la TV : la page est proposée si Lumen revient à l'écran dans les 10 min. */
    private static long wokeAt;
    private static final long WAKE_WINDOW = 10 * 60_000L;

    /** La TV vient de se rallumer. */
    static void tvWoke() { wokeAt = android.os.SystemClock.elapsedRealtime(); }

    /** Au démarrage ou au réveil de la TV : la page, s'il y a plusieurs profils. */
    static void maybeShow(final MainActivity a, long delay) {
        boolean start = !shownSinceStart;
        boolean wake = wokeAt > 0 && android.os.SystemClock.elapsedRealtime() - wokeAt < WAKE_WINDOW;
        shownSinceStart = true;
        wokeAt = 0;
        if (!(start || wake) || !a.prefs.hasProfiles()) return;
        a.h.postDelayed(new Runnable() {
            @Override public void run() {
                if (a.isFinishing() || !a.resumedNow || a.ambiance != null) return;
                if (a.ambientOn) a.exitAmbient(false);
                show(a, false);
            }
        }, delay);
    }

    /** Lumen redémarre (réglages restaurés, mise à jour…) : pas de page au retour. */
    static void skipNextStart() { shownSinceStart = true; }

    static void show(final MainActivity a, boolean withManage) {
        final FrameLayout o = a.openOverlay(null);
        // fond presque opaque : le fond d'écran transparaît à peine
        View veil = new View(a);
        veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xE6050608, 0xF20A0B12}));
        o.addView(veil, MainActivity.match());

        final LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        TextView title = a.text(60, "sans-serif-thin", 0xF5FFFFFF);
        title.setText("Qui regarde ?");
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
                @Override public void run() { chosen(a, o, col, p); }
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) cl.leftMargin = a.u(44);
            row.addView(card, cl);
            if (focus == null || p.id == a.prefs.activeProfile()) focus = card;
            // entrée en douceur, l'une après l'autre
            card.setAlpha(0f);
            card.setTranslationY(a.u(24));
            card.animate().alpha(1f).translationY(0).setStartDelay(120 + i * 70L).setDuration(500)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }

        if (withManage) {
            TextView manage = a.text(22, "sans-serif-medium", 0x99FFFFFF);
            manage.setText("Gérer les profils");
            manage.setPadding(a.u(28), a.u(14), a.u(28), a.u(14));
            manage.setFocusable(true);
            manage.setClickable(true);
            final GradientDrawable mb = new GradientDrawable();
            mb.setCornerRadius(a.u(30));
            mb.setColor(0x00FFFFFF);
            manage.setBackground(mb);
            manage.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean f) {
                    mb.setColor(f ? 0xFFF5F5F7 : 0x00FFFFFF);
                    ((TextView) v).setTextColor(f ? 0xFF111216 : 0x99FFFFFF);
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
        title.animate().alpha(1f).setDuration(600).start();
        if (focus != null) focus.requestFocus();
    }

    /** Une personne : grande pastille ronde avec l'initiale, prénom dessous. */
    private static View person(final MainActivity a, Profiles.Profile p, final Runnable onPick) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setFocusable(true);
        box.setClickable(true);
        box.setSoundEffectsEnabled(false);
        box.setClipChildren(false);

        final int size = a.u(176);
        final TextView disc = a.text(72, "sans-serif-light", 0xFFFFFFFF);
        disc.setText(initial(p.name));
        disc.setGravity(Gravity.CENTER);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        int c = COLORS[Math.abs(p.id) % COLORS.length];
        bg.setColors(new int[]{c, darker(c)});
        bg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        bg.setOrientation(GradientDrawable.Orientation.TL_BR);
        disc.setBackground(bg);
        disc.setElevation(a.u(6));
        box.addView(disc, new LinearLayout.LayoutParams(size, size));

        final TextView name = a.text(28, "sans-serif", 0x99FFFFFF);
        name.setText(p.name);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = a.u(22);
        box.addView(name, nl);

        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick();
                disc.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).setDuration(240)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
                bg.setStroke(f ? a.u(5) : 0, 0xFFF5F5F7);
                disc.setElevation(f ? a.u(24) : a.u(6));
                name.setTextColor(f ? 0xFFFFFFFF : 0x99FFFFFF);
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
        page.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(320).start();

        final TextView hello = a.text(84, "sans-serif-thin", 0xFFFFFFFF);
        hello.setText(Greeting.hello(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) + " " + p.name);
        hello.setGravity(Gravity.CENTER);
        hello.setLetterSpacing(0.02f);
        hello.setAlpha(0f);
        hello.setScaleX(0.94f);
        hello.setScaleY(0.94f);
        o.addView(hello, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        hello.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(220).setDuration(700)
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

    private static int darker(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return 0xFF000000 | ((int) (r * 0.72f) << 16) | ((int) (g * 0.72f) << 8) | (int) (b * 0.72f);
    }
}

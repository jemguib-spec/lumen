// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ouverture des applis (écran « lumen » puis fondu, code si verrouillée), fermeture d'une appli bloquée, redémarrage de Lumen. */
final class AppLauncher {
    private final MainActivity a;

    AppLauncher(MainActivity a) { this.a = a; }

    /** La vignette s'agrandit jusqu'à remplir l'écran, puis l'appli s'ouvre. */
    void launchAnimated(final AppRepo.App app, final Tile from) {
        whenUnlocked(app, new Runnable() { @Override public void run() { launchUnlocked(app, from); } });
    }

    /** Lance « r » tout de suite, ou après le code si l'appli est verrouillée. */
    void whenUnlocked(AppRepo.App app, Runnable r) {
        if (a.prefs.isLocked(app.key)) PinDialog.ask(a, "Code pour " + app.label, r);
        else r.run();
    }

    /**
     * Ouverture d'une appli : le même écran que le démarrage de Lumen — fond noir, « lumen » en
     * lettres fines — avec le nom de l'appli écrit dessous ; puis l'appli s'ouvre en fondu.
     */
    void launchUnlocked(final AppRepo.App app, Tile from) {
        Sounds.get(a).open();
        if (a.zoomView != null) { a.root.removeView(a.zoomView); a.zoomView = null; }
        if (a.root.getWidth() == 0) { launch(app); return; }
        // avec « Lumen télécommande » : l'écran lumen s'affiche par-dessus l'appli, qui se charge
        // derrière tout de suite (un seul chargement visible)
        if (RemoteService.showSplash(app.label, 1500)) {
            launch(app);
            a.overridePendingTransition(0, 0);
            return;
        }
        FrameLayout z = new FrameLayout(a);
        z.setBackgroundColor(Color.BLACK);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView word = a.text(84, "sans-serif-thin", 0xFFFFFFFF);
        word.setText("lumen");
        word.setLetterSpacing(0.35f);
        col.addView(word, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView name = a.text(24, "sans-serif-light", 0x99FFFFFF);
        name.setText(app.label.toUpperCase(java.util.Locale.FRANCE));
        name.setLetterSpacing(0.3f);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = a.u(18);
        col.addView(name, nl);
        z.addView(col, MainActivity.wrap(Gravity.CENTER));
        z.setAlpha(0f);
        z.setElevation(a.u(60));
        a.root.addView(z, MainActivity.match());
        DecelerateInterpolator di = new DecelerateInterpolator(1.6f);
        z.animate().alpha(1f).setDuration(280).setInterpolator(di).start();
        word.setAlpha(0f);
        word.setScaleX(0.96f);
        word.setScaleY(0.96f);
        word.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(600).setInterpolator(di).start();
        name.setAlpha(0f);
        name.setTranslationY(a.u(8));
        name.animate().alpha(1f).translationY(0).setStartDelay(320).setDuration(500).setInterpolator(di).start();
        if (from != null) from.animate().scaleX(1.12f).scaleY(1.12f).alpha(0f).setDuration(280).setInterpolator(di).start();
        final Tile src = from;
        a.zoomView = z;
        a.h.postDelayed(new Runnable() {
            @Override public void run() {
                launch(app);
                a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                if (src != null) src.restoreAfterLaunch(); // la vignette reprend sa place pour le retour
            }
        }, 900);
        // filet de sécurité si l'appli ne s'ouvre pas
        a.h.postDelayed(new Runnable() {
            @Override public void run() {
                if (a.zoomView != null && a.hasWindowFocus()) { a.root.removeView(a.zoomView); a.zoomView = null; }
            }
        }, 4000);
    }

    /**
     * Ferme une appli qui ne répond plus : Android arrête ses processus une fois qu'elle n'est plus
     * à l'écran (Lumen y est revenu). Elle redémarrera proprement à la prochaine ouverture.
     */
    boolean closeApp(AppRepo.App app) {
        if (app == null || app.isInput() || app.pkg.equals(a.getPackageName())) return false;
        try {
            ((android.app.ActivityManager) a.getSystemService(android.content.Context.ACTIVITY_SERVICE)).killBackgroundProcesses(app.pkg);
            return true;
        } catch (Exception e) { return false; }
    }

    /** Redémarre Lumen (en cas de souci) : Android relance l'écran d'accueil aussitôt. */
    void restartLumen() {
        try {
            Intent i = new Intent(a, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(a, 7, i,
                    android.app.PendingIntent.FLAG_CANCEL_CURRENT | 0x04000000 /* FLAG_IMMUTABLE */);
            ((android.app.AlarmManager) a.getSystemService(android.content.Context.ALARM_SERVICE)).set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 250, pi);
        } catch (Exception ignored) { }
        a.finishAffinity();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    void launch(AppRepo.App app) {
        a.lastLaunched = app;
        if (a.cinema != null) a.cinema.onLaunch(app.pkg);
        a.prefs.addRecent(app.key);
        a.prefs.recordHabit(app.key);
        try {
            a.startActivity(app.launchIntent());
        } catch (ActivityNotFoundException | SecurityException e) {
            Intent i = a.getPackageManager().getLeanbackLaunchIntentForPackage(app.pkg);
            if (i == null) i = a.getPackageManager().getLaunchIntentForPackage(app.pkg);
            try {
                if (i == null) throw new ActivityNotFoundException();
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(i);
            } catch (Exception e2) {
                a.showNotice("Impossible d'ouvrir " + app.label);
            }
        }
    }
}

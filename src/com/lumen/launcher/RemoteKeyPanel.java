// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Intent;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * « Relier ce bouton ? » : quand on appuie sur une touche de la télécommande que Lumen ne connaît
 * pas, ce panneau propose de lui donner une fonction (réglages, retour, une appli…). Il lance aussi
 * la fonction d'une touche déjà reliée. Les règles (quelles touches, quelles fonctions) : RemoteKeys.
 */
final class RemoteKeyPanel {
    private RemoteKeyPanel() { }

    /** Depuis l'accueil : ouvre le choix de la fonction pour la touche qui vient d'être pressée. */
    static void ask(final MainActivity a, final String id) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.glassCard(Radius.CARD);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(a.u(760), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        cl.topMargin = cl.bottomMargin = cl.rightMargin = a.u(48);
        o.addView(card, cl);
        actions(a, card, id, false);
    }

    /**
     * Remplit « card » avec les fonctions possibles. inSettings : on est dans la carte des réglages
     * (déjà ouverts avec le code), on revient à la page Télécommande après le choix ; sinon le
     * panneau se ferme et le code PIN est demandé avant d'enregistrer.
     */
    static void actions(final MainActivity a, final LinearLayout card, final String id, final boolean inSettings) {
        card.removeAllViews();
        a.rowRefreshers.clear();
        LinearLayout col = SettingsPanel.scrollColumn(a, card, 44);
        final String current = a.prefs.remoteKeys().get(id);
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText((inSettings ? "‹  " : "") + RemoteKeys.name(id));
        title.setPadding(a.u(32), 0, a.u(32), a.u(8));
        col.addView(title);
        TextView hint = a.text(22, "sans-serif", Palette.TEXT_2);
        hint.setText(current == null
                ? L.t("Cette touche n'a pas encore de rôle dans Lumen. Veux-tu la relier à une fonction ?")
                : L.t("Reliée à : ") + label(a, current));
        hint.setLineSpacing(0, 1.25f);
        hint.setPadding(a.u(32), 0, a.u(32), a.u(20));
        col.addView(hint);

        View first = null;
        for (final String action : RemoteKeys.ACTIONS) {
            if (RemoteKeys.HOME.equals(action) && !a.prefs.haConfigured()) continue; // pas de Home Assistant : pas de page Maison
            LinearLayout r = a.iconRow(glyph(action), RemoteKeys.actionName(action), null, new View.OnClickListener() {
                @Override public void onClick(View v) { save(a, card, id, action, inSettings); }
            }, false);
            col.addView(r, a.rowLp());
            if (first == null || action.equals(current)) first = r;
        }
        LinearLayout app = a.iconRow(Glyph.GRID, L.t("Ouvrir une appli"), new MainActivity.Value() {
            @Override public String get() { return "›"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { apps(a, card, id, inSettings); }
        }, false);
        col.addView(app, a.rowLp());
        if (RemoteKeys.isApp(current)) first = app;
        col.addView(a.menuDivider());
        LinearLayout none = a.iconRow(Glyph.CLOSE, L.t("Ne rien faire"), new MainActivity.Value() {
            @Override public String get() { return L.t("ne plus demander"); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { save(a, card, id, RemoteKeys.NONE, inSettings); }
        }, false);
        col.addView(none, a.rowLp());
        if (inSettings) {
            col.addView(a.iconRow(Glyph.TRASH, L.t("Oublier cette touche"), null, new View.OnClickListener() {
                @Override public void onClick(View v) { save(a, card, id, null, inSettings); }
            }, true), a.rowLp());
        }
        TextView foot = a.text(21, "sans-serif", Palette.TEXT_3);
        foot.setText(L.t("À changer quand tu veux dans Réglages › Télécommande."));
        foot.setLineSpacing(0, 1.25f);
        foot.setPadding(a.u(32), a.u(28), a.u(32), 0);
        col.addView(foot);
        if (first != null) first.requestFocus();
    }

    /** Deuxième étape : l'appli à ouvrir (celles que le profil a le droit de voir). */
    private static void apps(final MainActivity a, final LinearLayout card, final String id, final boolean inSettings) {
        card.removeAllViews();
        a.rowRefreshers.clear();
        LinearLayout col = SettingsPanel.scrollColumn(a, card, 48);
        LinearLayout back = a.listRow("‹  " + RemoteKeys.name(id), null, new View.OnClickListener() {
            @Override public void onClick(View v) { actions(a, card, id, inSettings); }
        });
        col.addView(back, a.rowLp());
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText(L.t("Ouvrir une appli"));
        title.setPadding(a.u(32), a.u(24), 0, a.u(20));
        col.addView(title);
        final String current = RemoteKeys.appKey(a.prefs.remoteKeys().get(id));
        View focus = back;
        for (final AppRepo.App app : a.repo.all()) {
            if (!a.prefs.appAllowed(app.key) || !RemoteKeys.validAction(RemoteKeys.APP_PREFIX + app.key)) continue;
            LinearLayout r = a.listRow(app.label, null, new View.OnClickListener() {
                @Override public void onClick(View v) { save(a, card, id, RemoteKeys.APP_PREFIX + app.key, inSettings); }
            });
            col.addView(r, a.rowLp());
            if (app.key.equals(current)) focus = r;
        }
        focus.requestFocus();
    }

    /** Enregistre le choix (action null : la touche est oubliée). Relier une touche est un réglage : code PIN s'il existe. */
    private static void save(final MainActivity a, final LinearLayout card, final String id, final String action, final boolean inSettings) {
        Runnable r = new Runnable() {
            @Override public void run() {
                a.prefs.setRemoteKey(id, action);
                if (inSettings) SettingsPanel.page(a, card, SettingsPanel.TOUCHES); else a.closeOverlay(true);
                String name = RemoteKeys.name(id);
                a.showNotice(action == null ? name + L.t(" : oubliée")
                        : RemoteKeys.NONE.equals(action) ? name + L.t(" : Lumen ne demandera plus")
                        : name + "  →  " + label(a, action), 3200);
            }
        };
        if (inSettings) r.run(); else PinDialog.ask(a, L.t("Code pour les réglages"), r);
    }

    /** Nom de la fonction reliée, avec le nom de l'appli quand c'en est une. */
    static String label(MainActivity a, String action) {
        if (!RemoteKeys.isApp(action)) return RemoteKeys.actionName(action);
        AppRepo.App app = a.repo.get(RemoteKeys.appKey(action));
        return app != null ? app.label : L.t("Appli désinstallée");
    }

    private static int glyph(String action) {
        switch (action) {
            case RemoteKeys.SETTINGS: return Glyph.SLIDERS;
            case RemoteKeys.APPS: return Glyph.GRID;
            case RemoteKeys.SEARCH: return Glyph.MIC;
            case RemoteKeys.RESUME: return Glyph.PLAY;
            case RemoteKeys.RECO: return Glyph.SPARK;
            case RemoteKeys.HOME: return Glyph.HOME;
            case RemoteKeys.VEILLE: return Glyph.MOON;
            case RemoteKeys.AMBIANCE: return Glyph.IMAGE;
            case RemoteKeys.SOUNDS: return Glyph.SOUND;
            case RemoteKeys.TV_SETTINGS: return Glyph.GEAR;
            default: return Glyph.RESTART; // retour
        }
    }

    /** Lance la fonction reliée à une touche. */
    static void run(final MainActivity a, String action) {
        if (RemoteKeys.isApp(action)) {
            AppRepo.App app = a.repo.get(RemoteKeys.appKey(action));
            // même règle que partout : une appli masquée ou hors du profil enfant ne s'ouvre pas, une appli verrouillée demande le code
            if (app == null || !a.prefs.appAllowed(app.key)) { a.showNotice(L.t("Cette appli n'est plus disponible · Réglages › Télécommande")); return; }
            a.launchAnimated(app, null);
            return;
        }
        switch (action) {
            case RemoteKeys.SETTINGS: SettingsPanel.openSettings(a); break;
            case RemoteKeys.BACK: a.onBackPressed(); break;
            case RemoteKeys.APPS: a.openApps(); break;
            case RemoteKeys.SEARCH: a.startVoiceSearch(); break;
            case RemoteKeys.RESUME: ResumePanel.openResume(a); break;
            case RemoteKeys.RECO: RecoPanel.open(a); break;
            case RemoteKeys.HOME:
                if (a.prefs.haConfigured()) HomePanel.open(a); else a.showNotice(L.t("Home Assistant n'est pas relié"));
                break;
            case RemoteKeys.VEILLE: a.enterAmbient(); break;
            case RemoteKeys.AMBIANCE: a.openAmbiance(); break;
            case RemoteKeys.SOUNDS: a.toggleSounds(); break;
            case RemoteKeys.TV_SETTINGS:
                // les paramètres de la TV sont derrière le code, comme dans Réglages › Système › Diagnostic
                TvQuickPanel.openLocked(a);
                break;
            default: break;
        }
    }
}

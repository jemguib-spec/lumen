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
 * Paramètres de la TV en un appui (bouton rond de l'accueil) : réseau, Bluetooth, son, image,
 * entrées, puis tous les paramètres d'Android. Chaque ligne ouvre l'écran d'Android qui correspond ;
 * si le fabricant ne le propose pas, on tombe sur les paramètres complets.
 */
final class TvQuickPanel {
    private TvQuickPanel() { }

    /** Derrière le code PIN s'il y en a un, comme les réglages de Lumen. */
    static void openLocked(final MainActivity a) {
        PinDialog.ask(a, L.t("Code pour les réglages"), new Runnable() {
            @Override public void run() { open(a); }
        });
    }

    private static void open(final MainActivity a) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.menuCard(40);
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText(L.t("Paramètres de la TV"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, a.u(30));
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout first = row(a, card, Glyph.WIFI, L.t("Réseau et Internet"), L.t("Wi-Fi, câble"),
                Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS);
        row(a, card, Glyph.BLUETOOTH, L.t("Télécommandes et accessoires"), L.t("Bluetooth"),
                Settings.ACTION_BLUETOOTH_SETTINGS);
        row(a, card, Glyph.SOUND, L.t("Son"), L.t("Sortie audio, enceintes"),
                Settings.ACTION_SOUND_SETTINGS);
        row(a, card, Glyph.PICTURE, L.t("Image et écran"), L.t("Résolution, mode d'image"),
                Settings.ACTION_DISPLAY_SETTINGS);
        if (!InputsPanel.inputs(a).isEmpty()) {
            LinearLayout r = a.iconRow(Glyph.INPUT, L.t("Entrées"), new MainActivity.Value() {
                @Override public String get() { return "HDMI   ›"; }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(false); InputsPanel.openInputs(a); }
            }, false);
            card.addView(r, a.rowLp());
        }
        row(a, card, Glyph.GEAR, L.t("Tous les paramètres"), null, Settings.ACTION_SETTINGS);

        o.addView(card, new FrameLayout.LayoutParams(a.u(720), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        first.requestFocus();
    }

    private static LinearLayout row(final MainActivity a, LinearLayout card, int glyph, String label, final String detail, final String... actions) {
        LinearLayout r = a.iconRow(glyph, label, detail == null ? null : new MainActivity.Value() {
            @Override public String get() { return detail; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.closeOverlay(false);
                if (!launch(a, actions)) a.showNotice(L.t("Paramètres indisponibles"));
            }
        }, false);
        card.addView(r, a.rowLp());
        return r;
    }

    /** Ouvre le premier écran d'Android disponible, sinon les paramètres complets. */
    static boolean launch(MainActivity a, String... actions) {
        String[] all = new String[actions.length + 1];
        System.arraycopy(actions, 0, all, 0, actions.length);
        all[actions.length] = Settings.ACTION_SETTINGS;
        for (String action : all) {
            try {
                Intent i = new Intent(action);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (i.resolveActivity(a.getPackageManager()) == null) continue;
                a.startActivity(i);
                return true;
            } catch (Exception ignored) { }
        }
        return false;
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Saisie d'une clé d'API directement sur la TV (TMDB pour « À l'affiche », Pexels pour les vidéos),
 * avec le clavier de la TV. Le téléphone reste plus pratique pour coller, mais n'est plus obligatoire.
 * La clé est vérifiée (format) puis gardée sur la TV ; elle n'est jamais réaffichée en entier.
 */
final class KeyPanel {
    private KeyPanel() { }

    static final int TMDB = 0, PEXELS = 1;

    static void open(final MainActivity a, final int which) {
        final boolean tmdb = which == TMDB;
        final boolean has = tmdb ? !a.prefs.tmdbKey().isEmpty() : !a.prefs.pexelsKey().isEmpty();
        final Runnable proceed = new Runnable() {
            @Override public void run() { show(a, tmdb, has); }
        };
        // changer une clé est une action sensible : code PIN s'il existe
        if (a.prefs.hasPin()) PinDialog.ask(a, "Code PIN", proceed); else proceed.run();
    }

    private static void show(final MainActivity a, final boolean tmdb, boolean has) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.glassCard(44);
        card.setPadding(a.u(40), a.u(44), a.u(40), a.u(32));
        TextView title = a.text(44, "sans-serif-light", 0xF5FFFFFF);
        title.setText(tmdb ? "Films du moment" : "Vidéos Pexels");
        card.addView(title);
        TextView hint = a.text(22, "sans-serif", 0x8CFFFFFF);
        hint.setText(tmdb
                ? "Clé gratuite sur themoviedb.org › Compte › Paramètres › API : la « Clé API » (32 caractères) ou le « Jeton d'accès en lecture ». Sans clé, Lumen ne contacte jamais TMDB."
                : "Clé gratuite sur pexels.com/api. Sans clé, Lumen ne contacte jamais Pexels.");
        hint.setPadding(0, a.u(12), 0, a.u(28));
        card.addView(hint);

        final android.widget.EditText in = new android.widget.EditText(a);
        in.setSingleLine(true);
        in.setHint(has ? "Nouvelle clé (une clé est déjà enregistrée)" : "Colle ou tape la clé");
        in.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28 * a.unit);
        in.setTypeface(Fonts.get("sans-serif", android.graphics.Typeface.NORMAL));
        in.setTextColor(0xFF111216);
        in.setHintTextColor(0x80111216);
        in.setPadding(a.u(28), a.u(20), a.u(28), a.u(20));
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        GradientDrawable ib = new GradientDrawable();
        ib.setColor(0xFFF5F5F7);
        ib.setCornerRadius(a.u(22));
        in.setBackground(ib);
        in.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        in.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent ev) { save(a, tmdb, in.getText().toString()); return true; }
        });
        card.addView(in, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(new View(a), new LinearLayout.LayoutParams(1, a.u(20)));
        card.addView(a.iconRow(Glyph.UNLOCK, "Enregistrer", null, new View.OnClickListener() {
            @Override public void onClick(View v) { save(a, tmdb, in.getText().toString()); }
        }, false), a.rowLp());
        if (has) {
            card.addView(a.iconRow(Glyph.TRASH, "Retirer la clé", null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (tmdb) a.prefs.setTmdbKey(""); else a.prefs.setPexelsKey("");
                    a.closeOverlay(true);
                    a.showNotice(tmdb ? "Films du moment désactivés" : "Pexels désactivé");
                }
            }, true), a.rowLp());
        }
        card.addView(a.iconRow(Glyph.PHONE, "Plutôt depuis le téléphone (pour coller)", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(false); PhonePanel.openPhonePanel(a); }
        }, false), a.rowLp());
        o.addView(card, new FrameLayout.LayoutParams(a.u(900), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        in.requestFocus();
    }

    private static void save(MainActivity a, boolean tmdb, String raw) {
        String k = raw == null ? "" : raw.trim().replace(" ", "");
        if (k.isEmpty()) return;
        boolean ok = tmdb ? TmdbPolicy.validKey(k) : Pexels.validKey(k);
        if (!ok) { a.showNotice("Clé non reconnue · vérifie qu'elle est complète"); return; }
        if (tmdb) a.prefs.setTmdbKey(k); else a.prefs.setPexelsKey(k);
        a.closeOverlay(true);
        a.showNotice(tmdb ? "Clé enregistrée · « À l'affiche » est prêt" : "Clé Pexels enregistrée");
        if (tmdb) a.h.postDelayed(new Runnable() { @Override public void run() { RecoPanel.open(a); } }, 900);
    }
}

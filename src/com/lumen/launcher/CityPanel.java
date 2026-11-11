// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;


/** Saisie de la ville pour la météo (clavier de la TV). */
final class CityPanel {
    private CityPanel() { }


    static void openCityInput(final MainActivity a) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.formCard(44, true); // la dernière ligne est toujours une ligne d'action
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText(L.t("Ville pour la météo"));
        card.addView(title);
        TextView hint = a.text(22, "sans-serif", Palette.TEXT_2);
        hint.setText(L.t("Seul le nom de la ville est envoyé à Open-Meteo, service météo gratuit et sans compte."));
        hint.setPadding(0, a.u(12), 0, a.u(28));
        card.addView(hint);
        final android.widget.EditText in = new android.widget.EditText(a);
        in.setSingleLine(true);
        in.setHint(L.t("Ex. Paris"));
        in.setText(a.weather.city());
        in.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32 * a.unit);
        in.setTextColor(Palette.ON_SELECTED);
        in.setHintTextColor(Palette.ON_SELECTED_2);
        in.setPadding(a.u(28), a.u(20), a.u(28), a.u(20));
        in.setBackground(a.fieldBackground());
        in.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        // valider : touche « OK » ou « Terminé » du clavier, Entrée, ou la ligne « Valider » dessous
        // (beaucoup de claviers de TV n'envoient pas l'action « Terminé » : la ligne reste le chemin sûr)
        final Runnable submit = new Runnable() {
            @Override public void run() {
                final String q = in.getText().toString().trim();
                hideKeyboard(a, in);
                if (q.isEmpty()) { a.showNotice(L.t("Tape le nom d'une ville")); return; }
                a.closeOverlay(true);
                a.showNotice(L.t("Recherche de « ") + q + " »…");
                a.weather.setCity(q, new Weather.Callback() {
                    @Override public void onWeather(String found) {
                        a.showNotice(found == null ? L.t("Ville introuvable") : L.t("Météo : ") + found);
                    }
                });
            }
        };
        in.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent ev) { submit.run(); return true; }
        });
        in.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (code != KeyEvent.KEYCODE_ENTER && code != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
                if (e.getAction() == KeyEvent.ACTION_UP) submit.run();
                return true;
            }
        });
        card.addView(in, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(new View(a), new LinearLayout.LayoutParams(1, a.u(20)));
        card.addView(a.iconRow(Glyph.SEARCH, L.t("Valider"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { submit.run(); }
        }, false), a.rowLp());
        if (a.weather.hasCity()) {
            card.addView(a.listRow(L.t("Désactiver la météo"), null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.weather.clearCity(); a.closeOverlay(true); a.showNotice("Météo désactivée"); }
            }), a.rowLp());
        }
        o.addView(card, new FrameLayout.LayoutParams(a.u(820), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        in.requestFocus();
        in.setSelection(in.getText().length());
        try {
            ((android.view.inputmethod.InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(in, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        } catch (Exception ignored) { }
    }

    static void hideKeyboard(Context c, View v) {
        try {
            ((android.view.inputmethod.InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }
}

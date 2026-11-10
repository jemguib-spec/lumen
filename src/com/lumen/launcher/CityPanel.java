// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
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
        LinearLayout card = a.glassCard(44);
        card.setPadding(a.u(40), a.u(44), a.u(40), a.u(32));
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText("Ville pour la météo");
        card.addView(title);
        TextView hint = a.text(22, "sans-serif", Palette.TEXT_2);
        hint.setText("Seul le nom de la ville est envoyé à Open-Meteo, service météo gratuit et sans compte.");
        hint.setPadding(0, a.u(12), 0, a.u(28));
        card.addView(hint);
        final android.widget.EditText in = new android.widget.EditText(a);
        in.setSingleLine(true);
        in.setHint("Ex. Paris");
        in.setText(a.weather.city());
        in.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32 * a.unit);
        in.setTextColor(Palette.ON_SELECTED);
        in.setHintTextColor(Palette.ON_SELECTED_2);
        in.setPadding(a.u(28), a.u(20), a.u(28), a.u(20));
        GradientDrawable ib = new GradientDrawable();
        ib.setColor(Palette.SELECTED);
        ib.setCornerRadius(a.u(22));
        in.setBackground(ib);
        in.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        in.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent ev) {
                final String q = in.getText().toString().trim();
                a.closeOverlay(true);
                if (q.isEmpty()) return true;
                a.showNotice("Recherche de « " + q + " »…");
                a.weather.setCity(q, new Weather.Callback() {
                    @Override public void onWeather(String found) {
                        a.showNotice(found == null ? "Ville introuvable" : "Météo : " + found);
                    }
                });
                return true;
            }
        });
        card.addView(in, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View spacer = new View(a);
        card.addView(spacer, new LinearLayout.LayoutParams(1, a.u(20)));
        if (a.weather.hasCity()) {
            card.addView(a.listRow("Désactiver la météo", null, new View.OnClickListener() {
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

}

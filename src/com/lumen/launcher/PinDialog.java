// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Saisie du code PIN : pavé à la télécommande (flèches + OK) ou touches chiffrées.
 * Vérification par Pin (empreinte salée), attente croissante après 5 erreurs.
 */
final class PinDialog {
    private PinDialog() { }

    /** Demande le code si un code existe, puis lance « ok ». Sans code, « ok » part tout de suite. */
    static void ask(final MainActivity a, String title, final Runnable ok) {
        if (!a.prefs.hasPin()) { ok.run(); return; }
        show(a, title, new Entered() {
            @Override public boolean on(String pin, TextView msg) {
                long wait = waitLeft(a);
                if (wait > 0) { msg.setText(L.t("Trop d'essais · réessayez dans ") + seconds(wait)); return false; }
                if (Pin.check(pin, a.prefs.pinStored())) {
                    a.prefs.pinOk();
                    a.closeOverlay(false);
                    ok.run();
                    return true;
                }
                a.prefs.pinFailed();
                long w = waitLeft(a);
                msg.setText(w > 0 ? L.t("Code incorrect · attendez ") + seconds(w) : L.t("Code incorrect"));
                return false;
            }
        });
    }

    /** Choix d'un nouveau code (saisi deux fois). */
    static void create(final MainActivity a, final Runnable done) {
        final String[] first = {null};
        show(a, L.t("Nouveau code à 4 chiffres"), new Entered() {
            @Override public boolean on(String pin, TextView msg) {
                if (first[0] == null) { first[0] = pin; msg.setText(L.t("Saisissez-le une seconde fois")); return false; }
                if (!first[0].equals(pin)) { first[0] = null; msg.setText(L.t("Les deux codes diffèrent · recommencez")); return false; }
                a.prefs.setPin(pin);
                a.closeOverlay(false);
                done.run();
                return true;
            }
        });
    }

    /** Code saisi en entier ; true s'il est accepté (le pavé se ferme), sinon les points se vident. */
    interface Entered { boolean on(String pin, TextView msg); }

    /** Saisie d'un code chiffré d'une autre longueur (code affiché par une autre TV). */
    static void code(MainActivity a, String title, int length, Entered entered) { show(a, title, length, entered); }

    private static long waitLeft(MainActivity a) {
        long lock = Pin.lockMillis(a.prefs.pinFails());
        return lock <= 0 ? 0 : Math.max(0, a.prefs.pinLastFail() + lock - System.currentTimeMillis());
    }

    private static String seconds(long ms) {
        long s = (ms + 999) / 1000;
        return s < 60 ? s + L.t(" s") : (s + 59) / 60 + L.t(" min");
    }

    private static void show(MainActivity a, String title, Entered entered) { show(a, title, Pin.LENGTH, entered); }

    private static void show(final MainActivity a, String title, final int length, final Entered entered) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.glassCard(Radius.FORM);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        // les touches ont leur écart autour d'elles : carte + écart = FORM_PAD jusqu'aux touches des coins
        final int side = a.u(Radius.FORM_PAD - Radius.KEY_GAP);
        card.setPadding(side, a.u(44), side, side);

        TextView t = a.text(36, "sans-serif-light", Palette.TEXT);
        t.setText(title);
        t.setGravity(Gravity.CENTER);
        card.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView msg = a.text(22, "sans-serif", Palette.TEXT_2);
        msg.setGravity(Gravity.CENTER);
        msg.setText(L.t("Flèches et OK, ou touches chiffrées"));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = a.u(14);
        card.addView(msg, ml);

        final LinearLayout dots = new LinearLayout(a);
        dots.setGravity(Gravity.CENTER);
        final GradientDrawable[] dot = new GradientDrawable[length];
        for (int i = 0; i < length; i++) {
            View d = new View(a);
            dot[i] = new GradientDrawable();
            dot[i].setShape(GradientDrawable.OVAL);
            dot[i].setStroke(Math.max(1, a.u(3)), 0xE6FFFFFF);
            d.setBackground(dot[i]);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(a.u(26), a.u(26));
            dl.leftMargin = dl.rightMargin = a.u(14);
            dots.addView(d, dl);
        }
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = a.u(34);
        dlp.bottomMargin = a.u(30);
        card.addView(dots, dlp);

        final StringBuilder typed = new StringBuilder();
        final Runnable paint = new Runnable() {
            @Override public void run() {
                for (int i = 0; i < length; i++) dot[i].setColor(i < typed.length() ? Palette.SELECTED : 0x00000000);
            }
        };
        paint.run();
        final Digit digit = new Digit() {
            @Override public void press(int d) {
                if (d < 0) { if (typed.length() > 0) typed.setLength(typed.length() - 1); paint.run(); return; }
                if (typed.length() >= length) return;
                typed.append((char) ('0' + d));
                paint.run();
                if (typed.length() == length) {
                    final String pin = typed.toString();
                    typed.setLength(0);
                    a.h.postDelayed(new Runnable() {
                        @Override public void run() { if (!entered.on(pin, msg)) paint.run(); }
                    }, 120);
                }
            }
        };

        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", ""};
        View firstKey = null;
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER);
            for (int c = 0; c < 3; c++) {
                String k = keys[r * 3 + c];
                View cellView;
                if (k.isEmpty()) cellView = new View(a);
                else {
                    final int value = k.equals("⌫") ? -1 : k.charAt(0) - '0';
                    cellView = key(a, k, value, digit);
                    if (firstKey == null && value == 5) firstKey = cellView;
                }
                // les touches occupent toute la largeur : celles des coins sont à FORM_PAD des bords
                LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(0, a.u(84), 1f);
                int gap = a.u(Radius.KEY_GAP);
                kl.setMargins(gap, gap, gap, gap);
                row.addView(cellView, kl);
            }
            card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        o.addView(card, new FrameLayout.LayoutParams(a.u(560), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        if (firstKey != null) firstKey.requestFocus();
    }

    private interface Digit { void press(int d); }

    private static TextView key(final MainActivity a, String label, final int value, final Digit digit) {
        final TextView k = a.text(36, "sans-serif", Palette.TEXT);
        k.setText(label);
        k.setGravity(Gravity.CENTER);
        k.setFocusable(true);
        k.setClickable(true);
        k.setSoundEffectsEnabled(false);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.u(Radius.ROW));
        bg.setColor(0x1FFFFFFF);
        k.setBackground(bg);
        k.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick();
                bg.setColor(f ? Palette.SELECTED : 0x1FFFFFFF);
                k.setTextColor(f ? Palette.ON_SELECTED : Palette.TEXT);
            }
        });
        k.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Sounds.get(a).tock(); digit.press(value); }
        });
        // touches chiffrées et « effacer » de la télécommande, quelle que soit la touche sélectionnée
        k.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) { digit.press(code - KeyEvent.KEYCODE_0); return true; }
                if (code == KeyEvent.KEYCODE_DEL) { digit.press(-1); return true; }
                return false;
            }
        });
        return k;
    }
}

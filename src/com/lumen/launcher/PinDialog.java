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
                if (wait > 0) { msg.setText("Trop d'essais · réessayez dans " + seconds(wait)); return false; }
                if (Pin.check(pin, a.prefs.pinStored())) {
                    a.prefs.pinOk();
                    a.closeOverlay(false);
                    ok.run();
                    return true;
                }
                a.prefs.pinFailed();
                long w = waitLeft(a);
                msg.setText(w > 0 ? "Code incorrect · attendez " + seconds(w) : "Code incorrect");
                return false;
            }
        });
    }

    /** Choix d'un nouveau code (saisi deux fois). */
    static void create(final MainActivity a, final Runnable done) {
        final String[] first = {null};
        show(a, "Nouveau code à 4 chiffres", new Entered() {
            @Override public boolean on(String pin, TextView msg) {
                if (first[0] == null) { first[0] = pin; msg.setText("Saisissez-le une seconde fois"); return false; }
                if (!first[0].equals(pin)) { first[0] = null; msg.setText("Les deux codes diffèrent · recommencez"); return false; }
                a.prefs.setPin(pin);
                a.closeOverlay(false);
                done.run();
                return true;
            }
        });
    }

    private interface Entered { boolean on(String pin, TextView msg); }

    private static long waitLeft(MainActivity a) {
        long lock = Pin.lockMillis(a.prefs.pinFails());
        return lock <= 0 ? 0 : Math.max(0, a.prefs.pinLastFail() + lock - System.currentTimeMillis());
    }

    private static String seconds(long ms) {
        long s = (ms + 999) / 1000;
        return s < 60 ? s + " s" : (s + 59) / 60 + " min";
    }

    private static void show(final MainActivity a, String title, final Entered entered) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.glassCard(44);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(a.u(40), a.u(44), a.u(40), a.u(40));

        TextView t = a.text(36, "sans-serif-light", 0xF5FFFFFF);
        t.setText(title);
        t.setGravity(Gravity.CENTER);
        card.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView msg = a.text(22, "sans-serif", 0xA6FFFFFF);
        msg.setGravity(Gravity.CENTER);
        msg.setText("Flèches et OK, ou touches chiffrées");
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = a.u(14);
        card.addView(msg, ml);

        final LinearLayout dots = new LinearLayout(a);
        dots.setGravity(Gravity.CENTER);
        final GradientDrawable[] dot = new GradientDrawable[Pin.LENGTH];
        for (int i = 0; i < Pin.LENGTH; i++) {
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
                for (int i = 0; i < Pin.LENGTH; i++) dot[i].setColor(i < typed.length() ? 0xFFF5F5F7 : 0x00000000);
            }
        };
        paint.run();
        final Digit digit = new Digit() {
            @Override public void press(int d) {
                if (d < 0) { if (typed.length() > 0) typed.setLength(typed.length() - 1); paint.run(); return; }
                if (typed.length() >= Pin.LENGTH) return;
                typed.append((char) ('0' + d));
                paint.run();
                if (typed.length() == Pin.LENGTH) {
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
                LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(a.u(116), a.u(84));
                kl.setMargins(a.u(8), a.u(8), a.u(8), a.u(8));
                row.addView(cellView, kl);
            }
            card.addView(row);
        }
        o.addView(card, new FrameLayout.LayoutParams(a.u(560), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        if (firstKey != null) firstKey.requestFocus();
    }

    private interface Digit { void press(int d); }

    private static TextView key(final MainActivity a, String label, final int value, final Digit digit) {
        final TextView k = a.text(36, "sans-serif", 0xEBFFFFFF);
        k.setText(label);
        k.setGravity(Gravity.CENTER);
        k.setFocusable(true);
        k.setClickable(true);
        k.setSoundEffectsEnabled(false);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.u(22));
        bg.setColor(0x1FFFFFFF);
        k.setBackground(bg);
        k.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick();
                bg.setColor(f ? 0xFFF5F5F7 : 0x1FFFFFFF);
                k.setTextColor(f ? 0xFF111216 : 0xEBFFFFFF);
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

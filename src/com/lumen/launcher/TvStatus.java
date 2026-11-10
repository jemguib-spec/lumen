// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * État de la TV sous l'horloge, seulement quand quelque chose mérite un mot : pas de réseau,
 * pile de la télécommande faible (Android 12 et plus, télécommandes qui l'annoncent), mise à
 * jour prête. Trois pictogrammes au trait et un mot ; rien quand tout va bien. Tout est lu sur
 * la TV, rien n'est envoyé.
 */
final class TvStatus extends LinearLayout {
    private final MainActivity a;
    private final Runnable tick = new Runnable() { @Override public void run() { refresh(); } };
    private String shown = "";

    TvStatus(MainActivity a) {
        super(a);
        this.a = a;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        setVisibility(GONE);
    }

    /** Relit l'état maintenant, puis toutes les minutes tant que l'accueil est affiché. */
    void start() { refresh(); a.h.removeCallbacks(tick); a.h.postDelayed(tick, 60_000); }
    void stop() { a.h.removeCallbacks(tick); }

    void refresh() {
        StringBuilder key = new StringBuilder();
        boolean offline = offline(a), low = remoteBatteryLow(), update = a.updater != null && a.updater.ready() != null;
        key.append(offline ? 'n' : '-').append(low ? 'b' : '-').append(update ? 'u' : '-');
        if (key.toString().equals(shown)) { if (isAttachedToWindow()) a.h.postDelayed(tick, 60_000); return; }
        shown = key.toString();
        removeAllViews();
        if (offline) add(Glyph.WIFI_OFF, L.t("Pas de réseau"));
        if (low) add(Glyph.BATTERY_LOW, L.t("Pile de la télécommande"));
        if (update) add(Glyph.UPDATE, L.t("Mise à jour prête"));
        boolean any = getChildCount() > 0;
        if (any != (getVisibility() == VISIBLE)) {
            setVisibility(VISIBLE);
            animate().cancel();
            setAlpha(any ? 0f : 1f);
            animate().alpha(any ? 1f : 0f).setDuration(Motion.MEDIUM).setInterpolator(Motion.EASE)
                    .withEndAction(new Runnable() { @Override public void run() { if (getChildCount() == 0) setVisibility(GONE); } }).start();
        }
        a.h.postDelayed(tick, 60_000);
    }

    private void add(int glyph, String label) {
        LinearLayout item = new LinearLayout(a);
        item.setOrientation(HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(new Glyph(glyph, Palette.TEXT_2));
        item.addView(iv, new LayoutParams(a.u(26), a.u(26)));
        TextView t = a.text(18, "sans-serif-medium", Palette.TEXT_2);
        t.setText(label);
        t.setShadowLayer(a.u(8), 0, 0, Palette.SHADOW);
        LayoutParams tl = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = a.u(8);
        item.addView(t, tl);
        item.setContentDescription(label);
        LayoutParams il = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (getChildCount() > 0) il.leftMargin = a.u(22);
        addView(item, il);
    }

    /** Aucune connexion réseau active (Wi-Fi coupé, câble débranché). */
    @SuppressWarnings("deprecation")
    static boolean offline(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
            return n == null || !n.isConnected();
        } catch (Exception e) { return false; }
    }

    /**
     * Télécommande (ou manette) Bluetooth dont la pile est sous 20 %. Android 12 publie le niveau
     * pour les télécommandes qui le déclarent ; sinon on ne dit rien.
     */
    static boolean remoteBatteryLow() {
        if (Build.VERSION.SDK_INT < 31) return false;
        try {
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice d = InputDevice.getDevice(id);
                if (d == null || d.isVirtual()) continue; // une pile présente suffit : seuls les périphériques externes en ont une
                // API 31 (BatteryState), appelée par réflexion : Lumen se compile contre l'API 29
                Object b = InputDevice.class.getMethod("getBatteryState").invoke(d);
                if (b == null || !(Boolean) b.getClass().getMethod("isPresent").invoke(b)) continue;
                float cap = (Float) b.getClass().getMethod("getCapacity").invoke(b);
                if (cap >= 0f && cap < 0.2f) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }
}

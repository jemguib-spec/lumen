// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


/** Panneau du QR code pour régler Lumen depuis le téléphone. */
final class PhonePanel {
    private PhonePanel() { }

    static void openPhonePanel(final MainActivity a) {
        if (a.phone.connected()) { openConnected(a); return; }
        showQr(a);
    }

    /** Un téléphone est déjà connecté : le déconnecter, ou en connecter un autre (nouveau QR code). */
    private static void openConnected(final MainActivity a) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.glassCard(44);
        card.setPadding(a.u(24), a.u(40), a.u(24), a.u(24));
        card.addView(a.menuHeader(null, false, "Téléphone connecté", a.phone.deviceName()));
        card.addView(a.iconRow(Glyph.PHONE, "Connecter un autre téléphone", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(false); showQr(a); }
        }, false), a.rowLp());
        card.addView(a.iconRow(Glyph.SLIDERS, "Télécommande dans les autres applis", new MainActivity.Value() {
            @Override public String get() { return RemoteService.connected() ? "Activée" : "À activer"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    a.startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    a.showNotice("Choisis « Lumen télécommande » et active-le");
                } catch (Exception e) { a.showNotice("Paramètres › Accessibilité › Lumen télécommande"); }
            }
        }, false), a.rowLp());
        card.addView(a.iconRow(Glyph.LOCK, "Accès du téléphone", new MainActivity.Value() {
            @Override public String get() { return a.prefs.phoneRemoteOnly() ? "Télécommande seulement" : "Complet"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setPhoneRemoteOnly(!a.prefs.phoneRemoteOnly()); }
        }, false), a.rowLp());
        card.addView(a.iconRow(Glyph.TRASH, "Oublier les téléphones mémorisés", new MainActivity.Value() {
            @Override public String get() { int n = a.prefs.trustedPhones().size(); return n == 0 ? "Aucun" : String.valueOf(n); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.forgetPhones(); a.showNotice("Chaque téléphone devra de nouveau être accepté sur la TV"); }
        }, false), a.rowLp());
        card.addView(a.menuDivider());
        LinearLayout off = a.iconRow(Glyph.TRASH, "Déconnecter le téléphone", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(true); a.phone.disconnect(); a.showNotice("Téléphone déconnecté"); }
        }, true);
        card.addView(off, a.rowLp());
        o.addView(card, new FrameLayout.LayoutParams(a.u(680), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        card.getChildAt(1).requestFocus();
    }

    private static void showQr(final MainActivity a) {
        final String url = a.phone.start();
        if (url == null) { a.showNotice("Pas de connexion réseau"); return; }
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.glassCard(48);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(a.u(64), a.u(56), a.u(64), a.u(48));
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText("Téléphone");
        card.addView(title);
        ImageView qr = new ImageView(a);
        qr.setImageBitmap(qrBitmap(a, url, a.u(440)));
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(a.u(440), a.u(440));
        ql.topMargin = a.u(36);
        ql.bottomMargin = a.u(28);
        qr.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline ol) { ol.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.u(28)); }
        });
        qr.setClipToOutline(true);
        card.addView(qr, ql);
        TextView addr = a.text(24, "sans-serif", Palette.TEXT_2);
        addr.setText(url.replaceFirst("#.*$", ""));
        card.addView(addr);
        LinearLayout ok = a.listRow("OK", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(true); }
        });
        LinearLayout.LayoutParams okl = new LinearLayout.LayoutParams(a.u(360), a.u(88));
        okl.topMargin = a.u(28);
        card.addView(ok, okl);
        o.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        ok.requestFocus();
    }

    static Bitmap qrBitmap(final MainActivity a, String text, int px) {
        QrCode q = QrCode.encode(text);
        int quiet = 3, n = q.size + quiet * 2;
        int scale = Math.max(1, px / n);
        Bitmap b = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.ARGB_8888);
        b.eraseColor(Palette.SELECTED);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColor(Palette.ON_SELECTED);
        for (int y = 0; y < q.size; y++) for (int x = 0; x < q.size; x++)
            if (q.get(x, y)) c.drawRect((x + quiet) * scale, (y + quiet) * scale, (x + quiet + 1) * scale, (y + quiet + 1) * scale, p);
        return b;
    }

}

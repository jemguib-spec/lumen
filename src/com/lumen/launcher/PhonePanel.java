// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        TextView title = a.text(40, "sans-serif-light", 0xF5FFFFFF);
        title.setText("Téléphone connecté");
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, a.u(30));
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout off = a.listRow("Déconnecter le téléphone", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(true); a.phone.disconnect(); a.showNotice("Téléphone déconnecté"); }
        });
        card.addView(off, a.rowLp());
        card.addView(a.listRow("Connecter un autre téléphone", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(false); showQr(a); }
        }), a.rowLp());
        o.addView(card, new FrameLayout.LayoutParams(a.u(680), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        off.requestFocus();
    }

    private static void showQr(final MainActivity a) {
        final String url = a.phone.start();
        if (url == null) { a.showNotice("Pas de connexion réseau"); return; }
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.glassCard(48);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(a.u(64), a.u(56), a.u(64), a.u(48));
        TextView title = a.text(44, "sans-serif-light", 0xF5FFFFFF);
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
        TextView addr = a.text(24, "sans-serif", 0x99FFFFFF);
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
        b.eraseColor(0xFFF5F5F7);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColor(0xFF111216);
        for (int y = 0; y < q.size; y++) for (int x = 0; x < q.size; x++)
            if (q.get(x, y)) c.drawRect((x + quiet) * scale, (y + quiet) * scale, (x + quiet + 1) * scale, (y + quiet + 1) * scale, p);
        return b;
    }

}

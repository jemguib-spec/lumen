// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Place occupée par Lumen sur la TV, et de quoi la libérer (jamais « Mes photos et vidéos »). */
final class StoragePanel {
    private StoragePanel() { }

    static void open(final MainActivity a) {
        FrameLayout o = a.openOverlay(null);
        final LinearLayout card = a.glassCard(44);
        card.setPadding(a.u(24), a.u(40), a.u(24), a.u(24));
        a.rowRefreshers.clear();
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText(L.t("Stockage"));
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView free = a.text(22, "sans-serif", Palette.TEXT_2);
        free.setGravity(Gravity.CENTER);
        free.setPadding(0, a.u(10), 0, a.u(26));
        card.addView(free, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final long[][] st = {a.online.storage()};
        final Runnable refreshFree = new Runnable() {
            @Override public void run() {
                long left = a.getFilesDir().getUsableSpace();
                String s = L.t("Libre sur la TV : ") + Online.human(left);
                // Lumen ne remplit jamais la TV : il garde de la place libre avant de télécharger
                if (left < Online.PHOTO_RESERVE + (50L << 20))
                    s += L.t("\nTéléchargements en pause : libère de la place dans les Paramètres de la TV");
                else if (left < Online.RESERVE + (50L << 20))
                    s += L.t("\nPhotos : oui   ·   vidéos en pause (Lumen garde ") + Online.human(Online.RESERVE) + L.t(" libres pour elles)");
                else s += L.t("   ·   Lumen garde toujours ") + Online.human(Online.RESERVE) + L.t(" libres");
                free.setText(s);
            }
        };
        refreshFree.run();
        String[] labels = {L.t("Photos téléchargées"), L.t("Vidéos de fond"), L.t("Vidéos des ambiances")};
        final int[] what = {Online.CLEAR_PHOTOS, Online.CLEAR_VIDEOS, Online.CLEAR_COSY};
        LinearLayout first = null;
        for (int i = 0; i < 3; i++) {
            final int k = i;
            LinearLayout r = a.iconRow(Glyph.TRASH, L.t("Vider · ") + labels[i], new MainActivity.Value() {
                @Override public String get() { return Online.human(st[0][k]); }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { clear(a, what[k], st, refreshFree); }
            }, false);
            card.addView(r, a.rowLp());
            if (first == null) first = r;
        }
        card.addView(a.menuDivider());
        card.addView(a.iconRow(Glyph.TRASH, L.t("Tout vider (sauf mes photos et vidéos)"), new MainActivity.Value() {
            @Override public String get() { return Online.human(st[0][0] + st[0][1] + st[0][2]); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { clear(a, Online.CLEAR_ALL, st, refreshFree); }
        }, true), a.rowLp());
        card.addView(a.listRow(L.t("Mes photos et vidéos"), new MainActivity.Value() {
            @Override public String get() { return Online.human(st[0][3]) + " · à gérer depuis le téléphone"; }
        }, new View.OnClickListener() { @Override public void onClick(View v) { } }), a.rowLp());
        o.addView(card, new FrameLayout.LayoutParams(a.u(860), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        if (first != null) first.requestFocus();
    }

    private static void clear(final MainActivity a, final int what, final long[][] st, final Runnable refreshFree) {
        a.online.clear(what);
        st[0] = a.online.storage();
        for (Runnable r : new java.util.ArrayList<>(a.rowRefreshers)) r.run();
        refreshFree.run();
        a.wall.refreshList();
        a.showNotice(L.t("Place libérée"));
    }
}

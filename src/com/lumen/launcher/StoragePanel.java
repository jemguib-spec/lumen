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
        TextView title = a.text(44, "sans-serif-light", 0xF5FFFFFF);
        title.setText("Stockage");
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView free = a.text(22, "sans-serif", 0x99FFFFFF);
        free.setGravity(Gravity.CENTER);
        free.setPadding(0, a.u(10), 0, a.u(26));
        card.addView(free, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final long[][] st = {a.online.storage()};
        final Runnable refreshFree = new Runnable() {
            @Override public void run() {
                long left = a.getFilesDir().getUsableSpace();
                free.setText("Libre sur la TV : " + Online.human(left) + "   ·   Lumen garde toujours "
                        + Online.human(Online.RESERVE) + " libres");
            }
        };
        refreshFree.run();
        String[] labels = {"Photos téléchargées", "Vidéos de fond", "Vidéos des ambiances"};
        final int[] what = {Online.CLEAR_PHOTOS, Online.CLEAR_VIDEOS, Online.CLEAR_COSY};
        LinearLayout first = null;
        for (int i = 0; i < 3; i++) {
            final int k = i;
            LinearLayout r = a.listRow("Vider · " + labels[i], new MainActivity.Value() {
                @Override public String get() { return Online.human(st[0][k]); }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { clear(a, what[k], st, refreshFree); }
            });
            card.addView(r, a.rowLp());
            if (first == null) first = r;
        }
        card.addView(a.listRow("Tout vider (sauf mes photos et vidéos)", new MainActivity.Value() {
            @Override public String get() { return Online.human(st[0][0] + st[0][1] + st[0][2]); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { clear(a, Online.CLEAR_ALL, st, refreshFree); }
        }), a.rowLp());
        card.addView(a.listRow("Mes photos et vidéos", new MainActivity.Value() {
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
        a.showNotice("Place libérée");
    }
}

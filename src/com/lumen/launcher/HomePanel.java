// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Panneau « Maison » : les appareils Home Assistant choisis (lumières, prises, scènes, volets…),
 * OK pour allumer, éteindre ou lancer. L'état se met à jour tout seul tant que le panneau est ouvert.
 */
final class HomePanel {
    private HomePanel() { }

    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final int MAX_SHOWN = 60;

    static void open(final MainActivity a) {
        FrameLayout o = a.openOverlay(null);
        final LinearLayout card = a.glassCard(44);
        ScrollView sv = new ScrollView(a) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(a.u(880), View.MeasureSpec.AT_MOST));
            }
        };
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(70));
        final LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(a.u(24), a.u(40), a.u(24), a.u(28));
        sv.addView(col);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        o.addView(card, new FrameLayout.LayoutParams(a.u(760), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        a.rowRefreshers.clear();

        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText("Maison");
        title.setGravity(Gravity.CENTER);
        col.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView status = a.text(22, "sans-serif", Palette.TEXT_2);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, a.u(10), 0, a.u(24));
        col.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!a.prefs.haConfigured()) {
            status.setText("Relie Lumen à ton Home Assistant depuis le téléphone :\nonglet Plus › Home Assistant (adresse et jeton).");
            LinearLayout r = a.listRow("Ouvrir les réglages du téléphone", null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(false); PhonePanel.openPhonePanel(a); }
            });
            col.addView(r, a.rowLp());
            r.requestFocus();
            return;
        }
        status.setText("Connexion à Home Assistant…");
        final Map<String, HomeAssistant.Entity> live = new HashMap<>();
        // focus posé tout de suite pour que Retour ferme le panneau même pendant le chargement
        card.setFocusable(true);
        card.requestFocus();
        load(a, card, col, status, live, true);
    }

    /** Lit les états ; construit les lignes la première fois, sinon met seulement les valeurs à jour. */
    private static void load(final MainActivity a, final LinearLayout card, final LinearLayout col, final TextView status,
                             final Map<String, HomeAssistant.Entity> live, final boolean build) {
        final String url = a.prefs.haUrl(), token = a.prefs.haToken();
        io.execute(new Runnable() {
            @Override public void run() {
                List<HomeAssistant.Entity> all = null;
                String err = null;
                try { all = HaStates.load(url, token); }
                catch (Exception e) {
                    err = HomeAssistant.human(e);
                    CrashLog.note(a, "Home Assistant : " + e);
                }
                final List<HomeAssistant.Entity> got = all;
                final String error = err;
                a.h.post(new Runnable() {
                    @Override public void run() {
                        if (!card.isAttachedToWindow() && !build) return;
                        if (got == null) {
                            status.setText(error);
                            if (build) retryRow(a, card, col, status, live);
                            return;
                        }
                        live.clear();
                        for (HomeAssistant.Entity e : got) live.put(e.id, e);
                        if (build) {
                            buildRows(a, card, col, status, live, got);
                            // tant que le panneau est ouvert, l'état suit la maison (une seule boucle)
                            a.h.postDelayed(new Runnable() {
                                @Override public void run() {
                                    if (!card.isAttachedToWindow()) return;
                                    load(a, card, col, status, live, false);
                                    a.h.postDelayed(this, 8000);
                                }
                            }, 8000);
                        } else for (Runnable r : new java.util.ArrayList<>(a.rowRefreshers)) r.run();
                    }
                });
            }
        });
    }

    private static void retryRow(final MainActivity a, final LinearLayout card, final LinearLayout col, final TextView status,
                                 final Map<String, HomeAssistant.Entity> live) {
        final LinearLayout r = a.listRow("Réessayer", null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                col.removeView(v);
                status.setText("Connexion à Home Assistant…");
                card.requestFocus();
                load(a, card, col, status, live, true);
            }
        });
        col.addView(r, a.rowLp());
        r.requestFocus();
    }

    private static void buildRows(final MainActivity a, final LinearLayout card, LinearLayout col, final TextView status,
                                  final Map<String, HomeAssistant.Entity> live, List<HomeAssistant.Entity> all) {
        List<HomeAssistant.Entity> shown = HomeAssistant.shown(all, a.prefs.haPinned(), MAX_SHOWN);
        if (shown.isEmpty()) {
            status.setText("Aucun appareil pilotable trouvé (lumières, prises, scènes, volets…).");
            return;
        }
        boolean pinned = !a.prefs.haPinned().isEmpty() && shown.size() < all.size();
        status.setText(pinned ? "Tes appareils · choisis-les sur le téléphone (Plus › Home Assistant)"
                : shown.size() + " appareils · choisis ceux à garder ici depuis le téléphone");
        String domain = null;
        View first = null;
        for (final HomeAssistant.Entity e0 : shown) {
            if (!e0.domain.equals(domain)) {
                domain = e0.domain;
                col.addView(a.section(HomeAssistant.domainName(domain)));
            }
            final String id = e0.id;
            final LinearLayout r = a.listRow(e0.name, new MainActivity.Value() {
                @Override public String get() {
                    HomeAssistant.Entity e = live.get(id);
                    return e == null ? "—" : pending.containsKey(id) ? pending.get(id) : HomeAssistant.label(e);
                }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    HomeAssistant.Entity e = live.get(id);
                    if (e != null) act(a, card, col, status, live, e);
                }
            });
            col.addView(r, a.rowLp());
            if (first == null) first = r;
        }
        if (first != null) first.requestFocus();
    }

    /** État affiché en attendant la réponse (« … »). */
    private static final Map<String, String> pending = new HashMap<>();

    private static void act(final MainActivity a, final LinearLayout card, final LinearLayout col, final TextView status,
                            final Map<String, HomeAssistant.Entity> live, final HomeAssistant.Entity e) {
        final String url = a.prefs.haUrl(), token = a.prefs.haToken();
        pending.put(e.id, "…");
        io.execute(new Runnable() {
            @Override public void run() {
                String err = null;
                try { HomeAssistant.call(url, token, e); }
                catch (Exception ex) { err = HomeAssistant.human(ex); CrashLog.note(a, "Home Assistant : " + ex); }
                final String error = err;
                a.h.postDelayed(new Runnable() {
                    @Override public void run() {
                        pending.remove(e.id);
                        if (error != null) a.showNotice(error);
                        else if ("scene".equals(e.domain) || "script".equals(e.domain) || "button".equals(e.domain)) a.showNotice(e.name);
                        if (card.isAttachedToWindow()) load(a, card, col, status, live, false);
                    }
                }, error == null ? 450 : 0);
            }
        });
    }
}

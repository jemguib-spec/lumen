// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Menu de l'appui long sur une appli : déplacer, favori, masquer, infos, désinstaller. */
final class AppMenu {
    private AppMenu() { }

    static void open(final MainActivity a, final Tile t, final boolean fromHome) {
        final AppRepo.App app = (AppRepo.App) t.tag;
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.glassCard(44);
        card.setPadding(a.u(24), a.u(40), a.u(24), a.u(24));

        TextView title = a.text(40, "sans-serif-light", 0xF5FFFFFF);
        title.setText(app.label);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, a.u(30));
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final boolean fav = a.prefs.favorites().contains(app.key);
        LinearLayout first = null;
        if (fromHome) {
            first = a.listRow("Déplacer", null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(true); a.startMoving(t); }
            });
            card.addView(first, a.rowLp());
        }
        LinearLayout favRow = a.listRow(fav ? "Retirer des favoris" : "Ajouter aux favoris", null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<String> f = a.prefs.favorites();
                if (fav) f.remove(app.key); else f.add(app.key);
                a.prefs.setFavorites(f);
                a.dirtyRow = true;
                a.markAppsDirty();
                a.closeOverlay(true);
                a.showNotice(app.label + (fav ? " retirée des favoris" : " ajoutée aux favoris"));
            }
        });
        card.addView(favRow, a.rowLp());
        if (first == null) first = favRow;
        card.addView(a.listRow("Masquer l'appli", null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.focusBeforeOverlay = null;
                a.closeOverlay(false);
                PinDialog.ask(a, "Code pour masquer " + app.label, new Runnable() {
                    @Override public void run() {
                        a.prefs.setAppHidden(app.key, true);
                        a.dirtyRow = true;
                        a.markAppsDirty();
                        a.showNotice(app.label + " masquée");
                    }
                });
            }
        }), a.rowLp());
        if (a.prefs.hasPin()) {
            final boolean locked = a.prefs.isLocked(app.key);
            card.addView(a.listRow(locked ? "Ne plus demander le code" : "Verrouiller par code", null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    a.closeOverlay(false);
                    PinDialog.ask(a, "Code PIN", new Runnable() {
                        @Override public void run() {
                            a.prefs.setLocked(app.key, !locked);
                            a.showNotice(app.label + (locked ? " déverrouillée" : " verrouillée par code"));
                        }
                    });
                }
            }), a.rowLp());
        }
        if (!app.isInput()) {
            card.addView(a.listRow("Infos sur l'appli", null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    a.closeOverlay(false);
                    try {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        a.startActivity(i);
                    } catch (Exception e) { a.showNotice("Infos indisponibles"); }
                }
            }), a.rowLp());
            card.addView(a.listRow("Désinstaller", null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    a.closeOverlay(false);
                    PinDialog.ask(a, "Code pour désinstaller " + app.label, new Runnable() {
                        @Override public void run() {
                            try {
                                Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg));
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                a.startActivity(i);
                            } catch (Exception e) { a.showNotice("Cette appli ne peut pas être désinstallée"); }
                        }
                    });
                }
            }), a.rowLp());
        }

        o.addView(card, new FrameLayout.LayoutParams(a.u(680), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        first.requestFocus();
    }

}

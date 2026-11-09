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

        // fiche : bannière (ou icône), nom, version
        android.graphics.drawable.Drawable banner = app.banner(a.getPackageManager());
        String sub = app.isInput() ? "Entrée de la TV" : "";
        if (!app.isInput()) try {
            String v = a.getPackageManager().getPackageInfo(app.pkg, 0).versionName;
            if (v != null && !v.isEmpty() && v.length() < 24) sub = "Version " + v;
        } catch (Exception ignored) { }
        card.addView(a.menuHeader(banner != null ? banner : app.icon(a.getPackageManager()), banner != null, app.label, sub));

        final boolean fav = a.prefs.favorites().contains(app.key);
        LinearLayout first = null;
        if (fromHome) {
            first = a.iconRow(Glyph.MOVE, "Déplacer", null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(true); a.startMoving(t); }
            }, false);
            card.addView(first, a.rowLp());
        }
        LinearLayout favRow = a.iconRow(fav ? Glyph.STAR_OFF : Glyph.STAR, fav ? "Retirer des favoris" : "Ajouter aux favoris", null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<String> f = a.prefs.favorites();
                if (fav) f.remove(app.key); else f.add(app.key);
                a.prefs.setFavorites(f);
                a.dirtyRow = true;
                a.markAppsDirty();
                a.closeOverlay(true);
                a.showNotice(app.label + (fav ? " retirée des favoris" : " ajoutée aux favoris"));
            }
        }, false);
        card.addView(favRow, a.rowLp());
        if (first == null) first = favRow;
        card.addView(a.iconRow(Glyph.HIDE, "Masquer l'appli", null, new View.OnClickListener() {
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
        }, false), a.rowLp());
        if (a.prefs.hasPin()) {
            final boolean locked = a.prefs.isLocked(app.key);
            card.addView(a.iconRow(locked ? Glyph.UNLOCK : Glyph.LOCK, locked ? "Ne plus demander le code" : "Verrouiller par code", null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    a.closeOverlay(false);
                    PinDialog.ask(a, "Code PIN", new Runnable() {
                        @Override public void run() {
                            a.prefs.setLocked(app.key, !locked);
                            a.showNotice(app.label + (locked ? " déverrouillée" : " verrouillée par code"));
                        }
                    });
                }
            }, false), a.rowLp());
        }
        if (!app.isInput()) {
            card.addView(a.menuDivider());
            card.addView(a.iconRow(Glyph.INFO, "Infos sur l'appli", null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    a.closeOverlay(false);
                    try {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        a.startActivity(i);
                    } catch (Exception e) { a.showNotice("Infos indisponibles"); }
                }
            }, false), a.rowLp());
            card.addView(a.iconRow(Glyph.TRASH, "Désinstaller", null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    a.closeOverlay(false);
                    PinDialog.ask(a, "Code pour désinstaller " + app.label, new Runnable() {
                        @Override public void run() {
                            a.uninstall(app);
                        }
                    });
                }
            }, true), a.rowLp());
        }

        o.addView(card, new FrameLayout.LayoutParams(a.u(640), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        first.requestFocus();
    }

}

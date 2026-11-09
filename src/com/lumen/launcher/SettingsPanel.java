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

/** Panneau des réglages (à droite) et ses sous-pages : villes, applis masquées. */
final class SettingsPanel {
    private SettingsPanel() { }


    /** Ouvre les réglages (code PIN demandé s'il y en a un). */
    static void openSettings(final MainActivity a) {
        PinDialog.ask(a, "Code pour les réglages", new Runnable() {
            @Override public void run() { openUnlocked(a); }
        });
    }

    private static void openUnlocked(final MainActivity a) {
        final int[] before = {a.prefs.source(), a.prefs.followTime() ? 1 : 0, a.prefs.intervalSeconds(), a.prefs.tileStyle(),
                a.prefs.showIcons() ? 1 : 0, a.prefs.tintTiles() ? 1 : 0};
        FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() {
                a.clockBox.setVisibility(a.prefs.showClock() ? View.VISIBLE : View.GONE);
                Sounds.get(a).setEnabled(a.prefs.sounds());
                if (!a.prefs.appGlow()) a.glow.fade();
                a.rowRefreshers.clear();
                if (before[3] != a.prefs.tileStyle() || before[4] != (a.prefs.showIcons() ? 1 : 0)
                        || before[5] != (a.prefs.tintTiles() ? 1 : 0)) { a.dirtyRow = true; a.markAppsDirty(); }
            }
        });
        final LinearLayout card = a.glassCard(48);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(a.u(760), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        cl.topMargin = cl.bottomMargin = cl.rightMargin = a.u(48);
        o.addView(card, cl);
        page = ROOT;
        page(a, card, ROOT);
        if (reopenPage >= 0) { int p = reopenPage; reopenPage = -1; lastRubric = p; page(a, card, p); }
    }

    /** Page à rouvrir après la reconstruction de l'interface (taille du texte, contraste). */
    static int reopenPage = -1;

    /** Rouvre les réglages sans redemander le code : on vient d'y être, l'interface a seulement été reconstruite. */
    static void reopen(MainActivity a) { if (reopenPage >= 0) openUnlocked(a); }

    /** Applique tout de suite un changement de fond (source, heure, rythme). */
    static void applyWallChange(final MainActivity a) {
        for (String t : Library.themesFor(a.prefs.source())) a.online.refreshIfNeeded(t, false);
        a.wall.restart();
        a.updateWeatherFx();
        if (a.wall.usingFallback() && Library.themesFor(a.prefs.source()).length > 0)
            a.showNotice("Téléchargement des photos en cours");
    }

    // ------------------------------------------------------------------ pages

    // numéros repris dans SettingsIndex (recherche) : à garder identiques
    static final int ROOT = 0, FOND = 1, ACCUEIL = 2, VEILLE = 3, AMBIANCES = 4, SYSTEME = 5,
            ABOUT = 6, LICENCES = 7, CREDITS = 8, LICENCE_INTER = 9, CITIES = 10, HIDDEN = 11, SCREENSAVER = 12, LICENCE_GPL = 13;
    private static final String[] TITLES = {"Réglages", "Fond d'écran", "Accueil et applis", "Mode veille", "Ambiances",
            "Système", "À propos", "Licences", "Crédits des fonds", "Police Inter", "Villes", "Applis masquées", "Économiseur d'écran", "Licence de Lumen"};
    private static final int[] PARENT = {ROOT, ROOT, ROOT, ROOT, ROOT, ROOT, SYSTEME, SYSTEME, LICENCES, LICENCES, FOND, ACCUEIL, VEILLE, LICENCES};
    private static boolean[] citiesChanged;
    private static int page = ROOT, lastRubric = -1;
    private static LinearLayout openCard;

    static void fillSettings(final MainActivity a, final LinearLayout card) { page(a, card, ROOT); }

    /** Retour (ou ◀) dans une sous-page : on remonte d'un niveau. false si on est déjà à l'accueil. */
    static boolean back(MainActivity a) {
        if (openCard == null || !openCard.isAttachedToWindow() || page == ROOT) return false;
        if (page == CITIES && citiesChanged != null && citiesChanged[0]) a.online.refreshIfNeeded(Online.VIDEO, true);
        page(a, openCard, PARENT[page]);
        return true;
    }

    static boolean inSubPage() { return openCard != null && openCard.isAttachedToWindow() && page != ROOT; }

    /** Cadre commun : titre (avec « ‹ » dans les sous-pages), liste qui défile, arrivée en glissant. */
    private static LinearLayout frame(final MainActivity a, final LinearLayout card, int which) {
        int from = page;
        page = which;
        openCard = card;
        card.removeAllViews();
        a.rowRefreshers.clear();
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(70));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(a.u(24), a.u(44), a.u(24), a.u(48));
        sv.addView(col);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView title = a.text(44, "sans-serif-light", 0xF5FFFFFF);
        title.setText(which == ROOT ? TITLES[ROOT] : "‹  " + TITLES[which]);
        title.setPadding(a.u(32), 0, 0, a.u(18));
        col.addView(title);
        // glisse depuis la droite en entrant dans une sous-page, depuis la gauche en revenant
        boolean deeper = which != ROOT && (from == ROOT || PARENT[which] == from);
        col.setTranslationX(a.u(deeper ? 48 : -48));
        col.setAlpha(0f);
        col.animate().translationX(0).alpha(1f).setDuration(200).setInterpolator(new DecelerateInterpolator(2f)).start();
        return col;
    }

    private static void focusFirst(LinearLayout col, View preferred) {
        if (preferred != null) { preferred.requestFocus(); return; }
        for (int i = 0; i < col.getChildCount(); i++) if (col.getChildAt(i).isFocusable()) { col.getChildAt(i).requestFocus(); return; }
    }

    /** Petit carré arrondi avec un pictogramme, devant le nom d'une rubrique ; il suit la sélection. */
    static LinearLayout withIcon(final MainActivity a, final LinearLayout row, int glyphType) {
        final FrameLayout chip = new FrameLayout(a);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.u(12));
        bg.setColor(0x24FFFFFF);
        chip.setBackground(bg);
        final Glyph g = new Glyph(glyphType, 0xE6FFFFFF);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(g);
        chip.addView(iv, new FrameLayout.LayoutParams(a.u(26), a.u(26), Gravity.CENTER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.u(46), a.u(46));
        lp.rightMargin = a.u(22);
        row.addView(chip, 0, lp);
        final View.OnFocusChangeListener old = row.getOnFocusChangeListener();
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (old != null) old.onFocusChange(v, f);
                g.setColor(f ? 0xFF111216 : 0xE6FFFFFF);
                bg.setColor(f ? 0x14000000 : 0x24FFFFFF);
            }
        });
        return row;
    }

    /** Ligne de rubrique : nom, état actuel, et « › ». */
    private static LinearLayout rubric(final MainActivity a, final LinearLayout card, int glyph, String label, MainActivity.Value value, final int target) {
        final MainActivity.Value v = value;
        return withIcon(a, a.listRow(label, new MainActivity.Value() {
            @Override public String get() { return (v == null ? "" : v.get() + "   ") + "›"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View view) { lastRubric = target; page(a, card, target); }
        }), glyph);
    }

    static void page(final MainActivity a, final LinearLayout card, final int which) {
        final LinearLayout col = frame(a, card, which);
        View focus = null;
        switch (which) {
            case ROOT: {
                LinearLayout[] rows = {
                    rubric(a, card, Glyph.IMAGE, "Fond d'écran", new MainActivity.Value() { @Override public String get() { return Prefs.SRC_NAMES[a.prefs.source()]; } }, FOND),
                    rubric(a, card, Glyph.HOME, "Accueil et applis", new MainActivity.Value() { @Override public String get() {
                        int st = a.prefs.tileStyle(); return st == Prefs.TILE_WORD ? "Nom de l'appli" : st == Prefs.TILE_BANNER ? "Bannières" : "Icône et nom"; } }, ACCUEIL),
                    rubric(a, card, Glyph.MOON, "Mode veille", new MainActivity.Value() { @Override public String get() {
                        int m = a.prefs.ambientMinutes(); return m <= 0 ? "Jamais" : m + " min"; } }, VEILLE),
                    rubric(a, card, Glyph.SPARK, "Ambiances", null, AMBIANCES),
                    null,
                    rubric(a, card, Glyph.GEAR, "Système", new MainActivity.Value() { @Override public String get() {
                        return a.updater.ready() != null ? "Mise à jour prête" : "Lumen " + a.updater.currentVersion(); } }, SYSTEME)};
                int[] targets = {FOND, ACCUEIL, VEILLE, AMBIANCES, -2, SYSTEME};
                for (int i = 0; i < rows.length; i++) {
                    if (rows[i] == null) {
                        col.addView(a.listRow("Réglages depuis le téléphone", new MainActivity.Value() {
                            @Override public String get() { return a.phone.connected() ? "Connecté" : "QR code"; }
                        }, new View.OnClickListener() {
                            @Override public void onClick(View v) { PhonePanel.openPhonePanel(a); }
                        }), a.rowLp());
                        withIcon(a, (LinearLayout) col.getChildAt(col.getChildCount() - 1), Glyph.PHONE);
                        continue;
                    }
                    col.addView(rows[i], a.rowLp());
                    if (targets[i] == lastRubric) focus = rows[i];
                }
                TextView foot = a.text(18, "sans-serif", 0x66FFFFFF);
                foot.setText("Lumen " + a.updater.currentVersion() + " · aucune publicité, aucune télémétrie");
                foot.setPadding(a.u(32), a.u(36), a.u(32), 0);
                col.addView(foot);
                break;
            }
            case FOND: {
        col.addView(a.listRow("Source", new MainActivity.Value() {
            @Override public String get() { return Prefs.SRC_NAMES[a.prefs.source()]; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setSource((a.prefs.source() + 1) % Prefs.SRC_NAMES.length); applyWallChange(a); }
        }), a.rowLp());
        col.addView(a.listRow("Suivre l'heure de la journée", new MainActivity.Value() {
            @Override public String get() { return a.prefs.followTime() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setFollowTime(!a.prefs.followTime()); applyWallChange(a); }
        }), a.rowLp());
        col.addView(a.listRow("Changer de fond", new MainActivity.Value() {
            @Override public String get() {
                int s = a.prefs.intervalSeconds();
                if (s <= 0) return "Jamais";
                return s < 60 ? "Toutes les " + s + " s" : "Toutes les " + (s / 60) + " min";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = a.prefs.intervalSeconds(), idx = 0;
                for (int i = 0; i < Prefs.INTERVALS.length; i++) if (Prefs.INTERVALS[i] == cur) idx = i;
                a.prefs.setIntervalSeconds(Prefs.INTERVALS[(idx + 1) % Prefs.INTERVALS.length]);
                applyWallChange(a);
            }
        }), a.rowLp());
        col.addView(a.listRow("Paysages Lumen animés", new MainActivity.Value() {
            @Override public String get() { return a.prefs.livingScenes() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setLivingScenes(!a.prefs.livingScenes()); a.wall.restart(); }
        }), a.rowLp());
        col.addView(a.listRow("Météo dans le paysage", new MainActivity.Value() {
            @Override public String get() {
                if (!a.weather.hasCity()) return "Choisir une ville d'abord";
                return a.prefs.weatherFx() ? "Oui" : "Non";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { if (a.weather.hasCity()) a.prefs.setWeatherFx(!a.prefs.weatherFx()); else CityPanel.openCityInput(a); }
        }), a.rowLp());
        col.addView(a.listRow("Décors de saison", new MainActivity.Value() {
            @Override public String get() { return a.prefs.seasonal() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setSeasonal(!a.prefs.seasonal()); }
        }), a.rowLp());
        col.addView(a.section("Choisir"));
        col.addView(a.listRow("Choisir les fonds", new MainActivity.Value() {
            @Override public String get() { return "Galerie"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                // fonds vidéo : on choisit sur aperçus avant tout téléchargement
                if (a.prefs.source() == Prefs.SRC_VIDEO) VideoPicker.open(a, Online.SCOPE_FONDS, null);
                else GalleryPanel.openGallery(a);
            }
        }), a.rowLp());
        col.addView(a.listRow("Fonds vidéo", new MainActivity.Value() {
            @Override public String get() { return "Aperçus"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { VideoPicker.open(a, Online.SCOPE_FONDS, null); }
        }), a.rowLp());
        col.addView(a.listRow("Villes", new MainActivity.Value() {
            @Override public String get() {
                int n = a.prefs.cities().size();
                return n == Online.CITIES.length ? "Toutes" : n + " sur " + Online.CITIES.length;
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { fillCities(a, card); }
        }), a.rowLp());
        col.addView(a.listRow("Thème des vidéos", new MainActivity.Value() {
            @Override public String get() { return Prefs.VT_NAMES[a.prefs.videoTheme()]; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.prefs.setVideoTheme((a.prefs.videoTheme() + 1) % Prefs.VT_NAMES.length);
                a.online.refreshIfNeeded(Online.VIDEO, true);
            }
        }), a.rowLp());
        col.addView(a.listRow("Fond suivant", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.wall.next(); a.showNotice("Fond suivant"); }
        }), a.rowLp());
        col.addView(a.listRow("Masquer ce fond", null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                WallItem c = a.wall.current();
                if (c == null) return;
                a.wall.hideCurrent();
                a.showNotice("« " + c.title + " » ne s'affichera plus");
            }
        }), a.rowLp());
        col.addView(a.listRow("Fonds masqués", new MainActivity.Value() {
            @Override public String get() {
                int n = a.prefs.hiddenWalls().size();
                return n == 0 ? "Aucun" : n + " · Tout réafficher";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (a.prefs.hiddenWalls().isEmpty()) return;
                a.prefs.unhideAllWalls();
                a.wall.refreshList();
                a.showNotice("Tous les fonds sont de nouveau affichés");
            }
        }), a.rowLp());
        col.addView(a.section("Avancé"));
        col.addView(a.listRow("Qualité des vidéos", new MainActivity.Value() {
            @Override public String get() { return a.prefs.video4k() ? "4K" : "HD"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setVideo4k(!a.prefs.video4k()); }
        }), a.rowLp());
        col.addView(a.listRow("Espace pour les vidéos", new MainActivity.Value() {
            @Override public String get() { return a.prefs.videoDiskGb() + " Go"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = a.prefs.videoDiskGb(), idx = 0;
                for (int i = 0; i < Prefs.DISK_GB.length; i++) if (Prefs.DISK_GB[i] == cur) idx = i;
                a.prefs.setVideoDiskGb(Prefs.DISK_GB[(idx + 1) % Prefs.DISK_GB.length]);
            }
        }), a.rowLp());
        col.addView(a.listRow("Télécharger de nouveaux fonds", new MainActivity.Value() {
            @Override public String get() {
                String[] th = Library.themesFor(a.prefs.source());
                if (th.length == 0) return "—";
                for (String t : th) if (a.online.isBusy(t)) return a.online.status(t);
                return a.online.status(th[0]);
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                String[] th = Library.themesFor(a.prefs.source());
                if (th.length == 0) { a.showNotice("Uniquement pour les sources photo"); return; }
                for (String t : th) a.online.refreshIfNeeded(t, true);
                a.showNotice("Téléchargement lancé");
            }
        }), a.rowLp());
        col.addView(a.listRow("Pexels", new MainActivity.Value() {
            @Override public String get() { return a.prefs.pexelsKey().isEmpty() ? "Désactivé · ajouter une clé" : "Activé"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { KeyPanel.open(a, KeyPanel.PEXELS); }
        }), a.rowLp());
                break;
            }
            case ACCUEIL: {
        col.addView(a.listRow("Bouton Maison (Home Assistant)", new MainActivity.Value() {
            @Override public String get() { return a.prefs.haButton() ? "Affiché" : "Masqué"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = !a.prefs.haButton();
                a.setHouseButton(on);
                if (on && !a.prefs.haConfigured()) a.showNotice("À relier depuis le téléphone : Plus › Home Assistant");
            }
        }), a.rowLp());
        col.addView(a.listRow("Films du moment (TMDB)", new MainActivity.Value() {
            @Override public String get() { return a.prefs.tmdbKey().isEmpty() ? "Ajouter une clé" : "Activé"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { KeyPanel.open(a, KeyPanel.TMDB); }
        }), a.rowLp());
        col.addView(a.listRow("Bandeau d'accueil", new MainActivity.Value() {
            @Override public String get() { return a.prefs.greeting() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setGreeting(!a.prefs.greeting()); a.refreshGreeting(); }
        }), a.rowLp());
        col.addView(a.listRow("Vignettes", new MainActivity.Value() {
            @Override public String get() {
                int st = a.prefs.tileStyle();
                return st == Prefs.TILE_WORD ? "Nom de l'appli" : st == Prefs.TILE_BANNER ? "Bannières officielles" : "Icône et nom";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int st = a.prefs.tileStyle();
                a.prefs.setTileStyle(st == Prefs.TILE_WORD ? Prefs.TILE_MONO : st == Prefs.TILE_MONO ? Prefs.TILE_BANNER : Prefs.TILE_WORD);
            }
        }), a.rowLp());
        col.addView(a.listRow("Applis par ligne", new MainActivity.Value() {
            @Override public String get() { return String.valueOf(a.prefs.homeCols()); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int n = a.prefs.homeCols() + 1;
                a.prefs.setHomeCols(n > 7 ? 3 : n);
                a.dirtyRow = true;
            }
        }), a.rowLp());
        col.addView(a.listRow("Nombre de lignes", new MainActivity.Value() {
            @Override public String get() { return String.valueOf(a.prefs.homeRows()); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int n = a.prefs.homeRows() + 1;
                a.prefs.setHomeRows(n > 3 ? 1 : n);
                a.dirtyRow = true;
            }
        }), a.rowLp());
        col.addView(a.listRow("Icônes des applis", new MainActivity.Value() {
            @Override public String get() { return a.prefs.showIcons() ? "Affichées" : "Masquées"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setShowIcons(!a.prefs.showIcons()); }
        }), a.rowLp());
        col.addView(a.listRow("Couleur des vignettes", new MainActivity.Value() {
            @Override public String get() { return a.prefs.tintTiles() ? "Couleur de l'appli" : "Verre neutre"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setTintTiles(!a.prefs.tintTiles()); }
        }), a.rowLp());
        col.addView(a.listRow("Lueur de l'appli sélectionnée", new MainActivity.Value() {
            @Override public String get() { return a.prefs.appGlow() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setAppGlow(!a.prefs.appGlow()); }
        }), a.rowLp());
        col.addView(a.listRow("Horloge", new MainActivity.Value() {
            @Override public String get() { return a.prefs.showClock() ? "Affichée" : "Masquée"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setShowClock(!a.prefs.showClock()); }
        }), a.rowLp());
        col.addView(a.listRow("Estomper l'interface au repos", new MainActivity.Value() {
            @Override public String get() { return a.prefs.idleFade() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setIdleFade(!a.prefs.idleFade()); }
        }), a.rowLp());
        col.addView(a.section("Reprendre"));
        col.addView(a.listRow("Reprendre au-dessus des applis", new MainActivity.Value() {
            @Override public String get() {
                if (!a.prefs.topShelf()) return "Non";
                return WatchNext.granted(a) ? "Oui" : "Oui · autorisation à donner";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (a.prefs.topShelf() && !WatchNext.granted(a)) { a.requestWatchNext(); return; }
                a.prefs.setTopShelf(!a.prefs.topShelf());
                a.shelf.reload();
            }
        }), a.rowLp());
        col.addView(a.listRow("Affiches des épisodes", new MainActivity.Value() {
            @Override public String get() { return a.prefs.shelfPosters() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setShelfPosters(!a.prefs.shelfPosters()); a.shelf.reload(); }
        }), a.rowLp());
        col.addView(a.listRow("Historique complet pour Reprendre", new MainActivity.Value() {
            @Override public String get() { return ResumePanel.usageAllowed(a) ? "Autorisé" : "Non autorisé"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ResumePanel.usageAllowed(a)) return;
                try {
                    Intent i = new Intent("android.settings.USAGE_ACCESS_SETTINGS");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(i);
                } catch (Exception e) { a.showNotice("À autoriser par ADB, voir le guide"); }
            }
        }), a.rowLp());
        col.addView(a.listRow("Applis selon l'heure", new MainActivity.Value() {
            @Override public String get() { return a.prefs.habitsOn() ? "Oui · calculé sur la TV" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setHabitsOn(!a.prefs.habitsOn()); a.markAppsDirty(); }
        }), a.rowLp());
        col.addView(a.section("Lisibilité"));
        col.addView(a.listRow("Taille du texte", new MainActivity.Value() {
            @Override public String get() { return Prefs.TEXT_SCALE_NAMES[a.prefs.textScale()]; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setTextScale((a.prefs.textScale() + 1) % Prefs.TEXT_SCALES.length); a.restyle(); }
        }), a.rowLp());
        col.addView(a.listRow("Contraste renforcé", new MainActivity.Value() {
            @Override public String get() { return a.prefs.highContrast() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setHighContrast(!a.prefs.highContrast()); a.restyle(); }
        }), a.rowLp());
        col.addView(a.section("Masquées"));
        col.addView(a.listRow("Applis masquées", new MainActivity.Value() {
            @Override public String get() { int n = a.prefs.hiddenApps().size(); return n == 0 ? "Aucune" : String.valueOf(n); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { if (!a.prefs.hiddenApps().isEmpty()) fillHiddenApps(a, card); }
        }), a.rowLp());
                break;
            }
            case VEILLE: {
        col.addView(a.listRow("Activer après", new MainActivity.Value() {
            @Override public String get() { int m = a.prefs.ambientMinutes(); return m <= 0 ? "Jamais" : m + " min sans activité"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = a.prefs.ambientMinutes(), idx = 0;
                for (int i = 0; i < Prefs.AMBIENT_MIN.length; i++) if (Prefs.AMBIENT_MIN[i] == cur) idx = i;
                a.prefs.setAmbientMinutes(Prefs.AMBIENT_MIN[(idx + 1) % Prefs.AMBIENT_MIN.length]);
            }
        }), a.rowLp());
        col.addView(a.listRow("Images du mode veille", new MainActivity.Value() {
            @Override public String get() { return Prefs.veilleName(a.prefs.veilleSource()); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = a.prefs.veilleSource(), idx = 0;
                for (int i = 0; i < Prefs.VEILLE_CHOICES.length; i++) if (Prefs.VEILLE_CHOICES[i] == cur) idx = i;
                int next = Prefs.VEILLE_CHOICES[(idx + 1) % Prefs.VEILLE_CHOICES.length];
                a.prefs.setVeilleSource(next);
                if (next >= 0) for (String t : Library.themesFor(Library.veilleSource(a.prefs))) a.online.refreshIfNeeded(t, false);
            }
        }), a.rowLp());
        col.addView(a.listRow("Écran de nuit", new MainActivity.Value() {
            @Override public String get() {
                int hh = a.prefs.nightHour();
                return hh < 0 ? "Désactivé" : (hh == 0 ? "De minuit" : "De " + hh + " h") + " à 7 h";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = a.prefs.nightHour(), idx = 0;
                for (int i = 0; i < Prefs.NIGHT_HOURS.length; i++) if (Prefs.NIGHT_HOURS[i] == cur) idx = i;
                a.prefs.setNightHour(Prefs.NIGHT_HOURS[(idx + 1) % Prefs.NIGHT_HOURS.length]);
            }
        }), a.rowLp());
        col.addView(a.listRow("Lancer le mode veille", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(false); a.enterAmbient(); }
        }), a.rowLp());
        col.addView(a.listRow("Météo", new MainActivity.Value() {
            @Override public String get() { return a.weather.hasCity() ? a.weather.city() : "Choisir une ville"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { CityPanel.openCityInput(a); }
        }), a.rowLp());
        col.addView(a.listRow("Économiseur d'écran de la TV", new MainActivity.Value() {
            @Override public String get() { return ScreenSaver.state(a); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ScreenSaver.isLumen(a)) { a.showNotice("Lumen est déjà l'économiseur d'écran"); return; }
                if (ScreenSaver.setLumen(a)) { a.showNotice("Lumen est maintenant l'économiseur d'écran"); return; }
                if (ScreenSaver.openSystemScreen(a)) { a.closeOverlay(false); a.showNotice("Choisissez « Lumen » dans la liste"); return; }
                page(a, card, SCREENSAVER); // Google TV cache ce choix : marche à suivre
            }
        }), a.rowLp());
                break;
            }
            case AMBIANCES: {
                col.addView(a.listRow("Ouvrir les ambiances", null, new View.OnClickListener() {
                    @Override public void onClick(View v) { a.closeOverlay(false); a.openAmbiance(); }
                }), a.rowLp());
        col.addView(a.listRow("Vidéos des ambiances", new MainActivity.Value() {
            @Override public String get() { return "Aperçus"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { VideoPicker.open(a, Online.SCOPE_COSY, null); }
        }), a.rowLp());
                col.addView(a.listRow("Son de la vidéo", new MainActivity.Value() {
                    @Override public String get() { return a.prefs.ambianceSound() ? "Activé" : "Coupé"; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { a.prefs.setAmbianceSound(!a.prefs.ambianceSound()); }
                }), a.rowLp());
                break;
            }
            case SYSTEME: {
        col.addView(a.listRow("Redémarrer Lumen", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.restartLumenNicely(); }
        }), a.rowLp());
        col.addView(a.listRow("Stockage", new MainActivity.Value() {
            @Override public String get() { long[] s = a.online.storage(); return Online.human(s[0] + s[1] + s[2] + s[3]) + " utilisés"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { StoragePanel.open(a); }
        }), a.rowLp());
        col.addView(a.listRow("Mise à jour de Lumen", new MainActivity.Value() {
            @Override public String get() { return a.updater.state(); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (a.updater.ready() != null) { a.closeOverlay(false); a.installUpdate(); } else a.updater.check(true);
            }
        }), a.rowLp());
        col.addView(a.listRow("Mises à jour automatiques", new MainActivity.Value() {
            @Override public String get() { return a.prefs.autoUpdate() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setAutoUpdate(!a.prefs.autoUpdate()); }
        }), a.rowLp());
        col.addView(a.listRow("Sons de l'interface", new MainActivity.Value() {
            @Override public String get() { return a.prefs.sounds() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.prefs.setSounds(!a.prefs.sounds());
                Sounds.get(a).setEnabled(a.prefs.sounds());
            }
        }), a.rowLp());
        col.addView(a.listRow("Paramètres de la TV", null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Settings.ACTION_SETTINGS);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(i);
                } catch (Exception e) { a.showNotice("Paramètres indisponibles"); }
            }
        }), a.rowLp());
        col.addView(a.section("Sécurité"));
        col.addView(a.listRow("Code PIN", new MainActivity.Value() {
            @Override public String get() { return a.prefs.hasPin() ? "Activé · OK pour le retirer" : "Désactivé"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (a.prefs.hasPin()) {
                    // les réglages viennent d'être ouverts avec le code : on le redemande quand même pour le retirer
                    PinDialog.ask(a, "Code actuel", new Runnable() {
                        @Override public void run() { a.prefs.setPin(null); a.showNotice("Code PIN retiré"); }
                    });
                } else {
                    PinDialog.create(a, new Runnable() {
                        @Override public void run() { a.showNotice("Code PIN activé · réglages et désinstallation protégés"); }
                    });
                }
            }
        }), a.rowLp());
        col.addView(a.listRow("Sauvegarde des réglages", new MainActivity.Value() {
            @Override public String get() { return "Téléphone › Plus"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { PhonePanel.openPhonePanel(a); }
        }), a.rowLp());
        col.addView(a.section("Diagnostic"));
        col.addView(a.listRow("Mesures", new MainActivity.Value() {
            @Override public String get() { return a.prefs.showPerf() ? "Affichées" : "Masquées"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setShowPerf(!a.prefs.showPerf()); a.perf.setOn(a.prefs.showPerf()); }
        }), a.rowLp());
        col.addView(a.listRow("Journal d'erreurs", new MainActivity.Value() {
            @Override public String get() {
                int n = CrashLog.count(a);
                return n == 0 ? "Aucune erreur" : n + (n > 1 ? " erreurs" : " erreur") + " · OK pour effacer";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { CrashLog.clear(a); a.showNotice("Journal effacé"); }
        }), a.rowLp());
                col.addView(a.section("Informations"));
                col.addView(rubric(a, card, Glyph.INFO, "À propos", null, ABOUT), a.rowLp());
                col.addView(rubric(a, card, Glyph.DOC, "Licences", null, LICENCES), a.rowLp());
                break;
            }
            case ABOUT: About.fillAbout(a, col); break;
            case LICENCES: About.fillLicences(a, card, col); break;
            case CREDITS: About.fillCredits(a, col); break;
            case LICENCE_INTER: About.fillText(a, col, "fonts/LICENCE-Inter.txt"); break;
            case LICENCE_GPL: About.fillText(a, col, "LICENSE.txt"); break;
            case SCREENSAVER: About.fillText(a, col, "docs/economiseur.txt"); break;
        }
        focusFirst(col, focus);
    }

    /** Choix des villes pour les vidéos aériennes. */
    static void fillCities(final MainActivity a, final LinearLayout card) {
        card.removeAllViews();
        a.rowRefreshers.clear();
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(70));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(a.u(24), a.u(48), a.u(24), a.u(48));
        sv.addView(col);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final boolean[] changed = {false};
        page = CITIES;
        openCard = card;
        citiesChanged = changed;
        LinearLayout back = a.listRow("‹  Fond d'écran", null, new View.OnClickListener() {
            @Override public void onClick(View v) { back(a); }
        });
        col.addView(back, a.rowLp());
        TextView title = a.text(44, "sans-serif-light", 0xF5FFFFFF);
        title.setText("Villes");
        title.setPadding(a.u(32), a.u(24), 0, a.u(20));
        col.addView(title);
        for (String[] c : Online.CITIES) {
            final String name = c[0];
            col.addView(a.listRow(name, new MainActivity.Value() {
                @Override public String get() { return a.prefs.cities().contains(name) ? "✓" : ""; }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { a.prefs.setCity(name, !a.prefs.cities().contains(name)); changed[0] = true; }
            }), a.rowLp());
        }
        back.requestFocus();
    }

    static void fillHiddenApps(final MainActivity a, final LinearLayout card) {
        card.removeAllViews();
        a.rowRefreshers.clear();
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(70));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(a.u(24), a.u(48), a.u(24), a.u(48));
        sv.addView(col);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        page = HIDDEN;
        openCard = card;
        LinearLayout back = a.listRow("‹  Accueil et applis", null, new View.OnClickListener() {
            @Override public void onClick(View v) { back(a); }
        });
        col.addView(back, a.rowLp());
        TextView title = a.text(44, "sans-serif-light", 0xF5FFFFFF);
        title.setText("Applis masquées");
        title.setPadding(a.u(32), a.u(24), 0, a.u(8));
        col.addView(title);
        TextView hint = a.text(22, "sans-serif", 0x8CFFFFFF);
        hint.setText("OK pour réafficher l'appli");
        hint.setPadding(a.u(32), 0, 0, a.u(20));
        col.addView(hint);
        for (final String key : a.prefs.hiddenApps()) {
            AppRepo.App app = a.repo.get(key);
            final String name = app != null ? app.label : key;
            final LinearLayout[] holder = new LinearLayout[1];
            holder[0] = a.listRow(name, new MainActivity.Value() {
                @Override public String get() { return a.prefs.hiddenApps().contains(key) ? "Masquée" : "Visible"; }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean nowHidden = !a.prefs.hiddenApps().contains(key);
                    a.prefs.setAppHidden(key, nowHidden);
                    a.dirtyRow = true;
                    a.markAppsDirty();
                }
            });
            col.addView(holder[0], a.rowLp());
        }
        back.requestFocus();
    }

}

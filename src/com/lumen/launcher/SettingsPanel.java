// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


/** Panneau des réglages (à droite) et ses sous-pages : villes, applis masquées. */
final class SettingsPanel {
    private SettingsPanel() { }


    /** Ouvre les réglages (code PIN demandé s'il y en a un). */
    static void openSettings(final MainActivity a) {
        PinDialog.ask(a, L.t("Code pour les réglages"), new Runnable() {
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

    /** Ouvre directement une page (assistant d'installation au premier démarrage). */
    static void openAt(MainActivity a, int which) { reopenPage = which; openUnlocked(a); }

    /** Applique tout de suite un changement de fond (source, heure, rythme). */
    static void applyWallChange(final MainActivity a) {
        for (String t : Library.themesFor(a.prefs.source())) a.online.refreshIfNeeded(t, false);
        a.wall.restart();
        a.updateWeatherFx();
        if (a.wall.usingFallback() && Library.themesFor(a.prefs.source()).length > 0) {
            String low = lowSpaceNotice(a);
            a.showNotice(low != null ? low : L.t("Téléchargement des photos en cours"));
        }
    }

    /** Message si la source choisie ne peut pas se télécharger faute de place sur la TV, sinon null. */
    static String lowSpaceNotice(MainActivity a) {
        for (String t : Library.themesFor(a.prefs.source()))
            if (a.online.lowSpace(t))
                return L.t("Plus assez de place sur la TV (") + Online.human(a.getFilesDir().getUsableSpace()) + L.t(" libres) · Réglages › Système › Stockage");
        return null;
    }

    // ------------------------------------------------------------------ pages

    // numéros repris dans SettingsIndex (recherche) : à garder identiques
    static final int ROOT = 0, FOND = 1, ACCUEIL = 2, VEILLE = 3, AMBIANCES = 4, SYSTEME = 5,
            ABOUT = 6, LICENCES = 7, CREDITS = 8, LICENCE_INTER = 9, CITIES = 10, HIDDEN = 11, SCREENSAVER = 12, LICENCE_GPL = 13, INSTALL = 14,
            PAYSAGES = 15, FOND_AVANCE = 16, VIGNETTES = 17, REPRENDRE = 18, LISIBILITE = 19, DIAG = 20, AFFICHE = 21;
    private static final String[] TITLES = {"Réglages", "Fond d'écran", "Accueil et applis", "Mode veille", "Ambiances",
            "Système", "À propos", "Licences", "Crédits des fonds", "Police Inter", "Villes", "Applis masquées", "Économiseur d'écran", "Licence de Lumen", "Installation",
            "Paysages Lumen", "Avancé", "Vignettes et disposition", "Reprendre", "Lisibilité", "Diagnostic", "À l'affiche"};
    private static final int[] PARENT = {ROOT, ROOT, ROOT, ROOT, ROOT, ROOT, SYSTEME, SYSTEME, LICENCES, LICENCES, FOND, ACCUEIL, VEILLE, LICENCES, SYSTEME,
            FOND, FOND, ACCUEIL, ACCUEIL, ACCUEIL, SYSTEME, ACCUEIL};
    private static boolean[] citiesChanged;
    private static int page = ROOT, lastRubric = -1;
    private static LinearLayout openCard;

    /** Retour (ou ◀) dans une sous-page : on remonte d'un niveau. false si on est déjà à l'accueil. */
    static boolean back(MainActivity a) {
        if (openCard == null || !openCard.isAttachedToWindow() || page == ROOT) return false;
        if (page == CITIES && citiesChanged != null && citiesChanged[0]) a.online.refreshIfNeeded(Online.VIDEO, true);
        page(a, openCard, PARENT[page]);
        return true;
    }

    static boolean inSubPage() { return openCard != null && openCard.isAttachedToWindow() && page != ROOT; }

    /** Colonne qui défile (sans barre, bords estompés) remplissant la carte des réglages. */
    private static LinearLayout scrollColumn(MainActivity a, LinearLayout card, int top) {
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(70));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(a.u(24), a.u(top), a.u(24), a.u(48));
        sv.addView(col);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return col;
    }

    /** Cadre commun : titre (avec « ‹ » dans les sous-pages), liste qui défile, arrivée en glissant. */
    private static LinearLayout frame(final MainActivity a, final LinearLayout card, int which) {
        int from = page;
        page = which;
        openCard = card;
        card.removeAllViews();
        a.rowRefreshers.clear();
        LinearLayout col = scrollColumn(a, card, 44);
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText(which == ROOT ? L.t(TITLES[ROOT]) : "‹  " + L.t(TITLES[which]));
        title.setPadding(a.u(32), 0, 0, a.u(18));
        col.addView(title);
        // glisse depuis la droite en entrant dans une sous-page, depuis la gauche en revenant
        boolean deeper = which != ROOT && (from == ROOT || PARENT[which] == from);
        col.setTranslationX(a.u(deeper ? 48 : -48));
        col.setAlpha(0f);
        col.animate().translationX(0).alpha(1f).setDuration(Motion.FAST).setInterpolator(new DecelerateInterpolator(2f)).start();
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
        if (row instanceof GlassUi.Row) ((GlassUi.Row) row).follow(new GlassUi.Row.Tint() {
            @Override public void at(float t) {
                g.setColor(Palette.mix(0xE6FFFFFF, Palette.ON_SELECTED, t));
                bg.setColor(Palette.mix(0x24FFFFFF, 0x14000000, t));
            }
        });
        return row;
    }

    /** Ligne à interrupteur : get lit l'état, toggle le change (puis les lignes se rafraîchissent). */
    private static LinearLayout toggle(final MainActivity a, String label, GlassUi.Bool get, final Runnable toggle) {
        return a.ui.toggleRow(label, get, new View.OnClickListener() {
            @Override public void onClick(View v) { toggle.run(); }
        });
    }

    /** Position d'une valeur dans un tableau de choix (0 si absente). */
    private static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    /** Phrase d'aide discrète en bas d'une page. */
    private static void hint(MainActivity a, LinearLayout col, String text) {
        TextView t = a.text(21, "sans-serif", Palette.TEXT_3);
        t.setText(text);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(a.u(32), a.u(28), a.u(32), 0);
        col.addView(t);
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
                    rubric(a, card, Glyph.IMAGE, L.t("Fond d'écran"), new MainActivity.Value() { @Override public String get() { return L.t(Prefs.SRC_NAMES[a.prefs.source()]); } }, FOND),
                    rubric(a, card, Glyph.HOME, "Accueil et applis", new MainActivity.Value() { @Override public String get() {
                        int st = a.prefs.tileStyle(); return st == Prefs.TILE_WORD ? L.t("Nom de l'appli") : st == Prefs.TILE_BANNER ? L.t("Bannières") : L.t("Icône et nom"); } }, ACCUEIL),
                    rubric(a, card, Glyph.MOON, "Mode veille", new MainActivity.Value() { @Override public String get() {
                        int m = a.prefs.ambientMinutes(); return m <= 0 ? L.t("Jamais") : m + L.t(" min"); } }, VEILLE),
                    rubric(a, card, Glyph.SPARK, L.t("Ambiances"), null, AMBIANCES),
                    null,
                    null,
                    rubric(a, card, Glyph.GEAR, "Système", new MainActivity.Value() { @Override public String get() {
                        return a.updater.ready() != null ? L.t("Mise à jour prête") : L.t("Lumen ") + a.updater.currentVersion(); } }, SYSTEME)};
                int[] targets = {FOND, ACCUEIL, VEILLE, AMBIANCES, -3, -2, SYSTEME};
                for (int i = 0; i < rows.length; i++) {
                    if (rows[i] == null && targets[i] == -3) {
                        col.addView(a.listRow(L.t("Profils"), new MainActivity.Value() {
                            @Override public String get() { return a.prefs.hasProfiles() ? a.prefs.activeProfileName() : "Un seul"; }
                        }, new View.OnClickListener() {
                            @Override public void onClick(View v) { a.closeOverlay(false); ProfilePanel.manage(a); }
                        }), a.rowLp());
                        withIcon(a, (LinearLayout) col.getChildAt(col.getChildCount() - 1), Glyph.PERSON);
                        continue;
                    }
                    if (rows[i] == null) {
                        col.addView(a.listRow(L.t("Réglages depuis le téléphone"), new MainActivity.Value() {
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
                TextView foot = a.text(18, "sans-serif", Palette.TEXT_3);
                foot.setText(L.t("Lumen ") + a.updater.currentVersion() + L.t(" · aucune publicité, aucune télémétrie"));
                foot.setPadding(a.u(32), a.u(36), a.u(32), 0);
                col.addView(foot);
                break;
            }
            case FOND: {
                col.addView(a.ui.choiceRow(L.t("Source"), L.all(Prefs.SRC_NAMES), new GlassUi.IntValue() {
                    @Override public int get() { return a.prefs.source(); }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) { a.prefs.setSource(i); applyWallChange(a); }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Suivre l'heure de la journée"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.followTime(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setFollowTime(!a.prefs.followTime()); applyWallChange(a); }
                }), a.rowLp());
                final String[] intervalNames = new String[Prefs.INTERVALS.length];
                for (int i = 0; i < intervalNames.length; i++) {
                    int sec = Prefs.INTERVALS[i];
                    intervalNames[i] = sec <= 0 ? L.t("Jamais") : sec < 60 ? L.t("Toutes les ") + sec + L.t(" s") : L.t("Toutes les ") + (sec / 60) + L.t(" min");
                }
                col.addView(a.ui.choiceRow(L.t("Changer de fond"), intervalNames, new GlassUi.IntValue() {
                    @Override public int get() { return indexOf(Prefs.INTERVALS, a.prefs.intervalSeconds()); }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) { a.prefs.setIntervalSeconds(Prefs.INTERVALS[i]); applyWallChange(a); }
                }), a.rowLp());
                col.addView(rubric(a, card, Glyph.IMAGE, L.t("Paysages Lumen"), new MainActivity.Value() {
                    @Override public String get() { return a.prefs.livingScenes() ? "Animés" : "Fixes"; }
                }, PAYSAGES), a.rowLp());
                col.addView(a.section(L.t("Choisir")));
                col.addView(a.listRow(L.t("Choisir les fonds"), new MainActivity.Value() {
                    @Override public String get() { return "Galerie"; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        // fonds vidéo : on choisit sur aperçus avant tout téléchargement
                        if (a.prefs.source() == Prefs.SRC_VIDEO) VideoPicker.open(a, Online.SCOPE_FONDS, null);
                        else GalleryPanel.openGallery(a);
                    }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Fonds vidéo"), new MainActivity.Value() {
                    @Override public String get() { return "Aperçus"; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { VideoPicker.open(a, Online.SCOPE_FONDS, null); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Fond suivant"), null, new View.OnClickListener() {
                    @Override public void onClick(View v) { a.wall.next(); a.showNotice("Fond suivant"); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Masquer ce fond"), null, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        WallItem c = a.wall.current();
                        if (c == null) return;
                        a.wall.hideCurrent();
                        a.showNotice("« " + c.title + L.t(" » ne s'affichera plus"));
                    }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Fonds masqués"), new MainActivity.Value() {
                    @Override public String get() {
                        int n = a.prefs.hiddenWalls().size();
                        return n == 0 ? L.t("Aucun") : n + L.t(" · Tout réafficher");
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (a.prefs.hiddenWalls().isEmpty()) return;
                        a.prefs.unhideAllWalls();
                        a.wall.refreshList();
                        a.showNotice(L.t("Tous les fonds sont de nouveau affichés"));
                    }
                }), a.rowLp());
                col.addView(a.section(L.t("Plus")));
                col.addView(rubric(a, card, Glyph.GEAR, L.t("Avancé"), new MainActivity.Value() {
                    @Override public String get() { return (a.prefs.video4k() ? "4K" : "HD") + " · " + a.prefs.videoDiskGb() + " Go"; }
                }, FOND_AVANCE), a.rowLp());
                break;
            }
            case PAYSAGES: {
                col.addView(toggle(a, L.t("Paysages Lumen animés"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.livingScenes(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setLivingScenes(!a.prefs.livingScenes()); a.wall.restart(); }
                }), a.rowLp());
                if (a.weather.hasCity()) {
                    col.addView(toggle(a, L.t("Météo dans le paysage"), new GlassUi.Bool() {
                        @Override public boolean get() { return a.prefs.weatherFx(); }
                    }, new Runnable() {
                        @Override public void run() { a.prefs.setWeatherFx(!a.prefs.weatherFx()); a.updateWeatherFx(); }
                    }), a.rowLp());
                } else {
                    col.addView(a.listRow(L.t("Météo dans le paysage"), new MainActivity.Value() {
                        @Override public String get() { return "Choisir une ville d'abord"; }
                    }, new View.OnClickListener() {
                        @Override public void onClick(View v) { CityPanel.openCityInput(a); }
                    }), a.rowLp());
                }
                col.addView(toggle(a, L.t("Décors de saison"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.seasonal(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setSeasonal(!a.prefs.seasonal()); }
                }), a.rowLp());
                hint(a, col, L.t("Les paysages suivent le vrai lever et coucher du soleil de ta ville, calculés sur la TV. Animés : nuages, halo du soleil, scintillement de l'eau, étoiles filantes la nuit."));
                break;
            }
            case FOND_AVANCE: {
                col.addView(toggle(a, L.t("Vidéos en 4K"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.video4k(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setVideo4k(!a.prefs.video4k()); }
                }), a.rowLp());
                final String[] diskNames = new String[Prefs.DISK_GB.length];
                for (int i = 0; i < diskNames.length; i++) diskNames[i] = Prefs.DISK_GB[i] + L.t(" Go");
                col.addView(a.ui.choiceRow(L.t("Espace pour les vidéos"), diskNames, new GlassUi.IntValue() {
                    @Override public int get() { return indexOf(Prefs.DISK_GB, a.prefs.videoDiskGb()); }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) { a.prefs.setVideoDiskGb(Prefs.DISK_GB[i]); }
                }), a.rowLp());
                col.addView(a.ui.choiceRow(L.t("Thème des vidéos"), L.all(Prefs.VT_NAMES), new GlassUi.IntValue() {
                    @Override public int get() { return a.prefs.videoTheme(); }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) { a.prefs.setVideoTheme(i); a.online.refreshIfNeeded(Online.VIDEO, true); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Villes"), new MainActivity.Value() {
                    @Override public String get() {
                        int n = a.prefs.cities().size();
                        return n == Online.CITIES.length ? L.t("Toutes") : n + L.t(" sur ") + Online.CITIES.length;
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { fillCities(a, card); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Télécharger de nouveaux fonds"), new MainActivity.Value() {
                    @Override public String get() {
                        String[] th = Library.themesFor(a.prefs.source());
                        if (th.length == 0) return "—";
                        for (String t : th) if (a.online.isBusy(t)) return a.online.status(t);
                        return a.online.status(th[0]);
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String[] th = Library.themesFor(a.prefs.source());
                        if (th.length == 0) { a.showNotice(L.t("Uniquement pour les sources photo")); return; }
                        for (String t : th) a.online.refreshIfNeeded(t, true);
                        a.showNotice(L.t("Téléchargement lancé"));
                    }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Pexels"), new MainActivity.Value() {
                    @Override public String get() { return a.prefs.pexelsKey().isEmpty() ? "Désactivé · ajouter une clé" : "Activé"; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { KeyPanel.open(a, KeyPanel.PEXELS); }
                }), a.rowLp());
                break;
            }
            case ACCUEIL: {
                col.addView(toggle(a, L.t("Bandeau d'accueil"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.greeting(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setGreeting(!a.prefs.greeting()); a.refreshGreeting(); }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Horloge"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.showClock(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setShowClock(!a.prefs.showClock()); }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Estomper l'interface au repos"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.idleFade(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setIdleFade(!a.prefs.idleFade()); }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Applis selon l'heure"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.habitsOn(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setHabitsOn(!a.prefs.habitsOn()); a.markAppsDirty(); }
                }), a.rowLp());
                col.addView(a.section(L.t("Rubriques")));
                col.addView(rubric(a, card, Glyph.GRID, L.t("Vignettes et disposition"), new MainActivity.Value() {
                    @Override public String get() { return a.prefs.homeCols() + " × " + a.prefs.homeRows(); }
                }, VIGNETTES), a.rowLp());
                col.addView(rubric(a, card, Glyph.RESTART, L.t("Reprendre"), new MainActivity.Value() {
                    @Override public String get() { return a.prefs.topShelf() ? "Activé" : "Désactivé"; }
                }, REPRENDRE), a.rowLp());
                col.addView(rubric(a, card, Glyph.DOC, L.t("Lisibilité"), new MainActivity.Value() {
                    @Override public String get() { return L.t(Prefs.TEXT_SCALE_NAMES[a.prefs.textScale()]) + (a.prefs.highContrast() ? " · contraste" : ""); }
                }, LISIBILITE), a.rowLp());
                col.addView(a.section(L.t("Services")));
                col.addView(toggle(a, L.t("Bouton Maison (Home Assistant)"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.haButton(); }
                }, new Runnable() {
                    @Override public void run() {
                        boolean on = !a.prefs.haButton();
                        a.setHouseButton(on);
                        if (on && !a.prefs.haConfigured()) a.showNotice(L.t("À relier depuis le téléphone : Plus › Home Assistant"));
                    }
                }), a.rowLp());
                col.addView(rubric(a, card, Glyph.SPARK, L.t("À l'affiche"), new MainActivity.Value() {
                    @Override public String get() { return a.prefs.tmdbKey().isEmpty() ? "Désactivé" : a.prefs.tmdbOwnKey().isEmpty() ? "Clé partagée" : "Ma clé"; }
                }, AFFICHE), a.rowLp());
                col.addView(a.listRow(L.t("Applis masquées"), new MainActivity.Value() {
                    @Override public String get() { int n = a.prefs.hiddenApps().size(); return n == 0 ? "Aucune" : String.valueOf(n); }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { if (!a.prefs.hiddenApps().isEmpty()) fillHiddenApps(a, card); }
                }), a.rowLp());
                break;
            }
            case AFFICHE: {
                final boolean hasKey = !a.prefs.tmdbKey().isEmpty();
                col.addView(a.listRow(L.t("Ma clé TMDB"), new MainActivity.Value() {
                    @Override public String get() {
                        String k = a.prefs.tmdbOwnKey();
                        return k.isEmpty() ? L.t("Aucune · OK pour en saisir une") : "····" + k.substring(Math.max(0, k.length() - 4)) + L.t(" · OK pour changer");
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { KeyPanel.open(a, KeyPanel.TMDB); }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Clé partagée du projet Lumen"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.tmdbSharedOk(); }
                }, new Runnable() {
                    @Override public void run() {
                        a.prefs.setTmdbSharedOk(!a.prefs.tmdbSharedOk());
                        if (a.prefs.tmdbSharedOk() && a.prefs.tmdbSharedKey().isEmpty()) new Thread(new Runnable() {
                            @Override public void run() { a.updater.refreshServices(); a.h.post(new Runnable() { @Override public void run() { page(a, card, AFFICHE); } }); }
                        }, "lumen-services").start();
                        else page(a, card, AFFICHE);
                    }
                }), a.rowLp());
                if (!a.prefs.tmdbOwnKey().isEmpty()) hint(a, col, L.t("Ta clé passe avant la clé partagée."));
                else if (a.prefs.tmdbSharedOk() && a.prefs.tmdbSharedKey().isEmpty()) hint(a, col, L.t("Le projet ne fournit pas de clé pour l'instant : « À l'affiche » reste fermé sans clé personnelle."));
                if (hasKey) {
                    col.addView(a.listRow(L.t("Ouvrir À l'affiche"), null, new View.OnClickListener() {
                        @Override public void onClick(View v) { a.closeOverlay(false); RecoPanel.open(a); }
                    }), a.rowLp());
                    col.addView(a.section(L.t("Au départ")));
                    final String[] filterVals = {"mine", ""};
                    col.addView(a.ui.choiceRow(L.t("Plateformes"), new String[]{L.t("Mes applis"), L.t("Partout")}, new GlassUi.IntValue() {
                        @Override public int get() { return "".equals(a.prefs.recoFilter()) ? 1 : 0; }
                    }, new GlassUi.IntSetter() {
                        @Override public void set(int i) { a.prefs.setRecoFilter(filterVals[i]); }
                    }), a.rowLp());
                    final String[] modeVals = {Tmdb.POPULAR, Tmdb.NEW, Tmdb.TOP, Tmdb.FOR_YOU};
                    col.addView(a.ui.choiceRow(L.t("Sélection"), new String[]{L.t("Populaire"), L.t("Nouveautés"), L.t("Mieux notés"), L.t("Pour toi")}, new GlassUi.IntValue() {
                        @Override public int get() { return Math.max(0, java.util.Arrays.asList(modeVals).indexOf(a.prefs.recoMode())); }
                    }, new GlassUi.IntSetter() {
                        @Override public void set(int i) { a.prefs.setRecoMode(modeVals[i]); }
                    }), a.rowLp());
                    final String[] typeVals = {"", "movie", "tv"};
                    col.addView(a.ui.choiceRow(L.t("Type"), new String[]{L.t("Films et séries"), L.t("Films"), L.t("Séries")}, new GlassUi.IntValue() {
                        @Override public int get() { return Math.max(0, java.util.Arrays.asList(typeVals).indexOf(a.prefs.recoType())); }
                    }, new GlassUi.IntSetter() {
                        @Override public void set(int i) { a.prefs.setRecoType(typeVals[i]); }
                    }), a.rowLp());
                    col.addView(a.section(L.t("Vie privée")));
                    col.addView(a.listRow(L.t("Historique « Pour toi »"), new MainActivity.Value() {
                        @Override public String get() { int n = a.prefs.recoHistory().size(); return n == 0 ? "Vide" : n + (n > 1 ? " titres" : " titre") + " · OK pour effacer"; }
                    }, new View.OnClickListener() {
                        @Override public void onClick(View v) { a.prefs.clearRecoHistory(); a.showNotice("Historique « Pour toi » effacé"); }
                    }), a.rowLp());
                    col.addView(a.iconRow(Glyph.TRASH, L.t("Tout désactiver"), null, new View.OnClickListener() {
                        @Override public void onClick(View v) { a.prefs.setTmdbKey(""); a.prefs.setTmdbSharedOk(false); a.prefs.clearRecoHistory(); page(a, card, AFFICHE); a.showNotice("À l'affiche désactivé · Lumen ne contacte plus TMDB"); }
                    }, true), a.rowLp());
                    hint(a, col, L.t("Les titres ouverts depuis « À l'affiche » nourrissent « Pour toi ». Ils restent sur la TV : seules des listes publiques sont demandées à TMDB, jamais ce que tu regardes."));
                } else {
                    hint(a, col, L.t("Films et séries du moment, avec affiches, notes et bandes-annonces, filtrés sur les applis installées. Active la clé partagée du projet, ou saisis ta propre clé gratuite (themoviedb.org › Compte › Paramètres › API). Sans l'un ni l'autre, Lumen ne contacte jamais TMDB."));
                }
                break;
            }
            case VIGNETTES: {
                final int[] styles = {Prefs.TILE_WORD, Prefs.TILE_MONO, Prefs.TILE_BANNER};
                col.addView(a.ui.choiceRow(L.t("Vignettes"), new String[]{L.t("Nom de l'appli"), L.t("Icône et nom"), L.t("Bannières officielles")}, new GlassUi.IntValue() {
                    @Override public int get() { return indexOf(styles, a.prefs.tileStyle()); }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) { a.prefs.setTileStyle(styles[i]); }
                }), a.rowLp());
                col.addView(a.ui.choiceRow(L.t("Applis par ligne"), new String[]{"3", "4", "5", "6", "7"}, new GlassUi.IntValue() {
                    @Override public int get() { return a.prefs.homeCols() - 3; }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) { a.prefs.setHomeCols(i + 3); a.dirtyRow = true; }
                }), a.rowLp());
                col.addView(a.ui.choiceRow(L.t("Nombre de lignes"), new String[]{"1", "2", "3"}, new GlassUi.IntValue() {
                    @Override public int get() { return a.prefs.homeRows() - 1; }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) { a.prefs.setHomeRows(i + 1); a.dirtyRow = true; }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Icônes des applis"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.showIcons(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setShowIcons(!a.prefs.showIcons()); }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Verre teinté de la couleur de l'appli"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.tintTiles(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setTintTiles(!a.prefs.tintTiles()); }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Lueur sous l'appli sélectionnée"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.appGlow(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setAppGlow(!a.prefs.appGlow()); }
                }), a.rowLp());
                hint(a, col, L.t("Les changements s'appliquent en refermant les réglages."));
                break;
            }
            case REPRENDRE: {
                col.addView(toggle(a, L.t("Reprendre au-dessus des applis"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.topShelf(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setTopShelf(!a.prefs.topShelf()); a.shelf.reload(); }
                }), a.rowLp());
                if (a.prefs.topShelf() && !WatchNext.granted(a)) {
                    col.addView(a.listRow(L.t("Autorisation « programmes TV »"), new MainActivity.Value() {
                        @Override public String get() { return "À donner · OK"; }
                    }, new View.OnClickListener() {
                        @Override public void onClick(View v) { a.requestWatchNext(); }
                    }), a.rowLp());
                }
                col.addView(toggle(a, L.t("Affiches des épisodes"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.shelfPosters(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setShelfPosters(!a.prefs.shelfPosters()); a.shelf.reload(); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Historique complet pour Reprendre"), new MainActivity.Value() {
                    @Override public String get() { return ResumePanel.usageAllowed(a) ? "Autorisé" : "Non autorisé · OK pour autoriser"; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (ResumePanel.usageAllowed(a)) return;
                        try {
                            Intent i = new Intent("android.settings.USAGE_ACCESS_SETTINGS");
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            a.startActivity(i);
                        } catch (Exception e) { a.showNotice(L.t("À autoriser par ADB : Réglages › Système › Installation")); }
                    }
                }), a.rowLp());
                hint(a, col, L.t("Sélectionne une appli de l'accueil : ses épisodes en cours apparaissent au-dessus. ◀ depuis la première vignette ouvre les dernières applis utilisées."));
                break;
            }
            case LISIBILITE: {
                col.addView(a.ui.choiceRow(L.t("Langue"), Lang.NAMES, new GlassUi.IntValue() {
                    @Override public int get() { return Math.max(0, java.util.Arrays.asList(Lang.CODES).indexOf(a.prefs.lang())); }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) { a.prefs.setLang(Lang.CODES[i]); a.restyle(); }
                }), a.rowLp());
                col.addView(a.ui.choiceRow(L.t("Taille du texte"), L.all(Prefs.TEXT_SCALE_NAMES), new GlassUi.IntValue() {
                    @Override public int get() { return a.prefs.textScale(); }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) { a.prefs.setTextScale(i); a.restyle(); }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Contraste renforcé"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.highContrast(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setHighContrast(!a.prefs.highContrast()); a.restyle(); }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Sons de l'interface"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.sounds(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setSounds(!a.prefs.sounds()); Sounds.get(a).setEnabled(a.prefs.sounds()); }
                }), a.rowLp());
                hint(a, col, L.t("Avec TalkBack (Paramètres de la TV › Accessibilité), chaque élément est annoncé avec sa valeur."));
                break;
            }
            case VEILLE: {
                final String[] afterNames = new String[Prefs.AMBIENT_MIN.length];
                for (int i = 0; i < afterNames.length; i++) afterNames[i] = Prefs.AMBIENT_MIN[i] <= 0 ? L.t("Jamais") : Prefs.AMBIENT_MIN[i] + L.t(" min sans activité");
                col.addView(a.ui.choiceRow(L.t("Activer après"), afterNames, new GlassUi.IntValue() {
                    @Override public int get() { return indexOf(Prefs.AMBIENT_MIN, a.prefs.ambientMinutes()); }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) { a.prefs.setAmbientMinutes(Prefs.AMBIENT_MIN[i]); }
                }), a.rowLp());
                final String[] veilleNames = new String[Prefs.VEILLE_CHOICES.length];
                for (int i = 0; i < veilleNames.length; i++) veilleNames[i] = Prefs.veilleName(Prefs.VEILLE_CHOICES[i]);
                col.addView(a.ui.choiceRow(L.t("Images du mode veille"), veilleNames, new GlassUi.IntValue() {
                    @Override public int get() { return indexOf(Prefs.VEILLE_CHOICES, a.prefs.veilleSource()); }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) {
                        int next = Prefs.VEILLE_CHOICES[i];
                        a.prefs.setVeilleSource(next);
                        if (next >= 0) for (String t : Library.themesFor(Library.veilleSource(a.prefs))) a.online.refreshIfNeeded(t, false);
                    }
                }), a.rowLp());
                final String[] nightNames = new String[Prefs.NIGHT_HOURS.length];
                for (int i = 0; i < nightNames.length; i++) {
                    int hh = Prefs.NIGHT_HOURS[i];
                    nightNames[i] = hh < 0 ? L.t("Désactivé") : (hh == 0 ? L.t("De minuit") : L.t("De ") + hh + L.t(" h")) + L.t(" à 7 h");
                }
                col.addView(a.ui.choiceRow(L.t("Écran de nuit"), nightNames, new GlassUi.IntValue() {
                    @Override public int get() { return indexOf(Prefs.NIGHT_HOURS, a.prefs.nightHour()); }
                }, new GlassUi.IntSetter() {
                    @Override public void set(int i) { a.prefs.setNightHour(Prefs.NIGHT_HOURS[i]); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Lancer le mode veille"), null, new View.OnClickListener() {
                    @Override public void onClick(View v) { a.closeOverlay(false); a.enterAmbient(); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Météo"), new MainActivity.Value() {
                    @Override public String get() { return a.weather.hasCity() ? a.weather.city() : "Choisir une ville"; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { CityPanel.openCityInput(a); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Économiseur d'écran de la TV"), new MainActivity.Value() {
                    @Override public String get() { return ScreenSaver.state(a); }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (ScreenSaver.isLumen(a)) { a.showNotice(L.t("Lumen est déjà l'économiseur d'écran")); return; }
                        if (ScreenSaver.setLumen(a)) { a.showNotice(L.t("Lumen est maintenant l'économiseur d'écran")); return; }
                        if (ScreenSaver.openSystemScreen(a)) { a.closeOverlay(false); a.showNotice(L.t("Choisissez « Lumen » dans la liste")); return; }
                        page(a, card, SCREENSAVER); // Google TV cache ce choix : marche à suivre
                    }
                }), a.rowLp());
                break;
            }
            case AMBIANCES: {
                col.addView(a.listRow(L.t("Ouvrir les ambiances"), null, new View.OnClickListener() {
                    @Override public void onClick(View v) { a.closeOverlay(false); a.openAmbiance(); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Vidéos des ambiances"), new MainActivity.Value() {
                    @Override public String get() { return "Aperçus"; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { VideoPicker.open(a, Online.SCOPE_COSY, null); }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Son de la vidéo"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.ambianceSound(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setAmbianceSound(!a.prefs.ambianceSound()); }
                }), a.rowLp());
                hint(a, col, L.t("Cheminée, pluie, aquarium, nuit étoilée, vagues, neige : de vraies vidéos en plein écran, avec minuterie de sommeil."));
                break;
            }
            case SYSTEME: {
                a.updater.checkSoon(); // une nouvelle version se voit dès qu'on ouvre cette page
                col.addView(a.listRow(L.t("Rechercher une mise à jour"), new MainActivity.Value() {
                    @Override public String get() { return a.updater.state(); }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (a.updater.ready() != null) { a.closeOverlay(false); a.installUpdate(); } else a.updater.check(true);
                    }
                }), a.rowLp());
                final String notesFor = a.updater.ready() != null ? a.updater.readyVersion() : a.updater.currentVersion();
                final String notes = a.prefs.updateNotes(notesFor);
                if (!notes.isEmpty()) {
                    col.addView(a.listRow(L.t("Nouveautés"), new MainActivity.Value() {
                        @Override public String get() { return notesFor; }
                    }, new View.OnClickListener() {
                        @Override public void onClick(View v) { About.showNotes(a, notesFor, notes); }
                    }), a.rowLp());
                }
                col.addView(toggle(a, L.t("Mises à jour automatiques"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.autoUpdate(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setAutoUpdate(!a.prefs.autoUpdate()); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Stockage"), new MainActivity.Value() {
                    @Override public String get() { long[] s = a.online.storage(); return Online.human(s[0] + s[1] + s[2] + s[3]) + " utilisés"; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { StoragePanel.open(a); }
                }), a.rowLp());
                col.addView(a.section(L.t("Sécurité")));
                col.addView(a.listRow(L.t("Code PIN"), new MainActivity.Value() {
                    @Override public String get() { return a.prefs.hasPin() ? "Activé · OK pour le retirer" : "Désactivé"; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (a.prefs.hasPin()) {
                            // les réglages viennent d'être ouverts avec le code : on le redemande quand même pour le retirer
                            PinDialog.ask(a, L.t("Code actuel"), new Runnable() {
                                @Override public void run() { a.prefs.setPin(null); a.showNotice("Code PIN retiré"); }
                            });
                        } else {
                            PinDialog.create(a, new Runnable() {
                                @Override public void run() { a.showNotice("Code PIN activé · réglages et désinstallation protégés"); }
                            });
                        }
                    }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Sauvegarde des réglages"), new MainActivity.Value() {
                    @Override public String get() { return "Téléphone › Plus"; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { PhonePanel.openPhonePanel(a); }
                }), a.rowLp());
                col.addView(a.section(L.t("Informations")));
                col.addView(rubric(a, card, Glyph.HOME, L.t("Installation"), new MainActivity.Value() {
                    @Override public String get() { return Install.isHome(a) ? "Terminée" : "À finir"; }
                }, INSTALL), a.rowLp());
                col.addView(rubric(a, card, Glyph.GEAR, L.t("Diagnostic"), new MainActivity.Value() {
                    @Override public String get() { int n = CrashLog.count(a); return n == 0 ? "Aucune erreur" : n + (n > 1 ? " erreurs" : " erreur"); }
                }, DIAG), a.rowLp());
                col.addView(rubric(a, card, Glyph.INFO, L.t("À propos"), null, ABOUT), a.rowLp());
                col.addView(rubric(a, card, Glyph.DOC, L.t("Licences"), null, LICENCES), a.rowLp());
                break;
            }
            case DIAG: {
                col.addView(a.listRow(L.t("Redémarrer Lumen"), null, new View.OnClickListener() {
                    @Override public void onClick(View v) { a.restartLumenNicely(); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Paramètres de la TV"), null, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            Intent i = new Intent(Settings.ACTION_SETTINGS);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            a.startActivity(i);
                        } catch (Exception e) { a.showNotice(L.t("Paramètres indisponibles")); }
                    }
                }), a.rowLp());
                col.addView(toggle(a, L.t("Mesures à l'écran"), new GlassUi.Bool() {
                    @Override public boolean get() { return a.prefs.showPerf(); }
                }, new Runnable() {
                    @Override public void run() { a.prefs.setShowPerf(!a.prefs.showPerf()); a.perf.setOn(a.prefs.showPerf()); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Journal d'erreurs"), new MainActivity.Value() {
                    @Override public String get() {
                        int n = CrashLog.count(a);
                        return n == 0 ? L.t("Aucune erreur") : n + (n > 1 ? L.t(" erreurs") : L.t(" erreur")) + L.t(" · OK pour effacer");
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { CrashLog.clear(a); a.showNotice("Journal effacé"); }
                }), a.rowLp());
                col.addView(a.listRow(L.t("Revoir les conseils"), new MainActivity.Value() {
                    @Override public String get() { return a.prefs.tipStep() >= Tips.ALL.length ? "Tous vus" : "En cours"; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { Tips.reset(a); a.showNotice("Les conseils reviendront aux prochains démarrages"); }
                }), a.rowLp());
                hint(a, col, L.t("Mesures : temps de démarrage, images par seconde, mémoire et processeur, en haut à gauche. Tout reste sur la TV."));
                break;
            }
            case ABOUT: About.fillAbout(a, col); break;
            case INSTALL: Install.fill(a, card, col); break;
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
        LinearLayout col = scrollColumn(a, card, 48);
        final boolean[] changed = {false};
        page = CITIES;
        openCard = card;
        citiesChanged = changed;
        LinearLayout back = a.listRow(L.t("‹  Fond d'écran"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { back(a); }
        });
        col.addView(back, a.rowLp());
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText(L.t("Villes"));
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
        LinearLayout col = scrollColumn(a, card, 48);

        page = HIDDEN;
        openCard = card;
        LinearLayout back = a.listRow(L.t("‹  Accueil et applis"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { back(a); }
        });
        col.addView(back, a.rowLp());
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText(L.t("Applis masquées"));
        title.setPadding(a.u(32), a.u(24), 0, a.u(8));
        col.addView(title);
        TextView hint = a.text(22, "sans-serif", Palette.TEXT_2);
        hint.setText(L.t("OK pour réafficher l'appli"));
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

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Page Applications : recherche en haut, puis une rangée par catégorie (Favoris, Vidéo et TV,
 * Musique et radio, Jeux, Outils et autres). Construite d'avance par MainActivity pour s'ouvrir
 * instantanément ; l'ouverture, la fermeture et les gestes restent dans MainActivity.
 */
final class AppsPage {
    private final MainActivity a;
    ScrollView scroll;
    LinearLayout sections;
    android.widget.EditText search;

    AppsPage(MainActivity a) { this.a = a; }

    /** Retour pendant une recherche : efface la recherche (true si c'était le cas). */
    boolean clearSearch() {
        if (search == null || search.length() == 0) return false;
        search.setText("");
        a.focusFirstApp();
        return true;
    }

    /** À la fermeture de la page. */
    void reset() {
        if (search != null) { hideKeyboard(search); if (search.length() > 0) search.setText(""); }
    }

    /** À l'ouverture : la page et ses rangées repartent du début. */
    void rewind() {
        if (scroll != null) scroll.scrollTo(0, 0);
        if (sections != null) for (int i = 0; i < sections.getChildCount(); i++) {
            View v = sections.getChildAt(i);
            if (v instanceof HorizontalScrollView) v.scrollTo(0, 0);
        }
    }

    boolean built() { return scroll != null; }

    /**
     * Arrivée de la page : l'en-tête puis chaque rangée montent l'une après l'autre (cascade
     * douce), au lieu d'un bloc qui glisse d'un coup.
     */
    void animateIn() {
        if (scroll == null || !(scroll.getChildAt(0) instanceof ViewGroup)) return;
        java.util.List<View> parts = new java.util.ArrayList<>();
        ViewGroup col = (ViewGroup) scroll.getChildAt(0);
        for (int i = 0; i < col.getChildCount(); i++) {
            View c = col.getChildAt(i);
            if (c == sections) for (int k = 0; k < sections.getChildCount(); k++) parts.add(sections.getChildAt(k));
            else parts.add(c);
        }
        android.view.animation.DecelerateInterpolator di = new android.view.animation.DecelerateInterpolator(2.4f);
        int n = 0;
        for (final View v : parts) {
            if (n >= 9) break; // au-delà, hors de l'écran : rien à animer
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(a.u(46));
            v.animate().alpha(1f).translationY(0).setStartDelay(90 + n * 55L).setDuration(460).setInterpolator(di)
                    .withEndAction(new Runnable() {
                        @Override public void run() { v.animate().setStartDelay(0); a.refreshGlass(scroll); }
                    }).start();
            n++;
        }
    }

    /** Texte tapé sur le téléphone : il remplit la recherche (la liste se filtre aussitôt). */
    /**
     * Texte qui arrive du téléphone : le champ de recherche prend le focus pour que son curseur
     * clignote à l'écran (sans ouvrir le clavier de la TV), et suit la frappe en direct.
     */
    /** Recherche prête : le champ prend la main et le clavier de la TV s'ouvre. */
    void focusSearch() {
        if (search == null) return;
        search.setShowSoftInputOnFocus(true);
        search.requestFocus();
        if (search.length() == 0 && sections != null) fillApps("", null); // recherches récentes
        try {
            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(search, 0);
        } catch (Exception ignored) { }
    }

    void typeFromPhone(String q) {
        if (search == null) return;
        search.setShowSoftInputOnFocus(false);
        if (!search.isFocused()) search.requestFocus();
        search.setCursorVisible(true);
        setQuery(q);
    }

    void setQuery(String q) {
        if (search == null) return;
        search.setText(q);
        search.setSelection(search.length());
    }


    /**
     * Page Applications : recherche en haut, puis une rangée par catégorie (Favoris, Vidéo et TV,
     * Musique et radio, Jeux, Outils et autres). Pendant une recherche, une seule grille de résultats.
     */
    /** Tranche horaire de la dernière construction (la rangée « Ce soir… » change avec l'heure). */
    private int builtSlot = -1;

    boolean stale() { return builtSlot != Habits.slot(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)); }

    void build(String focusKey) {
        a.dirtyApps = false;
        builtSlot = Habits.slot(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY));
        if (scroll != null) a.appsLayer.removeView(scroll);
        scroll = new ScrollView(a) {
            @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
                super.onScrollChanged(l, t, ol, ot);
                a.refreshGlass(this);
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(a.u(60));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(0, a.u(64), 0, a.u(120));
        scroll.addView(col);

        // en-tête : titre à gauche, recherche à droite
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(a.u(112), 0, a.u(112), a.u(8));
        TextView title = a.text(52, "sans-serif-light", Palette.TEXT);
        title.setText(L.t("Applications"));
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String keep = search != null ? search.getText().toString() : "";
        search = searchField();
        search.setText(keep);
        head.addView(search, new LinearLayout.LayoutParams(a.u(520), a.u(76)));
        if (a.voiceAvailable()) {
            Tile mic = Tile.button(a, Glyph.MIC, L.t("Recherche vocale"), a.u(76));
            mic.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.startVoiceSearch(); } });
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(a.u(76), a.u(76));
            ml.leftMargin = a.u(16);
            head.addView(mic, ml);
        }
        col.addView(head);

        sections = new LinearLayout(a);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setClipChildren(false);
        sections.setClipToPadding(false);
        col.addView(sections);
        Tile toFocus = fillApps(keep, focusKey);

        a.appsLayer.addView(scroll, MainActivity.match());
        if (a.appsOpen && a.overlay == null) {
            if (toFocus != null) toFocus.requestFocus(); else a.focusFirstApp();
        }
    }

    /** Champ de recherche en verre ; la liste se filtre à chaque lettre. */
    private android.widget.EditText searchField() {
        final android.widget.EditText e = new android.widget.EditText(a);
        e.setSingleLine(true);
        e.setHint(L.t("Appli, film, réglage, lumière…"));
        e.setContentDescription(L.t("Recherche"));
        e.setHintTextColor(Palette.TEXT_3);
        e.setTextColor(Palette.TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * a.unit);
        e.setTypeface(Fonts.get("sans-serif", Typeface.NORMAL));
        e.setPadding(a.u(30), 0, a.u(30), 0);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.u(Radius.PILL));
        bg.setColor(0x26FFFFFF);
        bg.setStroke(Math.max(1, a.u(1.5f)), 0x33FFFFFF);
        e.setBackground(bg);
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                bg.setColor(f ? Palette.SELECTED : 0x26FFFFFF);
                e.setTextColor(f ? Palette.ON_SELECTED : Palette.TEXT);
                e.setHintTextColor(f ? Palette.ON_SELECTED_2 : Palette.TEXT_3);
                if (f) Sounds.get(a).tick();
                if (!f) e.setShowSoftInputOnFocus(true); // la saisie depuis le téléphone est terminée
            }
        });
        e.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(android.text.Editable ed) { if (sections != null) fillApps(ed.toString(), null); }
        });
        e.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent ev) {
                hideKeyboard(e);
                // Entrée : lance directement le meilleur résultat, sinon va au premier
                View b = bestView;
                if (b != null && b.isAttachedToWindow()) { b.requestFocus(); b.performClick(); }
                else { remember(q0()); a.focusFirstApp(); }
                return true;
            }
        });
        return e;
    }

    private void hideKeyboard(View v) {
        try {
            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    /** Premier résultat (lancé par Entrée sur le clavier). */
    private View bestView;

    private String q0() { return search == null ? "" : search.getText().toString().trim(); }

    /** Applis classées par pertinence, avec un petit bonus pour celles ouvertes récemment. */
    private List<AppRepo.App> rankApps(List<AppRepo.App> apps, String q) {
        final java.util.Map<String, Integer> score = new java.util.HashMap<>();
        List<String[]> rec = a.prefs.recents();
        List<AppRepo.App> out = new ArrayList<>();
        for (AppRepo.App x : apps) {
            int s = SmartSearch.score(x.label, q);
            if (s <= 0) continue;
            for (int i = 0; i < rec.size() && i < 8; i++) if (rec.get(i)[0].equals(x.key)) { s += 40 - i * 4; break; }
            score.put(x.key, s);
            out.add(x);
        }
        java.util.Collections.sort(out, new java.util.Comparator<AppRepo.App>() {
            @Override public int compare(AppRepo.App x, AppRepo.App y) { return score.get(y.key) - score.get(x.key); }
        });
        return out;
    }

    /** Ambiances par nom ou par idée (« feu », « mer », « poissons », « étoiles »…). */
    private static final String[] MOOD_WORDS = {"feu cheminee bois flamme hiver", "pluie orage averse", "aquarium poisson eau",
            "nuit etoile ciel cosmos", "vague mer ocean plage", "neige flocon hiver"};

    private List<Integer> ambianceMatches(String q) {
        List<Integer> out = new ArrayList<>();
        String f = SmartSearch.norm(q);
        if (f.length() < 3) return out;
        for (int k = 0; k < Online.COSY_NAMES.length; k++) {
            boolean hit = SmartSearch.score(Online.COSY_NAMES[k], q) > 0;
            if (!hit && k < MOOD_WORDS.length) for (String w : MOOD_WORDS[k].split(" ")) if (w.startsWith(f) || (f.length() >= 4 && SmartSearch.score(w, f) >= 400)) { hit = true; break; }
            if (hit) out.add(k);
        }
        return out;
    }

    /** Appareils Home Assistant qui correspondent (états gardés 60 s, relus en arrière-plan). */
    private List<HomeAssistant.Entity> houseMatches(final String q) {
        List<HomeAssistant.Entity> out = new ArrayList<>();
        if (!a.prefs.haConfigured() || SmartSearch.norm(q).length() < 3) return out;
        List<HomeAssistant.Entity> all = HaStates.cached();
        if (all == null || HaStates.stale()) {
            HaStates.refresh(a.prefs.haUrl(), a.prefs.haToken(), new Runnable() {
                @Override public void run() {
                    a.h.post(new Runnable() {
                        @Override public void run() { if (sections != null && q.equals(q0())) fillApps(q, null); }
                    });
                }
            });
        }
        if (all == null) return out;
        final java.util.Map<String, Integer> sc = new java.util.HashMap<>();
        for (HomeAssistant.Entity e : all) {
            int s = SmartSearch.score(e.name, q);
            if (s <= 0) s = SmartSearch.score(HomeAssistant.domainName(e.domain), q) / 2;
            if (s > 0) { sc.put(e.id, s); out.add(e); }
        }
        java.util.Collections.sort(out, new java.util.Comparator<HomeAssistant.Entity>() {
            @Override public int compare(HomeAssistant.Entity x, HomeAssistant.Entity y) { return sc.get(y.id) - sc.get(x.id); }
        });
        return out.size() > 5 ? out.subList(0, 5) : out;
    }

    private void toggleHouse(final HomeAssistant.Entity e) {
        final String url = a.prefs.haUrl(), token = a.prefs.haToken();
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                try { HomeAssistant.call(url, token, e); } catch (Exception ex) { err = HomeAssistant.human(ex); }
                final String m = err == null ? e.name : err;
                a.h.post(new Runnable() { @Override public void run() { a.showNotice(m); HaStates.invalidate(); } });
            }
        }, "lumen-ha").start();
    }

    /** Recherches récentes (proposées quand le champ est vide). */
    private void remember(String q) { if (q != null && q.trim().length() >= 2) a.prefs.addSearch(q.trim()); }

    /** Une appli (ou la recherche Google TV) capable de chercher un titre. */
    static final class SearchTarget {
        final String label, hint, pkg, action, extra;
        SearchTarget(String label, String hint, String pkg, String action, String extra) {
            this.label = label; this.hint = hint; this.pkg = pkg; this.action = action; this.extra = extra;
        }
    }

    private static final String GLOBAL = "android.search.action.GLOBAL_SEARCH", GMS = "com.google.android.gms.actions.SEARCH_ACTION";
    private List<SearchTarget> targetsCache;

    /**
     * Recherche de la TV (Google TV : toutes les applis d'un coup, Canal+ et Plex compris), puis
     * les applis installées qui acceptent une recherche directe.
     */
    private List<SearchTarget> searchTargets(Set<String> hidden) {
        if (targetsCache != null && !a.dirtyApps) return targetsCache;
        List<SearchTarget> out = new ArrayList<>();
        android.content.pm.PackageManager pm = a.getPackageManager();
        try {
            if (!a.prefs.isKidProfile() && pm.resolveActivity(new android.content.Intent(GLOBAL), 0) != null)
                out.add(new SearchTarget(L.t("Recherche de la TV"), L.t("Films et séries de toutes les applis"), null, GLOBAL, android.app.SearchManager.QUERY));
        } catch (Exception ignored) { }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String action : new String[]{android.content.Intent.ACTION_SEARCH, GMS}) {
            List<android.content.pm.ResolveInfo> l;
            try { l = pm.queryIntentActivities(new android.content.Intent(action), 0); } catch (Exception e) { continue; }
            for (android.content.pm.ResolveInfo ri : l) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(a.getPackageName()) || !seen.add(pkg)) continue;
                AppRepo.App app = a.repo.findByPackage(pkg);
                if (app == null || !a.prefs.appAllowed(app.key)) continue; // seulement les applis visibles de l'accueil
                out.add(new SearchTarget(app.label, L.t("Chercher dans l'appli"), pkg, action,
                        GMS.equals(action) ? "query" : android.app.SearchManager.QUERY));
                if (out.size() >= 10) break;
            }
        }
        targetsCache = out;
        return out;
    }

    private void searchIn(SearchTarget t, String q) {
        android.content.Intent i = new android.content.Intent(t.action);
        if (t.pkg != null) i.setPackage(t.pkg);
        i.putExtra(t.extra, q);
        i.putExtra(android.app.SearchManager.QUERY, q);
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try { a.startActivity(i); }
        catch (Exception e) {
            // l'appli n'accepte pas la recherche directe : on l'ouvre simplement
            AppRepo.App app = t.pkg == null ? null : a.repo.findByPackage(t.pkg);
            if (app != null) a.launchAnimated(app, null); else a.showNotice(L.t("Recherche indisponible"));
        }
    }

    /** Ligne de résultat (épisode, réglage) alignée sur les vignettes, de largeur raisonnable. */
    private void resultRow(LinearLayout r) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.u(1100), a.u(84 * a.textScale));
        lp.leftMargin = a.u(112);
        lp.bottomMargin = a.u(6);
        sections.addView(r, lp);
    }

    private void openSetting(SettingsIndex.Entry e) {
        if (e.page == SettingsIndex.PHONE) { PhonePanel.openPhonePanel(a); return; }
        if (e.page == SettingsIndex.STORAGE) { StoragePanel.open(a); return; }
        if (e.page == SettingsIndex.HOME) { HomePanel.open(a); return; }
        if (e.page == SettingsIndex.RECO) { RecoPanel.open(a); return; }
        SettingsPanel.reopenPage = e.page;
        SettingsPanel.openSettings(a);
    }

    /** Remplit les rangées (ou les résultats d'une recherche). Renvoie la vignette à sélectionner. */
    private Tile fillApps(String query, String focusKey) {
        sections.removeAllViews();
        Set<String> hidden = a.prefs.hiddenApps();
        final List<String> favs = a.prefs.favorites();
        List<AppRepo.App> apps = new ArrayList<>();
        for (AppRepo.App x : a.repo.all()) if (a.prefs.appAllowed(x.key)) apps.add(x);
        final boolean bn = a.banner();
        int screenW = a.root.getWidth() > 0 ? a.root.getWidth() : a.u(1920);
        final int cols = 6, gap = a.u(30), tw = (screenW - a.u(224) - gap * (cols - 1)) / cols, th = tw * 9 / 16;
        Tile[] focus = {null};
        boolean[] first = {true};

        String q = query == null ? "" : query.trim();
        if (!q.isEmpty()) {
            bestView = null;
            final List<AppRepo.App> found = rankApps(apps, q);
            // « plex dune » : chercher directement le titre dans l'appli nommée
            List<String> labels = new ArrayList<>();
            for (AppRepo.App x : apps) labels.add(x.label);
            Object[] at = SmartSearch.appAndTitle(q, labels);
            List<SearchTarget> targets = searchTargets(hidden);
            if (at != null) {
                final AppRepo.App app = apps.get((Integer) at[0]);
                final String title = (String) at[1];
                SearchTarget t0 = null;
                for (SearchTarget t : targets) if (app.pkg.equals(t.pkg)) t0 = t;
                final SearchTarget direct = t0;
                sections.addView(sectionLabel(L.t("Meilleur résultat")));
                LinearLayout best = a.iconRow(Glyph.SEARCH, L.t("Chercher « ") + title + L.t(" » dans ") + app.label,
                        new MainActivity.Value() { @Override public String get() { return direct != null ? "Entrée" : "Ouvrir l'appli"; } },
                        new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                remember(q0());
                                if (direct != null) searchIn(direct, title); else a.launchAnimated(app, null);
                            }
                        }, false);
                resultRow(best);
                bestView = best;
            }
            // recherche universelle : applis, épisodes en cours, maison, ambiances, puis réglages
            List<WatchNext.Entry> shows = a.shelf == null ? new ArrayList<WatchNext.Entry>() : a.shelf.search(q, 6);
            List<SettingsIndex.Entry> sets = SettingsIndex.search(q, 6);
            List<HomeAssistant.Entity> house = houseMatches(q);
            List<Integer> moods = ambianceMatches(q);
            boolean none = at == null && found.isEmpty() && shows.isEmpty() && sets.isEmpty() && house.isEmpty() && moods.isEmpty();
            if (!found.isEmpty() || none)
                sections.addView(sectionLabel(none ? L.t("Rien ne correspond à « ") + q.trim() + L.t(" » · un autre mot, ou « Chercher dans… » ci-dessous pour chercher dans une appli") : found.size() + (found.size() > 1 ? L.t(" applis") : L.t(" appli"))));
            LinearLayout line = null;
            for (int i = 0; i < found.size(); i++) {
                if (i % cols == 0) { line = appsLine(); sections.addView(line); }
                addAppCell(line, found.get(i), tw, th, bn, favs, i % cols != cols - 1 && i < found.size() - 1 ? gap : 0, focusKey, focus, first);
                if (bestView == null && line != null && line.getChildCount() > 0 && SmartSearch.score(found.get(i).label, q) >= 700)
                    bestView = line.getChildAt(line.getChildCount() - 1);
            }
            if (!house.isEmpty()) {
                sections.addView(sectionLabel(L.t("Maison")));
                for (final HomeAssistant.Entity e : house) {
                    resultRow(a.iconRow(Glyph.HOME, e.name, new MainActivity.Value() { @Override public String get() { return HomeAssistant.label(e); } },
                            new View.OnClickListener() { @Override public void onClick(View v) { remember(q0()); toggleHouse(e); } }, false));
                }
            }
            if (!moods.isEmpty()) {
                sections.addView(sectionLabel(L.t("Ambiances")));
                for (final int k : moods) {
                    resultRow(a.iconRow(Glyph.SPARK, L.t(Online.COSY_NAMES[k]), new MainActivity.Value() { @Override public String get() { return "Lancer l'ambiance"; } },
                            new View.OnClickListener() { @Override public void onClick(View v) { remember(q0()); a.phoneHost.phoneAmbiance(k); } }, false));
                }
            }
            if (!shows.isEmpty()) {
                sections.addView(sectionLabel(L.t("En cours")));
                for (final WatchNext.Entry e : shows) {
                    AppRepo.App owner = a.repo.findByPackage(e.pkg);
                    final String sub = owner == null ? "" : owner.label;
                    String label = e.title + (e.subtitle == null || e.subtitle.isEmpty() ? "" : " · " + e.subtitle);
                    resultRow(a.iconRow(Glyph.SPARK, label, new MainActivity.Value() { @Override public String get() { return sub; } },
                            new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    android.content.Intent i = WatchNext.intentFor(e);
                                    try { if (i != null) a.startActivity(i); else a.showNotice(L.t("Impossible de reprendre ce programme")); }
                                    catch (Exception ex) { a.showNotice(L.t("Impossible de reprendre ce programme")); }
                                }
                            }, false));
                }
            }
            // chercher le film ou la série directement dans les applis (Canal+, Plex, Netflix…)
            if (!targets.isEmpty() && q.length() >= 2) {
                sections.addView(sectionLabel(L.t("Chercher « ") + q + L.t(" » dans…")));
                for (final SearchTarget t : targets) {
                    final String qq = q;
                    resultRow(a.iconRow(Glyph.SEARCH, t.label, new MainActivity.Value() { @Override public String get() { return t.hint; } },
                            new View.OnClickListener() { @Override public void onClick(View v) { remember(qq); searchIn(t, qq); } }, false));
                }
            }
            if (!sets.isEmpty()) {
                sections.addView(sectionLabel(L.t("Réglages")));
                for (final SettingsIndex.Entry e : sets) {
                    resultRow(a.iconRow(e.page == SettingsIndex.HOME ? Glyph.HOME : e.page == SettingsIndex.PHONE ? Glyph.PHONE : e.page == SettingsIndex.RECO ? Glyph.SPARK : Glyph.SLIDERS,
                            L.t(e.label), new MainActivity.Value() { @Override public String get() { return L.t(e.where); } },
                            new View.OnClickListener() { @Override public void onClick(View v) { openSetting(e); } }, false));
                }
            }
            return focus[0];
        }

        // champ de recherche sélectionné et vide : les recherches récentes, prêtes à relancer
        if (search != null && search.hasFocus() && !a.prefs.searches().isEmpty()) {
            sections.addView(sectionLabel(L.t("Recherches récentes")));
            for (final String r : a.prefs.searches()) {
                resultRow(a.iconRow(Glyph.SEARCH, r, null, new View.OnClickListener() {
                    @Override public void onClick(View v) { search.setText(r); search.setSelection(search.length()); }
                }, false));
            }
            resultRow(a.iconRow(Glyph.TRASH, L.t("Effacer l'historique"), null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.prefs.clearSearches(); fillApps("", null); }
            }, false));
        }
        // Habitudes : ce que tu ouvres le plus à ce moment de la journée (calculé sur la TV)
        if (a.prefs.habitsOn()) {
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            List<AppRepo.App> usual = new ArrayList<>();
            for (String k : a.prefs.habits().top(hour, 6, 2)) { AppRepo.App x = a.repo.get(k); if (x != null && !hidden.contains(k)) usual.add(x); }
            if (usual.size() >= 2) addAppRow(Habits.SLOT_NAMES[Habits.slot(hour)], usual, tw, th, bn, favs, gap, focusKey, focus, first);
        }
        // Favoris d'abord, dans l'ordre de l'accueil
        List<AppRepo.App> favApps = new ArrayList<>();
        for (String k : favs) { AppRepo.App x = a.repo.get(k); if (x != null && !hidden.contains(k)) favApps.add(x); }
        addAppRow(L.t("Favoris"), favApps, tw, th, bn, favs, gap, focusKey, focus, first);
        List<List<AppRepo.App>> groups = new ArrayList<>();
        for (int i = 0; i < AppCategory.NAMES.length; i++) groups.add(new ArrayList<AppRepo.App>());
        for (AppRepo.App x : apps) if (!favs.contains(x.key)) groups.get(x.section()).add(x); // pas de doublon avec les favoris
        for (int i : AppCategory.ORDER)
            addAppRow(L.t(AppCategory.NAMES[i]), groups.get(i), tw, th, bn, favs, gap, focusKey, focus, first);
        return focus[0];
    }

    private TextView sectionLabel(String s) {
        TextView t = a.text(22, "sans-serif-medium", Palette.TEXT_2);
        t.setText(s.toUpperCase());
        t.setLetterSpacing(0.08f);
        t.setPadding(a.u(112), a.u(34), 0, a.u(2));
        return t;
    }

    private LinearLayout appsLine() {
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setClipChildren(false);
        line.setClipToPadding(false);
        line.setPadding(a.u(112), a.u(22), a.u(112), a.u(a.banner() ? 10 : 22));
        return line;
    }

    /** Une rangée qui défile horizontalement (toutes les applis d'une catégorie). */
    private void addAppRow(String name, List<AppRepo.App> list, int tw, int th, boolean bn, List<String> favs, int gap,
                           String focusKey, Tile[] focus, boolean[] first) {
        if (list.isEmpty()) return;
        sections.addView(sectionLabel(name));
        LinearLayout line = appsLine();
        HorizontalScrollView hs = new HorizontalScrollView(a) {
            @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
                super.onScrollChanged(l, t, ol, ot);
                a.refreshGlass(this);
            }
        };
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.addView(line);
        sections.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < list.size(); i++)
            addAppCell(line, list.get(i), tw, th, bn, favs, i < list.size() - 1 ? gap : 0, focusKey, focus, first);
    }

    private void addAppCell(LinearLayout line, final AppRepo.App app, int tw, int th, boolean bn, List<String> favs, int rightGap,
                            String focusKey, Tile[] focus, boolean[] first) {
        LinearLayout cell = new LinearLayout(a);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        final Tile t = Tile.app(a, app, tw, th, bn, a.prefs.showIcons(), a.prefs.tintTiles(), bn, a.wordTiles());
        if (first[0]) { t.setTag("first"); first[0] = false; }
        t.setFavoriteMark(favs.contains(app.key));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.launchAnimated(app, t); } });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { a.openAppMenu(t, false); return true; }
        });
        cell.addView(t);
        if (bn) {
            final TextView name = a.text(22, "sans-serif", Palette.TEXT_2);
            name.setText(app.label);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(tw, ViewGroup.LayoutParams.WRAP_CONTENT);
            nl.topMargin = a.u(16);
            cell.addView(name, nl);
            t.setFocusListener(new Tile.FocusListener() {
                @Override public void onTileFocus(Tile tile, boolean f) {
                    name.setTextColor(f ? 0xFFFFFFFF : Palette.TEXT_2);
                    name.animate().translationY(f ? a.u(12) : 0).setDuration(Motion.FAST).start();
                }
            });
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.rightMargin = rightGap;
        line.addView(cell, cl);
        if (focus[0] == null && app.key.equals(focusKey)) focus[0] = t;
    }

}

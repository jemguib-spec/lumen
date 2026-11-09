// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * « À l'affiche » : panneau qui glisse depuis la droite (pendant de « Reprendre » à gauche) avec
 * les films et séries du moment, partout ou seulement sur les applis de la TV (Netflix, Prime…).
 * OK : le titre s'ouvre dans l'appli qui le propose (sa recherche), sinon dans la recherche de la TV.
 */
final class RecoPanel {
    private RecoPanel() { }

    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final ExecutorService img = Executors.newFixedThreadPool(2);

    static void open(final MainActivity a) {
        a.recoOpen = true;
        FrameLayout o = a.openOverlay(new Runnable() { @Override public void run() { a.recoOpen = false; } });
        hero = new Hero(a, o);
        final LinearLayout card = a.glassCard(48);
        card.setPadding(a.u(24), a.u(48), a.u(24), a.u(28));
        // en-tête : le titre, et à droite le choix Tout · Films · Séries
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = a.text(44, "sans-serif-light", 0xF5FFFFFF);
        title.setText("À l'affiche");
        title.setPadding(a.u(28), 0, 0, a.u(6));
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final LinearLayout seg = new LinearLayout(a);
        GradientDrawable segBg = new GradientDrawable();
        segBg.setCornerRadius(a.u(30));
        segBg.setColor(0x14FFFFFF);
        segBg.setStroke(Math.max(1, a.u(1.5f)), 0x1FFFFFFF);
        seg.setBackground(segBg);
        seg.setPadding(a.u(4), a.u(4), a.u(4), a.u(4));
        head.addView(seg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(head);
        final TextView sub = a.text(18, "sans-serif-medium", 0x80FFFFFF);
        sub.setLetterSpacing(0.12f);
        sub.setPadding(a.u(28), 0, 0, a.u(20));
        card.addView(sub);

        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(a.u(640), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        cl.rightMargin = cl.topMargin = cl.bottomMargin = a.u(48);
        o.addView(card, cl);
        card.setTranslationX(a.u(700));
        card.animate().translationX(0).setStartDelay(0).setDuration(380).setInterpolator(new DecelerateInterpolator(2.2f)).start();

        final String key = a.prefs.tmdbKey();
        if (key.isEmpty()) {
            seg.setVisibility(View.GONE);
            sub.setText("FILMS ET SÉRIES DU MOMENT");
            TextView how = a.text(24, "sans-serif", 0xB3FFFFFF);
            how.setText("Pour voir les films et séries les plus regardés en ce moment, sur toutes les plateformes ou seulement sur tes applis :\n\n"
                    + "1. Crée une clé gratuite sur themoviedb.org (Compte › Paramètres › API).\n"
                    + "2. Saisis-la ici, ou colle-la depuis le téléphone (Plus › Films du moment).\n\n"
                    + "Sans clé, Lumen ne contacte jamais ce service.");
            how.setPadding(a.u(28), a.u(10), a.u(28), a.u(24));
            card.addView(how);
            LinearLayout r = a.iconRow(Glyph.UNLOCK, "Saisir la clé sur la TV", null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(false); KeyPanel.open(a, KeyPanel.TMDB); }
            }, false);
            card.addView(r, a.rowLp());
            card.addView(a.iconRow(Glyph.PHONE, "La coller depuis le téléphone", null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(false); PhonePanel.openPhonePanel(a); }
            }, false), a.rowLp());
            r.requestFocus();
            return;
        }

        // filtres : mes applis, partout, puis chaque service présent sur la TV
        final List<String[]> mine = TmdbPolicy.installed(visiblePackages(a));
        final List<String[]> filters = new ArrayList<>();
        if (!mine.isEmpty()) filters.add(new String[]{"mine", "Mes applis"});
        filters.add(new String[]{"", "Partout"});
        for (String[] p : mine) filters.add(new String[]{p[0], p[2]});
        String f0 = a.prefs.recoFilter();
        boolean known = false;
        for (String[] f : filters) if (f[0].equals(f0)) known = true;
        if (!known) f0 = filters.get(0)[0];

        HorizontalScrollView chipsScroll = new HorizontalScrollView(a);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // un bouton qui dépasse s'efface en douceur au bord (la rangée défile), au lieu d'être coupé net
        chipsScroll.setHorizontalFadingEdgeEnabled(true);
        chipsScroll.setFadingEdgeLength(a.u(70));
        final LinearLayout chips = new LinearLayout(a);
        chips.setPadding(a.u(20), a.u(6), a.u(20), a.u(18));
        chipsScroll.addView(chips);
        card.addView(chipsScroll);

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(60));
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final String[] current = {f0};
        final List<TextView> chipViews = new ArrayList<>();
        for (final String[] f : filters) {
            final TextView c = a.text(22, "sans-serif-medium", 0xEBFFFFFF);
            c.setText(f[1]);
            c.setSingleLine(true);
            c.setFocusable(true);
            c.setClickable(true);
            c.setSoundEffectsEnabled(false);
            c.setPadding(a.u(26), a.u(12), a.u(26), a.u(12));
            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(a.u(36));
            c.setBackground(bg);
            c.setTag(f[0]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = a.u(12);
            chips.addView(c, lp);
            chipViews.add(c);
            c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) {
                    if (has) Sounds.get(a).tick();
                    styleChip(a, c, f[0].equals(current[0]), has);
                }
            });
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Sounds.get(a).tock();
                    current[0] = f[0];
                    a.prefs.setRecoFilter(f[0]);
                    for (TextView x : chipViews) styleChip(a, x, x.getTag().equals(current[0]), x.isFocused());
                    load(a, card, list, sub, key, f, mine, false);
                }
            });
            styleChip(a, c, f[0].equals(f0), false);
        }
        // Tout · Films · Séries
        type = a.prefs.recoType();
        final String[][] types = {{"", "Tout"}, {"movie", "Films"}, {"tv", "Séries"}};
        final List<TextView> segViews = new ArrayList<>();
        for (final String[] ty : types) {
            final TextView s = a.text(18, "sans-serif-medium", 0xB3FFFFFF);
            s.setText(ty[1]);
            s.setSingleLine(true);
            s.setFocusable(true);
            s.setClickable(true);
            s.setSoundEffectsEnabled(false);
            s.setPadding(a.u(20), a.u(8), a.u(20), a.u(8));
            GradientDrawable sb = new GradientDrawable();
            sb.setCornerRadius(a.u(26));
            s.setBackground(sb);
            s.setTag(ty[0]);
            seg.addView(s);
            segViews.add(s);
            s.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) {
                    if (has) Sounds.get(a).tick();
                    styleSeg(s, ty[0].equals(type), has);
                }
            });
            s.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Sounds.get(a).tock();
                    type = ty[0];
                    a.prefs.setRecoType(type);
                    for (TextView x : segViews) styleSeg(x, x.getTag().equals(type), x.isFocused());
                    if (lastFilter != null) load(a, card, list, sub, key, lastFilter, mine, false);
                }
            });
            styleSeg(s, ty[0].equals(type), false);
        }
        for (String[] f : filters) if (f[0].equals(f0)) load(a, card, list, sub, key, f, mine, true);
    }

    private static String type = "";
    private static String[] lastFilter;

    private static void styleSeg(TextView s, boolean selected, boolean focused) {
        ((GradientDrawable) s.getBackground()).setColor(focused ? 0xFFF5F5F7 : selected ? 0x40FFFFFF : 0x00FFFFFF);
        s.setTextColor(focused ? 0xFF111216 : selected ? 0xFFFFFFFF : 0x99FFFFFF);
    }

    private static void styleChip(MainActivity a, TextView c, boolean selected, boolean focused) {
        GradientDrawable bg = (GradientDrawable) c.getBackground();
        bg.setColor(focused ? 0xFFF5F5F7 : selected ? 0x40FFFFFF : 0x14FFFFFF);
        bg.setStroke(Math.max(1, a.u(1.5f)), selected && !focused ? 0x80FFFFFF : 0x1FFFFFFF);
        c.setTextColor(focused ? 0xFF111216 : selected ? 0xFFFFFFFF : 0xB3FFFFFF);
    }

    private static Set<String> visiblePackages(MainActivity a) {
        Set<String> hidden = a.prefs.hiddenApps(), out = new HashSet<>();
        for (AppRepo.App x : a.repo.all()) if (!x.isInput() && !hidden.contains(x.key)) out.add(x.pkg);
        return out;
    }

    private static void load(final MainActivity a, final LinearLayout card, final LinearLayout list, final TextView sub,
                             final String key, final String[] filter, final List<String[]> mine, final boolean focusFirst) {
        list.removeAllViews();
        lastFilter = filter;
        final String ty = type;
        String what = ty.isEmpty() ? "" : ty.equals("movie") ? "FILMS " : "SÉRIES ";
        sub.setText(filter[0].isEmpty() ? (what.isEmpty() ? "" : what + "· ") + "LES PLUS REGARDÉS CETTE SEMAINE"
                : (what.isEmpty() ? "" : what + "· ") + (filter[0].equals("mine") ? "LES PLUS POPULAIRES SUR TES APPLIS" : "LES PLUS POPULAIRES SUR " + filter[1].toUpperCase(Locale.FRANCE)));
        final TextView wait = a.text(24, "sans-serif", 0x99FFFFFF);
        wait.setText("Chargement…");
        wait.setPadding(a.u(28), a.u(16), 0, 0);
        list.addView(wait);
        io.execute(new Runnable() {
            @Override public void run() {
                List<Tmdb.Title> got = null;
                String err = null;
                try {
                    if (filter[0].isEmpty()) got = Tmdb.trending(key, ty);
                    else if (filter[0].equals("mine")) got = Tmdb.mine(key, mine, ty);
                    else for (String[] p : mine) if (p[0].equals(filter[0])) got = Tmdb.onProvider(key, p, 10, ty);
                } catch (Net.HttpError e) {
                    err = e.code == 401 ? "Clé TMDB refusée · vérifie-la sur le téléphone (Plus › Films du moment)" : "Service indisponible pour l'instant";
                } catch (Exception e) {
                    err = Messages.download(String.valueOf(e));
                    CrashLog.note(a, "TMDB : " + e);
                }
                final List<Tmdb.Title> titles = got;
                final String error = err;
                a.h.post(new Runnable() {
                    @Override public void run() {
                        if (!card.isAttachedToWindow() || lastFilter != filter || !ty.equals(type)) return; // filtre changé entre-temps
                        list.removeAllViews();
                        if (titles == null || titles.isEmpty()) {
                            wait.setText(error != null ? error : "Rien pour l'instant");
                            list.addView(wait);
                            return;
                        }
                        View first = null;
                        int n = 0;
                        for (Tmdb.Title t : titles) {
                            View r = row(a, t, key, n + 1);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.u(124));
                            lp.bottomMargin = a.u(6);
                            list.addView(r, lp);
                            // arrivée en cascade
                            r.setAlpha(0f);
                            r.setTranslationX(a.u(30));
                            r.animate().alpha(1f).translationX(0).setStartDelay(Math.min(n++, 8) * 45L).setDuration(380)
                                    .setInterpolator(new DecelerateInterpolator(2f)).start();
                            if (first == null) first = r;
                        }
                        if (focusFirst && first != null) first.requestFocus();
                    }
                });
            }
        });
    }

    private static View row(final MainActivity a, final Tmdb.Title t, final String key, int rank) {
        final LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setSoundEffectsEnabled(false);
        r.setPadding(a.u(16), a.u(12), a.u(24), a.u(12));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.u(24));
        bg.setColor(0x00FFFFFF);
        r.setBackground(bg);

        // classement (1 à 10) en chiffres fins, à la manière d'un « top 10 »
        final TextView num = a.text(40, "sans-serif-thin", 0x80FFFFFF);
        num.setText(rank <= 10 ? String.valueOf(rank) : "");
        num.setGravity(Gravity.CENTER);
        num.setIncludeFontPadding(false);
        r.addView(num, new LinearLayout.LayoutParams(a.u(52), ViewGroup.LayoutParams.WRAP_CONTENT));

        final ImageView poster = new ImageView(a);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(0x1AFFFFFF);
        poster.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.u(12)); }
        });
        poster.setClipToOutline(true);
        r.addView(poster, new LinearLayout.LayoutParams(a.u(66), a.u(99)));
        img.execute(new Runnable() {
            @Override public void run() {
                final Bitmap b = Tmdb.poster(t.poster, 185);
                if (b != null) a.h.post(new Runnable() { @Override public void run() { poster.setImageBitmap(b); } });
            }
        });

        LinearLayout txt = new LinearLayout(a);
        txt.setOrientation(LinearLayout.VERTICAL);
        final TextView name = a.text(24, "sans-serif-medium", 0xF2FFFFFF);
        name.setText(t.name);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(name);
        final String meta = metaShort(t);
        final TextView m = a.text(18, "sans-serif", 0x99FFFFFF);
        m.setText(meta);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = a.u(6);
        // l'icône de l'appli qui propose le titre (quand on la connaît), comme une signature
        LinearLayout metaRow = new LinearLayout(a);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        AppRepo.App owner = t.provider == null ? null : a.repo.findByPackage(t.provider[0]);
        if (owner != null) {
            ImageView ic = new ImageView(a);
            ic.setImageDrawable(owner.icon(a.getPackageManager()));
            ic.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.u(6)); }
            });
            ic.setClipToOutline(true);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(a.u(26), a.u(26));
            il.rightMargin = a.u(10);
            metaRow.addView(ic, il);
        }
        metaRow.addView(m);
        txt.addView(metaRow, ml);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = a.u(24);
        r.addView(txt, tl);

        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick();
                bg.setColor(f ? 0xFFF5F5F7 : 0x00FFFFFF);
                name.setTextColor(f ? 0xFF111216 : 0xF2FFFFFF);
                m.setTextColor(f ? 0x99111216 : 0x99FFFFFF);
                num.setTextColor(f ? 0xFFFFFFFF : 0x80FFFFFF);
                r.animate().scaleX(f ? 1.02f : 1f).scaleY(f ? 1.02f : 1f).setStartDelay(0).setDuration(180).start();
                if (f && hero != null) hero.show(t);
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Sounds.get(a).tock(); watch(a, t, key); }
        });
        return r;
    }

    /** Ligne courte des listes : type, année, note (l'appli est montrée par son icône). */
    static String metaShort(Tmdb.Title t) {
        StringBuilder b = new StringBuilder("movie".equals(t.type) ? "Film" : "Série");
        if (!t.year.isEmpty()) b.append("  ·  ").append(t.year);
        if (t.rating > 0) b.append("  ·  ★ ").append(String.format(Locale.FRANCE, "%.1f", t.rating));
        return b.toString();
    }

    static String meta(Tmdb.Title t) {
        StringBuilder b = new StringBuilder("movie".equals(t.type) ? "Film" : "Série");
        if (!t.year.isEmpty()) b.append("  ·  ").append(t.year);
        if (t.rating > 0) b.append("  ·  ★ ").append(String.format(Locale.FRANCE, "%.1f", t.rating));
        if (!t.genres.isEmpty()) b.append("  ·  ").append(t.genres);
        if (t.provider != null) b.append("  ·  ").append(t.provider[2]);
        return b.toString();
    }

    private static Hero hero;

    /**
     * Grande image du titre sélectionné, à gauche du panneau : elle arrive en fondu (avec un très
     * léger zoom), assombrie vers le panneau et vers le bas ; dessus, le titre en lettres fines,
     * une ligne d'infos et trois lignes de résumé. Même esprit que l'accueil : sobre et lumineux.
     */
    private static final class Hero {
        final MainActivity a;
        final FrameLayout layer;
        final ImageView[] pics = new ImageView[2];
        int front = 0;
        final TextView title, meta, overview;
        final ImageView logo;
        final LinearLayout text;
        Tmdb.Title shown;
        private final Runnable pending = new Runnable() { @Override public void run() { load(); } };
        private Tmdb.Title want;

        Hero(final MainActivity a, FrameLayout o) {
            this.a = a;
            layer = new FrameLayout(a);
            for (int i = 0; i < 2; i++) {
                pics[i] = new ImageView(a);
                pics[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
                pics[i].setAlpha(0f);
                layer.addView(pics[i], MainActivity.match());
            }
            // voiles : vers le panneau (droite) et vers le bas, pour que le texte reste lisible
            View side = new View(a);
            side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x6605060A, 0x2205060A, 0xE605060A}));
            layer.addView(side, MainActivity.match());
            View bottom = new View(a);
            bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xD905060A, 0x0005060A}));
            layer.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.u(620), Gravity.BOTTOM));

            text = new LinearLayout(a);
            text.setOrientation(LinearLayout.VERTICAL);
            title = a.text(56, "sans-serif-light", 0xF5FFFFFF);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setShadowLayer(a.u(12), 0, 0, 0x66000000);
            text.addView(title);
            // logo officiel du titre (s'il existe) à la place du texte, comme sur les grandes plateformes
            logo = new ImageView(a);
            logo.setScaleType(ImageView.ScaleType.FIT_START);
            logo.setAdjustViewBounds(true);
            logo.setMaxHeight(a.u(170));
            logo.setMaxWidth(a.u(720));
            logo.setVisibility(View.GONE);
            text.addView(logo, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            meta = a.text(20, "sans-serif-medium", 0xB3FFFFFF);
            meta.setLetterSpacing(0.06f);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ml.topMargin = a.u(14);
            text.addView(meta, ml);
            overview = a.text(22, "sans-serif", 0xB3FFFFFF);
            overview.setMaxLines(3);
            overview.setEllipsize(TextUtils.TruncateAt.END);
            overview.setLineSpacing(a.u(4), 1f);
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ol.topMargin = a.u(18);
            text.addView(overview, ol);
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(a.u(980), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
            tl.leftMargin = a.u(112);
            tl.bottomMargin = a.u(96);
            layer.addView(text, tl);
            text.setAlpha(0f);
            o.addView(layer, MainActivity.match());
        }

        /** Titre sélectionné : on attend un instant (défilement rapide) avant de changer l'image. */
        void show(Tmdb.Title t) {
            want = t;
            a.h.removeCallbacks(pending);
            a.h.postDelayed(pending, shown == null ? 0 : 260);
        }

        private void load() {
            final Tmdb.Title t = want;
            if (t == null || t == shown) return;
            shown = t;
            // texte : léger fondu croisé
            text.animate().alpha(0f).setStartDelay(0).setDuration(140).withEndAction(new Runnable() {
                @Override public void run() {
                    if (shown != t) return;
                    title.setText(t.name);
                    title.setVisibility(View.VISIBLE);
                    logo.setVisibility(View.GONE);
                    meta.setText(meta(t).toUpperCase(Locale.FRANCE));
                    overview.setText(t.overview);
                    overview.setVisibility(t.overview == null || t.overview.isEmpty() ? View.GONE : View.VISIBLE);
                    text.setTranslationY(a.u(10));
                    text.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(new DecelerateInterpolator(2f)).start();
                }
            }).start();
            RecoPanel.img.execute(new Runnable() {
                @Override public void run() {
                    final Bitmap b = Tmdb.poster(t.backdrop.isEmpty() ? t.poster : t.backdrop, t.backdrop.isEmpty() ? 500 : 1280);
                    final Bitmap lg = Tmdb.logo(a.prefs.tmdbKey(), t);
                    if (lg != null) a.h.post(new Runnable() {
                        @Override public void run() {
                            if (shown != t) return;
                            logo.setImageBitmap(lg);
                            logo.setAlpha(0f);
                            logo.setVisibility(View.VISIBLE);
                            title.setVisibility(View.GONE);
                            logo.animate().alpha(1f).setDuration(500).start();
                        }
                    });
                    a.h.post(new Runnable() {
                        @Override public void run() {
                            if (shown != t || b == null || !layer.isAttachedToWindow()) return;
                            final ImageView in = RecoPanel.Hero.this.pics[1 - front], out = RecoPanel.Hero.this.pics[front];
                            front = 1 - front;
                            in.setImageBitmap(b);
                            in.setScaleX(1.06f);
                            in.setScaleY(1.06f);
                            // fondu croisé entre les deux images (voiles et texte restent toujours devant)
                            in.setAlpha(0f);
                            in.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(new DecelerateInterpolator(1.6f))
                                    .withEndAction(new Runnable() {
                                        @Override public void run() {
                                            // lent mouvement de caméra tant que le titre reste sélectionné
                                            if (shown == t) in.animate().scaleX(1.07f).scaleY(1.07f).translationX(-a.u(24))
                                                    .setDuration(22_000).setInterpolator(new android.view.animation.LinearInterpolator()).start();
                                        }
                                    }).start();
                            in.setTranslationX(0);
                            out.animate().alpha(0f).setDuration(900).start();
                        }
                    });
                }
            });
        }
    }

    /** Ouvre le titre dans l'appli qui le propose (sa recherche) ; sinon la recherche de la TV. */
    private static void watch(final MainActivity a, final Tmdb.Title t, final String key) {
        if (t.provider != null) { openIn(a, t.provider, t.name); return; }
        a.showNotice("Recherche de « " + t.name + " »…");
        io.execute(new Runnable() {
            @Override public void run() {
                List<String[]> ps = null;
                try { ps = Tmdb.providersOf(key, t); } catch (Exception ignored) { }
                Set<String> have = visiblePackages(a);
                String[] pick = null;
                if (ps != null) for (String[] p : ps) if (have.contains(p[0])) { pick = p; break; }
                final String[] chosen = pick;
                a.h.post(new Runnable() {
                    @Override public void run() { if (chosen != null) openIn(a, chosen, t.name); else globalSearch(a, t.name); }
                });
            }
        });
    }

    private static void openIn(MainActivity a, String[] provider, String title) {
        a.closeOverlay(false);
        for (String action : new String[]{Intent.ACTION_SEARCH, "com.google.android.gms.actions.SEARCH_ACTION"}) {
            try {
                Intent i = new Intent(action).setPackage(provider[0]);
                i.putExtra(android.app.SearchManager.QUERY, title);
                i.putExtra("query", title);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(i);
                a.showNotice(title + " · " + provider[2]);
                return;
            } catch (Exception ignored) { }
        }
        AppRepo.App app = a.repo.findByPackage(provider[0]);
        if (app != null) { a.showNotice("Cherche « " + title + " » dans " + provider[2]); a.launchAnimated(app, null); }
    }

    private static void globalSearch(MainActivity a, String title) {
        a.closeOverlay(false);
        try {
            Intent i = new Intent("android.search.action.GLOBAL_SEARCH");
            i.putExtra(android.app.SearchManager.QUERY, title);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            a.showNotice("« " + title + " » n'est sur aucune de tes applis");
        }
    }
}

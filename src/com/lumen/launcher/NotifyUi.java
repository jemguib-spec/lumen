// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.app.PendingIntent;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Notifications des applis sur l'accueil : une petite carte de verre à gauche de l'écran quand une
 * appli annonce quelque chose (nouvelle sortie, nouvel épisode, message), puis le bouton cloche
 * qui garde les dernières et les rouvre dans leur appli. Rien dans un profil enfant ; une appli
 * verrouillée par code ne montre que son nom.
 */
final class NotifyUi implements NotifyService.Listener {
    private final MainActivity a;
    private LinearLayout card;
    private ImageView icon;
    private TextView app, title, text;
    private NotifyService.Item current;
    private final Runnable hide = new Runnable() { @Override public void run() { hideCard(); } };

    NotifyUi(MainActivity a) { this.a = a; }

    /** Notifications activées et permises dans ce profil. */
    boolean on() { return a.prefs.notifyOn() && !a.prefs.isKidProfile(); }

    // ------------------------------------------------------------------ arrivée d'une notification

    @Override public void onNotify(NotifyService.Item item, boolean fresh) {
        updateBell();
        if (item == null) {
            // retirée par son appli : la carte en cours s'efface aussi
            if (current != null && !contains(current.key)) hideCard();
            return;
        }
        a.prefs.addNotifySeen(item.pkg);
        if (!fresh || !on() || a.prefs.notifyMuted().contains(item.pkg)) return;
        if (!a.resumedNow || a.ambiance != null || (a.ambient != null && a.ambient.nightShown())) return; // pas pendant une ambiance ni la nuit
        showCard(item);
    }

    private static boolean contains(String key) {
        for (NotifyService.Item i : NotifyService.items()) if (i.key.equals(key)) return true;
        return false;
    }

    /** Une appli verrouillée par code : on dit seulement qu'elle a une notification. */
    private boolean locked(String pkg) {
        if (!a.prefs.hasPin()) return false;
        for (AppRepo.App ap : a.repo.all()) if (pkg.equals(ap.pkg) && a.prefs.isLocked(ap.key)) return true;
        return false;
    }

    /**
     * Petite carte de verre à gauche, sous le nom du fond : discrète, alignée sur la légende. L'icône
     * est posée dans le coin haut gauche, à NOTE_PAD des bords : son arrondi suit celui de la carte.
     */
    private void build() {
        card = a.glassCard(Radius.NOTE);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.TOP);
        int pad = a.u(Radius.NOTE_PAD);
        card.setPadding(pad, pad, a.u(Radius.NOTE_PAD + 8), pad);
        card.setAlpha(0f);
        card.setVisibility(View.GONE);
        icon = new ImageView(a);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.u(Radius.NOTE_ICON)); }
        });
        icon.setClipToOutline(true);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(a.u(56), a.u(56));
        il.rightMargin = a.u(18);
        card.addView(icon, il);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        app = a.text(16, "sans-serif-medium", Palette.TEXT_3);
        app.setLetterSpacing(0.08f);
        app.setSingleLine(true);
        app.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(app);
        title = a.text(25, "sans-serif-medium", Palette.TEXT);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = a.u(6);
        col.addView(title, tl);
        text = a.text(20, "sans-serif", Palette.TEXT_2);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        xl.topMargin = a.u(4);
        col.addView(text, xl);
        card.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        card.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); // TalkBack la lit à son arrivée
        // à gauche, alignée sur le nom du fond et le bandeau d'accueil, juste dessous
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(a.u(580), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        cl.leftMargin = a.u(112);
        cl.topMargin = a.u(214);
        a.root.addView(card, cl);
    }

    private void showCard(NotifyService.Item item) {
        if (card == null) build();
        current = item;
        boolean hidden = locked(item.pkg);
        String appName = Install.label(a, item.pkg);
        app.setText((appName + " · " + NotifyPolicy.ago(item.when, System.currentTimeMillis(), L.french())).toUpperCase(java.util.Locale.FRANCE));
        title.setText(hidden ? L.t("Nouvelle notification") : (item.title.isEmpty() ? appName : item.title));
        text.setText(hidden ? L.t("Appli verrouillée par code") : item.text);
        text.setVisibility(text.getText().length() == 0 ? View.GONE : View.VISIBLE);
        Drawable d = null;
        try { d = a.getPackageManager().getApplicationIcon(item.pkg); } catch (Exception ignored) { }
        icon.setImageDrawable(d);
        icon.setVisibility(d == null ? View.GONE : View.VISIBLE);
        card.setContentDescription(appName + ". " + title.getText() + ". " + text.getText());
        card.bringToFront(); // au-dessus des panneaux ouverts et du mode veille
        card.setVisibility(View.VISIBLE);
        card.animate().cancel();
        if (card.getAlpha() < 0.05f) card.setTranslationX(-a.u(28)); // glisse depuis la gauche
        card.animate().alpha(1f).translationX(0).setDuration(Motion.MEDIUM).setInterpolator(Motion.EASE).start();
        Sounds.get(a).tock();
        a.h.removeCallbacks(hide);
        a.h.postDelayed(hide, NotifyPolicy.displayMs(a.prefs.notifySeconds(), item.title, item.text));
    }

    /** Exemple (Réglages › Notifications › Essayer) : la carte telle qu'elle apparaîtra. */
    void sample() {
        showCard(new NotifyService.Item("lumen-essai", a.getPackageName(), L.t("Nouvelle saison disponible"),
                L.t("Exemple : c'est ainsi qu'une appli annonce une nouvelle sortie sur l'accueil."), System.currentTimeMillis(), null));
    }

    void hideCard() {
        a.h.removeCallbacks(hide);
        current = null;
        if (card == null || card.getVisibility() != View.VISIBLE) return;
        card.animate().cancel();
        card.animate().alpha(0f).translationX(-a.u(20)).setDuration(Motion.MEDIUM).setInterpolator(Motion.EASE).withEndAction(new Runnable() {
            @Override public void run() { if (current == null) card.setVisibility(View.GONE); }
        }).start();
    }

    /** La carte est à l'écran (Retour l'efface sans rien faire d'autre). */
    boolean visible() { return current != null; }

    // ------------------------------------------------------------------ bouton cloche et liste

    /** Bouton cloche de l'accueil : son nom donne le nombre ; il n'apparaît que si des notifications attendent. */
    void updateBell() {
        Tile b = a.bellButton;
        if (b == null) return;
        int n = pending();
        b.setGlyph(Glyph.BELL, n == 1 ? L.t("1 notification") : n + L.t(" notifications"));
        a.updateButtons();
    }

    /** Notifications qui attendent sous la cloche (0 si elles sont coupées ou dans un profil enfant). */
    int pending() { return on() ? visibleCount() : 0; }

    private int visibleCount() {
        int n = 0;
        java.util.Set<String> muted = a.prefs.notifyMuted();
        for (NotifyService.Item i : NotifyService.items()) if (!muted.contains(i.pkg)) n++;
        return n;
    }

    // ------------------------------------------------------------------ volet des notifications

    private boolean shadeOpen;

    /** Le volet a quelque chose à montrer : des notifications permises, ou la météo d'une ville. */
    boolean shadeUseful() { return on() || a.weather.hasCity(); }

    /** Le volet est ouvert (▼ le referme quand rien n'est plus bas). */
    boolean shadeOpen() { return shadeOpen && a.overlay != null; }

    /**
     * Volet des notifications : une bande de verre qui descend du haut de l'écran (▲ depuis les
     * boutons ronds, ou la cloche), avec toutes les notifications côte à côte, la plus récente à
     * gauche. ◀ ▶ pour passer de l'une à l'autre, OK ouvre l'appli, ▼ ou Retour referme.
     */
    void openShade() {
        hideCard();
        final FrameLayout o = a.openOverlay(new Runnable() { @Override public void run() { shadeOpen = false; updateBell(); } });
        shadeOpen = true;
        LinearLayout band = a.glassCard(Radius.CARD);
        band.setOrientation(LinearLayout.VERTICAL);
        int pad = a.u(Radius.PAD);
        band.setPadding(pad, a.u(Radius.PAD + 6), pad, pad);

        // en-tête : titre, nombre, et le geste pour refermer
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(a.u(16), 0, a.u(16), a.u(18));
        // la date du jour, comme en tête d'un agenda ; le nombre de notifications à côté
        TextView title = a.text(32, "sans-serif-light", Palette.TEXT);
        title.setText(DateLine.format(java.util.Calendar.getInstance(), L.lang()));
        head.addView(title);
        final List<NotifyService.Item> items = visibleItems();
        TextView count = a.text(22, "sans-serif", Palette.TEXT_3);
        count.setText(items.isEmpty() ? "" : "·   " + (items.size() == 1 ? L.t("1 notification") : items.size() + L.t(" notifications")));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.leftMargin = a.u(14);
        head.addView(count, cl);
        TextView how = a.text(19, "sans-serif", Palette.TEXT_3);
        how.setText(L.t("OK pour ouvrir · ▼ pour fermer"));
        head.addView(how);
        band.addView(head);

        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipToPadding(false);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(row);
        band.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View first = null;
        // la météo d'abord, à gauche ; les notifications à côté
        View wx = weatherCell();
        if (wx != null) row.addView(wx, cellLp(row));
        for (final NotifyService.Item it : items) {
            boolean hidden = locked(it.pkg);
            String appName = Install.label(a, it.pkg);
            Drawable d = null;
            try { d = a.getPackageManager().getApplicationIcon(it.pkg); } catch (Exception ignored) { }
            View c = cell(d, appName + " · " + NotifyPolicy.ago(it.when, System.currentTimeMillis(), L.french()),
                    hidden ? L.t("Nouvelle notification") : (it.title.isEmpty() ? appName : it.title),
                    hidden ? L.t("Appli verrouillée par code") : it.text, a.u(440), new View.OnClickListener() {
                        @Override public void onClick(View v) { a.closeOverlay(false); open(it); }
                    });
            row.addView(c, cellLp(row));
            if (first == null) first = c;
        }
        if (items.isEmpty() && on()) {
            boolean granted = NotifyService.granted(a);
            first = cell(null, "", L.t("Aucune notification"),
                    granted ? L.t("Les nouvelles sorties et messages des applis apparaîtront ici.") : L.t("OK : autoriser Lumen à lire les notifications"),
                    a.u(560), new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            a.closeOverlay(false);
                            SettingsPanel.reopenPage = SettingsPanel.NOTIFS;
                            SettingsPanel.openSettings(a);
                        }
                    });
            row.addView(first, cellLp(row));
        } else if (!items.isEmpty()) {
            row.addView(cell(null, "", L.t("Tout effacer"), L.t("Seulement de l'accueil"), a.u(260), new View.OnClickListener() {
                @Override public void onClick(View v) { NotifyService.forgetAll(); a.closeOverlay(true); }
            }), cellLp(row));
        }

        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        bl.leftMargin = bl.rightMargin = a.u(48);
        bl.topMargin = a.u(36);
        o.addView(band, bl);
        if (first == null) first = wx;
        if (first != null) first.requestFocus();
    }

    // ------------------------------------------------------------------ météo du volet

    /** Texte qui passe du blanc au sombre quand sa carte est sélectionnée. */
    private static void tint(GlassUi.Row c, final TextView t, final int rest, final int selected) {
        c.follow(new GlassUi.Row.Tint() { @Override public void at(float k) { t.setTextColor(Palette.mix(rest, selected, k)); } });
    }

    private static void tint(GlassUi.Row c, final Glyph g) {
        c.follow(new GlassUi.Row.Tint() { @Override public void at(float k) { g.setColor(Palette.mix(Palette.TEXT, Palette.ON_SELECTED, k)); } });
    }

    private static int glyphFor(int code) {
        switch (Forecast.kind(code)) {
            case Forecast.CLEAR: return Glyph.SUN;
            case Forecast.PARTLY: return Glyph.PARTLY;
            case Forecast.FOG: return Glyph.FOG;
            case Forecast.RAIN: return Glyph.RAIN;
            case Forecast.SNOW: return Glyph.SNOW;
            case Forecast.STORM: return Glyph.STORM;
            default: return Glyph.CLOUD;
        }
    }

    private ImageView glyphView(Glyph g) {
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(g);
        return iv;
    }

    /**
     * Météo du volet, dans le style des cartes de notification : grand pictogramme au trait et
     * température, le ciel et les extrêmes du jour, puis les trois jours suivants et le soleil
     * (lever, coucher, calculés sur la TV). Sans ville : OK pour en choisir une. Remplie depuis le
     * cache tout de suite, puis mise à jour si une prévision plus fraîche arrive.
     */
    private View weatherCell() {
        if (!a.weather.hasCity()) {
            if (a.prefs.isKidProfile()) return null;
            return cell(null, L.t("Météo"), L.t("Choisir une ville"), L.t("OK : la météo et les prochains jours s'afficheront ici"), a.u(440),
                    new View.OnClickListener() {
                        @Override public void onClick(View v) { a.closeOverlay(false); CityPanel.openCityInput(a); }
                    });
        }
        final GlassUi.Row c = new GlassUi.Row(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setFocusable(true);
        c.setClickable(true);
        c.setSoundEffectsEnabled(false);
        final int pad = a.u(Radius.NOTE_PAD);
        c.setPadding(pad, pad, pad, pad);
        c.setMinimumWidth(a.u(600));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.u(Radius.ROW)); // posée à PAD des bords de la bande : rayon externe = interne + marge
        c.setBackground(bg);
        c.follow(new GlassUi.Row.Tint() { @Override public void at(float k) { bg.setColor(Palette.mix(0x14FFFFFF, Palette.SELECTED, k)); } });
        c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) { if (f) Sounds.get(a).tick(); c.select(f); }
        });
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Sounds.get(a).tock(); a.showNotice(L.t("Ville de la météo : Réglages › Mode veille › Météo")); }
        });

        final TextView meta = a.text(15, "sans-serif-medium", Palette.TEXT_3);
        meta.setLetterSpacing(0.08f);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setText((L.t("Météo") + " · " + a.weather.city()).toUpperCase(java.util.Locale.FRANCE));
        tint(c, meta, Palette.TEXT_3, Palette.ON_SELECTED_2);
        c.addView(meta);

        // maintenant : pictogramme, température en grand, ciel et extrêmes du jour
        LinearLayout now = new LinearLayout(a);
        now.setOrientation(LinearLayout.HORIZONTAL);
        now.setGravity(Gravity.CENTER_VERTICAL);
        final Glyph big = new Glyph(Glyph.CLOUD, Palette.TEXT);
        tint(c, big);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(a.u(64), a.u(64));
        bl.rightMargin = a.u(16);
        now.addView(glyphView(big), bl);
        final TextView temp = a.text(58, "sans-serif-light", Palette.TEXT);
        temp.setText("—");
        tint(c, temp, Palette.TEXT, Palette.ON_SELECTED);
        now.addView(temp);
        LinearLayout sky = new LinearLayout(a);
        sky.setOrientation(LinearLayout.VERTICAL);
        final TextView cond = a.text(21, "sans-serif-medium", Palette.TEXT);
        cond.setSingleLine(true);
        cond.setEllipsize(TextUtils.TruncateAt.END);
        tint(c, cond, Palette.TEXT, Palette.ON_SELECTED);
        sky.addView(cond);
        final TextView range = a.text(18, "sans-serif", Palette.TEXT_2);
        tint(c, range, Palette.TEXT_2, Palette.ON_SELECTED_2);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = a.u(4);
        sky.addView(range, rl);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sl.leftMargin = a.u(20);
        now.addView(sky, sl);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = a.u(6);
        c.addView(now, nl);

        // les trois jours suivants, côte à côte ; le soleil dessous
        final LinearLayout days = new LinearLayout(a);
        days.setOrientation(LinearLayout.HORIZONTAL);
        days.setVisibility(View.GONE);
        final TextView[] dayName = new TextView[3], dayTemp = new TextView[3];
        final Glyph[] dayGlyph = new Glyph[3];
        for (int i = 0; i < 3; i++) {
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.HORIZONTAL);
            col.setGravity(Gravity.CENTER_VERTICAL);
            dayName[i] = a.text(16, "sans-serif-medium", Palette.TEXT_3);
            tint(c, dayName[i], Palette.TEXT_3, Palette.ON_SELECTED_2);
            col.addView(dayName[i]);
            dayGlyph[i] = new Glyph(Glyph.CLOUD, Palette.TEXT);
            tint(c, dayGlyph[i]);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(a.u(30), a.u(30));
            il.leftMargin = il.rightMargin = a.u(8);
            col.addView(glyphView(dayGlyph[i]), il);
            dayTemp[i] = a.text(17, "sans-serif", Palette.TEXT_2);
            tint(c, dayTemp[i], Palette.TEXT_2, Palette.ON_SELECTED_2);
            col.addView(dayTemp[i]);
            days.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = a.u(16);
        c.addView(days, dl);
        final TextView sun = a.text(17, "sans-serif", Palette.TEXT_3);
        java.util.Calendar today = java.util.Calendar.getInstance();
        int rise = Sky.sunrise(today), set = Sky.sunset(today);
        sun.setText(rise >= 0 && set >= 0 ? L.t("Soleil") + "   ↑ " + Sky.hhmm(rise) + "   ↓ " + Sky.hhmm(set) : "");
        sun.setVisibility(sun.getText().length() == 0 ? View.GONE : View.VISIBLE);
        tint(c, sun, Palette.TEXT_3, Palette.ON_SELECTED_2);
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ul.topMargin = a.u(12);
        c.addView(sun, ul);

        c.setContentDescription(L.t("Météo") + " " + a.weather.city());
        a.weather.forecast(new Weather.ForecastCallback() {
            @Override public void onForecast(Forecast f) {
                big.setType(glyphFor(f.code));
                temp.setText(Forecast.degrees(f.temp));
                cond.setText(L.t(Forecast.describe(f.code)));
                if (f.max.length > 0) range.setText("↑ " + Forecast.degrees(f.max[0]) + "    ↓ " + Forecast.degrees(f.min[0]));
                java.util.Calendar day = java.util.Calendar.getInstance();
                int shown = 0;
                for (int i = 0; i < 3 && i + 1 < f.codes.length; i++) {
                    day.add(java.util.Calendar.DAY_OF_MONTH, 1);
                    dayName[i].setText(DateLine.shortDay(day, L.lang()).toUpperCase(java.util.Locale.FRANCE));
                    dayGlyph[i].setType(glyphFor(f.codes[i + 1]));
                    dayTemp[i].setText(Forecast.degrees(f.max[i + 1]) + "  " + Forecast.degrees(f.min[i + 1]));
                    shown++;
                }
                days.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
                c.setContentDescription(L.t("Météo") + " " + a.weather.city() + ". " + temp.getText() + ", " + cond.getText() + ". " + range.getText());
            }
        });
        return c;
    }

    private List<NotifyService.Item> visibleItems() {
        List<NotifyService.Item> out = new java.util.ArrayList<>();
        if (!on()) return out;
        java.util.Set<String> muted = a.prefs.notifyMuted();
        for (NotifyService.Item i : NotifyService.items()) if (!muted.contains(i.pkg)) out.add(i);
        return out;
    }

    private LinearLayout.LayoutParams cellLp(LinearLayout row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        if (row.getChildCount() > 0) lp.leftMargin = a.u(Radius.ROW_GAP * 2);
        return lp;
    }

    /**
     * Une notification dans le volet : icône, appli et heure, titre, deux lignes de texte. Posée à
     * PAD des bords de la bande, elle en prend l'arrondi (ROW) ; sélectionnée, elle devient claire
     * en fondu, comme les lignes des réglages.
     */
    private View cell(Drawable art, String meta, String head, String body, int width, View.OnClickListener click) {
        final GlassUi.Row c = new GlassUi.Row(a);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.TOP);
        c.setFocusable(true);
        c.setClickable(true);
        c.setSoundEffectsEnabled(false);
        int pad = a.u(Radius.NOTE_PAD);
        c.setPadding(pad, pad, pad, pad);
        c.setMinimumWidth(width);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.u(Radius.ROW));
        c.setBackground(bg);
        if (art != null) {
            ImageView iv = new ImageView(a);
            iv.setImageDrawable(art);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            final float r = a.u(Radius.inner(Radius.ROW, Radius.NOTE_PAD));
            iv.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
            });
            iv.setClipToOutline(true);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(a.u(48), a.u(48));
            il.rightMargin = a.u(16);
            c.addView(iv, il);
        }
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        final TextView m = a.text(15, "sans-serif-medium", Palette.TEXT_3);
        m.setText(meta.toUpperCase(java.util.Locale.FRANCE));
        m.setLetterSpacing(0.08f);
        m.setSingleLine(true);
        m.setEllipsize(TextUtils.TruncateAt.END);
        m.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
        col.addView(m);
        final TextView t = a.text(23, "sans-serif-medium", Palette.TEXT);
        t.setText(head);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = meta.isEmpty() ? 0 : a.u(6);
        col.addView(t, tl);
        final TextView b = a.text(19, "sans-serif", Palette.TEXT_2);
        b.setText(body);
        b.setMaxLines(2);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setVisibility(body == null || body.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        xl.topMargin = a.u(4);
        col.addView(b, xl);
        int inner = width - 2 * pad - (art != null ? a.u(64) : 0);
        t.setMaxWidth(inner);
        b.setMaxWidth(inner);
        m.setMaxWidth(inner);
        c.addView(col, new LinearLayout.LayoutParams(inner, ViewGroup.LayoutParams.WRAP_CONTENT));
        c.setContentDescription(meta + ". " + head + ". " + (body == null ? "" : body));
        c.follow(new GlassUi.Row.Tint() {
            @Override public void at(float k) {
                int sel = Palette.SELECTED;
                bg.setColor(Palette.mix(0x14FFFFFF, sel, k));
                t.setTextColor(Palette.mix(Palette.TEXT, Palette.ON_SELECTED, k));
                b.setTextColor(Palette.mix(Palette.TEXT_2, Palette.ON_SELECTED_2, k));
                m.setTextColor(Palette.mix(Palette.TEXT_3, Palette.ON_SELECTED_2, k));
            }
        });
        c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick();
                c.select(f);
            }
        });
        final View.OnClickListener k = click;
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Sounds.get(a).tock(); k.onClick(v); }
        });
        return c;
    }

    /** Ouvre la notification dans son appli (code demandé si l'appli est verrouillée). */
    private void open(final NotifyService.Item it) {
        Runnable go = new Runnable() {
            @Override public void run() {
                NotifyService.forget(it.key);
                boolean ok = false;
                if (it.open != null) {
                    try { it.open.send(); ok = true; } catch (PendingIntent.CanceledException ignored) { } catch (Exception ignored) { }
                }
                if (!ok) {
                    android.content.Intent i = a.getPackageManager().getLeanbackLaunchIntentForPackage(it.pkg);
                    if (i == null) i = a.getPackageManager().getLaunchIntentForPackage(it.pkg);
                    if (i != null) {
                        try { i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); a.startActivity(i); ok = true; } catch (Exception ignored) { }
                    }
                }
                if (!ok) a.showNotice(L.t("Cette notification ne s'ouvre pas"));
            }
        };
        if (locked(it.pkg)) PinDialog.ask(a, L.t("Code pour ") + Install.label(a, it.pkg), go);
        else go.run();
    }
}

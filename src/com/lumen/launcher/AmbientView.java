// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Random;

/**
 * Mode veille : plus d'applis, le fond en plein écran, et une seule ligne discrète en bas :
 * heure · date · météo · soleil · lune, et à droite la légende de l'image.
 * La ligne se déplace de quelques pixels chaque minute pour protéger l'écran.
 */
final class AmbientView extends FrameLayout {
    private static final int TXT = 0xE6FFFFFF, DIM = 0x8CFFFFFF;
    private final float unit;
    private final LinearLayout bar;
    private final TextView weather, sky, caption, sepWeather, sepSky;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final Weather weatherSrc;
    private final TextClock clock;
    private boolean active;
    /** Images propres au mode veille (réglage « Images du mode veille »), sous le voile et la ligne. */
    private final FrameLayout wallLayer;
    /** Lumière selon l'heure (plus chaude le soir, plus sombre la nuit). */
    private final View tint;
    /** Écran de nuit : presque noir, une grande horloge très douce. */
    private final FrameLayout night;
    private final TextClock nightClock;
    private final Prefs prefs;
    private boolean nightOn;

    AmbientView(Context c, float unit, Weather w) {
        super(c);
        this.unit = unit;
        this.weatherSrc = w;
        setClipChildren(false);

        prefs = new Prefs(c);
        wallLayer = new FrameLayout(c);
        addView(wallLayer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tint = new View(c);
        addView(tint, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // voile très léger, juste sous la ligne
        View scrim = new View(c);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x8C04040A, 0x2604040A, 0x0004040A}));
        addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u(260), Gravity.BOTTOM));

        bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        clock = new TextClock(c);
        clock.setFormat24Hour("H:mm");
        clock.setFormat12Hour("h:mm");
        style(clock, 46, "sans-serif-light", 0xF5FFFFFF);
        clock.setMinWidth(Math.round(clock.getPaint().measureText("00:00") * 1.04f));
        bar.addView(clock);

        DateClock date = new DateClock(c); // dans la langue de Lumen, pas celle d'Android
        style(date, 24, "sans-serif", TXT);
        bar.addView(date, gap(18));

        sepWeather = sep(c);
        bar.addView(sepWeather);
        weather = new TextView(c);
        style(weather, 24, "sans-serif", TXT);
        bar.addView(weather);

        sepSky = sep(c);
        bar.addView(sepSky);
        sky = new TextView(c);
        style(sky, 24, "sans-serif", DIM);
        bar.addView(sky);

        // la légende prend la place qui reste, à droite, coupée proprement si elle est longue
        caption = new TextView(c);
        style(caption, 21, "sans-serif", DIM);
        caption.setGravity(Gravity.END);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.leftMargin = u(48);
        bar.addView(caption, cl);

        LayoutParams bl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bl.leftMargin = bl.rightMargin = u(112);
        bl.bottomMargin = u(56);
        addView(bar, bl);

        night = new FrameLayout(c);
        night.setBackgroundColor(0xF5000000);
        night.setVisibility(GONE);
        nightClock = new TextClock(c);
        nightClock.setFormat24Hour("H:mm");
        nightClock.setFormat12Hour("h:mm");
        nightClock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 260 * unit);
        nightClock.setTypeface(Fonts.get("sans-serif-thin", Typeface.NORMAL));
        nightClock.setTextColor(0x38FFFFFF); // juste lisible dans le noir, sans éclairer la pièce
        nightClock.setIncludeFontPadding(false);
        night.addView(nightClock, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        addView(night, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    FrameLayout wallLayer() { return wallLayer; }

    /** Écran de nuit affiché (presque noir) : rien ne doit l'éclairer, pas même une notification. */
    boolean nightShown() { return nightOn && getVisibility() == VISIBLE; }

    /** Voile de couleur selon l'heure : doré le matin, neutre le jour, chaud le soir, sombre la nuit. */
    static int tintFor(int hour) {
        if (hour >= 6 && hour < 9) return 0x14FFC880;
        if (hour >= 9 && hour < 17) return 0x00000000;
        if (hour >= 17 && hour < 21) return 0x22FF9440;
        return 0x3A0A0500;
    }

    /** Écran de nuit actif à cette heure ? (de nightHour jusqu'à 7 h ; -1 = jamais) */
    static boolean isNight(int hour, int nightHour) { return Greeting.night(hour, nightHour); }

    /** Lumière et écran de nuit selon l'heure (appelé au départ puis chaque minute). */
    private void updateMood(boolean animate) {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        final int want = tintFor(hour);
        Object cur = tint.getTag();
        if (!(cur instanceof Integer) || (Integer) cur != want) {
            tint.setTag(want);
            if (animate) {
                android.animation.ValueAnimator va = android.animation.ValueAnimator.ofArgb(
                        cur instanceof Integer ? (Integer) cur : 0, want);
                va.setDuration(20_000);
                va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(android.animation.ValueAnimator a) { tint.setBackgroundColor((Integer) a.getAnimatedValue()); }
                });
                va.start();
            } else tint.setBackgroundColor(want);
        }
        boolean n = isNight(hour, prefs.nightHour());
        if (n != nightOn) {
            nightOn = n;
            if (n) {
                night.setVisibility(VISIBLE);
                night.setAlpha(0f);
                night.animate().alpha(1f).setDuration(animate ? 6000 : 0).start();
                bar.animate().alpha(0f).setDuration(animate ? 3000 : 0).start();
            } else {
                night.animate().alpha(0f).setDuration(animate ? 6000 : 0).withEndAction(new Runnable() {
                    @Override public void run() { if (!nightOn) night.setVisibility(GONE); }
                }).start();
                bar.animate().alpha(1f).setDuration(animate ? 3000 : 0).start();
            }
        }
    }

    private int u(float px) { return Math.round(px * unit); }

    private void style(TextView t, float px, String family, int color) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * unit);
        t.setTypeface(Fonts.get(family, Typeface.NORMAL));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setShadowLayer(u(10), 0, 0, 0x66000000);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
    }

    private LinearLayout.LayoutParams gap(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = u(left);
        return lp;
    }

    private TextView sep(Context c) {
        TextView s = new TextView(c);
        style(s, 24, "sans-serif", 0x59FFFFFF);
        s.setText("·");
        s.setPadding(u(18), 0, u(18), 0);
        return s;
    }

    void setCaption(final WallItem item) {
        if (item == null) return;
        caption.animate().alpha(0f).setDuration(400).withEndAction(new Runnable() {
            @Override public void run() {
                caption.setText(TextUtils.isEmpty(item.credit) ? item.title : item.title + "  ·  " + item.credit);
                caption.animate().alpha(1f).setDuration(900).start();
            }
        }).start();
    }

    void start() {
        active = true;
        nightOn = !isNight(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY), prefs.nightHour()); // force la mise en place
        updateMood(false);
        clock.requestLayout();
        refreshSky();
        refreshWeather();
        h.postDelayed(drift, 60_000);
    }

    void stop() {
        active = false;
        h.removeCallbacksAndMessages(null);
    }

    /** « ↑ 7:58  ↓ 17:12  ·  Premier croissant » : soleil et Lune calculés sur la TV. */
    private void refreshSky() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        sky.setText("↑ " + Sky.hhmm(Sky.sunrise(now)) + "   ↓ " + Sky.hhmm(Sky.sunset(now))
                + "   ·   " + Sky.moonPhase(System.currentTimeMillis()));
        if (active) h.postDelayed(new Runnable() { @Override public void run() { if (active) refreshSky(); } }, 30 * 60_000);
    }

    private void refreshWeather() {
        weatherSrc.current(new Weather.Callback() {
            @Override public void onWeather(String line) {
                boolean has = !TextUtils.isEmpty(line);
                weather.setText(has ? line : "");
                if (has && java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= 18) {
                    final String today = line;
                    weatherSrc.tomorrow(new Weather.Callback() {
                        @Override public void onWeather(String t) { if (!TextUtils.isEmpty(t)) weather.setText(today + "   ·   " + t); }
                    });
                }
                weather.setVisibility(has ? VISIBLE : GONE);
                sepWeather.setVisibility(has ? VISIBLE : GONE);
            }
        });
        if (active) h.postDelayed(new Runnable() { @Override public void run() { if (active) refreshWeather(); } }, 15 * 60_000);
    }

    /** Déplace doucement la ligne de quelques pixels chaque minute (anti-marquage). */
    private final Runnable drift = new Runnable() {
        @Override public void run() {
            float dx = (rnd.nextFloat() - 0.5f) * u(40), dy = (rnd.nextFloat() - 0.5f) * u(24);
            bar.animate().translationX(dx).translationY(dy).setDuration(8000).start();
            // l'horloge de nuit se déplace bien plus largement (protection de l'écran)
            nightClock.animate().translationX((rnd.nextFloat() - 0.5f) * u(520)).translationY((rnd.nextFloat() - 0.5f) * u(300)).setDuration(20_000).start();
            updateMood(true);
            if (active) h.postDelayed(this, 60_000);
        }
    };
}

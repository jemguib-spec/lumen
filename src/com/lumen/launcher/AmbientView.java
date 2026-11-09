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

    AmbientView(Context c, float unit, Weather w) {
        super(c);
        this.unit = unit;
        this.weatherSrc = w;
        setClipChildren(false);

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

        TextClock date = new TextClock(c);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
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
            if (active) h.postDelayed(this, 60_000);
        }
    };
}

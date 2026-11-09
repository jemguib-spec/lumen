// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Vignette du style « Verre » : surface en verre dépoli avec le nom de l'appli (monochrome)
 * ou bannière officielle. Au focus : se soulève, et en monochrome devient blanche, texte noir.
 */
final class Tile extends FrameLayout {

    interface FocusListener { void onTileFocus(Tile t, boolean focused); }

    final FrameLayout card;
    Object tag;
    String title = "";
    private final float radius;
    private final boolean round;
    private Glass.Surface glass;
    private TextView label;
    private Glyph glyph;
    private View favDot;
    private FocusListener focusListener;
    private float focusScale = 1.1f;
    private int focusText = 0xFF111216;
    private boolean banner;

    /**
     * Couleur de l'appli lisible sur blanc : trop claire → assombrie ; grise ou terne → presque noir.
     */
    static int readable(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        if (hsv[1] < 0.25f) return 0xFF111216;           // pas de vraie couleur : texte sombre
        hsv[2] = Math.min(hsv[2], hsv[1] > 0.6f ? 0.82f : 0.62f); // assez sombre pour le contraste
        hsv[1] = Math.max(hsv[1], 0.55f);
        return Color.HSVToColor(hsv);
    }
    private final float restAlpha;

    static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    /** Vignette d'appli. */
    /**
     * @param banner   bannière officielle si l'appli en a une
     * @param showIcon icône de l'appli au-dessus du nom (vignettes en verre)
     * @param tint     verre teinté de la couleur dominante de l'icône
     */
    static Tile app(Context c, AppRepo.App a, int w, int h, boolean banner, boolean showIcon, boolean tint, boolean labelBelow) {
        return app(c, a, w, h, banner, showIcon, tint, labelBelow, false);
    }

    /**
     * @param word style « Nom » : le nom de l'appli en grand ; au focus, vignette blanche et nom
     *             dans la couleur de l'appli
     */
    static Tile app(Context c, AppRepo.App a, int w, int h, boolean banner, boolean showIcon, boolean tint, boolean labelBelow, boolean word) {
        Tile t = new Tile(c, w, h, dp(c, 10), false, banner ? 0.85f : 1f);
        t.title = a.label;
        t.tag = a;
        Drawable bannerD = banner ? a.banner(c.getPackageManager()) : null;
        if (bannerD != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(bannerD);
            t.card.addView(iv, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            t.card.setBackgroundColor(0xFF1C1D22);
            t.banner = true;
        } else if (word) {
            t.glass = new Glass.Surface(t.card, dp(c, 10), 0x1FFFFFFF);
            if (tint) t.glass.setGlassTint(a.color(c.getPackageManager()));
            t.card.setBackground(t.glass);
            t.focusText = readable(a.color(c.getPackageManager()));
            t.label = new TextView(c);
            t.label.setText(a.label);
            t.label.setTextColor(0xF2FFFFFF);
            t.label.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * (a.label.length() > 12 ? 0.15f : 0.19f));
            t.label.setTypeface(Fonts.get("sans-serif", Typeface.BOLD));
            t.label.setLetterSpacing(-0.01f);
            t.label.setSingleLine(true);
            t.label.setEllipsize(TextUtils.TruncateAt.END);
            t.label.setGravity(Gravity.CENTER);
            LayoutParams tl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            tl.leftMargin = tl.rightMargin = (int) (h * 0.12f);
            t.card.addView(t.label, tl);
        } else {
            t.glass = new Glass.Surface(t.card, dp(c, 10), 0x1FFFFFFF);
            if (tint) t.glass.setGlassTint(a.color(c.getPackageManager()));
            t.card.setBackground(t.glass);
            boolean icon = showIcon || banner; // sans bannière, on montre au moins l'icône
            if (icon && labelBelow) {
                // le nom est déjà affiché sous la vignette : icône seule, centrée
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageDrawable(a.icon(c.getPackageManager()));
                int s = (int) (h * 0.46f);
                t.card.addView(iv, new LayoutParams(s, s, Gravity.CENTER));
                return t;
            }
            if (icon) {
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageDrawable(a.icon(c.getPackageManager()));
                int s = (int) (h * 0.38f);
                LayoutParams lp = new LayoutParams(s, s, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                lp.topMargin = (int) (h * 0.17f);
                iv.setElevation(dp(c, 2));
                t.card.addView(iv, lp);
            }
            t.label = new TextView(c);
            t.label.setText(a.label);
            t.label.setTextColor(0xF2FFFFFF);
            t.label.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * (icon ? 0.13f : 0.175f));
            t.label.setTypeface(Fonts.get("sans-serif-medium", Typeface.NORMAL));
            t.label.setSingleLine(true);
            t.label.setEllipsize(TextUtils.TruncateAt.END);
            t.label.setGravity(Gravity.CENTER);
            LayoutParams tl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    icon ? Gravity.BOTTOM : Gravity.CENTER);
            tl.leftMargin = tl.rightMargin = (int) (h * 0.1f);
            if (icon) tl.bottomMargin = (int) (h * 0.14f);
            t.card.addView(t.label, tl);
        }
        return t;
    }

    /** Bouton rond en verre (toutes les applis, réglages). */
    static Tile button(Context c, int glyphType, String title, int size) {
        Tile t = new Tile(c, size, size, size / 2f, true, 1f);
        t.title = title;
        t.focusScale = 1.12f;
        t.glass = new Glass.Surface(t.card, size / 2f, 0x1FFFFFFF);
        t.card.setBackground(t.glass);
        t.glyph = new Glyph(glyphType, 0xF2FFFFFF);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(t.glyph);
        int g = (int) (size * 0.42f);
        t.card.addView(iv, new LayoutParams(g, g, Gravity.CENTER));
        return t;
    }

    private Tile(Context c, int w, int h, float radius, boolean round, float restAlpha) {
        super(c);
        this.radius = radius;
        this.round = round;
        this.restAlpha = restAlpha;
        setFocusable(true);
        setClickable(true);
        setSoundEffectsEnabled(false);
        setClipChildren(false);
        setClipToPadding(false);
        card = new FrameLayout(c);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Tile.this.radius);
            }
        });
        card.setClipToOutline(true);
        addView(card, new LayoutParams(w, h));
        setAlpha(restAlpha);
        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) { animateFocus(f); }
        });
    }

    void setFocusListener(FocusListener l) { focusListener = l; }

    void setFavoriteMark(boolean fav) {
        if (fav && favDot == null) {
            favDot = new View(getContext());
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.WHITE);
            d.setStroke((int) dp(getContext(), 1), 0x55000000);
            favDot.setBackground(d);
            int s = (int) dp(getContext(), 6);
            LayoutParams lp = new LayoutParams(s, s, Gravity.TOP | Gravity.END);
            lp.topMargin = lp.rightMargin = (int) dp(getContext(), 7);
            card.addView(favDot, lp);
        }
        if (favDot != null) favDot.setVisibility(fav ? VISIBLE : GONE);
    }

    void setMoving(boolean moving) {
        if (moving) {
            GradientDrawable b = new GradientDrawable();
            b.setCornerRadius(radius);
            b.setStroke((int) dp(getContext(), 2), Color.WHITE);
            card.setForeground(b);
            animate().translationY(-dp(getContext(), 8)).setDuration(180).start();
        } else {
            card.setForeground(null);
            animate().translationY(0).setDuration(180).start();
        }
    }

    /** Redessine le verre (le fond a changé ou la vue a bougé). */
    void refreshGlass() { card.invalidate(); }

    private void animateFocus(boolean f) {
        float s = f ? focusScale : 1f;
        animate().scaleX(s).scaleY(s).alpha(f ? 1f : restAlpha)
                .setDuration(240).setInterpolator(new DecelerateInterpolator(2.2f)).start();
        card.setElevation(f ? dp(getContext(), 16) : 0);
        if (banner) { // bannière : liseré blanc au focus (une bannière blanche ne doit pas sembler sélectionnée)
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(radius);
            ring.setStroke((int) dp(getContext(), 2.5f), Color.WHITE);
            card.setForeground(f ? ring : null);
        }
        if (glass != null) {
            glass.setSolid(f);
            if (label != null) label.setTextColor(f ? focusText : 0xF2FFFFFF);
            if (glyph != null) glyph.setColor(f ? 0xFF111216 : 0xF2FFFFFF);
        }
        if (f) {
            sheen();
            Sounds snd = Sounds.peek();
            if (snd != null) snd.tick();
        }
        if (focusListener != null) focusListener.onTileFocus(this, f);
    }

    /** Reflet lumineux qui balaie la vignette une fois quand elle reçoit le focus. */
    private void sheen() {
        int w = card.getWidth(), h = card.getHeight();
        if (w == 0 || round) return;
        final View s = new View(getContext());
        s.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00FFFFFF, glass != null ? 0x26FFFFFF : 0x40FFFFFF, 0x00FFFFFF}));
        int sw = (int) (w * 0.4f);
        LayoutParams lp = new LayoutParams(sw, (int) (h * 1.8f));
        lp.topMargin = (int) (-h * 0.4f);
        s.setRotation(18);
        s.setTranslationX(-sw * 1.2f);
        card.addView(s, lp);
        s.animate().translationX(w + sw * 0.2f).setStartDelay(60).setDuration(700)
                .setInterpolator(new DecelerateInterpolator(1.4f)).withEndAction(new Runnable() {
                    @Override public void run() { card.removeView(s); }
                }).start();
    }
}

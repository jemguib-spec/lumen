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
    /** Rayon des vignettes d'applis, en dp. */
    static final float TILE_RADIUS_DP = 10;
    private final boolean round;
    private Glass.Surface glass;
    private TextView label;
    private Glyph glyph;
    private View favDot;
    private FocusListener focusListener;
    private float focusScale = 1.1f;
    private int focusText = Palette.ON_SELECTED;
    private boolean banner;

    /**
     * Couleur de l'appli lisible sur blanc : trop claire → assombrie ; grise ou terne → presque noir.
     */
    static int readable(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        if (hsv[1] < 0.25f) return Palette.ON_SELECTED;           // pas de vraie couleur : texte sombre
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
        Tile t = new Tile(c, w, h, dp(c, TILE_RADIUS_DP), false, banner ? 0.85f : 1f);
        t.title = a.label;
        t.setContentDescription(a.label); // lecteur d'écran : le nom de l'appli, pas « bouton sans nom »
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
            t.glass = new Glass.Surface(t.card, dp(c, TILE_RADIUS_DP), 0x1FFFFFFF);
            if (tint) t.glass.setGlassTint(a.color(c.getPackageManager()));
            t.card.setBackground(t.glass);
            t.focusText = readable(a.color(c.getPackageManager()));
            t.label = new TextView(c);
            t.label.setText(a.label);
            t.label.setTextColor(Palette.TEXT);
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
            t.glass = new Glass.Surface(t.card, dp(c, TILE_RADIUS_DP), 0x1FFFFFFF);
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
            t.label.setTextColor(Palette.TEXT);
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
    /** Change le pictogramme et le nom d'un bouton rond (bouton Sons : allumé ou coupé). */
    void setGlyph(int glyphType, String newTitle) {
        if (glyph != null) glyph.setType(glyphType);
        title = newTitle;
        setContentDescription(newTitle);
    }

    static Tile button(Context c, int glyphType, String title, int size) {
        Tile t = new Tile(c, size, size, size / 2f, true, 1f);
        t.title = title;
        t.setContentDescription(title);
        t.focusScale = 1.12f;
        t.glass = new Glass.Surface(t.card, size / 2f, 0x1FFFFFFF);
        t.card.setBackground(t.glass);
        t.glyph = new Glyph(glyphType, Palette.TEXT);
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
            d.setStroke((int) dp(getContext(), 1), Palette.SHADOW);
            favDot.setBackground(d);
            int s = (int) dp(getContext(), 6);
            LayoutParams lp = new LayoutParams(s, s, Gravity.TOP | Gravity.END);
            // arrondis imbriqués (Radius) : rayon du point (3) + marge (7) = rayon de la vignette (10)
            lp.topMargin = lp.rightMargin = (int) dp(getContext(), Radius.pad(TILE_RADIUS_DP, 3));
            card.addView(favDot, lp);
        }
        if (favDot != null) favDot.setVisibility(fav ? VISIBLE : GONE);
    }

    private TextView badge;

    /**
     * Bouton rond « vivant » : n > 0 affiche le nombre en petit et éclaire légèrement le verre
     * (lumières allumées dans la maison) ; 0 le remet au repos.
     */
    void setBadge(int n) {
        if (glass != null) glass.setVeil(n > 0 ? 0x4DFFD9A0 : 0x1FFFFFFF);
        if (n <= 0) { if (badge != null) badge.setVisibility(GONE); return; }
        int size = card.getLayoutParams().width;
        if (badge == null) {
            badge = new TextView(getContext());
            badge.setTypeface(Fonts.get("sans-serif-medium", Typeface.BOLD));
            badge.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.19f);
            badge.setTextColor(Palette.ON_SELECTED);
            badge.setGravity(Gravity.CENTER);
            badge.setIncludeFontPadding(false);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(size);
            d.setColor(Palette.SELECTED_NEUTRAL);
            badge.setBackground(d);
            int h = Math.round(size * 0.32f);
            badge.setMinWidth(h);
            badge.setPadding(h / 4, 0, h / 4, 0);
            badge.setElevation(dp(getContext(), 20));
            badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(badge, new LayoutParams(LayoutParams.WRAP_CONTENT, h, Gravity.TOP | Gravity.END));
        }
        badge.setText(String.valueOf(Math.min(n, 99)));
        badge.setVisibility(VISIBLE);
    }

    private View usualRing;

    /** Appli habituelle à cette heure : un liseré lumineux qui respire doucement, puis reste discret. */
    void setUsual(boolean on) {
        if (!on) { if (usualRing != null) { usualRing.animate().cancel(); card.removeView(usualRing); usualRing = null; } return; }
        if (usualRing != null) return;
        usualRing = new View(getContext());
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(radius);
        ring.setStroke((int) dp(getContext(), 2), 0xCCFFFFFF);
        usualRing.setBackground(ring);
        usualRing.setAlpha(0f);
        card.addView(usualRing, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        breathe(usualRing, 3);
    }

    private static void breathe(final View v, final int left) {
        if (v.getParent() == null) return;
        v.animate().alpha(left > 0 ? 0.9f : 0.32f).setDuration(1100).withEndAction(new Runnable() {
            @Override public void run() {
                if (left > 0) v.animate().alpha(0.15f).setDuration(1100).withEndAction(new Runnable() {
                    @Override public void run() { breathe(v, left - 1); }
                }).start();
            }
        }).start();
    }

    /** Opacité au repos (sélectionnée ou non). */
    float restingAlpha() { return isFocused() ? 1f : restAlpha; }

    /** Après l'ouverture d'une appli : la vignette reprend sa taille, prête pour le retour. */
    void restoreAfterLaunch() {
        animate().cancel();
        animate().setStartDelay(0);
        float s = isFocused() ? focusScale : 1f;
        setScaleX(s);
        setScaleY(s);
        setAlpha(isFocused() ? 1f : restAlpha);
    }

    void setMoving(boolean moving) {
        if (moving) {
            GradientDrawable b = new GradientDrawable();
            b.setCornerRadius(radius);
            b.setStroke((int) dp(getContext(), 2), Color.WHITE);
            card.setForeground(b);
            animate().translationY(-dp(getContext(), 8)).setDuration(Motion.FAST).start();
        } else {
            card.setForeground(null);
            animate().translationY(0).setDuration(Motion.FAST).start();
        }
    }

    /** Redessine le verre (le fond a changé ou la vue a bougé). */
    void refreshGlass() { card.invalidate(); }

    private void animateFocus(boolean f) {
        float s = f ? focusScale : 1f;
        // léger rebond à la sélection, retour sans rebond ; l'ombre monte et descend avec
        animate().scaleX(s).scaleY(s).alpha(f ? 1f : restAlpha)
                .setDuration(f ? 360 : 220)
                .setInterpolator(f ? new android.view.animation.OvershootInterpolator(1.6f) : new DecelerateInterpolator(2.2f)).start();
        card.animate().translationZ(f ? dp(getContext(), 18) : 0).setDuration(f ? 360 : 220).start();
        if (banner) { // bannière : liseré blanc au focus (une bannière blanche ne doit pas sembler sélectionnée)
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(radius);
            ring.setStroke((int) dp(getContext(), 2.5f), Color.WHITE);
            card.setForeground(f ? ring : null);
        }
        if (glass != null) fadeSelection(f);
        if (f) {
            sheen();
            Sounds snd = Sounds.peek();
            if (snd != null) snd.tick();
        }
        if (focusListener != null) focusListener.onTileFocus(this, f);
    }

    private float selection;                 // 0 = verre, 1 = vignette blanche
    private android.animation.ValueAnimator selectionAnim;

    /**
     * Le verre devient blanc (et le nom ou le pictogramme sombre) en fondu court, au lieu de
     * basculer d'un coup : la sélection glisse d'une vignette à l'autre.
     */
    private void fadeSelection(boolean f) {
        if (selectionAnim != null) selectionAnim.cancel();
        selectionAnim = android.animation.ValueAnimator.ofFloat(selection, f ? 1f : 0f);
        selectionAnim.setDuration(Motion.FOCUS);
        selectionAnim.setInterpolator(Motion.EASE);
        selectionAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator va) {
                selection = (Float) va.getAnimatedValue();
                glass.setSolidLevel(selection);
                if (label != null) label.setTextColor(Palette.mix(Palette.TEXT, focusText, selection));
                if (glyph != null) glyph.setColor(Palette.mix(Palette.TEXT, Palette.ON_SELECTED, selection));
            }
        });
        selectionAnim.start();
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
        s.animate().translationX(w + sw * 0.2f).setStartDelay(60).setDuration(Motion.SLOW)
                .setInterpolator(new DecelerateInterpolator(1.4f)).withEndAction(new Runnable() {
                    @Override public void run() { card.removeView(s); }
                }).start();
    }
}

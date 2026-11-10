// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/** Briques des panneaux en verre : carte, lignes de réglage et de menu, en-tête, séparation, titre de section. */
final class GlassUi {
    private final MainActivity a;

    GlassUi(MainActivity a) { this.a = a; }

    /** Carte de verre flottante. */
    LinearLayout glassCard(float radiusPx) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        final float radius = a.u(radiusPx);
        card.setBackground(new Glass.Surface(card, radius, a.highContrast ? Palette.SURFACE_SOLID : Palette.SURFACE));
        card.setElevation(a.u(40));
        // le contenu (listes qui défilent) reste dans les bords arrondis
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        return card;
    }

    LinearLayout listRow(String label, final MainActivity.Value value, final View.OnClickListener click) {
        final LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setSoundEffectsEnabled(false);
        r.setPadding(a.u(32), 0, a.u(32), 0);
        final GradientDrawable bg = new GradientDrawable();
        // arrondi concentrique avec les cartes (rayon 48, marge 24) : les coins suivent ceux du panneau
        bg.setCornerRadius(a.u(24));
        bg.setColor(0x00FFFFFF);
        r.setBackground(bg);
        final TextView l = a.text(28, "sans-serif", Palette.TEXT);
        l.setText(label);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView v = a.text(24, "sans-serif", Palette.TEXT_2);
        // lecteur d'écran : « Horloge, Oui » plutôt que deux textes séparés
        r.setContentDescription(label);
        if (value != null) {
            v.setText(value.get());
            r.setContentDescription(label + ", " + value.get());
            a.rowRefreshers.add(new Runnable() { @Override public void run() { String s = value.get(); v.setText(s); r.setContentDescription(label + ", " + s); } });
        }
        l.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.leftMargin = a.u(20);
        r.addView(v, vl);
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean f) {
                if (f) Sounds.get(a).tick();
                bg.setColor(f ? Palette.SELECTED : 0x00FFFFFF);
                l.setTextColor(f ? Palette.ON_SELECTED : Palette.TEXT);
                v.setTextColor(f ? Palette.ON_SELECTED_2 : Palette.TEXT_2);
                // ni agrandissement ni ombre : dans un panneau arrondi, ils seraient coupés aux bords
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Sounds.get(a).tock();
                click.onClick(view);
                for (Runnable rr : new ArrayList<>(a.rowRefreshers)) rr.run();
            }
        });
        return r;
    }

    interface Bool { boolean get(); }
    interface IntValue { int get(); }
    interface IntSetter { void set(int i); }

    /**
     * Ligne de réglage à deux états, avec un vrai interrupteur dessiné à droite (lisible de loin,
     * sans lire « Oui / Non »). OK bascule ; l'appelant change le réglage dans click.
     */
    LinearLayout toggleRow(String label, final Bool get, final View.OnClickListener click) {
        final LinearLayout r = listRow(label, null, click);
        final Toggle t = new Toggle(a);
        t.setOn(get.get(), false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(a.u(72), a.u(40));
        tl.leftMargin = a.u(20);
        r.addView(t, tl);
        r.setContentDescription(label + ", " + (get.get() ? "activé" : "désactivé"));
        a.rowRefreshers.add(new Runnable() {
            @Override public void run() { t.setOn(get.get(), true); r.setContentDescription(r.getChildAt(0) == null ? "" : ((TextView) r.getChildAt(0)).getText() + ", " + (get.get() ? "activé" : "désactivé")); }
        });
        final View.OnFocusChangeListener base = r.getOnFocusChangeListener();
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) { if (base != null) base.onFocusChange(v, f); t.setFocused(f); }
        });
        return r;
    }

    /**
     * Ligne de réglage à plusieurs valeurs : « ‹  valeur  › ». ◀ et ▶ changent la valeur sans
     * quitter la ligne, OK passe à la suivante. Fini le choix en boucle à l'aveugle.
     */
    LinearLayout choiceRow(String label, final String[] names, final IntValue get, final IntSetter set) {
        final MainActivity.Value v = new MainActivity.Value() {
            @Override public String get() { int i = get.get(); return "‹   " + names[Math.max(0, Math.min(names.length - 1, i))] + "   ›"; }
        };
        final LinearLayout r = listRow(label, v, new View.OnClickListener() {
            @Override public void onClick(View view) { set.set((get.get() + 1) % names.length); }
        });
        r.setTag("choice"); // MainActivity : ◀ ne revient pas en arrière sur cette ligne
        r.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View view, int code, android.view.KeyEvent e) {
                if (e.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                int n = names.length;
                if (code == android.view.KeyEvent.KEYCODE_DPAD_LEFT) set.set((get.get() + n - 1) % n);
                else if (code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) set.set((get.get() + 1) % n);
                else return false;
                Sounds.get(a).tick();
                for (Runnable rr : new ArrayList<>(a.rowRefreshers)) rr.run();
                return true;
            }
        });
        return r;
    }

    /** Interrupteur : rail arrondi et bouton rond qui glisse ; vert d'accent quand il est allumé. */
    static final class Toggle extends View {
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float pos;          // 0 = éteint, 1 = allumé
        private boolean on, focused;
        private android.animation.ValueAnimator anim;

        Toggle(android.content.Context c) { super(c); }

        void setFocused(boolean f) { focused = f; invalidate(); }

        void setOn(boolean value, boolean animate) {
            if (on == value && (animate || pos == (value ? 1f : 0f))) return;
            on = value;
            if (anim != null) anim.cancel();
            if (!animate) { pos = on ? 1f : 0f; invalidate(); return; }
            anim = android.animation.ValueAnimator.ofFloat(pos, on ? 1f : 0f);
            anim.setDuration(Motion.FAST);
            anim.setInterpolator(Motion.EASE);
            anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator va) { pos = (Float) va.getAnimatedValue(); invalidate(); }
            });
            anim.start();
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            int offTrack = focused ? 0x26000000 : 0x38FFFFFF;
            p.setColor(blend(offTrack, Palette.ACCENT, pos));
            c.drawRoundRect(0, 0, w, h, r, r, p);
            float kr = r - h * 0.14f, kx = r + pos * (w - 2 * r);
            p.setColor(focused && pos < 0.5f ? Palette.ON_SELECTED : 0xFFFFFFFF);
            c.drawCircle(kx, r, kr, p);
        }

        private static int blend(int a, int b, float t) {
            int aa = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t), rr = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t),
                    gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
            return (aa << 24) | (rr << 16) | (gg << 8) | bb;
        }
    }

    /**
     * Ligne de menu avec pictogramme au trait. danger = action qui retire quelque chose
     * (texte rouge doux, fond rouge à la sélection).
     */
    LinearLayout iconRow(int glyphType, String label, MainActivity.Value value, View.OnClickListener click, final boolean danger) {
        final LinearLayout r = listRow(label, value, click);
        final Glyph g = new Glyph(glyphType, danger ? Palette.DANGER : Palette.TEXT);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(g);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(a.u(34), a.u(34));
        il.rightMargin = a.u(22);
        r.addView(iv, 0, il);
        final TextView l = (TextView) r.getChildAt(1);
        if (danger) l.setTextColor(Palette.DANGER);
        final View.OnFocusChangeListener base = r.getOnFocusChangeListener();
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (base != null) base.onFocusChange(v, f);
                if (danger) {
                    ((GradientDrawable) r.getBackground()).setColor(f ? Palette.DANGER_SELECTED : 0x00FFFFFF);
                    l.setTextColor(f ? 0xFFFFFFFF : Palette.DANGER);
                    g.setColor(f ? 0xFFFFFFFF : Palette.DANGER);
                } else g.setColor(f ? Palette.ON_SELECTED : Palette.TEXT);
            }
        });
        return r;
    }

    /**
     * En-tête des petits menus : visuel arrondi (bannière ou icône), titre, ligne discrète dessous.
     * Le tout centré, comme une fiche.
     */
    LinearLayout menuHeader(android.graphics.drawable.Drawable art, boolean wide, String title, String sub) {
        LinearLayout h = new LinearLayout(a);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setGravity(Gravity.CENTER_HORIZONTAL);
        h.setPadding(0, a.u(8), 0, a.u(28));
        if (art != null) {
            ImageView iv = new ImageView(a);
            iv.setImageDrawable(art);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            final int rad = a.u(wide ? 16 : 22);
            iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad); }
            });
            iv.setClipToOutline(true);
            iv.setElevation(a.u(10));
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(wide ? a.u(240) : a.u(112), wide ? a.u(135) : a.u(112));
            al.bottomMargin = a.u(22);
            h.addView(iv, al);
        }
        TextView t = a.text(36, "sans-serif-light", Palette.TEXT);
        t.setText(title);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        h.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (sub != null && !sub.isEmpty()) {
            TextView st = a.text(18, "sans-serif-medium", Palette.TEXT_3);
            st.setText(sub.toUpperCase(java.util.Locale.FRANCE));
            st.setLetterSpacing(0.14f);
            st.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.topMargin = a.u(8);
            h.addView(st, sl);
        }
        return h;
    }

    /** Fine séparation entre groupes d'actions d'un menu. */
    View menuDivider() {
        View d = new View(a);
        d.setBackgroundColor(Palette.LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, a.u(1.5f)));
        lp.setMargins(a.u(32), a.u(10), a.u(32), a.u(16));
        d.setLayoutParams(lp);
        return d;
    }

    TextView section(String s) {
        TextView t = a.text(20, "sans-serif-medium", Palette.TEXT_2);
        t.setText(s.toUpperCase(java.util.Locale.FRANCE));
        t.setLetterSpacing(0.1f);
        t.setPadding(a.u(32), a.u(40), 0, a.u(14));
        return t;
    }

    LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.u(88 * a.textScale));
        lp.bottomMargin = a.u(6);
        return lp;
    }
}

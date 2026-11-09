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
        card.setBackground(new Glass.Surface(card, radius, a.highContrast ? 0xD90C0D12 : 0x2E14161E));
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
        final TextView l = a.text(28, "sans-serif", 0xEBFFFFFF);
        l.setText(label);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView v = a.text(24, "sans-serif", 0x8CFFFFFF);
        if (value != null) {
            v.setText(value.get());
            a.rowRefreshers.add(new Runnable() { @Override public void run() { v.setText(value.get()); } });
        }
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.leftMargin = a.u(20);
        r.addView(v, vl);
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean f) {
                if (f) Sounds.get(a).tick();
                bg.setColor(f ? 0xFFF5F5F7 : 0x00FFFFFF);
                l.setTextColor(f ? 0xFF111216 : 0xEBFFFFFF);
                v.setTextColor(f ? 0x99111216 : 0x8CFFFFFF);
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

    /**
     * Ligne de menu avec pictogramme au trait. danger = action qui retire quelque chose
     * (texte rouge doux, fond rouge à la sélection).
     */
    LinearLayout iconRow(int glyphType, String label, MainActivity.Value value, View.OnClickListener click, final boolean danger) {
        final LinearLayout r = listRow(label, value, click);
        final Glyph g = new Glyph(glyphType, danger ? 0xFFFF8A80 : 0xD9FFFFFF);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(g);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(a.u(34), a.u(34));
        il.rightMargin = a.u(22);
        r.addView(iv, 0, il);
        final TextView l = (TextView) r.getChildAt(1);
        if (danger) l.setTextColor(0xFFFF8A80);
        final View.OnFocusChangeListener base = r.getOnFocusChangeListener();
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (base != null) base.onFocusChange(v, f);
                if (danger) {
                    ((GradientDrawable) r.getBackground()).setColor(f ? 0xFFFF6B5E : 0x00FFFFFF);
                    l.setTextColor(f ? 0xFFFFFFFF : 0xFFFF8A80);
                    g.setColor(f ? 0xFFFFFFFF : 0xFFFF8A80);
                } else g.setColor(f ? 0xFF111216 : 0xD9FFFFFF);
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
        TextView t = a.text(36, "sans-serif-light", 0xF5FFFFFF);
        t.setText(title);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        h.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (sub != null && !sub.isEmpty()) {
            TextView st = a.text(18, "sans-serif-medium", 0x80FFFFFF);
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
        d.setBackgroundColor(0x1AFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, a.u(1.5f)));
        lp.setMargins(a.u(32), a.u(10), a.u(32), a.u(16));
        d.setLayoutParams(lp);
        return d;
    }

    TextView section(String s) {
        TextView t = a.text(18, "sans-serif-medium", 0x80FFFFFF);
        t.setText(s.toUpperCase());
        t.setLetterSpacing(0.12f);
        t.setPadding(a.u(32), a.u(40), 0, a.u(14));
        return t;
    }

    LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.u(88 * a.textScale));
        lp.bottomMargin = a.u(6);
        return lp;
    }
}

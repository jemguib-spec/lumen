// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tuile d'un appareil Home Assistant : pictogramme, nom, état. Allumé, le verre prend la couleur de
 * la lumière (ou un blanc pour le reste) et une jauge monte jusqu'au niveau (intensité, position,
 * vitesse). OK allume ou éteint ; appui long sur OK : réglage, ◀ ▶ changent le niveau, OK termine.
 */
final class HaTile extends FrameLayout {

    interface Listener {
        void toggle(HomeAssistant.Entity e);
        void level(HomeAssistant.Entity e, int pct);
    }

    /** Blanc chaud d'une lumière qui n'annonce pas sa couleur. */
    private static final int WARM = 0xFFFFD9A0;
    private static final int STEP = 10;
    private static final long SEND_AFTER_MS = 350, HOLD_MS = 2500;

    private final MainActivity a;
    private final FrameLayout card;
    private final Glass.Surface glass;
    private final View fill;
    private final Glyph glyph;
    private final android.graphics.drawable.GradientDrawable badge;
    private final TextView name, state;
    private final Listener listener;
    private final float radius;
    private HomeAssistant.Entity e;
    private boolean adjusting, waiting;
    private int wanted = -1;      // niveau choisi sur la TV, pas encore confirmé par la maison
    private long holdUntil;       // d'ici là, les relectures ne remplacent pas ce qu'on vient de régler

    HaTile(final MainActivity a, HomeAssistant.Entity entity, int w, int h, Listener l) {
        super(a);
        this.a = a;
        this.listener = l;
        this.e = entity;
        radius = a.u(Radius.HA_TILE); // pastille ronde du coin + sa marge
        setFocusable(true);
        setClickable(true);
        setSoundEffectsEnabled(false);
        setClipChildren(false);
        setClipToPadding(false);
        card = new FrameLayout(a);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        card.setClipToOutline(true);
        glass = new Glass.Surface(card, radius, 0x1FFFFFFF);
        card.setBackground(glass);
        fill = new View(a); // jauge : monte du bas de la tuile
        fill.setPivotY(h);
        fill.setScaleY(0f);
        card.addView(fill, new LayoutParams(LayoutParams.MATCH_PARENT, h));
        glyph = new Glyph(glyphFor(entity.domain), Palette.TEXT_2);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(glyph);
        // pastille ronde derrière le pictogramme : discrète éteint, pleine de la couleur de la lumière allumé
        badge = new android.graphics.drawable.GradientDrawable();
        badge.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        iv.setBackground(badge);
        iv.setPadding(a.u(12), a.u(12), a.u(12), a.u(12));
        LayoutParams gl = new LayoutParams(a.u(Radius.HA_ICON), a.u(Radius.HA_ICON), Gravity.TOP | Gravity.START);
        gl.leftMargin = gl.topMargin = a.u(Radius.HA_INSET);
        card.addView(iv, gl);
        LinearLayout txt = new LinearLayout(a);
        txt.setOrientation(LinearLayout.VERTICAL);
        name = a.text(24, "sans-serif-medium", Palette.TEXT);
        name.setShadowLayer(a.u(6), 0, 0, Palette.SHADOW);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(name);
        state = a.text(20, "sans-serif", Palette.TEXT_2);
        state.setShadowLayer(a.u(6), 0, 0, Palette.SHADOW);
        state.setSingleLine(true);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        sl.topMargin = a.u(4);
        txt.addView(state, sl);
        LayoutParams tl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        tl.leftMargin = tl.rightMargin = a.u(22);
        tl.bottomMargin = a.u(18);
        card.addView(txt, tl);
        addView(card, new LayoutParams(w, h));

        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick(); else if (adjusting) endAdjust();
                animate().scaleX(f ? 1.07f : 1f).scaleY(f ? 1.07f : 1f).setStartDelay(0).setDuration(Motion.FAST).setInterpolator(Motion.EASE).start();
                card.animate().translationZ(f ? a.u(24) : 0).setDuration(Motion.FAST).start();
                card.setForeground(f ? CheckCell.ring(a, radius) : null);
            }
        });
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (!e.available() || "unavailable".equals(e.state)) { a.showNotice(e.name + L.t(" ne répond pas")); return; }
                Sounds.get(a).tock();
                waiting = true;
                render();
                listener.toggle(e);
            }
        });
        setOnLongClickListener(new OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                if (!HomeAssistant.adjustable(e)) { a.showNotice(L.t("Cet appareil s'allume ou s'éteint, sans réglage")); return true; }
                Sounds.get(a).tock();
                adjusting = true;
                wanted = shownLevel();
                render();
                return true;
            }
        });
        setOnKeyListener(new OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent ev) {
                if (!adjusting) return false;
                // l'appui long qui a ouvert le réglage se termine : son relâchement ne doit rien faire
                boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
                if (ok && ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() > 0) return true;
                if (ok && ev.getAction() == KeyEvent.ACTION_UP && (ev.getFlags() & KeyEvent.FLAG_CANCELED_LONG_PRESS) != 0) return true;
                if (ok && ev.getAction() == KeyEvent.ACTION_UP && !released) { released = true; return true; }
                if (ev.getAction() != KeyEvent.ACTION_DOWN) return true;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int next = Math.max(0, Math.min(100, (wanted / STEP) * STEP + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? STEP : wanted % STEP == 0 ? -STEP : 0)));
                    if (next != wanted) {
                        wanted = next;
                        Sounds.get(a).tick();
                        holdUntil = System.currentTimeMillis() + HOLD_MS;
                        render();
                        a.h.removeCallbacks(send);
                        a.h.postDelayed(send, SEND_AFTER_MS);
                    }
                    return true;
                }
                endAdjust();
                // OK et Retour terminent seulement le réglage ; ▲ ▼ le terminent et déplacent la sélection
                return ok || code == KeyEvent.KEYCODE_BACK;
            }
        });
        render();
    }

    /** Le relâchement de l'appui long a-t-il déjà été vu ? (sinon il fermerait le réglage aussitôt ouvert) */
    private boolean released;

    private final Runnable send = new Runnable() {
        @Override public void run() { if (wanted >= 0) listener.level(e, wanted); }
    };

    private void endAdjust() {
        adjusting = false;
        released = false;
        render();
    }

    String entityId() { return e.id; }

    /** Nouvel état lu dans la maison. */
    void update(HomeAssistant.Entity fresh) {
        if (fresh == null) return;
        e = fresh;
        waiting = false;
        if (!adjusting && System.currentTimeMillis() >= holdUntil) wanted = -1;
        render();
    }

    private int shownLevel() {
        if (wanted >= 0) return wanted;
        if (e.level >= 0) return e.on() || "cover".equals(e.domain) ? e.level : 0;
        return e.on() ? 100 : 0;
    }

    private void render() {
        boolean gone = "unavailable".equals(e.state);
        boolean on = wanted >= 0 ? wanted > 0 : e.on();
        int lit = "light".equals(e.domain) ? (e.color != 0 ? e.color : WARM) : 0xFFFFFFFF;
        glass.setVeil(on ? (lit & 0x00FFFFFF) | ("light".equals(e.domain) ? 0x66000000 : 0x40000000) : 0x1FFFFFFF);
        boolean gauge = on && (wanted >= 0 || e.level >= 0) && HomeAssistant.adjustable(e);
        fill.setBackgroundColor((lit & 0x00FFFFFF) | 0x4D000000);
        fill.animate().scaleY(gauge ? shownLevel() / 100f : 0f).setStartDelay(0).setDuration(Motion.FAST).setInterpolator(Motion.EASE).start();
        badge.setColor(on ? lit : 0x1FFFFFFF);
        glyph.setColor(on ? Palette.ON_SELECTED : Palette.TEXT_2);
        name.setText(e.name);
        String s = waiting ? "…" : stateText(on);
        state.setText(adjusting ? "‹   " + shownLevel() + " %   ›" : s);
        state.setTextColor(adjusting || on ? Palette.TEXT : Palette.TEXT_2);
        setAlpha(gone ? 0.45f : 1f);
        setContentDescription(e.name + ", " + s);
    }

    private String stateText(boolean on) {
        if (wanted >= 0 && HomeAssistant.adjustable(e)) return wanted == 0 && !"cover".equals(e.domain) ? off() : wanted + " %";
        switch (e.state) {
            case "on": return e.level > 0 && HomeAssistant.adjustable(e) ? e.level + " %" : "light".equals(e.domain) ? L.t("Allumée") : L.t("Allumé");
            case "off": return off();
            case "open": return e.level >= 0 && e.level < 100 ? e.level + " %" : L.t("Ouvert");
            case "opening": return L.t("Ouverture…");
            case "closed": return L.t("Fermé");
            case "closing": return L.t("Fermeture…");
            case "playing": return L.t("Lecture");
            case "paused": return L.t("Pause");
            case "idle": case "standby": return L.t("En veille");
            case "unavailable": return L.t("Indisponible");
            case "unknown": return "—";
            default: return e.state;
        }
    }

    private String off() { return "light".equals(e.domain) ? L.t("Éteinte") : L.t("Éteint"); }

    static int glyphFor(String domain) {
        switch (domain) {
            case "light": return Glyph.BULB;
            case "switch": return Glyph.PLUG;
            case "cover": return Glyph.BLIND;
            case "fan": return Glyph.FAN;
            case "media_player": return Glyph.PLAY;
            case "input_boolean": return Glyph.SWITCH;
            default: return Glyph.SPARK;
        }
    }
}

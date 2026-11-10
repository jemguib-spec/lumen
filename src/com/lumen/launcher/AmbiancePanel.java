// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Mode « Ambiances » plein écran : vraies vidéos filmées (Wikimedia Commons, licences libres),
 * avec leur propre son, et minuterie de sommeil.
 * ◀ ▶ change d'ambiance, OK ouvre le menu, Retour quitte.
 * Une ambiance n'apparaît que si au moins une vidéo est téléchargée : pas de vidéo, pas d'ambiance.
 * Les vidéos d'une même ambiance s'enchaînent en fondu ; une seule vidéo tourne en boucle.
 */
final class AmbiancePanel {
    private static final int[] TIMER_MIN = {0, 15, 30, 60, 90};
    private static final int FADE_MS = 1500;

    private final MainActivity a;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final FrameLayout layer, stage;
    private final TextView status, credit, wait;
    private final View black;
    private LinearLayout menu;
    private int kind, timerIdx, pos;
    private long timerEnd;
    private boolean asleep, closed;
    private List<WallItem> items = new ArrayList<>();
    private WallItem current;
    private TextureView view;
    private MediaPlayer player;
    private int generation;
    private float volume = 1f;

    private AmbiancePanel(MainActivity a) {
        this.a = a;
        layer = new FrameLayout(a);
        layer.setBackgroundColor(Color.BLACK);
        stage = new FrameLayout(a);
        TextClock clock = new TextClock(a);
        clock.setFormat24Hour("H:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 40 * a.unit);
        clock.setTypeface(Fonts.get("sans-serif-light", Typeface.NORMAL));
        clock.setTextColor(Palette.TEXT_2);
        clock.setShadowLayer(a.u(10), 0, 0, 0x88000000);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        cl.topMargin = a.u(56);
        cl.rightMargin = a.u(80);
        status = a.text(22, "sans-serif", Palette.TEXT_3);
        status.setShadowLayer(a.u(10), 0, 0, 0x88000000);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        sl.topMargin = a.u(110);
        sl.rightMargin = a.u(80);
        // auteur et licence de la vidéo (obligatoire pour les licences CC BY), très discret
        credit = a.text(18, "sans-serif", 0x59FFFFFF);
        credit.setShadowLayer(a.u(8), 0, 0, 0x88000000);
        credit.setSingleLine(true);
        credit.setEllipsize(android.text.TextUtils.TruncateAt.END);
        credit.setMaxWidth(a.u(900));
        FrameLayout.LayoutParams crl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        crl.leftMargin = a.u(80);
        crl.bottomMargin = a.u(48);
        wait = a.text(28, "sans-serif-light", 0xB3FFFFFF);
        wait.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        black = new View(a);
        black.setBackgroundColor(Color.BLACK);
        black.setAlpha(0f);
        // en attendant une vidéo : le fond d'écran flouté et assombri plutôt qu'un écran noir
        if (Glass.backdrop() != null) {
            android.widget.ImageView back = new android.widget.ImageView(a);
            back.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            back.setImageBitmap(Glass.backdrop());
            back.setAlpha(0.55f);
            layer.addView(back, MainActivity.match());
        }
        layer.addView(stage, MainActivity.match());
        layer.addView(wait, wl);
        layer.addView(clock, cl);
        layer.addView(status, sl);
        layer.addView(credit, crl);
        layer.addView(black, MainActivity.match());
        kind = a.prefs.ambianceKind();
    }

    static AmbiancePanel open(MainActivity a) {
        AmbiancePanel p = new AmbiancePanel(a);
        a.root.addView(p.layer, MainActivity.match());
        p.layer.setAlpha(0f);
        p.layer.animate().alpha(1f).setDuration(Motion.SLOW).start();
        a.prefs.setCosyWanted(true);
        a.online.refreshCosy();
        p.showKind(p.kind, 1);
        p.h.post(p.poll);
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        return p;
    }

    void close() {
        closed = true;
        h.removeCallbacksAndMessages(null);
        generation++;
        stopPlayer(player, view);
        player = null;
        view = null;
        a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final FrameLayout l = layer;
        l.animate().alpha(0f).setDuration(Motion.MEDIUM).withEndAction(new Runnable() {
            @Override public void run() { a.root.removeView(l); }
        }).start();
    }

    /** Appelé quand une nouvelle vidéo d'ambiance vient d'être téléchargée. */
    void onVideosUpdated() {
        if (closed || asleep) return;
        if (current == null) { showKind(kind, 1); return; }
        items = a.online.cosyItems(kind); // la suivante sera prise au prochain enchaînement
        if (player != null && items.size() > 1) try { player.setLooping(false); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ choix de l'ambiance

    private boolean has(int k) { return !a.online.cosyItems(k).isEmpty(); }

    /** Affiche l'ambiance k, ou la prochaine disponible dans le sens dir. */
    private void showKind(int k, int dir) {
        int n = Online.COSY_NAMES.length;
        int found = -1;
        for (int i = 0; i < n; i++) {
            int c = ((k + i * dir) % n + n) % n;
            if (has(c)) { found = c; break; }
        }
        if (found < 0) { current = null; updateStatus(); return; }
        if (found == kind && current != null) return;
        kind = found;
        a.prefs.setAmbianceKind(kind);
        items = a.online.cosyItems(kind);
        pos = 0;
        play(items.get(0));
    }

    private int availableCount() {
        int c = 0;
        for (int i = 0; i < Online.COSY_NAMES.length; i++) if (has(i)) c++;
        return c;
    }

    // ------------------------------------------------------------------ lecture vidéo

    private void play(final WallItem item) {
        current = item;
        final int gen = ++generation;
        final TextureView tv = new TextureView(a);
        final MediaPlayer mp = new MediaPlayer();
        tv.setAlpha(0f);
        stage.addView(tv, MainActivity.match());
        updateStatus();
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int hh) {
                try {
                    mp.setSurface(new Surface(st));
                    mp.setDataSource(item.path);
                    applyVolume(mp);
                    mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override public void onPrepared(MediaPlayer p) {
                            if (gen != generation) { stopPlayer(mp, tv); return; }
                            crop(tv, p.getVideoWidth(), p.getVideoHeight());
                            p.setLooping(items.size() < 2);
                            p.start();
                        }
                    });
                    mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                        @Override public boolean onInfo(MediaPlayer p, int what, int extra) {
                            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && gen == generation) swapIn(tv, mp);
                            return false;
                        }
                    });
                    mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override public void onCompletion(MediaPlayer p) { if (gen == generation) next(); }
                    });
                    mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override public boolean onError(MediaPlayer p, int x, int y) { fail(gen, item, mp, tv); return true; }
                    });
                    mp.prepareAsync();
                } catch (Exception e) { fail(gen, item, mp, tv); }
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int hh) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });
    }

    /** Fondu enchaîné : la nouvelle vidéo apparaît par-dessus, l'ancienne s'arrête ensuite. */
    private void swapIn(final TextureView tv, MediaPlayer mp) {
        final TextureView oldView = view;
        final MediaPlayer oldPlayer = player;
        view = tv;
        player = mp;
        tv.animate().alpha(1f).setDuration(FADE_MS).withEndAction(new Runnable() {
            @Override public void run() { stopPlayer(oldPlayer, oldView); }
        }).start();
    }

    /** Vidéo illisible sur cette TV : on l'écarte et on passe à la suivante. */
    private void fail(int gen, WallItem item, MediaPlayer mp, TextureView tv) {
        stopPlayer(mp, tv);
        if (gen != generation) return;
        a.online.dismissCosy(item.id);
        items = a.online.cosyItems(kind);
        if (!items.isEmpty()) { pos = pos % items.size(); play(items.get(pos)); }
        else { current = null; showKind(kind + 1, 1); }
    }

    private void next() {
        items = a.online.cosyItems(kind);
        if (items.isEmpty()) { current = null; showKind(kind + 1, 1); return; }
        pos = (pos + 1) % items.size();
        play(items.get(pos));
    }

    private void stopPlayer(MediaPlayer mp, View tv) {
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) { }
            try { mp.release(); } catch (Exception ignored) { }
        }
        if (tv != null) stage.removeView(tv);
    }

    private void applyVolume(MediaPlayer mp) {
        float v = a.prefs.ambianceSound() ? volume : 0f;
        try { mp.setVolume(v, v); } catch (Exception ignored) { }
    }

    private static void crop(TextureView tv, int vw, int vh) {
        int w = tv.getWidth(), hh = tv.getHeight();
        if (vw <= 0 || vh <= 0 || w == 0 || hh == 0) return;
        float s = Math.max(w / (float) vw, hh / (float) vh);
        Matrix m = new Matrix();
        m.setScale(vw * s / w, vh * s / hh, w / 2f, hh / 2f);
        tv.setTransform(m);
    }

    // ------------------------------------------------------------------ textes

    private void updateStatus() {
        if (current == null) {
            String st = a.online.status(Online.COSY);
            if (a.prefs.videoOkCount() == 0 && a.online.cosyMine().isEmpty())
                wait.setText("Aucune vidéo d'ambiance choisie (feu, pluie, océan…).\n"
                        + "OK : les choisir sur les aperçus   ·   Retour : quitter\n\n"
                        + "Le mode veille avec tes images : Réglages › Mode veille › Lancer le mode veille.");
            else wait.setText(a.online.cosyBusy()
                    ? "Téléchargement des vidéos choisies…" + (st.isEmpty() ? "" : "\n" + st)
                    : "Aucune vidéo prête pour l'instant." + (st.isEmpty() ? "" : "\n" + st)
                    + "\nOK : choisir d'autres vidéos ou réessayer");
            wait.setVisibility(View.VISIBLE);
            status.setText("");
            credit.setText("");
            return;
        }
        wait.setVisibility(View.GONE);
        StringBuilder b = new StringBuilder(Online.COSY_NAMES[kind]);
        if (timerEnd > 0) {
            long left = Math.max(0, (timerEnd - System.currentTimeMillis()) / 60000 + 1);
            b.append("  ·  arrêt dans ").append(left).append(" min");
        }
        status.setText(b.toString());
        credit.setText(current.credit);
    }

    /** Rafraîchit le texte d'attente tant qu'aucune vidéo n'est prête. */
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (current == null) updateStatus();
            h.postDelayed(this, current == null ? 1000 : 30_000);
        }
    };

    // ------------------------------------------------------------------ minuterie

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (timerEnd <= 0) return;
            if (System.currentTimeMillis() >= timerEnd) { fallAsleep(); return; }
            updateStatus();
            h.postDelayed(this, 30_000);
        }
    };

    // ------------------------------------------------------------------ commande depuis le téléphone

    /** Ambiance affichée (n° dans Online.COSY_NAMES). */
    int kind() { return kind; }

    /** Minuterie restante en minutes (0 = désactivée). */
    int timerLeftMinutes() { return timerEnd <= 0 ? 0 : (int) Math.max(1, (timerEnd - System.currentTimeMillis() + 59_999) / 60_000); }

    /** Durée choisie pour la minuterie en cours (0 = aucune). */
    int timerChoice() { return timerEnd <= 0 ? 0 : TIMER_MIN[timerIdx]; }

    void selectKind(int k) { if (k >= 0 && k < Online.COSY_NAMES.length && k != kind) showKind(k, 1); }

    /** Minuterie réglée sur l'une des durées proposées (0, 15, 30, 60, 90 min). false sinon. */
    boolean setTimerMinutes(int minutes) {
        for (int i = 0; i < TIMER_MIN.length; i++) if (TIMER_MIN[i] == minutes) { setTimer(i); return true; }
        return false;
    }

    static int[] timerChoices() { return TIMER_MIN.clone(); }

    private void setTimer(int idx) {
        timerIdx = idx % TIMER_MIN.length;
        h.removeCallbacks(tick);
        timerEnd = TIMER_MIN[timerIdx] == 0 ? 0 : System.currentTimeMillis() + TIMER_MIN[timerIdx] * 60_000L;
        if (timerEnd > 0) h.postDelayed(tick, 30_000);
        updateStatus();
    }

    /** Fin de minuterie : fondu du son et de l'image, puis on laisse la TV se mettre en veille. */
    private void fallAsleep() {
        asleep = true;
        timerEnd = 0;
        closeMenu();
        final long start = System.currentTimeMillis();
        h.post(new Runnable() {
            @Override public void run() {
                float t = Math.min(1f, (System.currentTimeMillis() - start) / 20_000f);
                volume = 1f - t;
                if (player != null) applyVolume(player);
                if (t < 1f) h.postDelayed(this, 200);
            }
        });
        black.animate().alpha(1f).setDuration(20_000).withEndAction(new Runnable() {
            @Override public void run() {
                generation++;
                stopPlayer(player, view);
                player = null;
                view = null;
                a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }).start();
    }

    // ------------------------------------------------------------------ télécommande

    /** Renvoie true si la touche a été traitée par le mode Ambiances. */
    boolean onKey(KeyEvent e) {
        boolean up = e.getAction() == KeyEvent.ACTION_UP;
        int code = e.getKeyCode();
        if (asleep) { if (up) a.closeAmbiance(); return true; }
        if (menu != null) {
            if (code == KeyEvent.KEYCODE_BACK) { if (up) closeMenu(); return true; }
            return false; // navigation normale dans le menu
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: if (!up && current != null) showKind(kind - 1, -1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (!up && current != null) showKind(kind + 1, 1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MENU: if (up) openMenu(); return true;
            case KeyEvent.KEYCODE_BACK: if (up) a.closeAmbiance(); return true;
            default: return true; // les autres touches ne sortent pas de l'ambiance
        }
    }

    private void openMenu() {
        menu = a.glassCard(44);
        menu.setPadding(a.u(24), a.u(28), a.u(24), a.u(24));
        LinearLayout first = null;
        if (current != null) {
            if (availableCount() > 1) {
                first = a.listRow("Ambiance", new MainActivity.Value() {
                    @Override public String get() { return Online.COSY_NAMES[kind]; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { showKind(kind + 1, 1); }
                });
                menu.addView(first, a.rowLp());
            }
            LinearLayout other = a.listRow("Ne plus montrer cette vidéo", null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (current == null) return;
                    a.prefs.hideWall(current.id);
                    a.online.refreshCosy(); // une autre vidéo la remplacera
                    closeMenu();
                    next();
                }
            });
            menu.addView(other, a.rowLp());
            if (first == null) first = other;
            menu.addView(a.listRow("Son de la vidéo", new MainActivity.Value() {
                @Override public String get() { return a.prefs.ambianceSound() ? "Activé" : "Coupé"; }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    a.prefs.setAmbianceSound(!a.prefs.ambianceSound());
                    if (player != null) applyVolume(player);
                }
            }), a.rowLp());
            menu.addView(a.listRow("Choisir les vidéos", null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.pickCosyVideos(); }
            }), a.rowLp());
            menu.addView(a.listRow("Minuterie de sommeil", new MainActivity.Value() {
                @Override public String get() { return TIMER_MIN[timerIdx] == 0 ? "Désactivée" : TIMER_MIN[timerIdx] + " min"; }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { setTimer(timerIdx + 1); }
            }), a.rowLp());
        }
        if (current == null) {
            first = a.listRow("Choisir les vidéos", null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.pickCosyVideos(); }
            });
            menu.addView(first, a.rowLp());
            if (a.prefs.videoOkCount() > 0) {
                LinearLayout retry = a.listRow("Réessayer le téléchargement", new MainActivity.Value() {
                    @Override public String get() { return a.online.cosyBusy() ? "En cours…" : ""; }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { a.online.refreshCosy(); a.showNotice("Téléchargement relancé"); }
                });
                menu.addView(retry, 0, a.rowLp());
                first = retry;
            }
        }
        LinearLayout quit = a.listRow("Quitter", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeAmbiance(); }
        });
        menu.addView(quit, a.rowLp());
        if (first == null) first = quit;
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(a.u(760), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        ml.bottomMargin = a.u(80);
        layer.addView(menu, layer.indexOfChild(black), ml);
        menu.setAlpha(0f);
        menu.setTranslationY(a.u(40));
        menu.animate().alpha(1f).translationY(0).setDuration(250).start();
        first.requestFocus();
    }

    private void closeMenu() {
        if (menu == null) return;
        final View m = menu;
        menu = null;
        a.rowRefreshers.clear();
        m.animate().alpha(0f).setDuration(Motion.FAST).withEndAction(new Runnable() {
            @Override public void run() { layer.removeView(m); }
        }).start();
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Moteur de fond d'écran : enchaîne images (avec lent zoom), scènes animées et vidéos,
 * en fondu. Prévient l'interface du fond affiché (légende + image floutée pour le verre).
 */
final class Wall {

    interface Listener {
        void onWallShown(WallItem item, Bitmap backdrop);
    }

    private static final long FADE_MS = 3200, VEILLE_FADE_MS = 4800;
    private long fade() { return veille ? VEILLE_FADE_MS : FADE_MS; }
    private final Context ctx;
    private final Prefs prefs;
    private final Online online;
    private final FrameLayout host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Listener listener;

    private List<WallItem> items = new ArrayList<>();
    private int index = -1;
    private WallItem current;
    private View currentView;
    private Bitmap currentBitmap;
    private AnimatedScene currentScene;
    private MediaPlayer player;
    private boolean running;
    private int generation;
    private String lastPeriod = Library.period();
    private boolean usingFallback;
    /** Fond du mode veille : images seulement, source réglée à part. */
    private boolean veille;

    Wall(Context ctx, Prefs prefs, Online online, FrameLayout host) {
        this.ctx = ctx;
        this.prefs = prefs;
        this.online = online;
        this.host = host;
    }

    void setListener(Listener l) { listener = l; }
    void setVeilleMode(boolean on) { veille = on; }
    private int source() { return veille ? Library.veilleSource(prefs) : prefs.source(); }
    WallItem current() { return current; }
    int position() { return index; }
    int count() { return items.size(); }
    boolean usingFallback() { return usingFallback; }

    // ------------------------------------------------------------------ cycle

    void start() {
        if (running) return;
        running = true;
        LivingScene.paused = false;
        if (currentView instanceof FrameLayout) {
            FrameLayout f = (FrameLayout) currentView;
            for (int i = 0; i < f.getChildCount(); i++) if (f.getChildAt(i) instanceof LivingScene) ((LivingScene) f.getChildAt(i)).resume();
        }
        if (items.isEmpty()) rebuild();
        if (current == null) next();
        else { resumeCurrent(); schedule(); }
        h.postDelayed(clock, 60_000);
    }

    void stop() {
        running = false;
        LivingScene.paused = true; // plus aucune image calculée quand le fond n'est pas visible
        generation++;
        h.removeCallbacksAndMessages(null);
        if (currentScene != null) currentScene.setRunning(false);
        if (player != null) try { player.pause(); } catch (Exception ignored) { }
    }

    /** Relance l'animation du paysage Lumen affiché (après une pause). */
    void resumeLiving() {
        if (!running || !(currentView instanceof FrameLayout)) return;
        FrameLayout f = (FrameLayout) currentView;
        for (int i = 0; i < f.getChildCount(); i++) if (f.getChildAt(i) instanceof LivingScene) ((LivingScene) f.getChildAt(i)).resume();
    }

    /** Démarre sur une liste neuve, en commençant si possible par ce fond (mode veille : pas de saut d'image). */
    void startWith(WallItem first) {
        rebuild();
        if (first != null && first.kind != WallItem.VIDEO_FILE)
            for (int i = 0; i < items.size(); i++)
                if (items.get(i).id.equals(first.id)) { items.add(0, items.remove(i)); break; }
        current = null;
        start();
    }

    /** Arrête et libère tout ce qui est affiché (images, scène, vidéo), sans toucher aux autres fonds. */
    void release() {
        running = false;
        generation++;
        h.removeCallbacksAndMessages(null);
        host.removeAllViews();
        if (currentScene != null) { currentScene.setRunning(false); currentScene.data().recycle(); }
        if (player != null) release(player);
        current = null;
        currentView = null;
        currentScene = null;
        currentBitmap = null;
        player = null;
    }

    private void resumeCurrent() {
        if (currentScene != null) currentScene.setRunning(true);
        if (player != null) try { player.start(); } catch (Exception ignored) { }
    }

    /** Recharge la liste (changement de source, d'heure, de masquage) et affiche un nouveau fond. */
    void restart() {
        rebuild();
        if (running) next();
    }

    /** Recharge la liste sans changer le fond affiché (ex. nouvelles photos téléchargées). */
    void refreshList() {
        boolean wasFallback = usingFallback;
        rebuild();
        if (wasFallback && !usingFallback && running) next();
    }

    private void rebuild() {
        items = veille ? Library.buildVeille(ctx, prefs, online) : Library.build(ctx, prefs, online);
        usingFallback = items.isEmpty();
        if (usingFallback) items = Library.fallback(ctx);
        index = -1;
    }

    private final Runnable clock = new Runnable() {
        @Override public void run() {
            String p = Library.period();
            int src = source();
            if (!p.equals(lastPeriod)) {
                lastPeriod = p;
                if (prefs.followTime() && (src == Prefs.SRC_LUMEN || src == Prefs.SRC_ANIM || usingFallback)) restart();
            }
            if (running) h.postDelayed(this, 60_000);
        }
    };

    private void schedule() {
        h.removeCallbacks(nextRun);
        int s = prefs.intervalSeconds();
        if (s <= 0 || items.size() < 2 || current == null) return;
        long ms = s * 1000L;
        if (current.kind == WallItem.ANIM) ms = Math.max(ms * 4, 180_000);
        if (current.kind == WallItem.VIDEO_FILE) return; // la vidéo passe à la suivante à la fin
        h.postDelayed(nextRun, ms);
    }

    private final Runnable nextRun = new Runnable() { @Override public void run() { next(); } };

    void next() {
        if (items.isEmpty()) return;
        index = (index + 1) % items.size();
        final WallItem item = items.get(index);
        final int gen = ++generation;
        if (item.kind == WallItem.VIDEO_FILE) { showVideo(item, gen); return; }
        new Thread(new Runnable() {
            @Override public void run() {
                final Object loaded = item.kind == WallItem.ANIM
                        ? AnimatedScene.Data.load(ctx, item.path) : decode(item);
                h.post(new Runnable() {
                    @Override public void run() {
                        if (gen != generation || !running) { recycleLoaded(loaded); return; }
                        if (loaded == null) { items.remove(item); if (!items.isEmpty()) next(); return; }
                        if (loaded instanceof Bitmap) showImage(item, (Bitmap) loaded);
                        else showScene(item, (AnimatedScene.Data) loaded);
                        schedule();
                    }
                });
            }
        }, "lumen-wall").start();
    }

    /** Masque le fond affiché et passe au suivant. */
    void hideCurrent() {
        if (current == null) return;
        prefs.hideWall(current.id); // le fichier est gardé : on peut le réafficher depuis la galerie
        items.remove(current);
        if (items.isEmpty()) rebuild();
        index = Math.max(-1, Math.min(index - 1, items.size() - 1));
        next();
    }

    private static void recycleLoaded(Object o) {
        if (o instanceof Bitmap) ((Bitmap) o).recycle();
        else if (o instanceof AnimatedScene.Data) ((AnimatedScene.Data) o).recycle();
    }

    // ------------------------------------------------------------------ images

    private Bitmap decode(WallItem item) {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
            int target = Math.max(1920, Math.max(dm.widthPixels, dm.heightPixels));
            BitmapFactory.Options o = new BitmapFactory.Options();
            if (item.kind == WallItem.IMAGE_FILE) {
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(item.path, o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= target) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                Bitmap b = BitmapFactory.decodeFile(item.path, o);
                b = trimBorders(b);
                if (b != null && b.getWidth() > target * 1.4f) {
                    Bitmap s = Bitmap.createScaledBitmap(b, target, (int) (b.getHeight() * (target / (float) b.getWidth())), true);
                    if (s != b) b.recycle();
                    b = s;
                }
                return b;
            }
            try (InputStream in = ctx.getAssets().open(item.path)) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Rogne les marges unies (blanches, noires…) que contiennent certaines photos,
     * pour que l'image remplisse vraiment l'écran.
     */
    static Bitmap trimBorders(Bitmap b) {
        if (b == null) return null;
        try {
            int w = b.getWidth(), h = b.getHeight();
            int left = 0, right = w - 1, top = 0, bottom = h - 1;
            int maxX = (int) (w * 0.4f), maxY = (int) (h * 0.4f);
            int ref = b.getPixel(0, 0);
            while (left < maxX && uniformCol(b, left, ref)) left++;
            ref = b.getPixel(w - 1, 0);
            while (w - 1 - right < maxX && uniformCol(b, right, ref)) right--;
            ref = b.getPixel(0, 0);
            while (top < maxY && uniformRow(b, top, ref)) top++;
            ref = b.getPixel(0, h - 1);
            while (h - 1 - bottom < maxY && uniformRow(b, bottom, ref)) bottom--;
            // marge de sécurité de 2 px pour ne pas garder un liseré
            if (left > 0) left += 2;
            if (top > 0) top += 2;
            if (right < w - 1) right -= 2;
            if (bottom < h - 1) bottom -= 2;
            int nw = right - left + 1, nh = bottom - top + 1;
            if (nw >= w - 4 && nh >= h - 4) return b;
            if (nw < w * 0.3f || nh < h * 0.3f) return b;
            Bitmap out = Bitmap.createBitmap(b, left, top, nw, nh);
            if (out != b) b.recycle();
            return out;
        } catch (Throwable t) {
            return b;
        }
    }

    private static boolean close(int a, int c) {
        return Math.abs(((a >> 16) & 255) - ((c >> 16) & 255)) < 22
                && Math.abs(((a >> 8) & 255) - ((c >> 8) & 255)) < 22
                && Math.abs((a & 255) - (c & 255)) < 22;
    }

    private static boolean uniformCol(Bitmap b, int x, int ref) {
        int h = b.getHeight(), step = Math.max(1, h / 120);
        for (int y = 0; y < h; y += step) if (!close(b.getPixel(x, y), ref)) return false;
        return true;
    }

    private static boolean uniformRow(Bitmap b, int y, int ref) {
        int w = b.getWidth(), step = Math.max(1, w / 160);
        for (int x = 0; x < w; x += step) if (!close(b.getPixel(x, y), ref)) return false;
        return true;
    }

    private void showImage(WallItem item, Bitmap bmp) {
        ImageView img = new ImageView(ctx);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setImageBitmap(bmp);
        View iv = img;
        // paysages Lumen : une couche animée (nuages, eau, étoiles…) bouge avec l'image
        if (item.kind == WallItem.IMAGE_ASSET && LivingScene.handles(item.path) && prefs.livingScenes()) {
            FrameLayout f = new FrameLayout(ctx);
            f.addView(img, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            f.addView(new LivingScene(ctx, item.path), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            iv = f;
        }
        boolean still = prefs.intervalSeconds() <= 0;
        long dur = (still ? 60 : prefs.intervalSeconds()) * 1000L + fade() * 2;
        float drift = still ? 0 : (Math.random() < 0.5 ? 1 : -1) * Tile.dp(ctx, 14);
        AnimatorSet kb = new AnimatorSet();
        kb.playTogether(
                ObjectAnimator.ofFloat(iv, "scaleX", 1f, still ? 1f : 1.07f),
                ObjectAnimator.ofFloat(iv, "scaleY", 1f, still ? 1f : 1.07f),
                ObjectAnimator.ofFloat(iv, "translationX", -drift / 2, drift / 2));
        kb.setDuration(dur);
        kb.setInterpolator(new LinearInterpolator());
        iv.setTag(kb);
        kb.start();
        swapIn(item, iv, bmp, null, null);
    }

    // ------------------------------------------------------------------ scène animée

    private void showScene(WallItem item, AnimatedScene.Data data) {
        AnimatedScene s = new AnimatedScene(ctx, data);
        swapIn(item, s, data.sky, s, null);
    }

    // ------------------------------------------------------------------ vidéo

    private void showVideo(final WallItem item, final int gen) {
        final TextureView tv = new TextureView(ctx);
        final MediaPlayer mp = new MediaPlayer();
        tv.setAlpha(0f);
        host.addView(tv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int hh) {
                try {
                    mp.setSurface(new Surface(st));
                    mp.setDataSource(item.path);
                    mp.setVolume(0f, 0f);
                    mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override public void onPrepared(MediaPlayer p) {
                            if (gen != generation || !running) { release(mp); host.removeView(tv); return; }
                            cropVideo(tv, p.getVideoWidth(), p.getVideoHeight());
                            p.setLooping(items.size() < 2);
                            p.start();
                        }
                    });
                    mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                        @Override public boolean onInfo(MediaPlayer p, int what, int extra) {
                            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && gen == generation) {
                                Bitmap snap = null;
                                try { snap = tv.getBitmap(320, 180); } catch (Exception ignored) { }
                                swapIn(item, tv, snap, null, mp);
                            }
                            return false;
                        }
                    });
                    mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override public void onCompletion(MediaPlayer p) { if (gen == generation) next(); }
                    });
                    mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override public boolean onError(MediaPlayer p, int a, int b) {
                            release(mp);
                            host.removeView(tv);
                            if (gen == generation) { items.remove(item); if (!items.isEmpty()) next(); }
                            return true;
                        }
                    });
                    mp.prepareAsync();
                } catch (Exception e) {
                    release(mp);
                    host.removeView(tv);
                    if (gen == generation) { items.remove(item); if (!items.isEmpty()) next(); }
                }
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int hh) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });
    }

    private static void cropVideo(TextureView tv, int vw, int vh) {
        int w = tv.getWidth(), hh = tv.getHeight();
        if (vw <= 0 || vh <= 0 || w == 0 || hh == 0) return;
        float s = Math.max(w / (float) vw, hh / (float) vh);
        Matrix m = new Matrix();
        m.setScale(vw * s / w, vh * s / hh, w / 2f, hh / 2f);
        tv.setTransform(m);
    }

    private static void release(MediaPlayer mp) {
        try { mp.stop(); } catch (Exception ignored) { }
        try { mp.release(); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ fondu enchaîné

    /** Couche floue de la nouvelle image, posée juste sous elle pendant la transition. */
    private ImageView softLayer(Bitmap sharp, View incoming) {
        Bitmap b = Glass.soft(sharp);
        if (b == null) return null;
        ImageView v = new ImageView(ctx);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setImageBitmap(b);
        v.setAlpha(0f);
        v.setScaleX(1.04f);
        v.setScaleY(1.04f);
        host.addView(v, host.indexOfChild(incoming), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    private void swapIn(WallItem item, final View incoming, Bitmap backdrop, AnimatedScene scene, MediaPlayer mp) {
        final View outgoing = currentView;
        final Bitmap oldBitmap = currentBitmap;
        final AnimatedScene oldScene = currentScene;
        final MediaPlayer oldPlayer = player;

        if (incoming.getParent() == null) {
            host.addView(incoming, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            incoming.bringToFront();
        }
        incoming.setAlpha(0f);
        // entre deux images : la nouvelle arrive d'abord floue, puis devient nette (mise au point)
        final long fd = outgoing == null ? 900 : fade();
        final ImageView softView = outgoing != null && mp == null && scene == null ? softLayer(backdrop, incoming) : null;
        if (softView != null) {
            softView.animate().alpha(1f).setStartDelay(0).setDuration(fd * 45 / 100)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f)).start();
        }
        incoming.animate().alpha(1f).setStartDelay(softView != null ? fd * 30 / 100 : 0)
                .setDuration(softView != null ? fd * 70 / 100 : fd).withEndAction(new Runnable() {
            @Override public void run() {
                if (softView != null) host.removeView(softView);
                if (outgoing != null) {
                    Object a = outgoing.getTag();
                    if (a instanceof Animator) ((Animator) a).cancel();
                    host.removeView(outgoing);
                }
                if (oldScene != null) { oldScene.setRunning(false); oldScene.data().recycle(); }
                else if (oldBitmap != null && !oldBitmap.isRecycled() && oldPlayer == null) oldBitmap.recycle();
                if (oldPlayer != null) release(oldPlayer);
            }
        }).start();

        current = item;
        currentView = incoming;
        currentScene = scene;
        player = mp;
        currentBitmap = mp == null ? backdrop : null;
        if (listener != null) listener.onWallShown(item, backdrop);
    }
}

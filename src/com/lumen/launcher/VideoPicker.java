package com.lumen.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Choix des vidéos avec aperçus : chaque vidéo proposée (catalogue Commons, et Pexels si une clé est
 * réglée) s'affiche avec une image, sa durée et sa définition. OK coche ou décoche.
 * Seules les vidéos cochées sont téléchargées ; décocher une vidéo l'efface de la TV.
 */
final class VideoPicker {
    private VideoPicker() { }

    private static java.util.concurrent.ExecutorService pool;
    private static synchronized java.util.concurrent.ExecutorService pool() {
        if (pool == null) pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        return pool;
    }

    /** scope : Online.SCOPE_FONDS ou Online.SCOPE_COSY. onClose est appelé après la fermeture. */
    static void open(final MainActivity a, final String scope, final Runnable onClose) {
        final boolean cosy = Online.SCOPE_COSY.equals(scope);
        final boolean[] changed = {false};
        FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() {
                if (changed[0]) {
                    if (cosy) a.online.refreshCosy();
                    else { a.online.refreshIfNeeded(Online.VIDEO, true); a.wall.restart(); }
                }
                if (onClose != null) onClose.run();
            }
        });
        ImageView bg = new ImageView(a);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (Glass.backdrop() != null) bg.setImageBitmap(Glass.backdrop());
        o.addView(bg, MainActivity.match());
        View tint = new View(a);
        tint.setBackgroundColor(0xB805060A);
        o.addView(tint, MainActivity.match());

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(80));
        final LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(a.u(112), a.u(72), a.u(112), a.u(140));
        sv.addView(col);
        o.addView(sv, MainActivity.match());

        TextView title = a.text(52, "sans-serif-light", 0xF5FFFFFF);
        title.setText(cosy ? "Vidéos des ambiances" : "Fonds vidéo");
        col.addView(title);
        final TextView state = a.text(24, "sans-serif", 0x99FFFFFF);
        state.setPadding(0, a.u(12), 0, a.u(30));
        state.setText("Chargement des aperçus…");
        col.addView(state);

        final LinearLayout back = a.listRow("Terminé", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(true); }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(a.u(400), a.u(88));
        bl.bottomMargin = a.u(20);
        col.addView(back, bl);
        back.requestFocus();

        pool().execute(new Runnable() {
            @Override public void run() {
                List<Online.Pick> picks;
                String err = null;
                try { picks = a.online.picks(scope); }
                catch (Exception e) { picks = new ArrayList<>(); err = e.getMessage(); }
                final List<Online.Pick> list = picks;
                final String error = err;
                a.h.post(new Runnable() {
                    @Override public void run() {
                        if (col.getParent() == null || !col.isAttachedToWindow()) return;
                        if (list.isEmpty()) {
                            state.setText(error == null ? "Aucune vidéo proposée pour l'instant." : "Connexion impossible · réessaie plus tard");
                            return;
                        }
                        fill(a, col, state, list, cosy, changed);
                    }
                });
            }
        });
    }

    private static void fill(final MainActivity a, LinearLayout col, final TextView state, List<Online.Pick> picks,
                             boolean cosy, final boolean[] changed) {
        // regroupe par ville, thème ou ambiance, dans l'ordre d'arrivée
        Map<String, List<Online.Pick>> groups = new LinkedHashMap<>();
        for (Online.Pick p : picks) {
            List<Online.Pick> l = groups.get(p.group);
            if (l == null) { l = new ArrayList<>(); groups.put(p.group, l); }
            l.add(p);
        }
        if (cosy) { // ordre des ambiances, catalogue puis Pexels mélangés par ambiance
            Map<String, List<Online.Pick>> ordered = new LinkedHashMap<>();
            for (int k = 0; k < Online.COSY_NAMES.length; k++) {
                List<Online.Pick> l = groups.get(Online.cosyGroup(k));
                if (l != null) ordered.put(Online.cosyGroup(k), l);
            }
            groups = ordered;
        }
        final Runnable count = new Runnable() {
            @Override public void run() {
                int n = 0;
                for (Online.Pick p : a.online.lastPicks()) if (a.prefs.videoOk(p.key)) n++;
                state.setText(n == 0 ? "Coche les vidéos à garder : seules celles-ci seront téléchargées."
                        : n + (n > 1 ? " vidéos cochées" : " vidéo cochée") + " · décocher efface la vidéo de la TV");
            }
        };
        a.online.rememberPicks(picks);
        count.run();

        int screenW = a.root.getWidth() > 0 ? a.root.getWidth() : a.u(1920);
        final int cols = 4, gap = a.u(36), tw = (screenW - a.u(224) - gap * (cols - 1)) / cols, th = tw * 9 / 16;
        for (Map.Entry<String, List<Online.Pick>> g : groups.entrySet()) {
            String name = g.getKey();
            if (name.startsWith("cosy:")) {
                try { name = Online.COSY_NAMES[Integer.parseInt(name.substring(5))]; } catch (Exception ignored) { }
            }
            TextView h = a.text(30, "sans-serif-medium", 0xE6FFFFFF);
            h.setText(name);
            h.setPadding(0, a.u(36), 0, a.u(4));
            col.addView(h);
            LinearLayout line = null;
            List<Online.Pick> l = g.getValue();
            for (int i = 0; i < l.size(); i++) {
                if (i % cols == 0) {
                    line = new LinearLayout(a);
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    line.setClipChildren(false);
                    line.setClipToPadding(false);
                    line.setPadding(0, a.u(18), 0, a.u(18));
                    col.addView(line);
                }
                View cell = cell(a, l.get(i), tw, th, changed, count);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i % cols != cols - 1) cl.rightMargin = gap;
                line.addView(cell, cl);
            }
        }
    }

    private static View cell(final MainActivity a, final Online.Pick p, int tw, int th, final boolean[] changed, final Runnable count) {
        final FrameLayout cell = new FrameLayout(a);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setSoundEffectsEnabled(false);
        cell.setClipChildren(false);
        final FrameLayout card = new FrameLayout(a);
        final float radius = a.u(18);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        card.setClipToOutline(true);
        card.setBackgroundColor(0xFF15161B);
        final ImageView img = new ImageView(a);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(img, MainActivity.match());
        final View dim = new View(a);
        dim.setBackgroundColor(0x8C000000);
        card.addView(dim, MainActivity.match());
        // durée · définition · source, en bas de l'aperçu
        TextView info = a.text(18, "sans-serif-medium", 0xF2FFFFFF);
        info.setText(describe(p));
        info.setShadowLayer(a.u(6), 0, 0, 0xCC000000);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        il.leftMargin = a.u(14);
        il.bottomMargin = a.u(10);
        card.addView(info, il);
        final TextView check = a.text(30, "sans-serif-medium", 0xFF111216);
        check.setText("✓");
        check.setGravity(Gravity.CENTER);
        GradientDrawable cb = new GradientDrawable();
        cb.setShape(GradientDrawable.OVAL);
        cb.setColor(0xFFF5F5F7);
        check.setBackground(cb);
        FrameLayout.LayoutParams ckl = new FrameLayout.LayoutParams(a.u(48), a.u(48), Gravity.TOP | Gravity.END);
        ckl.topMargin = ckl.rightMargin = a.u(14);
        card.addView(check, ckl);
        cell.addView(card, new FrameLayout.LayoutParams(tw, th));
        TextView name = a.text(21, "sans-serif", 0xB3FFFFFF);
        name.setText(p.title);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(tw, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        nl.topMargin = th + a.u(12);
        cell.addView(name, nl);
        cell.setLayoutParams(new LinearLayout.LayoutParams(tw, th + a.u(48)));

        final Runnable paint = new Runnable() {
            @Override public void run() {
                boolean on = a.prefs.videoOk(p.key);
                dim.setVisibility(on ? View.GONE : View.VISIBLE);
                check.setVisibility(on ? View.VISIBLE : View.GONE);
            }
        };
        paint.run();
        cell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) Sounds.get(a).tick();
                cell.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(220)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
                card.setElevation(f ? a.u(30) : 0);
                card.setForeground(f ? GalleryPanel.ring(a, radius) : null);
            }
        });
        cell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Sounds.get(a).tock();
                a.prefs.setVideoOk(p.key, !a.prefs.videoOk(p.key));
                changed[0] = true;
                paint.run();
                count.run();
            }
        });
        pool().execute(new Runnable() {
            @Override public void run() {
                File f = a.online.thumbOf(p);
                if (f == null) return;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 1;
                final Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (b != null) a.h.post(new Runnable() { @Override public void run() { img.setImageBitmap(b); } });
            }
        });
        return cell;
    }

    /** « 4 min 24 · 4K · Pexels » */
    static String describe(Online.Pick p) {
        StringBuilder b = new StringBuilder();
        int s = (int) Math.round(p.duration);
        if (s > 0) b.append(s >= 60 ? (s / 60) + " min" + (s % 60 > 0 ? " " + (s % 60 < 10 ? "0" : "") + (s % 60) : "") : s + " s");
        String def = p.height >= 2000 ? "4K" : p.height >= 1400 ? "1440p" : p.height >= 1000 ? "HD" : p.height > 0 ? p.height + "p" : "";
        if (!def.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(def);
        if (p.pexels) b.append(b.length() > 0 ? " · " : "").append("Pexels");
        return b.toString();
    }
}

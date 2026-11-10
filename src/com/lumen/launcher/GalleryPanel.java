// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Galerie des fonds : miniatures de la source, OK coche ou décoche chaque image. */
final class GalleryPanel {
    private GalleryPanel() { }


    /** Deux fils pour les miniatures, créés à la première ouverture seulement. */
    private static java.util.concurrent.ExecutorService thumbs;

    private static synchronized java.util.concurrent.ExecutorService thumbs() {
        if (thumbs == null) thumbs = java.util.concurrent.Executors.newFixedThreadPool(2);
        return thumbs;
    }

    /** Toutes les images de la source en miniatures : OK coche ou décoche (affichée / masquée). */
    static void openGallery(final MainActivity a) {
        final List<WallItem> items = Library.buildAll(a, a.prefs, a.online);
        final boolean[] changed = {false};
        FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() { if (changed[0]) a.wall.restart(); }
        });
        ImageView bg = new ImageView(a);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (Glass.backdrop() != null) bg.setImageBitmap(Glass.backdrop());
        o.addView(bg, MainActivity.match());
        View tint = new View(a);
        tint.setBackgroundColor(0xA605060A);
        o.addView(tint, MainActivity.match());

        ScrollView sv = new ScrollView(a) {
            @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
                super.onScrollChanged(l, t, ol, ot);
                a.refreshGlass(this);
            }
        };
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(a.u(112), a.u(72), a.u(112), a.u(120));
        sv.addView(col);
        o.addView(sv, MainActivity.match());

        TextView title = a.text(52, "sans-serif-light", Palette.TEXT);
        title.setText("Fonds · " + Prefs.SRC_NAMES[a.prefs.source()]);
        col.addView(title);
        final TextView state = a.text(24, "sans-serif", Palette.TEXT_2);
        state.setPadding(0, a.u(12), 0, a.u(36));
        col.addView(state);
        final String[] themes = Library.themesFor(a.prefs.source());
        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (a.overlay == null || state.getParent() == null || !state.isAttachedToWindow()) return;
                String st = "";
                for (String t : themes) { st = a.online.status(t); if (a.online.isBusy(t)) break; }
                int visible = 0;
                for (WallItem w : items) if (!a.prefs.hiddenWalls().contains(w.id)) visible++;
                String count;
                if (items.isEmpty()) {
                    // pas de « 0 fond · 0 affichés » : un message doux, avec des points qui avancent
                    int dots = (int) (System.currentTimeMillis() / 600 % 4);
                    count = "Les photos arrivent" + "...".substring(0, dots) + "   ".substring(dots);
                } else {
                    count = items.size() + (items.size() > 1 ? " fonds" : " fond")
                            + (visible == items.size() ? "" : " · " + visible + (visible > 1 ? " affichés" : " affiché"));
                }
                state.setText(themes.length == 0 ? count : count + (st.isEmpty() || st.startsWith("À jour") && items.isEmpty() ? "" : "   ·   " + st));
                a.h.postDelayed(this, items.isEmpty() ? 600 : 1000);
            }
        };
        tick.run();

        if (items.isEmpty()) {
            TextView none = a.text(28, "sans-serif", 0xB3FFFFFF);
            none.setText("Elles s'afficheront ici dès qu'elles seront prêtes.");
            col.addView(none);
            LinearLayout back = a.listRow("Retour", null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(true); }
            });
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(a.u(400), a.u(88));
            bl.topMargin = a.u(30);
            col.addView(back, bl);
            back.requestFocus();
            return;
        }

        int screenW = a.root.getWidth() > 0 ? a.root.getWidth() : a.u(1920);
        final int cols = 4, gap = a.u(40), tw = (screenW - a.u(224) - gap * (cols - 1)) / cols, th = tw * 9 / 16;
        LinearLayout line = null;
        View first = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % cols == 0) {
                line = new LinearLayout(a);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setClipChildren(false);
                line.setClipToPadding(false);
                line.setPadding(0, a.u(20), 0, a.u(20));
                col.addView(line);
            }
            final WallItem it = items.get(i);
            View cell = galleryCell(a, it, tw, th, changed);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % cols != cols - 1) cl.rightMargin = gap;
            line.addView(cell, cl);
            if (first == null) first = cell;
        }
        first.requestFocus();
    }

    static View galleryCell(final MainActivity a, final WallItem it, int tw, int th, final boolean[] changed) {
        final CheckCell c = new CheckCell(a, tw, th);
        final ImageView img = c.img;
        // voile sur les fonds masqués
        final View dim = new View(a);
        dim.setBackgroundColor(0xB3000000);
        c.card.addView(dim, MainActivity.match());
        c.check.setText("✓");
        final Runnable paint = new Runnable() {
            @Override public void run() {
                boolean visible = !a.prefs.hiddenWalls().contains(it.id);
                dim.setVisibility(visible ? View.GONE : View.VISIBLE);
                c.check.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        };
        paint.run();
        View cell = c.finish(it.title, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (a.prefs.hiddenWalls().contains(it.id)) a.prefs.unhideWall(it.id); else a.prefs.hideWall(it.id);
                changed[0] = true;
                paint.run();
            }
        });
        // miniature chargée en arrière-plan
        thumbs().execute(new Runnable() {
            @Override public void run() {
                final Bitmap b = thumbnail(a, it);
                if (b != null) a.h.post(new Runnable() { @Override public void run() { img.setImageBitmap(b); } });
            }
        });
        return cell;
    }

    static Bitmap thumbnail(final MainActivity a, WallItem it) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            if (it.kind == WallItem.IMAGE_ASSET) {
                o.inSampleSize = 4;
                java.io.InputStream in = a.getAssets().open(it.path);
                Bitmap b = android.graphics.BitmapFactory.decodeStream(in, null, o);
                in.close();
                return b;
            }
            if (it.kind == WallItem.ANIM) {
                o.inSampleSize = 4;
                java.io.InputStream in = a.getAssets().open(it.path + "/sky.jpg");
                Bitmap sky = android.graphics.BitmapFactory.decodeStream(in, null, o);
                in.close();
                Bitmap out = sky.copy(Bitmap.Config.ARGB_8888, true);
                sky.recycle();
                android.graphics.Canvas c = new android.graphics.Canvas(out);
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
                for (String l : new String[]{"l0", "l1", "l2", "l3", "fog"}) {
                    java.io.InputStream li = a.getAssets().open(it.path + "/" + l + ".png");
                    Bitmap lb = android.graphics.BitmapFactory.decodeStream(li, null, o);
                    li.close();
                    java.io.InputStream ti = a.getAssets().open(it.path + "/" + l + ".txt");
                    byte[] buf = new byte[16];
                    int n = ti.read(buf);
                    ti.close();
                    int top = Integer.parseInt(new String(buf, 0, Math.max(0, n)).trim()) / 4;
                    c.drawBitmap(lb, 0, top, p);
                    lb.recycle();
                }
                return out;
            }
            if (it.kind == WallItem.IMAGE_FILE) {
                o.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(it.path, o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= 480) sample *= 2;
                android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
                o2.inSampleSize = sample;
                return Wall.trimBorders(android.graphics.BitmapFactory.decodeFile(it.path, o2));
            }
            if (it.kind == WallItem.VIDEO_FILE) {
                android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                r.setDataSource(it.path);
                long durMs = 0;
                try { durMs = Long.parseLong(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)); } catch (Exception ignored) { }
                // image prise au tiers de la vidéo (évite les débuts en fondu noir)
                Bitmap f = r.getFrameAtTime(durMs > 0 ? durMs * 1000 / 3 : 5_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                r.release();
                if (f != null && f.getWidth() > 640) {
                    Bitmap s = Bitmap.createScaledBitmap(f, 640, f.getHeight() * 640 / f.getWidth(), true);
                    if (s != f) f.recycle();
                    f = s;
                }
                return f;
            }
        } catch (Throwable ignored) { }
        return null;
    }

}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Liste les applis installées, avec leur bannière TV (ou leur icône à défaut). */
final class AppRepo {

    static final class App {
        final String key;          // "package/activité"
        final String pkg;
        final String label;
        final ComponentName component;
        final boolean leanback;
        /** Entrée de la TV (HDMI…) présentée comme une appli ; null pour une vraie appli. */
        final String inputId;
        private final android.media.tv.TvInputInfo input;
        private final ActivityInfo info;
        private Drawable banner;
        private Drawable icon;
        private boolean loaded;

        App(ResolveInfo ri, PackageManager pm, boolean leanback) {
            this.info = ri.activityInfo;
            this.pkg = info.packageName;
            this.component = new ComponentName(info.packageName, info.name);
            this.key = component.flattenToShortString();
            CharSequence l = ri.loadLabel(pm);
            this.label = l == null ? pkg : l.toString().trim();
            this.leanback = leanback;
            this.inputId = null;
            this.input = null;
        }

        /** Une entrée de la TV : clé « input:<id> », elle se range dans les favoris comme une appli. */
        App(android.media.tv.TvInputInfo in, String label) {
            this.info = null;
            this.input = in;
            this.inputId = in.getId();
            this.key = INPUT_PREFIX + inputId;
            String p = "";
            try { p = in.getServiceInfo().packageName; } catch (Throwable ignored) { }
            this.pkg = p;
            this.component = null;
            this.label = label;
            this.leanback = true;
        }

        boolean isInput() { return inputId != null; }

        /** Rangée de la page Applications (catégorie déclarée par l'appli si Android 8+, sinon devinée). */
        private int section = -2;

        synchronized int section() {
            if (section != -2) return section;
            if (isInput()) return section = AppCategory.SOURCES;
            int cat = -1;
            try { cat = info.applicationInfo.getClass().getField("category").getInt(info.applicationInfo); } catch (Throwable ignored) { }
            section = AppCategory.of(pkg, label, cat);
            return section;
        }

        private synchronized void load(PackageManager pm) {
            if (loaded) return;
            loaded = true;
            if (isInput()) { // icône de l'entrée (Android 7+), sinon pictogramme HDMI
                try { icon = (Drawable) input.getClass().getMethod("loadIcon", Context.class).invoke(input, ctxRef); } catch (Throwable ignored) { }
                if (icon == null) icon = new Glyph(Glyph.INPUT, 0xFFF5F5F7);
                return;
            }
            try { banner = info.loadBanner(pm); } catch (Throwable ignored) { }
            if (banner == null) {
                try { banner = info.applicationInfo.loadBanner(pm); } catch (Throwable ignored) { }
            }
            try { icon = info.loadIcon(pm); } catch (Throwable ignored) { }
        }

        Drawable banner(PackageManager pm) { load(pm); return banner; }
        Drawable icon(PackageManager pm) { load(pm); return icon; }

        private int color;
        private boolean colorDone;

        /** Couleur dominante de l'icône (ou de la bannière), assombrie pour garder un texte blanc lisible. */
        synchronized int color(PackageManager pm) {
            if (colorDone) return color;
            colorDone = true;
            if (isInput()) return color = 0xFF2E3B52;
            color = dominant(icon(pm));
            if (color == 0) color = dominant(banner(pm));
            if (color == 0) color = 0xFF3A3C44;
            return color;
        }

        private static int dominant(Drawable d) {
            if (d == null) return 0;
            try {
                int n = 32;
                android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(b);
                android.graphics.Rect old = new android.graphics.Rect(d.getBounds());
                d.setBounds(0, 0, n, n);
                d.draw(cv);
                d.setBounds(old);
                int[] px = new int[n * n];
                b.getPixels(px, 0, n, 0, 0, n, n);
                b.recycle();
                // histogramme de teintes pondéré par la saturation
                float[] weight = new float[24];
                float[][] sum = new float[24][3];
                float[] hsv = new float[3];
                float greyW = 0;
                float[] greySum = new float[3];
                for (int c : px) {
                    if ((c >>> 24) < 200) continue;
                    android.graphics.Color.colorToHSV(c, hsv);
                    if (hsv[2] < 0.12f) continue;
                    int r = (c >> 16) & 255, g = (c >> 8) & 255, bl = c & 255;
                    if (hsv[1] < 0.18f) { greyW += 1; greySum[0] += r; greySum[1] += g; greySum[2] += bl; continue; }
                    int bin = Math.min(23, (int) (hsv[0] / 15f));
                    float w = hsv[1] * hsv[2];
                    weight[bin] += w;
                    sum[bin][0] += r * w; sum[bin][1] += g * w; sum[bin][2] += bl * w;
                }
                int best = -1;
                for (int i = 0; i < 24; i++) if (best < 0 || weight[i] > weight[best]) best = i;
                int rgb;
                if (best >= 0 && weight[best] > 4f) {
                    float w = weight[best];
                    rgb = android.graphics.Color.rgb((int) (sum[best][0] / w), (int) (sum[best][1] / w), (int) (sum[best][2] / w));
                } else if (greyW > 0) {
                    rgb = android.graphics.Color.rgb((int) (greySum[0] / greyW), (int) (greySum[1] / greyW), (int) (greySum[2] / greyW));
                } else return 0;
                android.graphics.Color.colorToHSV(rgb, hsv);
                hsv[2] = Math.min(hsv[2], 0.62f);            // assez sombre pour un texte blanc
                if (hsv[1] > 0.2f) hsv[1] = Math.max(hsv[1], 0.55f); // couleur franche
                return android.graphics.Color.HSVToColor(hsv);
            } catch (Throwable t) {
                return 0;
            }
        }

        Intent launchIntent() {
            if (isInput()) { // l'appli TV du téléviseur s'ouvre directement sur cette entrée
                android.net.Uri u = input.isPassthroughInput()
                        ? android.media.tv.TvContract.buildChannelUriForPassthroughInput(inputId)
                        : android.media.tv.TvContract.buildChannelsUriForInput(inputId);
                Intent i = new Intent(Intent.ACTION_VIEW, u);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return i;
            }
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
            i.setComponent(component);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return i;
        }
    }

    static final String INPUT_PREFIX = "input:";
    /** Contexte applicatif, pour l'icône des entrées (Android 7+). */
    private static Context ctxRef;

    private final Context ctx;
    private final Map<String, App> byKey = new LinkedHashMap<>();
    private final List<App> sorted = new ArrayList<>();

    AppRepo(Context ctx) { this.ctx = ctx.getApplicationContext(); ctxRef = this.ctx; }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        Map<String, App> old = new LinkedHashMap<>(byKey);
        byKey.clear();
        Map<String, Boolean> seenPkg = new LinkedHashMap<>();
        // Applis TV en priorité (elles ont une vraie bannière)
        add(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, old, seenPkg);
        add(pm, Intent.CATEGORY_LAUNCHER, false, old, seenPkg);
        addInputs(old);
        sorted.clear();
        sorted.addAll(byKey.values());
        final Collator c = Collator.getInstance(Locale.getDefault());
        c.setStrength(Collator.PRIMARY);
        Collections.sort(sorted, new Comparator<App>() {
            @Override public int compare(App a, App b) { return c.compare(a.label, b.label); }
        });
    }

    private void add(PackageManager pm, String category, boolean leanback,
                     Map<String, App> old, Map<String, Boolean> seenPkg) {
        Intent q = new Intent(Intent.ACTION_MAIN);
        q.addCategory(category);
        List<ResolveInfo> list;
        try { list = pm.queryIntentActivities(q, 0); } catch (Throwable t) { return; }
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) continue;
            String p = ri.activityInfo.packageName;
            if (p.equals(ctx.getPackageName())) continue;
            if (!leanback && seenPkg.containsKey(p)) continue; // déjà présente en version TV
            App a = new App(ri, pm, leanback);
            App prev = old.get(a.key);
            if (prev != null && prev.leanback == leanback) a = prev; // garde le cache d'images
            if (!byKey.containsKey(a.key)) byKey.put(a.key, a);
            seenPkg.put(p, Boolean.TRUE);
        }
    }

    /** Entrées physiques de la TV (HDMI, AV, composante) : visibles et rangées comme des applis. */
    private void addInputs(Map<String, App> old) {
        try {
            android.media.tv.TvInputManager tim = (android.media.tv.TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return;
            for (android.media.tv.TvInputInfo in : tim.getTvInputList()) {
                int type = in.getType();
                if (type == android.media.tv.TvInputInfo.TYPE_TUNER || type == android.media.tv.TvInputInfo.TYPE_OTHER) continue;
                try { if ((Boolean) in.getClass().getMethod("isHidden", Context.class).invoke(in, ctx)) continue; } catch (Throwable ignored) { }
                String key = INPUT_PREFIX + in.getId();
                App prev = old.get(key);
                if (prev != null) { byKey.put(key, prev); continue; }
                CharSequence lbl = null;
                try { lbl = (CharSequence) in.getClass().getMethod("loadCustomLabel", Context.class).invoke(in, ctx); } catch (Throwable ignored) { }
                if (lbl == null || lbl.length() == 0) lbl = in.loadLabel(ctx);
                byKey.put(key, new App(in, lbl == null ? "HDMI" : lbl.toString().trim()));
            }
        } catch (Throwable ignored) { } // TV sans entrées ou sans service TV
    }

    List<App> all() { return sorted; }

    /**
     * Prépare en arrière-plan icônes, bannières, couleurs et rangées de toutes les applis, pour que
     * la page Applications s'ouvre instantanément (le décodage des icônes est le plus lent).
     */
    void warmUp() {
        final List<App> list = new java.util.ArrayList<>(sorted);
        final PackageManager pm = ctx.getPackageManager();
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                for (App a : list) {
                    try { a.icon(pm); a.banner(pm); a.color(pm); a.section(); } catch (Throwable ignored) { }
                }
            }
        }, "lumen-warmup");
        t.setDaemon(true);
        t.start();
    }
    App get(String key) { return byKey.get(key); }

    /** Première activité trouvée pour un nom de package donné. */
    App findByPackage(String pkg) {
        for (App a : sorted) if (a.pkg.equals(pkg)) return a;
        return null;
    }
}

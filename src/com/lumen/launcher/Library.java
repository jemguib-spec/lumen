// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Construit la liste des fonds à afficher selon la source choisie et l'heure. */
final class Library {
    static final String[] PERIODS = {"aube", "jour", "soir", "nuit"};
    private static final String[] PERIOD_NAMES = {"Aube", "Jour", "Soir", "Nuit"};

    /** Moment de la journée d'après le vrai lever et coucher du soleil (voir Sky). */
    static String period() { return Sky.period(Calendar.getInstance()); }

    static String periodName(String p) {
        for (int i = 0; i < PERIODS.length; i++) if (PERIODS[i].equals(p)) return PERIOD_NAMES[i];
        return p;
    }

    private static String sceneName(String s) {
        if (s.equals("cretes")) return "Crêtes";
        if (s.equals("horizon")) return "Horizon";
        if (s.equals("lac")) return "Lac";
        return s;
    }

    /** Liste des fonds, déjà filtrée des fonds masqués. Peut être vide (photos pas encore téléchargées). */
    static List<WallItem> build(Context c, Prefs prefs, Online online) {
        return build(c, prefs, online, prefs.source());
    }

    /** Source des images du mode veille : jamais de vidéo (la source « Vidéos » devient les paysages Lumen). */
    static int veilleSource(Prefs prefs) {
        int v = prefs.veilleSource();
        int src = v >= 0 ? v : prefs.source();
        return src == Prefs.SRC_VIDEO ? Prefs.SRC_LUMEN : src;
    }

    /** Fonds du mode veille : images (et paysages animés), sans aucune vidéo. */
    static List<WallItem> buildVeille(Context c, Prefs prefs, Online online) {
        List<WallItem> out = new ArrayList<>();
        for (WallItem w : build(c, prefs, online, veilleSource(prefs))) if (w.kind != WallItem.VIDEO_FILE) out.add(w);
        return out;
    }

    static List<WallItem> build(Context c, Prefs prefs, Online online, int src) {
        List<WallItem> out = new ArrayList<>();
        String now = period();
        boolean follow = prefs.followTime();
        if (src == Prefs.SRC_LUMEN) {
            try {
                String[] files = c.getAssets().list("scenes");
                if (files != null) for (String f : files) {
                    String base = f.replaceAll("\\.jpg$", "");
                    int u = base.indexOf('_');
                    if (u < 0) continue;
                    String p = base.substring(0, u), s = base.substring(u + 1);
                    if (follow && !p.equals(now)) continue;
                    out.add(new WallItem(WallItem.IMAGE_ASSET, "lumen:" + base, "scenes/" + f,
                            sceneName(s) + " · " + periodName(p), ""));
                }
            } catch (Exception ignored) { }
        } else if (src == Prefs.SRC_ANIM) {
            for (String p : PERIODS) {
                if (follow && !p.equals(now)) continue;
                out.add(new WallItem(WallItem.ANIM, "anim:" + p, "anim/" + p,
                        "Crêtes animées · " + periodName(p), ""));
            }
        } else if (src == Prefs.SRC_SPACE) {
            out.addAll(online.items(Online.SPACE));
        } else if (src == Prefs.SRC_NATURE) {
            out.addAll(online.items(Online.NATURE));
        } else if (src == Prefs.SRC_ANIMALS) {
            out.addAll(online.items(Online.ANIMALS));
        } else if (src == Prefs.SRC_VIDEO) {
            out.addAll(online.items(Online.VIDEO));
        } else if (src == Prefs.SRC_MINE) {
            out.addAll(online.items(Online.MINE));
        } else if (src == Prefs.SRC_POTD) {
            // la plus récente d'abord, sans mélange : l'image d'aujourd'hui s'affiche en premier
            List<WallItem> p = online.items(Online.POTD);
            Collections.sort(p, new java.util.Comparator<WallItem>() {
                @Override public int compare(WallItem x, WallItem y) { return y.id.compareTo(x.id); }
            });
            Set<String> hid = prefs.hiddenWalls();
            List<WallItem> v = new ArrayList<>();
            for (WallItem w : p) if (!hid.contains(w.id)) v.add(w);
            return v;
        } else if (src == Prefs.SRC_MIX) {
            out.addAll(online.items(Online.SPACE));
            out.addAll(online.items(Online.NATURE));
            out.addAll(online.items(Online.ANIMALS));
        }
        Set<String> hidden = prefs.hiddenWalls();
        List<WallItem> visible = new ArrayList<>();
        for (WallItem w : out) if (!hidden.contains(w.id)) visible.add(w);
        Collections.shuffle(visible);
        return visible;
    }

    /** Tous les fonds de la source, masqués compris, dans un ordre stable (pour la galerie). */
    static List<WallItem> buildAll(Context c, Prefs prefs, Online online) {
        List<WallItem> out = new ArrayList<>();
        int src = prefs.source();
        if (src == Prefs.SRC_LUMEN) {
            String[] scenes = {"cretes", "horizon", "lac"};
            for (String p : PERIODS) for (String s : scenes)
                out.add(new WallItem(WallItem.IMAGE_ASSET, "lumen:" + p + "_" + s, "scenes/" + p + "_" + s + ".jpg",
                        sceneName(s) + " · " + periodName(p), ""));
        } else if (src == Prefs.SRC_ANIM) {
            for (String p : PERIODS) out.add(new WallItem(WallItem.ANIM, "anim:" + p, "anim/" + p,
                    "Crêtes animées · " + periodName(p), ""));
        } else if (src == Prefs.SRC_MINE) {
            out.addAll(online.items(Online.MINE));
        } else {
            for (String t : themesFor(src)) out.addAll(online.items(t));
        }
        return out;
    }

    /** Thèmes à télécharger pour la source choisie. */
    static String[] themesFor(int src) {
        switch (src) {
            case Prefs.SRC_SPACE: return new String[]{Online.SPACE};
            case Prefs.SRC_NATURE: return new String[]{Online.NATURE};
            case Prefs.SRC_ANIMALS: return new String[]{Online.ANIMALS};
            case Prefs.SRC_VIDEO: return new String[]{Online.VIDEO};
            case Prefs.SRC_MIX: return new String[]{Online.SPACE, Online.NATURE, Online.ANIMALS};
            case Prefs.SRC_POTD: return new String[]{Online.POTD};
            default: return new String[0];
        }
    }

    /** Fonds de secours quand les photos ne sont pas encore là : paysages Lumen du moment. */
    static List<WallItem> fallback(Context c) {
        List<WallItem> out = new ArrayList<>();
        String p = period();
        String[] scenes = {"cretes", "horizon", "lac"};
        for (String s : scenes) out.add(new WallItem(WallItem.IMAGE_ASSET, "lumen:" + p + "_" + s,
                "scenes/" + p + "_" + s + ".jpg", sceneName(s) + " · " + periodName(p), ""));
        Collections.shuffle(out);
        return out;
    }
}

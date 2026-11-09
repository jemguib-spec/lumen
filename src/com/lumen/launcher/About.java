// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pages « À propos », « Licences », « Crédits des fonds » et texte d'une licence (Réglages › Système). */
final class About {
    private About() { }

    static final String SOURCE = "github.com/jemguib-spec/lumen";

    // ------------------------------------------------------------------ À propos

    static void fillAbout(final MainActivity a, final LinearLayout col) {
        col.addView(info(a, "Lumen " + a.updater.currentVersion(), "Écran d'accueil pour Android TV · " + a.updater.state()));
        col.addView(info(a, "Code source", SOURCE));
        col.addView(info(a, "Licence", "GNU GPL version 3 ou ultérieure : vous pouvez utiliser, étudier, modifier et redistribuer Lumen, à condition de garder la même licence."));
        DisplayMetrics dm = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        col.addView(info(a, "Téléviseur", cap(Build.MANUFACTURER) + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE
                + " · écran " + Math.max(dm.widthPixels, dm.heightPixels) + " × " + Math.min(dm.widthPixels, dm.heightPixels)));
        final TextView[] space = new TextView[1];
        LinearLayout sp = info(a, "Espace utilisé par Lumen", "Calcul…");
        space[0] = (TextView) sp.getChildAt(1);
        col.addView(sp);
        new Thread(new Runnable() {
            @Override public void run() {
                final long online = size(new File(a.getFilesDir(), "online")), other = size(a.getFilesDir()) - online;
                a.h.post(new Runnable() {
                    @Override public void run() { space[0].setText("Photos et vidéos " + mb(online) + " · reste " + mb(other)); }
                });
            }
        }, "lumen-about").start();

        col.addView(a.section("Vie privée"));
        col.addView(info(a, "Aucune publicité, aucune télémétrie", "Aucun compte, aucune statistique, aucun identifiant envoyé."));
        StringBuilder hosts = new StringBuilder();
        for (String h : new java.util.TreeSet<>(NetPolicy.ALLOWED_HOSTS)) hosts.append(hosts.length() > 0 ? "\n" : "").append(h);
        if (!a.prefs.pexelsKey().isEmpty())
            for (String h : new java.util.TreeSet<>(NetPolicy.PEXELS_HOSTS)) hosts.append('\n').append(h);
        col.addView(info(a, "Seuls serveurs que Lumen peut contacter (HTTPS)", hosts.toString()));
        col.addView(info(a, "Affiches des épisodes", a.prefs.shelfPosters()
                ? "Téléchargées depuis le service qui les publie (Netflix…), contrôlées, jamais enregistrées."
                : "Désactivées."));
    }

    // ------------------------------------------------------------------ Licences

    static void fillLicences(final MainActivity a, final LinearLayout card, LinearLayout col) {
        col.addView(link(a, card, "Lumen", "Logiciel libre · GNU GPL version 3 ou ultérieure · code source : " + SOURCE, SettingsPanel.LICENCE_GPL));
        col.addView(link(a, card, "Police Inter", "SIL Open Font License 1.1 · The Inter Project Authors", SettingsPanel.LICENCE_INTER));
        col.addView(link(a, card, "Photos et vidéos Wikimedia Commons",
                "Licences libres (CC0, CC BY, CC BY-SA, domaine public) · auteur et licence de chaque fond", SettingsPanel.CREDITS));
        col.addView(info(a, "Pexels" + (a.prefs.pexelsKey().isEmpty() ? " (désactivé)" : ""),
                "Licence Pexels : utilisation gratuite ; le nom de l'auteur est affiché sur chaque vidéo."));
        col.addView(info(a, "Météo", "Données Open-Meteo.com, licence CC BY 4.0."));
        col.addView(info(a, "Paysages Lumen", "Créés par Lumen (tools/scenes.py), animations dessinées sur la TV."));
        col.addView(info(a, "Soleil et Lune", "Calculés sur la TV (algorithme d'almanach), sans service extérieur."));
        col.addView(info(a, "Tes photos et vidéos", "Envoyées depuis ton téléphone ; elles restent sur la TV."));
    }

    /** Tous les fonds téléchargés, avec leur auteur et leur licence. */
    static void fillCredits(final MainActivity a, final LinearLayout col) {
        final TextView wait = a.text(24, "sans-serif", 0x99FFFFFF);
        wait.setText("Chargement…");
        wait.setPadding(a.u(32), a.u(10), 0, 0);
        col.addView(wait);
        new Thread(new Runnable() {
            @Override public void run() {
                final List<WallItem> all = new ArrayList<>();
                for (String t : new String[]{Online.SPACE, Online.NATURE, Online.ANIMALS, Online.POTD, Online.VIDEO}) all.addAll(a.online.items(t));
                for (int k = 0; k < Online.COSY_NAMES.length; k++) all.addAll(a.online.cosyItems(k));
                a.h.post(new Runnable() {
                    @Override public void run() {
                        if (!col.isAttachedToWindow()) return;
                        col.removeView(wait);
                        int n = 0;
                        for (WallItem w : all) {
                            if (TextUtils.isEmpty(w.credit)) continue;
                            col.addView(info(a, w.title, w.credit));
                            n++;
                        }
                        if (n == 0) col.addView(info(a, "Aucun fond téléchargé", "Les paysages Lumen n'ont pas d'auteur extérieur."));
                    }
                });
            }
        }, "lumen-credits").start();
    }

    /** Texte d'une licence (fichier dans assets), lisible en défilant. */
    static void fillText(MainActivity a, LinearLayout col, String asset) {
        String txt;
        try (InputStream in = a.getAssets().open(asset)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            txt = b.toString("UTF-8");
        } catch (Exception e) { txt = "Texte indisponible."; }
        // découpé en paragraphes sélectionnables : la télécommande fait défiler la page
        for (String para : txt.split("\n\\s*\n")) {
            String p = para.trim();
            if (p.isEmpty()) continue;
            if (asset.equals("LICENSE.txt")) p = p.replaceAll("\\s*\n\\s*", " "); // texte GPL : lignes recollées
            LinearLayout r = info(a, null, p.replaceAll("\n ", "\n").replaceAll("\n\\.\n", "\n\n"));
            col.addView(r);
        }
    }

    // ------------------------------------------------------------------ éléments

    /** Ligne d'information : titre et texte, sélectionnable pour pouvoir défiler à la télécommande. */
    static LinearLayout info(final MainActivity a, String title, String text) {
        final LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setFocusable(true);
        r.setPadding(a.u(32), a.u(20), a.u(32), a.u(20));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.u(24));
        bg.setColor(0x00FFFFFF);
        r.setBackground(bg);
        final TextView t = a.text(26, "sans-serif", 0xEBFFFFFF);
        if (title != null) { t.setText(title); r.addView(t); } else t.setVisibility(View.GONE);
        final TextView s = a.text(title == null ? 21 : 22, "sans-serif", 0x8CFFFFFF);
        s.setText(text);
        s.setLineSpacing(0, 1.2f);
        if (title != null) s.setPadding(0, a.u(6), 0, 0);
        r.addView(s);
        if (title == null) r.addView(t); // garde l'index 1 pour le texte
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                bg.setColor(f ? 0x1FFFFFFF : 0x00FFFFFF);
                if (f) Sounds.get(a).tick();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = a.u(4);
        r.setLayoutParams(lp);
        return r;
    }

    /** Ligne qui ouvre une sous-page (« › »). */
    private static LinearLayout link(final MainActivity a, final LinearLayout card, String title, String text, final int page) {
        LinearLayout r = info(a, title + "   ›", text);
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Sounds.get(a).tock(); SettingsPanel.page(a, card, page); }
        });
        return r;
    }

    private static long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long n = 0;
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) n += size(x);
        return n;
    }

    private static String mb(long b) {
        return b >= (1L << 30) ? String.format(Locale.FRANCE, "%.1f Go", b / (double) (1L << 30)) : (b >> 20) + " Mo";
    }

    private static String cap(String s) { return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}

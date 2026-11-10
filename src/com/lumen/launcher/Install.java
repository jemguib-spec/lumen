// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Assistant d'installation (Réglages › Système › Installation, et au premier démarrage tant que
 * Lumen n'est pas l'écran d'accueil) : où en est la TV, les commandes exactes à taper depuis un
 * ordinateur avec l'adresse de la TV déjà remplie, et un QR code vers le guide. Lumen ne peut pas
 * désactiver lui-même l'accueil d'origine : seul ADB le permet, c'est voulu par Android.
 * Les règles pures (commandes, état) sont dans InstallPolicy, testé hors Android.
 */
final class Install {
    private Install() { }

    static final String[] STOCK_LAUNCHERS = InstallPolicy.STOCK_LAUNCHERS;
    static final String SETUP_WRAITH = InstallPolicy.SETUP_WRAITH;
    static final String GUIDE = InstallPolicy.GUIDE;

    static List<String> commands(String ip, String stock, boolean wraith) { return InstallPolicy.commands(ip, stock, wraith); }
    static List<String> undoCommands(String stock, boolean wraith) { return InstallPolicy.undoCommands(stock, wraith); }
    static boolean isHome(String resolvedPackage) { return InstallPolicy.isHome(resolvedPackage); }

    // ------------------------------------------------------------------ état de la TV

    /** Paquet qui répond à la touche Accueil (« android » quand la TV demande encore de choisir). */
    static String homePackage(Context c) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo r = c.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return r == null || r.activityInfo == null ? null : r.activityInfo.packageName;
        } catch (Exception e) { return null; }
    }

    static boolean isHome(Context c) { return isHome(homePackage(c)); }

    /** Accueil d'origine installé sur cette TV (null si aucun des connus). */
    static String stockLauncher(Context c) {
        for (String p : STOCK_LAUNCHERS) if (installed(c, p)) return p;
        return null;
    }

    static boolean installed(Context c, String pkg) {
        try { c.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    /** Un paquet est-il encore actif (non désactivé) ? */
    static boolean enabled(Context c, String pkg) {
        try {
            int s = c.getPackageManager().getApplicationEnabledSetting(pkg);
            return s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        } catch (Exception e) { return false; }
    }

    /** Nom lisible d'un paquet. */
    static String label(Context c, String pkg) {
        try { return String.valueOf(c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0))); }
        catch (Exception e) { return pkg; }
    }

    // ------------------------------------------------------------------ premier démarrage

    /** Au premier démarrage, si Lumen n'est pas l'accueil : la page s'ouvre une fois. */
    static void maybeShowAtStart(MainActivity a) {
        if (a.prefs.installHelpShown() || a.overlay != null || a.splashing || a.ambiance != null || a.ambientOn || WhoPage.isShowing(a)) return;
        if (isHome(a)) { a.prefs.setInstallHelpShown(true); return; } // tout est déjà en place : on ne dérange pas
        a.prefs.setInstallHelpShown(true);
        SettingsPanel.openAt(a, SettingsPanel.INSTALL);
    }

    // ------------------------------------------------------------------ page

    static void fill(final MainActivity a, final LinearLayout card, final LinearLayout col) {
        String home = homePackage(a);
        boolean ok = isHome(home);
        String stock = stockLauncher(a);
        boolean stockOn = stock != null && enabled(a, stock);
        boolean wraith = installed(a, SETUP_WRAITH);
        boolean wraithOn = wraith && enabled(a, SETUP_WRAITH);
        java.net.InetAddress ip = PhoneServer.localAddress();

        col.addView(a.section(L.t("Où en est la TV")));
        col.addView(About.info(a, state(ok) + L.t("Écran d'accueil"),
                ok ? L.t("Lumen s'ouvre quand tu appuies sur Accueil.") :
                home == null || "android".equals(home) ? L.t("La TV demande encore quel accueil utiliser : appuie sur Accueil, choisis Lumen puis « Toujours ».")
                        : L.t("La touche Accueil ouvre encore « ") + label(a, home) + L.t(" » : désactive-le avec les commandes ci-dessous.")));
        if (stock != null)
            col.addView(About.info(a, state(!stockOn) + L.t("Accueil d'origine (") + label(a, stock) + ")",
                    stockOn ? L.t("Encore actif : il reprendra la main tant qu'il n'est pas désactivé (réversible).") : L.t("Désactivé. Lumen est seul à répondre à la touche Accueil.")));
        if (wraith)
            col.addView(About.info(a, state(!wraithOn) + L.t("Assistant de configuration Google"),
                    wraithOn ? L.t("Encore actif : il peut relancer l'accueil Google au démarrage.") : L.t("Désactivé.")));
        boolean usage = ResumePanel.usageAllowed(a);
        col.addView(About.info(a, state(usage) + L.t("Historique complet pour « Reprendre »"),
                usage ? L.t("Autorisé : les applis ouvertes autrement que depuis Lumen apparaissent aussi.") : L.t("Facultatif. Sans lui, « Reprendre » ne connaît que les applis ouvertes depuis Lumen.")));
        boolean saver = ScreenSaver.isLumen(a);
        col.addView(About.info(a, state(saver) + L.t("Économiseur d'écran de la TV"),
                saver ? L.t("Le mode veille de Lumen s'affiche par-dessus n'importe quelle appli.") : L.t("Facultatif : Réglages › Mode veille › Économiseur d'écran de la TV.")));

        col.addView(a.section(ok ? L.t("Pour revenir à l'accueil d'origine") : L.t("Depuis un ordinateur")));
        TextView how = a.text(22, "sans-serif", Palette.TEXT_2);
        how.setText(ok ? L.t("Si un jour tu veux retrouver l'accueil Google, avec les « Android SDK Platform-Tools » (adb) :")
                : L.t("1. Paramètres de la TV › Système › À propos : appuie 7 fois sur « Build », puis Options pour les développeurs › Débogage USB.\n")
                + L.t("2. Sur un ordinateur du même Wi-Fi, avec les « Android SDK Platform-Tools » (adb), tape ces lignes et accepte la demande sur la TV :"));
        how.setLineSpacing(0, 1.25f);
        how.setPadding(a.u(32), 0, a.u(32), a.u(14));
        col.addView(how);
        List<String> cmds = ok ? undoCommands(stock, wraith) : commands(ip == null ? null : ip.getHostAddress(), stock, wraith);
        col.addView(code(a, cmds));
        if (!ok) {
            TextView then = a.text(22, "sans-serif", Palette.TEXT_2);
            then.setText(L.t("3. Appuie sur Accueil : Lumen s'affiche. Si la TV propose un choix, prends Lumen puis « Toujours ».\n")
                    + L.t("Sans ordinateur : l'appli « Send Files to TV » permet d'installer Lumen, mais pas de désactiver l'accueil Google."));
            then.setLineSpacing(0, 1.25f);
            then.setPadding(a.u(32), a.u(14), a.u(32), 0);
            col.addView(then);
        }

        col.addView(a.section(L.t("Guide complet")));
        LinearLayout guide = new LinearLayout(a);
        guide.setOrientation(LinearLayout.HORIZONTAL);
        guide.setGravity(Gravity.CENTER_VERTICAL);
        guide.setPadding(a.u(32), 0, a.u(32), 0);
        ImageView qr = new ImageView(a);
        qr.setImageBitmap(PhonePanel.qrBitmap(a, GUIDE, a.u(200)));
        qr.setContentDescription(L.t("QR code vers le guide d'installation"));
        qr.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.u(16)); }
        });
        qr.setClipToOutline(true);
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(a.u(200), a.u(200));
        ql.rightMargin = a.u(28);
        guide.addView(qr, ql);
        TextView g = a.text(22, "sans-serif", Palette.TEXT_2);
        g.setText("Scanne ce code avec le téléphone pour lire le guide pas à pas (installation, accueil, retour en arrière, télécommande).\n" + GUIDE.replace("https://", ""));
        g.setLineSpacing(0, 1.25f);
        guide.addView(g, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(guide);

        col.addView(a.section(L.t("Options")));
        col.addView(a.listRow(L.t("Vérifier à nouveau"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { SettingsPanel.page(a, card, SettingsPanel.INSTALL); }
        }), a.rowLp());
        col.addView(a.listRow(L.t("Proposer cette page au démarrage"), new MainActivity.Value() {
            @Override public String get() { return a.prefs.installHelpShown() ? "Non" : "Tant que Lumen n'est pas l'accueil"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setInstallHelpShown(!a.prefs.installHelpShown()); }
        }), a.rowLp());
    }

    private static String state(boolean ok) { return ok ? "✓  " : "○  "; }

    /** Bloc de commandes, une par ligne, en caractères à chasse fixe sur fond sombre. */
    private static View code(MainActivity a, List<String> lines) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setFocusable(true);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(a.u(20));
        bg.setColor(Palette.SHADOW);
        box.setBackground(bg);
        box.setPadding(a.u(28), a.u(20), a.u(28), a.u(20));
        StringBuilder all = new StringBuilder();
        for (String l : lines) {
            TextView t = new TextView(a);
            t.setTypeface(Typeface.MONOSPACE);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 23 * a.unit * a.textScale);
            t.setTextColor(Palette.TEXT);
            t.setText(l);
            t.setPadding(0, a.u(4), 0, a.u(4));
            box.addView(t);
            all.append(l).append('\n');
        }
        box.setContentDescription(L.t("Commandes : ") + all);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(a.u(32), 0, a.u(32), 0);
        box.setLayoutParams(lp);
        return box;
    }
}

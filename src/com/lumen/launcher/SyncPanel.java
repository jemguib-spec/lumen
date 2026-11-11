// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Réglages › Système › Autres TV : copier les réglages de cette TV vers une autre TV Lumen du même
 * réseau. L'autre TV ouvre « Recevoir » et affiche un code ; ici on choisit ce qui part, la TV, puis
 * on tape le code. L'autre TV demande confirmation avant d'appliquer. Règles dans TvSync, réseau
 * dans SyncLink.
 */
final class SyncPanel {
    private SyncPanel() { }

    /** Groupes à envoyer (tous par défaut, le temps que Lumen reste ouvert). */
    static final boolean[] groups = {true, true, true, true};

    private static LinearLayout tvBox;
    private static View addressRow;
    private static boolean sending;

    /** Nom de cette TV (celui choisi dans les paramètres d'Android, sinon le modèle). */
    static String tvName(MainActivity a) {
        String n = null;
        try { n = android.provider.Settings.Global.getString(a.getContentResolver(), "device_name"); } catch (Exception ignored) { }
        if (n == null || n.trim().isEmpty()) n = Build.MODEL;
        return TvSync.cleanName(n);
    }

    // ------------------------------------------------------------------ page des réglages

    static void fill(final MainActivity a, final LinearLayout col) {
        col.addView(a.listRow(L.t("Recevoir les réglages d'une autre TV"), new MainActivity.Value() {
            @Override public String get() { return L.t("Afficher un code"); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { openReceive(a); }
        }), a.rowLp());
        col.addView(a.section(L.t("Envoyer les réglages de cette TV")));
        for (int i = 0; i < groups.length; i++) {
            final int g = i;
            col.addView(a.ui.toggleRow(L.t(TvSync.GROUP_NAMES[g]), new GlassUi.Bool() {
                @Override public boolean get() { return groups[g]; }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { groups[g] = !groups[g]; }
            }), a.rowLp());
        }
        col.addView(a.section(L.t("Vers la TV")));
        tvBox = new LinearLayout(a);
        tvBox.setOrientation(LinearLayout.VERTICAL);
        tvBox.setClipChildren(false);
        col.addView(tvBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout addr = a.listRow(L.t("Saisir l'adresse d'une TV"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { openAddress(a); }
        });
        addressRow = addr;
        col.addView(addr, a.rowLp());
        TextView t = a.text(21, "sans-serif", Palette.TEXT_3);
        t.setText(L.t("Sur l'autre TV : Réglages › Système › Autres TV › Recevoir. Elle apparaît ici ; choisis-la puis tape le code qu'elle affiche. Le code PIN, Home Assistant, les clés, les habitudes, la télécommande et les vidéos téléchargées restent propres à chaque TV. Les réglages vont au profil ouvert sur l'autre TV."));
        t.setLineSpacing(0, 1.25f);
        t.setPadding(a.u(32), a.u(28), a.u(32), 0);
        col.addView(t);
        showTvs(a, new ArrayList<SyncLink.Tv>());
        SyncLink.startFinding(a, new SyncLink.Found() {
            @Override public void on(List<SyncLink.Tv> tvs) { if (tvBox != null && tvBox.isAttachedToWindow()) showTvs(a, tvs); }
        });
    }

    /** Liste des TV trouvées ; la TV sélectionnée le reste quand la liste change. */
    private static void showTvs(final MainActivity a, List<SyncLink.Tv> tvs) {
        View f = a.getCurrentFocus();
        Object keep = f != null && f.getParent() == tvBox ? f.getTag() : null;
        boolean hadFocus = f != null && f.getParent() == tvBox;
        tvBox.removeAllViews();
        View again = null;
        if (tvs.isEmpty()) {
            TextView w = a.text(24, "sans-serif", Palette.TEXT_2);
            w.setText(L.t("Recherche des TV en réception…"));
            w.setPadding(a.u(32), a.u(18), a.u(32), a.u(18));
            tvBox.addView(w);
        }
        for (final SyncLink.Tv tv : tvs) {
            LinearLayout row = a.listRow(tv.name, new MainActivity.Value() {
                @Override public String get() { return tv.ip.getHostAddress(); }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { askCode(a, tv.ip, tv.port, tv.name); }
            });
            row.setTag(tv.name);
            tvBox.addView(row, a.rowLp());
            if (tv.name.equals(keep)) again = row;
        }
        if (again != null) again.requestFocus();
        else if (hadFocus && addressRow != null) addressRow.requestFocus();
    }

    // ------------------------------------------------------------------ envoyer

    /** Adresse tapée à la main, si la TV n'apparaît pas (réseau qui bloque les annonces). */
    private static void openAddress(final MainActivity a) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.formCard(44, true);
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText(L.t("Adresse de l'autre TV"));
        card.addView(title);
        TextView hint = a.text(22, "sans-serif", Palette.TEXT_2);
        hint.setText(L.t("Elle s'affiche sous le code, sur la TV en réception."));
        hint.setPadding(0, a.u(12), 0, a.u(28));
        card.addView(hint);
        final android.widget.EditText in = new android.widget.EditText(a);
        in.setSingleLine(true);
        in.setHint(L.t("Ex. 192.168.1.20"));
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        in.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32 * a.unit);
        in.setTextColor(Palette.ON_SELECTED);
        in.setHintTextColor(Palette.ON_SELECTED_2);
        in.setPadding(a.u(28), a.u(20), a.u(28), a.u(20));
        in.setBackground(a.fieldBackground());
        in.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        final Runnable submit = new Runnable() {
            @Override public void run() {
                CityPanel.hideKeyboard(a, in);
                final Object[] ad = TvSync.address(in.getText().toString());
                if (ad == null) { a.showNotice(L.t("Adresse du réseau local attendue, ex. 192.168.1.20")); return; }
                a.showNotice(L.t("Recherche de la TV…"));
                new Thread(new Runnable() {
                    @Override public void run() {
                        InetAddress ip = null;
                        String[] h = null;
                        try { ip = InetAddress.getByName((String) ad[0]); h = SyncLink.hello(ip, (Integer) ad[1]); } catch (Exception ignored) { }
                        final InetAddress fip = ip;
                        final String[] fh = h;
                        a.h.post(new Runnable() {
                            @Override public void run() {
                                if (fh == null) { a.showNotice(L.t("Aucune TV en réception à cette adresse"), 3500); return; }
                                askCode(a, fip, (Integer) ad[1], fh[1]);
                            }
                        });
                    }
                }, "lumen-sync-hello").start();
            }
        };
        in.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent ev) { submit.run(); return true; }
        });
        in.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (code != KeyEvent.KEYCODE_ENTER && code != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
                if (e.getAction() == KeyEvent.ACTION_UP) submit.run();
                return true;
            }
        });
        card.addView(in, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(new View(a), new LinearLayout.LayoutParams(1, a.u(20)));
        card.addView(a.iconRow(Glyph.SEARCH, L.t("Valider"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { submit.run(); }
        }, false), a.rowLp());
        o.addView(card, new FrameLayout.LayoutParams(a.u(820), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        in.requestFocus();
    }

    /** Code affiché par l'autre TV, puis envoi. Le pavé reste ouvert tant que l'envoi n'a pas réussi. */
    private static void askCode(final MainActivity a, final InetAddress ip, final int port, final String name) {
        if (!TvSync.any(groups)) { a.showNotice(L.t("Coche au moins un groupe de réglages")); return; }
        final byte[] body;
        try {
            JSONObject o = new JSONObject();
            o.put("format", TvSync.FORMAT);
            o.put("version", TvSync.VERSION);
            o.put("from", tvName(a));
            o.put("groups", TvSync.groupsText(groups));
            o.put("settings", a.prefs.exportSync(groups));
            body = o.toString().getBytes("UTF-8");
        } catch (Exception e) {
            a.showNotice(L.t("Réglages illisibles"));
            return;
        }
        if (body.length > TvSync.MAX_BODY) { a.showNotice(L.t("Trop de réglages à envoyer")); return; }
        sending = false;
        PinDialog.code(a, L.t("Code affiché sur « ") + name + L.t(" »"), TvSync.CODE_LENGTH, new PinDialog.Entered() {
            @Override public boolean on(final String code, final TextView msg) {
                if (sending) return false;
                sending = true;
                msg.setText(L.t("Envoi…"));
                new Thread(new Runnable() {
                    @Override public void run() {
                        final int r = SyncLink.send(ip, port, code, body);
                        a.h.post(new Runnable() {
                            @Override public void run() {
                                sending = false;
                                switch (r) {
                                    case SyncLink.SENT:
                                        a.closeOverlay(true);
                                        a.showNotice(L.t("Réglages envoyés · à accepter sur « ") + name + L.t(" »"), 4500);
                                        break;
                                    case SyncLink.BAD_CODE: msg.setText(L.t("Code incorrect")); break;
                                    case SyncLink.CLOSED: msg.setText(L.t("Cette TV n'attend plus · relance « Recevoir » sur elle")); break;
                                    case SyncLink.NOT_LUMEN: msg.setText(L.t("Cette TV n'est plus en réception")); break;
                                    default: msg.setText(L.t("TV injoignable · sur le même réseau ?")); break;
                                }
                            }
                        });
                    }
                }, "lumen-sync-send").start();
                return false;
            }
        });
    }

    // ------------------------------------------------------------------ recevoir

    /** Affiche le code et attend un envoi (3 min au plus). Fermer l'écran arrête tout. */
    static void openReceive(final MainActivity a) {
        final String name = tvName(a);
        final String[] got = SyncLink.startReceiving(a, name, new SyncLink.Received() {
            @Override public void on(String fromIp, String json) { a.closeOverlay(false); confirm(a, fromIp, json); }
        }, new SyncLink.Stopped() {
            @Override public void on(int why) {
                a.closeOverlay(true);
                a.showNotice(why == SyncLink.TOO_MANY ? L.t("Trop de codes faux · réception arrêtée") : L.t("Réception arrêtée (rien reçu en 3 min)"), 3500);
            }
        });
        if (got == null) { a.showNotice(L.t("Pas de connexion réseau")); return; }
        FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() { SyncLink.stopReceiving(a); }
        });
        LinearLayout card = a.glassCard(Radius.CARD);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(a.u(64), a.u(56), a.u(64), a.u(48));
        TextView title = a.text(44, "sans-serif-light", Palette.TEXT);
        title.setText(L.t("Recevoir des réglages"));
        card.addView(title);
        TextView code = a.text(120, "sans-serif-light", Palette.TEXT);
        code.setText(TvSync.spaced(got[0]));
        code.setContentDescription(got[0]);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = a.u(24);
        card.addView(code, cl);
        TextView where = a.text(26, "sans-serif", Palette.TEXT_2);
        where.setText(name + "  ·  " + got[1]);
        card.addView(where);
        TextView how = a.text(22, "sans-serif", Palette.TEXT_3);
        how.setText(L.t("Sur l'autre TV : Réglages › Système › Autres TV, choisis cette TV puis tape ce code. Rien n'est appliqué sans ton accord ; la réception s'arrête après 3 min."));
        how.setGravity(Gravity.CENTER);
        how.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(a.u(720), ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = a.u(24);
        card.addView(how, hl);
        LinearLayout cancel = a.listRow(L.t("Annuler"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(true); }
        });
        LinearLayout.LayoutParams okl = new LinearLayout.LayoutParams(a.u(360), a.u(88));
        okl.topMargin = a.u(28);
        card.addView(cancel, okl);
        o.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        cancel.requestFocus();
    }

    /** Envoi reçu et signé : on montre d'où il vient et ce qu'il change, rien n'est appliqué sans « Appliquer ». */
    private static void confirm(final MainActivity a, String fromIp, String json) {
        final JSONObject settings;
        final boolean[] g;
        String from;
        try {
            JSONObject o = new JSONObject(json);
            if (!TvSync.FORMAT.equals(o.optString("format")) || o.optInt("version") != TvSync.VERSION) throw new Exception("format");
            settings = o.getJSONObject("settings");
            g = TvSync.parseGroups(o.optString("groups"));
            if (!TvSync.any(g)) throw new Exception("groupes");
            from = TvSync.cleanName(o.optString("from", fromIp));
        } catch (Exception e) {
            a.showNotice(L.t("Réglages reçus illisibles"));
            return;
        }
        StringBuilder what = new StringBuilder();
        for (int i = 0; i < g.length; i++) if (g[i]) what.append(what.length() == 0 ? "" : ", ").append(L.t(TvSync.GROUP_NAMES[i]));
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.menuCard(40);
        card.addView(a.menuHeader(null, false, L.t("Réglages reçus"), L.t("De « ") + from + L.t(" »") + " (" + fromIp + ")"));
        TextView why = a.text(22, "sans-serif", Palette.TEXT_2);
        String profile = a.prefs.hasProfiles() ? L.t(" Profil : ") + a.prefs.activeProfileName() + "." : "";
        why.setText(what + "." + profile + "\n" + L.t("Les réglages de ces groupes que l'autre TV n'a jamais changés reviennent à leur valeur par défaut."));
        why.setGravity(Gravity.CENTER);
        why.setLineSpacing(0, 1.2f);
        why.setPadding(a.u(24), 0, a.u(24), a.u(24));
        card.addView(why);
        LinearLayout apply = a.iconRow(Glyph.UPDATE, L.t("Appliquer sur cette TV"), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.closeOverlay(true);
                try {
                    int n = a.prefs.importSync(settings, g);
                    a.showNotice(n + L.t(" réglages copiés"));
                    a.h.postDelayed(new Runnable() { @Override public void run() { a.recreate(); } }, 1200); // tout est relu proprement
                } catch (Exception e) {
                    a.showNotice(L.t("Réglages reçus illisibles"));
                }
            }
        }, false);
        card.addView(apply, a.rowLp());
        card.addView(a.menuDivider());
        card.addView(a.iconRow(Glyph.CLOSE, L.t("Refuser"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(true); a.showNotice(L.t("Réglages refusés")); }
        }, true), a.rowLp());
        o.addView(card, new FrameLayout.LayoutParams(a.u(760), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        apply.requestFocus();
    }
}

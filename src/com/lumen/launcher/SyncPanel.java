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
 * Réglages › Système › Autres TV : relier deux TV Lumen du même réseau pour qu'elles restent
 * identiques. L'une ouvre « Recevoir » et affiche un code ; sur l'autre, qui sert de modèle, on la
 * choisit et on tape le code. La TV qui reçoit montre ce qui va se passer et ne fait rien sans
 * « Relier ». Ensuite, PairLink garde les deux TV pareilles. Règles dans TvSync et SyncState,
 * réseau dans SyncLink et PairLink.
 */
final class SyncPanel {
    private SyncPanel() { }

    private static LinearLayout tvBox;
    private static View addressRow;
    private static boolean sending;

    /** Nom de cette TV (celui choisi dans les paramètres d'Android, sinon le modèle). */
    static String tvName(MainActivity a) { return PairLink.deviceName(a); }

    /** « il y a 2 min », « En attente de l'autre TV », « Injoignable »… */
    static String status(MainActivity a) {
        long ok = a.pair.lastOk();
        if (a.pair.unreachable()) return L.t("Injoignable · Lumen éteint sur l'autre TV ?");
        if (ok <= 0) return L.t("En attente de l'autre TV");
        long min = (System.currentTimeMillis() - ok) / 60_000L;
        return min < 1 ? L.t("Synchronisées") : L.t("Synchronisées il y a ") + (min < 60 ? min + L.t(" min") : (min / 60) + L.t(" h"));
    }

    // ------------------------------------------------------------------ page des réglages

    static void fill(final MainActivity a, final LinearLayout col) {
        if (a.pair.paired()) {
            final LinearLayout st = a.listRow(L.t("Reliée à « ") + a.pair.peerName() + L.t(" »"), new MainActivity.Value() {
                @Override public String get() { return status(a); }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { a.pair.syncNow(); a.showNotice(L.t("Synchronisation…")); }
            });
            col.addView(st, a.rowLp());
            col.addView(a.listRow(L.t("Synchroniser maintenant"), null, new View.OnClickListener() {
                @Override public void onClick(View v) { a.pair.syncNow(); a.showNotice(L.t("Synchronisation…")); }
            }), a.rowLp());
            col.addView(a.menuDivider());
            col.addView(a.iconRow(Glyph.CLOSE, L.t("Délier les deux TV"), null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String name = a.pair.peerName();
                    a.pair.unpair(true);
                    a.showNotice(L.t("Plus reliée à « ") + name + L.t(" » · chaque TV garde ses réglages"), 4000);
                    SettingsPanel.page(a, (LinearLayout) col.getParent().getParent().getParent(), SettingsPanel.SYNC);
                }
            }, true), a.rowLp());
            hint(a, col, L.t("Les deux TV restent identiques : profils, favoris, fonds, boutons, sons, météo, clés, Home Assistant, code PIN, habitudes. Un changement sur l'une arrive sur l'autre en quelques secondes quand Lumen est à l'écran sur les deux, sinon dès qu'il y revient. Restent propres à chaque TV : touches de la télécommande, place pour les vidéos, qualité 4K. Tout est chiffré."));
            // l'état se met à jour pendant qu'on regarde la page
            a.h.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!st.isAttachedToWindow()) return;
                    for (Runnable r : new ArrayList<>(a.rowRefreshers)) r.run();
                    a.h.postDelayed(this, 2000);
                }
            }, 2000);
            return;
        }
        col.addView(a.listRow(L.t("Recevoir les réglages d'une autre TV"), new MainActivity.Value() {
            @Override public String get() { return L.t("Afficher un code"); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { openReceive(a, null); }
        }), a.rowLp());
        col.addView(a.section(L.t("Ou relier à cette TV, qui sert de modèle")));
        tvBox = new LinearLayout(a);
        tvBox.setOrientation(LinearLayout.VERTICAL);
        tvBox.setClipChildren(false);
        col.addView(tvBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout addr = a.listRow(L.t("Saisir l'adresse d'une TV"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { openAddress(a); }
        });
        addressRow = addr;
        col.addView(addr, a.rowLp());
        hint(a, col, L.t("Deux TV reliées restent identiques, en permanence. Sur l'autre TV : Réglages › Système › Autres TV › Recevoir. Elle apparaît ici ; choisis-la puis tape le code qu'elle affiche : elle prend tous les réglages de cette TV, puis chaque changement passe de l'une à l'autre. Tout est chiffré grâce au code."));
        showTvs(a, new ArrayList<SyncLink.Tv>());
        SyncLink.startFinding(a, new SyncLink.Found() {
            @Override public void on(List<SyncLink.Tv> tvs) { if (tvBox != null && tvBox.isAttachedToWindow()) showTvs(a, tvs); }
        });
    }

    private static void hint(MainActivity a, LinearLayout col, String text) {
        TextView t = a.text(21, "sans-serif", Palette.TEXT_3);
        t.setText(text);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(a.u(32), a.u(28), a.u(32), 0);
        col.addView(t);
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
                                if (fh[0].isEmpty()) { a.showNotice(L.t("Mets d'abord Lumen à jour sur l'autre TV (4.34 ou plus)"), 4000); return; }
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

    /**
     * Code affiché par l'autre TV, puis envoi de tout l'état de cette TV (le modèle). Le pavé reste
     * ouvert tant que l'envoi n'a pas réussi ; réussi, les deux TV sont reliées.
     */
    private static void askCode(final MainActivity a, final InetAddress ip, final int port, final String name) {
        final byte[] body;
        try {
            JSONObject o = new JSONObject();
            o.put("format", TvSync.FORMAT);
            o.put("version", TvSync.VERSION);
            o.put("from", tvName(a));
            o.put("device", a.pair.myId());
            o.put("port", a.pair.prepare()); // l'autre TV répondra ici
            o.put("entries", a.pair.state());
            body = o.toString().getBytes("UTF-8");
        } catch (Exception e) {
            a.showNotice(L.t("Réglages illisibles"));
            return;
        }
        if (body.length > TvSync.MAX_BODY / 2) { a.showNotice(L.t("Trop de réglages à envoyer")); return; }
        sending = false;
        PinDialog.code(a, L.t("Code affiché sur « ") + name + L.t(" »"), TvSync.CODE_LENGTH, new PinDialog.Entered() {
            @Override public boolean on(final String code, final TextView msg) {
                if (sending) return false;
                sending = true;
                msg.setText(L.t("Envoi…"));
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String[][] hello = new String[1][];
                        final byte[][] key = new byte[1][];
                        final int r = SyncLink.send(ip, port, code, body, hello, key);
                        a.h.post(new Runnable() {
                            @Override public void run() {
                                sending = false;
                                switch (r) {
                                    case SyncLink.SENT:
                                        a.pair.pair(hello[0][3], hello[0][1], ip.getHostAddress(), 0, key[0]);
                                        a.closeOverlay(true);
                                        a.showNotice(L.t("Reliée à « ") + name + L.t(" » · accepte sur l'autre TV"), 4500);
                                        break;
                                    case SyncLink.BAD_CODE: msg.setText(L.t("Code incorrect")); break;
                                    case SyncLink.CLOSED: msg.setText(L.t("Cette TV n'attend plus · relance « Recevoir » sur elle")); break;
                                    case SyncLink.NOT_LUMEN: msg.setText(L.t("Cette TV n'est plus en réception")); break;
                                    case SyncLink.OLDER_PEER: msg.setText(L.t("Mets d'abord Lumen à jour sur l'autre TV (4.34 ou plus)")); break;
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

    /** Fin de la réception (assistant du premier démarrage) : applied = réglages appliqués. */
    interface Done { void on(boolean applied); }

    /** Prévient « done » une seule fois, après la fermeture de l'écran en cours. */
    private static Done once(final MainActivity a, final Done done) {
        if (done == null) return null;
        final boolean[] said = {false};
        return new Done() {
            @Override public void on(final boolean applied) {
                if (said[0]) return;
                said[0] = true;
                a.h.post(new Runnable() { @Override public void run() { done.on(applied); } });
            }
        };
    }

    /** Affiche le code et attend un envoi (3 min au plus). Fermer l'écran arrête tout. done peut être null. */
    static void openReceive(final MainActivity a, Done whenDone) {
        final Done done = once(a, whenDone);
        final String name = tvName(a);
        final boolean[] received = {false};
        final String[] got = SyncLink.startReceiving(a, name, a.pair.myId(), new SyncLink.Received() {
            @Override public void on(String fromIp, String json, byte[] key) { received[0] = true; a.closeOverlay(false); confirm(a, fromIp, json, key, done); }
        }, new SyncLink.Stopped() {
            @Override public void on(int why) {
                a.closeOverlay(true);
                a.showNotice(why == SyncLink.TOO_MANY ? L.t("Trop de codes faux · réception arrêtée") : L.t("Réception arrêtée (rien reçu en 3 min)"), 3500);
            }
        });
        if (got == null) { a.showNotice(L.t("Pas de connexion réseau")); if (done != null) done.on(false); return; }
        FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() { SyncLink.stopReceiving(a); if (!received[0] && done != null) done.on(false); }
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
        how.setText(L.t("Sur l'autre TV : Réglages › Système › Autres TV, choisis cette TV puis tape ce code. Rien ne change sans ton accord ; la réception s'arrête après 3 min."));
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

    /**
     * Envoi reçu et déchiffré : on dit d'où il vient et ce qui va se passer (cette TV prend tout l'état
     * de l'autre, puis elles restent pareilles) ; rien ne change sans « Relier ».
     */
    private static void confirm(final MainActivity a, final String fromIp, String json, final byte[] key, final Done done) {
        final java.util.Map<String, SyncState.Entry> entries;
        final String from, device;
        final int port;
        try {
            JSONObject o = new JSONObject(json);
            if (!TvSync.FORMAT.equals(o.optString("format")) || o.optInt("version") != TvSync.VERSION) throw new Exception("format");
            entries = PairLink.entriesFrom(o.getJSONObject("entries"));
            device = o.optString("device");
            if (entries == null || !SyncState.deviceIdOk(device) || key == null) throw new Exception("contenu");
            from = TvSync.cleanName(o.optString("from", fromIp));
            int p = o.optInt("port", 0);
            port = p > 0 && p <= 65535 ? p : 0;
        } catch (Exception e) {
            a.showNotice(L.t("Réglages reçus illisibles"));
            if (done != null) done.on(false);
            return;
        }
        final boolean[] answered = {false};
        FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() { if (!answered[0] && done != null) done.on(false); } // Retour = refusé
        });
        LinearLayout card = a.menuCard(40);
        card.addView(a.menuHeader(null, false, L.t("Relier à « ") + from + L.t(" » ?"), fromIp));
        TextView why = a.text(22, "sans-serif", Palette.TEXT_2);
        String profile = a.prefs.hasProfiles() ? "\n" + L.t("Les profils de cette TV sont remplacés par ceux de l'autre.") : "";
        why.setText(L.t("Cette TV prend tous les réglages de l'autre : profils, favoris, fonds, boutons, sons, météo, clés, Home Assistant, code PIN, habitudes. Ensuite, les deux restent identiques : un changement sur l'une arrive sur l'autre.")
                + profile + "\n" + L.t("Restent propres à chaque TV : touches de la télécommande, place pour les vidéos, qualité 4K."));
        why.setGravity(Gravity.CENTER);
        why.setLineSpacing(0, 1.2f);
        why.setPadding(a.u(24), 0, a.u(24), a.u(24));
        card.addView(why);
        LinearLayout apply = a.iconRow(Glyph.UPDATE, L.t("Relier et prendre ses réglages"), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                answered[0] = true;
                if (done != null) done.on(true); // avant la reconstruction de l'écran
                a.closeOverlay(true);
                a.pair.adopt(entries);
                a.pair.pair(device, from, fromIp, port, key);
                a.showNotice(L.t("Reliée à « ") + from + L.t(" » · les deux TV sont identiques"), 4000);
                a.h.postDelayed(new Runnable() { @Override public void run() { a.recreate(); } }, 1200); // tout est relu proprement
            }
        }, false);
        card.addView(apply, a.rowLp());
        card.addView(a.menuDivider());
        card.addView(a.iconRow(Glyph.CLOSE, L.t("Refuser"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(true); a.showNotice(L.t("Réglages refusés")); }
        }, true), a.rowLp());
        o.addView(card, new FrameLayout.LayoutParams(a.u(820), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        apply.requestFocus();
    }
}

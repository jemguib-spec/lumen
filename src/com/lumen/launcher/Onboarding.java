// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Bitmap;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Assistant du premier démarrage, juste après l'installation. Première page : démarrer de zéro, ou
 * se synchroniser avec une autre TV du foyer (copie des réglages, voir SyncPanel). De zéro : profil,
 * accueil (langue, fond, vignettes, texte, horloge, sons), boutons de l'accueil, clés et services
 * renseignés depuis le téléphone, puis « C'est prêt ». L'étape est enregistrée : l'assistant reprend
 * où il en était (changement de langue, téléphone accepté, redémarrage). Ordre dans SetupFlow.
 * Retour revient à l'étape précédente ; « Plus tard » le range dans Réglages › Système.
 */
final class Onboarding {
    private Onboarding() { }

    private static FrameLayout shown;
    /** Ligne d'état seulement (OK ne fait rien). */
    private static final View.OnClickListener NOTHING = new View.OnClickListener() { @Override public void onClick(View v) { } };
    private static int step = SetupFlow.WELCOME;
    /** Sous-page du profil : ajouter une autre personne. */
    private static boolean addingPerson;
    /** L'écran va être reconstruit (langue, taille du texte) : l'assistant se rouvre aussitôt après. */
    static boolean reopenAfterRebuild;
    private static Runnable ticker;

    static boolean isShowing(MainActivity a) { return shown != null && a.overlay == shown; }

    /** L'assistant attend-il encore (installation neuve pas terminée) ? Décide une fois pour toutes au premier appel. */
    static boolean pending(MainActivity a) {
        int stored = a.prefs.setupStep();
        if (stored == SetupFlow.UNSET) {
            long first = 0, last = 0;
            try {
                android.content.pm.PackageInfo p = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                first = p.firstInstallTime;
                last = p.lastUpdateTime;
            } catch (Exception ignored) { }
            int s = SetupFlow.start(stored, first, last);
            a.prefs.setSetupStep(s);
            // déjà installé avant cette version : l'assistant d'installation garde son comportement
            return s != SetupFlow.DONE;
        }
        return stored != SetupFlow.DONE;
    }

    /** Au démarrage (et au retour sur Lumen) : l'assistant s'affiche tant qu'il n'est pas terminé. */
    static void maybeShowAtStart(MainActivity a) {
        if (!pending(a) || a.overlay != null || a.splashing || a.ambiance != null || a.ambientOn || !a.resumedNow) return;
        show(a, a.prefs.setupStep());
    }

    /** Après la reconstruction de l'écran (langue, taille du texte) ou un autre écran fermé (téléphone accepté). */
    static void resume(MainActivity a) {
        if (a.isFinishing() || a.overlay != null || !pending(a)) return;
        show(a, a.prefs.setupStep());
    }

    /** Relance l'assistant depuis Réglages › Système. */
    static void restart(MainActivity a) {
        a.prefs.setSetupStep(SetupFlow.WELCOME);
        show(a, SetupFlow.WELCOME);
    }

    /** Retour : étape précédente. false si l'assistant n'est pas à l'écran. */
    static boolean back(MainActivity a) {
        if (!isShowing(a)) return false;
        if (addingPerson) { addingPerson = false; show(a, SetupFlow.PROFILE); return true; }
        if (step == SetupFlow.WELCOME) return true; // « Plus tard » est là pour ça
        show(a, SetupFlow.prev(step));
        return true;
    }

    private static void go(MainActivity a, int s) {
        a.prefs.setSetupStep(s);
        if (s == SetupFlow.DONE) { android.util.Log.i("Lumen", "assistant: fin"); a.closeOverlay(true); return; }
        show(a, s);
    }

    // ------------------------------------------------------------------ cadre

    private static void show(final MainActivity a, int which) {
        if (which < SetupFlow.WELCOME || which > SetupFlow.LAST) return;
        if (which != SetupFlow.PROFILE) addingPerson = false;
        step = which;
        // repère pour le parcours automatique (tools/smoke.sh) : rien de personnel, juste le numéro d'étape
        android.util.Log.i("Lumen", "assistant: etape " + which);
        a.rowRefreshers.clear();
        if (ticker != null) a.h.removeCallbacks(ticker);
        FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() { if (ticker != null) a.h.removeCallbacks(ticker); shown = null; }
        });
        shown = o;
        LinearLayout card = a.glassCard(Radius.CARD);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(a.u(1000), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        cl.topMargin = cl.bottomMargin = a.u(64);
        o.addView(card, cl);
        LinearLayout col = SettingsPanel.scrollColumn(a, card, 48);
        int n = SetupFlow.number(which);
        if (n > 0) {
            TextView st = a.text(20, "sans-serif-medium", Palette.TEXT_3);
            st.setText(L.t("Étape ") + n + L.t(" sur ") + SetupFlow.count());
            st.setPadding(a.u(32), 0, a.u(32), a.u(6));
            col.addView(st);
        }
        View focus;
        switch (which) {
            case SetupFlow.WELCOME: focus = welcome(a, col); break;
            case SetupFlow.PROFILE: focus = addingPerson ? addPerson(a, col) : profile(a, col); break;
            case SetupFlow.LOOK: focus = look(a, col); break;
            case SetupFlow.BUTTONS: focus = buttons(a, col); break;
            case SetupFlow.KEYS: focus = keys(a, col); break;
            default: focus = ready(a, col); break;
        }
        if (focus != null) focus.requestFocus();
    }

    private static void title(MainActivity a, LinearLayout col, String t, String sub) {
        TextView title = a.text(48, "sans-serif-light", Palette.TEXT);
        title.setText(t);
        title.setPadding(a.u(32), 0, a.u(32), a.u(10));
        col.addView(title);
        if (sub != null) {
            TextView s = a.text(24, "sans-serif", Palette.TEXT_2);
            s.setText(sub);
            s.setLineSpacing(0, 1.25f);
            s.setPadding(a.u(32), 0, a.u(32), a.u(26));
            col.addView(s);
        }
    }

    private static void hint(MainActivity a, LinearLayout col, String text) {
        TextView t = a.text(21, "sans-serif", Palette.TEXT_3);
        t.setText(text);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(a.u(32), a.u(24), a.u(32), 0);
        col.addView(t);
    }

    /** Ligne « Continuer » (ou autre) en bas de chaque étape. */
    private static LinearLayout nextRow(final MainActivity a, LinearLayout col, String label, final Runnable before) {
        col.addView(a.menuDivider());
        LinearLayout r = a.iconRow(Glyph.PLAY, label, null, new View.OnClickListener() {
            @Override public void onClick(View v) { if (before != null) before.run(); go(a, SetupFlow.next(step)); }
        }, false);
        col.addView(r, a.rowLp());
        return r;
    }

    // ------------------------------------------------------------------ 0. accueil

    private static View welcome(final MainActivity a, LinearLayout col) {
        title(a, col, L.t("Bienvenue sur Lumen"), L.t("Comment veux-tu commencer ?"));
        LinearLayout fresh = a.iconRow(Glyph.SPARK, L.t("Démarrer de zéro"), new MainActivity.Value() {
            @Override public String get() { return L.t("Profil, accueil, clés"); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { go(a, SetupFlow.PROFILE); }
        }, false);
        col.addView(fresh, a.rowLp());
        col.addView(a.iconRow(Glyph.TV, L.t("Me synchroniser avec une autre TV du foyer"), new MainActivity.Value() {
            @Override public String get() { return L.t("Copier ses réglages"); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { syncFromOtherTv(a); }
        }, false), a.rowLp());
        hint(a, col, L.t("Se synchroniser : sur une TV de la maison qui a déjà Lumen, ouvre Réglages › Système › Autres TV, choisis cette TV et tape le code qu'elle va afficher. Fond, accueil, boutons, favoris, sons, ville de la météo et clés arrivent d'un coup, chiffrés."));
        col.addView(a.menuDivider());
        col.addView(a.listRow(L.t("Plus tard"), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                go(a, SetupFlow.DONE);
                a.showNotice(L.t("L'assistant reste dans Réglages › Système"), 3500);
            }
        }), a.rowLp());
        return fresh;
    }

    /** Réception des réglages d'une autre TV ; appliqués : il ne reste que les clés. Sinon, retour à l'accueil de l'assistant. */
    private static void syncFromOtherTv(final MainActivity a) {
        SyncPanel.openReceive(a, new SyncPanel.Done() {
            @Override public void on(boolean applied) {
                if (applied) { a.prefs.setSetupStep(SetupFlow.afterSync()); return; } // l'écran se reconstruit, l'assistant reprend aux clés
                if (a.overlay == null) show(a, SetupFlow.WELCOME);
            }
        });
    }

    // ------------------------------------------------------------------ 1. profil

    private static EditText field(MainActivity a, String hint, String value) {
        EditText in = new EditText(a);
        in.setSingleLine(true);
        in.setHint(hint);
        in.setText(value);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        in.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32 * a.unit);
        in.setTextColor(Palette.ON_SELECTED);
        in.setHintTextColor(Palette.ON_SELECTED_2);
        in.setPadding(a.u(28), a.u(20), a.u(28), a.u(20));
        in.setBackground(a.fieldBackground());
        in.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        in.setSelection(in.getText().length());
        return in;
    }

    private static void addField(MainActivity a, LinearLayout col, final EditText in, final Runnable onDone) {
        in.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent ev) { onDone.run(); return true; }
        });
        in.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (code != KeyEvent.KEYCODE_ENTER && code != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
                if (e.getAction() == KeyEvent.ACTION_UP) onDone.run();
                return true;
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(a.u(32), 0, a.u(32), a.u(20));
        col.addView(in, lp);
    }

    /** Prénom du profil principal tel qu'enregistré (vide s'il s'appelle encore « Principal »). */
    private static String mainName(MainActivity a) {
        for (Profiles.Profile p : a.prefs.profiles()) if (p.id == Profiles.MAIN) return "Principal".equals(p.name) ? "" : p.name;
        return "";
    }

    private static boolean saveMainName(MainActivity a, EditText in) {
        String typed = in.getText().toString().trim();
        CityPanel.hideKeyboard(a, in);
        if (typed.isEmpty()) return true; // facultatif : « Principal » reste
        String clean = Profiles.cleanName(typed);
        if (clean == null || clean.isEmpty() || !a.prefs.renameProfile(Profiles.MAIN, clean)) {
            a.showNotice(L.t("Prénom refusé : lettres et chiffres, 20 caractères au plus"));
            return false;
        }
        return true;
    }

    private static View profile(final MainActivity a, LinearLayout col) {
        title(a, col, L.t("Qui regarde cette TV ?"), L.t("Ton prénom crée ton profil : tes favoris, ton fond et tes réglages. Chaque personne de la maison peut avoir le sien."));
        final EditText in = field(a, L.t("Ton prénom"), mainName(a));
        final Runnable cont = new Runnable() {
            @Override public void run() { if (saveMainName(a, in)) go(a, SetupFlow.next(SetupFlow.PROFILE)); }
        };
        addField(a, col, in, cont);
        List<Profiles.Profile> all = a.prefs.profiles();
        if (all.size() > 1) {
            StringBuilder b = new StringBuilder();
            for (Profiles.Profile p : all) if (p.id != Profiles.MAIN) b.append(b.length() == 0 ? "" : ", ").append(p.name);
            TextView others = a.text(22, "sans-serif", Palette.TEXT_2);
            others.setText(L.t("Aussi : ") + b);
            others.setPadding(a.u(32), 0, a.u(32), a.u(16));
            col.addView(others);
        }
        col.addView(a.iconRow(Glyph.PERSON, L.t("Ajouter une autre personne"), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!saveMainName(a, in)) return;
                if (a.prefs.profiles().size() >= Profiles.MAX) { a.showNotice(L.t("Nombre maximal de profils atteint")); return; }
                addingPerson = true;
                show(a, SetupFlow.PROFILE);
            }
        }, false), a.rowLp());
        col.addView(a.menuDivider());
        col.addView(a.iconRow(Glyph.PLAY, L.t("Continuer"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { cont.run(); }
        }, false), a.rowLp());
        hint(a, col, L.t("Facultatif : sans prénom, le profil s'appelle « Principal ». Profils enfants et code PIN : Réglages › Profils, plus tard."));
        return in;
    }

    private static View addPerson(final MainActivity a, LinearLayout col) {
        title(a, col, L.t("Une autre personne"), L.t("Son prénom : elle aura ses propres favoris et réglages. Au démarrage, Lumen demandera « Qui regarde ? »."));
        final EditText in = field(a, L.t("Prénom"), "");
        final Runnable add = new Runnable() {
            @Override public void run() {
                String clean = Profiles.cleanName(in.getText().toString().trim());
                CityPanel.hideKeyboard(a, in);
                if (clean == null || clean.isEmpty()) { a.showNotice(L.t("Tape un prénom")); return; }
                if (a.prefs.addProfile(clean) < 0) { a.showNotice(L.t("Prénom vide ou déjà pris")); return; }
                a.updateProfileButton();
                addingPerson = false;
                show(a, SetupFlow.PROFILE);
                a.showNotice(L.t("Profil ajouté : ") + clean);
            }
        };
        addField(a, col, in, add);
        col.addView(a.iconRow(Glyph.PERSON, L.t("Ajouter"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { add.run(); }
        }, false), a.rowLp());
        col.addView(a.listRow(L.t("Annuler"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { addingPerson = false; show(a, SetupFlow.PROFILE); }
        }), a.rowLp());
        return in;
    }

    // ------------------------------------------------------------------ 2. accueil

    private static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    /** Reconstruit l'écran (langue, taille du texte) puis rouvre l'assistant à la même étape. */
    private static void rebuild(MainActivity a) {
        reopenAfterRebuild = true;
        a.recreate();
    }

    private static View look(final MainActivity a, LinearLayout col) {
        title(a, col, L.t("Ton accueil"), L.t("◀ ▶ pour changer : tout s'applique aussitôt, derrière cette fenêtre."));
        LinearLayout first = a.ui.choiceRow(L.t("Langue"), Lang.NAMES, new GlassUi.IntValue() {
            @Override public int get() { return Math.max(0, java.util.Arrays.asList(Lang.CODES).indexOf(a.prefs.lang())); }
        }, new GlassUi.IntSetter() {
            @Override public void set(int i) { a.prefs.setLang(Lang.CODES[i]); rebuild(a); }
        });
        col.addView(first, a.rowLp());
        col.addView(a.ui.choiceRow(L.t("Fond d'écran"), L.all(Prefs.SRC_NAMES), new GlassUi.IntValue() {
            @Override public int get() { return a.prefs.source(); }
        }, new GlassUi.IntSetter() {
            @Override public void set(int i) { a.prefs.setSource(i); SettingsPanel.applyWallChange(a); }
        }), a.rowLp());
        final int[] styles = {Prefs.TILE_WORD, Prefs.TILE_MONO, Prefs.TILE_BANNER};
        col.addView(a.ui.choiceRow(L.t("Vignettes"), new String[]{L.t("Nom de l'appli"), L.t("Icône et nom"), L.t("Bannières officielles")}, new GlassUi.IntValue() {
            @Override public int get() { return indexOf(styles, a.prefs.tileStyle()); }
        }, new GlassUi.IntSetter() {
            @Override public void set(int i) { a.prefs.setTileStyle(styles[i]); a.dirtyRow = true; a.markAppsDirty(); }
        }), a.rowLp());
        col.addView(a.ui.choiceRow(L.t("Applis par ligne"), new String[]{"3", "4", "5", "6", "7"}, new GlassUi.IntValue() {
            @Override public int get() { return a.prefs.homeCols() - 3; }
        }, new GlassUi.IntSetter() {
            @Override public void set(int i) { a.prefs.setHomeCols(i + 3); a.dirtyRow = true; }
        }), a.rowLp());
        col.addView(a.ui.choiceRow(L.t("Taille du texte"), L.all(Prefs.TEXT_SCALE_NAMES), new GlassUi.IntValue() {
            @Override public int get() { return a.prefs.textScale(); }
        }, new GlassUi.IntSetter() {
            @Override public void set(int i) { a.prefs.setTextScale(i); rebuild(a); }
        }), a.rowLp());
        col.addView(a.ui.toggleRow(L.t("Horloge"), new GlassUi.Bool() {
            @Override public boolean get() { return a.prefs.showClock(); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.prefs.setShowClock(!a.prefs.showClock());
                a.clockBox.setVisibility(a.prefs.showClock() ? View.VISIBLE : View.GONE);
            }
        }), a.rowLp());
        col.addView(a.ui.toggleRow(L.t("Sons de l'interface"), new GlassUi.Bool() {
            @Override public boolean get() { return a.prefs.sounds(); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setSounds(!a.prefs.sounds()); Sounds.get(a).apply(a.prefs); }
        }), a.rowLp());
        nextRow(a, col, L.t("Continuer"), null);
        return first;
    }

    // ------------------------------------------------------------------ 3. boutons

    private static View buttons(final MainActivity a, LinearLayout col) {
        title(a, col, L.t("Boutons de l'accueil"), L.t("Les boutons ronds du haut de l'accueil : garde ceux qui te servent."));
        View first = null;
        for (int i = 0; i < HomeButtons.IDS.length; i++) {
            final String id = HomeButtons.IDS[i];
            String when = HomeButtons.WHEN[i];
            LinearLayout r = a.ui.toggleRow(L.t(HomeButtons.NAMES[i]) + (when.isEmpty() ? "" : " · " + L.t(when)), new GlassUi.Bool() {
                @Override public boolean get() { return a.prefs.button(id); }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean on = !a.prefs.button(id);
                    if (HomeButtons.HOUSE.equals(id)) a.setHouseButton(on);
                    else { a.prefs.setButton(id, on); a.updateSoundButton(); }
                }
            });
            col.addView(r, a.rowLp());
            if (first == null) first = r;
        }
        nextRow(a, col, L.t("Continuer"), null);
        hint(a, col, L.t("Sans le bouton Réglages, les réglages restent là : touche Menu de la télécommande, ou ▼ puis rechercher « réglages »."));
        return first;
    }

    // ------------------------------------------------------------------ 4. clés et services

    private static View keys(final MainActivity a, LinearLayout col) {
        title(a, col, L.t("Clés et services"), L.t("Facultatif. Les clés se tapent bien mieux sur le téléphone : scanne ce code, puis onglet « Plus »."));
        String url = a.phone.connected() ? null : a.phone.start();
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(a.u(32), 0, a.u(32), a.u(18));
        if (url != null) {
            ImageView qr = new ImageView(a);
            Bitmap bm = PhonePanel.qrBitmap(a, url, a.u(260));
            qr.setImageBitmap(bm);
            qr.setContentDescription(L.t("QR code pour le téléphone"));
            qr.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline ol) { ol.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.u(Radius.QR)); }
            });
            qr.setClipToOutline(true);
            LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(a.u(260), a.u(260));
            ql.rightMargin = a.u(32);
            box.addView(qr, ql);
        }
        TextView how = a.text(22, "sans-serif", Palette.TEXT_2);
        how.setText(url != null
                ? L.t("Même Wi-Fi que la TV. Accepte le téléphone quand la TV le demande, puis dans « Plus » :\n• TMDB pour « À l'affiche » (films et séries du moment)\n• Pexels pour plus de vidéos de fond\n• Home Assistant pour les lumières de la maison\n") + url.replaceFirst("#.*$", "")
                : a.phone.connected() ? L.t("Téléphone connecté : onglet « Plus » pour TMDB, Pexels et Home Assistant.") : L.t("Pas de connexion réseau : les clés pourront se régler plus tard depuis le téléphone."));
        how.setLineSpacing(0, 1.25f);
        box.addView(how, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(box);
        col.addView(a.section(L.t("Où tu en es")));
        col.addView(a.listRow(L.t("À l'affiche (TMDB)"), new MainActivity.Value() {
            @Override public String get() {
                return !a.prefs.tmdbOwnKey().isEmpty() ? L.t("✓ Ta clé") : a.prefs.tmdbSharedOk() ? L.t("Clé partagée du projet") : L.t("Pas de clé");
            }
        }, NOTHING), a.rowLp());
        LinearLayout shared = a.ui.toggleRow(L.t("Clé partagée du projet Lumen"), new GlassUi.Bool() {
            @Override public boolean get() { return a.prefs.tmdbSharedOk(); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.prefs.setTmdbSharedOk(!a.prefs.tmdbSharedOk());
                if (a.prefs.tmdbSharedOk() && a.prefs.tmdbSharedKey().isEmpty()) new Thread(new Runnable() {
                    @Override public void run() { a.updater.refreshServices(); }
                }, "lumen-services").start();
            }
        });
        col.addView(shared, a.rowLp());
        col.addView(a.listRow(L.t("Vidéos Pexels"), new MainActivity.Value() {
            @Override public String get() { return a.prefs.pexelsKey().isEmpty() ? L.t("Pas de clé") : L.t("✓ Clé réglée"); }
        }, NOTHING), a.rowLp());
        col.addView(a.listRow(L.t("Home Assistant"), new MainActivity.Value() {
            @Override public String get() { return a.prefs.haConfigured() ? L.t("✓ Relié") : L.t("Pas relié"); }
        }, NOTHING), a.rowLp());
        col.addView(a.listRow(L.t("Téléphone"), new MainActivity.Value() {
            @Override public String get() { return a.phone.connected() ? L.t("✓ Connecté") : L.t("En attente"); }
        }, NOTHING), a.rowLp());
        LinearLayout next = nextRow(a, col, L.t("Continuer"), null);
        hint(a, col, L.t("Les clés restent sur la TV, chiffrées, et ne sont jamais renvoyées au téléphone ; vers une autre TV de la maison, seulement dans un envoi chiffré, si tu le choisis. Tout se change plus tard dans Réglages."));
        // l'état se met à jour pendant qu'on règle les clés sur le téléphone
        ticker = new Runnable() {
            @Override public void run() {
                if (!isShowing(a)) return;
                for (Runnable r : new java.util.ArrayList<>(a.rowRefreshers)) r.run();
                a.h.postDelayed(this, 2000);
            }
        };
        a.h.postDelayed(ticker, 2000);
        return next;
    }

    // ------------------------------------------------------------------ 5. prêt

    private static View ready(final MainActivity a, LinearLayout col) {
        String name = mainName(a);
        title(a, col, name.isEmpty() ? L.t("C'est prêt") : L.t("C'est prêt, ") + name, L.t("Ton accueil est réglé. Appui long sur OK sur une appli pour l'ajouter aux favoris ou la déplacer."));
        View focus;
        if (!Install.isHome(a)) {
            LinearLayout home = a.iconRow(Glyph.HOME, L.t("Faire de Lumen l'accueil de la TV"), new MainActivity.Value() {
                @Override public String get() { return L.t("Conseillé"); }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    a.prefs.setSetupStep(SetupFlow.DONE);
                    a.prefs.setInstallHelpShown(true);
                    a.closeOverlay(false);
                    SettingsPanel.openAt(a, SettingsPanel.INSTALL);
                }
            }, false);
            col.addView(home, a.rowLp());
            focus = home;
            col.addView(a.iconRow(Glyph.PLAY, L.t("Terminer"), null, new View.OnClickListener() {
                @Override public void onClick(View v) { finish(a); }
            }, false), a.rowLp());
            hint(a, col, L.t("Tant que Lumen n'est pas l'accueil, la touche Accueil ramène l'écran d'origine de la TV. La page Installation explique comment faire, pas à pas."));
        } else {
            LinearLayout end = a.iconRow(Glyph.PLAY, L.t("Terminer"), null, new View.OnClickListener() {
                @Override public void onClick(View v) { finish(a); }
            }, false);
            col.addView(end, a.rowLp());
            focus = end;
        }
        hint(a, col, L.t("L'assistant se relance depuis Réglages › Système. Une autre TV de la maison ? Réglages › Système › Autres TV copie tout vers elle."));
        return focus;
    }

    private static void finish(MainActivity a) {
        go(a, SetupFlow.DONE);
        a.showNotice(L.t("Bienvenue sur Lumen"));
    }
}

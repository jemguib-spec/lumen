// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Gestion des profils sur la TV (ajouter, renommer, supprimer : code PIN s'il existe) et
 * changement de profil. Le choix « Qui regarde ? » est dans WhoPage. Voir Profiles pour ce qui
 * est propre à chacun et ce qui est partagé.
 */
final class ProfilePanel {
    private ProfilePanel() { }

    /** Gestion des profils : code PIN d'abord, s'il existe. */
    static void openManage(final MainActivity a) {
        Runnable go = new Runnable() { @Override public void run() { manage(a); } };
        if (a.prefs.hasPin()) PinDialog.ask(a, L.t("Code pour gérer les profils"), go); else go.run();
    }

    /** Gestion des profils, déjà déverrouillée (depuis les réglages). */
    static void manage(final MainActivity a) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.menuCard(40);
        card.addView(a.menuHeader(null, false, L.t("Profils"),
                L.t("Chacun a ses favoris, ses fonds et son affichage. Clés, Maison et code PIN restent communs.")));
        LinearLayout first = null;
        for (final Profiles.Profile p : a.prefs.profiles()) {
            LinearLayout row = a.iconRow(Glyph.PERSON, p.name, new MainActivity.Value() {
                @Override public String get() { return (p.id == a.prefs.activeProfile() ? "Actif   " : "") + "›"; }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { a.closeOverlay(false); edit(a, p); }
            }, false);
            card.addView(row, a.rowLp());
            if (first == null) first = row;
        }
        card.addView(a.iconRow(Glyph.PERSON, L.t("Au démarrage"), new MainActivity.Value() {
            @Override public String get() { return a.prefs.askProfileAtStart() ? "Demander qui regarde" : "Reprendre le dernier profil"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setAskProfileAtStart(!a.prefs.askProfileAtStart()); }
        }, false), a.rowLp());
        if (a.prefs.profiles().size() < Profiles.MAX) {
            card.addView(a.menuDivider());
            card.addView(a.iconRow(Glyph.STAR, L.t("Ajouter un profil"), null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    a.closeOverlay(false);
                    askName(a, L.t("Nouveau profil"), "", new Name() {
                        @Override public boolean take(String n) {
                            int id = a.prefs.addProfile(n);
                            if (id < 0) return false;
                            a.updateProfileButton();
                            a.showNotice(L.t("Profil « ") + Profiles.cleanName(n) + L.t(" » ajouté"));
                            return true;
                        }
                    });
                }
            }, false), a.rowLp());
        }
        o.addView(card, new FrameLayout.LayoutParams(a.u(820), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        if (first != null) first.requestFocus();
    }

    private static void edit(final MainActivity a, final Profiles.Profile p) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.menuCard(40);
        WhoPage.Face face = new WhoPage.Face(a, a.prefs.avatarOf(p.id), p.name, 48);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(a.u(112), a.u(112));
        fl.gravity = Gravity.CENTER_HORIZONTAL;
        fl.topMargin = a.u(8);
        card.addView(face.view, fl);
        card.addView(a.menuHeader(null, false, p.name, p.id == Profiles.MAIN ? L.t("Profil principal") : p.kid ? L.t("Profil enfant") : null));
        LinearLayout use = a.iconRow(Glyph.PERSON, L.t("Utiliser ce profil"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(true); switchTo(a, p.id); }
        }, false);
        card.addView(use, a.rowLp());
        if (p.id != Profiles.MAIN) {
            card.addView(a.iconRow(p.kid ? Glyph.UNLOCK : Glyph.LOCK, L.t("Profil enfant"), new MainActivity.Value() {
                @Override public String get() { return a.prefs.isKidProfile(p.id) ? "Oui · seulement ses favoris" : "Non"; }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    final boolean on = !a.prefs.isKidProfile(p.id);
                    final Runnable apply = new Runnable() {
                        @Override public void run() {
                            a.prefs.setProfileKid(p.id, on);
                            a.updateProfileButton();
                            if (p.id == a.prefs.activeProfile()) { a.dirtyRow = true; a.markAppsDirty(); }
                            a.showNotice(on ? L.t("Profil enfant : seules ses applis favorites sont visibles · code PIN pour le reste")
                                    : L.t("Profil enfant désactivé"));
                            for (Runnable r : new java.util.ArrayList<>(a.rowRefreshers)) r.run();
                        }
                    };
                    // un profil enfant n'a de sens qu'avec un code PIN : on le crée d'abord s'il manque
                    if (on && !a.prefs.hasPin()) { a.closeOverlay(false); PinDialog.create(a, apply); }
                    else apply.run();
                }
            }, false), a.rowLp());
        }
        card.addView(a.iconRow(Glyph.IMAGE, L.t("Image du profil"), new MainActivity.Value() {
            @Override public String get() {
                int i = a.prefs.avatarOf(p.id);
                return (AvatarArt.valid(i) ? L.t(AvatarArt.NAMES[i]) : L.t("Initiale")) + "   ›";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(false); pickAvatar(a, p); }
        }, false), a.rowLp());
        card.addView(a.iconRow(Glyph.DOC, L.t("Renommer"), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.closeOverlay(false);
                askName(a, L.t("Renommer"), p.name, new Name() {
                    @Override public boolean take(String n) { return a.prefs.renameProfile(p.id, n); }
                });
            }
        }, false), a.rowLp());
        if (p.id != Profiles.MAIN) {
            card.addView(a.menuDivider());
            card.addView(a.iconRow(Glyph.TRASH, L.t("Supprimer ce profil et ses réglages"), null, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!remove(a, p.id)) return;
                    a.closeOverlay(true);
                }
            }, true), a.rowLp());
        }
        o.addView(card, new FrameLayout.LayoutParams(a.u(760), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        use.requestFocus();
    }

    /** Choix de l'image du profil : l'initiale ou l'un des dessins de Lumen (voir AvatarArt). */
    static void pickAvatar(final MainActivity a, final Profiles.Profile p) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.menuCard(40);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.addView(a.menuHeader(null, false, L.t("Image de ") + p.name, null));
        final TextView name = a.text(26, "sans-serif", Palette.TEXT_2);
        name.setGravity(Gravity.CENTER);
        final int current = a.prefs.avatarOf(p.id);
        final int perRow = 6, size = a.u(112), gap = a.u(16);
        View focus = null;
        LinearLayout row = null;
        for (int k = 0; k <= AvatarArt.count(); k++) {
            final int idx = k - 1; // d'abord l'initiale, puis les dessins
            if (k % perRow == 0) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rl.topMargin = k == 0 ? 0 : gap;
                card.addView(row, rl);
            }
            final WhoPage.Face f = new WhoPage.Face(a, idx, p.name, 48);
            final String label = AvatarArt.valid(idx) ? L.t(AvatarArt.NAMES[idx]) : L.t("Initiale");
            f.view.setFocusable(true);
            f.view.setClickable(true);
            f.view.setSoundEffectsEnabled(false);
            f.view.setContentDescription(label);
            f.view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean on) {
                    f.setSelected(on);
                    v.animate().scaleX(on ? 1.12f : 1f).scaleY(on ? 1.12f : 1f).setDuration(Motion.FAST).start();
                    v.setElevation(on ? a.u(14) : 0);
                    if (on) { name.setText(label); Sounds.get(a).tick(); }
                }
            });
            f.view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Sounds.get(a).tock();
                    a.prefs.setAvatar(p.id, idx);
                    a.updateProfileButton();
                    a.closeOverlay(false);
                    edit(a, p);
                }
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(size, size);
            if (k % perRow != 0) l.leftMargin = gap;
            row.addView(f.view, l);
            if (idx == current || focus == null) focus = f.view;
        }
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = a.u(26);
        card.addView(name, nl);
        o.addView(card, new FrameLayout.LayoutParams(a.u(1000), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        focus.requestFocus();
    }

    interface Name { boolean take(String name); }

    /** Saisie d'un prénom (clavier de la TV). */
    private static void askName(final MainActivity a, String title, String initial, final Name done) {
        FrameLayout o = a.openOverlay(null);
        LinearLayout card = a.formCard(44, true);
        TextView t = a.text(44, "sans-serif-light", Palette.TEXT);
        t.setText(title);
        t.setPadding(0, 0, 0, a.u(28));
        card.addView(t);
        final android.widget.EditText in = new android.widget.EditText(a);
        in.setSingleLine(true);
        in.setText(initial);
        in.setSelection(in.length());
        in.setHint(L.t("Prénom"));
        in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(Profiles.MAX_NAME)});
        in.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30 * a.unit);
        in.setTypeface(Fonts.get("sans-serif", android.graphics.Typeface.NORMAL));
        in.setTextColor(Palette.ON_SELECTED);
        in.setHintTextColor(Palette.ON_SELECTED_2);
        in.setPadding(a.u(28), a.u(20), a.u(28), a.u(20));
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        in.setBackground(a.fieldBackground());
        in.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        final Runnable save = new Runnable() {
            @Override public void run() {
                if (done.take(in.getText().toString())) { a.closeOverlay(true); manage(a); }
                else a.showNotice(L.t("Prénom vide ou déjà pris"));
            }
        };
        in.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent ev) { save.run(); return true; }
        });
        card.addView(in, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(new View(a), new LinearLayout.LayoutParams(1, a.u(20)));
        card.addView(a.iconRow(Glyph.UNLOCK, L.t("Enregistrer"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { save.run(); }
        }, false), a.rowLp());
        o.addView(card, new FrameLayout.LayoutParams(a.u(760), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        in.requestFocus();
    }

    /** Supprime un profil et ses réglages ; si c'était l'actif, on revient au principal. */
    static boolean remove(MainActivity a, int id) {
        Profiles.Profile p = Profiles.find(a.prefs.profiles(), id);
        boolean wasActive = id == a.prefs.activeProfile();
        float scale = a.textScale;
        boolean contrast = a.highContrast;
        if (p == null || !a.prefs.removeProfile(id)) return false;
        a.showNotice(L.t("Profil « ") + p.name + L.t(" » supprimé"));
        if (wasActive) reload(a, scale, contrast); else a.updateProfileButton();
        return true;
    }

    /** Passe au profil « id » et réaffiche tout avec ses réglages. */
    static boolean switchTo(MainActivity a, int id) { return switchTo(a, id, false); }

    /** Quitter un profil enfant demande le code PIN ; sinon changement direct. */
    static void switchGuarded(final MainActivity a, final int id, final Runnable then) {
        if (a.prefs.isKidProfile() && id != a.prefs.activeProfile() && a.prefs.hasPin()) {
            PinDialog.ask(a, L.t("Code pour quitter le profil enfant"), then);
        } else then.run();
    }

    /** quiet : sans le message « Profil : … » (la page « Qui regarde ? » dit déjà bonjour). */
    static boolean switchTo(MainActivity a, int id, boolean quiet) {
        if (id == a.prefs.activeProfile()) return true;
        float scale = a.textScale;
        boolean contrast = a.highContrast;
        if (!a.prefs.switchProfile(id)) return false;
        if (!quiet) a.showNotice(L.t("Profil : ") + a.prefs.activeProfileName());
        reload(a, scale, contrast);
        return true;
    }

    /** Passer au profil « id » oblige-t-il à reconstruire tout l'écran (taille du texte, contraste, langue) ? */
    static boolean needsRebuild(MainActivity a, int id) {
        if (id == a.prefs.activeProfile()) return false;
        return Prefs.TEXT_SCALES[a.prefs.textScaleOf(id)] != a.textScale || a.prefs.highContrastOf(id) != a.highContrast
                || !L.choose(a.prefs.langOf(id), java.util.Locale.getDefault().getLanguage()).equals(L.lang());
    }

    /**
     * Réaffiche l'accueil avec les réglages du profil actif. Si la taille du texte ou le contraste
     * changent, l'écran est entièrement reconstruit ; sinon tout est rafraîchi sur place (un
     * téléphone connecté le reste).
     */
    private static void reload(MainActivity a, float oldScale, boolean oldContrast) {
        String before = L.lang();
        Lang.init(a, a.prefs.lang());
        if (Prefs.TEXT_SCALES[a.prefs.textScale()] != oldScale || a.prefs.highContrast() != oldContrast || !before.equals(L.lang())) {
            a.recreate();
            return;
        }
        a.ensureDefaultFavorites();
        a.addInputsOnce();
        a.phoneHost.onSettingsChangedFromPhone(false);
        SettingsPanel.applyWallChange(a);
        a.buildRow(null);
        a.markAppsDirty();
        a.updateProfileButton();
    }
}

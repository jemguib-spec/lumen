// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Ce que la TV fait à la demande du téléphone (PhoneServer.Host) : touches, applis, texte,
 * ambiances, veille, fonds, réglages, acceptation d'un nouveau téléphone, pastille « connecté ».
 * Tout s'exécute sur le fil principal, dans MainActivity.
 */
final class PhoneBridge implements PhoneServer.Host {
    private final MainActivity a;

    PhoneBridge(MainActivity a) { this.a = a; }

    @Override public void phoneRestart() { a.restartLumenNicely(); }

    @Override public void onSettingsChangedFromPhone(boolean wallChanged) {
        if (wallChanged) a.wall.restart();
        Sounds.get(a).setEnabled(a.prefs.sounds());
        a.clockBox.setVisibility(a.prefs.showClock() ? View.VISIBLE : View.GONE);
        if (a.houseButton != null) a.houseButton.setVisibility(a.prefs.haButton() ? View.VISIBLE : View.GONE);
        a.refreshGreeting();
        a.updateWeatherFx();
        a.wake(); // le délai du mode veille repart avec le nouveau réglage
    }

    /**
     * Un téléphone vient de scanner le QR code : rien ne se passe tant que quelqu'un devant la TV
     * ne l'a pas accepté (une photo du QR code ne suffit donc pas). Sans réponse en 60 s : refusé.
     */
    @Override public void askPhoneApproval(String name, final PhoneServer.Approval cb) {
        final boolean[] done = {false};
        final Runnable timeout = new Runnable() {
            @Override public void run() { if (!done[0]) { done[0] = true; a.closeOverlay(true); cb.answer(false, false); a.showNotice("Téléphone refusé (pas de réponse)"); } }
        };
        FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() { a.h.removeCallbacks(timeout); if (!done[0]) { done[0] = true; cb.answer(false, false); } }
        });
        LinearLayout card = a.glassCard(44);
        card.setPadding(a.u(24), a.u(40), a.u(24), a.u(24));
        card.addView(a.menuHeader(null, false, L.t("Nouveau téléphone"), name));
        TextView why = a.text(22, "sans-serif", 0x99FFFFFF);
        why.setText(L.t("Il pourra piloter la TV, ouvrir des applis et écrire du texte. Accepte seulement si c'est le tien."));
        why.setGravity(Gravity.CENTER);
        why.setPadding(a.u(24), 0, a.u(24), a.u(24));
        card.addView(why);
        LinearLayout allow = a.iconRow(Glyph.UNLOCK, L.t("Autoriser"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { done[0] = true; a.h.removeCallbacks(timeout); a.closeOverlay(true); cb.answer(true, false); }
        }, false);
        card.addView(allow, a.rowLp());
        card.addView(a.iconRow(Glyph.STAR, L.t("Autoriser et se souvenir de ce téléphone"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { done[0] = true; a.h.removeCallbacks(timeout); a.closeOverlay(true); cb.answer(true, true); }
        }, false), a.rowLp());
        card.addView(a.menuDivider());
        card.addView(a.iconRow(Glyph.CLOSE, L.t("Refuser"), null, new View.OnClickListener() {
            @Override public void onClick(View v) { done[0] = true; a.h.removeCallbacks(timeout); a.closeOverlay(true); cb.answer(false, false); a.showNotice("Téléphone refusé"); }
        }, true), a.rowLp());
        o.addView(card, new FrameLayout.LayoutParams(a.u(760), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        allow.requestFocus();
        a.h.postDelayed(timeout, 60_000);
    }

    /** Depuis le téléphone : retour sur Lumen puis fermeture de l'appli ouverte. */
    @Override public String phoneCloseApp() {
        final AppRepo.App app = a.lastLaunched;
        if (app == null) return "{\"ok\":false,\"none\":true}";
        if (!a.resumedNow) {
            if (!RemoteService.perform(RemoteService.HOME)) {
                try { a.startActivity(new Intent(a, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
            }
        }
        a.h.postDelayed(new Runnable() {
            @Override public void run() {
                if (a.closeApp(app)) { a.showNotice(app.label + L.t(" fermée")); a.lastLaunched = null; }
            }
        }, 1500);
        return "{\"ok\":true,\"name\":" + org.json.JSONObject.quote(app.label) + "}";
    }

    @Override public AppRepo repo() { return a.repo; }

    @Override public Prefs prefs() { return a.prefs; }

    @Override public Online online() { return a.online; }

    @Override public void onFavoritesChanged() {
        if (a.overlay == null && !a.appsOpen) a.buildRow(a.focusedKey()); else a.dirtyRow = true;
        a.markAppsDirty();
        a.showNotice(L.t("Favoris mis à jour"));
    }

    @Override public boolean onTvHome() { return a.resumedNow; }

    @Override public String phoneKey(String k) { return PhoneRemote.press(a, k); }

    @Override public void phoneLaunch(AppRepo.App app) {
        if (a.ambiance != null) a.closeAmbiance();
        if (a.ambientOn) a.exitAmbient(false);
        a.closeOverlay(false);
        a.launch(app);
    }

    @Override public void phoneText(String text) {
        if (a.ambientOn) a.exitAmbient(false);
        View f = a.getCurrentFocus();
        if (text.isEmpty() && !(f instanceof android.widget.EditText)) { if (a.appsOpen) a.appsPage.setQuery(""); return; }
        if (f instanceof android.widget.EditText) { // champ de Lumen déjà ouvert (recherche, ville)
            android.widget.EditText e = (android.widget.EditText) f;
            e.setText(text);
            e.setSelection(e.length());
            return;
        }
        if (a.ambiance != null) a.closeAmbiance();
        a.closeOverlay(false);
        a.openApps();
        a.appsPage.typeFromPhone(text);
    }

    @Override public void phoneAmbiance(int kind) {
        if (kind < 0) { a.closeAmbiance(); return; }
        if (a.ambientOn) a.exitAmbient(false);
        a.prefs.setAmbianceKind(kind);
        if (a.ambiance == null) a.openAmbiance(); else a.ambiance.selectKind(kind);
    }

    @Override public boolean phoneTimer(int minutes) {
        if (a.ambiance == null) return false;
        return a.ambiance.setTimerMinutes(minutes);
    }

    @Override public int[] ambianceState() {
        return a.ambiance == null ? new int[]{-1, 0, 0} : new int[]{a.ambiance.kind(), a.ambiance.timerLeftMinutes(), a.ambiance.timerChoice()};
    }

    @Override public void phoneVeille(boolean on) {
        if (on && !a.ambientOn) { if (a.ambiance != null) a.closeAmbiance(); a.closeOverlay(false); a.enterAmbient(); }
        else if (!on && a.ambientOn) a.exitAmbient(true);
    }

    @Override public boolean veilleOn() { return a.ambientOn; }

    @Override public void phoneSource(int source) {
        a.prefs.setSource(source);
        SettingsPanel.applyWallChange(a);
    }

    @Override public void phoneNextWall() { if (a.ambientOn && a.veilleOwn) a.veilleWall.next(); else a.wall.next(); }

    /** Plusieurs vidéos cochées d'affilée sur le téléphone : un seul rafraîchissement, 3 s après la dernière. */
    private String picksScope;

    private final Runnable picksRefresh = new Runnable() {
        @Override public void run() {
            if (Online.SCOPE_COSY.equals(picksScope)) a.online.refreshCosy();
            else { a.online.refreshIfNeeded(Online.VIDEO, true); a.wall.restart(); }
        }
    };

    @Override public void phonePicksChanged(String scope) {
        picksScope = scope;
        a.h.removeCallbacks(picksRefresh);
        a.h.postDelayed(picksRefresh, 3000);
    }

    private View phonePill;

    /**
     * Simple pastille verte tout en haut à gauche tant qu'un téléphone est connecté,
     * au-dessus du nom du fond (sans texte, pour ne rien chevaucher).
     */
    @Override public void onPhoneConnected(boolean connected) {
        if (connected && phonePill == null) {
            phonePill = new View(a);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xFF34C759);
            bg.setStroke(Math.max(1, a.u(2)), 0x66FFFFFF);
            phonePill.setBackground(bg);
            phonePill.setElevation(a.u(4));
            phonePill.setContentDescription(L.t("Téléphone connecté"));
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(a.u(16), a.u(16), Gravity.TOP | Gravity.START);
            pl.leftMargin = a.u(112);
            pl.topMargin = a.u(36);
            a.root.addView(phonePill, a.root.indexOfChild(a.notice), pl);
            phonePill.setAlpha(0f);
            phonePill.setScaleX(0.3f);
            phonePill.setScaleY(0.3f);
            phonePill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
            pulse(pl, 0);
            pulse(pl, 520);
            a.showNotice(L.t("Contrôlée par ") + a.phone.deviceName());
        } else if (!connected && phonePill != null) {
            final View pp = phonePill;
            phonePill = null;
            pp.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                @Override public void run() { a.root.removeView(pp); }
            }).start();
        }
    }

    @Override public void onHaLinked() {
        if (!a.prefs.haButton()) { a.setHouseButton(true); a.showNotice(L.t("Bouton Maison ajouté à l'accueil")); }
    }

    @Override public void onPhoneName(String name) { if (phonePill != null) a.showNotice("Contrôlée par " + name); }

    /** Onde verte qui s'élargit autour de la pastille (connexion du téléphone). */
    private void pulse(FrameLayout.LayoutParams at, long delay) {
        final View ring = new View(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(Math.max(1, a.u(2)), 0xFF34C759);
        ring.setBackground(g);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(at.width, at.height, at.gravity);
        rl.leftMargin = at.leftMargin;
        rl.topMargin = at.topMargin;
        a.root.addView(ring, a.root.indexOfChild(a.notice), rl);
        ring.setAlpha(0.9f);
        ring.animate().scaleX(3.4f).scaleY(3.4f).alpha(0f).setStartDelay(delay).setDuration(900)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .withEndAction(new Runnable() { @Override public void run() { a.root.removeView(ring); } }).start();
    }

    @Override public void onSettingsRestored(int count) {
        a.showNotice(count + L.t(" réglages restaurés"));
        a.h.postDelayed(new Runnable() { @Override public void run() { a.recreate(); } }, 1200); // tout est relu proprement
    }

    @Override public void onMineChanged(int received) {
        if (received > 0 && a.prefs.source() != Prefs.SRC_MINE) {
            a.prefs.setSource(Prefs.SRC_MINE);
            a.wall.restart();
        } else {
            a.wall.refreshList();
            if (a.prefs.source() == Prefs.SRC_MINE && a.wall.usingFallback()) a.wall.restart();
        }
        if (received > 0) a.showNotice(L.t("Reçu depuis le téléphone"));
    }

    @Override public String phoneProfile(String action, int id, String name) {
        boolean ok;
        switch (action) {
            case "use": ok = !a.prefs.isKidProfile() && ProfilePanel.switchTo(a, id); break; // quitter un profil enfant : sur la TV, avec le code
            case "add": ok = a.prefs.addProfile(name) >= 0; if (ok) a.updateProfileButton(); break;
            case "rename": ok = a.prefs.renameProfile(id, name); break;
            case "delete": ok = ProfilePanel.remove(a, id); break;
            default: return "{\"ok\":false}";
        }
        return ok ? "{\"ok\":true}" : "{\"ok\":false,\"error\":\"Prénom vide ou déjà pris\"}";
    }
}

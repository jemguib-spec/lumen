// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;

/** Mises à jour et redémarrage : proposer la version prête, l'installer derrière l'écran « lumen », puis l'annoncer au retour. */
final class UpdateFlow {
    private final MainActivity a;

    UpdateFlow(MainActivity a) { this.a = a; }

    /** Une mise à jour vérifiée est prête : on le dit, puis Android demande confirmation (une fois par version). */
    void updateStateChanged() {
        for (Runnable r : new ArrayList<>(a.rowRefreshers)) r.run();
        if (a.status != null) a.status.refresh();
        final String v = a.updater.readyVersion();
        if (v.isEmpty() || v.equals(a.prefs.updatePrompted())) return;
        a.showNotice(L.t("Mise à jour ") + v + L.t(" prête"));
        a.h.postDelayed(new Runnable() {
            @Override public void run() {
                if (a.overlay != null || a.ambientOn || a.ambiance != null || !a.hasWindowFocus()) return; // on redemandera au prochain retour
                a.prefs.setUpdatePrompted(v);
                installUpdate();
            }
        }, 2500);
    }

    /**
     * Redémarrage propre : écran « lumen · redémarrage », puis Lumen s'arrête et repart aussitôt ;
     * au retour, l'écran de démarrage enchaîne sans coupure.
     */
    void restartLumenNicely() {
        a.closeOverlay(false);
        a.lumenScreen(L.t("Redémarrage"));
        a.prefs.setRestarting(true);
        a.h.postDelayed(new Runnable() { @Override public void run() { a.restartLumen(); } }, 1400);
    }

    /**
     * Installe la mise à jour derrière un écran « lumen · mise à jour » : pendant qu'Android
     * remplace Lumen, il ne peut rien afficher ; dès que c'est fait, Lumen revient de lui-même
     * (réception de « paquet remplacé » ou service « Lumen télécommande ») au lieu de laisser
     * l'écran d'accueil d'origine de la TV.
     */
    void installUpdate() {
        String v = a.updater.readyVersion();
        a.prefs.setUpdatePending(a.updater.currentVersion());
        FrameLayout z = a.lumenScreen(L.t("Mise à jour") + (v.isEmpty() ? "" : " · " + v));
        a.systemVeil = z; // repart tout seul si la mise à jour est annulée (Lumen reprend la main)
        a.veilApp = null;
        final View veil = z;
        if (!a.updater.install()) { a.prefs.setUpdatePending(""); a.hideSystemVeil(true); a.showNotice(L.t("Installation impossible")); return; }
        // filet de sécurité : si aucune fenêtre d'Android ne s'est ouverte, on rend la main
        a.h.postDelayed(new Runnable() {
            @Override public void run() {
                if (a.systemVeil == veil && a.hasWindowFocus()) { a.prefs.setUpdatePending(""); a.hideSystemVeil(true); }
            }
        }, 10_000);
    }

    /** Au démarrage : Lumen vient-il d'être mis à jour ? On le dit, simplement. */
    void announceUpdate() {
        String from = a.prefs.updatePending();
        if (from.isEmpty()) return;
        String now = a.updater.currentVersion();
        if (!from.equals(now)) {
            a.prefs.setUpdatePending("");
            String notes = a.prefs.updateNotes(now);
            a.showNotice(L.t("Lumen est à jour · ") + now + (notes.isEmpty() ? "" : L.t(" · Réglages › Système › Nouveautés")));
        } else if (!hasUpdateVeil()) {
            a.prefs.setUpdatePending(""); // installation annulée
        }
    }

    boolean hasUpdateVeil() { return a.systemVeil != null; }
}

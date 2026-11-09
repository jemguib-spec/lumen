// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

/**
 * Télécommande du téléphone dans les autres applis (facultatif, à activer par l'utilisateur dans
 * Paramètres › Accessibilité). Ce service ne lit rien à l'écran et ne reçoit aucun événement :
 * il sait seulement appuyer sur Retour, Accueil et, à partir d'Android 13, sur les flèches et OK.
 * Les touches viennent uniquement de la page du téléphone (session authentifiée, liste fermée).
 */
public class RemoteService extends AccessibilityService {
    // Actions globales d'Android (constantes absentes du SDK 23 utilisé pour compiler)
    static final int BACK = 1, HOME = 2, DPAD_UP = 16, DPAD_DOWN = 17, DPAD_LEFT = 18, DPAD_RIGHT = 19, DPAD_CENTER = 20;

    private static volatile RemoteService instance;

    static boolean connected() { return instance != null; }

    /** Flèches et OK dans les autres applis : Android 13 (API 33) et plus. */
    static boolean dpadSupported() { return Build.VERSION.SDK_INT >= 33; }

    /** Appuie sur une touche globale ; false si le service n'est pas actif ou si la TV ne le permet pas. */
    static boolean perform(int action) {
        RemoteService s = instance;
        if (s == null) return false;
        if (action >= DPAD_UP && !dpadSupported()) return false;
        try { return s.performGlobalAction(action); } catch (Exception e) { return false; }
    }

    @Override protected void onServiceConnected() { instance = this; }
    @Override public boolean onUnbind(android.content.Intent i) { instance = null; return super.onUnbind(i); }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}

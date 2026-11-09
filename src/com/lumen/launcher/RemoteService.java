// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Télécommande du téléphone dans les autres applis (facultatif, à activer par l'utilisateur dans
 * Paramètres › Accessibilité). Il ne traite aucun événement et ne lit rien de lui-même :
 * il appuie sur Retour, Accueil, Veille et, à partir d'Android 13, sur les flèches et OK ; et quand le
 * téléphone envoie du texte, il cherche le champ de saisie de l'appli à l'écran pour y écrire.
 * Les touches viennent uniquement de la page du téléphone (session authentifiée, liste fermée).
 */
public class RemoteService extends AccessibilityService {
    // Actions globales d'Android (constantes absentes du SDK 23 utilisé pour compiler)
    static final int BACK = 1, HOME = 2, LOCK_SCREEN = 8, DPAD_UP = 16, DPAD_DOWN = 17, DPAD_LEFT = 18, DPAD_RIGHT = 19, DPAD_CENTER = 20;

    private static volatile RemoteService instance;

    static boolean connected() { return instance != null; }

    /** Flèches et OK dans les autres applis : Android 13 (API 33) et plus. */
    static boolean dpadSupported() { return Build.VERSION.SDK_INT >= 33; }

    /** Appuie sur une touche globale ; false si le service n'est pas actif ou si la TV ne le permet pas. */
    static boolean perform(int action) {
        RemoteService s = instance;
        if (s == null) return false;
        if (action >= DPAD_UP && !dpadSupported()) return false;
        if (action == LOCK_SCREEN && Build.VERSION.SDK_INT < 28) return false;
        try { return s.performGlobalAction(action); } catch (Exception e) { return false; }
    }

    /** Écrit dans le champ de saisie de l'appli à l'écran : 1 écrit, 0 aucun champ, -1 service inactif. */
    static int typeText(String text, boolean enter) {
        RemoteService s = instance;
        if (s == null) return -1;
        try {
            AccessibilityNodeInfo n = s.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (n == null || !n.isEditable()) n = firstEditable(s.getRootInActiveWindow());
            if (n == null) return 0;
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean ok = n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
            if (ok && enter && Build.VERSION.SDK_INT >= 30) n.performAction(IME_ENTER);
            return ok ? 1 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Action « Entrée » du clavier (Android 11+), absente du SDK 23. */
    private static final int IME_ENTER = 0x01020036;

    /** Premier champ modifiable de la fenêtre (parcours borné). */
    private static AccessibilityNodeInfo firstEditable(AccessibilityNodeInfo root) {
        if (root == null) return null;
        java.util.ArrayDeque<AccessibilityNodeInfo> q = new java.util.ArrayDeque<>();
        q.add(root);
        int seen = 0;
        while (!q.isEmpty() && seen++ < 400) {
            AccessibilityNodeInfo n = q.poll();
            if (n == null) continue;
            if (n.isEditable() && n.isVisibleToUser()) return n;
            for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
        }
        return null;
    }

    @Override protected void onServiceConnected() { instance = this; }
    @Override public boolean onUnbind(android.content.Intent i) { instance = null; return super.onUnbind(i); }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}

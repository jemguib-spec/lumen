// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;

/** Touches de la télécommande du téléphone (liste fermée : PhoneGate.KEYS), jouées sur la TV. */
final class PhoneRemote {
    private PhoneRemote() { }

    /**
     * Télécommande du téléphone. Lumen à l'écran : la touche est rejouée dans Lumen (toutes versions).
     * Autre appli : service d'accessibilité « Lumen télécommande » s'il est activé. Volume et lecture :
     * toujours, par le système audio (aucune autorisation nécessaire).
     */
    static String press(MainActivity a, String k) {
        android.media.AudioManager am = (android.media.AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        switch (k) {
            case "sleep":
                if (RemoteService.perform(RemoteService.LOCK_SCREEN)) return "{\"ok\":true}";
                return "{\"ok\":false,\"service\":" + RemoteService.connected() + ",\"sleep\":false}";
            case "volup": case "voldown": case "mute":
                int dir = "volup".equals(k) ? android.media.AudioManager.ADJUST_RAISE
                        : "voldown".equals(k) ? android.media.AudioManager.ADJUST_LOWER : android.media.AudioManager.ADJUST_TOGGLE_MUTE;
                try { am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, android.media.AudioManager.FLAG_SHOW_UI); }
                catch (Exception e) { return "{\"ok\":false}"; }
                return "{\"ok\":true}";
            case "play": {
                long t = android.os.SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                return "{\"ok\":true}";
            }
            default: break;
        }
        if (a.resumedNow) {
            if ("home".equals(k)) {
                if (a.ambiance != null) a.closeAmbiance();
                if (a.ambientOn) a.exitAmbient(true);
                a.onNewIntent(a.getIntent()); // comme la touche Accueil : tout se referme, premier favori
                return "{\"ok\":true}";
            }
            int code = "up".equals(k) ? KeyEvent.KEYCODE_DPAD_UP : "down".equals(k) ? KeyEvent.KEYCODE_DPAD_DOWN
                    : "left".equals(k) ? KeyEvent.KEYCODE_DPAD_LEFT : "right".equals(k) ? KeyEvent.KEYCODE_DPAD_RIGHT
                    : "ok".equals(k) ? KeyEvent.KEYCODE_DPAD_CENTER : KeyEvent.KEYCODE_BACK;
            long t = android.os.SystemClock.uptimeMillis();
            boolean handled = a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
            // Une flèche injectée ici ne passe pas par Android, qui déplace normalement la sélection
            // lui-même : sans cela, « haut » ne menait jamais aux boutons du haut (Réglages…).
            if (!handled) {
                int dir = code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : code == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN
                        : code == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : code == KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT : 0;
                View f = a.getCurrentFocus();
                if (dir != 0 && f != null) {
                    View n = f.focusSearch(dir);
                    if (n != null) n.requestFocus(dir);
                }
            }
            a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
            return "{\"ok\":true}";
        }
        int action = "back".equals(k) ? RemoteService.BACK : "home".equals(k) ? RemoteService.HOME
                : "up".equals(k) ? RemoteService.DPAD_UP : "down".equals(k) ? RemoteService.DPAD_DOWN
                : "left".equals(k) ? RemoteService.DPAD_LEFT : "right".equals(k) ? RemoteService.DPAD_RIGHT : RemoteService.DPAD_CENTER;
        if (RemoteService.perform(action)) return "{\"ok\":true}";
        if ("home".equals(k)) {
            try {
                a.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setPackage(a.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return "{\"ok\":true}";
            } catch (Exception ignored) { }
        }
        boolean svc = RemoteService.connected();
        return "{\"ok\":false,\"service\":" + svc + ",\"dpad\":" + RemoteService.dpadSupported() + "}";
    }
}

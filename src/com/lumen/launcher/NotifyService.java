// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Lit les notifications des applis (nouvel épisode, nouvelle sortie, message…) pour que Lumen les
 * affiche en grand sur l'accueil. Facultatif : Android demande d'autoriser l'accès aux
 * notifications (ou une commande ADB, voir INSTALLATION.md). Lecture seule, en mémoire : rien
 * n'est enregistré ni envoyé, Lumen ne supprime jamais la notification d'une autre appli.
 */
public final class NotifyService extends NotificationListenerService {

    /** Une notification retenue pour l'accueil. */
    static final class Item {
        final String key, pkg, title, text, fingerprint;
        final long when;
        final PendingIntent open;
        Item(String key, String pkg, String title, String text, long when, PendingIntent open) {
            this.key = key; this.pkg = pkg; this.title = title; this.text = text; this.when = when; this.open = open;
            this.fingerprint = NotifyPolicy.fingerprint(pkg, title, text);
        }
    }

    /** L'accueil, quand il est à l'écran. */
    interface Listener {
        /** fresh : vient d'arriver (à montrer en grand) ; false : la liste a seulement changé. */
        void onNotify(Item item, boolean fresh);
    }

    private static final Object LOCK = new Object();
    private static List<Item> items = new ArrayList<>();
    private static final List<String> shown = new ArrayList<>(); // empreintes déjà montrées en grand
    private static volatile Listener listener;
    private static volatile boolean connected;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void setListener(Listener l) { listener = l; }
    static boolean connected() { return connected; }

    /** Notifications retenues, la plus récente d'abord. */
    static List<Item> items() { synchronized (LOCK) { return new ArrayList<>(items); } }

    /** Oublie une notification dans Lumen (elle reste dans Android). */
    static void forget(String key) {
        synchronized (LOCK) {
            List<Item> out = new ArrayList<>();
            for (Item i : items) if (!i.key.equals(key)) out.add(i);
            items = out;
        }
        tell(null, false);
    }

    static void forgetAll() {
        synchronized (LOCK) { items = new ArrayList<>(); }
        tell(null, false);
    }

    @Override public void onListenerConnected() {
        connected = true;
        // celles qui sont déjà là rejoignent la liste, sans surgir en grand
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) for (StatusBarNotification sbn : all) {
                Item it = read(sbn);
                if (it != null) keep(it, false);
            }
        } catch (Throwable ignored) { }
        tell(null, false);
    }

    /** API 24 (Lumen se compile contre l'API 23) : appelée quand Android retire l'accès. */
    public void onListenerDisconnected() { connected = false; }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        Item it = read(sbn);
        if (it == null) return;
        boolean fresh = keep(it, true);
        tell(it, fresh);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        boolean had = false;
        synchronized (LOCK) {
            List<Item> out = new ArrayList<>();
            for (Item i : items) { if (i.key.equals(sbn.getKey())) had = true; else out.add(i); }
            items = out;
        }
        if (had) tell(null, false);
    }

    /**
     * Range l'élément ; true s'il vient d'arriver (fresh) avec un contenu jamais vu. Celles déjà
     * présentes au démarrage sont notées comme vues : leur prochaine mise à jour ne surgit pas.
     */
    private static boolean keep(final Item it, boolean fresh) {
        synchronized (LOCK) {
            items = NotifyPolicy.push(items, it, it.key, new NotifyPolicy.KeyOf<Item>() {
                @Override public String key(Item t) { return t.key; }
            });
            if (shown.contains(it.fingerprint)) return false;
            shown.add(it.fingerprint);
            while (shown.size() > 100) shown.remove(0);
            return fresh;
        }
    }

    private static void tell(final Item it, final boolean fresh) {
        MAIN.post(new Runnable() {
            @Override public void run() { Listener l = listener; if (l != null) l.onNotify(it, fresh); }
        });
    }

    @SuppressWarnings("deprecation")
    private Item read(StatusBarNotification sbn) {
        try {
            if (sbn == null) return null;
            Notification n = sbn.getNotification();
            if (n == null) return null;
            Bundle x = n.extras;
            String title = x == null ? null : str(x.getCharSequence(Notification.EXTRA_TITLE));
            String text = x == null ? null : str(x.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (text == null || text.isEmpty()) text = x == null ? null : str(x.getCharSequence(Notification.EXTRA_TEXT));
            boolean ongoing = sbn.isOngoing() || (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
            boolean summary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
            if (!NotifyPolicy.show(sbn.getPackageName(), getPackageName(), title, text, n.category,
                    ongoing, summary, n.priority <= Notification.PRIORITY_MIN)) return null;
            return new Item(sbn.getKey(), sbn.getPackageName(), NotifyPolicy.clean(title), NotifyPolicy.clean(text),
                    sbn.getPostTime(), n.contentIntent);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String str(CharSequence c) { return c == null ? null : c.toString(); }

    // ------------------------------------------------------------------ autorisation

    static String component(Context c) { return new ComponentName(c, NotifyService.class).flattenToString(); }

    /** L'accès aux notifications est-il donné à Lumen ? */
    static boolean granted(Context c) {
        try {
            String v = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            if (v == null) return false;
            String me = component(c), shortMe = new ComponentName(c, NotifyService.class).flattenToShortString();
            for (String p : v.split(":")) if (p.trim().equals(me) || p.trim().equals(shortMe)) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    /**
     * Donne l'accès sans passer par l'écran d'Android, si WRITE_SECURE_SETTINGS a été accordée une
     * fois par ADB (comme pour l'économiseur d'écran). Les autres applis autorisées le restent.
     */
    static boolean grantSelf(Context c) {
        if (c.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return false;
        try {
            String v = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            String next = v == null || v.trim().isEmpty() ? component(c) : v + ":" + component(c);
            Settings.Secure.putString(c.getContentResolver(), "enabled_notification_listeners", next);
            return granted(c);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ouvre l'écran d'Android « Accès aux notifications », s'il existe sur cette TV. */
    static boolean openSystemScreen(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(c.getPackageManager()) == null) return false;
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Commande ADB qui donne l'accès (Android 8 et plus), affichée quand rien d'autre ne marche. */
    static String adbCommand(Context c) {
        return "adb shell cmd notification allow_listener " + new ComponentName(c, NotifyService.class).flattenToShortString();
    }
}

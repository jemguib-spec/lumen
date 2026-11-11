// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.TextView;
import java.util.Calendar;

/**
 * Ligne de date qui se tient à jour (minute, changement de jour, d'heure ou de fuseau), écrite
 * dans la langue de Lumen par {@link DateLine} au lieu de celle d'Android (TextClock).
 */
final class DateClock extends TextView {
    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };
    private boolean registered;

    DateClock(Context c) { super(c); refresh(); }

    void refresh() {
        String s = DateLine.format(Calendar.getInstance(), L.lang());
        if (!s.contentEquals(getText())) setText(s);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_DATE_CHANGED);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        try { getContext().registerReceiver(tick, f); registered = true; } catch (Exception ignored) { }
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        if (registered) { try { getContext().unregisterReceiver(tick); } catch (Exception ignored) { } registered = false; }
        super.onDetachedFromWindow();
    }
}

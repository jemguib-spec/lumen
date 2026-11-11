// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Réglages vus par la synchronisation entre TV : tous ceux qui voyagent (SyncState), de tous les
 * profils, plus la ville de la météo. Chaque changement fait ici est daté (réglages à part,
 * « lumen_sync »), une suppression aussi. Ce qui vient de l'autre TV est appliqué sur le fil
 * principal, sans être redaté ni renvoyé. Les secrets sont rouverts pour l'envoi (chiffré) et
 * rechiffrés à l'arrivée avec la clé propre à cette TV (Vault).
 */
final class SyncStore {
    private static final String WEATHER = "weather:";
    private final SharedPreferences raw, weather, meta;
    private boolean applying;
    /** Un réglage qui voyage vient de changer ici (fil principal). */
    Runnable onLocalChange;

    // gardés en champ : Android ne retient les écouteurs que faiblement
    private final SharedPreferences.OnSharedPreferenceChangeListener rawWatch = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) { changed(key); }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener weatherWatch = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) { if (key != null) changed(WEATHER + key); }
    };

    SyncStore(Context c) {
        Context app = c.getApplicationContext();
        raw = app.getSharedPreferences("lumen", Context.MODE_PRIVATE);
        weather = app.getSharedPreferences("lumen_weather", Context.MODE_PRIVATE);
        meta = app.getSharedPreferences("lumen_sync", Context.MODE_PRIVATE);
        raw.registerOnSharedPreferenceChangeListener(rawWatch);
        weather.registerOnSharedPreferenceChangeListener(weatherWatch);
    }

    /** Plus d'écoute (l'écran est détruit). */
    void close() {
        raw.unregisterOnSharedPreferenceChangeListener(rawWatch);
        weather.unregisterOnSharedPreferenceChangeListener(weatherWatch);
        onLocalChange = null;
    }

    private void changed(String key) {
        if (applying || key == null || !SyncState.synced(key)) return;
        meta.edit().putLong(key, System.currentTimeMillis()).apply();
        if (onLocalChange != null) onLocalChange.run();
    }

    /** Tous les réglages qui voyagent, datés ; les suppressions datées en font partie. */
    Map<String, SyncState.Entry> export() {
        Map<String, SyncState.Entry> out = new HashMap<>();
        for (Map.Entry<String, ?> e : raw.getAll().entrySet()) put(out, e.getKey(), e.getValue());
        for (Map.Entry<String, ?> e : weather.getAll().entrySet()) put(out, WEATHER + e.getKey(), e.getValue());
        for (Map.Entry<String, ?> e : meta.getAll().entrySet()) {
            if (!SyncState.synced(e.getKey()) || out.containsKey(e.getKey()) || !(e.getValue() instanceof Long)) continue;
            out.put(e.getKey(), new SyncState.Entry(null, (Long) e.getValue()));
        }
        return out;
    }

    private void put(Map<String, SyncState.Entry> out, String key, Object v) {
        if (!SyncState.synced(key)) return;
        if (SyncState.SECRETS.contains(key)) {
            if (!(v instanceof String)) return;
            v = Vault.open((String) v);
            if (((String) v).isEmpty()) return;
        }
        String enc = SyncState.encode(v);
        if (enc != null) out.put(key, new SyncState.Entry(enc, meta.getLong(key, 0)));
    }

    /**
     * Applique les réglages de l'autre TV (sur le fil principal : les écouteurs sont appelés pendant
     * l'écriture, donc sans redater). adopt = liaison : cette TV prend tout, et ce qui n'existe pas
     * là-bas disparaît ici. Renvoie le nombre de réglages changés.
     */
    int apply(Map<String, SyncState.Entry> theirs, List<String> keys, boolean adopt) {
        applying = true;
        int n = 0;
        boolean weatherChanged = false;
        try {
            SharedPreferences.Editor r = raw.edit(), w = weather.edit(), m = meta.edit();
            if (adopt) {
                for (String k : export().keySet()) {
                    if (theirs.containsKey(k)) continue;
                    editor(k, r, w).remove(name(k));
                    m.remove(k);
                    n++;
                }
            }
            for (String k : keys) {
                SyncState.Entry t = theirs.get(k);
                if (t == null || !SyncState.synced(k)) continue;
                SharedPreferences.Editor ed = editor(k, r, w);
                if (t.value == null) {
                    ed.remove(name(k));
                } else {
                    Object v = SyncState.decode(t.value);
                    if (!SyncState.acceptable(k, v)) continue;
                    if (SyncState.SECRETS.contains(k)) v = Vault.seal((String) v);
                    putTyped(ed, name(k), v);
                }
                m.putLong(k, t.ts);
                if (k.startsWith(WEATHER)) weatherChanged = true;
                n++;
            }
            // autre ville : la météo gardée en mémoire n'est plus la bonne, elle sera redemandée
            if (weatherChanged) w.putLong("t", 0).putLong("tomorrow_t", 0).remove("forecast").remove("line").remove("tomorrow");
            r.commit();
            w.commit();
            m.commit();
        } finally {
            applying = false;
        }
        return n;
    }

    private SharedPreferences.Editor editor(String k, SharedPreferences.Editor r, SharedPreferences.Editor w) { return k.startsWith(WEATHER) ? w : r; }
    private static String name(String k) { return k.startsWith(WEATHER) ? k.substring(WEATHER.length()) : k; }

    @SuppressWarnings("unchecked")
    private static void putTyped(SharedPreferences.Editor ed, String k, Object v) {
        if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
        else if (v instanceof Integer) ed.putInt(k, (Integer) v);
        else if (v instanceof Long) ed.putLong(k, (Long) v);
        else if (v instanceof Float) ed.putFloat(k, (Float) v);
        else if (v instanceof String) ed.putString(k, (String) v);
        else if (v instanceof Set) ed.putStringSet(k, (Set<String>) v);
    }
}

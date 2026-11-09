// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Réglages vus par le profil actif : un réglage personnel (Profiles.PERSONAL) est lu et écrit
 * dans la version du profil actif, tout le reste dans la version commune. Prefs passe par ici,
 * si bien que le reste de Lumen n'a pas à connaître les profils. Le profil actif est relu à
 * chaque accès : un changement de profil vaut aussitôt partout.
 */
final class ProfilePrefs implements SharedPreferences {
    private final SharedPreferences raw;

    ProfilePrefs(SharedPreferences raw) { this.raw = raw; }

    private int checkedId = -1;
    private String checkedList;
    private boolean checkedOk;

    /** Profil actif (le principal si le profil enregistré n'existe plus). */
    synchronized int active() {
        int id = raw.getInt(Profiles.ACTIVE_KEY, Profiles.MAIN);
        if (id == Profiles.MAIN) return id;
        String list = raw.getString(Profiles.LIST_KEY, null);
        if (id != checkedId || (list == null ? checkedList != null : !list.equals(checkedList))) {
            checkedOk = Profiles.find(Profiles.parse(list), id) != null;
            checkedId = id;
            checkedList = list;
        }
        return checkedOk ? id : Profiles.MAIN;
    }

    private String k(String key) { return Profiles.key(key, active()); }

    /** Accès direct aux réglages communs (liste des profils, effacement d'un profil). */
    SharedPreferences raw() { return raw; }

    @Override public Map<String, ?> getAll() {
        int me = active();
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, ?> e : raw.getAll().entrySet()) {
            String key = e.getKey();
            int owner = Profiles.ownerOf(key);
            if (owner == -1) {
                // commun, ou réglage personnel du profil principal (qui ne compte que pour lui)
                if (me == Profiles.MAIN || !Profiles.personal(key)) out.put(key, e.getValue());
            } else if (owner == me) {
                out.put(key.substring(Profiles.prefix(me).length()), e.getValue());
            }
        }
        return out;
    }

    @Override public String getString(String key, String def) { return raw.getString(k(key), def); }
    @Override public Set<String> getStringSet(String key, Set<String> def) { return raw.getStringSet(k(key), def); }
    @Override public int getInt(String key, int def) { return raw.getInt(k(key), def); }
    @Override public long getLong(String key, long def) { return raw.getLong(k(key), def); }
    @Override public float getFloat(String key, float def) { return raw.getFloat(k(key), def); }
    @Override public boolean getBoolean(String key, boolean def) { return raw.getBoolean(k(key), def); }
    @Override public boolean contains(String key) { return raw.contains(k(key)); }

    @Override public Editor edit() {
        final Editor e = raw.edit();
        final int me = active();
        return new Editor() {
            private String k(String key) { return Profiles.key(key, me); }
            @Override public Editor putString(String key, String v) { e.putString(k(key), v); return this; }
            @Override public Editor putStringSet(String key, Set<String> v) { e.putStringSet(k(key), v); return this; }
            @Override public Editor putInt(String key, int v) { e.putInt(k(key), v); return this; }
            @Override public Editor putLong(String key, long v) { e.putLong(k(key), v); return this; }
            @Override public Editor putFloat(String key, float v) { e.putFloat(k(key), v); return this; }
            @Override public Editor putBoolean(String key, boolean v) { e.putBoolean(k(key), v); return this; }
            @Override public Editor remove(String key) { e.remove(k(key)); return this; }
            /** Jamais tout effacer d'un coup (les autres profils et les réglages communs y passeraient). */
            @Override public Editor clear() { throw new UnsupportedOperationException("clear"); }
            @Override public boolean commit() { return e.commit(); }
            @Override public void apply() { e.apply(); }
        };
    }

    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { raw.registerOnSharedPreferenceChangeListener(l); }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { raw.unregisterOnSharedPreferenceChangeListener(l); }
}

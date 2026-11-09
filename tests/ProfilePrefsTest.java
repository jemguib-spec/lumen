// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Profils de bout en bout sur des réglages en mémoire : chaque profil voit ses favoris, les clés
 * restent communes, le profil principal garde les réglages d'avant. Compilé avec android.jar
 * (interface SharedPreferences seulement) : lancé par tests/run.sh quand il est disponible.
 */
public class ProfilePrefsTest {
    static int ok, ko;
    static void check(String n, boolean c) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }

    /** Réglages en mémoire. */
    static final class Mem implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();
        @Override public Map<String, ?> getAll() { return new HashMap<>(m); }
        @Override public String getString(String k, String d) { return m.containsKey(k) ? (String) m.get(k) : d; }
        @SuppressWarnings("unchecked")
        @Override public Set<String> getStringSet(String k, Set<String> d) { return m.containsKey(k) ? (Set<String>) m.get(k) : d; }
        @Override public int getInt(String k, int d) { return m.containsKey(k) ? (Integer) m.get(k) : d; }
        @Override public long getLong(String k, long d) { return m.containsKey(k) ? (Long) m.get(k) : d; }
        @Override public float getFloat(String k, float d) { return m.containsKey(k) ? (Float) m.get(k) : d; }
        @Override public boolean getBoolean(String k, boolean d) { return m.containsKey(k) ? (Boolean) m.get(k) : d; }
        @Override public boolean contains(String k) { return m.containsKey(k); }
        @Override public Editor edit() {
            final Map<String, Object> put = new HashMap<>();
            final Set<String> rm = new HashSet<>();
            return new Editor() {
                public Editor putString(String k, String v) { put.put(k, v); return this; }
                public Editor putStringSet(String k, Set<String> v) { put.put(k, v); return this; }
                public Editor putInt(String k, int v) { put.put(k, v); return this; }
                public Editor putLong(String k, long v) { put.put(k, v); return this; }
                public Editor putFloat(String k, float v) { put.put(k, v); return this; }
                public Editor putBoolean(String k, boolean v) { put.put(k, v); return this; }
                public Editor remove(String k) { rm.add(k); return this; }
                public Editor clear() { m.clear(); return this; }
                public boolean commit() { for (String k : rm) m.remove(k); m.putAll(put); return true; }
                public void apply() { commit(); }
            };
        }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
    }

    public static void main(String[] args) {
        Mem raw = new Mem();
        // réglages d'avant les profils
        raw.m.put("favorites", "netflix,youtube");
        raw.m.put("tmdb_key", "cle");
        raw.m.put("source", 3);
        ProfilePrefs sp = new ProfilePrefs(raw);
        check("avant les profils : réglages intacts", "netflix,youtube".equals(sp.getString("favorites", "")) && sp.getInt("source", 0) == 3);

        raw.m.put(Profiles.LIST_KEY, "0:Moi|1:Léa");
        raw.m.put(Profiles.ACTIVE_KEY, 1);
        check("profil neuf : pas de favoris", !sp.contains("favorites") && sp.getInt("source", 0) == 0);
        check("profil neuf : clés partagées", "cle".equals(sp.getString("tmdb_key", "")));
        sp.edit().putString("favorites", "disney").putString("pexels_key", "px").apply();
        check("favoris écrits dans le profil", "disney".equals(raw.m.get("p1.favorites")) && "netflix,youtube".equals(raw.m.get("favorites")));
        check("clé écrite en commun", "px".equals(raw.m.get("pexels_key")) && !raw.m.containsKey("p1.pexels_key"));
        Map<String, ?> all = sp.getAll();
        check("vue complète du profil (sauvegarde)", "disney".equals(all.get("favorites")) && "cle".equals(all.get("tmdb_key")) && !all.containsKey("source") && !all.containsKey("p1.favorites"));

        raw.m.put(Profiles.ACTIVE_KEY, 0);
        check("retour au principal : ses favoris", "netflix,youtube".equals(sp.getString("favorites", "")) && "px".equals(sp.getString("pexels_key", "")));
        check("vue du principal sans les autres profils", !sp.getAll().containsKey("p1.favorites") && sp.getAll().containsKey("favorites"));

        raw.m.put(Profiles.ACTIVE_KEY, 7);
        check("profil disparu : retour au principal", sp.active() == 0 && "netflix,youtube".equals(sp.getString("favorites", "")));
        boolean refused = false;
        try { sp.edit().clear(); } catch (UnsupportedOperationException e) { refused = true; }
        check("tout effacer d'un coup : refusé", refused);

        System.out.println(ok + " tests de profils réussis, " + ko + " échec(s)");
        if (ko > 0) System.exit(1);
    }
}

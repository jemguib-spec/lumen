// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Synchronisation permanente de deux TV reliées : règles pures (Java pur, testé).
 * Quels réglages voyagent (tout, sauf ce qui tient à l'appareil), comment une valeur s'écrit dans
 * un échange, et la fusion : pour chaque réglage, la valeur la plus récente gagne (à égalité,
 * l'identifiant de TV le plus grand, pour que les deux TV décident pareil). Une suppression est
 * une valeur vide datée (« pierre tombale »), pour qu'un réglage remis par défaut le soit partout.
 * La lecture et l'écriture des réglages sont dans SyncStore, l'échange dans PairLink.
 */
final class SyncState {
    private SyncState() { }

    /**
     * Réglages qui voyagent (nom de base ; les réglages personnels voyagent pour chaque profil).
     * Tout ce qui se règle, plus les profils, le code PIN (son empreinte salée), les habitudes,
     * l'historique, les vidéos cochées, les clés et Home Assistant.
     */
    static final Set<String> SYNCED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // affichage et sons
            "app_glow", "show_icons", "tint_tiles", "tint_ui", "tile_style", "tile_word_v1", "text_scale", "high_contrast", "clock",
            "immersive", "lang", "sounds", "sound_theme", "sound_volume", "ambiance_sound", "ambiance_kind",
            // fond, veille, vidéos
            "source", "interval", "follow_time", "living_scenes", "seasonal", "weather_fx", "video_theme", "video_ok", "cities",
            "hidden_walls", "idle", "ambient_min", "veille_src", "night_hour", "cosy_wanted",
            // accueil, boutons, notifications
            "home_cols", "home_rows", "greeting", "top_shelf", "shelf_posters", "habits_on", "habits", "recents", "searches",
            "notif_on", "notif_secs", "notif_muted", "inputs_home_v1",
            "btn_notifs", "btn_search", "btn_ambiances", "btn_inputs", "btn_profiles", "btn_apps", "btn_tv", "btn_settings",
            "sound_button", "ha_button",
            // applis
            "favorites", "hidden_apps", "locked_apps",
            // À l'affiche
            "reco_filter", "reco_mode", "reco_type", "reco_hist", "tmdb_shared_ok",
            // maison, profils, sécurité, système
            "ha_url", "ha_cert", "ha_cine_open", "ha_film", "ha_pause", "ha_pinned", "ha_home", "ha_cine_apps",
            Profiles.LIST_KEY, "profile_ask", "pin", "phone_remote_only", "auto_update",
            // secrets : envoyés en clair dans l'échange chiffré, rangés chiffrés (Vault) à l'arrivée
            "tmdb_key", "pexels_key", "ha_token",
            // météo (réglages à part : « weather: »)
            "weather:city", "weather:lat", "weather:lon")));

    /** Gardés sur leur TV : ils tiennent à l'appareil, à la télécommande, ou à ce qui s'y passe en ce moment. */
    static final Set<String> LOCAL = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "remote_keys", "remote_ask", "video_disk_gb", "video_4k", "show_perf",
            Profiles.ACTIVE_KEY, "pin_fails", "pin_last_fail", "trusted_phones", "tmdb_shared", "ha_last_scene",
            "notif_seen", "setup_step", "install_help_shown", "tip_step", "watch_next_asked", "restarting",
            "update_check", "update_from", "update_notes", "update_notes_v", "update_prompted", "update_ready", "update_system_tried")));

    /** Gardés sur leur TV aussi : tout ce qui commence ainsi (dates de téléchargement par thème). */
    static final String[] LOCAL_PREFIXES = {"refresh_"};

    /** Secrets : jamais tels quels (chiffrés par la clé propre à chaque TV), toujours rouverts puis rechiffrés. */
    static final Set<String> SECRETS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("tmdb_key", "pexels_key", "ha_token")));

    /** Historique qui change sans cesse (applis ouvertes, recherches) : repris sans redessiner l'écran. */
    static final Set<String> QUIET = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("habits", "recents", "searches", "reco_hist")));

    static boolean quiet(String key) { return QUIET.contains(base(key)); }

    /** Nom de base d'un réglage : « p3.favorites » → « favorites » (réglage personnel du profil 3). */
    static String base(String key) {
        if (key == null) return null;
        int owner = Profiles.ownerOf(key);
        return owner > Profiles.MAIN ? key.substring(Profiles.prefix(owner).length()) : key;
    }

    /** Ce réglage voyage-t-il ? (un réglage d'un profil n'est accepté que s'il est vraiment personnel) */
    static boolean synced(String key) {
        if (key == null || key.length() > 80) return false;
        String b = base(key);
        if (!b.equals(key) && !Profiles.personal(b)) return false;
        return SYNCED.contains(b);
    }

    static boolean local(String key) {
        String b = base(key);
        if (LOCAL.contains(b)) return true;
        for (String p : LOCAL_PREFIXES) if (b.startsWith(p)) return true;
        return false;
    }

    // ------------------------------------------------------------------ valeurs

    static final int MAX_TEXT = 20_000, MAX_SET = 500, MAX_ITEM = 300;
    private static final char SEP = '\u001f';

    /** Valeur → texte : « b1 », « i42 », « l123 », « f1.5 », « s… », « S » + éléments séparés. null si non transmissible. */
    static String encode(Object v) {
        if (v instanceof Boolean) return ((Boolean) v) ? "b1" : "b0";
        if (v instanceof Integer) return "i" + v;
        if (v instanceof Long) return "l" + v;
        if (v instanceof Float) return "f" + v;
        if (v instanceof String) return ((String) v).length() > MAX_TEXT ? null : "s" + v;
        if (v instanceof Set) {
            Set<?> s = (Set<?>) v;
            if (s.size() > MAX_SET) return null;
            StringBuilder b = new StringBuilder("S");
            boolean first = true;
            for (Object o : new TreeSet<>(asStrings(s))) {
                String item = String.valueOf(o);
                if (item.indexOf(SEP) >= 0 || item.length() > MAX_ITEM) return null;
                if (!first) b.append(SEP);
                b.append(item);
                first = false;
            }
            return b.toString();
        }
        return null;
    }

    private static Set<String> asStrings(Set<?> s) {
        Set<String> out = new HashSet<>();
        for (Object o : s) out.add(String.valueOf(o));
        return out;
    }

    /** Texte → valeur (Boolean, Integer, Long, Float, String, Set&lt;String&gt;), ou null si mal formé. */
    static Object decode(String e) {
        if (e == null || e.isEmpty()) return null;
        String r = e.substring(1);
        try {
            switch (e.charAt(0)) {
                case 'b': return r.equals("1") ? Boolean.TRUE : r.equals("0") ? Boolean.FALSE : null;
                case 'i': return Integer.valueOf(r);
                case 'l': return Long.valueOf(r);
                case 'f': { Float f = Float.valueOf(r); return f.isNaN() || f.isInfinite() ? null : f; }
                case 's': return r.length() > MAX_TEXT ? null : r;
                case 'S': {
                    Set<String> s = new LinkedHashSet<>();
                    if (r.isEmpty()) return s;
                    for (String item : r.split(String.valueOf(SEP), -1)) {
                        if (item.length() > MAX_ITEM) return null;
                        s.add(item);
                    }
                    return s.size() > MAX_SET ? null : s;
                }
                default: return null;
            }
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ fusion

    /** Un réglage dans un échange : valeur encodée (null = supprimé) et date du dernier changement. */
    static final class Entry {
        final String value;
        final long ts;
        Entry(String value, long ts) { this.value = value; this.ts = ts; }
        @Override public boolean equals(Object o) {
            return o instanceof Entry && ((Entry) o).ts == ts && (value == null ? ((Entry) o).value == null : value.equals(((Entry) o).value));
        }
        @Override public int hashCode() { return (value == null ? 0 : value.hashCode()) * 31 + (int) ts; }
    }

    /** L'autre valeur l'emporte-t-elle sur la mienne ? Plus récente ; à égalité de date, l'identifiant le plus grand. */
    static boolean wins(Entry mine, Entry theirs, String myId, String theirId) {
        if (theirs == null) return false;
        if (mine == null) return theirs.value != null || theirs.ts > 0;
        if (theirs.ts != mine.ts) return theirs.ts > mine.ts;
        if (theirs.value == null ? mine.value == null : theirs.value.equals(mine.value)) return false;
        return theirId.compareTo(myId) > 0;
    }

    /** Réglages à prendre chez l'autre TV (ceux qui voyagent et où elle l'emporte). */
    static List<String> toApply(Map<String, Entry> mine, Map<String, Entry> theirs, String myId, String theirId, long now) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Entry> e : theirs.entrySet()) {
            String k = e.getKey();
            Entry t = e.getValue();
            if (!synced(k) || t == null) continue;
            if (t.ts > now + 24 * 3600_000L) continue; // horloge folle : ignoré plutôt que gagner pour toujours
            if (t.value != null && decode(t.value) == null) continue;
            if (wins(mine.get(k), t, myId, theirId)) out.add(k);
        }
        Collections.sort(out);
        return out;
    }

    /**
     * Valeur reçue acceptable pour ce réglage : bon type, bornes de la sauvegarde, et pour les clés,
     * Home Assistant, le code PIN et la météo, les mêmes contrôles que depuis le téléphone.
     */
    static boolean acceptable(String key, Object v) {
        if (v == null || !synced(key)) return false;
        String b = base(key);
        switch (b) {
            case "tmdb_key": return v instanceof String && TmdbPolicy.validKey((String) v);
            case "pexels_key": return v instanceof String && Pexels.validKey((String) v);
            case "ha_token": return v instanceof String && HomeAssistant.validToken((String) v);
            case "ha_url": return v instanceof String && (((String) v).isEmpty() || v.equals(HomeAssistant.normalizeUrl((String) v)));
            case "ha_cert": return v instanceof String && TvSync.certOk((String) v);
            case "pin": return v instanceof String && ((String) v).matches("[0-9a-f]{16,64}:[0-9a-f]{32,128}");
            case Profiles.LIST_KEY: return v instanceof String && ((String) v).length() <= 2000;
            case "weather:city": return v instanceof String && !((String) v).trim().isEmpty() && Backup.textOk((String) v, 80);
            case "weather:lat": return v instanceof Float && Math.abs((Float) v) <= 90;
            case "weather:lon": return v instanceof Float && Math.abs((Float) v) <= 180;
            default: break;
        }
        switch (Backup.typeOf(b)) {
            case Backup.BOOL: return v instanceof Boolean;
            case Backup.INT: return v instanceof Integer && Backup.intOk(b, (Integer) v);
            case Backup.STRING: return v instanceof String;
            case Backup.SET: return v instanceof Set;
            default: return true;
        }
    }

    /** Identifiant de TV : 16 chiffres hexadécimaux au hasard, tiré une fois. */
    static boolean deviceIdOk(String id) { return id != null && id.matches("[0-9a-f]{16}"); }
}

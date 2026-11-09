// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Prefs {
    static final int[] INTERVALS = {15, 40, 120, 300, 0}; // 0 = fond fixe

    // Sources de fond d'écran
    static final int SRC_LUMEN = 0, SRC_ANIM = 1, SRC_SPACE = 2, SRC_NATURE = 3, SRC_ANIMALS = 4,
            SRC_VIDEO = 5, SRC_MIX = 6, SRC_MINE = 7, SRC_POTD = 8;
    static final String[] SRC_NAMES = {"Paysages Lumen", "Paysages animés", "Espace", "Nature",
            "Animaux", "Vidéos 4K", "Mélange de photos", "Mes photos et vidéos", "Photo du jour"};

    static final int TILE_MONO = 0, TILE_BANNER = 1, TILE_WORD = 2;

    private final SharedPreferences sp;

    Prefs(Context c) { sp = c.getSharedPreferences("lumen", Context.MODE_PRIVATE); }

    boolean hasFavorites() { return sp.contains("favorites"); }

    List<String> favorites() {
        String s = sp.getString("favorites", "");
        List<String> out = new ArrayList<>();
        if (!TextUtils.isEmpty(s)) out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    void setFavorites(List<String> keys) {
        sp.edit().putString("favorites", TextUtils.join(",", keys)).apply();
    }

    private Set<String> set(String k) { return new HashSet<>(sp.getStringSet(k, new HashSet<String>())); }
    private void put(String k, Set<String> s) { sp.edit().putStringSet(k, new HashSet<>(s)).apply(); }

    Set<String> hiddenApps() { return set("hidden_apps"); }
    void setAppHidden(String key, boolean hidden) {
        Set<String> s = hiddenApps();
        if (hidden) s.add(key); else s.remove(key);
        put("hidden_apps", s);
    }

    Set<String> hiddenWalls() { return set("hidden_walls"); }
    void hideWall(String id) { Set<String> s = hiddenWalls(); s.add(id); put("hidden_walls", s); }
    void unhideAllWalls() { put("hidden_walls", new HashSet<String>()); }

    int source() { return sp.getInt("source", SRC_LUMEN); }
    void setSource(int s) { sp.edit().putInt("source", s).apply(); }

    boolean followTime() { return sp.getBoolean("follow_time", true); }
    void setFollowTime(boolean b) { sp.edit().putBoolean("follow_time", b).apply(); }

    int intervalSeconds() { return sp.getInt("interval", 40); }
    void setIntervalSeconds(int s) { sp.edit().putInt("interval", s).apply(); }

    int tileStyle() {
        // v3.8 : le style « Nom » devient le style par défaut (une seule fois)
        if (!sp.getBoolean("tile_word_v1", false)) sp.edit().putBoolean("tile_word_v1", true).putInt("tile_style", TILE_WORD).apply();
        return sp.getInt("tile_style", TILE_WORD);
    }
    void setTileStyle(int s) { sp.edit().putInt("tile_style", s).apply(); }

    boolean showIcons() { return sp.getBoolean("show_icons", false); }
    void setShowIcons(boolean b) { sp.edit().putBoolean("show_icons", b).apply(); }

    boolean tintTiles() { return sp.getBoolean("tint_tiles", false); }
    void setTintTiles(boolean b) { sp.edit().putBoolean("tint_tiles", b).apply(); }

    /**
     * Images du mode veille : le fond d'écran sans vidéo (défaut), une source choisie (SRC_*),
     * ou l'ancien comportement « comme l'accueil » (vidéos comprises).
     */
    static final int VEILLE_HOME = -1, VEILLE_LIVE = -2;
    static final int[] VEILLE_CHOICES = {VEILLE_HOME, SRC_LUMEN, SRC_ANIM, SRC_SPACE, SRC_NATURE, SRC_ANIMALS, SRC_MIX,
            SRC_MINE, SRC_POTD, VEILLE_LIVE};
    static String veilleName(int v) {
        if (v == VEILLE_HOME) return "Fond d'écran (sans vidéo)";
        if (v == VEILLE_LIVE) return "Comme l'accueil (vidéos comprises)";
        return v >= 0 && v < SRC_NAMES.length ? SRC_NAMES[v] : "Fond d'écran (sans vidéo)";
    }
    int veilleSource() {
        int v = sp.getInt("veille_src", VEILLE_HOME);
        for (int c : VEILLE_CHOICES) if (c == v) return v;
        return VEILLE_HOME;
    }
    void setVeilleSource(int v) { sp.edit().putInt("veille_src", v).apply(); }

    /** Bandeau d'accueil au-dessus des favoris. */
    boolean greeting() { return sp.getBoolean("greeting", true); }
    void setGreeting(boolean b) { sp.edit().putBoolean("greeting", b).apply(); }

    /** Écran de nuit du mode veille : heure de début (-1 = jamais), jusqu'à 7 h. */
    static final int[] NIGHT_HOURS = {22, 23, 0, -1};
    int nightHour() { return sp.getInt("night_hour", 23); }
    void setNightHour(int h) { sp.edit().putInt("night_hour", h).apply(); }

    static final int[] AMBIENT_MIN = {1, 3, 5, 10, 0}; // 0 = jamais
    int ambientMinutes() { return sp.getInt("ambient_min", 3); }
    void setAmbientMinutes(int m) { sp.edit().putInt("ambient_min", m).apply(); }

    boolean weatherFx() { return sp.getBoolean("weather_fx", true); }
    void setWeatherFx(boolean b) { sp.edit().putBoolean("weather_fx", b).apply(); }

    boolean sounds() { return sp.getBoolean("sounds", true); }
    void setSounds(boolean b) { sp.edit().putBoolean("sounds", b).apply(); }

    /** Mesures affichées en haut à gauche (diagnostic). */
    boolean showPerf() { return sp.getBoolean("show_perf", false); }
    void setShowPerf(boolean b) { sp.edit().putBoolean("show_perf", b).apply(); }

    /** Mise à jour automatique depuis le dépôt GitHub de Lumen. */
    boolean autoUpdate() { return sp.getBoolean("auto_update", true); }
    void setAutoUpdate(boolean b) { sp.edit().putBoolean("auto_update", b).apply(); }
    long lastUpdateCheck() { return sp.getLong("update_check", 0); }
    void setLastUpdateCheck(long t) { sp.edit().putLong("update_check", t).apply(); }
    String updateReadyVersion() { return sp.getString("update_ready", ""); }
    void setUpdateReadyVersion(String v) { sp.edit().putString("update_ready", v).apply(); }
    /** Version avant une mise à jour lancée (vide sinon) : sert à revenir et à annoncer « à jour ». */
    String updatePending() { return sp.getString("update_from", ""); }
    void setUpdatePending(String v) { sp.edit().putString("update_from", v).commit(); } // écrit tout de suite : Lumen va être arrêté

    /** Redémarrage demandé (l'écran de démarrage le dit au retour). */
    boolean restarting() { return sp.getBoolean("restarting", false); }
    void setRestarting(boolean b) { sp.edit().putBoolean("restarting", b).commit(); }

    String updatePrompted() { return sp.getString("update_prompted", ""); }
    void setUpdatePrompted(String v) { sp.edit().putString("update_prompted", v).apply(); }

    /** Paysages Lumen animés (nuages, eau, étoiles). */
    boolean livingScenes() { return sp.getBoolean("living_scenes", true); }
    void setLivingScenes(boolean b) { sp.edit().putBoolean("living_scenes", b).apply(); }

    /** « Reprendre » au-dessus de l'appli sélectionnée. */
    boolean topShelf() { return sp.getBoolean("top_shelf", true); }
    void setTopShelf(boolean b) { sp.edit().putBoolean("top_shelf", b).apply(); }
    /** Affiches des épisodes téléchargées depuis le service (sinon couleur de l'appli). */
    boolean shelfPosters() { return sp.getBoolean("shelf_posters", true); }
    void setShelfPosters(boolean b) { sp.edit().putBoolean("shelf_posters", b).apply(); }
    boolean watchNextAsked() { return sp.getBoolean("watch_next_asked", false); }
    void setWatchNextAsked(boolean b) { sp.edit().putBoolean("watch_next_asked", b).apply(); }

    boolean appGlow() { return sp.getBoolean("app_glow", true); }
    void setAppGlow(boolean b) { sp.edit().putBoolean("app_glow", b).apply(); }

    int homeCols() { return sp.getInt("home_cols", 5); }
    void setHomeCols(int n) { sp.edit().putInt("home_cols", n).apply(); }

    int homeRows() { return sp.getInt("home_rows", 1); }
    void setHomeRows(int n) { sp.edit().putInt("home_rows", n).apply(); }

    void unhideWall(String id) { Set<String> s = hiddenWalls(); s.remove(id); put("hidden_walls", s); }

    boolean video4k() { return sp.getBoolean("video_4k", false); } // HD par défaut : plus léger pour le processeur de la TV
    void setVideo4k(boolean b) { sp.edit().putBoolean("video_4k", b).apply(); }

    static final int VT_ALL = 0, VT_CITY = 1, VT_NATURE = 2, VT_SPACE = 3;
    static final String[] VT_NAMES = {"Tout", "Villes", "Nature", "Espace et Terre"};
    int videoTheme() { return sp.getInt("video_theme", VT_ALL); }
    void setVideoTheme(int t) { sp.edit().putInt("video_theme", t).apply(); }

    /** Vidéos cochées dans l'écran de choix (titre de page Commons ou « pexels:<id> »). Rien par défaut. */
    boolean videoOk(String key) { return key != null && !key.isEmpty() && set("video_ok").contains(key); }
    void setVideoOk(String key, boolean on) {
        Set<String> s = set("video_ok");
        if (on) s.add(key); else s.remove(key);
        put("video_ok", s);
    }
    int videoOkCount() { return set("video_ok").size(); }

    /** Clé Pexels personnelle (vide = Pexels désactivé). Reste sur la TV, n'est jamais renvoyée au téléphone. */
    /** Home Assistant : adresse, jeton (jamais renvoyé au téléphone, absent de la sauvegarde), appareils choisis. */
    String haUrl() { return sp.getString("ha_url", ""); }
    String haToken() { return sp.getString("ha_token", ""); }
    /** Bouton « Maison » sur l'accueil : masqué par défaut. */
    boolean haButton() { return sp.getBoolean("ha_button", false); }
    void setHaButton(boolean b) { sp.edit().putBoolean("ha_button", b).apply(); }
    boolean haConfigured() { return !haUrl().isEmpty() && !haToken().isEmpty(); }
    void setHa(String url, String token) { sp.edit().putString("ha_url", url).putString("ha_token", token).apply(); }
    Set<String> haPinned() { return set("ha_pinned"); }
    void setHaPinned(String id, boolean on) { Set<String> s = haPinned(); if (on) s.add(id); else s.remove(id); put("ha_pinned", s); }

    /** Scènes Home Assistant en raccourci sur l'accueil : « id\tnom », 4 au plus. */
    List<String[]> haHome() {
        List<String[]> out = new ArrayList<>();
        for (String s : new java.util.TreeSet<>(set("ha_home"))) { String[] p = s.split("\t", 2); if (p.length == 2) out.add(p); }
        return out.size() > 4 ? out.subList(0, 4) : out;
    }
    void setHaHome(String id, String name, boolean on) {
        Set<String> s = set("ha_home"), out = new HashSet<>();
        for (String x : s) if (!x.startsWith(id + "\t")) out.add(x);
        if (on) out.add(id + "\t" + name);
        put("ha_home", out);
    }
    /** Mode cinéma : scène au début d'un film, scène en pause ou au retour, applis concernées. */
    String haFilmScene() { return sp.getString("ha_film", ""); }
    String haPauseScene() { return sp.getString("ha_pause", ""); }
    boolean haCinemaOnOpen() { return sp.getBoolean("ha_cine_open", false); }
    Set<String> haCinemaApps() {
        if (!sp.contains("ha_cine_apps")) return new HashSet<>(Arrays.asList(Cinema.DEFAULT_APPS));
        return set("ha_cine_apps");
    }
    void setHaCinema(String film, String pause, boolean onOpen, Set<String> apps) {
        sp.edit().putString("ha_film", film).putString("ha_pause", pause).putBoolean("ha_cine_open", onOpen)
                .putStringSet("ha_cine_apps", new HashSet<>(apps)).apply();
    }

    /** Clé TMDB personnelle (vide = « À l'affiche » désactivé) ; jamais renvoyée au téléphone. */
    String tmdbKey() { return sp.getString("tmdb_key", ""); }
    void setTmdbKey(String k) {
        String v = TmdbPolicy.validKey(k) ? k : "";
        sp.edit().putString("tmdb_key", v).apply();
        NetPolicy.setTmdbEnabled(!v.isEmpty());
    }
    /** Filtre de « À l'affiche » : "" = partout, "mine" = mes applis, sinon un paquet. */
    String recoFilter() { return sp.getString("reco_filter", "mine"); }
    void setRecoFilter(String f) { sp.edit().putString("reco_filter", f).apply(); }
    /** Mode d'« À l'affiche » : popular, new, top, foryou. */
    String recoMode() { return sp.getString("reco_mode", "popular"); }
    void setRecoMode(String m) { sp.edit().putString("reco_mode", m).apply(); }
    /** Titres ouverts depuis « À l'affiche » (« movie:123 »), les plus récents d'abord ; restent sur la TV. */
    List<String> recoHistory() {
        List<String> out = new ArrayList<>();
        for (String s : sp.getString("reco_hist", "").split(",")) if (s.matches("(movie|tv):[0-9]{1,10}")) out.add(s);
        return out;
    }
    void addRecoHistory(String typeId) {
        List<String> l = recoHistory();
        l.remove(typeId);
        l.add(0, typeId);
        while (l.size() > 12) l.remove(l.size() - 1);
        sp.edit().putString("reco_hist", TextUtils.join(",", l)).apply();
    }

    /** Type dans « À l'affiche » : "" = films et séries, "movie" ou "tv". */
    String recoType() { return sp.getString("reco_type", ""); }
    void setRecoType(String t) { sp.edit().putString("reco_type", t).apply(); }

    /** Téléphones déjà acceptés sur la TV (empreintes, jamais l'identifiant lui-même). */
    Set<String> trustedPhones() { return set("trusted_phones"); }
    void trustPhone(String fp) { Set<String> s = trustedPhones(); s.add(fp); while (s.size() > 8) s.remove(s.iterator().next()); put("trusted_phones", s); }
    void forgetPhones() { put("trusted_phones", new HashSet<String>()); }
    /** Accès du téléphone : true = télécommande seulement (touches, texte, ouvrir une appli). */
    boolean phoneRemoteOnly() { return sp.getBoolean("phone_remote_only", false); }
    void setPhoneRemoteOnly(boolean b) { sp.edit().putBoolean("phone_remote_only", b).apply(); }

    String pexelsKey() { return sp.getString("pexels_key", ""); }
    void setPexelsKey(String k) {
        String v = Pexels.validKey(k) ? k : "";
        sp.edit().putString("pexels_key", v).apply();
        NetPolicy.setPexelsEnabled(!v.isEmpty());
    }

    /** Villes choisies pour les vidéos (toutes par défaut). */
    Set<String> cities() {
        if (!sp.contains("cities")) {
            Set<String> all = new HashSet<>();
            for (String[] c : Online.CITIES) all.add(c[0]);
            return all;
        }
        // seules les villes encore au catalogue comptent (Londres, Tokyo… ont été retirées)
        Set<String> s = set("cities"), out = new HashSet<>();
        for (String n : s) if (VideoCatalog.isCity(n)) out.add(n);
        return out;
    }
    void setCity(String name, boolean on) {
        Set<String> s = cities();
        if (on) s.add(name); else s.remove(name);
        put("cities", s);
    }

    static final int[] DISK_GB = {1, 2, 3, 6, 10};
    int videoDiskGb() { return sp.getInt("video_disk_gb", 2); }
    void setVideoDiskGb(int g) { sp.edit().putInt("video_disk_gb", g).apply(); }

    /** Recherches récentes (6 au plus, la plus récente d'abord) ; restent sur la TV. */
    List<String> searches() {
        List<String> out = new ArrayList<>();
        for (String s : sp.getString("searches", "").split("\n")) if (!s.trim().isEmpty()) out.add(s);
        return out;
    }
    void addSearch(String q) {
        List<String> l = searches();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).equalsIgnoreCase(q)) l.remove(i);
        l.add(0, q.replace('\n', ' '));
        while (l.size() > 6) l.remove(l.size() - 1);
        sp.edit().putString("searches", TextUtils.join("\n", l)).apply();
    }
    void clearSearches() { sp.edit().remove("searches").apply(); }

    /** Applis lancées récemment : « clé|horodatage », la plus récente en premier. */
    List<String[]> recents() {
        List<String[]> out = new ArrayList<>();
        String s = sp.getString("recents", "");
        if (TextUtils.isEmpty(s)) return out;
        for (String e : s.split(",")) {
            int b = e.lastIndexOf('|');
            if (b > 0) out.add(new String[]{e.substring(0, b), e.substring(b + 1)});
        }
        return out;
    }

    void addRecent(String key) {
        List<String[]> r = recents();
        StringBuilder b = new StringBuilder(key).append('|').append(System.currentTimeMillis());
        int n = 1;
        for (String[] e : r) {
            if (e[0].equals(key) || n >= 20) continue;
            b.append(',').append(e[0]).append('|').append(e[1]);
            n++;
        }
        sp.edit().putString("recents", b.toString()).apply();
    }

    boolean seasonal() { return sp.getBoolean("seasonal", true); }
    void setSeasonal(boolean b) { sp.edit().putBoolean("seasonal", b).apply(); }

    int ambianceKind() { return sp.getInt("ambiance_kind", 0); }
    void setAmbianceKind(int k) { sp.edit().putInt("ambiance_kind", k).apply(); }

    /** L'utilisateur a ouvert les ambiances au moins une fois : on garde leurs vidéos à jour. */
    boolean cosyWanted() { return sp.getBoolean("cosy_wanted", false); }
    void setCosyWanted(boolean b) { sp.edit().putBoolean("cosy_wanted", b).apply(); }

    boolean ambianceSound() { return sp.getBoolean("ambiance_sound", true); }
    void setAmbianceSound(boolean b) { sp.edit().putBoolean("ambiance_sound", b).apply(); }

    boolean showClock() { return sp.getBoolean("clock", true); }
    void setShowClock(boolean b) { sp.edit().putBoolean("clock", b).apply(); }

    boolean idleFade() { return sp.getBoolean("idle", true); }
    void setIdleFade(boolean b) { sp.edit().putBoolean("idle", b).apply(); }

    long lastRefresh(String theme) { return sp.getLong("refresh_" + theme, 0); }
    void setLastRefresh(String theme, long t) { sp.edit().putLong("refresh_" + theme, t).apply(); }

    /** Drapeaux « fait une fois » (migrations, questions déjà posées). */
    boolean flag(String name) { return sp.getBoolean(name, false); }
    void setFlag(String name) { sp.edit().putBoolean(name, true).apply(); }

    // ------------------------------------------------------------------ code PIN

    String pinStored() { return sp.getString("pin", null); }
    boolean hasPin() { return pinStored() != null; }
    void setPin(String pin) {
        if (pin == null) sp.edit().remove("pin").remove("pin_fails").remove("pin_last_fail").putStringSet("locked_apps", new HashSet<String>()).apply();
        else sp.edit().putString("pin", Pin.store(pin)).putInt("pin_fails", 0).apply();
    }
    int pinFails() { return sp.getInt("pin_fails", 0); }
    long pinLastFail() { return sp.getLong("pin_last_fail", 0); }
    void pinFailed() { sp.edit().putInt("pin_fails", pinFails() + 1).putLong("pin_last_fail", System.currentTimeMillis()).commit(); }
    void pinOk() { sp.edit().putInt("pin_fails", 0).apply(); }

    Set<String> lockedApps() { return set("locked_apps"); }
    boolean isLocked(String key) { return hasPin() && lockedApps().contains(key); }
    void setLocked(String key, boolean on) { Set<String> s = lockedApps(); if (on) s.add(key); else s.remove(key); put("locked_apps", s); }

    // ------------------------------------------------------------------ lisibilité

    static final float[] TEXT_SCALES = {1f, 1.15f, 1.3f};
    static final String[] TEXT_SCALE_NAMES = {"Normale", "Grande", "Très grande"};
    int textScale() { return Math.max(0, Math.min(2, sp.getInt("text_scale", 0))); }
    void setTextScale(int i) { sp.edit().putInt("text_scale", i).commit(); }
    boolean highContrast() { return sp.getBoolean("high_contrast", false); }
    void setHighContrast(boolean b) { sp.edit().putBoolean("high_contrast", b).commit(); }

    // ------------------------------------------------------------------ habitudes selon l'heure (local)

    boolean habitsOn() { return sp.getBoolean("habits_on", true); }
    void setHabitsOn(boolean b) { sp.edit().putBoolean("habits_on", b).apply(); if (!b) sp.edit().remove("habits").apply(); }
    Habits habits() { return new Habits(sp.getString("habits", "")); }
    void recordHabit(String key) {
        if (!habitsOn()) return;
        Habits h = habits();
        h.record(key, java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY));
        sp.edit().putString("habits", h.serialize()).apply();
    }

    // ------------------------------------------------------------------ sauvegarde

    /** Réglages exportables (liste blanche de Backup). */
    org.json.JSONObject exportBackup() throws org.json.JSONException {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("format", Backup.FORMAT);
        o.put("version", Backup.VERSION);
        org.json.JSONObject v = new org.json.JSONObject();
        java.util.Map<String, ?> all = sp.getAll();
        for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
            String k = e.getKey();
            Object x = e.getValue();
            switch (Backup.typeOf(k)) {
                case Backup.BOOL: if (x instanceof Boolean) v.put(k, x); break;
                case Backup.INT: if (x instanceof Integer) v.put(k, x); break;
                case Backup.STRING: if (x instanceof String) v.put(k, x); break;
                case Backup.SET:
                    if (x instanceof Set) { org.json.JSONArray a = new org.json.JSONArray(); for (Object i : (Set<?>) x) a.put(String.valueOf(i)); v.put(k, a); }
                    break;
                default: break;
            }
        }
        o.put("settings", v);
        return o;
    }

    /** Restaure une sauvegarde : seules les clés connues, du bon type et dans les bornes, sont reprises. Renvoie le nombre de réglages repris. */
    int importBackup(org.json.JSONObject o) throws java.io.IOException {
        if (o == null || !Backup.FORMAT.equals(o.optString("format")) || o.optInt("version", 0) != Backup.VERSION)
            throw new java.io.IOException("Ce fichier n'est pas une sauvegarde Lumen");
        org.json.JSONObject v = o.optJSONObject("settings");
        if (v == null) throw new java.io.IOException("Sauvegarde vide");
        SharedPreferences.Editor ed = sp.edit();
        int n = 0;
        java.util.Iterator<String> it = v.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object x = v.opt(k);
            switch (Backup.typeOf(k)) {
                case Backup.BOOL:
                    if (x instanceof Boolean) { ed.putBoolean(k, (Boolean) x); n++; }
                    break;
                case Backup.INT:
                    if (x instanceof Number && Backup.intOk(k, ((Number) x).longValue())) { ed.putInt(k, ((Number) x).intValue()); n++; }
                    break;
                case Backup.STRING:
                    if (x instanceof String && Backup.textOk((String) x, Backup.MAX_STRING)) { ed.putString(k, (String) x); n++; }
                    break;
                case Backup.SET:
                    if (x instanceof org.json.JSONArray && ((org.json.JSONArray) x).length() <= Backup.MAX_SET) {
                        org.json.JSONArray a = (org.json.JSONArray) x;
                        Set<String> s = new HashSet<>();
                        for (int i = 0; i < a.length(); i++) {
                            String item = a.optString(i, null);
                            if (Backup.textOk(item, Backup.MAX_ITEM)) s.add(item);
                        }
                        if ("locked_apps".equals(k)) {
                            if (!hasPin()) break;        // pas de verrou sans code
                            s.addAll(lockedApps());      // une sauvegarde ajoute des verrous, n'en retire jamais
                        }
                        ed.putStringSet(k, s);
                        n++;
                    }
                    break;
                default: break; // clé inconnue ou protégée : ignorée
            }
        }
        ed.commit();
        return n;
    }
}

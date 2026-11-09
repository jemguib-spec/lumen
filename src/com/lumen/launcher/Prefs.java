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

    static final int[] AMBIENT_MIN = {1, 3, 5, 10, 0}; // 0 = jamais
    int ambientMinutes() { return sp.getInt("ambient_min", 3); }
    void setAmbientMinutes(int m) { sp.edit().putInt("ambient_min", m).apply(); }

    boolean weatherFx() { return sp.getBoolean("weather_fx", true); }
    void setWeatherFx(boolean b) { sp.edit().putBoolean("weather_fx", b).apply(); }

    boolean sounds() { return sp.getBoolean("sounds", true); }
    void setSounds(boolean b) { sp.edit().putBoolean("sounds", b).apply(); }

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

    boolean video4k() { return sp.getBoolean("video_4k", true); }
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

    static final int[] DISK_GB = {3, 6, 10, 20};
    int videoDiskGb() { return sp.getInt("video_disk_gb", 6); }
    void setVideoDiskGb(int g) { sp.edit().putInt("video_disk_gb", g).apply(); }

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
}

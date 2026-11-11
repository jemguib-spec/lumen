// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lecture des états de Home Assistant (/api/states). */
final class HaStates {
    private HaStates() { }

    static List<HomeAssistant.Entity> load(String base, String token) throws Exception {
        List<HomeAssistant.Entity> l = parseStates(HomeAssistant.request(base, token, "GET", "/api/states", null), areas(base, token));
        cache = l;
        cacheTime = System.currentTimeMillis();
        return l;
    }

    private static volatile Map<String, String> areaCache;
    private static volatile long areaTime;
    private static volatile String areaBase = "";

    /** Pièces des appareils, relues toutes les 10 minutes ; sans réponse (vieux Home Assistant…) : pas de pièces. */
    private static Map<String, String> areas(String base, String token) {
        long now = System.currentTimeMillis();
        if (areaCache != null && areaBase.equals(base) && now - areaTime < 600_000) return areaCache;
        Map<String, String> got = null;
        try { got = HomeAssistant.parseAreas(HomeAssistant.request(base, token, "POST", "/api/template", HomeAssistant.AREA_BODY)); }
        catch (Exception ignored) { }
        if (got == null) got = areaCache != null && areaBase.equals(base) ? areaCache : new HashMap<String, String>();
        areaCache = got;
        areaBase = base;
        areaTime = now;
        return got;
    }

    // dernier état connu (recherche : pas d'attente réseau à chaque lettre)
    private static volatile List<HomeAssistant.Entity> cache;
    private static volatile long cacheTime;
    private static volatile boolean loading;

    static List<HomeAssistant.Entity> cached() { return cache; }
    static boolean stale() { return System.currentTimeMillis() - cacheTime > 60_000; }
    static void invalidate() { cacheTime = 0; }

    /** Relit les états en arrière-plan (une seule lecture à la fois), puis prévient. */
    static void refresh(final String base, final String token, final Runnable done) {
        if (loading) return;
        loading = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try { load(base, token); if (done != null) done.run(); }
                catch (Exception ignored) { }
                finally { loading = false; }
            }
        }, "lumen-ha-search").start();
    }

    private static int pct(double v) { return (int) Math.max(0, Math.min(100, Math.round(v))); }
    private static int byteOf(JSONArray a, int i) { return Math.max(0, Math.min(255, a.optInt(i, 0))); }

    /** Réponse de /api/states → appareils pilotables, triés par type puis par nom. */
    static List<HomeAssistant.Entity> parseStates(String json) throws Exception { return parseStates(json, null); }

    static List<HomeAssistant.Entity> parseStates(String json, Map<String, String> areas) throws Exception {
        JSONArray a = new JSONArray(json);
        List<HomeAssistant.Entity> out = new ArrayList<>();
        for (int i = 0; i < a.length() && i < 5000; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("entity_id");
            if (!HomeAssistant.validEntityId(id)) continue;
            JSONObject at = o.optJSONObject("attributes");
            if (at != null && at.optBoolean("hidden", false)) continue;
            String name = at == null ? "" : at.optString("friendly_name", "");
            if (name.trim().isEmpty()) name = id.substring(id.indexOf('.') + 1).replace('_', ' ');
            name = HomeAssistant.clean(name, 60);
            int level = -1, color = 0;
            boolean dim = false, group = false;
            if (at != null) {
                if (id.startsWith("light.")) {
                    // un groupe annonce ses ampoules (« entity_id ») ; les pièces et zones Hue se déclarent comme telles
                    JSONArray members = at.optJSONArray("entity_id");
                    group = members != null && members.length() > 0 || at.optBoolean("is_hue_group", false);
                    if (at.has("brightness") && !at.isNull("brightness")) level = (int) Math.round(at.optDouble("brightness", 0) * 100 / 255.0);
                    JSONArray modes = at.optJSONArray("supported_color_modes");
                    if (modes == null) dim = level >= 0;
                    else for (int m = 0; m < modes.length(); m++) if (!"onoff".equals(modes.optString(m))) dim = true;
                    JSONArray rgb = at.optJSONArray("rgb_color");
                    if (rgb != null && rgb.length() >= 3) color = 0xFF000000 | (byteOf(rgb, 0) << 16) | (byteOf(rgb, 1) << 8) | byteOf(rgb, 2);
                    else if (at.has("color_temp_kelvin") && !at.isNull("color_temp_kelvin")) color = HomeAssistant.kelvinColor(at.optInt("color_temp_kelvin", 3000));
                } else if (id.startsWith("cover.")) {
                    if (at.has("current_position") && !at.isNull("current_position")) level = pct(at.optDouble("current_position", 0));
                } else if (id.startsWith("fan.")) {
                    if (at.has("percentage") && !at.isNull("percentage")) level = pct(at.optDouble("percentage", 0));
                }
            }
            String area = areas == null ? null : areas.get(id);
            out.add(new HomeAssistant.Entity(id, name, HomeAssistant.clean(o.optString("state", ""), 30), level, color, area, dim, group));
        }
        Collections.sort(out, new Comparator<HomeAssistant.Entity>() {
            @Override public int compare(HomeAssistant.Entity x, HomeAssistant.Entity y) {
                int d = HomeAssistant.DOMAINS.indexOf(x.domain) - HomeAssistant.DOMAINS.indexOf(y.domain);
                return d != 0 ? d : x.name.compareToIgnoreCase(y.name);
            }
        });
        return out;
    }

}

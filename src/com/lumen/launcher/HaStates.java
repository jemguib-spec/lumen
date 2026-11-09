// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Lecture des états de Home Assistant (/api/states). */
final class HaStates {
    private HaStates() { }

    static List<HomeAssistant.Entity> load(String base, String token) throws Exception {
        return parseStates(HomeAssistant.request(base, token, "GET", "/api/states", null));
    }

    /** Réponse de /api/states → appareils pilotables, triés par type puis par nom. */
    static List<HomeAssistant.Entity> parseStates(String json) throws Exception {
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
            int br = -1;
            if (at != null && at.has("brightness") && !at.isNull("brightness")) br = (int) Math.round(at.optDouble("brightness", 0) * 100 / 255.0);
            out.add(new HomeAssistant.Entity(id, name, HomeAssistant.clean(o.optString("state", ""), 30), br));
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

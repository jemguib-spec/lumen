// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Films et séries du moment (TMDB). Tout passe par Net (HTTPS, liste blanche, TMDB permis seulement
 * avec une clé). Réponses gardées 6 h en mémoire, affiches en mémoire (8 Mo au plus).
 */
final class Tmdb {
    private Tmdb() { }

    static final class Title {
        String id, type, name, year, overview, poster, backdrop;
        double rating;
        String[] provider; // service connu (filtre par appli), sinon null
    }

    private static final long TTL = 6 * 3600_000L;
    private static final Map<String, Object[]> cache = new HashMap<>();
    private static final LruCache<String, Bitmap> posters = new LruCache<String, Bitmap>(8 << 20) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    private static JSONObject get(String path, String key) throws Exception {
        String url = TmdbPolicy.url(path, key);
        synchronized (cache) {
            Object[] c = cache.get(url);
            if (c != null && System.currentTimeMillis() - (Long) c[0] < TTL) return (JSONObject) c[1];
        }
        JSONObject o = Net.getJson(url, TmdbPolicy.bearer(key) ? "Bearer " + key : null);
        synchronized (cache) { cache.put(url, new Object[]{System.currentTimeMillis(), o}); }
        return o;
    }

    private static List<Title> parse(JSONObject o, String forceType, String[] provider) {
        List<Title> out = new ArrayList<>();
        JSONArray a = o.optJSONArray("results");
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.optJSONObject(i);
            if (r == null || r.optBoolean("adult")) continue;
            String type = forceType != null ? forceType : r.optString("media_type");
            if (!"movie".equals(type) && !"tv".equals(type)) continue;
            Title t = new Title();
            t.id = String.valueOf(r.optLong("id"));
            t.type = type;
            t.name = r.optString("movie".equals(type) ? "title" : "name");
            String date = r.optString("movie".equals(type) ? "release_date" : "first_air_date");
            t.year = date.length() >= 4 ? date.substring(0, 4) : "";
            t.overview = r.optString("overview");
            t.poster = r.isNull("poster_path") ? "" : r.optString("poster_path");
            t.backdrop = r.isNull("backdrop_path") ? "" : r.optString("backdrop_path");
            t.rating = r.optDouble("vote_average", 0);
            t.provider = provider;
            if (t.name.isEmpty() || t.poster.isEmpty()) continue;
            out.add(t);
        }
        return out;
    }

    /** Le plus regardé cette semaine, partout. */
    static List<Title> trending(String key) throws Exception {
        return parse(get("/trending/all/week", key), null, null);
    }

    /** Le plus populaire sur un service (films et séries mêlés). */
    static List<Title> onProvider(String key, String[] provider, int perType) throws Exception {
        String q = "?with_watch_providers=" + provider[1] + "&watch_region=FR&sort_by=popularity.desc&vote_count.gte=30";
        List<Title> m = parse(get("/discover/movie" + q, key), "movie", provider);
        List<Title> s = parse(get("/discover/tv" + q, key), "tv", provider);
        List<Title> out = new ArrayList<>();
        for (int i = 0; i < Math.max(m.size(), s.size()) && out.size() < perType * 2; i++) {
            if (i < m.size() && i < perType) out.add(m.get(i));
            if (i < s.size() && i < perType) out.add(s.get(i));
        }
        return out;
    }

    /** Sur mes applis : les meilleurs de chaque service, mêlés, sans doublon. */
    static List<Title> mine(String key, List<String[]> providers) throws Exception {
        List<List<Title>> per = new ArrayList<>();
        for (String[] p : providers) {
            try { per.add(onProvider(key, p, 5)); } catch (Exception ignored) { }
        }
        List<Title> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10; i++)
            for (List<Title> l : per)
                if (i < l.size() && seen.add(l.get(i).type + l.get(i).id)) out.add(l.get(i));
        return out;
    }

    /** Services (France, abonnement) qui proposent ce titre, parmi ceux connus de Lumen. */
    static List<String[]> providersOf(String key, Title t) throws Exception {
        List<String[]> out = new ArrayList<>();
        JSONObject r = get("/" + t.type + "/" + t.id + "/watch/providers", key).optJSONObject("results");
        JSONObject fr = r == null ? null : r.optJSONObject("FR");
        JSONArray flat = fr == null ? null : fr.optJSONArray("flatrate");
        if (flat != null) for (int i = 0; i < flat.length(); i++) {
            String[] p = TmdbPolicy.byProviderId(flat.optJSONObject(i).optInt("provider_id"));
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Affiche (petite taille), gardée en mémoire. */
    static Bitmap poster(String path, int width) {
        if (path == null || path.isEmpty()) return null;
        String k = width + path;
        Bitmap b = posters.get(k);
        if (b != null) return b;
        try {
            byte[] d = Net.getBytes("https://image.tmdb.org/t/p/w" + width + path, 2 << 20);
            b = BitmapFactory.decodeByteArray(d, 0, d.length);
            if (b != null) posters.put(k, b);
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}

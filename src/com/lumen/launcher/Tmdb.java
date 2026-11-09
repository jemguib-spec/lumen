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
        String id, type, name, year, overview, poster, backdrop, genres = "";
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
            JSONArray g = r.optJSONArray("genre_ids");
            if (g != null) {
                StringBuilder gb = new StringBuilder();
                for (int k = 0, n = 0; k < g.length() && n < 2; k++) {
                    String gn = GENRES.get(g.optInt(k));
                    if (gn != null) { gb.append(n++ > 0 ? " · " : "").append(gn); }
                }
                t.genres = gb.toString();
            }
            t.provider = provider;
            if (t.name.isEmpty() || t.poster.isEmpty()) continue;
            out.add(t);
        }
        return out;
    }

    /** Genres TMDB (films et séries) en français. */
    static final Map<Integer, String> GENRES = new HashMap<>();
    static {
        Object[] g = {28, "Action", 12, "Aventure", 16, "Animation", 35, "Comédie", 80, "Policier", 99, "Documentaire",
                18, "Drame", 10751, "Famille", 14, "Fantastique", 36, "Histoire", 27, "Horreur", 10402, "Musique",
                9648, "Mystère", 10749, "Romance", 878, "Science-fiction", 53, "Thriller", 10752, "Guerre", 37, "Western",
                10759, "Action", 10762, "Enfants", 10763, "Actualités", 10764, "Téléréalité", 10765, "Science-fiction",
                10766, "Feuilleton", 10767, "Talk-show", 10768, "Guerre"};
        for (int i = 0; i < g.length; i += 2) GENRES.put((Integer) g[i], (String) g[i + 1]);
    }

    /** Le plus regardé cette semaine, partout. type : "" (tout), "movie" ou "tv". */
    static List<Title> trending(String key, String type) throws Exception {
        return parse(get("/trending/" + (type.isEmpty() ? "all" : type) + "/week", key), type.isEmpty() ? null : type, null);
    }

    /** Le plus populaire sur un service (films et séries mêlés, ou un seul des deux). */
    static List<Title> onProvider(String key, String[] provider, int perType, String type) throws Exception {
        String q = "?with_watch_providers=" + provider[1] + "&watch_region=FR&sort_by=popularity.desc&vote_count.gte=30";
        if (!type.isEmpty()) {
            List<Title> one = parse(get("/discover/" + type + q, key), type, provider);
            return one.size() > perType * 2 ? new ArrayList<>(one.subList(0, perType * 2)) : one;
        }
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
    static List<Title> mine(String key, List<String[]> providers, String type) throws Exception {
        List<List<Title>> per = new ArrayList<>();
        for (String[] p : providers) {
            try { per.add(onProvider(key, p, 5, type)); } catch (Exception ignored) { }
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

    /** Logo officiel du titre (PNG transparent), en français de préférence ; null s'il n'y en a pas. */
    static Bitmap logo(String key, Title t) {
        try {
            JSONObject o = get("/" + t.type + "/" + t.id + "/images?include_image_language=fr,en,null", key);
            JSONArray l = o.optJSONArray("logos");
            if (l == null || l.length() == 0) return null;
            String path = null;
            for (String lang : new String[]{"fr", "en"}) {
                for (int i = 0; i < l.length() && path == null; i++) {
                    JSONObject x = l.optJSONObject(i);
                    if (x != null && lang.equals(x.optString("iso_639_1")) && x.optString("file_path").endsWith(".png")) path = x.optString("file_path");
                }
                if (path != null) break;
            }
            return path == null ? null : poster(path, 500);
        } catch (Exception e) {
            return null;
        }
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

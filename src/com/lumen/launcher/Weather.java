// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Locale;

/**
 * Météo actuelle via Open-Meteo (gratuit, sans compte ni clé). Seule la ville choisie
 * par l'utilisateur est envoyée ; rien n'est demandé tant qu'aucune ville n'est réglée.
 */
final class Weather {
    interface Callback { void onWeather(String line); }

    private static final long TTL = 30L * 60 * 1000;
    private final SharedPreferences sp;
    private final Handler main = new Handler(Looper.getMainLooper());

    Weather(Context c) { sp = c.getSharedPreferences("lumen_weather", Context.MODE_PRIVATE); }

    String city() { return sp.getString("city", ""); }
    boolean hasCity() { return !city().isEmpty(); }

    /** Retire la ville (clés une à une d'abord : la synchronisation entre TV voit la suppression). */
    void clearCity() { sp.edit().remove("city").remove("lat").remove("lon").commit(); sp.edit().clear().apply(); }

    /** Coordonnées de la ville choisie, ou null. */
    double[] coords() { return hasCity() ? new double[]{sp.getFloat("lat", 0), sp.getFloat("lon", 0)} : null; }

    /** Dernier code météo connu (WMO), -1 si inconnu. */
    int lastCode() { return hasCity() ? sp.getInt("code", -1) : -1; }

    /** Cherche une ville par son nom ; rappelle avec le nom trouvé ou null. */
    void setCity(String raw, final Callback cb) {
        // nom de ville uniquement : lettres, espaces, tirets, apostrophes ; 60 caractères au plus
        final String query = raw == null ? "" : raw.replaceAll("[^\\p{L}\\p{M} '\\-]", "").trim();
        if (query.isEmpty() || query.length() > 60) { cb.onWeather(null); return; }
        new Thread(new Runnable() {
            @Override public void run() {
                String found = null;
                try {
                    JSONObject r = get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=fr&format=json&name="
                            + URLEncoder.encode(query.trim(), "UTF-8"));
                    JSONArray res = r.optJSONArray("results");
                    if (res != null && res.length() > 0) {
                        JSONObject o = res.getJSONObject(0);
                        found = o.optString("name");
                        Sky.setLocation(o.optDouble("latitude"), o.optDouble("longitude"));
                        sp.edit().putString("city", found)
                                .putFloat("lat", (float) o.optDouble("latitude"))
                                .putFloat("lon", (float) o.optDouble("longitude"))
                                .putLong("t", 0).apply();
                    }
                } catch (Exception ignored) { }
                final String f = found;
                main.post(new Runnable() { @Override public void run() { cb.onWeather(f); } });
            }
        }, "lumen-geo").start();
    }

    /** Ligne prête à afficher, ex. « 12° · Nuageux · Paris ». Utilise le cache s'il est récent. */
    void current(final Callback cb) {
        if (!hasCity()) { cb.onWeather(""); return; }
        String cached = sp.getString("line", "");
        if (!cached.isEmpty()) cb.onWeather(cached);
        if (System.currentTimeMillis() - sp.getLong("t", 0) < TTL && !cached.isEmpty()) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject r = get(String.format(Locale.US,
                            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code&timezone=auto",
                            sp.getFloat("lat", 0), sp.getFloat("lon", 0)));
                    JSONObject cur = r.getJSONObject("current");
                    int temp = (int) Math.round(cur.getDouble("temperature_2m"));
                    final String line = temp + "° · " + describe(cur.optInt("weather_code")) + " · " + city();
                    sp.edit().putString("line", line).putInt("code", cur.optInt("weather_code", -1))
                            .putLong("t", System.currentTimeMillis()).apply();
                    main.post(new Runnable() { @Override public void run() { cb.onWeather(line); } });
                } catch (Exception ignored) { }
            }
        }, "lumen-weather").start();
    }

    /** « Demain 14° / 6° · Pluie » (prévision du lendemain, en cache 3 h). Vide sans ville. */
    void tomorrow(final Callback cb) {
        if (!hasCity()) { cb.onWeather(""); return; }
        String cached = sp.getString("tomorrow", "");
        if (!cached.isEmpty() && System.currentTimeMillis() - sp.getLong("tomorrow_t", 0) < 3 * 3600_000L) { cb.onWeather(cached); return; }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject r = get(String.format(Locale.US,
                            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto",
                            sp.getFloat("lat", 0), sp.getFloat("lon", 0)));
                    JSONObject d = r.getJSONObject("daily");
                    final String line = tomorrowLine(d.getJSONArray("temperature_2m_max").getDouble(1),
                            d.getJSONArray("temperature_2m_min").getDouble(1), d.getJSONArray("weather_code").getInt(1));
                    sp.edit().putString("tomorrow", line).putLong("tomorrow_t", System.currentTimeMillis()).apply();
                    main.post(new Runnable() { @Override public void run() { cb.onWeather(line); } });
                } catch (Exception ignored) { }
            }
        }, "lumen-weather").start();
    }

    static String tomorrowLine(double max, double min, int code) {
        return "Demain " + Math.round(max) + "° / " + Math.round(min) + "° · " + describe(code);
    }

    static String describe(int c) { return Forecast.describe(c); }

    interface ForecastCallback { void onForecast(Forecast f); }

    /**
     * Prévision du volet de l'accueil : maintenant et les jours suivants (même serveur et même
     * ville que le reste, une requête toutes les 30 min au plus). Rappelle d'abord avec le cache,
     * puis avec la version fraîche ; jamais sans ville.
     */
    void forecast(final ForecastCallback cb) {
        if (!hasCity()) return;
        final Forecast cached = Forecast.parse(sp.getString("forecast", ""));
        if (cached != null) cb.onForecast(cached);
        if (cached != null && System.currentTimeMillis() - cached.time < TTL) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject r = get(String.format(Locale.US,
                            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code"
                                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=4&timezone=auto",
                            sp.getFloat("lat", 0), sp.getFloat("lon", 0)));
                    JSONObject cur = r.getJSONObject("current");
                    JSONObject d = r.getJSONObject("daily");
                    JSONArray mx = d.getJSONArray("temperature_2m_max"), mn = d.getJSONArray("temperature_2m_min"), wc = d.getJSONArray("weather_code");
                    int n = Math.min(4, Math.min(mx.length(), Math.min(mn.length(), wc.length())));
                    int[] max = new int[n], min = new int[n], codes = new int[n];
                    for (int i = 0; i < n; i++) {
                        max[i] = (int) Math.round(mx.getDouble(i));
                        min[i] = (int) Math.round(mn.getDouble(i));
                        codes[i] = wc.getInt(i);
                    }
                    final Forecast f = new Forecast((int) Math.round(cur.getDouble("temperature_2m")), cur.optInt("weather_code", -1),
                            max, min, codes, System.currentTimeMillis());
                    sp.edit().putString("forecast", f.compact()).apply();
                    main.post(new Runnable() { @Override public void run() { cb.onForecast(f); } });
                } catch (Exception ignored) { }
            }
        }, "lumen-forecast").start();
    }

    private static JSONObject get(String url) throws Exception {
        return Net.getJson(url); // HTTPS + liste blanche (NetPolicy)
    }
}

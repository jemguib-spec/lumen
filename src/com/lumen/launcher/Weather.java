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

    void clearCity() { sp.edit().clear().apply(); }

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

    static String describe(int c) {
        if (c == 0) return "Ciel dégagé";
        if (c == 1) return "Peu nuageux";
        if (c == 2) return "Éclaircies";
        if (c == 3) return "Couvert";
        if (c == 45 || c == 48) return "Brouillard";
        if (c >= 51 && c <= 57) return "Bruine";
        if (c >= 61 && c <= 67) return "Pluie";
        if (c >= 71 && c <= 77) return "Neige";
        if (c >= 80 && c <= 82) return "Averses";
        if (c == 85 || c == 86) return "Averses de neige";
        if (c >= 95) return "Orage";
        return "—";
    }

    private static JSONObject get(String url) throws Exception {
        return Net.getJson(url); // HTTPS + liste blanche (NetPolicy)
    }
}

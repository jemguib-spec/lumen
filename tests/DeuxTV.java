// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

/**
 * Deux « TV » dans le même programme, avec le vrai code de liaison et de synchronisation (PairLink,
 * SyncStore, SyncLink, SyncCrypto, SyncState) et de vrais sockets en 127.0.0.1 : liaison avec le
 * code, puis changements dans les deux sens. Android est remplacé par des doublures (tests/doublures).
 * Lancé par tests/run.sh.
 */
public class DeuxTV {
  static int ok, ko;
  static void check(String n, boolean c) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : deux TV : " + n); } }
  static boolean waitFor(Callable<Boolean> c, long ms) throws Exception { long end = System.currentTimeMillis() + ms; while (System.currentTimeMillis() < end) { if (c.call()) return true; Thread.sleep(100); } return c.call(); }
  /** Comme sur la TV : les réglages s'écrivent sur le fil principal. */
  static void onMain(Runnable r) throws Exception { CountDownLatch l = new CountDownLatch(1); new android.os.Handler(null).post(() -> { r.run(); l.countDown(); }); l.await(5, TimeUnit.SECONDS); }

  public static void main(String[] args) throws Exception {
    Context ca = new Context(), cb = new Context();
    final SharedPreferences A = ca.getSharedPreferences("lumen", 0), B = cb.getSharedPreferences("lumen", 0);
    final SharedPreferences WA = ca.getSharedPreferences("lumen_weather", 0), WB = cb.getSharedPreferences("lumen_weather", 0);
    final int[] appliedA = {0}, appliedB = {0}; final String[] unpairedB = {null};
    final PairLink[] pa = new PairLink[1], pb = new PairLink[1];
    onMain(() -> {
      pa[0] = new PairLink(ca, new PairLink.Listener() { public void applied(int v, String n) { appliedA[0] += v; } public void unpaired(String n) { } });
      pb[0] = new PairLink(cb, new PairLink.Listener() { public void applied(int v, String n) { appliedB[0] += v; } public void unpaired(String n) { unpairedB[0] = n; } });
      // TV A, le modèle
      A.edit().putBoolean("clock", false).putInt("home_cols", 6).putString("favorites", "netflix,plex").putString("pin", "00112233445566778899aabbccddeeff:" + "ab".repeat(32))
        .putString("tmdb_key", Vault.seal("0123456789abcdef0123456789abcdef")).putString("profiles", "0:Moi|1:Léa").putString("p1.favorites", "youtube")
        .putStringSet("hidden_apps", new HashSet<>(Arrays.asList("x", "y"))).putString("remote_keys", "A-remote").commit();
      WA.edit().putString("city", "Lyon").putFloat("lat", 45.76f).putFloat("lon", 4.84f).putString("line", "cache").commit();
      // TV B, avant : d'autres réglages, sa télécommande, sa 4K
      B.edit().putBoolean("clock", true).putBoolean("sounds", false).putString("remote_keys", "B-remote").putBoolean("video_4k", true).putInt("profile_active", 0).commit();
    });
    PairLink a = pa[0], b = pb[0];
    check("identifiants des TV différents", !a.myId().equals(b.myId()));

    // ---- liaison : B reçoit (code), A envoie tout son état
    final String[][] got = {null}; final byte[][] gotKey = {null}; final String[] gotIp = {null};
    String[] rx = SyncLink.startReceiving(cb, "Chambre", b.myId(), (ip, json, key) -> { got[0] = new String[]{json}; gotKey[0] = key; gotIp[0] = ip; }, why -> { });
    final JSONObject[] payload = {null};
    onMain(() -> { try { payload[0] = new JSONObject().put("format", "lumen-sync").put("version", 3).put("from", "Salon").put("device", a.myId()).put("port", a.prepare()).put("entries", a.state()); } catch (Exception e) { throw new RuntimeException(e); } });
    String[][] hello = new String[1][]; byte[][] key = new byte[1][];
    int r = SyncLink.send(InetAddress.getByName("127.0.0.1"), TvSync.PORT, rx[0], payload[0].toString().getBytes("UTF-8"), hello, key);
    check("liaison : envoi avec le code accepté", r == SyncLink.SENT && hello[0] != null && hello[0][3].equals(b.myId()));
    check("liaison : B a reçu l'état de A", waitFor(() -> got[0] != null, 3000));
    check("liaison : même clé des deux côtés", Arrays.equals(key[0], gotKey[0]));
    onMain(() -> a.pair(hello[0][3], hello[0][1], "127.0.0.1", 0, key[0]));
    onMain(() -> { try {
      JSONObject o = new JSONObject(got[0][0]);
      b.adopt(PairLink.entriesFrom(o.getJSONObject("entries")));
      b.pair(o.getString("device"), o.getString("from"), gotIp[0], o.getInt("port"), gotKey[0]);
    } catch (Exception e) { throw new RuntimeException(e); } });
    check("liaison : B prend les réglages de A", !B.getBoolean("clock", true) && B.getInt("home_cols", 0) == 6 && "netflix,plex".equals(B.getString("favorites", null))
        && "youtube".equals(B.getString("p1.favorites", null)) && "0:Moi|1:Léa".equals(B.getString("profiles", null)));
    check("liaison : code PIN, clé TMDB (rechiffrée), météo", B.getString("pin", "").startsWith("00112233") && "V:0123456789abcdef0123456789abcdef".equals(B.getString("tmdb_key", ""))
        && "Lyon".equals(WB.getString("city", null)) && Math.abs(WB.getFloat("lat", 0) - 45.76f) < 1e-4 && WB.getString("line", null) == null);
    check("liaison : ce qui n'existe pas sur A disparaît de B", !B.contains("sounds"));
    check("liaison : B garde sa télécommande, sa 4K, son profil affiché", "B-remote".equals(B.getString("remote_keys", null)) && B.getBoolean("video_4k", false) && B.contains("profile_active"));
    check("liaison : reliées des deux côtés", a.paired() && b.paired());

    // ---- synchro permanente
    onMain(() -> { a.start(); b.start(); });
    check("premier échange réussi des deux côtés", waitFor(() -> a.lastOk() > 0 && b.lastOk() > 0, 10000));
    int before = appliedB[0];
    onMain(() -> A.edit().putBoolean("clock", true).putStringSet("hidden_apps", new HashSet<>(Arrays.asList("x"))).commit());
    check("un changement sur A arrive sur B (quelques secondes)", waitFor(() -> B.getBoolean("clock", false) && B.getStringSet("hidden_apps", null).size() == 1, 10000));
    check("B prévenu (écran à relire)", appliedB[0] > before);
    onMain(() -> { B.edit().remove("favorites").commit(); WB.edit().putString("city", "Paris").putFloat("lat", 48.85f).putFloat("lon", 2.35f).commit(); });
    check("un changement sur B arrive sur A, suppression comprise", waitFor(() -> !A.contains("favorites") && "Paris".equals(WA.getString("city", null)), 10000));
    onMain(() -> A.edit().putString("habits", "netflix:20").commit());
    int vis = appliedB[0];
    check("historique (habitudes) synchronisé", waitFor(() -> "netflix:20".equals(B.getString("habits", null)), 10000));
    check("historique repris sans relire l'écran", appliedB[0] == vis);
    onMain(() -> A.edit().putInt("home_cols", 4).commit());
    Thread.sleep(50);
    onMain(() -> B.edit().putInt("home_cols", 7).commit());
    check("deux changements presque en même temps : le plus récent gagne partout", waitFor(() -> A.getInt("home_cols", 0) == 7 && B.getInt("home_cols", 0) == 7, 12000));
    onMain(() -> A.edit().putString("remote_keys", "A-remote-2").commit());
    Thread.sleep(5000);
    check("la télécommande ne voyage jamais", "B-remote".equals(B.getString("remote_keys", null)));
    @SuppressWarnings("unchecked") Map<String, SyncState.Entry>[] ea = new Map[1], eb = new Map[1];
    // états identiques (ce qui voyage)
    Thread.sleep(4000);
    onMain(() -> { try { ea[0] = PairLink.entriesFrom(a.state()); eb[0] = PairLink.entriesFrom(b.state()); } catch (Exception e) { throw new RuntimeException(e); } });
    boolean same = true;
    for (String k : new TreeSet<>(ea[0].keySet())) { SyncState.Entry x = ea[0].get(k), y = eb[0].get(k); if (y == null || !Objects.equals(x.value, y.value)) { same = false; System.out.println("  différent : " + k); } }
    for (String k : eb[0].keySet()) if (!ea[0].containsKey(k)) { same = false; System.out.println("  seulement sur B : " + k); }
    check("au final, les deux TV sont identiques", same);

    // ---- un intrus avec une autre clé
    byte[] bogus = SyncCrypto.pairSeal(new byte[32], "{}".getBytes("UTF-8"), b.myId(), a.myId(), new java.security.SecureRandom());
    Object[] resp = null;
    try { resp = SyncLink.request(InetAddress.getByName("127.0.0.1"), 8767, "POST", PairLink.SYNC, bogus, 4096); } catch (Exception e) { }
    check("message d'une TV non reliée refusé (403)", resp != null && (Integer) resp[0] == 403);

    // ---- délier
    onMain(() -> a.unpair(true));
    check("délier sur A : B est prévenue et déliée aussi", waitFor(() -> unpairedB[0] != null && !b.paired(), 5000) && !a.paired());
    onMain(() -> { a.close(); b.close(); });
    System.out.println("Deux TV reliées : " + ok + " vérifications réussies, " + ko + " échec(s)");
    System.exit(ko == 0 ? 0 : 1);
  }
}

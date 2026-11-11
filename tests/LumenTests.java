// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Tests des parties critiques de Lumen, exécutables sur n'importe quel JDK (sans Android) :
 * politique réseau, signatures de fichiers, parseur HTTP, authentification, QR code.
 * Lancer : tests/run.sh
 */
public class LumenTests {
    static int ok, ko;

    static int countAllowed(RateLimit r, long at) { int n = 0; for (int i = 0; i < 100; i++) if (r.allow(at)) n++; return n; }

    static HttpRequest req(String method, String target, String... headers) {
        StringBuilder b = new StringBuilder(method + " " + target + " HTTP/1.1\r\n");
        for (String h : headers) b.append(h).append("\r\n");
        b.append("\r\n");
        try { return HttpRequest.read(new ByteArrayInputStream(b.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    static void phoneGate() throws Exception {
        final String H = "192.168.1.20:8765";
        // Host (DNS rebinding)
        check("page du téléphone : bon Host accepté", PhoneGate.host(req("GET", "/api/state", "Host: " + H), H) == null);
        check("page du téléphone : autre Host refusé (421)", PhoneGate.host(req("GET", "/api/state", "Host: evil.example:8765"), H) == PhoneGate.BAD_HOST);
        check("page du téléphone : Host absent refusé", PhoneGate.host(req("GET", "/api/state"), H) == PhoneGate.BAD_HOST);
        // X-Lumen
        check("API sans X-Lumen refusée (403)", PhoneGate.apiHeader(req("POST", "/api/key?k=up", "Host: " + H)) == PhoneGate.NO_HEADER);
        check("API avec X-Lumen: 0 refusée", PhoneGate.apiHeader(req("POST", "/api/key?k=up", "X-Lumen: 0")) == PhoneGate.NO_HEADER);
        check("API avec X-Lumen acceptée", PhoneGate.apiHeader(req("POST", "/api/key?k=up", "X-Lumen: 1")) == null);
        check("images sans X-Lumen acceptées (balise <img>)", PhoneGate.apiHeader(req("GET", "/api/icon?key=x")) == null);
        check("POST sur une adresse d'image : X-Lumen exigé", PhoneGate.apiHeader(req("POST", "/api/icon")) == PhoneGate.NO_HEADER);
        // session, appareil, acceptation
        HttpRequest key = req("POST", "/api/key?k=up", "X-Lumen: 1");
        check("sans session : 401", PhoneGate.access(key, false, true, true, false) == PhoneGate.NO_SESSION);
        check("cookie volé, autre appareil : 401", PhoneGate.access(key, true, false, true, false) == PhoneGate.NO_SESSION);
        check("pas encore accepté sur la TV : en attente", PhoneGate.access(key, true, true, false, false) == PhoneGate.PENDING);
        check("session acceptée : permis", PhoneGate.access(key, true, true, true, false) == null);
        // « télécommande seulement »
        check("télécommande seulement : touche permise", PhoneGate.access(key, true, true, true, true) == null);
        for (String p : new String[]{"/api/upload", "/api/favorites", "/api/backup", "/api/restore", "/api/ha", "/api/tmdb", "/api/pexels", "/api/log", "/api/lock", "/api/setting", "/api/storage/clear"})
            check("télécommande seulement : " + p + " refusé", PhoneGate.access(req("POST", p, "X-Lumen: 1"), true, true, true, true) == PhoneGate.LIMITED);
        // signature
        check("requête non signée refusée", PhoneGate.signature(key, false) == PhoneGate.BAD_SIG);
        check("requête signée acceptée", PhoneGate.signature(key, true) == null);
        check("images dispensées de signature", PhoneGate.signature(req("GET", "/api/thumb?id=1"), false) == null);
        Signer sg = new Signer("cle-du-qr");
        String sig = Signer.hmacHex("cle-du-qr".getBytes("UTF-8"), Signer.httpMessage("POST", "/api/key?k=up", 7, Signer.sha256Hex(new byte[0])));
        HttpRequest signed = req("POST", "/api/key?k=up", "X-Lumen: 1", "X-Lumen-Seq: 7", "X-Lumen-Sig: " + sig);
        check("signature d'une vraie requête vérifiée", sg.verify(7, Signer.httpMessage(signed.method, signed.target, 7, Signer.sha256Hex(new byte[0])), signed.header("x-lumen-sig")));
        HttpRequest moved = req("POST", "/api/key?k=down", "X-Lumen: 1", "X-Lumen-Seq: 8", "X-Lumen-Sig: " + sig);
        check("signature recopiée sur une autre adresse refusée", !sg.verify(8, Signer.httpMessage(moved.method, moved.target, 8, Signer.sha256Hex(new byte[0])), moved.header("x-lumen-sig")));
        // limite de débit
        RateLimit actions = new RateLimit(4, 20, 0), keys = new RateLimit(12, 30, 0);
        int passed = 0;
        for (int i = 0; i < 50; i++) if (PhoneGate.rate(req("POST", "/api/setting?k=a&v=1", "X-Lumen: 1"), actions, keys, 1000) == null) passed++;
        check("actions : rafale de 20 puis 429", passed == 20 && PhoneGate.rate(req("POST", "/api/ping"), actions, keys, 1000) == PhoneGate.SLOW);
        check("touches : leur propre limite, encore libre", PhoneGate.rate(key, actions, keys, 1000) == null);
        check("images : jamais limitées", PhoneGate.rate(req("GET", "/api/icon?key=x"), actions, keys, 1000) == null);
        check("actions : reprennent après une seconde", PhoneGate.rate(req("POST", "/api/ping"), actions, keys, 2000) == null);
        // code PIN
        for (String p : new String[]{"/api/ha", "/api/tmdb", "/api/pexels", "/api/restore", "/api/lock"})
            check("code PIN exigé : " + p, PhoneGate.pin(req("POST", p), true) == PhoneGate.PIN && PhoneGate.pin(req("POST", p), false) == null);
        check("code PIN non exigé pour une touche", PhoneGate.pin(key, true) == null);
        // WebSocket : ouverture
        String[] ws = {"Host: " + H, "Upgrade: websocket", "Origin: http://" + H, "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ=="};
        check("WebSocket : ouverture permise", PhoneGate.webSocket(req("GET", "/ws", ws), H, true, true, true, 0) == 0);
        check("WebSocket : Origin d'un autre site refusé", PhoneGate.webSocket(req("GET", "/ws", ws[0], ws[1], "Origin: http://evil.example", ws[3]), H, true, true, true, 0) == 403);
        check("WebSocket : sans Origin refusé", PhoneGate.webSocket(req("GET", "/ws", ws[0], ws[1], ws[3]), H, true, true, true, 0) == 403);
        check("WebSocket : sans Upgrade refusé", PhoneGate.webSocket(req("GET", "/ws", ws[0], ws[2], ws[3]), H, true, true, true, 0) == 403);
        check("WebSocket : sans session refusé", PhoneGate.webSocket(req("GET", "/ws", ws), H, false, true, true, 0) == 403);
        check("WebSocket : non accepté sur la TV refusé", PhoneGate.webSocket(req("GET", "/ws", ws), H, true, false, true, 0) == 403);
        check("WebSocket : autre appareil refusé", PhoneGate.webSocket(req("GET", "/ws", ws), H, true, true, false, 0) == 403);
        check("WebSocket : 3e connexion refusée (429)", PhoneGate.webSocket(req("GET", "/ws", ws), H, true, true, true, 2) == 429);
        // WebSocket : messages signés
        Signer wsSg = new Signer("cle-ws");
        String k1 = Signer.hmacHex("cle-ws".getBytes("UTF-8"), Signer.wsMessage(1, "k up"));
        check("WebSocket : touche signée acceptée", "k up".equals(PhoneGate.wsCommand("S 1 " + k1 + " k up", wsSg, null)));
        check("WebSocket : message rejoué refusé", PhoneGate.wsCommand("S 1 " + k1 + " k up", wsSg, null) == null);
        check("WebSocket : message non signé refusé", PhoneGate.wsCommand("k up", wsSg, null) == null);
        check("WebSocket : contenu modifié refusé", PhoneGate.wsCommand("S 2 " + k1 + " k down", wsSg, null) == null);
        String k3 = Signer.hmacHex("cle-ws".getBytes("UTF-8"), Signer.wsMessage(3, "k reboot"));
        check("WebSocket : touche hors liste refusée même signée", PhoneGate.wsCommand("S 3 " + k3 + " k reboot", wsSg, null) == null);
        byte[] wsKey = Seal.key("cle-ws");
        String t4 = "t1 " + Seal.seal(wsKey, "bonjour".getBytes("UTF-8"));
        String k4 = Signer.hmacHex("cle-ws".getBytes("UTF-8"), Signer.wsMessage(4, t4));
        check("WebSocket : texte signé et chiffré accepté", "t1 bonjour".equals(PhoneGate.wsCommand("S 4 " + k4 + " " + t4, wsSg, wsKey)));
        String k5 = Signer.hmacHex("cle-ws".getBytes("UTF-8"), Signer.wsMessage(5, "x"));
        check("WebSocket : commande inconnue refusée", PhoneGate.wsCommand("S 5 " + k5 + " x", wsSg, null) == null);
        check("WebSocket : sans clé, tout est refusé", PhoneGate.wsCommand("S 6 " + k1 + " k up", null, null) == null);
    }

    /** Profils : liste, prénoms, réglages personnels ou partagés. */
    static void profilesTests() {
        java.util.List<Profiles.Profile> l = Profiles.parse(null);
        check("profils : le principal existe toujours", l.size() == 1 && l.get(0).id == Profiles.MAIN);
        l = Profiles.add(l, "  Léa  ");
        check("profils : ajout", l != null && l.size() == 2 && l.get(1).id == 1 && "Léa".equals(l.get(1).name));
        check("profils : prénom déjà pris refusé (casse ignorée)", Profiles.add(l, "léa") == null);
        check("profils : prénom vide refusé", Profiles.add(l, "  ") == null && Profiles.add(l, "<>{}") == null);
        check("profils : prénom nettoyé et borné", "Jean-Pierre O'Neil".equals(Profiles.cleanName("Jean-Pierre <O'Neil>"))
                && Profiles.cleanName("abcdefghijklmnopqrstuvwxyz").length() == Profiles.MAX_NAME);
        l = Profiles.add(l, "Tom");
        check("profils : numéros jamais réutilisés", l.get(2).id == 2);
        String stored = Profiles.serialize(l);
        check("profils : enregistrement relu à l'identique", stored.equals(Profiles.serialize(Profiles.parse(stored))));
        check("profils : liste abîmée tolérée", Profiles.parse("x|1:|2:Ana|2:Bis|abc:Def|-3:Neg").size() == 2);
        check("profils : renommer", "Théo".equals(Profiles.find(Profiles.rename(l, 2, "Théo"), 2).name));
        check("profils : renommer vers un prénom pris refusé", Profiles.rename(l, 2, "Léa") == null);
        check("profils : le principal ne se supprime pas", Profiles.remove(l, Profiles.MAIN) == null);
        check("profils : supprimer", Profiles.remove(l, 1).size() == 2 && Profiles.find(Profiles.remove(l, 1), 1) == null);
        java.util.List<Profiles.Profile> full = Profiles.parse(null);
        for (int i = 0; i < 20; i++) { java.util.List<Profiles.Profile> n = Profiles.add(full, "P" + i); if (n != null) full = n; }
        check("profils : 8 au plus", full.size() == Profiles.MAX);
        // réglages personnels / communs
        check("profils : favoris personnels", Profiles.key("favorites", 3).equals("p3.favorites") && Profiles.key("favorites", 0).equals("favorites"));
        for (String shared : new String[]{"tmdb_key", "pexels_key", "ha_url", "ha_token", "pin", "pin_fails", "locked_apps", "trusted_phones", "update_from", Profiles.LIST_KEY, Profiles.ACTIVE_KEY})
            check("profils : « " + shared + " » partagé", Profiles.key(shared, 3).equals(shared));
        check("profils : propriétaire d'un réglage", Profiles.ownerOf("p3.favorites") == 3 && Profiles.ownerOf("favorites") == -1
                && Profiles.ownerOf("p3.tmdb_key") == -1 && Profiles.ownerOf("p0.favorites") == -1 && Profiles.ownerOf("pin") == -1);
    }

    /** Déclarations de la première règle CSS dont le sélecteur est exactement sel (sans les accolades). */
    static String cssRule(String css, String sel) {
        int i = 0;
        while ((i = css.indexOf(sel + "{", i)) >= 0) {
            char before = i == 0 ? '\n' : css.charAt(i - 1);
            if (before == '\n' || before == '}') { int end = css.indexOf('}', i); return end < 0 ? "" : css.substring(i + sel.length() + 1, end); }
            i += sel.length();
        }
        return "";
    }

    static void check(String name, boolean cond) {
        if (cond) ok++; else { ko++; System.out.println("ÉCHEC : " + name); }
    }

    static boolean throwsOn(String raw) {
        try { HttpRequest.read(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8))); return false; }
        catch (Exception e) { return true; }
    }

    static boolean allCitiesHaveVideo() {
        for (String[] c : VideoCatalog.CITIES) if (c.length < 2) return false;
        return true;
    }

    public static void main(String[] args) throws Exception {
        // ---- politique réseau
        // ---- catalogue fermé des vidéos d'ambiance
        int total = 0;
        java.util.Set<String> uniq = new java.util.HashSet<>();
        boolean shape = true;
        for (int k = 0; k < CosyCatalog.FILES.length; k++) {
            check("ambiance « " + CosyCatalog.NAMES[k] + " » : au moins 3 vidéos au catalogue", CosyCatalog.FILES[k].length >= 3);
            for (String f : CosyCatalog.FILES[k]) {
                total++;
                uniq.add(f);
                if (!f.startsWith("File:") || !f.matches(".*\\.(webm|ogv|ogg)$") || f.contains("_")) shape = false;
                if (CosyCatalog.kindOf(f) != k) shape = false;
            }
        }
        check("ambiances : un nom par ambiance", CosyCatalog.NAMES.length == CosyCatalog.FILES.length);
        check("ambiances : titres bien formés et retrouvés", shape);
        check("ambiances : aucun doublon", uniq.size() == total);
        check("ambiances : une seule requête suffit (50 titres max)", total <= 50);
        check("ambiances : titre avec soulignés reconnu", CosyCatalog.kindOf("File:Varna_Aquarium_video.webm") == 2);
        check("ambiances : vidéo hors catalogue refusée", CosyCatalog.kindOf("File:Black Desert Mobile Campfire.webm") == -1);
        check("ambiances : visualisation NASA refusée",
                CosyCatalog.kindOf("File:Fermi's 14-Year Time-Lapse of the Gamma-Ray Sky (SVS14399 Fermi 14Year Fireplace 4k).webm") == -1);

        // ---- code PIN
        String stored = Pin.store("4821");
        check("pin : le bon code est accepté", Pin.check("4821", stored));
        check("pin : un mauvais code est refusé", !Pin.check("4822", stored));
        check("pin : jamais stocké en clair", !stored.contains("4821"));
        check("pin : sel différent à chaque fois", !stored.equals(Pin.store("4821")));
        check("pin : format 4 chiffres exigé", !Pin.wellFormed("12a4") && !Pin.wellFormed("123") && Pin.wellFormed("0000"));
        check("pin : valeur enregistrée abîmée refusée", !Pin.check("4821", "zz:zz") && !Pin.check("4821", "") && !Pin.check("4821", null));
        check("pin : 4 erreurs sans attente", Pin.lockMillis(4) == 0);
        check("pin : 5 erreurs → 30 s", Pin.lockMillis(5) == 30_000);
        check("pin : l'attente double", Pin.lockMillis(6) == 60_000);
        check("pin : attente plafonnée à 1 h", Pin.lockMillis(50) == 3_600_000L);

        // ---- habitudes selon l'heure
        check("habitudes : 20 h = soirée", Habits.slot(20) == 4 && Habits.slot(23) == 5 && Habits.slot(3) == 0 && Habits.slot(8) == 1);
        Habits hb = new Habits("");
        for (int i = 0; i < 5; i++) hb.record("netflix/A", 20);
        for (int i = 0; i < 3; i++) hb.record("plex/B", 19);
        hb.record("canal/C", 9);
        check("habitudes : la plus ouverte le soir d'abord", hb.top(20, 3, 1).equals(java.util.Arrays.asList("netflix/A", "plex/B")));
        check("habitudes : le matin est séparé du soir", hb.top(9, 3, 1).equals(java.util.Collections.singletonList("canal/C")));
        check("habitudes : seuil minimal respecté", hb.top(9, 3, 2).isEmpty());
        check("habitudes : relues à l'identique", new Habits(hb.serialize()).serialize().equals(hb.serialize()));
        check("habitudes : texte abîmé ignoré", new Habits("x;9:a:1;4:;4:b:-2;4:c:2").serialize().equals("4:c:2"));
        Habits often = new Habits("");
        for (int i = 0; i < 100; i++) often.record("app/X", 21);
        check("habitudes : les vieilles habitudes s'estompent", often.top(21, 1, 1).size() == 1 && often.serialize().matches("5:app/X:\\d+") && Integer.parseInt(often.serialize().split(":")[2]) < 60);

        // ---- sauvegarde des réglages
        check("sauvegarde : le code PIN n'est jamais exporté", Backup.typeOf("pin") == Backup.NONE && Backup.typeOf("pin_fails") == Backup.NONE);
        check("sauvegarde : la clé Pexels n'est jamais exportée", Backup.typeOf("pexels_key") == Backup.NONE);
        check("sauvegarde : les mises à jour sont exclues", Backup.typeOf("update_ready") == Backup.NONE);
        check("sauvegarde : favoris et villes repris", Backup.typeOf("favorites") == Backup.STRING && Backup.typeOf("cities") == Backup.SET);
        check("sauvegarde : entiers hors bornes refusés", !Backup.intOk("home_cols", 99) && Backup.intOk("home_cols", 6) && !Backup.intOk("inconnu", 1));
        check("sauvegarde : images du mode veille", Backup.intOk("veille_src", -1) && Backup.intOk("veille_src", -2) && !Backup.intOk("veille_src", -3));
        check("sauvegarde : texte avec caractères de contrôle refusé", !Backup.textOk("a\u0000b", 10) && Backup.textOk("Paris", 10) && !Backup.textOk("long", 3));

        // ---- sources HDMI
        check("sources : rangée Sources en premier", AppCategory.ORDER[0] == AppCategory.SOURCES);
        check("sources : chaque rangée a un nom", AppCategory.NAMES.length == AppCategory.ORDER.length);

        // ---- aperçus des vidéos Commons
        check("aperçu : dossier MD5 comme Commons", CommonsThumb.url("File:Example.jpg", 500).startsWith("https://upload.wikimedia.org/wikipedia/commons/thumb/a/a9/Example.jpg/"));
        check("aperçu : vidéo → image JPEG 500 px", CommonsThumb.url("File:Varna Aquarium video.webm", 500).endsWith("/500px--Varna_Aquarium_video.webm.jpg"));
        check("aperçu : accents encodés", CommonsThumb.url("File:Café.webm", 330).contains("Caf%C3%A9.webm"));
        check("aperçu : seulement des tailles standard", java.util.Arrays.equals(CommonsThumb.WIDTHS, new int[]{500, 330, 960}));
        check("aperçu : adresse de l'API essayée d'abord", CommonsThumb.candidates("https://x/a.jpg", "File:A.webm").get(0).equals("https://x/a.jpg"));
        check("aperçu : adresses construites autorisées", NetPolicy.allowed(CommonsThumb.url("File:A b.webm", 500)));

        // ---- mise à jour automatique
        check("version : v4.1.0 → 4.1.0", "4.1.0".equals(UpdatePolicy.version("v4.1.0")));
        check("version : étiquette fantaisiste refusée", UpdatePolicy.version("latest-build") == null);
        check("version : 4.10 plus récente que 4.9", UpdatePolicy.compare("4.10", "4.9") > 0);
        check("version : 4.0.1 plus récente que 4.0", UpdatePolicy.compare("4.0.1", "4.0") > 0);
        check("version : identiques", UpdatePolicy.compare("4.1.0", "4.1") == 0);
        check("empreinte SHA-256 valide", UpdatePolicy.sha256Ok("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"));
        check("empreinte SHA-256 trop courte refusée", !UpdatePolicy.sha256Ok("9f86d0"));
        check("empreinte : hexadécimal", "00ff10".equals(UpdatePolicy.hex(new byte[]{0, (byte) 255, 16})));
        check("mise à jour : uniquement depuis le dépôt du projet", UpdatePolicy.APK.startsWith("https://raw.githubusercontent.com/jemguib-spec/lumen/main/"));
        check("mise à jour : fichier propre à la version", UpdatePolicy.apkUrl("Lumen-4.18.2.apk", "4.18.2").equals(UpdatePolicy.BASE + "Lumen-4.18.2.apk"));
        check("mise à jour : sans nom de fichier, Lumen.apk", UpdatePolicy.apkUrl(null, "4.18.2").equals(UpdatePolicy.APK));
        check("mise à jour : nom de fichier étranger ignoré", UpdatePolicy.apkUrl("../../evil.apk", "4.18.2").equals(UpdatePolicy.APK)
                && UpdatePolicy.apkUrl("Lumen-4.18.1.apk", "4.18.2").equals(UpdatePolicy.APK) && UpdatePolicy.apkUrl("Lumen-x.apk", "x").equals(UpdatePolicy.APK));
        check("fichier : signature ZIP reconnue", UpdatePolicy.looksLikeApk(new byte[]{'P', 'K', 3, 4}));
        check("fichier : autre format refusé", !UpdatePolicy.looksLikeApk(new byte[]{'<', 'h', 't', 'm'}));
        check("GitHub (fichiers du dépôt) autorisé pour la mise à jour", NetPolicy.allowed(UpdatePolicy.LATEST));
        check("API GitHub non nécessaire, refusée", !NetPolicy.allowed("https://api.github.com/repos/x/y"));
        // GitHub Releases : seulement les fichiers des Releases du dépôt de Lumen
        String rel = UpdatePolicy.releaseUrl("4.20.0");
        check("Release : adresse recomposée", "https://github.com/jemguib-spec/lumen/releases/download/v4.20.0/Lumen-4.20.0.apk".equals(rel));
        check("Release : fichier autorisé", NetPolicy.allowed(rel));
        check("Release : redirection vers objects.githubusercontent.com autorisée", NetPolicy.allowed("https://objects.githubusercontent.com/github-production-release-asset/abc"));
        check("Release : redirection vers release-assets.githubusercontent.com autorisée", NetPolicy.allowed("https://release-assets.githubusercontent.com/github-production-release-asset/abc"));
        check("github.com : page du dépôt refusée", !NetPolicy.allowed("https://github.com/jemguib-spec/lumen"));
        check("github.com : autre dépôt refusé", !NetPolicy.allowed("https://github.com/autre/depot/releases/download/v1/x.apk"));
        check("github.com : dossier des Releases sans fichier refusé", !NetPolicy.allowed("https://github.com/jemguib-spec/lumen/releases/download/"));
        check("github.com : remontée « .. » dans le chemin refusée", !NetPolicy.allowed("https://github.com/jemguib-spec/lumen/releases/download/../../autre/x.apk"));
        check("github.com : chemin encodé refusé", !NetPolicy.allowed("https://github.com/jemguib-spec/lumen/releases/download/%2e%2e/x.apk"));
        check("github.com : sous-domaine piégé refusé", !NetPolicy.allowed("https://github.com.evil.net/jemguib-spec/lumen/releases/download/v1/x.apk"));
        check("latest.json : url de la Release de cette version utilisée", rel.equals(UpdatePolicy.apkUrl(null, "4.20.0", rel)));
        check("latest.json : url d'une autre version ignorée (dist/Lumen.apk)", UpdatePolicy.APK.equals(UpdatePolicy.apkUrl(null, "4.20.0", UpdatePolicy.releaseUrl("4.19.0"))));
        check("latest.json : url étrangère ignorée", UpdatePolicy.APK.equals(UpdatePolicy.apkUrl(null, "4.20.0", "https://evil.example/Lumen-4.20.0.apk")));
        check("latest.json : url absente, file propre à la version", (UpdatePolicy.BASE + "Lumen-4.20.0.apk").equals(UpdatePolicy.apkUrl("Lumen-4.20.0.apk", "4.20.0", null)));
        check("latest.json : version invalide → pas d'adresse de Release", UpdatePolicy.releaseUrl("4.20.0/../x") == null);
        check("nouveautés : puces et titres nettoyés", "· Recherche vocale\n· Assistant d'installation".equals(UpdatePolicy.notes("## 4.20.0\n\n- Recherche vocale\r\n* Assistant d'installation\n")));
        check("nouveautés : caractères de contrôle retirés", "ab".equals(UpdatePolicy.notes("a\u0007b")));
        check("nouveautés : texte long coupé", UpdatePolicy.notes(new String(new char[1000]).replace('\0', 'x')).length() <= UpdatePolicy.NOTES_MAX);
        check("nouveautés : null → vide", UpdatePolicy.notes(null).isEmpty());
        // langue
        java.util.Map<String, String> en = L.parse("# commentaire\nBonjour\tHello\nLigne\\nsuite\tLine\\nnext\nsans tab\n");
        check("langue : dictionnaire lu", "Hello".equals(en.get("Bonjour")) && en.size() == 2);
        check("langue : retours à la ligne décodés", "Line\nnext".equals(en.get("Ligne\nsuite")));
        L.load("en", en);
        check("langue : traduit", "Hello".equals(L.t("Bonjour")) && "Good evening".equals(L.t("Good evening")) == true);
        check("langue : texte inconnu rendu tel quel", "Au revoir".equals(L.t("Au revoir")));
        check("langue : L.all", "Hello".equals(L.all(new String[]{"Bonjour", "x"})[0]));
        L.load("fr", null);
        check("langue : retour au français", "Bonjour".equals(L.t("Bonjour")) && L.french());
        check("langue : choix système", "fr".equals(L.choose("", "fr")) && "en".equals(L.choose("", "de")) && "fr".equals(L.choose("fr", "en")));
        // profil enfant
        java.util.List<Profiles.Profile> kl = Profiles.add(Profiles.parse(null), "Tom");
        check("profil enfant : non par défaut", !kl.get(1).kid);
        kl = Profiles.setKid(kl, kl.get(1).id, true);
        check("profil enfant : marqué et conservé après lecture", kl.get(1).kid && Profiles.parse(Profiles.serialize(kl)).get(1).kid);
        check("profil enfant : enregistré comme « :k »", Profiles.serialize(kl).endsWith(":Tom:k"));
        check("profil enfant : le principal ne peut pas l'être", Profiles.setKid(kl, Profiles.MAIN, true) == null && !Profiles.parse("0:Moi:k").get(0).kid);
        check("profil enfant : renommer garde le drapeau", Profiles.rename(kl, kl.get(1).id, "Tim").get(1).kid);
        check("profil enfant : retiré", !Profiles.setKid(kl, kl.get(1).id, false).get(1).kid);
        // coffre des secrets (format AES-GCM ; la clé Keystore est testée sur la TV, ici une clé AES ordinaire)
        javax.crypto.KeyGenerator kg = javax.crypto.KeyGenerator.getInstance("AES");
        kg.init(256);
        javax.crypto.SecretKey vk = kg.generateKey(), vk2 = kg.generateKey();
        String vsealed = VaultCrypto.seal(vk, "jeton-home-assistant-é");
        check("coffre : format K1.<nonce>.<chiffré>", vsealed.matches("K1\\.[0-9a-f]{24}\\.[0-9a-f]+"));
        check("coffre : déchiffré à l'identique", "jeton-home-assistant-é".equals(VaultCrypto.open(vk, vsealed)));
        check("coffre : deux chiffrements du même secret diffèrent (nonce)", !vsealed.equals(VaultCrypto.seal(vk, "jeton-home-assistant-é")));
        check("coffre : autre clé refusée", VaultCrypto.open(vk2, vsealed) == null);
        String tampered = vsealed.substring(0, vsealed.length() - 1) + (vsealed.endsWith("0") ? "1" : "0");
        check("coffre : secret altéré refusé", VaultCrypto.open(vk, tampered) == null);
        check("coffre : valeur en clair reconnue comme non chiffrée", !VaultCrypto.sealed("abcd1234") && VaultCrypto.open(vk, "abcd1234") == null);
        check("coffre : mal formé refusé", VaultCrypto.open(vk, "K1.zz.yy") == null && VaultCrypto.open(vk, null) == null);
        // assistant d'installation
        check("installation : Lumen est l'accueil", InstallPolicy.isHome("fr.lumen.accueil"));
        check("installation : choix non fait (android) ≠ accueil", !InstallPolicy.isHome("android") && !InstallPolicy.isHome(null));
        java.util.List<String> cmd = InstallPolicy.commands("192.168.1.42", "com.google.android.apps.tv.launcherx", true);
        check("installation : adb connect avec l'adresse de la TV", cmd.get(0).equals("adb connect 192.168.1.42:5555"));
        check("installation : accueil Google désactivé pour l'utilisateur 0", cmd.get(1).equals("adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx"));
        check("installation : assistant Google désactivé", cmd.get(2).contains("com.google.android.tungsten.setupwraith"));
        check("installation : historique pour Reprendre", cmd.get(3).equals("adb shell appops set fr.lumen.accueil GET_USAGE_STATS allow"));
        check("installation : adresse inconnue → marque-place", InstallPolicy.commands(null, null, false).get(0).contains("ADRESSE-IP-DE-LA-TV"));
        check("installation : Android TV ancien modèle", InstallPolicy.commands(null, "com.google.android.tvlauncher", false).get(1).endsWith("com.google.android.tvlauncher"));
        check("installation : sans assistant Google, 3 commandes", InstallPolicy.commands(null, null, false).size() == 3);
        check("installation : retour en arrière réactive l'accueil d'origine", InstallPolicy.undoCommands(null, true).get(0).equals("adb shell pm enable com.google.android.apps.tv.launcherx") && InstallPolicy.undoCommands(null, true).size() == 2);

        // ---- page Applications : rangées et recherche
        check("rangée : Netflix en vidéo", AppCategory.of("com.netflix.ninja", "Netflix", -1) == AppCategory.VIDEO);
        check("rangée : Crunchyroll en vidéo", AppCategory.of("com.crunchyroll.crunchyroid", "Crunchyroll", -1) == AppCategory.VIDEO);
        check("rangée : Canal+ en vidéo", AppCategory.of("com.canal.android.canal", "CANAL+", -1) == AppCategory.VIDEO);
        check("rangée : YouTube Music en musique", AppCategory.of("com.google.android.youtube.tvmusic", "YouTube Music", -1) == AppCategory.MUSIC);
        check("rangée : Spotify en musique", AppCategory.of("com.spotify.tv.android", "Spotify", -1) == AppCategory.MUSIC);
        check("rangée : Proton VPN dans outils", AppCategory.of("ch.protonvpn.android", "Proton VPN", -1) == AppCategory.OTHER);
        check("rangée : paramètres TV dans outils", AppCategory.of("com.android.tv.settings", "Paramètres", -1) == AppCategory.OTHER);
        check("rangée : catégorie déclarée prioritaire", AppCategory.of("com.exemple.x", "X", 0) == AppCategory.GAMES);
        check("recherche : sans accents", AppCategory.matches("Paramètres", "parametres"));
        check("recherche : majuscules ignorées", AppCategory.matches("CANAL+", "canal"));
        check("recherche : « canal plus »", AppCategory.matches("CANAL+", "canal plus"));
        check("recherche : pas de faux positif", !AppCategory.matches("Netflix", "plex"));

        // ---- « Reprendre » au-dessus des applis
        check("épisode : S4 · É3 · titre", "S4 · É3 · Le Monstre".equals(WatchNextFormat.subtitle("4", "3", "Le Monstre")));
        check("épisode : film sans saison", "".equals(WatchNextFormat.subtitle(null, " ", null)));
        check("progression : moitié", Math.abs(WatchNextFormat.progress(30 * 60_000L, 60 * 60_000L) - 0.5f) < 0.001f);
        check("progression : inconnue sans durée", WatchNextFormat.progress(1000, 0) < 0);
        check("temps restant : 32 min", "Il reste 32 min".equals(WatchNextFormat.remaining(28 * 60_000L, 60 * 60_000L)));
        check("temps restant : 1 h 05", "Il reste 1 h 05".equals(WatchNextFormat.remaining(10 * 60_000L, 75 * 60_000L)));
        check("affiche : HTTPS d'un service accepté", WatchNextFormat.posterUrlOk("https://occ-0-56-55.1.nflxso.net/dnm/api/v6/x.jpg"));
        check("affiche : HTTP refusé", !WatchNextFormat.posterUrlOk("http://cdn.example.com/a.jpg"));
        check("affiche : réseau local refusé", !WatchNextFormat.posterUrlOk("https://192.168.1.10/a.jpg"));
        check("affiche : nom local refusé", !WatchNextFormat.posterUrlOk("https://nas.local/a.jpg"));
        check("affiche : identifiants refusés", !WatchNextFormat.posterUrlOk("https://user:pw@cdn.example.com/a.jpg"));
        check("affiche : port exotique refusé", !WatchNextFormat.posterUrlOk("https://cdn.example.com:8443/a.jpg"));

        // ---- Pexels (facultatif)
        NetPolicy.setPexelsEnabled(false);
        check("Pexels injoignable sans clé", !NetPolicy.allowed("https://api.pexels.com/videos/search?query=x"));
        NetPolicy.setPexelsEnabled(true);
        check("Pexels joignable avec clé", NetPolicy.allowed("https://api.pexels.com/videos/search?query=x"));
        check("Pexels : lecteur Vimeo toujours refusé", !NetPolicy.allowed("https://player.vimeo.com/external/1.hd.mp4"));
        check("Pexels : faux sous-domaine refusé", !NetPolicy.allowed("https://api.pexels.com.evil.net/x"));
        NetPolicy.setPexelsEnabled(false);
        check("clé Pexels valide acceptée", Pexels.validKey("AbCdEf0123456789AbCdEf0123456789AbCdEf0123456789AbCdEf01"));
        check("clé Pexels avec caractères spéciaux refusée", !Pexels.validKey("abc\r\nX-Evil: 1aaaaaaaaaaaaaaaaaaaa"));
        check("clé Pexels trop courte refusée", !Pexels.validKey("abc123"));
        java.util.List<Pexels.File> pf = new java.util.ArrayList<>();
        pf.add(new Pexels.File("https://videos.pexels.com/a.mp4", "video/mp4", 1920, 1080));
        pf.add(new Pexels.File("https://videos.pexels.com/b.mp4", "video/mp4", 3840, 2160));
        pf.add(new Pexels.File("http://videos.pexels.com/c.mp4", "video/mp4", 2560, 1440));
        pf.add(new Pexels.File("https://videos.pexels.com/d.mp4", "video/quicktime", 2560, 1440));
        check("Pexels : 4K choisie en mode 4K", Pexels.choose(pf, 2160, 720).height == 2160);
        check("Pexels : HD choisie en mode HD", Pexels.choose(pf, 1080, 720).height == 1080);
        check("Pexels : lien non chiffré ou format inconnu ignoré", Pexels.choose(pf, 1440, 1200) == null);
        check("Pexels : titre lisible tiré de l'adresse",
                "Aerial view of paris".equals(Pexels.titleFromUrl("https://www.pexels.com/video/aerial-view-of-paris-857195/", "x")));
        check("Pexels : une requête par ambiance", Pexels.COSY_QUERIES.length == CosyCatalog.NAMES.length);

        // ---- catalogue fermé des fonds vidéo
        int vtotal = VideoCatalog.count();
        java.util.Set<String> vuniq = new java.util.HashSet<>(java.util.Arrays.asList(VideoCatalog.allTitles().split("\\|")));
        boolean vshape = true;
        for (String f : vuniq) {
            if (!f.startsWith("File:") || !f.matches(".*\\.(webm|ogv|ogg)$") || f.contains("_")) vshape = false;
            if (VideoCatalog.groupOf(f) == null) vshape = false;
        }
        check("fonds vidéo : titres bien formés et retrouvés", vshape);
        check("fonds vidéo : aucun doublon", vuniq.size() == vtotal);
        check("fonds vidéo : une seule requête suffit (50 titres max)", vtotal <= 50);
        check("fonds vidéo : chaque ville a au moins une vidéo", allCitiesHaveVideo());
        check("fonds vidéo : au moins 6 paysages et 6 vidéos de l'espace",
                VideoCatalog.NATURE_FILES.length >= 6 && VideoCatalog.SPACE_FILES.length >= 6);
        check("fonds vidéo : Paris est au catalogue", VideoCatalog.isCity("Paris"));
        check("fonds vidéo : ville retirée ignorée", !VideoCatalog.isCity("Londres"));
        check("fonds vidéo : conférence hors catalogue refusée", VideoCatalog.groupOf("File:NASA press conference.webm") == null);
        check("fonds vidéo : simulation NASA SVS refusée",
                VideoCatalog.groupOf("File:Clouds 101 (SVS20383 - CloudsFormingRain ProRes 422LT).webm") == null);
        check("fonds vidéo : NASA SVS retiré des serveurs autorisés", !NetPolicy.allowed("https://svs.gsfc.nasa.gov/api/search/"));

        check("NASA retiré (photos Espace issues des sélections Commons)", !NetPolicy.allowed("https://images-api.nasa.gov/search?q=x"));
        check("https Commons autorisé", NetPolicy.allowed("https://commons.wikimedia.org/w/api.php"));
        check("https Wikimedia autorisé", NetPolicy.allowed("https://upload.wikimedia.org/a.jpg"));
        check("HA : adresse locale complétée", "http://192.168.1.20:8123".equals(HomeAssistant.normalizeUrl("192.168.1.20:8123"))
                && "http://homeassistant.local:8123".equals(HomeAssistant.normalizeUrl(" homeassistant.local:8123/ ")));
        check("HA : empreinte de certificat (SHA-256)", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".equals(HomeAssistant.fingerprint("abc".getBytes())));
        check("HA : certificat auto-signé jamais accepté hors du réseau local", HomeAssistant.localCertPin("https://maison.ui.nabu.casa") == null
                && HomeAssistant.localCertPin("http://192.168.1.20:8123") == null);
        check("HA : HTTPS distant accepté", "https://maison.ui.nabu.casa".equals(HomeAssistant.normalizeUrl("https://maison.ui.nabu.casa")));
        check("HA : jamais de jeton en clair sur Internet", HomeAssistant.normalizeUrl("http://exemple.com:8123") == null
                && HomeAssistant.normalizeUrl("http://8.8.8.8:8123") == null && HomeAssistant.normalizeUrl("http://172.32.0.1") == null);
        check("HA : adresses piégées refusées", HomeAssistant.normalizeUrl("http://user:pw@192.168.1.2") == null
                && HomeAssistant.normalizeUrl("http://192.168.1.2/api?x=1") == null && HomeAssistant.normalizeUrl("ftp://192.168.1.2") == null
                && HomeAssistant.normalizeUrl("javascript:alert(1)") == null);
        check("HA : jeton", HomeAssistant.validToken("eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ4In0.abc_DEF-123")
                && !HomeAssistant.validToken("court") && !HomeAssistant.validToken("eyJhbGciOiJIUzI1NiJ9\r\nX-Evil: 1"));
        check("HA : appareils en liste fermée (pas de serrure ni d'alarme)", HomeAssistant.validEntityId("light.salon")
                && HomeAssistant.validEntityId("scene.soiree_film") && !HomeAssistant.validEntityId("lock.porte")
                && !HomeAssistant.validEntityId("alarm_control_panel.maison") && !HomeAssistant.validEntityId("light.salon/../x"));
        HomeAssistant.Entity lamp = new HomeAssistant.Entity("light.salon", "Salon", "on", 60);
        check("HA : actions et états lisibles", "toggle".equals(HomeAssistant.service(lamp)) && "Allumée · 60 %".equals(HomeAssistant.label(lamp))
                && "turn_on".equals(HomeAssistant.service(new HomeAssistant.Entity("scene.film", "Film", "scening", -1)))
                && "Fermé".equals(HomeAssistant.label(new HomeAssistant.Entity("cover.volet", "Volet", "closed", -1))));
        // ---- Maison : niveau réglable, couleur, pièces
        HomeAssistant.Entity dimLamp = new HomeAssistant.Entity("light.salon", "Salon", "on", 60, 0xFFFF8800, "Salon", true);
        HomeAssistant.Entity plainLamp = new HomeAssistant.Entity("light.cave", "Cave", "on", -1, 0, "", false);
        HomeAssistant.Entity blind = new HomeAssistant.Entity("cover.volet", "Volet", "open", 40, 0, "Chambre", false);
        check("maison : seuls les appareils à niveau se règlent", HomeAssistant.adjustable(dimLamp) && !HomeAssistant.adjustable(plainLamp)
                && HomeAssistant.adjustable(blind) && !HomeAssistant.adjustable(new HomeAssistant.Entity("switch.prise", "Prise", "on", -1))
                && !HomeAssistant.adjustable(new HomeAssistant.Entity("light.x", "X", "unavailable", 50, 0, "", true)));
        String[] lr = HomeAssistant.levelRequest(dimLamp, 30), l0 = HomeAssistant.levelRequest(dimLamp, 0), cr = HomeAssistant.levelRequest(blind, 250);
        check("maison : réglage d'une lumière (0 = éteindre) et d'un volet (borné à 100)",
                "/api/services/light/turn_on".equals(lr[0]) && "{\"entity_id\":\"light.salon\",\"brightness_pct\":30}".equals(lr[1])
                && "/api/services/light/turn_off".equals(l0[0]) && "{\"entity_id\":\"light.salon\"}".equals(l0[1])
                && "/api/services/cover/set_cover_position".equals(cr[0]) && cr[1].endsWith("\"position\":100}"));
        check("maison : pas de réglage pour une prise ni pour un identifiant douteux",
                HomeAssistant.levelRequest(new HomeAssistant.Entity("switch.prise", "Prise", "on", -1), 50) == null);
        check("maison : blanc chaud ambré, blanc froid bleuté", ((HomeAssistant.kelvinColor(2200) >> 16) & 255) > (HomeAssistant.kelvinColor(2200) & 255) + 80
                && (HomeAssistant.kelvinColor(6500) & 255) > ((HomeAssistant.kelvinColor(6500) >> 16) & 255)
                && HomeAssistant.kelvinColor(100) == HomeAssistant.kelvinColor(2000));
        java.util.Map<String, String> areas = HomeAssistant.parseAreas("light.salon|Salon\nscene.film|\nlock.porte|Entrée\npas une ligne\ncover.volet|None\nswitch.prise| Cuisine \n");
        check("maison : pièces lues, appareils sans pièce ou non pilotables écartés", areas.size() == 2
                && "Salon".equals(areas.get("light.salon")) && "Cuisine".equals(areas.get("switch.prise")));
        check("maison : la demande des pièces est un JSON à une ligne, sans saut de ligne brut",
                HomeAssistant.AREA_BODY.startsWith("{\"template\":\"") && HomeAssistant.AREA_BODY.endsWith("\"}")
                && !HomeAssistant.AREA_BODY.contains("\n") && HomeAssistant.AREA_BODY.contains("\\n"));
        java.util.List<HomeAssistant.Entity> ents = java.util.Arrays.asList(lamp, new HomeAssistant.Entity("switch.prise", "Prise", "off", -1));
        check("HA : appareils choisis, sinon tous", HomeAssistant.shown(ents, new java.util.HashSet<String>(), 10).size() == 2
                && HomeAssistant.shown(ents, new java.util.HashSet<>(java.util.Arrays.asList("switch.prise")), 10).size() == 1);
        HomeAssistant.Entity salonGroup = new HomeAssistant.Entity("light.groupe_salon", "Salon", "on", 60, 0, "", true, true);
        java.util.List<HomeAssistant.Entity> house = java.util.Arrays.asList(salonGroup, lamp,
                new HomeAssistant.Entity("light.lumiere_10", "Lumière 10", "off", -1), new HomeAssistant.Entity("switch.prise", "Prise", "off", -1));
        java.util.Set<String> none = new java.util.HashSet<String>(), picked = new java.util.HashSet<>(java.util.Arrays.asList("light.lumiere_10"));
        check("HA : avec des groupes de lumières, les ampoules une à une sont masquées (sauf demande ou choix sur le téléphone)",
                HomeAssistant.shown(house, none, 10).size() == 2 && HomeAssistant.shown(house, none, 10).get(0) == salonGroup
                && HomeAssistant.hiddenBulbs(house, none) == 2 && HomeAssistant.shown(house, none, 10, true).size() == 4
                && HomeAssistant.shown(house, picked, 10).size() == 1 && HomeAssistant.hiddenBulbs(house, picked) == 0
                && HomeAssistant.hiddenBulbs(ents, none) == 0);
        java.util.Map<String, String> where = new java.util.HashMap<>();
        where.put("light.plan_de_travail", "Cuisine"); where.put("light.evier", "Cuisine"); where.put("light.couloir", "Entrée");
        check("HA : un groupe sans pièce prend celle de ses ampoules, sinon la pièce qui porte son nom",
                "Cuisine".equals(HomeAssistant.groupArea("Bas", java.util.Arrays.asList("light.plan_de_travail", "light.couloir", "light.evier"), where))
                && "Entrée".equals(HomeAssistant.groupArea("entrée", new java.util.ArrayList<String>(), where))
                && HomeAssistant.groupArea("Garage", java.util.Arrays.asList("light.inconnue"), where).isEmpty()
                && HomeAssistant.groupArea("Garage", new java.util.ArrayList<String>(), null).isEmpty());
        java.util.List<HomeAssistant.Entity> rooms = java.util.Arrays.asList(
                new HomeAssistant.Entity("light.groupe_salon", "Salon", "on", 60, 0, "Salon", true, true),
                new HomeAssistant.Entity("light.canape", "Canapé", "on", 60, 0, "Salon", true),
                new HomeAssistant.Entity("light.tele", "Télé", "off", 60, 0, "Salon", true),
                new HomeAssistant.Entity("light.toilette", "toilette", "on", -1, 0, "toilette", false));
        check("HA : chaque pièce montre ses appareils (une pièce sans groupe garde ses ampoules), lumières allumées sans doublon",
                HomeAssistant.inRoom(rooms, "Salon").size() == 3 && HomeAssistant.shown(HomeAssistant.inRoom(rooms, "Salon"), none, 10).size() == 1
                && HomeAssistant.shown(HomeAssistant.inRoom(rooms, "toilette"), none, 10).size() == 1
                && HomeAssistant.lightsOn(rooms) == 2 && HomeAssistant.lightsOn(java.util.Arrays.asList(salonGroup)) == 1);
        try {
            String chunked = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\n[{\"a\r\n3\r\n\":1\r\n2\r\n}]\r\n0\r\n\r\n";
            String plain = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n[]";
            check("HA : réponses HTTP lues (chunked et longueur)", "[{\"a\":1}]".equals(HomeAssistant.parseHttpResponse(new java.io.ByteArrayInputStream(chunked.getBytes("UTF-8"))))
                    && "[]".equals(HomeAssistant.parseHttpResponse(new java.io.ByteArrayInputStream(plain.getBytes("UTF-8")))));
            boolean refused = false;
            try { HomeAssistant.parseHttpResponse(new java.io.ByteArrayInputStream("HTTP/1.1 302 Found\r\nLocation: http://ailleurs\r\n\r\n".getBytes("UTF-8"))); }
            catch (java.io.IOException e) { refused = true; }
            check("HA : redirection jamais suivie", refused);
        } catch (Exception e) { check("HA : réponses HTTP lues", false); }
        try {
            check("signature : HMAC-SHA256 (RFC 4231, cas 2)", "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"
                    .equals(Signer.hmacHex("Jefe".getBytes("UTF-8"), "what do ya want for nothing?")));
            Signer sg = new Signer("cle-de-test");
            String msg = Signer.httpMessage("POST", "/api/key?k=up", 1, Signer.sha256Hex(new byte[0]));
            String sig = Signer.hmacHex("cle-de-test".getBytes("UTF-8"), msg);
            check("signature : requête juste acceptée", sg.verify(1, msg, sig));
            check("signature : rejeu refusé", !sg.verify(1, msg, sig));
            String msg2 = Signer.httpMessage("POST", "/api/key?k=down", 2, Signer.sha256Hex(new byte[0]));
            check("signature : requête modifiée refusée", !sg.verify(2, msg2, sig));
            check("signature : mauvaise clé refusée", !sg.verify(3, msg, Signer.hmacHex("autre".getBytes("UTF-8"), msg)));
            check("signature : SHA-256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".equals(Signer.sha256Hex(new byte[0])));
        } catch (Exception e) { check("signature", false); }
        try {
            check("WebSocket : clé d'acceptation (exemple de la RFC 6455)", "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=".equals(WsFrames.accept("dGhlIHNhbXBsZSBub25jZQ==")));
            check("WebSocket : clé du navigateur vérifiée", WsFrames.validKey("dGhlIHNhbXBsZSBub25jZQ==") && !WsFrames.validKey("x\r\nEvil: 1") && !WsFrames.validKey(null));
            // trame masquée « k up » (comme l'envoie le navigateur)
            byte[] msg = "k up".getBytes("UTF-8"), mask = {1, 2, 3, 4};
            byte[] fr = new byte[2 + 4 + msg.length];
            fr[0] = (byte) 0x81; fr[1] = (byte) (0x80 | msg.length);
            System.arraycopy(mask, 0, fr, 2, 4);
            for (int i = 0; i < msg.length; i++) fr[6 + i] = (byte) (msg[i] ^ mask[i & 3]);
            Object[] got = WsFrames.read(new java.io.ByteArrayInputStream(fr));
            check("WebSocket : trame masquée lue", (Integer) got[0] == WsFrames.TEXT && "k up".equals(new String((byte[]) got[1], "UTF-8")));
            boolean refused = false;
            try { WsFrames.read(new java.io.ByteArrayInputStream(new byte[]{(byte) 0x81, 4, 'k', ' ', 'u', 'p'})); } catch (java.io.IOException e) { refused = true; }
            check("WebSocket : trame non masquée refusée", refused);
            byte[] out = WsFrames.frame(WsFrames.TEXT, "ok".getBytes("UTF-8"));
            check("WebSocket : trame de la TV", out.length == 4 && (out[0] & 255) == 0x81 && out[1] == 2);
        } catch (Exception e) { check("WebSocket", false); }
        check("TMDB : clés acceptées", TmdbPolicy.validKey("0123456789abcdef0123456789abcdef")
                && TmdbPolicy.validKey("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIxMjM0NTY3ODkwYWJjZGVmIn0.abcdefghijklmnopqrstu-_")
                && !TmdbPolicy.validKey("abc") && !TmdbPolicy.validKey("0123456789abcdef0123456789abcdeG")
                && !TmdbPolicy.validKey("eyJ" + "a".repeat(80) + "\r\nX: y"));
        check("TMDB : clé v3 dans l'adresse, jeton v4 jamais dans l'adresse",
                TmdbPolicy.url("/trending/all/week", "0123456789abcdef0123456789abcdef").endsWith("&api_key=0123456789abcdef0123456789abcdef")
                && !TmdbPolicy.url("/x", "eyJabc").contains("eyJ") && TmdbPolicy.url("/x?a=1", "k").contains("?a=1&language=fr-FR"));
        check("TMDB : hôtes fermés sans clé", !NetPolicy.allowed("https://api.themoviedb.org/3/trending/all/week"));
        check("TMDB : mes applis seulement", TmdbPolicy.installed(new java.util.HashSet<>(java.util.Arrays.asList("com.netflix.ninja", "com.x"))).size() == 1
                && "Netflix".equals(TmdbPolicy.byProviderId(8)[2]));
        check("recherche : début de mot et initiales", SmartSearch.score("YouTube Music", "mus") > 0 && SmartSearch.score("YouTube Music", "ytm") > 0);
        check("recherche : fautes de frappe tolérées", SmartSearch.score("Netflix", "netflx") > 0 && SmartSearch.score("Disney+", "disnay") > 0
                && SmartSearch.score("Netflix", "zzz") == 0);
        check("recherche : classement (exact > début > milieu > faute)", SmartSearch.score("Plex", "plex") > SmartSearch.score("Plexamp", "plex")
                && SmartSearch.score("Prime Video", "prime") > SmartSearch.score("Amazon Prime Video", "prime")
                && SmartSearch.score("Netflix", "net") > SmartSearch.score("Netflix", "netflx"));
        check("recherche : + = plus", SmartSearch.score("CANAL+", "canal plus") >= 900 && SmartSearch.score("Disney+", "disney") > 0);
        java.util.List<String> labels = java.util.Arrays.asList("Netflix", "Plex", "CANAL+", "Prime Video");
        Object[] at = SmartSearch.appAndTitle("plex Dune", labels);
        check("recherche : « plex Dune » → Dune dans Plex", at != null && (Integer) at[0] == 1 && "Dune".equals(at[1]));
        Object[] at2 = SmartSearch.appAndTitle("canal+ Ligue 1", labels);
        Object[] at3 = SmartSearch.appAndTitle("prime video The Boys", labels);
        check("recherche : nom d'appli composé", at2 != null && "Ligue 1".equals(at2[1]) && at3 != null && "The Boys".equals(at3[1]));
        check("recherche : pas d'appli devant le titre", SmartSearch.appAndTitle("dune", labels) == null && SmartSearch.appAndTitle("netflix", labels) == null);
        check("recherche : réglages trouvés sans accents", !SettingsIndex.search("veille", 6).isEmpty()
                && SettingsIndex.search("stockage", 6).get(0).page == SettingsIndex.STORAGE
                && !SettingsIndex.search("ecran de nuit", 6).isEmpty() && SettingsIndex.search("x", 6).isEmpty()
                && SettingsIndex.search("zzzz", 6).isEmpty());
        check("recherche : au plus N réglages", SettingsIndex.search("ve", 3).size() <= 3);

        // ---- touches de la télécommande à relier
        check("télécommande : Guide, Info, couleurs, chiffres et boutons d'appli se relient",
                "k172".equals(RemoteKeys.id(172, 0)) && RemoteKeys.id(165, 0) != null && RemoteKeys.id(183, 0) != null
                && RemoteKeys.id(7, 0) != null && RemoteKeys.id(176, 0) != null && RemoteKeys.id(289, 0) != null);
        boolean kept = true;
        // flèches, OK, Retour, Accueil, volume, alimentation, Menu, recherche, lecture, micro : jamais proposées
        for (int c : new int[]{3, 4, 19, 20, 21, 22, 23, 24, 25, 26, 66, 82, 84, 85, 86, 87, 88, 89, 90, 126, 127, 164, 219, 231, 29, 54, 62, 67, 111})
            kept &= RemoteKeys.id(c, 0) == null;
        check("télécommande : navigation, volume, lecture et clavier gardent leur rôle", kept);
        check("télécommande : touche inconnue d'Android repérée par son code matériel", "s412".equals(RemoteKeys.id(0, 412)) && RemoteKeys.id(0, 0) == null);
        String rk = RemoteKeys.with("", "k172", RemoteKeys.SETTINGS);
        rk = RemoteKeys.with(rk, "s412", RemoteKeys.APP_PREFIX + "com.exemple.tv/.Main");
        rk = RemoteKeys.with(rk, "k183", RemoteKeys.NONE);
        java.util.Map<String, String> rm = RemoteKeys.parse(rk);
        check("télécommande : liaisons relues telles qu'enregistrées", rm.size() == 3 && RemoteKeys.SETTINGS.equals(rm.get("k172"))
                && "com.exemple.tv/.Main".equals(RemoteKeys.appKey(rm.get("s412"))) && RemoteKeys.NONE.equals(rm.get("k183")));
        check("télécommande : oublier une touche la retire", !RemoteKeys.parse(RemoteKeys.with(rk, "k172", null)).containsKey("k172"));
        check("télécommande : changer une liaison remplace l'ancienne", RemoteKeys.BACK.equals(RemoteKeys.parse(RemoteKeys.with(rk, "k172", RemoteKeys.BACK)).get("k172")));
        check("télécommande : fonction inconnue ou touche de navigation refusée",
                RemoteKeys.with("", "k172", "formater").isEmpty() && RemoteKeys.with("", "k4", RemoteKeys.SETTINGS).isEmpty()
                && RemoteKeys.with("", "k172", "app:").isEmpty() && RemoteKeys.with("", "k172", "app:a;k4=back").isEmpty());
        check("télécommande : réglage abîmé ignoré sans erreur", RemoteKeys.parse("k172;=x;k4=back;kx=apps;k99999999999=apps;k172=reco;;").size() == 1
                && RemoteKeys.parse(null).isEmpty());
        StringBuilder manyKeys = new StringBuilder();
        for (int i = 1; i <= 200; i++) manyKeys.append("s").append(i).append("=apps;");
        check("télécommande : nombre de liaisons borné", RemoteKeys.parse(manyKeys.toString()).size() == RemoteKeys.MAX_KEYS);
        boolean named = true;
        for (String act : RemoteKeys.ACTIONS) named &= RemoteKeys.validAction(act) && !RemoteKeys.actionName(act).isEmpty();
        check("télécommande : chaque fonction a un nom", named && !RemoteKeys.actionName(RemoteKeys.NONE).isEmpty());
        check("télécommande : nom des touches", RemoteKeys.name("k172").equals("Touche Guide") && RemoteKeys.name("k12").equals("Touche 5")
                && RemoteKeys.name("k185").equals("Bouton jaune") && RemoteKeys.name("s412").equals("Touche spéciale 412") && RemoteKeys.name("k240").equals("Touche n° 240"));
        check("télécommande : liaisons reprises dans la sauvegarde", Backup.typeOf("remote_keys") == Backup.STRING && Backup.typeOf("remote_ask") == Backup.BOOL);
        check("recherche : « télécommande » mène à la page des touches", !SettingsIndex.search("telecommande", 6).isEmpty()
                && SettingsIndex.search("touches", 6).get(0).page == SettingsIndex.TOUCHES);
        // ---- sons de l'interface : jeux et volume
        boolean sndOk = true;
        for (int th = 0; th < SoundDesign.THEME_NAMES.length; th++)
            for (int sd = 0; sd < SoundDesign.COUNT; sd++) {
                short[] pcm = SoundDesign.synth(th, sd);
                int peak = 0;
                for (short x : pcm) peak = Math.max(peak, Math.abs(x));
                // un son audible, jamais saturé, qui finit en silence (pas de clic), en moins de 2 s
                sndOk &= pcm.length > 0 && pcm.length < 2 * SoundDesign.RATE && peak > 500 && peak < 16000
                        && Math.abs(pcm[pcm.length - 1]) < 100 && SoundDesign.fileName(th, sd).endsWith(".wav");
            }
        check("sons : chaque jeu a ses cinq sons, doux et sans clic", sndOk);
        check("sons : le jeu d'origine est inchangé", java.util.Arrays.equals(SoundDesign.synth(SoundDesign.CRISTAL, SoundDesign.TICK),
                SoundDesign.tone(0.03f, 0.002f, 70f, 0.055f, 1760f)));
        check("sons : jeux distincts (fichiers et timbres)", !SoundDesign.fileName(0, 1).equals(SoundDesign.fileName(1, 1))
                && !java.util.Arrays.equals(SoundDesign.synth(0, 1), SoundDesign.synth(2, 1)));
        boolean rising = true;
        for (int v = 1; v < SoundDesign.VOLUME_NAMES.length; v++) rising &= SoundDesign.gain(SoundDesign.TOCK, v) > SoundDesign.gain(SoundDesign.TOCK, v - 1);
        check("sons : le volume monte d'un cran à l'autre, sans dépasser 1", rising && SoundDesign.gain(SoundDesign.TOCK, 4) <= 1f
                && SoundDesign.gain(SoundDesign.TOCK, SoundDesign.DEFAULT_VOLUME) == 0.6f && SoundDesign.gain(SoundDesign.TICK, 0) > 0f);
        check("sons : valeurs inconnues ramenées au défaut", SoundDesign.theme(99) == SoundDesign.CRISTAL && SoundDesign.theme(-1) == SoundDesign.CRISTAL
                && SoundDesign.volume(99) == SoundDesign.DEFAULT_VOLUME && SoundDesign.volume(3) == 3);
        check("sons : jeu et volume repris dans la sauvegarde, bornés", Backup.typeOf("sound_theme") == Backup.INT && Backup.intOk("sound_volume", 4)
                && !Backup.intOk("sound_volume", 5) && !Backup.intOk("sound_theme", -1));
        check("sons : « bip » trouve la page des sons", !SettingsIndex.search("bip", 6).isEmpty() && SettingsIndex.search("bip", 6).get(0).page == SettingsIndex.SONS);
        check("sons : une touche peut couper ou remettre les sons", RemoteKeys.validAction(RemoteKeys.SOUNDS) && !RemoteKeys.actionName(RemoteKeys.SOUNDS).isEmpty());
        check("recherche : « volume » et « muet » mènent à la page des sons", !SettingsIndex.search("volume", 6).isEmpty()
                && SettingsIndex.search("volume", 6).get(0).page == SettingsIndex.SONS && SettingsIndex.search("muet", 6).get(0).page == SettingsIndex.SONS);
        // arrondis imbriqués : rayon externe = rayon interne + marge, partout
        check("arrondis : rayon externe = interne + marge", Radius.outer(24, 24) == 48 && Radius.inner(48, 24) == 24 && Radius.pad(48, 24) == 24
                && Radius.inner(10, 30) == 0 && Radius.pad(10, 30) == 0);
        check("arrondis : cartes de menu = ligne + marge", Radius.CARD == Radius.ROW + Radius.PAD);
        check("arrondis : fenêtres de saisie = ligne + marge", Radius.FORM == Radius.ROW + Radius.FORM_PAD && Radius.FORM_PAD > Radius.KEY_GAP);
        check("arrondis : rail Tout · Films · Séries = pastille + marge", Radius.SEG == Radius.SEG_ITEM + Radius.SEG_PAD);
        check("arrondis : affiche = ligne − marge", Radius.POSTER + Radius.POSTER_PAD == Radius.ROW);
        check("arrondis : tuile Maison = pastille + marge", Radius.HA_TILE == Radius.HA_ICON / 2 + Radius.HA_INSET);
        check("arrondis : vignette à cocher = coche + marge", Radius.CHECK_CELL == Radius.CHECK / 2 + Radius.CHECK_INSET);
        check("arrondis : barre de progression = vignette − marge", Radius.SHELF_BAR / 2 + Radius.SHELF_BAR_INSET == Radius.SHELF);
        check("arrondis : contour tracé au bord = rayon − demi-épaisseur", Radius.stroke(18, 4) == 16 && Radius.stroke(1, 4) == 0);
        check("arrondis : pastille de rubrique = ligne − marge", Radius.CHIP + Radius.CHIP_PAD == Radius.ROW);
        // la même règle partout : aucun rayon écrit en dur dans l'interface (cartes, fonds, contours, dessins)
        boolean noLiteral = true;
        java.util.regex.Pattern literal = java.util.regex.Pattern.compile(
                "glassCard\\(\\s*\\d|Glass\\.Surface\\([^;]*\\bu\\(\\s*\\d|setCornerRadius\\(\\s*(a\\.)?u\\(\\s*\\d|(setRoundRect|drawRoundRect)\\([^;]*\\bu\\(\\s*\\d");
        try (java.util.stream.Stream<java.nio.file.Path> files = Files.list(new File("src/com/lumen/launcher").toPath())) {
            for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator) {
                java.util.regex.Matcher m = literal.matcher(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
                while (m.find()) { noLiteral = false; System.out.println("  rayon en dur : " + f.getFileName() + " : " + m.group()); }
            }
        } catch (java.io.IOException e) { noLiteral = false; }
        check("arrondis : aucun rayon en dur dans l'interface (passer par Radius)", noLiteral);
        String css;
        try { css = new String(Files.readAllBytes(new File("assets/phone.css").toPath()), StandardCharsets.UTF_8); }
        catch (java.io.IOException e) { css = ""; }
        check("arrondis (téléphone) : listes = icône + marge", cssRule(css, ":root").contains("--r:calc(var(--r-ic) + var(--pad-row))")
                && cssRule(css, ".row").contains("padding:var(--pad-row)") && cssRule(css, ".row img").contains("border-radius:var(--r-ic)")
                && cssRule(css, ".list").contains("border-radius:var(--r)"));
        boolean nested = true;
        for (String sel : new String[]{".seg", ".tabs", ".job"}) {
            String r = cssRule(css, sel);
            nested &= r.contains("padding:var(--pad)") && r.contains("border-radius:calc(var(--in) + var(--pad))");
        }
        nested &= cssRule(css, ".seg button").contains("border-radius:var(--in)") && cssRule(css, ".tabs button").contains("border-radius:var(--in)")
                && cssRule(css, ".tg").contains("border-radius:calc(var(--in) + var(--pad))") && cssRule(css, ".tg::after").contains("left:var(--pad)");
        check("arrondis (téléphone) : rails, onglets, interrupteurs, envois = intérieur + marge", nested);
        boolean cssNoLiteral = true;
        java.util.regex.Matcher rad = java.util.regex.Pattern.compile("border-radius:([^;}]*)").matcher(css);
        while (rad.find()) if (java.util.regex.Pattern.compile("(?<![\\w.-])(?!0px)\\d+(\\.\\d+)?px").matcher(rad.group(1)).find()) {
            cssNoLiteral = false; System.out.println("  rayon en dur (phone.css) : " + rad.group());
        }
        check("arrondis (téléphone) : aucun rayon en dur, tout passe par les jetons de :root", cssNoLiteral && cssRule(css, ":root").contains("--pill:"));
        check("arrondis (téléphone) : éléments au bord d'une ligne de liste = liste − marge",
                cssRule(css, ".row.ha .ck").contains("border-radius:var(--r-ic)") && cssRule(css, ".row.ha .hm").contains("border-radius:var(--r-ic)")
                && cssRule(css, ".set select").contains("border-radius:var(--r-ic)") && cssRule(css, ".tg").contains("--in:calc(var(--r-ic) - var(--pad))")
                && cssRule(css, ".set .row").contains("padding:var(--pad-row)"));
        check("arrondis (téléphone) : vignettes = rond ou pastille du coin + marge",
                cssRule(css, ".thumb").contains("border-radius:calc(var(--ring) + var(--gap))") && cssRule(css, ".thumb .ring").contains("top:var(--gap)")
                && cssRule(css, ".tile").contains("border-radius:calc(var(--x) + var(--gap))") && cssRule(css, ".tile .x").contains("top:var(--gap)")
                && cssRule(css, ".wall").contains("border-radius:calc(var(--pill) + var(--gap))") && cssRule(css, ".wall .eye").contains("right:var(--gap)"));
        check("bandeau : salutation selon l'heure", Greeting.hello(8).equals("Bonjour") && Greeting.hello(14).equals("Bon après-midi")
                && Greeting.hello(20).equals("Bonsoir") && Greeting.hello(23).equals("Bonne nuit") && Greeting.hello(3).equals("Bonne nuit"));
        check("bandeau : ligne complète", Greeting.line(21, "", "12° · Pluie · Paris", "The Bear")
                .equals("Bonsoir   ·   Pluie 12°   ·   Reprendre The Bear"));
        check("bandeau : rien d'inventé sans météo ni épisode", Greeting.line(9, "", "", null).equals("Bonjour"));
        // ---- fondu de sélection : mélange de couleurs
        check("fondu : extrémités exactes", Palette.mix(0x00F5F5F7, 0xFFF5F5F7, 0f) == 0x00F5F5F7 && Palette.mix(0x00F5F5F7, 0xFFF5F5F7, 1f) == 0xFFF5F5F7
                && Palette.mix(0x12345678, 0xFFFFFFFF, -1f) == 0x12345678 && Palette.mix(0x12345678, 0xFFFFFFFF, 2f) == 0xFFFFFFFF);
        // ---- couleurs accordées au fond
        int[] grey = new int[200], orange = new int[200], blue = new int[200], red = new int[200], mixed = new int[200];
        for (int i = 0; i < 200; i++) {
            grey[i] = 0xFF303030; orange[i] = 0xFF5A3A14; blue[i] = 0xFF101E4A; red[i] = 0xFF5A1010;
            mixed[i] = i % 2 == 0 ? 0xFF5A1010 : 0xFF105A5A; // deux teintes opposées : pas de dominante
        }
        check("teinte : image grise ou sans dominante → couleurs neutres", Palette.tintFor(grey) == null && Palette.tintFor(mixed) == null
                && Palette.tintFor(new int[]{0xFF000000}) == null);
        int[] to = Palette.tintFor(orange), tb = Palette.tintFor(blue), tr = Palette.tintFor(red);
        check("teinte : fond chaud → blanc chaud, fond bleu → blanc bleuté, toujours presque blanc", to != null && tb != null
                && ((to[0] >> 16) & 255) > (to[0] & 255) && (tb[0] & 255) > ((tb[0] >> 16) & 255)
                && (to[0] >>> 24) == 255 && (to[0] & 255) >= 220 && ((tb[0] >> 16) & 255) >= 220);
        check("teinte : l'accent n'est jamais rouge (réservé aux actions qui retirent)", tr != null
                && ((tr[1] >> 8) & 255) - (tr[1] & 255) >= 30);
        Palette.tint(orange);
        boolean tinted = Palette.SELECTED == to[0] && Palette.ACCENT == to[1];
        Palette.tint(null);
        check("teinte : appliquée puis remise au neutre", tinted && Palette.SELECTED == Palette.SELECTED_NEUTRAL && Palette.ACCENT == Palette.ACCENT_NEUTRAL);
        check("teinte : hsv", Palette.hsv(0, 1, 1) == 0xFFFF0000 && Palette.hsv(120, 1, 1) == 0xFF00FF00 && Palette.hsv(240, 1, 1) == 0xFF0000FF
                && Palette.hsv(200, 0, 1) == 0xFFFFFFFF);
        check("fondu : milieu, transparence comprise", Palette.mix(0x00000000, 0xFF204060, 0.5f) == 0x80102030
                && Palette.mix(0xFFFFFFFF, 0xFF111216, 0.5f) == 0xFF88898B);
        check("bandeau : titre long raccourci", Greeting.resume("Un titre vraiment très très long pour une seule ligne").endsWith("…"));
        check("écran de nuit : 23 h à 7 h", Greeting.night(23, 23) && Greeting.night(3, 23) && !Greeting.night(7, 23) && !Greeting.night(22, 23));
        check("écran de nuit : minuit et désactivé", Greeting.night(0, 0) && !Greeting.night(23, 0) && !Greeting.night(2, -1));
        check("messages : jamais technique", !Messages.download("Hôte non autorisé : thumb.wikimedia.org").contains("Hôte")
                && Messages.download("java.net.UnknownHostException").startsWith("Pas d'accès"));
        check("messages : pause demandée (429)", Messages.download("HTTP 429").contains("pause"));
        check("nom du téléphone : iPhone", DeviceName.from("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)").equals("iPhone"));
        check("nom du téléphone : modèle Android", DeviceName.from("Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP1A) Chrome").equals("Pixel 8"));
        check("nom du téléphone : modèle masqué par Chrome", DeviceName.from("Mozilla/5.0 (Linux; Android 10; K) Chrome").equals("un téléphone Android"));
        check("nom du téléphone : nom choisi prioritaire et propre", DeviceName.pick("  iPhone   de Jem ", "x").equals("iPhone de Jem")
                && DeviceName.pick("a\u0001b", null).equals("un téléphone") && DeviceName.clean("x".repeat(40)) == null);
        check("aperçus Wikimedia (thumb.wikimedia.org) autorisés", NetPolicy.allowed("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a9/Example.jpg/500px-Example.jpg"));
        check("http refusé", !NetPolicy.allowed("http://images-api.nasa.gov/search"));
        check("hôte inconnu refusé", !NetPolicy.allowed("https://evil.example.com/x"));
        check("sous-domaine piégé refusé", !NetPolicy.allowed("https://images-api.nasa.gov.evil.com/x"));
        check("identifiants dans l'URL refusés", !NetPolicy.allowed("https://user@upload.wikimedia.org/x"));
        check("port exotique refusé", !NetPolicy.allowed("https://upload.wikimedia.org:8443/x"));
        check("file:// refusé", !NetPolicy.allowed("file:///etc/passwd"));
        check("normalisation //", NetPolicy.https("//upload.wikimedia.org/a").equals("https://upload.wikimedia.org/a"));
        check("licence CC BY-SA 4.0", NetPolicy.freeLicense("CC BY-SA 4.0"));
        check("licence CC0", NetPolicy.freeLicense("CC0"));
        check("domaine public", NetPolicy.freeLicense("Public domain"));
        check("licence NC refusée", !NetPolicy.freeLicense("CC BY-NC 2.0"));
        check("licence ND refusée", !NetPolicy.freeLicense("CC BY-ND 4.0"));
        check("licence inconnue refusée", !NetPolicy.freeLicense("All rights reserved"));
        check("licence vide refusée", !NetPolicy.freeLicense(""));

        // ---- signatures de fichiers
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        byte[] mp4 = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        byte[] webm = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] apk = {'P', 'K', 3, 4, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] elf = {0x7F, 'E', 'L', 'F', 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] html = "<html><script>".getBytes(StandardCharsets.US_ASCII);
        byte[] heic = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};
        check("JPEG reconnu", MediaSniff.sniff(jpg, jpg.length) == MediaSniff.Type.JPEG);
        check("PNG reconnu", MediaSniff.sniff(png, png.length) == MediaSniff.Type.PNG);
        check("MP4 reconnu", MediaSniff.sniff(mp4, mp4.length) == MediaSniff.Type.MP4);
        check("WebM reconnu", MediaSniff.sniff(webm, webm.length) == MediaSniff.Type.WEBM);
        check("APK/ZIP refusé", MediaSniff.sniff(apk, apk.length) == MediaSniff.Type.UNKNOWN);
        check("exécutable ELF refusé", MediaSniff.sniff(elf, elf.length) == MediaSniff.Type.UNKNOWN);
        check("HTML/script refusé", MediaSniff.sniff(html, html.length) == MediaSniff.Type.UNKNOWN);
        check("HEIC refusé (non décodable partout)", MediaSniff.sniff(heic, heic.length) == MediaSniff.Type.UNKNOWN);
        check("fichier tronqué refusé", MediaSniff.sniff(jpg, 2) == MediaSniff.Type.UNKNOWN);
        File f = File.createTempFile("lumen", ".jpg");
        Files.write(f.toPath(), elf);
        check("extension .jpg mensongère refusée", MediaSniff.sniff(f) == MediaSniff.Type.UNKNOWN);
        f.delete();

        // ---- parseur HTTP
        HttpRequest r = HttpRequest.read(new ByteArrayInputStream(
                "GET /api/icon?key=a%2Fb HTTP/1.1\r\nHost: 192.168.1.2:8765\r\nCookie: x=1; lumen_s=abc\r\n\r\n".getBytes(StandardCharsets.UTF_8)));
        check("méthode", r.method.equals("GET"));
        check("chemin", r.path.equals("/api/icon"));
        check("paramètre décodé", "a/b".equals(r.query.get("key")));
        check("en-tête insensible à la casse", "192.168.1.2:8765".equals(r.header("HOST")));
        check("cookie", "abc".equals(r.cookie("lumen_s")));
        check("méthode PUT refusée", throwsOn("PUT / HTTP/1.1\r\n\r\n"));
        check("remontée de dossier refusée", throwsOn("GET /../../data HTTP/1.1\r\n\r\n"));
        check("chemin relatif refusé", throwsOn("GET api HTTP/1.1\r\n\r\n"));
        check("caractère de contrôle refusé", throwsOn("GET /a\u0001b HTTP/1.1\r\n\r\n"));
        check("chunked refusé", throwsOn("POST /api/upload HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"));
        check("longueur négative refusée", throwsOn("POST /x HTTP/1.1\r\nContent-Length: -5\r\n\r\n"));
        check("longueur invalide refusée", throwsOn("POST /x HTTP/1.1\r\nContent-Length: abc\r\n\r\n"));
        StringBuilder big = new StringBuilder("GET /");
        for (int i = 0; i < 5000; i++) big.append('a');
        check("ligne trop longue refusée", throwsOn(big + " HTTP/1.1\r\n\r\n"));
        StringBuilder many = new StringBuilder("GET / HTTP/1.1\r\n");
        for (int i = 0; i < 60; i++) many.append("X-H").append(i).append(": v\r\n");
        check("trop d'en-têtes refusés", throwsOn(many + "\r\n"));
        check("requête incomplète refusée", throwsOn("GET / HTTP/1.1\r\nHost: a"));
        boolean bodyLimit;
        try { HttpRequest.readBody(new ByteArrayInputStream(new byte[10]), 100_000, 64 * 1024); bodyLimit = false; }
        catch (Exception e) { bodyLimit = true; }
        check("corps trop gros refusé", bodyLimit);

        // ---- authentification
        final long[] now = {1_000_000L};
        PhoneAuth a = new PhoneAuth(new PhoneAuth.Clock() { public long now() { return now[0]; } });
        String tok = a.newToken();
        check("jeton long (≥ 22 caractères)", tok.length() >= 22);
        check("mauvais jeton refusé", a.openSession("faux") == null);
        String sid = a.openSession(tok);
        check("bon jeton accepté", sid != null);
        check("session valide", a.check(sid));
        check("jeton à usage unique", a.openSession(tok) == null);
        check("session inconnue refusée", !a.check("xyz"));
        now[0] += PhoneAuth.SESSION_IDLE_MS + 1;
        check("session expirée", !a.check(sid));
        PhoneAuth b = new PhoneAuth(new PhoneAuth.Clock() { public long now() { return now[0]; } });
        String t2 = b.newToken();
        now[0] += PhoneAuth.TOKEN_TTL_MS + 1;
        check("jeton expiré refusé", b.openSession(t2) == null);
        PhoneAuth c = new PhoneAuth(new PhoneAuth.Clock() { public long now() { return now[0]; } });
        String t3 = c.newToken();
        for (int i = 0; i < PhoneAuth.MAX_FAILURES; i++) c.openSession("x" + i);
        check("verrouillage après échecs", c.locked());
        check("même le bon jeton est refusé une fois verrouillé", c.openSession(t3) == null);
        check("comparaison constante", PhoneAuth.equal("abc", "abc") && !PhoneAuth.equal("abc", "abd"));
        PhoneAuth e = new PhoneAuth(new PhoneAuth.Clock() { public long now() { return now[0]; } });
        String s1 = e.openSession(e.newToken());
        check("téléphone : une seule session à la fois", PhoneAuth.MAX_SESSIONS == 1 && s1 != null);
        check("code PIN : pas d'élévation par défaut", !e.isElevated(s1));
        e.elevate(s1);
        check("code PIN : élévation après saisie", e.isElevated(s1));
        check("code PIN : élévation liée à la session", !e.isElevated("autre"));
        now[0] += PhoneAuth.ELEVATION_MS + 1;
        check("code PIN : élévation expire après 5 min", !e.isElevated(s1) && e.check(s1));
        e.elevate(s1);
        e.revokeAll();
        check("déconnexion : session et élévation supprimées", !e.check(s1) && !e.isElevated(s1));
        check("déconnexion : l'ancien QR code ne sert plus", !e.hasSessions());

        // ---- limite de débit
        RateLimit rl = new RateLimit(4, 20, 0);
        int passed = 0;
        for (int i = 0; i < 100; i++) if (rl.allow(0)) passed++;
        check("débit : rafale limitée à 20", passed == 20);
        check("débit : bloqué juste après", !rl.allow(100));
        check("débit : 4 actions par seconde ensuite", rl.allow(1000) && rl.allow(1000) && rl.allow(1000) && rl.allow(1000) && !rl.allow(1000));
        check("débit : jamais plus que la rafale après une pause", countAllowed(new RateLimit(4, 20, 0), 3_600_000L) == 20);

        // ---- QR code (rendu vérifié par zbarimg si disponible)
        String url = "http://192.168.1.23:8765/#AbCdEfGhIjKlMnOpQrStUv";
        QrCode q = QrCode.encode(url);
        check("QR généré", q.size >= 21);
        File png2 = new File(System.getProperty("java.io.tmpdir"), "lumen_qr_test.pgm");
        int s = 6, quiet = 4, n = (q.size + quiet * 2) * s;
        StringBuilder pgm = new StringBuilder("P2\n" + n + " " + n + "\n1\n");
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int mx = x / s - quiet, my = y / s - quiet;
                boolean dark = mx >= 0 && my >= 0 && mx < q.size && my < q.size && q.get(mx, my);
                pgm.append(dark ? "0 " : "1 ");
            }
            pgm.append('\n');
        }
        Files.write(png2.toPath(), pgm.toString().getBytes(StandardCharsets.US_ASCII));
        System.out.println("QR_FILE=" + png2.getAbsolutePath() + " QR_EXPECT=" + url);

        try {
            File sdir = Files.createTempDirectory("lumen-safe").toFile();
            File idx = new File(sdir, "index.json");
            check("écriture sûre : fichier écrit", SafeFile.write(idx, "[1]".getBytes("UTF-8")) && "[1]".equals(new String(Files.readAllBytes(idx.toPath()), "UTF-8")));
            check("écriture sûre : remplacé d'un coup", SafeFile.write(idx, "[2]".getBytes("UTF-8")) && "[2]".equals(new String(Files.readAllBytes(idx.toPath()), "UTF-8")));
            check("écriture sûre : aucun fichier temporaire laissé", sdir.list().length == 1);
            File gone = new File(new File(sdir, "absent"), "x.json");
            check("écriture sûre : échec signalé, rien d'écrit", !SafeFile.write(gone, new byte[1]) && !gone.exists());
            idx.delete(); sdir.delete();
        } catch (Exception safeErr) { check("écriture sûre : " + safeErr, false); }
        try {
            byte[] sk = Seal.key("cle-du-qr"), nonce = new byte[16];
            for (int i = 0; i < 16; i++) nonce[i] = (byte) i;
            // mêmes valeurs que tests/phone_crypto.js (vrai code de phone.js) : la page et la TV s'accordent
            check("chiffrement : identique à la page du téléphone",
                    "E1.000102030405060708090a0b0c0d0e0f.81e378f003adf5a9b1dfe53f7b65bb".equals(Seal.seal(sk, "jeton secret é".getBytes("UTF-8"), nonce)));
            String longText = "Jeton Home Assistant très long : eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.abc";
            check("chiffrement : identique sur plusieurs blocs",
                    "E1.000102030405060708090a0b0c0d0e0f.a1e378f003adcea3bfc8a00a28d57ba7f7829ef224653e0e66809b149f9b6f53b25036bd009c1b24ac8fe96b882099f04a6d1e625db33eb9ea691ff1964b919b92e4b7c7c702ad3363f8"
                            .equals(Seal.seal(sk, longText.getBytes("UTF-8"), nonce)));
            String sealed = Seal.seal(sk, longText.getBytes("UTF-8"));
            check("chiffrement : aller-retour", longText.equals(new String(Seal.open(sk, sealed), "UTF-8")));
            check("chiffrement : le secret n'apparaît pas", !sealed.contains("eyJ") && !sealed.contains("Jeton"));
            check("chiffrement : deux envois différents (nonce aléatoire)", !sealed.equals(Seal.seal(sk, longText.getBytes("UTF-8"))));
            check("chiffrement : texte vide", Seal.open(sk, Seal.seal(sk, new byte[0])).length == 0);
            check("chiffrement : autre clé, pas le bon texte", !longText.equals(new String(Seal.open(Seal.key("autre"), sealed), "UTF-8")));
            check("chiffrement : texte en clair refusé", Seal.open(sk, "abc") == null && Seal.open(sk, "") == null && Seal.open(sk, null) == null);
            check("chiffrement : format abîmé refusé", Seal.open(sk, "E1.00.zz") == null && Seal.open(sk, "E1.000102030405060708090a0b0c0d0e0f.abc") == null
                    && Seal.open(sk, "E1.000102030405060708090a0b0c0d0e0f.ab.cd") == null);
            StringBuilder huge = new StringBuilder("E1.000102030405060708090a0b0c0d0e0f.");
            for (int i = 0; i <= Seal.MAX; i++) huge.append("00");
            check("chiffrement : trop long refusé", Seal.open(sk, huge.toString()) == null);
            // la page du téléphone : secrets refusés en clair, acceptés chiffrés
            check("envoi secret en clair refusé", PhoneGate.unseal("abcd".getBytes("UTF-8"), sk) == null);
            check("envoi secret chiffré accepté", "1234".equals(new String(PhoneGate.unseal(Seal.seal(sk, "1234".getBytes("UTF-8")).getBytes("UTF-8"), sk), "UTF-8")));
            for (String path : new String[]{"/api/ha", "/api/tmdb", "/api/pexels", "/api/pin", "/api/text"})
                check("secret chiffré exigé : " + path, PhoneGate.isSealed(req("POST", path)));
            check("pas de chiffrement pour une touche", !PhoneGate.isSealed(req("POST", "/api/key?k=up")));
            // connexion permanente : texte chiffré
            Signer ws = new Signer("cle-du-qr");
            String payload = "t1 " + Seal.seal(sk, "mot de passe".getBytes("UTF-8"));
            String sig = Signer.hmacHex("cle-du-qr".getBytes("UTF-8"), Signer.wsMessage(9, payload));
            check("WebSocket : texte chiffré déchiffré", "t1 mot de passe".equals(PhoneGate.wsCommand("S 9 " + sig + " " + payload, ws, sk)));
            String clear = "t1 mot de passe";
            String sig2 = Signer.hmacHex("cle-du-qr".getBytes("UTF-8"), Signer.wsMessage(10, clear));
            check("WebSocket : texte en clair refusé", PhoneGate.wsCommand("S 10 " + sig2 + " " + clear, ws, sk) == null);
        } catch (Exception sealErr) { check("chiffrement : " + sealErr, false); }
        try { phoneGate(); } catch (Exception gateErr) { check("contrôles de la page du téléphone : " + gateErr, false); }

        profilesTests();

        // ---- ciel (références : éphémérides publiées, tolérance 4 min)
        java.util.TimeZone paris = java.util.TimeZone.getTimeZone("Europe/Paris");
        java.util.Calendar d = java.util.Calendar.getInstance(paris);
        // IMCCE, Paris 2026 (heures TU) : 9 janvier lever 7:43,8 / coucher 16:12,0 ; 7 juin lever 3:50,2 / coucher 19:49,2
        d.clear(); d.set(2026, java.util.Calendar.JANUARY, 9, 12, 0);
        int rise = Sky.sunMinutes(d, true, 48.8566, 2.3522), set = Sky.sunMinutes(d, false, 48.8566, 2.3522);
        check("lever Paris 9 janv ≈ 8:44 (IMCCE)", Math.abs(rise - (8 * 60 + 44)) <= 3);
        check("coucher Paris 9 janv ≈ 17:12 (IMCCE)", Math.abs(set - (17 * 60 + 12)) <= 3);
        d.clear(); d.set(2026, java.util.Calendar.JUNE, 7, 12, 0);
        rise = Sky.sunMinutes(d, true, 48.8566, 2.3522); set = Sky.sunMinutes(d, false, 48.8566, 2.3522);
        check("lever Paris 7 juin ≈ 5:50 (IMCCE)", Math.abs(rise - (5 * 60 + 50)) <= 3);
        check("coucher Paris 7 juin ≈ 21:49 (IMCCE)", Math.abs(set - (21 * 60 + 49)) <= 3);
        java.util.Calendar ny = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/New_York"));
        ny.clear(); ny.set(2026, java.util.Calendar.NOVEMBER, 9, 12, 0);
        rise = Sky.sunMinutes(ny, true, 40.7128, -74.0060); set = Sky.sunMinutes(ny, false, 40.7128, -74.0060);
        check("lever New York 9 nov ≈ 6:36", Math.abs(rise - (6 * 60 + 36)) <= 3);
        check("coucher New York 9 nov ≈ 16:43", Math.abs(set - (16 * 60 + 43)) <= 3);
        java.util.Calendar polar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Oslo"));
        polar.clear(); polar.set(2026, java.util.Calendar.DECEMBER, 21, 12, 0);
        check("nuit polaire à Tromsø", Sky.sunMinutes(polar, true, 69.65, 18.96) == -1);
        // pleine lune connue : 24 novembre 2026 ? on vérifie plutôt des dates sûres
        check("pleine lune 25 janvier 2024", Sky.moonPhase(1706183100000L).equals("Pleine lune"));
        check("nouvelle lune 11 janvier 2024", Sky.moonPhase(1704986400000L).equals("Nouvelle lune"));
        check("décembre = fêtes", Sky.festive(new java.util.GregorianCalendar(2026, java.util.Calendar.DECEMBER, 15)));
        check("juillet ≠ fêtes", !Sky.festive(new java.util.GregorianCalendar(2026, java.util.Calendar.JULY, 15)));

        System.out.println(ok + " tests réussis, " + ko + " échec(s)");
        if (ko > 0) System.exit(1);
    }
}

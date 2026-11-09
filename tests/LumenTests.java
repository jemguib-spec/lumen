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
        check("fichier : signature ZIP reconnue", UpdatePolicy.looksLikeApk(new byte[]{'P', 'K', 3, 4}));
        check("fichier : autre format refusé", !UpdatePolicy.looksLikeApk(new byte[]{'<', 'h', 't', 'm'}));
        check("GitHub (fichiers du dépôt) autorisé pour la mise à jour", NetPolicy.allowed(UpdatePolicy.LATEST));
        check("API GitHub non nécessaire, refusée", !NetPolicy.allowed("https://api.github.com/repos/x/y"));

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

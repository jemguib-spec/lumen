// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.util.Locale;

/**
 * Messages affichés à l'écran : jamais de texte technique. Le détail (« Hôte non autorisé : … »,
 * « HTTP 503 »…) va dans le journal d'erreurs ; l'écran dit simplement ce qui se passe. Java pur, testé.
 */
final class Messages {
    private Messages() { }

    /** Erreur technique de téléchargement → phrase calme pour l'écran. */
    static String download(String technical) {
        String t = technical == null ? "" : technical.toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return L.t("Rien de nouveau pour l'instant");
        if (t.contains("429") || t.contains("too many") || t.contains("patiente")) return "Le serveur demande une pause · reprise un peu plus tard";
        if (t.contains("unknownhost") || t.contains("unable to resolve") || t.contains("network is unreachable")
                || t.contains("failed to connect") || t.contains("connectexception") || t.contains("timeout") || t.contains("timed out"))
            return L.t("Pas d'accès à Internet pour l'instant · nouvel essai plus tard");
        if (t.contains("espace") || t.contains("no space")) return "Plus assez de place sur la TV";
        if (t.contains("refusé") || t.contains("non conforme")) return "Un fichier a été écarté · les autres continuent";
        return L.t("Les photos arrivent un peu plus tard · nouvel essai bientôt");
    }
}

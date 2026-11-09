package com.lumen.launcher;

/**
 * Règles pures (testées) de la mise à jour automatique : comparaison des versions et adresses
 * acceptées. La garantie principale reste la signature : un APK n'est proposé que s'il est signé
 * avec la même clé que Lumen installé (vérifié par Lumen, puis par Android).
 */
final class UpdatePolicy {
    private UpdatePolicy() { }

    static final String REPO = "jemguib-spec/lumen";
    static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    static final String ASSET_PREFIX = "https://github.com/" + REPO + "/releases/download/";
    static final String ASSET_NAME = "Lumen.apk";
    static final long MAX_BYTES = 40L << 20;

    /** « v4.1.0 » → « 4.1.0 » (null si ce n'est pas un numéro de version). */
    static String version(String tag) {
        if (tag == null) return null;
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        return t.matches("\\d{1,4}(\\.\\d{1,4}){0,3}") ? t : null;
    }

    /** > 0 si a est plus récente que b (« 4.10 » > « 4.9 », « 4.0.1 » > « 4.0 »). */
    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0, q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    /** Seul le fichier Lumen.apk d'une release de ce dépôt peut être téléchargé. */
    static boolean assetUrlOk(String url) {
        if (url == null || !url.startsWith(ASSET_PREFIX) || !url.endsWith("/" + ASSET_NAME)) return false;
        String tag = url.substring(ASSET_PREFIX.length(), url.length() - ASSET_NAME.length() - 1);
        return !tag.isEmpty() && !tag.contains("/") && !tag.contains("..") && !tag.contains("%") && !tag.contains("?");
    }

    /** Signature de fichier ZIP/APK (« PK\3\4 »). */
    static boolean looksLikeApk(byte[] head) {
        return head != null && head.length >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4;
    }
}

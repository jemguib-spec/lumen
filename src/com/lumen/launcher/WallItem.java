package com.lumen.launcher;

/** Un fond d'écran : image (embarquée ou téléchargée), scène animée ou vidéo. */
final class WallItem {
    static final int IMAGE_ASSET = 0, IMAGE_FILE = 1, ANIM = 2, VIDEO_FILE = 3;

    final int kind;
    final String id;      // identifiant stable (pour le masquage)
    final String path;    // chemin d'asset, de fichier ou dossier de scène
    final String title;
    final String credit;  // auteur / licence, peut être vide

    WallItem(int kind, String id, String path, String title, String credit) {
        this.kind = kind;
        this.id = id;
        this.path = path;
        this.title = title == null ? "" : title;
        this.credit = credit == null ? "" : credit;
    }
}

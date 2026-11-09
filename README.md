# Lumen

Écran d'accueil (launcher) pour Android TV / Google TV : minimaliste, en verre dépoli, sans publicité ni télémétrie.

- Favoris sur l'accueil, page Applications rangée par catégorie avec recherche
- « Reprendre » : épisodes en cours au-dessus de l'appli sélectionnée
- Fonds : paysages générés selon l'heure et la météo, photos et vidéos libres de droits (liste fermée, à choisir sur aperçus)
- Mode veille sur une ligne, ambiances vidéo avec minuterie de sommeil
- Maison : lumières, prises, scènes et volets Home Assistant depuis l'accueil
- Sources HDMI sur l'accueil, économiseur d'écran officiel de la TV
- Code PIN facultatif (réglages, désinstallation, applis verrouillées)
- Applis habituelles selon l'heure, calculées sur la TV uniquement
- Taille du texte et contraste réglables
- Télécommande, choix des vidéos, envois et sauvegarde depuis le téléphone par QR code (réseau local, un seul téléphone, actions en liste fermée)

Installation et utilisation : [INSTALLATION.md](INSTALLATION.md) · Sécurité et organisation du code : [SECURITE.md](SECURITE.md)

## Compiler

Sous Ubuntu/Debian :

    sudo apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-17-jdk
    ./build.sh        # produit build/Lumen.apk
    tests/run.sh      # tests

`build.sh` crée une clé de signature locale (`lumen.keystore`) si elle n'existe pas. Garde la même clé pour pouvoir installer les mises à jour par-dessus l'appli existante.

`tools/scenes.py` régénère les paysages de `assets/scenes` et `assets/anim` (Python, numpy, Pillow).

Police : Inter (licence OFL, `assets/fonts/LICENCE-Inter.txt`).

## Publier une mise à jour

Les TV équipées de Lumen lisent chaque jour `dist/latest.json` sur la branche `main`, puis téléchargent `dist/Lumen.apk` si la version est plus récente.

1. Augmenter `versionCode` et `versionName` dans `AndroidManifest.xml`.
2. `tools/publish.sh` (compile, lance les tests, remplit `dist/`).
3. Pousser sur `main`.

L'APK doit être signé avec la même clé (`lumen.keystore`, jamais publiée) que la version installée : sinon Lumen le refuse, et Android aussi.

## Licence
Copyright (C) 2026 jemguib-spec. Lumen est un logiciel libre : vous pouvez le redistribuer et le modifier selon les termes de la GNU General Public License, version 3 ou (à votre choix) toute version ultérieure. Il est distribué sans aucune garantie. Voir [LICENSE](LICENSE).

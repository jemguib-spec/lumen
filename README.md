# Lumen

Écran d'accueil (launcher) pour Android TV / Google TV : minimaliste, en verre dépoli, sans publicité ni télémétrie.

- Favoris sur l'accueil, page Applications rangée par catégorie avec recherche
- « Reprendre » : épisodes en cours au-dessus de l'appli sélectionnée
- Fonds : paysages générés selon l'heure et la météo, photos et vidéos libres de droits (liste fermée, à choisir sur aperçus)
- Mode veille sur une ligne, ambiances vidéo avec minuterie de sommeil
- Réglages et envois depuis le téléphone par QR code (réseau local uniquement)

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

Les TV équipées de Lumen vérifient chaque jour la dernière release de ce dépôt.

1. Augmenter `versionCode` et `versionName` dans `AndroidManifest.xml`, puis `./build.sh`.
2. Créer une release dont l'étiquette est la version (ex. `v4.2.0`) avec le fichier `build/Lumen.apk` nommé exactement `Lumen.apk`.

L'APK doit être signé avec la même clé (`lumen.keystore`) que la version installée : sinon Lumen le refuse.

# Lumen

Écran d'accueil (launcher) pour Android TV / Google TV : minimaliste, en verre dépoli, sans publicité ni télémétrie.

![Paysages Lumen : crêtes, horizon et lac, à l'aube, le jour, le soir et la nuit](docs/paysages.jpg)

*Les paysages Lumen, générés par `tools/scenes.py` et animés sur la TV. Des captures d'écran de l'interface prises sur un émulateur Android TV sont jointes à chaque exécution de la CI (artefact « captures », onglet Actions).*

- Favoris sur l'accueil, page Applications rangée par catégorie avec recherche
- « Reprendre » : épisodes en cours au-dessus de l'appli sélectionnée
- Fonds : paysages générés selon l'heure et la météo, photos et vidéos libres de droits (liste fermée, à choisir sur aperçus)
- Mode veille sur une ligne, ambiances vidéo avec minuterie de sommeil
- Maison : lumières, prises, scènes et volets Home Assistant depuis l'accueil
- Sources HDMI sur l'accueil, économiseur d'écran officiel de la TV
- Code PIN facultatif (réglages, désinstallation, applis verrouillées) ; profil enfant : seulement ses favoris, code PIN pour le reste
- État de la TV sous l'horloge : pas de réseau, pile de la télécommande, mise à jour prête
- Interface en français ou en anglais (réglage par profil)
- Applis habituelles selon l'heure, calculées sur la TV uniquement
- Taille du texte et contraste réglables, lecteur d'écran (TalkBack) pris en charge
- Recherche vocale (micro de la télécommande ou bouton micro de la recherche)
- Touches de la télécommande sans rôle (Guide, Info, couleurs, chiffres…) : Lumen propose de les relier à une fonction ou à une appli
- Assistant d'installation sur la TV : état de l'accueil, commandes à copier, QR code vers le guide
- Télécommande, choix des vidéos, envois et sauvegarde depuis le téléphone par QR code (réseau local, un seul téléphone, actions en liste fermée)

Installation et utilisation : [INSTALLATION.md](INSTALLATION.md) · Sécurité et organisation du code : [SECURITE.md](SECURITE.md)

## Compiler

Sous Ubuntu/Debian :

    sudo apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-17-jdk
    ./build.sh        # produit build/Lumen.apk
    tests/run.sh      # tests

`build.sh` crée une clé de signature locale (`lumen.keystore`) si elle n'existe pas. Garde la même clé pour pouvoir installer les mises à jour par-dessus l'appli existante.

`dist/services.json` : clé TMDB partagée du projet (facultative), proposée aux TV qui n'ont pas de clé personnelle pour « À l'affiche ». Pour la fournir, crée une clé gratuite sur themoviedb.org, mets-la dans le champ `tmdb` et pousse sur `main`.

`tools/i18n_check.py` vérifie que chaque texte de l'interface a sa traduction (`assets/lang/en.tsv`).

`tools/scenes.py` régénère les paysages de `assets/scenes` et `assets/anim` (Python, numpy, Pillow).

Police : Inter (licence OFL, `assets/fonts/LICENCE-Inter.txt`).

## Vérification continue

À chaque envoi, GitHub Actions compile Lumen, lance les tests, contrôle la cohérence de `dist/latest.json` et du `CHANGELOG.md`, puis installe l'APK sur un émulateur Android TV et parcourt l'accueil, la page Applications et les réglages (`tools/smoke.sh`) en prenant des captures d'écran. Le même parcours se lance à la main sur une TV branchée en adb : `tools/smoke.sh build/Lumen.apk captures`.

## Publier une mise à jour

Les TV équipées de Lumen lisent toutes les 6 heures (ou à l'ouverture de Réglages › Système) `dist/latest.json` sur la branche `main`, puis téléchargent l'APK de la [GitHub Release](https://github.com/jemguib-spec/lumen/releases) correspondante si la version est plus récente. Les nouveautés de la version s'affichent sur la TV (Réglages › Système › Nouveautés).

1. Augmenter `versionCode` et `versionName` dans `AndroidManifest.xml`.
2. Décrire la version dans `CHANGELOG.md` (section `## <version>`) : c'est le texte de la Release et des nouveautés sur la TV.
3. `tools/publish.sh` (compile, lance les tests, crée la Release `v<version>` avec l'APK, met à jour `dist/latest.json`). Nécessite `gh` connecté.
4. Pousser sur `main`.

L'APK doit être signé avec la même clé (`lumen.keystore`, jamais publiée) que la version installée : sinon Lumen le refuse, et Android aussi.

`dist/Lumen.apk` n'est plus qu'un pont pour les TV encore en 4.19 ou avant, qui ne connaissent pas les Releases ; `tools/publish.sh --sans-pont` cesse de le publier quand toutes les TV sont passées.

## Contribuer

Voir [CONTRIBUTING.md](CONTRIBUTING.md) : compiler, tester, proposer une idée ou signaler un problème.

## Licence
Copyright (C) 2026 jemguib-spec. Lumen est un logiciel libre : vous pouvez le redistribuer et le modifier selon les termes de la GNU General Public License, version 3 ou (à votre choix) toute version ultérieure. Il est distribué sans aucune garantie. Voir [LICENSE](LICENSE).

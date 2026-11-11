# Contribuer à Lumen

Merci de t'intéresser à Lumen. Le projet est petit et direct : pas de framework, du Java Android pur, des réglages en code, une politique de sécurité écrite ([SECURITE.md](SECURITE.md)).

## Compiler et tester

Sous Ubuntu/Debian :

    sudo apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-17-jdk
    ./build.sh        # produit build/Lumen.apk (clé de test créée au premier passage)
    tests/run.sh      # tests JVM, sans Android ni émulateur

Sur une TV (ou un émulateur Android TV) branchée en adb :

    adb install -r build/Lumen.apk
    tools/smoke.sh build/Lumen.apk captures    # parcours de l'interface, captures, vérification des plantages

## Règles du code

- **Lisible pour un humain** : chaque fichier commence par une phrase qui dit ce qu'il fait ; les commentaires expliquent le pourquoi, en français.
- **Rien n'est envoyé qui ne soit décrit** dans [SECURITE.md](SECURITE.md). Un nouvel hôte réseau passe par `NetPolicy`, avec un test dans `tests/LumenTests.java`.
- **Les règles pures sont testées** : séparer ce qui ne dépend pas d'Android (`…Policy`, `Habits`, `Greeting`…) et l'ajouter à `tests/run.sh`.
- **Aucune dépendance** : pas de bibliothèque tierce, pas de Gradle imposé. Ça se compile avec `javac` et les outils Android de la distribution.
- **Textes à l'écran** calmes et sans jargon ; le détail technique va dans le journal d'erreurs (`Messages`).
- **Accessibilité** : tout élément sélectionnable a une `contentDescription` (les briques `Tile`, `GlassUi.listRow`, `About.info` le font déjà).
- **Arrondis imbriqués** : rayon externe = rayon interne + marge (padding). Une carte, un rail ou une vignette qui contient un élément arrondi prend son rayon dans `Radius` (`Radius.outer`, `menuCard`, `formCard`), jamais un nombre en dur ; côté téléphone, `phone.css` l'écrit `calc(interne + marge)`. `tests/LumenTests.java` le vérifie.
- **Couleurs et mouvement** : pas de couleur hexadécimale ni de durée en dur dans l'interface ; `Palette` (texte, surfaces, accent, danger) et `Motion` (rapide, moyen, lent, une seule courbe). Les dessins (paysages, météo) gardent leurs couleurs.
- **Textes** : écrits en français dans le code, toujours passés par `L.t("…")` (ou `L.all` pour un tableau) ; chaque nouveau texte reçoit sa ligne dans `assets/lang/en.tsv`, `tools/i18n_check.py` le vérifie (lancé par `tests/run.sh`).
- **Réglages** : un réglage à deux états est un `toggleRow`, à plusieurs valeurs un `choiceRow` ; une page de réglages ne dépasse pas dix lignes, au-delà on crée une sous-page.

## Proposer un changement

1. Une branche par sujet, des commits qui disent ce qu'ils changent pour l'utilisateur (« Recherche vocale depuis la télécommande »).
2. `tests/run.sh` au vert ; `./build.sh` passe.
3. Si le changement se voit, ajoute une ligne dans `CHANGELOG.md`, section de la prochaine version.
4. Ouvre la pull request : la CI compile, teste et parcourt l'interface sur un émulateur Android TV (captures en artefact).

## Signaler un problème, proposer une idée

Utilise les modèles d'issue (bogue, idée). Pour un bogue, Réglages › Système › Diagnostic › Journal d'erreurs sur la TV, ou Plus › Journal depuis le téléphone, donne le détail à coller.

## Publier (mainteneur)

Voir « Publier une mise à jour » dans [README.md](README.md). La clé de signature `lumen.keystore` ne quitte jamais la machine du mainteneur et n'est jamais commitée (`.gitignore`).

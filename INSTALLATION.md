# Lumen — installation sur une TV TCL (Google TV / Android TV)

## 1. Préparer la TV
1. Paramètres > Système > À propos > cliquer 7 fois sur « Build » (ou « Version de l'OS ») → mode développeur activé.
2. Paramètres > Système > Options pour les développeurs > activer « Débogage USB » (il active aussi le débogage par le réseau sur la plupart des TCL).
3. Noter l'adresse IP de la TV : Paramètres > Réseau et Internet > ton Wi-Fi.

## 2. Installer l'appli depuis un PC
Installer les « Android SDK Platform-Tools » (site developer.android.com), puis dans un terminal :

    adb connect 192.168.1.XX:5555      (accepter la demande sur la TV)
    adb install Lumen.apk

Sans PC : envoyer Lumen.apk sur la TV avec l'appli « Send Files to TV » et l'ouvrir avec un gestionnaire de fichiers.

## 3. Faire de Lumen l'écran d'accueil
Google TV ne permet pas de choisir l'accueil dans les menus : on désactive l'accueil Google (c'est réversible).

    adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
    adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith

(Android TV ancien modèle : remplacer par com.google.android.tvlauncher)

Appuyer ensuite sur Accueil : Lumen s'affiche. Si un choix est proposé, sélectionner Lumen puis « Toujours ».

## 4. Revenir à l'accueil d'origine

    adb shell pm enable com.google.android.apps.tv.launcherx
    adb shell pm enable com.google.android.tungsten.setupwraith

## Réglages
Bouton Réglages (ou touche Menu) : six rubriques — Fond d'écran, Accueil et applis, Mode veille, Ambiances, Réglages depuis le téléphone, Système. Chaque rubrique s'ouvre dans le même panneau ; ◀ ou Retour revient en arrière. Système › Informations : À propos (version, téléviseur, espace utilisé, serveurs contactés) et Licences (police, photos et vidéos avec leurs auteurs, météo, Pexels).

## Utilisation
- **Accueil** : tes favoris en vignettes de verre, centrés. Les boutons Entrées, Toutes les applications et Réglages sont en haut à droite, à côté de l'horloge.
- **Disposition** (Réglages › Applications) : nombre d'applis par ligne (3 à 7) et nombre de lignes (1 à 3).
- **Gestes** : ▼ depuis l'accueil → toutes les applis. ▲ depuis la première ligne des applis → retour à l'accueil. Retour fonctionne aussi.
- **Appui long sur OK** sur une appli : Déplacer (accueil), Ajouter/Retirer des favoris, Masquer l'appli, Infos, Désinstaller.
- **Nouvelle appli installée** : elle apparaît tout de suite dans « Toutes les applications » (une notification le signale).
- **Style des vignettes** (Réglages › Applications) : verre ou bannières officielles ; icônes affichées ou non ; verre neutre ou teinté de la couleur dominante de l'icône.
- **Touche Menu** de la télécommande : réglages.
- Après 45 s sans toucher la télécommande, l'interface s'efface pour ne laisser que le fond.

## Fonds d'écran (Réglages › Fond d'écran)
- **Paysages Lumen** : crêtes, horizon, lac — l'ambiance suit le vrai lever et coucher du soleil de ta ville (aube, jour, soir, nuit), calculés sur la TV sans Internet. Ils sont animés : nuages qui dérivent, halo du soleil, scintillement sur l'eau, brume, étoiles et étoiles filantes la nuit (Réglages › Fond d'écran › Paysages Lumen animés).
- **Paysages animés** : crêtes qui défilent lentement, brume qui glisse, étoiles qui scintillent la nuit.
- **Espace**, **Nature** et **Animaux** (photos primées — « Featured pictures » — de Wikimedia Commons, jusqu'à 4K ; vues d'artiste et schémas écartés), **Vidéos 4K** (à choisir sur aperçus : Réglages › Fond d'écran › Fonds vidéo ; rien n'est téléchargé sans être coché, décocher efface la vidéo ; une liste fermée de vraies prises de vue choisies une par une sur Wikimedia Commons : vues de drone de Paris, New York, Hong Kong, Chicago, San Francisco et Stockholm, paysages filmés, la Terre filmée depuis la Station spatiale ; ni conférences, ni simulations ; thème au choix (Tout, Villes, Nature, Espace et Terre) ; Réglages › Villes pour choisir les villes ; Espace pour les vidéos : 3, 6, 10 ou 20 Go ; qualité 4K ou HD au choix), **Mélange de photos**, **Photo du jour** (la photo choisie chaque jour par Wikimedia Commons, avec sa légende en français ; les 10 derniers jours, la plus récente en premier).
- Les photos se téléchargent en arrière-plan la première fois (quelques minutes) ; en attendant, les paysages Lumen s'affichent. Le cache se renouvelle chaque jour.
- **Choisir les fonds** (Réglages › Fond d'écran) : galerie de toutes les images de la source en miniatures ; OK coche ou décoche chaque image.
- **Masquer ce fond** : il ne reviendra plus. **Fonds masqués › Tout réafficher** pour annuler.
- Le titre du fond (et l'auteur / la licence pour les photos) s'affiche discrètement en haut à gauche.

## Effets
- **Météo dans le paysage** : avec une ville réglée, les paysages Lumen montrent le temps réel — pluie, bruine, neige, brume, ciel couvert, orage avec éclairs (Réglages › Fond d'écran › Météo dans le paysage).
- **Lueur de l'appli** : une lueur de la couleur de l'appli monte sous la vignette sélectionnée et la suit (Réglages › Accueil et applis).
- **Ouverture animée** : la vignette s'agrandit jusqu'à remplir l'écran avant d'ouvrir l'appli.
- **Sons de l'interface** : clics très discrets, désactivables (Réglages › Accueil et applis).
- **Entrées** : le bouton rond « Entrées » de l'accueil liste les prises HDMI et les chaînes TV et bascule directement dessus.

## Police
- Lumen utilise sa propre police, Inter (licence libre OFL, fichier de licence dans assets/fonts), identique sur toutes les TV quelle que soit la police du fabricant.

## Page Applications
- ▼ depuis l'accueil. Une recherche en haut (sans accents ni majuscules ; Retour efface la recherche), puis des rangées : Favoris, Vidéo et TV, Musique et radio, Jeux, Outils et autres. Les applis se rangent seules (catégorie déclarée par l'appli, sinon reconnue d'après son nom) ; chaque rangée défile horizontalement.

## Vignettes
- Réglages › Accueil et applis › Vignettes : « Nom de l'appli » (par défaut : le nom en grand ; sélectionnée, la vignette devient blanche et le nom prend la couleur de l'appli), « Icône et nom », ou « Bannières officielles » (liseré blanc autour de la vignette sélectionnée).

## Reprendre au-dessus des applis
- Sélectionne une appli de l'accueil (Netflix, Prime Video, Plex…) : ses films et épisodes en cours apparaissent juste au-dessus, avec l'affiche, l'épisode et la barre de progression. ▲ pour y aller, OK pour reprendre la lecture.
- Au premier lancement, Android demande l'autorisation d'accéder aux « programmes TV » : accepte-la. Sinon : Réglages › Accueil et applis › Reprendre au-dessus des applis.
- Chaque appli décide de publier ou non ses « en cours » sur Android TV : selon les applis et la version de Google TV, certaines n'apparaîtront pas. Réglages › Accueil et applis › Affiches des épisodes : Non pour n'afficher que la couleur de l'appli.

## Ambiances et minuterie de sommeil
- Bouton rond **Ambiances** (lune) en haut de l'accueil : de vraies vidéos filmées en plein écran, avec leur propre son : **Cheminée**, **Pluie**, **Aquarium**, **Nuit étoilée**, **Vagues**, **Neige**.
- **Choisir les vidéos** : chaque vidéo proposée s'affiche avec un aperçu, sa durée et sa définition ; OK coche ou décoche. Seules les vidéos cochées sont téléchargées (4 Go au maximum). Accès : menu des ambiances (OK) › Choisir les vidéos, ou Réglages › Ambiances › Vidéos des ambiances.
- Les propositions viennent d'une liste fermée de fichiers Wikimedia Commons choisis un par un (licence libre revérifiée), plus Pexels si tu as réglé ta clé. L'auteur et la licence s'affichent discrètement en bas à gauche.
- **Tes propres vidéos** : depuis le téléphone (Envois), choisis « Ambiance · Cheminée » (ou une autre) puis envoie la vidéo. Elle passe avant les autres dans cette ambiance.
- ◀ ▶ changent d'ambiance ; OK ouvre le menu (ambiance, ne plus montrer cette vidéo — elle est décochée et effacée —, choisir les vidéos, son de la vidéo, minuterie, quitter) ; Retour quitte.
- **Minuterie de sommeil** : 15, 30, 60 ou 90 min. À la fin, le son baisse doucement, l'écran passe au noir en 20 secondes et l'appli laisse la TV se mettre en veille selon son propre réglage (Paramètres de la TV › Énergie). Lumen ne peut pas éteindre la TV elle-même.

## Ciel et saisons
- **Décors de saison** (Réglages › Accueil et applis) : neige légère sur les paysages du 1er décembre au 6 janvier.

## Pexels (facultatif)
- Pexels propose des vidéos libres de droits de grande qualité (villes en 4K, cheminées longues…). Crée une clé gratuite sur pexels.com/api, puis colle-la depuis le téléphone (onglet Pexels).
- Les vidéos Pexels apparaissent alors dans les écrans de choix, à côté du catalogue Wikimedia ; rien n'est téléchargé sans être coché.
- Sans clé, Lumen ne contacte jamais Pexels. Réglages › Fond d'écran › Avancé › Pexels pour retirer la clé.

## Téléphone et Reprendre
- **Réglages depuis le téléphone** (Réglages, première ligne) : un QR code s'affiche ; scanne-le avec le téléphone connecté au même Wi-Fi. Cinq onglets en bas :
  - **TV** : télécommande (glisser sur le pavé pour se déplacer, toucher pour OK, garder le doigt pour défiler ; Retour, Accueil, lecture/pause, veille de l'écran en deux touches, volume), tes trois premiers favoris en raccourcis, écrire ou dicter (micro du clavier) en direct dans la recherche à l'écran — de Lumen ou de l'appli ouverte, ouvrir une appli ou une source HDMI, lancer une ambiance et sa minuterie, mode veille, fond suivant, source du fond ;
    La télécommande marche toujours dans Lumen. Pour les autres applis (Netflix, YouTube…), active une fois « Lumen télécommande » : Réglages › Réglages depuis le téléphone › Télécommande dans les autres applis (Paramètres › Accessibilité de la TV). Retour, Accueil, volume, lecture, raccourcis et texte marchent partout ; la veille demande Android 9, les flèches et OK hors de Lumen Android 13. Après la mise à jour 4.7, Android peut demander de réactiver le service (il peut maintenant écrire dans les champs de recherche).
  - **Accueil** : réorganiser, ajouter, retirer les favoris ; verrouiller une appli (avec le code PIN) ;
  - **Vidéos** : cocher les vidéos d'ambiance et de fond sur aperçus (liste fermée, décocher efface la vidéo) ;
  - **Envois** : envoyer tes photos et vidéos ;
  - **Plus** : code PIN, Pexels, sauvegarde, journal, déconnexion.

  Tout reste sur ton réseau local. Un seul téléphone à la fois ; la connexion survit quand tu ouvres une appli, et se coupe quand la TV s'éteint ou après 15 minutes sans activité.
- **Reprendre** : sur l'accueil, ◀ depuis la première vignette d'une ligne ouvre le panneau des dernières applis utilisées (▶ ou Retour pour le fermer). Par défaut, il connaît les applis ouvertes depuis Lumen. Pour y inclure aussi celles ouvertes autrement (bouton de la télécommande, Gemini…), autorise une fois :

    adb shell appops set com.lumen.launcher GET_USAGE_STATS allow

## Accueil
- **Bandeau d'accueil** : une ligne au-dessus des favoris — salutation selon l'heure, météo (si une ville est réglée) et dernier épisode en cours. Réglages › Accueil et applis › Bandeau d'accueil pour le masquer.
- L'appli que tu ouvres d'habitude à cette heure est sélectionnée au démarrage et entourée d'un liseré qui respire doucement.

## Recherche
- La recherche de « Toutes les applications » trouve aussi les épisodes en cours (Reprendre) et les réglages (« veille », « stockage », « pin »…).

## Stockage
- Réglages › Système › Stockage (ou sur le téléphone : Plus › Stockage de la TV) : place prise par les photos, les vidéos de fond et celles des ambiances, avec de quoi vider chaque catégorie. Tes photos et vidéos envoyées du téléphone ne sont jamais effacées là.
- Lumen garde toujours au moins 2 Go libres sur la TV : s'il manque de place, il réduit ses vidéos et n'en télécharge plus. Les téléchargements interrompus sont effacés au démarrage.

## Maison (Home Assistant)
- Bouton **Maison** en haut de l'accueil, à côté de « Toutes les applications » (masqué par défaut : Réglages › Accueil et applis › Bouton Maison ; il apparaît aussi tout seul dès que Home Assistant est relié) : tes lumières, prises, scènes, volets et lecteurs Home Assistant. OK pour allumer, éteindre ou lancer une scène ; l'état se met à jour tout seul.
- Pour relier Lumen : dans Home Assistant, Profil › Sécurité › Jetons d'accès longue durée › Créer un jeton. Puis sur le téléphone connecté à la TV : Plus › Home Assistant, colle l'adresse (ex. `192.168.1.20:8123` ou `homeassistant.local:8123`, ou ton adresse `https://…` à distance) et le jeton.
- Toujours sur le téléphone, coche les appareils à garder sur la TV (aucun coché : tous, 60 au plus).
- Serrures et alarmes ne sont jamais pilotables depuis la TV.
- **Scènes sur l'accueil** : sur le téléphone, ✦ Accueil à côté d'une scène ou d'un script : elle apparaît en raccourci sous le bandeau d'accueil (4 au plus), OK pour la lancer.
- **Cinéma** : sur le téléphone, Plus › Home Assistant › Cinéma, choisis la scène « film » (ex. lumières tamisées) et, si tu veux, la scène « pause ». Quand un film démarre dans une appli cochée (Netflix, Plex…), ouverte depuis Lumen, la scène se lance ; après 15 s de pause ou au retour sur l'accueil, la seconde. Lumen sait seulement si un son de type film est en cours : il ne lit rien d'autre.

## Mode veille et économiseur d'écran
- **Mode veille** : après quelques minutes sans activité (réglable : 1, 3, 5, 10 min ou jamais), les applis disparaissent ; il reste le fond en plein écran et une seule ligne discrète en bas : heure, date, météo, lever et coucher du soleil, phase de la Lune, et à droite la légende de l'image. La ligne se déplace de quelques pixels chaque minute pour protéger l'écran. Un appui sur la télécommande revient à l'accueil. Réglages › Mode veille › Lancer le mode veille pour l'essayer tout de suite.
- **Écran de nuit** (Réglages › Mode veille) : de 22 h, 23 h (par défaut) ou minuit jusqu'à 7 h, le mode veille devient presque noir avec une grande horloge très douce qui se déplace lentement. Le reste du temps, la lumière des images suit l'heure (dorée le matin, chaude le soir) et, le soir, la ligne du bas donne aussi la météo du lendemain.
- **Images du mode veille** (Réglages › Mode veille) : par défaut, l'image du fond d'écran, sans vidéo (si le fond est une vidéo, ce sont les paysages Lumen). On peut aussi choisir des images précises : paysages Lumen ou animés, espace, nature, animaux, mélange, mes photos, photo du jour — ou « Comme l'accueil » pour garder les vidéos.
- **Météo** : Réglages › Mode veille › Météo, puis tape le nom de ta ville. Seul ce nom est envoyé à Open-Meteo (gratuit, sans compte).
- **Économiseur d'écran de la TV** (le mode veille par-dessus n'importe quelle appli) : Réglages › Mode veille › Économiseur d'écran de la TV. Si Google TV cache ce choix, donne une seule fois l'autorisation depuis un ordinateur, puis appuie à nouveau sur cette ligne : Lumen se règle tout seul.

    adb shell pm grant com.lumen.launcher android.permission.WRITE_SECURE_SETTINGS

## Sources HDMI
Les entrées HDMI de la TV apparaissent sur l'accueil (ajoutées une seule fois) et dans la rangée « Sources (HDMI) » de la page Applications. On les déplace, retire ou masque comme une appli (appui long). OK ouvre directement la source.

## Code PIN
Réglages › Système › Code PIN. Une fois activé, le code est demandé pour ouvrir les réglages, masquer ou désinstaller une appli, et pour les applis verrouillées (appui long › Verrouiller par code). Code oublié : Paramètres de la TV › Applis › Lumen › Effacer les données.

## Applis selon l'heure
Lumen retient, sur la TV seulement, ce que tu ouvres à chaque moment de la journée : en revenant à l'accueil, l'appli habituelle est déjà sélectionnée, et la page Applications commence par une rangée « Ce soir », « Ce matin »… Réglages › Accueil et applis › Applis selon l'heure pour désactiver (l'historique est alors effacé).

## Lisibilité
Réglages › Accueil et applis › Lisibilité : taille du texte (normale, grande, très grande) et contraste renforcé (panneaux plus opaques, textes plus blancs).

## Sauvegarde des réglages
Page du téléphone › Plus › Télécharger la sauvegarde (un petit fichier .json) ; Restaurer… pour le renvoyer sur la TV, par exemple après une remise à zéro. Le code PIN et la clé Pexels n'y sont jamais inclus.

## Mises à jour
- Une fois par jour, quand Lumen est à l'écran, il regarde s'il existe une nouvelle version dans le dépôt GitHub du projet. Si oui, il la télécharge, vérifie qu'elle est signée avec la même clé que Lumen installé, puis Android demande « Mettre à jour ? » : un clic suffit.
- La toute première fois, Android demande d'autoriser Lumen à installer des applis : accepte (Paramètres › Applis › Lumen › Installer des applis inconnues).
- Réglages › Système › Mise à jour de Lumen : vérifier maintenant ou installer ; « Mises à jour automatiques » pour couper.

## Mesures et journal d'erreurs
- Réglages › Système › Mesures : affiche en haut à gauche le temps de démarrage, les images par seconde, la mémoire et le processeur utilisés par Lumen, et le type de fond. Utile pour repérer ce qui ralentit la TV.
- Journal d'erreurs : si Lumen plante, l'erreur est notée sur la TV (jamais envoyée). Elle est visible depuis le téléphone (onglet Plus) et s'efface dans Réglages › Système. Après deux plantages de suite au démarrage, Lumen repart en mode sans échec (paysages fixes, sans vidéo).
- Les vidéos sont en HD par défaut (plus légères pour la TV) ; la 4K reste disponible dans Réglages › Fond d'écran.

## Vie privée et sécurité
Aucune publicité, aucun compte, aucune télémétrie. Internet sert uniquement à télécharger, en HTTPS et depuis une liste fermée de serveurs (NASA, Wikimedia Commons, Open-Meteo), des contenus sous licence libre. Les fichiers sont vérifiés et les photos réécrites avant affichage. Détails complets dans SECURITE.md.

## Recompiler
Sous Ubuntu/Debian : `sudo apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-17-jdk`, puis `./build.sh`. Tests : `tests/run.sh`.
Fonds générés : `assets/scenes/` (moment_scène.jpg) et `assets/anim/<moment>/` (calques animés).

## Licence
Lumen est un logiciel libre : GNU GPL version 3 ou ultérieure (voir `LICENSE` ; Réglages › Système › Licences sur la TV).

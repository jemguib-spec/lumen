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
- **Paysages Lumen** : crêtes, horizon, lac — l'ambiance suit le vrai lever et coucher du soleil de ta ville (aube, jour, soir, nuit), calculés sur la TV sans Internet. Ils sont animés : nuages qui dérivent, halo du soleil, scintillement sur l'eau, brume, étoiles et étoiles filantes la nuit (Réglages › Paysages Lumen animés).
- **Paysages animés** : crêtes qui défilent lentement, brume qui glisse, étoiles qui scintillent la nuit.
- **Espace**, **Nature** et **Animaux** (photos primées — « Featured pictures » — de Wikimedia Commons, jusqu'à 4K ; vues d'artiste et schémas écartés), **Vidéos 4K** (à choisir sur aperçus : Réglages › Fond d'écran › Fonds vidéo ; rien n'est téléchargé sans être coché, décocher efface la vidéo ; une liste fermée de vraies prises de vue choisies une par une sur Wikimedia Commons : vues de drone de Paris, New York, Hong Kong, Chicago, San Francisco et Stockholm, paysages filmés, la Terre filmée depuis la Station spatiale ; ni conférences, ni simulations ; thème au choix (Tout, Villes, Nature, Espace et Terre) ; Réglages › Villes pour choisir les villes ; Espace pour les vidéos : 3, 6, 10 ou 20 Go ; qualité 4K ou HD au choix), **Mélange de photos**, **Photo du jour** (la photo choisie chaque jour par Wikimedia Commons, avec sa légende en français ; les 10 derniers jours, la plus récente en premier).
- Les photos se téléchargent en arrière-plan la première fois (quelques minutes) ; en attendant, les paysages Lumen s'affichent. Le cache se renouvelle chaque jour.
- **Choisir les fonds** (Réglages › Fond d'écran) : galerie de toutes les images de la source en miniatures ; OK coche ou décoche chaque image.
- **Masquer ce fond** : il ne reviendra plus. **Fonds masqués › Tout réafficher** pour annuler.
- Le titre du fond (et l'auteur / la licence pour les photos) s'affiche discrètement en haut à gauche.

## Effets
- **Météo dans le paysage** : avec une ville réglée, les paysages Lumen montrent le temps réel — pluie, bruine, neige, brume, ciel couvert, orage avec éclairs (Réglages › Mode veille › Météo dans le paysage).
- **Lueur de l'appli** : une lueur de la couleur de l'appli monte sous la vignette sélectionnée et la suit (Réglages › Affichage).
- **Ouverture animée** : la vignette s'agrandit jusqu'à remplir l'écran avant d'ouvrir l'appli.
- **Sons de l'interface** : clics très discrets, désactivables (Réglages › Affichage).
- **Entrées** : le bouton rond « Entrées » de l'accueil liste les prises HDMI et les chaînes TV et bascule directement dessus.

## Police
- Lumen utilise sa propre police, Inter (licence libre OFL, fichier de licence dans assets/fonts), identique sur toutes les TV quelle que soit la police du fabricant.

## Page Applications
- ▼ depuis l'accueil. Une recherche en haut (sans accents ni majuscules ; Retour efface la recherche), puis des rangées : Favoris, Vidéo et TV, Musique et radio, Jeux, Outils et autres. Les applis se rangent seules (catégorie déclarée par l'appli, sinon reconnue d'après son nom) ; chaque rangée défile horizontalement.

## Vignettes
- Réglages › Affichage › Vignettes : « Nom de l'appli » (par défaut : le nom en grand ; sélectionnée, la vignette devient blanche et le nom prend la couleur de l'appli), « Icône et nom », ou « Bannières officielles » (liseré blanc autour de la vignette sélectionnée).

## Reprendre au-dessus des applis
- Sélectionne une appli de l'accueil (Netflix, Prime Video, Plex…) : ses films et épisodes en cours apparaissent juste au-dessus, avec l'affiche, l'épisode et la barre de progression. ▲ pour y aller, OK pour reprendre la lecture.
- Au premier lancement, Android demande l'autorisation d'accéder aux « programmes TV » : accepte-la. Sinon : Réglages › Affichage › Reprendre au-dessus des applis.
- Chaque appli décide de publier ou non ses « en cours » sur Android TV : selon les applis et la version de Google TV, certaines n'apparaîtront pas. Réglages › Affichage › Affiches des épisodes : Non pour n'afficher que la couleur de l'appli.

## Ambiances et minuterie de sommeil
- Bouton rond **Ambiances** (lune) en haut de l'accueil : de vraies vidéos filmées en plein écran, avec leur propre son : **Cheminée**, **Pluie**, **Aquarium**, **Nuit étoilée**, **Vagues**, **Neige**.
- **Choisir les vidéos** : chaque vidéo proposée s'affiche avec un aperçu, sa durée et sa définition ; OK coche ou décoche. Seules les vidéos cochées sont téléchargées (4 Go au maximum). Accès : menu des ambiances (OK) › Choisir les vidéos, ou Réglages › Fond d'écran › Vidéos des ambiances.
- Les propositions viennent d'une liste fermée de fichiers Wikimedia Commons choisis un par un (licence libre revérifiée), plus Pexels si tu as réglé ta clé. L'auteur et la licence s'affichent discrètement en bas à gauche.
- **Tes propres vidéos** : depuis le téléphone (Envois), choisis « Ambiance · Cheminée » (ou une autre) puis envoie la vidéo. Elle passe avant les autres dans cette ambiance.
- ◀ ▶ changent d'ambiance ; OK ouvre le menu (ambiance, ne plus montrer cette vidéo — elle est décochée et effacée —, choisir les vidéos, son de la vidéo, minuterie, quitter) ; Retour quitte.
- **Minuterie de sommeil** : 15, 30, 60 ou 90 min. À la fin, le son baisse doucement, l'écran passe au noir en 20 secondes et l'appli laisse la TV se mettre en veille selon son propre réglage (Paramètres de la TV › Énergie). Lumen ne peut pas éteindre la TV elle-même.

## Ciel et saisons
- **Décors de saison** (Réglages › Affichage) : neige légère sur les paysages du 1er décembre au 6 janvier.

## Pexels (facultatif)
- Pexels propose des vidéos libres de droits de grande qualité (villes en 4K, cheminées longues…). Crée une clé gratuite sur pexels.com/api, puis colle-la depuis le téléphone (onglet Pexels).
- Les vidéos Pexels apparaissent alors dans les écrans de choix, à côté du catalogue Wikimedia ; rien n'est téléchargé sans être coché.
- Sans clé, Lumen ne contacte jamais Pexels. Réglages › Fond d'écran › Pexels pour retirer la clé.

## Téléphone et Reprendre
- **Réglages depuis le téléphone** (Réglages, première ligne) : un QR code s'affiche ; scanne-le avec le téléphone connecté au même Wi-Fi. La page permet de réorganiser, ajouter ou retirer les favoris, et d'envoyer tes propres photos et vidéos (source « Mes photos et vidéos », choisie automatiquement au premier envoi). Tout reste sur ton réseau local ; l'accès est protégé par un code unique contenu dans le QR code, et le serveur s'arrête seul après 15 minutes sans activité.
- **Reprendre** : sur l'accueil, ◀ depuis la première vignette d'une ligne ouvre le panneau des dernières applis utilisées (▶ ou Retour pour le fermer). Par défaut, il connaît les applis ouvertes depuis Lumen. Pour y inclure aussi celles ouvertes autrement (bouton de la télécommande, Gemini…), autorise une fois :

    adb shell appops set com.lumen.launcher GET_USAGE_STATS allow

## Mode veille et économiseur d'écran
- **Mode veille** : après quelques minutes sans activité (réglable : 1, 3, 5, 10 min ou jamais), les applis disparaissent ; il reste le fond en plein écran et une seule ligne discrète en bas : heure, date, météo, lever et coucher du soleil, phase de la Lune, et à droite la légende de l'image. La ligne se déplace de quelques pixels chaque minute pour protéger l'écran. Un appui sur la télécommande revient à l'accueil. Réglages › Mode veille › Lancer le mode veille pour l'essayer tout de suite.
- **Météo** : Réglages › Mode veille › Météo, puis tape le nom de ta ville. Seul ce nom est envoyé à Open-Meteo (gratuit, sans compte).
- **Économiseur d'écran de la TV** (le mode veille par-dessus n'importe quelle appli) : Paramètres de la TV › Système › Économiseur d'écran › Lumen. Si Google TV ne propose pas ce choix :

    adb shell settings put secure screensaver_components com.lumen.launcher/.LumenDream
    adb shell settings put secure screensaver_enabled 1

## Mises à jour
- Une fois par jour, quand Lumen est à l'écran, il regarde s'il existe une nouvelle version dans le dépôt GitHub du projet. Si oui, il la télécharge, vérifie qu'elle est signée avec la même clé que Lumen installé, puis Android demande « Mettre à jour ? » : un clic suffit.
- La toute première fois, Android demande d'autoriser Lumen à installer des applis : accepte (Paramètres › Applis › Lumen › Installer des applis inconnues).
- Réglages › Système › Mise à jour de Lumen : vérifier maintenant ou installer ; « Mises à jour automatiques » pour couper.

## Vie privée et sécurité
Aucune publicité, aucun compte, aucune télémétrie. Internet sert uniquement à télécharger, en HTTPS et depuis une liste fermée de serveurs (NASA, Wikimedia Commons, Open-Meteo), des contenus sous licence libre. Les fichiers sont vérifiés et les photos réécrites avant affichage. Détails complets dans SECURITE.md.

## Recompiler
Sous Ubuntu/Debian : `sudo apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-17-jdk`, puis `./build.sh`. Tests : `tests/run.sh`.
Fonds générés : `assets/scenes/` (moment_scène.jpg) et `assets/anim/<moment>/` (calques animés).

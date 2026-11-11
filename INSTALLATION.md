# Lumen — installation sur une TV TCL (Google TV / Android TV)

## 0. L'assistant sur la TV
Dès que Lumen est installé et ouvert sans être l'écran d'accueil, il affiche lui-même cette page (aussi dans Réglages › Système › Installation) : où en est la TV (accueil, accueil Google, historique pour « Reprendre », économiseur), les commandes exactes à taper avec l'adresse IP de la TV déjà remplie, un QR code vers ce guide, et les commandes pour revenir en arrière. Le reste de ce chapitre détaille les mêmes étapes.

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

## 5. Passer d'un ancien Lumen (com.lumen.launcher) au nouveau (fr.lumen.accueil)
L'identifiant de l'appli est devenu `fr.lumen.accueil` (enregistrement auprès de Google). Pour Android, c'est une autre appli : la mise à jour automatique d'un ancien Lumen la refuse (« Fichier refusé (autre appli) »), il faut l'installer une fois à la main.

1. Sur le téléphone : Plus › Sauvegarde des réglages, pour les retrouver ensuite (facultatif).
2. Installer le nouveau, l'ouvrir, puis retirer l'ancien :

       adb install Lumen.apk
       adb uninstall com.lumen.launcher

3. Redonner les autorisations facultatives (« Reprendre », économiseur d'écran) avec les commandes de ce guide, puis restaurer la sauvegarde depuis le téléphone.

## Réglages
Bouton Réglages (ou touche Menu) : six rubriques — Fond d'écran, Accueil et applis, Mode veille, Ambiances, Réglages depuis le téléphone, Système. Chaque rubrique tient en dix lignes au plus, le reste est rangé en sous-pages (Paysages Lumen, Avancé ; Vignettes et disposition, Reprendre, Lisibilité ; Installation, Diagnostic). Les réglages à deux états ont un interrupteur ; ceux à plusieurs valeurs se changent avec ◀ ▶ sans quitter la ligne. ◀ ou Retour revient en arrière. Système › Informations : À propos (version, téléviseur, espace utilisé, serveurs contactés) et Licences (police, photos et vidéos avec leurs auteurs, météo, Pexels).

## Utilisation
- **Accueil** : tes favoris en vignettes de verre, centrés. Les boutons Entrées, Toutes les applications et Réglages sont en haut à droite, à côté de l'horloge.
- **Disposition** (Réglages › Accueil et applis › Vignettes et disposition) : nombre d'applis par ligne (3 à 7) et nombre de lignes (1 à 3).
- **Gestes** : ▼ depuis l'accueil → toutes les applis. ▲ depuis la première ligne des applis → retour à l'accueil. Retour fonctionne aussi.
- **Appui long sur OK** sur une appli : Déplacer (accueil), Ajouter/Retirer des favoris, Masquer l'appli, Infos, Désinstaller.
- **Nouvelle appli installée** : elle apparaît tout de suite dans « Toutes les applications » (une notification le signale).
- **Style des vignettes** (Réglages › Accueil et applis › Vignettes et disposition) : verre ou bannières officielles ; icônes affichées ou non ; verre neutre ou teinté de la couleur dominante de l'icône.
- **Touche Menu** de la télécommande : réglages.
- Après 45 s sans toucher la télécommande, l'interface s'efface pour ne laisser que le fond.

## Fonds d'écran (Réglages › Fond d'écran)
- **Paysages Lumen** : crêtes, horizon, lac — l'ambiance suit le vrai lever et coucher du soleil de ta ville (aube, jour, soir, nuit), calculés sur la TV sans Internet. Ils sont animés : nuages qui dérivent, halo du soleil, scintillement sur l'eau, brume, étoiles et étoiles filantes la nuit (Réglages › Fond d'écran › Paysages Lumen).
- **Paysages animés** : crêtes qui défilent lentement, brume qui glisse, étoiles qui scintillent la nuit.
- **Espace**, **Nature** et **Animaux** (photos primées — « Featured pictures » — de Wikimedia Commons, jusqu'à 4K ; vues d'artiste et schémas écartés), **Vidéos 4K** (à choisir sur aperçus : Réglages › Fond d'écran › Fonds vidéo ; rien n'est téléchargé sans être coché, décocher efface la vidéo ; une liste fermée de vraies prises de vue choisies une par une sur Wikimedia Commons : vues de drone de Paris, New York, Hong Kong, Chicago, San Francisco et Stockholm, paysages filmés, la Terre filmée depuis la Station spatiale ; ni conférences, ni simulations ; thème au choix (Tout, Villes, Nature, Espace et Terre) ; Réglages › Villes pour choisir les villes ; Espace pour les vidéos : 3, 6, 10 ou 20 Go ; qualité 4K ou HD au choix), **Mélange de photos**, **Photo du jour** (la photo choisie chaque jour par Wikimedia Commons, avec sa légende en français ; les 10 derniers jours, la plus récente en premier).
- Les photos se téléchargent en arrière-plan la première fois (quelques minutes) ; en attendant, les paysages Lumen s'affichent. Le cache se renouvelle chaque jour.
- **Choisir les fonds** (Réglages › Fond d'écran) : galerie de toutes les images de la source en miniatures ; OK coche ou décoche chaque image.
- **Masquer ce fond** : il ne reviendra plus. **Fonds masqués › Tout réafficher** pour annuler.
- Le titre du fond (et l'auteur / la licence pour les photos) s'affiche discrètement en haut à gauche.

## Effets
- **Météo dans le paysage** : avec une ville réglée, les paysages Lumen montrent le temps réel — pluie, bruine, neige, brume, ciel couvert, orage avec éclairs (Réglages › Fond d'écran › Météo dans le paysage).
- **Couleurs accordées au fond** : la vignette sélectionnée et les interrupteurs prennent une nuance du fond affiché (blanc chaud à l'aube, bleuté la nuit) ; neutres sur un fond gris ou avec « Contraste renforcé ». Réglages › Accueil et applis › Vignettes et disposition pour revenir au blanc et au vert fixes.
- **Lueur de l'appli** : une lueur de la couleur de l'appli monte sous la vignette sélectionnée et la suit (Réglages › Accueil et applis).
- **Ouverture animée** : la vignette s'agrandit jusqu'à remplir l'écran avant d'ouvrir l'appli.
- **Sons de l'interface** : clics très discrets, désactivables (Réglages › Accueil et applis).
- **Entrées** : le bouton rond « Entrées » de l'accueil liste les prises HDMI et les chaînes TV et bascule directement dessus.

## Police
- Lumen utilise sa propre police, Inter (licence libre OFL, fichier de licence dans assets/fonts), identique sur toutes les TV quelle que soit la police du fabricant.

## Recherche vocale
- Touche **Recherche** ou **Assistant** (micro) de la télécommande, ou le bouton micro à côté de la recherche de la page Applications : la fenêtre de reconnaissance vocale d'Android s'ouvre, et ce que tu dis arrive dans la recherche de Lumen (appli, film, réglage, lumière…). Lumen n'écoute rien lui-même : c'est le service vocal de la TV qui reconnaît la phrase, comme pour n'importe quelle appli. Sur certaines TV, Android garde la touche micro pour son propre assistant ; le bouton micro à l'écran marche toujours.

## Langue
- Réglages › Accueil et applis › Lisibilité › Langue : Système (français si la TV est en français, sinon anglais), Français ou English. Réglage par profil. La page du téléphone suit la langue de la TV.

## Lecteur d'écran
- Avec TalkBack (Paramètres de la TV › Accessibilité), vignettes, boutons et lignes de réglage sont annoncés avec leur nom et leur valeur.

## Page Applications
- ▼ depuis l'accueil. Une recherche en haut (sans accents ni majuscules ; Retour efface la recherche), puis des rangées : Favoris, Vidéo et TV, Musique et radio, Jeux, Outils et autres. Les applis se rangent seules (catégorie déclarée par l'appli, sinon reconnue d'après son nom) ; chaque rangée défile horizontalement.

## Vignettes
- Réglages › Accueil et applis › Vignettes et disposition : « Nom de l'appli » (par défaut : le nom en grand ; sélectionnée, la vignette devient blanche et le nom prend la couleur de l'appli), « Icône et nom », ou « Bannières officielles » (liseré blanc autour de la vignette sélectionnée).

## Reprendre au-dessus des applis
- Sélectionne une appli de l'accueil (Netflix, Prime Video, Plex…) : ses films et épisodes en cours apparaissent juste au-dessus, avec l'affiche, l'épisode et la barre de progression. ▲ pour y aller, OK pour reprendre la lecture.
- Au premier lancement, Android demande l'autorisation d'accéder aux « programmes TV » : accepte-la. Sinon : Réglages › Accueil et applis › Reprendre.
- **Fond immersif** : reste une seconde sur une appli qui a un épisode en cours, le fond de l'accueil devient l'image de cet épisode, adoucie et assombrie, avec son titre en haut à gauche ; le paysage revient dès que tu passes à une autre appli. Mêmes images que « Reprendre », rien de plus n'est téléchargé. Réglages › Accueil et applis › Reprendre › Fond de l'épisode en cours pour le désactiver.
- Chaque appli décide de publier ou non ses « en cours » sur Android TV : selon les applis et la version de Google TV, certaines n'apparaîtront pas. Réglages › Accueil et applis › Reprendre › Affiches des épisodes : Non pour n'afficher que la couleur de l'appli.

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
  - **Plus** : réglages de la TV (source et changement du fond, retirer ou remettre chaque fond d'un toucher sur son aperçu, mode veille, écran de nuit, bandeau, horloge, sons…), Home Assistant, films du moment, stockage, sauvegarde ;
  - **Plus** : code PIN, Pexels, sauvegarde, journal, déconnexion.

  Tout reste sur ton réseau local. Un seul téléphone à la fois ; la connexion survit quand tu ouvres une appli, et se coupe quand la TV s'éteint ou après 15 minutes sans activité.
- **Reprendre** : sur l'accueil, ◀ depuis la première vignette d'une ligne ouvre le panneau des dernières applis utilisées (▶ ou Retour pour le fermer). Par défaut, il connaît les applis ouvertes depuis Lumen. Pour y inclure aussi celles ouvertes autrement (bouton de la télécommande, Gemini…), autorise une fois :

    adb shell appops set fr.lumen.accueil GET_USAGE_STATS allow

## Accueil
- **Bandeau d'accueil** : une ligne au-dessus des favoris — salutation selon l'heure, météo (si une ville est réglée) et dernier épisode en cours. Réglages › Accueil et applis › Bandeau d'accueil pour le masquer. Quelques conseils sur les gestes s'affichent aux premiers démarrages, un à la fois (Réglages › Système › Diagnostic › Revoir les conseils pour les revoir).
- L'appli que tu ouvres d'habitude à cette heure est sélectionnée au démarrage et entourée d'un liseré qui respire doucement.
- Les **boutons ronds** du haut affichent leur nom juste dessous quand on les sélectionne. Chaque bouton s'affiche ou se masque dans Réglages › Accueil et applis › Boutons de l'accueil (par profil). Le bouton Sons (haut-parleur, pour couper les sons de Lumen d'un appui) et le bouton Maison sont masqués par défaut. Bouton Réglages masqué : touche Menu, ou ▼ puis rechercher « réglages ». Le bouton Entrées n'apparaît que si la TV a des entrées (HDMI, chaînes).
- La **date** sous l'horloge suit la langue de Lumen, même si Android est réglé dans une autre langue.
- **État de la TV**, sous l'horloge, seulement quand quelque chose mérite un mot : pas de réseau, pile de la télécommande faible (Android 12 et plus, télécommandes Bluetooth qui l'annoncent), mise à jour prête. Rien quand tout va bien.

## À l'affiche
- Flèche **droite** depuis la dernière appli d'une rangée de l'accueil (le pendant de « Reprendre » à gauche), ou « affiche » dans la recherche : les films et séries du moment, avec affiches, année et note.
- Filtres : **Mes applis** (seulement ce qui est sur Netflix, Prime Video, Disney+, Canal+… installés sur la TV), **Partout** (le plus regardé cette semaine, toutes plateformes), ou une appli précise.
- Modes : **Populaire**, **Nouveautés**, **Mieux notés**, **Pour toi** (d'après les titres que tu as ouverts depuis « À l'affiche » ; calculé avec TMDB, l'historique reste sur la TV). Et Tout · Films · Séries.
- OK sur un titre : sa fiche. **Regarder sur** chaque appli qui le propose (icône de l'appli), **Bande-annonce** (YouTube), **Titres similaires**, **Chercher sur la TV**. ◀ ou Retour revient à la liste.
- La grande image indique aussi sur quelles applis le titre est disponible.
- Réglages › Accueil et applis › À l'affiche : ma clé (affichée masquée), **clé partagée du projet** (interrupteur), plateformes, sélection et type au départ, historique « Pour toi » (effaçable), tout désactiver.
- **Sans clé personnelle** : « Utiliser la clé partagée de Lumen » dans le panneau. Le projet publie une clé TMDB commune dans `dist/services.json` ; la TV la lit avec les mises à jour. Rien n'est contacté avant ton accord, et ta propre clé, si tu en mets une, passe avant.
- Il faut une clé gratuite TMDB (themoviedb.org › Compte › Paramètres › API) : à saisir sur la TV (Réglages › Accueil et applis › À l'affiche, ou directement depuis le panneau) ou à coller depuis le téléphone (Plus › Films du moment). Sans clé, Lumen ne contacte jamais ce service.

## Quand une appli bloque
- Appui long sur l'appli › **Fermer l'appli** : Android arrête ses processus ; elle redémarre proprement à la prochaine ouverture.
- Depuis le téléphone (onglet TV) : **Fermer l'appli** revient sur l'accueil puis ferme la dernière appli ouverte depuis Lumen.
- Si c'est Lumen qui se comporte mal : Réglages › Système › **Redémarrer Lumen**.

## Mises à jour
- Lumen vérifie toutes les 6 heures (et à l'ouverture de Réglages › Système) si une version plus récente existe, la télécharge depuis les GitHub Releases du projet, la vérifie (empreinte, taille, même clé de signature), puis Android demande la confirmation d'installation.
- **Nouveautés** (Réglages › Système) : ce qui change dans la version installée ou prête à installer.
- Pendant un téléchargement de photos ou de vidéos, un petit indicateur en bas à droite de l'accueil dit ce qui se passe (« Ambiances · Vidéo 2 sur 4 · 37 % »).

## Recherche
- Bouton **loupe** en haut de l'accueil (ou la recherche de « Toutes les applications ») : applis, épisodes en cours, réglages, et **Chercher « … » dans…** : la recherche de la TV (Google TV, toutes les applis d'un coup) et chaque appli qui accepte une recherche directe (Netflix, Plex, YouTube…). Une appli qui refuse la recherche directe s'ouvre simplement.
- Recherche tolérante : sans accents ni majuscules, début d'un mot (« mus » → YouTube Music), initiales (« ytm »), fautes de frappe (« netflx »). Les applis que tu ouvres souvent passent devant.
- « plex Dune », « netflix stranger things », « canal+ ligue 1 » : le nom d'une appli suivi d'un titre propose directement de chercher ce titre dans cette appli (Meilleur résultat).
- Trouve aussi les appareils Home Assistant (« salon » → allumer/éteindre) et les ambiances (« feu », « mer », « pluie »).
- Entrée sur le clavier lance le meilleur résultat. Champ vide : tes recherches récentes (effaçables).

## Sécurité du téléphone
- Chaque nouveau téléphone doit être **accepté sur la TV** après avoir scanné le QR code (« Autoriser », ou « Autoriser et se souvenir de ce téléphone » pour ne plus le redemander).
- TV › Réglages › Réglages depuis le téléphone (téléphone connecté) : **Accès du téléphone** « Complet » ou « Télécommande seulement », **Oublier les téléphones mémorisés**, **Déconnecter**.
- Lumen n'a accès ni au micro ni à la caméra de la TV.

## Stockage
- Réglages › Système › Stockage (ou sur le téléphone : Plus › Stockage de la TV) : place prise par les photos, les vidéos de fond et celles des ambiances, avec de quoi vider chaque catégorie. Tes photos et vidéos envoyées du téléphone ne sont jamais effacées là.
- Lumen ne remplit jamais la TV : il garde au moins 2 Go libres avant de télécharger une vidéo, et 500 Mo avant une photo. S'il manque de place, il réduit ses vidéos, met les téléchargements en pause et le dit à l'écran (détail dans Réglages › Système › Stockage). Les téléchargements interrompus sont effacés au démarrage.

## Maison (Home Assistant)
- Bouton **Maison** en haut de l'accueil, à côté de « Toutes les applications » (masqué par défaut : Réglages › Accueil et applis › Boutons de l'accueil › Maison ; il apparaît aussi tout seul dès que Home Assistant est relié) : tes lumières, prises, scènes, volets et lecteurs Home Assistant, en plein écran. Chaque appareil est une tuile de verre : allumée, une lumière éclaire sa tuile dans sa vraie couleur (ambrée, blanche, colorée) et une jauge montre son intensité ; les scènes sont des pastilles en haut. OK pour allumer, éteindre ou lancer une scène ; l'état se met à jour tout seul.
- **Régler** : OK maintenu sur une lumière à intensité variable, un volet ou un ventilateur, puis ◀ ▶ pour l'intensité, la position ou la vitesse (par pas de 10 %) ; OK ou Retour pour terminer.
- **Pièces** : si tes appareils sont rangés par pièce dans Home Assistant, un onglet par pièce apparaît en haut (Toute la maison, Salon, Cuisine…) ; se poser dessus l'ouvre.
- **Bouton Maison vivant** : le nombre de lumières allumées s'affiche sur le bouton rond de l'accueil, qui s'éclaire légèrement (relu chaque minute).
- Pour relier Lumen : dans Home Assistant, Profil › Sécurité › Jetons d'accès longue durée › Créer un jeton. Puis sur le téléphone connecté à la TV : Plus › Home Assistant, colle l'adresse (ex. `192.168.1.20:8123` ou `homeassistant.local:8123`, ou ton adresse `https://…` à distance) et le jeton. Home Assistant en `https://` sur le réseau local avec un certificat auto-signé (ex. `https://192.168.1.20:8123`) fonctionne aussi : Lumen retient ce certificat à la liaison ; si tu le changes, refais la liaison.
- Toujours sur le téléphone, coche les appareils à garder sur la TV (aucun coché : tous, 60 au plus).
- Serrures et alarmes ne sont jamais pilotables depuis la TV.
- **Scènes sur l'accueil** : sur le téléphone, ✦ Accueil à côté d'une scène ou d'un script : elle apparaît en pastille de verre sous le bandeau d'accueil (4 au plus), OK pour la lancer : une onde lumineuse part de la pastille, et la dernière scène lancée garde son étincelle colorée jusqu'à ce qu'un appareil change.
- **Cinéma** : sur le téléphone, Plus › Home Assistant › Cinéma, choisis la scène « film » (ex. lumières tamisées) et, si tu veux, la scène « pause ». Quand un film démarre dans une appli cochée (Netflix, Plex…), ouverte depuis Lumen, la scène se lance ; après 15 s de pause ou au retour sur l'accueil, la seconde. Lumen sait seulement si un son de type film est en cours : il ne lit rien d'autre.

## Mode veille et économiseur d'écran
- **Mode veille** : après quelques minutes sans activité (réglable : 1, 3, 5, 10 min ou jamais), les applis disparaissent ; il reste le fond en plein écran et une seule ligne discrète en bas : heure, date, météo, lever et coucher du soleil, phase de la Lune, et à droite la légende de l'image. La ligne se déplace de quelques pixels chaque minute pour protéger l'écran. Un appui sur la télécommande revient à l'accueil. Réglages › Mode veille › Lancer le mode veille pour l'essayer tout de suite.
- **Écran de nuit** (Réglages › Mode veille) : de 22 h, 23 h (par défaut) ou minuit jusqu'à 7 h, le mode veille devient presque noir avec une grande horloge très douce qui se déplace lentement. Le reste du temps, la lumière des images suit l'heure (dorée le matin, chaude le soir) et, le soir, la ligne du bas donne aussi la météo du lendemain.
- **Images du mode veille** (Réglages › Mode veille) : par défaut, l'image du fond d'écran, sans vidéo (si le fond est une vidéo, ce sont les paysages Lumen). On peut aussi choisir des images précises : paysages Lumen ou animés, espace, nature, animaux, mélange, mes photos, photo du jour — ou « Comme l'accueil » pour garder les vidéos.
- **Météo** : Réglages › Mode veille › Météo, puis tape le nom de ta ville. Seul ce nom est envoyé à Open-Meteo (gratuit, sans compte).
- **Économiseur d'écran de la TV** (le mode veille par-dessus n'importe quelle appli) : Réglages › Mode veille › Économiseur d'écran de la TV. Si Google TV cache ce choix, donne une seule fois l'autorisation depuis un ordinateur, puis appuie à nouveau sur cette ligne : Lumen se règle tout seul.

    adb shell pm grant fr.lumen.accueil android.permission.WRITE_SECURE_SETTINGS

## Notifications des applis
Quand une appli annonce une nouvelle sortie, un nouvel épisode ou un message, Lumen l'affiche dans une petite carte de verre à gauche de l'accueil, sous le nom du fond (6, 10 ou 20 s), puis la garde dans le volet des notifications. Depuis les boutons ronds du haut, ▲ fait descendre ce volet : toutes les notifications côte à côte, la plus récente à gauche ; ◀ ▶ pour choisir, OK l'ouvre dans son appli, ▼ ou Retour referme. La cloche (qui n'apparaît que lorsqu'il y a des notifications) ouvre le même volet. Retour efface la petite carte. Réglages › Notifications : activer ou couper, durée à l'écran, applis à faire taire une à une, « Essayer ».

Android doit d'abord autoriser Lumen à lire les notifications : Réglages › Notifications › Accès aux notifications ouvre l'écran d'Android quand la TV le propose. Si Google TV le cache, depuis un ordinateur :

    adb shell cmd notification allow_listener fr.lumen.accueil/com.lumen.launcher.NotifyService

(ou, si `WRITE_SECURE_SETTINGS` a déjà été donnée pour l'économiseur d'écran, la ligne « Accès aux notifications » suffit : Lumen s'autorise tout seul). Pour retirer l'accès : `adb shell cmd notification disallow_listener fr.lumen.accueil/com.lumen.launcher.NotifyService`.

## Paramètres de la TV
Le bouton écran en haut de l'accueil ouvre en un appui les paramètres d'Android les plus utiles : réseau et Internet, télécommandes et accessoires Bluetooth, son et sortie audio, image et écran, entrées HDMI, puis tous les paramètres. Avec un code PIN, il est demandé d'abord.

## Sources HDMI
Les entrées HDMI de la TV apparaissent sur l'accueil (ajoutées une seule fois) et dans la rangée « Sources (HDMI) » de la page Applications. On les déplace, retire ou masque comme une appli (appui long). OK ouvre directement la source.

## Profils
Chaque personne peut avoir son profil : ses favoris, ses applis masquées, son fond d'écran, son affichage (vignettes, taille du texte, contraste), son mode veille, ses sons, ses recherches et ses habitudes. Les clés API (TMDB, Pexels), Home Assistant, le code PIN, les applis verrouillées et les vidéos téléchargées restent communs à toute la maison.

- Ajouter, renommer ou supprimer : Réglages › Profils, ou téléphone › Plus › Profil (code PIN demandé s'il existe).
- « Qui regarde ? » : dès qu'il y a deux profils, une page plein écran propose de choisir la personne à chaque démarrage de Lumen et au rallumage de la TV, puis dit « Bonjour Léa » (ou Bonsoir…). Retour garde le profil actuel. Réglages › Profils › Au démarrage : « Reprendre le dernier profil » pour ne plus la voir (réglage commun à tous).
- Changer de profil à tout moment : bouton « Profils » en haut de l'accueil (même page, avec « Gérer les profils »), sans code, ou depuis le téléphone.
- **Profil enfant** (Réglages › Profils › le profil › Profil enfant) : dans ce profil, seules ses applis favorites existent ; la page Applications, la recherche, « Reprendre » et le téléphone ne voient rien d'autre. Le menu d'une appli se limite à « Fermer l'appli » et « Modifier (code PIN) ». Quitter le profil, ouvrir les réglages, gérer les profils, masquer ou désinstaller demandent le code PIN de la maison ; « À l'affiche » est fermé. Activer un profil enfant crée le code PIN s'il n'existe pas. L'adulte règle les favoris de l'enfant depuis ce profil (appui long › Modifier, avec le code), puis le laisse à l'enfant.
- Le profil principal garde les réglages d'avant les profils ; il ne peut pas être supprimé ni devenir un profil enfant.
- Les applis verrouillées le restent dans tous les profils : changer de profil ne lève jamais un verrou.

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
- Réglages › Système : ouvrir la page lance la recherche ; « Rechercher une mise à jour » pour vérifier ou installer ; « Mises à jour automatiques » pour couper.

## Mesures et journal d'erreurs
- Réglages › Système › Diagnostic › Mesures : affiche en haut à gauche le temps de démarrage, les images par seconde, la mémoire et le processeur utilisés par Lumen, et le type de fond. Utile pour repérer ce qui ralentit la TV.
- Journal d'erreurs : si Lumen plante, l'erreur est notée sur la TV (jamais envoyée). Elle est visible depuis le téléphone (onglet Plus) et s'efface dans Réglages › Système. Après deux plantages de suite au démarrage, Lumen repart en mode sans échec (paysages fixes, sans vidéo).
- Les vidéos sont en HD par défaut (plus légères pour la TV) ; la 4K reste disponible dans Réglages › Fond d'écran.

## Vie privée et sécurité
Aucune publicité, aucun compte, aucune télémétrie. Internet sert uniquement à télécharger, en HTTPS et depuis une liste fermée de serveurs (NASA, Wikimedia Commons, Open-Meteo), des contenus sous licence libre. Les fichiers sont vérifiés et les photos réécrites avant affichage. Détails complets dans SECURITE.md.

## Recompiler
Sous Ubuntu/Debian : `sudo apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-17-jdk`, puis `./build.sh`. Tests : `tests/run.sh`.
Fonds générés : `assets/scenes/` (moment_scène.jpg) et `assets/anim/<moment>/` (calques animés).

## Licence
Lumen est un logiciel libre : GNU GPL version 3 ou ultérieure (voir `LICENSE` ; Réglages › Système › Licences sur la TV).

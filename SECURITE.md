# Lumen 4.20 — sécurité, vie privée et organisation du code

## Ce que Lumen ne fait jamais
- Aucune télémétrie, aucune statistique, aucun compte, aucune publicité, aucun identifiant envoyé.
- Aucune requête réseau sortante en dehors de la liste ci-dessous, et uniquement des lectures (GET).
- Rien ne tourne quand Lumen n'est pas à l'écran : téléchargements mis en pause, serveur téléphone arrêté, liaison avec une autre TV arrêtée (elle rattrape les changements au retour), animations, vidéos et ambiances stoppées.
- « Reprendre » : Lumen lit, sur l'appareil et en lecture seule, les films et épisodes en cours que les applis publient (autorisation Android READ_TV_LISTINGS, demandée une fois). L'intention de lecture est refusée si elle vise une autre appli que celle qui l'a publiée, et les droits d'accès aux fichiers en sont retirés. Les affiches sont la seule exception à la liste blanche : adresse HTTPS publiée par l'appli elle-même, port standard, ni adresse IP ni nom local (le réseau de la maison reste hors d'atteinte), sans redirection, 3 Mo au plus, type d'image vérifié, image réduite en mémoire et jamais enregistrée. Désactivable (Réglages › Affichage › Affiches des épisodes).
- Mise à jour : Lumen ne lit que `dist/latest.json` et `dist/Lumen.apk` de la branche `main` du dépôt `jemguib-spec/lumen` (40 Mo au plus). Avant toute proposition d'installation, Lumen vérifie la taille et l'empreinte SHA-256 annoncées, que c'est bien une appli, que c'est Lumen, que la version est plus récente et qu'elle est signée avec exactement la même clé que Lumen installé ; Android refait ce contrôle et demande confirmation. Quelqu'un qui prendrait le contrôle du dépôt ne pourrait donc pas installer une appli piégée sans la clé de signature, qui n'est jamais publiée.
- Aucune vidéo n'est téléchargée sans avoir été cochée sur son aperçu ; décocher l'efface. Les aperçus sont de petites images vérifiées et réécrites comme les photos.
- Les fonds vidéo ne viennent que d'une liste fermée de fichiers Wikimedia Commons (`VideoCatalog`) : pas de recherche automatique, donc ni conférences ni simulations ; les anciennes vidéos hors liste sont effacées.
- Les vidéos d'ambiance ne viennent que d'une liste fermée de fichiers Wikimedia Commons (`CosyCatalog`) ; tout fichier hors liste est effacé. Elles suivent le même contrôle que les autres vidéos (licence libre revérifiée, signature du fichier, 500 Mo maximum par fichier, 4 Go en tout) ; une vidéo que la TV n'arrive pas à lire est écartée.
- Les photos « Espace » viennent des sélections de Wikimedia Commons (« Featured pictures »), plus de la recherche NASA ; les serveurs NASA sont retirés de la liste blanche.
- Les vidéos envoyées du téléphone vers une ambiance suivent les mêmes contrôles que les autres envois (vidéo uniquement, signature vérifiée).
- La Photo du jour passe par le même canal que le reste (Wikimedia Commons, licence libre vérifiée, fichier contrôlé puis réécrit) ; la légende est réduite à du texte brut, sans lien ni balise.

## Réseau sortant (`NetPolicy`, `Net`)
- HTTPS obligatoire, port 443 uniquement, pas d'identifiants dans les adresses.
- Liste blanche stricte : `raw.githubusercontent.com` (mise à jour : `dist/latest.json` du dépôt, toutes les 6 heures au plus ou à l'ouverture de Réglages › Système, désactivable), `github.com` **uniquement** pour un fichier sous `/jemguib-spec/lumen/releases/download/` (l'APK de la Release ; l'adresse est recomposée par Lumen à partir du numéro de version, jamais reprise telle quelle du fichier) et ses redirections `objects.githubusercontent.com` / `release-assets.githubusercontent.com`, `commons.wikimedia.org`, `upload.wikimedia.org`, `thumb.wikimedia.org` (aperçus), `api.open-meteo.com`, `geocoding-api.open-meteo.com` (météo et prévision des jours suivants, au plus toutes les 30 min, seulement si une ville est réglée). `api.pexels.com`, `videos.pexels.com`, `images.pexels.com` ne sont joignables que si l'utilisateur a réglé sa propre clé Pexels ; la clé n'est envoyée qu'à `api.pexels.com`, jamais après une redirection, et n'est jamais renvoyée au téléphone.
- Les redirections sont suivies à la main et chaque destination est revérifiée.
- Certificats validés par le magasin système d'Android ; les certificats ajoutés par l'utilisateur ne sont pas acceptés (cible Android 29). Le trafic en clair est interdit (`usesCleartextTraffic=false`).
- Réponses et fichiers plafonnés en taille.
- Home Assistant (facultatif) : seule exception à la liste blanche, l'adresse de TON Home Assistant, réglée depuis le téléphone (action protégée par le code PIN s'il existe). En HTTP, l'adresse doit être sur le réseau local, et elle est revérifiée après résolution : le jeton ne part jamais en clair sur Internet. En HTTPS, tout hôte est permis (accès à distance). Certificat auto-signé : accepté seulement pour une adresse du réseau local (revérifiée après résolution), au moment de la liaison ; Lumen lit le certificat sans rien envoyer, retient son empreinte SHA-256, et n'accepte ensuite que ce certificat-là pour cette adresse (un autre certificat = connexion refusée, il faut refaire la liaison). Sur Internet, un certificat reconnu reste exigé. Aucune redirection n'est suivie. Le jeton reste sur la TV : il n'est jamais renvoyé au téléphone et n'est pas dans la sauvegarde. Actions en liste fermée (allumer/éteindre, scène, script, bouton, volet, lecture, et le niveau d'une lumière, d'un volet ou d'un ventilateur, borné de 0 à 100) ; serrures et alarmes exclues. Lumen lit aussi la pièce de chaque appareil pilotable, par une demande fixe au moteur de modèles de Home Assistant : rien de ce que l'on tape n'y entre.
- « À l'affiche » (facultatif) : `api.themoviedb.org` et `image.tmdb.org` ne sont joignables que si l'utilisateur a réglé sa propre clé TMDB (depuis le téléphone ou la TV, code PIN demandé s'il existe) **ou accepté, une fois, la clé partagée du projet** (`dist/services.json`, lue avec `latest.json` ; la clé personnelle passe avant). La clé personnelle n'est jamais renvoyée au téléphone ni sauvegardée dans la sauvegarde. Seules des listes publiques de films sont demandées : rien sur ce que tu regardes n'est envoyé.

## Contenus légaux uniquement
- NASA : domaine public.
- Wikimedia Commons : seules les licences libres sont acceptées (domaine public, CC0, CC BY, CC BY-SA). Les licences « non commerciale » ou « pas de modification » et toute licence inconnue sont refusées. L'auteur et la licence s'affichent à l'écran.

## Fichiers photo et vidéo (`MediaSniff`, `MediaGuard`)
- Le type réel est vérifié par la signature binaire du fichier ; le nom et l'extension envoyés ne comptent pas. Seuls JPEG, PNG, WebP, MP4/MOV et WebM sont acceptés : un exécutable, une archive, un APK ou une page HTML déguisés sont refusés.
- Chaque image est décodée avec des limites (taille, nombre de pixels) puis **réécrite en JPEG neuf** : métadonnées (dont la position GPS), profils et données cachées disparaissent.
- Les vidéos sont lues par le lecteur média isolé d'Android, jamais par Lumen.
- Les fichiers sont nommés par Lumen et gardés dans son dossier privé. Rien n'est jamais exécuté.

## Réglages depuis le téléphone (`PhoneServer`, `PhoneAuth`, `HttpRequest`)
- Le serveur ne démarre qu'à l'ouverture du QR code (Réglages, ou l'étape « Clés et services » de l'assistant du premier démarrage, qui affiche le même QR code). Il s'arrête quand Lumen passe en arrière-plan sans téléphone connecté, quand l'écran de la TV s'éteint, après 15 minutes sans activité, après 5 tentatives d'accès refusées, ou sur demande (téléphone › Plus › Déconnecter, ou TV › Réglages › Réglages depuis le téléphone › Déconnecter).
- **Un seul téléphone à la fois.** Tant qu'il est connecté, une pastille discrète « Téléphone connecté » reste visible sur la TV.
- **Liste fermée d'actions** : lancer une appli installée et non masquée (ou une source HDMI), remplir un champ de Lumen (recherche, ville ; 100 caractères, sans caractère de contrôle), ambiances et minuterie (valeurs proposées seulement), mode veille, source du fond, fond suivant, cocher une vidéo **du catalogue fermé**. Jamais d'installation d'appli, d'ouverture d'adresse web, de commande système ni d'accès aux fichiers.
- Les actions visibles sur la TV ne sont faites que si Lumen est à l'écran : le téléphone ne peut pas interrompre une appli en cours.
- **Code PIN** : s'il existe, il est exigé sur le téléphone pour ouvrir une appli verrouillée, (dé)verrouiller une appli, restaurer une sauvegarde et changer la clé Pexels. Même vérification et mêmes attentes croissantes que sur la TV ; une fois saisi, il vaut 5 minutes pour cette session seulement.
- **Limite de débit** : rafale de 20 actions, puis 4 par seconde (les images ne comptent pas). Au-delà, la TV répond « 429 ».
- Les aperçus de vidéos ne sont servis que s'ils sont déjà sur la TV, pour une vidéo du catalogue ; le téléphone ne déclenche jamais de téléchargement arbitraire.
- Il écoute uniquement sur l'adresse locale de la TV et n'accepte que les appareils du réseau privé (10.x, 172.16-31.x, 192.168.x).
- Le QR code contient un jeton aléatoire de 128 bits, **à usage unique**, valable 10 minutes. Il est placé après « # » : le navigateur ne le met jamais dans l'adresse envoyée. La page l'échange une fois contre une session (cookie `HttpOnly`, `SameSite=Strict`, 30 min), puis l'efface de l'adresse.
- Toute requête d'API exige l'en-tête `X-Lumen` : une page d'un autre site ne peut pas forger de requête. L'en-tête `Host` est vérifié, ce qui bloque les attaques par « DNS rebinding ».
- En-têtes stricts : CSP sans script externe ni inline, pas d'iframe, `nosniff`, `no-referrer`.
- Requêtes strictement analysées : méthodes GET et POST seulement, chemins sans « .. », en-têtes et corps plafonnés, envoi fragmenté refusé, 4 connexions au plus.
- Les favoris reçus sont filtrés : uniquement des applis installées, sans doublon.
- Télécommande du téléphone : liste fermée de touches (flèches, OK, Retour, Accueil, volume, lecture). Hors de Lumen, elle passe par le service d'accessibilité facultatif « Lumen télécommande », désactivé tant que l'utilisateur ne l'active pas : il ne traite aucun événement, appuie seulement sur des touches globales (dont la veille de l'écran) et, quand le téléphone envoie du texte, cherche le champ de saisie de l'appli à l'écran pour y écrire ; rien de ce qu'il voit n'est gardé ni envoyé. Avec ce service actif, le téléphone peut aussi ouvrir une appli quand une autre est à l'écran. Touches et texte en direct ont leur propre limite de débit (12 par seconde, rafale de 30).
- Télécommande rapide : une connexion permanente (WebSocket, `/ws`) entre la page du téléphone et la TV. Mêmes garde-fous que le reste : session valide (cookie), page servie par la TV uniquement (en-tête Origin vérifié), 2 connexions au plus, messages en liste fermée (touche, texte, signe de vie), 4 Ko au plus, limite de débit ; coupée à la déconnexion du téléphone.
- Acceptation sur la TV : un téléphone qui a scanné le QR code n'a accès à rien tant que quelqu'un devant la TV ne l'a pas accepté (« Autoriser », ou « Autoriser et se souvenir ») ; sans réponse en 60 s, il est refusé et le serveur s'arrête. Une photo du QR code ne suffit donc pas. La TV ne garde qu'une empreinte (SHA-256) de l'identifiant aléatoire du téléphone mémorisé ; « Oublier les téléphones mémorisés » efface tout.
- Accès « Télécommande seulement » (TV › Réglages depuis le téléphone) : le téléphone ne peut plus que les touches, le texte, ouvrir une appli, la veille et les ambiances ; envois, favoris, sauvegarde, Home Assistant, clés et journal sont refusés par la TV.
- Micro et caméra : Lumen ne demande ni l'un ni l'autre à Android (aucune autorisation RECORD_AUDIO ni CAMERA) ; personne ne peut donc y accéder par Lumen. La page du téléphone les interdit aussi (Permissions-Policy).
- Session liée à l'appareil : la session ne fonctionne que depuis l'adresse Wi-Fi du téléphone qui a scanné le QR code ; un cookie intercepté sur le réseau est inutilisable depuis un autre appareil.
- Signature des échanges : le QR code contient aussi une clé secrète (après « # », jamais envoyée sur le réseau). Chaque requête et chaque message de la connexion permanente portent un numéro à usage unique et une signature HMAC-SHA256 (méthode, adresse, numéro et contenu ; pour l'envoi de fichiers, adresse et numéro). Sans la clé, impossible de fabriquer, modifier ou rejouer une requête, même en écoutant le Wi-Fi. Seules les images (icônes, aperçus) en sont dispensées.
- Chiffrement des secrets (`Seal`) : le jeton Home Assistant, les clés TMDB et Pexels, le code PIN et le texte tapé sur le téléphone (y compris en direct) sont chiffrés par la page avec une clé tirée de celle du QR code, qui n'est jamais envoyée. La TV refuse ces envois s'ils arrivent en clair. Le message chiffré est ensuite signé comme le reste : impossible à lire, à modifier ou à rejouer, même en écoutant le Wi-Fi. Le même calcul est vérifié des deux côtés (Java et le vrai code de la page, exécuté par les tests).
- Ce qui reste lisible sur le réseau local (page en HTTP : un certificat local provoquerait une alerte du navigateur à chaque connexion) : uniquement des informations sans secret, comme la liste des applis, les réglages affichés ou la touche pressée. Personne ne peut pour autant agir à ta place : chaque requête est signée.

## Deux TV reliées (`TvSync`, `SyncCrypto`, `SyncState`, `SyncStore`, `PairLink`, `SyncLink`, `SyncPanel`)
- Réglages › Système › Autres TV (code PIN demandé s'il existe, comme pour tous les réglages), ou « Me synchroniser avec une autre TV du foyer » dans l'assistant du premier démarrage (TV tout juste installée, sans code PIN encore). Seule exception au réseau sortant décrit plus haut : les deux TV se parlent, sur le réseau local uniquement (adresse IPv4 privée 10.x, 172.16-31.x, 192.168.x, revérifiée avant chaque connexion, sans proxy ni redirection).
- **Relier : un code à 6 chiffres.** La TV qui reçoit n'ouvre son serveur de liaison (port 8766) qu'à l'ouverture de « Recevoir », sur son adresse locale, n'accepte que les appareils du réseau privé et s'annonce (DNS-SD, `_lumen-sync._tcp`) le temps de la réception. Tout s'arrête au premier envoi accepté, après 3 minutes, après 5 codes faux, en fermant l'écran, quand Lumen quitte l'écran ou quand la TV s'éteint.
- **Échange authentifié par le code** (`SyncCrypto`) : le code sert à établir une clé par un échange authentifié par mot de passe (SPAKE2, groupe MODP de 2048 bits de la RFC 3526, exposants secrets de 256 bits, valeurs reçues vérifiées dans le bon sous-groupe), puis le contenu part en AES-256-GCM. Quelqu'un qui écoute le Wi-Fi ne voit que des nombres au hasard : il ne peut ni lire l'envoi, ni retrouver le code, même hors ligne avec un ordinateur. Celui qui se ferait passer pour l'une des deux TV n'a qu'un essai de code par échange ; la TV qui reçoit change sa valeur publique après chaque mauvais code et s'arrête après 5 : une chance sur 200 000. Toute modification en route est détectée (GCM).
- **Rien ne change sans confirmation** devant la TV qui reçoit : elle dit qu'elle va prendre tous les réglages de l'autre et rester identique ensuite, puis « Relier et prendre ses réglages » ou « Refuser ».
- **Clé de liaison** : tirée de la clé de cet échange (jamais la même), rangée chiffrée (`Vault`) sur chaque TV, avec l'identifiant au hasard de l'autre TV. Ensuite, chaque TV n'écoute (port 8767, appareils du réseau privé seulement) **que tant que Lumen est à l'écran**, et s'annonce sur le réseau sous un nom tiré de son identifiant (`_lumen-pair._tcp`, « lumen-<identifiant> ») pour être retrouvée si son adresse change. Chaque message est chiffré et authentifié avec la clé de liaison (AES-256-GCM), avec l'expéditeur et le destinataire dans les données authentifiées : un message qui ne vient pas de l'autre TV, ou renvoyé dans l'autre sens, est refusé (et ralenti au-delà de quelques essais). « Délier » efface la clé des deux côtés.
- **Ce qui voyage** (`SyncState`, liste fermée) : tous les réglages, de tous les profils, la liste des profils, l'empreinte salée du code PIN, les applis verrouillées et masquées, les habitudes et l'historique, les vidéos cochées (chaque TV télécharge alors les mêmes), la ville de la météo, les clés TMDB et Pexels, la liaison Home Assistant (adresse, jeton, empreinte du certificat). Les secrets sont rouverts pour l'échange chiffré puis rechiffrés à l'arrivée avec la clé propre à la TV (`Vault`) ; jamais dans la sauvegarde ni vers le téléphone. **Restent sur chaque TV** : touches de la télécommande, place réservée aux vidéos, qualité 4K, profil affiché en ce moment, tentatives de code PIN, téléphones mémorisés, mises à jour, mesures, assistant. Un test (`tools/sync_check.py`) vérifie que chaque réglage enregistré par Lumen est classé dans l'une des deux listes : un nouveau réglage ne peut pas voyager par oubli.
- **Contrôles à l'arrivée** : chaque valeur reçue passe les mêmes contrôles qu'une sauvegarde ou que le téléphone (type, bornes, format des clés, adresse Home Assistant en http seulement sur le réseau local, format de l'empreinte du PIN, coordonnées de la météo) ; une valeur refusée est ignorée. Le plus récent gagne, réglage par réglage ; une date dans le futur lointain est ignorée.
- Ce qui reste lisible sur le réseau local : que deux TV Lumen se parlent, leurs annonces, la taille des échanges. Rien du contenu. Une TV en Lumen 4.31 à 4.33 est reconnue et doit d'abord être mise à jour : jamais d'envoi non chiffré.
- Testé hors Android : chiffrement (bon et mauvais code, contenu modifié, valeurs piégées, rejeu), fusion, et deux TV complètes dans un même programme (`tests/DeuxTV.java` : liaison, changements dans les deux sens, suppressions, changements simultanés, télécommande qui reste sur place, intrus refusé, déliaison).

## Secrets gardés sur la TV (`Vault`)
- Le jeton Home Assistant et les clés TMDB et Pexels sont chiffrés (AES-256-GCM) avec une clé créée dans l'Android Keystore de la TV, qui ne sort jamais du matériel quand celui-ci le permet. Une copie des préférences ne contient donc aucun secret lisible. Les valeurs enregistrées par une version précédente sont chiffrées à la première lecture. Sur Android 5, sans Keystore AES, elles restent telles quelles.
- Le code PIN n'est pas un secret stocké : seul son empreinte PBKDF2 salée est gardée.
- La sauvegarde (`Backup`) est une liste blanche de réglages : aucun de ces secrets n'y figure.

## Permissions Android
- `INTERNET` : photos, vidéos, météo, mises à jour.
- Aucune autorisation micro : la recherche vocale ouvre la fenêtre de reconnaissance d'Android (`RecognizerIntent`) ; c'est le service vocal de la TV qui écoute et transcrit, Lumen ne reçoit que le texte et ne l'envoie nulle part.
- `REQUEST_DELETE_PACKAGES` : proposer la désinstallation d'une appli, que tu confirmes à chaque fois.
- `PACKAGE_USAGE_STATS` (facultative, à accorder par ADB) : historique pour « Reprendre ».
- `WRITE_SECURE_SETTINGS` (facultative, à accorder par ADB) : sert uniquement à choisir Lumen comme économiseur d'écran (`ScreenSaver`) et, si tu le demandes dans Réglages › Notifications, à ajouter Lumen aux applis qui lisent les notifications (`NotifyService.grantSelf`, les autres applis autorisées le restent) ; aucun autre réglage de la TV n'est touché.
- Accès aux notifications (facultatif, `NotifyService`, donné dans Android ou par ADB) : Lumen lit le titre et le texte des notifications des applis pour les afficher sur l'accueil. Lecture seule et en mémoire : rien n'est écrit sur le disque ni envoyé, ni au téléphone ni sur Internet ; Lumen ne supprime jamais la notification d'une autre appli (« Tout effacer » ne vide que sa propre liste). Rien ne s'affiche dans un profil enfant ; pour une appli verrouillée par code, seul son nom apparaît et l'ouvrir redemande le code. Les notifications permanentes (lecture, téléchargements, services) et les recommandations sont ignorées.
- `READ_TV_LISTINGS` : lire les épisodes en cours (« Reprendre ») et la liste des entrées HDMI, sur l'appareil.

## Profils (`Profiles`, `ProfilePrefs`, `ProfilePanel`, `WhoPage`)
- Chaque profil a ses réglages personnels (liste fermée `Profiles.PERSONAL` : favoris, applis masquées, fonds, affichage, veille, sons, recherches, habitudes…). Tout le reste est commun : clés API, Home Assistant, code PIN et ses tentatives, **applis verrouillées** (changer de profil ne lève jamais un verrou), téléphones mémorisés, mises à jour.
- Changer de profil ne demande pas de code ; ajouter, renommer ou supprimer un profil passe par le code PIN s'il existe (sur la TV comme depuis le téléphone).
- **Profil enfant** (`Profile.kid`) : la TV ne connaît plus que ses applis favorites (`Prefs.appAllowed`, appliqué à la page Applications, à la recherche, à « Reprendre », à « À l'affiche » et aux demandes du téléphone). Quitter ce profil, ouvrir les réglages, gérer les profils, le menu complet d'une appli, masquer et désinstaller exigent le code PIN ; le téléphone ne peut pas changer de profil depuis un profil enfant. Un profil enfant ne peut être activé que si un code PIN existe (il est créé sinon) ; le profil principal ne peut pas l'être.
- Une sauvegarde exporte et restaure les réglages du profil actif ; elle ne contient toujours ni code PIN ni clés.
- Le profil principal garde les réglages d'avant les profils : rien n'est migré, rien ne peut être perdu à la mise à jour.

## Code PIN (`Pin`, `PinDialog`)
- Facultatif. Protège l'ouverture des réglages (donc aussi le QR code du téléphone), le masquage et la désinstallation des applis, et les applis que tu verrouilles (y compris depuis « Reprendre »).
- Protège aussi la gestion des profils (ajout, renommage, suppression).
- Jamais enregistré en clair : sel aléatoire de 16 octets + PBKDF2 (20 000 tours), comparaison à temps constant.
- Après 5 erreurs, attente de 30 s, doublée à chaque nouvelle erreur (1 h au plus) ; le compteur survit au redémarrage de Lumen.
- Code oublié : Paramètres de la TV › Applis › Lumen › Effacer les données (tous les réglages de Lumen repartent à zéro).

## Sauvegarde des réglages (`Backup`)
- Export et import depuis la page du téléphone (onglet Plus), donc derrière l'appairage par QR code.
- Liste blanche stricte : chaque clé a un type et des bornes ; tout le reste est ignoré. Le code PIN, la clé Pexels, l'état des mises à jour et l'historique ne sont jamais exportés ni importés.
- Une sauvegarde peut ajouter des verrous d'applis, jamais en retirer.

## Habitudes selon l'heure (`Habits`)
- Compte des ouvertures d'applis par moment de la journée, gardé uniquement sur la TV (désactivable, effacé à la désactivation).
- Pas de micro, pas de caméra, pas de localisation, pas de contacts, pas de stockage partagé.

## Organisation du code (`src/com/lumen/launcher`)
| Fichier | Rôle |
|---|---|
| `MainActivity` | Accueil, page des applis, gestes, superpositions ; délègue aux classes ci-dessous |
| `PhoneBridge` | Ce que la TV fait à la demande du téléphone (touches, applis, texte, ambiances, réglages, acceptation, pastille « connecté ») |
| `Veille`, `UpdateFlow`, `DownloadPill` | Mode veille en cascade ; mises à jour et redémarrage ; indicateur de téléchargement |
| `GlassUi`, `Palette`, `Motion` | Briques des panneaux en verre (carte, lignes, interrupteurs, choix, en-têtes) ; couleurs et durées nommées de l'interface |
| `Tips` | Quelques conseils sur les gestes, un par démarrage |
| `TvStatus` | État de la TV sous l'horloge (réseau, pile de la télécommande, mise à jour) : lecture locale, rien d'envoyé |
| `L`, `Lang` | Textes de l'interface et dictionnaires `assets/lang/*.tsv` (anglais, TV et page du téléphone ; `/app.lang` sert le dictionnaire de la page, fichier fixe) ; couverture vérifiée par `tools/i18n_check.py` |
| `HomeBuilder`, `AppLauncher` | Construction de l'écran d'accueil ; ouverture et fermeture des applis, redémarrage |
| `SettingsPanel`, `GalleryPanel`, `ResumePanel`, `PhonePanel`, `InputsPanel`, `CityPanel` | Un panneau par fonction |
| `Tile`, `Glass`, `Glyph`, `Glow`, `AnimatedScene`, `WeatherFx`, `AmbientView` | Composants visuels |
| `Wall`, `Library`, `WallItem` | Moteur de fonds (images, scènes animées, vidéos) |
| `Online` | Recherche et cache des photos et vidéos NASA / Wikimedia |
| `Net`, `NetPolicy` | Réseau sortant sécurisé |
| `MediaSniff`, `MediaGuard` | Contrôle des fichiers |
| `PhoneServer`, `PhoneAuth`, `HttpRequest`, `QrCode`, `PhonePanel` | Réglages depuis le téléphone : serveur local, appairage, QR code |
| `PhoneGate` | Contrôles d'accès de la page du téléphone (Host, X-Lumen, session, acceptation, « télécommande seulement », signature, débit, code PIN, connexion permanente) |
| `Seal` | Chiffrement des secrets envoyés par le téléphone (clé tirée de celle du QR code) |
| `Vault`, `VaultCrypto` | Coffre des secrets sur la TV : jeton Home Assistant, clés TMDB et Pexels chiffrés en AES-256-GCM avec une clé de l'Android Keystore (Android 6+), jamais en clair dans les préférences |
| `Profiles`, `ProfilePrefs`, `ProfilePanel`, `WhoPage` | Profils par personne : réglages personnels ou communs, gestion des profils, page « Qui regarde ? » |
| `Signer`, `WsFrames`, `RateLimit` | Signature HMAC des échanges, connexion permanente (WebSocket), limite de débit |
| `PhoneRemote`, `RemoteService` | Touches de la télécommande du téléphone, dans Lumen puis dans les autres applis (service d'accessibilité facultatif) |
| `DeviceName` | Nom du téléphone affiché sur la TV |
| `HomeAssistant`, `HaStates`, `HomePanel`, `HaTile`, `Cinema` | Home Assistant : appareils et scènes, page « Maison » (tuiles, pièces, réglage du niveau), mode cinéma |
| `Tmdb`, `TmdbPolicy`, `RecoPanel` | « À l'affiche » : films et séries du moment (clé TMDB personnelle) |
| `KeyPanel` | Saisie des clés TMDB et Pexels directement sur la TV |
| `Pin`, `PinDialog` | Code PIN |
| `Backup`, `Habits` | Sauvegarde des réglages, habitudes selon l'heure |
| `AppsPage`, `AppMenu`, `SmartSearch`, `SettingsIndex` | Page Applications, menu de l'appui long, recherche |
| `Greeting`, `Messages`, `LumenScreen` | Bandeau d'accueil, messages à l'écran, écran « lumen » de mise à jour |
| `CheckCell` | Vignette cochable commune (galerie des fonds, choix des vidéos) |
| `SafeFile` | Écriture sûre des index et catalogues (rien de corrompu si la TV est débranchée) |
| `About`, `StoragePanel`, `Perf`, `CrashLog` | À propos et licences, stockage, mesures, journal d'erreurs local |
| `ScreenSaver` | Lumen comme économiseur d'écran |
| `NotifyService`, `NotifyPolicy`, `NotifyUi` | Notifications des applis sur l'accueil (facultatif) : lecture locale et en mémoire, tri de ce qui mérite d'être montré, carte, cloche et liste |
| `TvQuickPanel` | Paramètres de la TV en un appui (réseau, Bluetooth, son, image, entrées) : ouvre les écrans d'Android, ne change rien lui-même |
| `Forecast` | Prévision du volet (maintenant et trois jours) : mots et pictogrammes du ciel, cache texte |
| `DateLine`, `DateClock` | Date sous l'horloge et dans le mode veille, dans la langue de Lumen |
| `Sky` | Lever / coucher du soleil, phase de lune, saisons (calcul local, sans réseau) |
| `TopShelf`, `WatchNext`, `WatchNextFormat` | « Reprendre » au-dessus des applis : lecture des « en cours » publiés par les applis, affiches contrôlées |
| `ApkProvider` | Prête le fichier de mise à jour déjà vérifié à l'installateur d'Android : non exporté, lecture seule, ce seul fichier, accès accordé le temps de l'installation |
| `Updater`, `UpdatePolicy` | Mise à jour automatique : `dist/latest.json` du dépôt, APK de la GitHub Release de la version (adresse recomposée par Lumen, jamais reprise du fichier), vérification de l'empreinte et de la signature ; nouveautés nettoyées avant affichage |
| `Install`, `InstallPolicy` | Assistant d'installation : état de l'accueil (lecture seule, `PackageManager`), commandes ADB à recopier ; Lumen ne désactive rien lui-même |
| `LivingScene` | Animation des paysages Lumen (dessinée sur l'appareil) |
| `AppCategory` | Rangées de la page Applications et recherche |
| `Fonts` | Police Inter embarquée (assets/fonts, licence OFL) |
| `VideoPicker` | Choix des vidéos sur aperçus : rien n'est téléchargé sans être coché |
| `Pexels` | Option Pexels (clé personnelle) : requêtes, choix du fichier, contrôle de la clé |
| `VideoCatalog` | Liste fermée des fonds vidéo autorisés (aucune recherche par mots-clés) |
| `AmbiancePanel`, `CosyCatalog` | Ambiances en vraies vidéos (lecture, minuterie de sommeil) et liste fermée des vidéos autorisées |
| `AppRepo`, `Prefs`, `Weather`, `Sounds`, `LumenDream` | Applis, réglages, météo, sons, économiseur d'écran |

## Tests
Le dépôt GitHub compile et lance les tests à chaque envoi (`.github/workflows/verifier.yml`) et vérifie que `dist/latest.json` décrit bien `dist/Lumen.apk`.

`tests/run.sh` lance plus de 360 tests sur la politique réseau, le code PIN, la sauvegarde, les habitudes, les licences, les signatures de fichiers, le parseur HTTP, l'authentification, **les contrôles d'accès de la page du téléphone** (Host, X-Lumen, session volée, appareil, « télécommande seulement », signature, débit, code PIN, ouverture et messages de la connexion permanente, sur de vraies requêtes HTTP), la signature HMAC, le WebSocket, TMDB, Home Assistant, les catalogues de vidéos (fonds et ambiances), l'option Pexels et les calculs du ciel (comparés aux éphémérides de l'IMCCE, ±3 min). Il vérifie aussi que le QR code est lisible par un vrai décodeur (`zbarimg`). La CI installe ensuite l'APK sur un émulateur Android TV et parcourt l'interface (`tools/smoke.sh`) : captures d'écran et journal en artefacts, échec si Lumen plante ou se bloque.

## Licence
Lumen est un logiciel libre sous licence GNU GPL version 3 ou ultérieure (fichier `LICENSE`, en-tête `SPDX-License-Identifier: GPL-3.0-or-later` dans chaque fichier source). La police Inter reste sous SIL Open Font License 1.1.

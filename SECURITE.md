# Lumen 4.1 — sécurité, vie privée et organisation du code

## Ce que Lumen ne fait jamais
- Aucune télémétrie, aucune statistique, aucun compte, aucune publicité, aucun identifiant envoyé.
- Aucune requête réseau sortante en dehors de la liste ci-dessous, et uniquement des lectures (GET).
- Rien ne tourne quand Lumen n'est pas à l'écran : téléchargements mis en pause, serveur téléphone arrêté, animations, vidéos et ambiances stoppées.
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
- Liste blanche stricte : `raw.githubusercontent.com` (mise à jour : deux fichiers du dépôt, une fois par jour au plus, désactivable), `commons.wikimedia.org`, `upload.wikimedia.org`, `thumb.wikimedia.org` (aperçus), `api.open-meteo.com`, `geocoding-api.open-meteo.com` (météo seulement si une ville est réglée). `api.pexels.com`, `videos.pexels.com`, `images.pexels.com` ne sont joignables que si l'utilisateur a réglé sa propre clé Pexels ; la clé n'est envoyée qu'à `api.pexels.com`, jamais après une redirection, et n'est jamais renvoyée au téléphone.
- Les redirections sont suivies à la main et chaque destination est revérifiée.
- Certificats validés par le magasin système d'Android ; les certificats ajoutés par l'utilisateur ne sont pas acceptés (cible Android 29). Le trafic en clair est interdit (`usesCleartextTraffic=false`).
- Réponses et fichiers plafonnés en taille.

## Contenus légaux uniquement
- NASA : domaine public.
- Wikimedia Commons : seules les licences libres sont acceptées (domaine public, CC0, CC BY, CC BY-SA). Les licences « non commerciale » ou « pas de modification » et toute licence inconnue sont refusées. L'auteur et la licence s'affichent à l'écran.

## Fichiers photo et vidéo (`MediaSniff`, `MediaGuard`)
- Le type réel est vérifié par la signature binaire du fichier ; le nom et l'extension envoyés ne comptent pas. Seuls JPEG, PNG, WebP, MP4/MOV et WebM sont acceptés : un exécutable, une archive, un APK ou une page HTML déguisés sont refusés.
- Chaque image est décodée avec des limites (taille, nombre de pixels) puis **réécrite en JPEG neuf** : métadonnées (dont la position GPS), profils et données cachées disparaissent.
- Les vidéos sont lues par le lecteur média isolé d'Android, jamais par Lumen.
- Les fichiers sont nommés par Lumen et gardés dans son dossier privé. Rien n'est jamais exécuté.

## Réglages depuis le téléphone (`PhoneServer`, `PhoneAuth`, `HttpRequest`)
- Le serveur ne démarre qu'à l'ouverture du QR code. Il s'arrête quand Lumen passe en arrière-plan sans téléphone connecté, quand l'écran de la TV s'éteint, après 15 minutes sans activité, après 5 tentatives d'accès refusées, ou sur demande (téléphone › Plus › Déconnecter, ou TV › Réglages › Réglages depuis le téléphone › Déconnecter).
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
- Limite connue : sur le réseau local, la page n'est pas chiffrée (un certificat HTTPS local provoquerait des alertes dans le navigateur). Quelqu'un qui espionnerait activement ton Wi-Fi pendant l'utilisation pourrait voir le trafic. Le jeton est à usage unique et la session est courte, et c'est sans risque sur un Wi-Fi domestique protégé par mot de passe.

## Permissions Android
- `INTERNET` : photos, vidéos, météo.
- `REQUEST_DELETE_PACKAGES` : proposer la désinstallation d'une appli, que tu confirmes à chaque fois.
- `PACKAGE_USAGE_STATS` (facultative, à accorder par ADB) : historique pour « Reprendre ».
- `WRITE_SECURE_SETTINGS` (facultative, à accorder par ADB) : sert uniquement à choisir Lumen comme économiseur d'écran (`ScreenSaver`) ; aucun autre réglage de la TV n'est touché.
- `READ_TV_LISTINGS` : lire les épisodes en cours (« Reprendre ») et la liste des entrées HDMI, sur l'appareil.

## Code PIN (`Pin`, `PinDialog`)
- Facultatif. Protège l'ouverture des réglages (donc aussi le QR code du téléphone), le masquage et la désinstallation des applis, et les applis que tu verrouilles (y compris depuis « Reprendre »).
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
| `MainActivity` | Accueil, page des applis, gestes, mode veille, superpositions |
| `SettingsPanel`, `GalleryPanel`, `ResumePanel`, `PhonePanel`, `InputsPanel`, `CityPanel` | Un panneau par fonction |
| `Tile`, `Glass`, `Glyph`, `Glow`, `AnimatedScene`, `WeatherFx`, `AmbientView` | Composants visuels |
| `Wall`, `Library`, `WallItem` | Moteur de fonds (images, scènes animées, vidéos) |
| `Online` | Recherche et cache des photos et vidéos NASA / Wikimedia |
| `Net`, `NetPolicy` | Réseau sortant sécurisé |
| `MediaSniff`, `MediaGuard` | Contrôle des fichiers |
| `PhoneServer`, `PhoneAuth`, `HttpRequest`, `QrCode` | Réglages depuis le téléphone |
| `Sky` | Lever / coucher du soleil, phase de lune, saisons (calcul local, sans réseau) |
| `TopShelf`, `WatchNext`, `WatchNextFormat` | « Reprendre » au-dessus des applis : lecture des « en cours » publiés par les applis, affiches contrôlées |
| `Updater`, `UpdatePolicy` | Mise à jour automatique depuis le dépôt GitHub, vérification de l'empreinte et de la signature |
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

`tests/run.sh` lance 180 tests sur la politique réseau, le code PIN, la sauvegarde, les habitudes, les licences, les signatures de fichiers, le parseur HTTP, l'authentification, les catalogues de vidéos (fonds et ambiances), l'option Pexels et les calculs du ciel (comparés aux éphémérides de l'IMCCE, ±3 min). Il vérifie aussi que le QR code est lisible par un vrai décodeur (`zbarimg`).

## Licence
Lumen est un logiciel libre sous licence GNU GPL version 3 ou ultérieure (fichier `LICENSE`, en-tête `SPDX-License-Identifier: GPL-3.0-or-later` dans chaque fichier source). La police Inter reste sous SIL Open Font License 1.1.
- Télécommande du téléphone : liste fermée de touches (flèches, OK, Retour, Accueil, volume, lecture). Hors de Lumen, elle passe par le service d'accessibilité facultatif « Lumen télécommande », désactivé tant que l'utilisateur ne l'active pas : il ne traite aucun événement, appuie seulement sur des touches globales (dont la veille de l'écran) et, quand le téléphone envoie du texte, cherche le champ de saisie de l'appli à l'écran pour y écrire ; rien de ce qu'il voit n'est gardé ni envoyé. Avec ce service actif, le téléphone peut aussi ouvrir une appli quand une autre est à l'écran. Touches et texte en direct ont leur propre limite de débit (12 par seconde, rafale de 30).
- Home Assistant (facultatif) : seule exception à la liste blanche, l'adresse de TON Home Assistant, réglée depuis le téléphone (action protégée par le code PIN s'il existe). En HTTP, l'adresse doit être sur le réseau local, et elle est revérifiée après résolution : le jeton ne part jamais en clair sur Internet. En HTTPS, tout hôte est permis (accès à distance). Aucune redirection n'est suivie. Le jeton reste sur la TV : il n'est jamais renvoyé au téléphone et n'est pas dans la sauvegarde. Actions en liste fermée (allumer/éteindre, scène, script, bouton, volet, lecture) ; serrures et alarmes exclues.

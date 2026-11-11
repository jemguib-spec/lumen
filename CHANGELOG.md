# Nouveautés de Lumen

Chaque version a sa section « ## version ». `tools/publish.sh` publie le texte de la section dans la GitHub Release et dans `dist/latest.json` : c'est ce que la TV affiche dans Réglages › Système › Nouveautés.

## 4.28.1

- Notifications des applis plus discrètes : une petite carte de verre à gauche de l'accueil, sous le nom du fond, au lieu de la grande carte au milieu en haut. Icône, nom de l'appli, titre et deux lignes de texte au plus ; elle glisse depuis la gauche.
- Réglages › Notifications : « Afficher les notifications sur l'accueil ».

## 4.28.0

- Tous les boutons ronds de l'accueil se masquent ou s'affichent, chacun avec son interrupteur, au même endroit : Réglages › Accueil et applis › Boutons de l'accueil. Notifications, Rechercher, Ambiances, Entrées, Maison, Profils, Toutes les applications, Paramètres de la TV, Sons et Réglages ; par profil, repris dans la sauvegarde des réglages.
- La cloche, Entrées et Profils gardent en plus leur condition (une notification qui attend, une TV qui a des entrées, au moins deux profils) ; Maison et Sons restent masqués par défaut.
- Bouton Réglages masqué : les réglages restent accessibles par la touche Menu, ou ▼ puis la recherche « réglages ».
- Les réglages des boutons Maison et Sons quittent leurs anciennes pages pour cette page unique (rien n'est perdu : ils gardent leur état).

## 4.27.0

- Notifications des applis en grand : quand une appli annonce une nouvelle sortie, un nouvel épisode ou un message, une grande carte de verre l'affiche en haut de l'accueil, avec l'icône et le nom de l'appli. Retour l'efface. Elle reste ensuite sous la cloche, un nouveau bouton qui n'apparaît que s'il y a des notifications : OK en ouvre une dans son appli.
- Nouvelle rubrique Réglages › Notifications : activer ou couper, accès aux notifications (écran d'Android, ou commande ADB si la TV le cache), durée à l'écran (6, 10 ou 20 s), couper une appli, dernières notifications, « Essayer ». Les notifications ne sont ni enregistrées ni envoyées ; rien dans un profil enfant ; une appli verrouillée par code ne montre que son nom.
- Nouveau bouton Paramètres de la TV en haut de l'accueil : réseau et Internet, télécommandes et accessoires Bluetooth, son, image, entrées et tous les paramètres, en un appui (code PIN demandé s'il y en a un). La touche de télécommande reliée à « Paramètres de la TV » ouvre aussi ce panneau.
- Le bouton rond sélectionné affiche son nom juste dessous : plus besoin de deviner ce que font ✦ ou l'icône des entrées.
- Le bouton Sons (haut-parleur) est masqué par défaut ; Réglages › Sons › Bouton Sons sur l'accueil le remet (réglable aussi depuis le téléphone).
- Le bouton Entrées n'apparaît que si la TV a des entrées (HDMI, chaînes).
- La date sous l'horloge et dans le mode veille suit la langue de Lumen (« Mercredi 11 novembre ») même si Android est réglé dans une autre langue.
- Réglages : le nom d'une ligne n'est plus coupé par sa valeur ; c'est la valeur qui se raccourcit (« À jour · 4.27.0 »). Un petit chevron en bas des panneaux indique qu'il reste des lignes plus bas.
- Profils : la phrase d'explication s'affiche en casse normale, plus lisible.
- Accueil presque vide (premier lancement) : une ligne explique comment ajouter ses applis aux favoris.

## 4.26.3

- Réglages : les pastilles d'icône devant les rubriques (Vignettes et disposition, Reprendre, Lisibilité, À l'affiche…) s'affichent en entier ; elles étaient coupées sur leur côté gauche.

## 4.26.2

- Arrondis, suite : les anneaux de sélection (À l'affiche, Reprendre, tuiles Maison, vignettes à cocher), le liseré des applis habituelles, le contour du mode déplacement et le filet du verre de toutes les cartes suivent eux aussi la règle : un trait posé au bord prend le rayon de la forme moins sa demi-épaisseur.
- Les pastilles de suggestions de l'accueil et de la Maison sont arrondies en entier, comme les autres pastilles.

## 4.26.1

- Une seule règle pour tous les coins arrondis, partout : quand un élément arrondi est posé dans un autre, son coin suit celui qui l'entoure (rayon extérieur = rayon intérieur + marge). Menus, réglages et leurs pastilles de rubrique, fenêtres de saisie, code PIN, À l'affiche, tuiles Maison, vignettes à cocher, barre de progression des programmes.
- Page du téléphone : même règle pour les listes et tout ce qui touche leur bord (icônes, cases, boutons, menus déroulants, interrupteurs), les onglets, les vignettes et leurs pastilles.
- Les pastilles (puces, notifications, recherche) sont arrondies en entier ; plus aucun rayon n'est choisi au cas par cas.
- Code PIN : les touches occupent toute la largeur de la fenêtre.

## 4.26.0

- Nouveau bouton haut-parleur en haut de l'accueil, à côté de Réglages : un appui coupe les sons de Lumen (le haut-parleur est alors barré), un autre les remet.
- Nouvelle rubrique Réglages › Sons : couper les sons de Lumen, choisir leur jeu et leur volume.
- Une touche de la télécommande sans rôle peut aussi être reliée à « Couper ou remettre les sons ».
- Quatre jeux de sons : Cristal (les sons d'origine), Bois (marimba), Gouttes (gouttes d'eau) et Ouate (très doux, plus grave). ◀ ▶ sur « Jeu de sons » ou « Volume » fait entendre le changement aussitôt ; « Écouter » le rejoue.
- Volume en cinq crans, de « Très bas » à « Très fort ». Seuls les sons de Lumen changent : le volume de la TV, des applis et des vidéos reste le même.
- Réglable aussi depuis le téléphone (Réglages › Accueil), par profil, et repris dans la sauvegarde des réglages. La recherche trouve la page avec « sons », « bip », « volume » ou « muet ».

## 4.25.1

- Mise à jour : Android ne rouvre plus la dernière appli lancée pendant que Lumen se remplace. L'installation passe par l'écran d'installation d'Android, qui reste affiché (« Installation… ») jusqu'au retour de Lumen.
- Si cet écran ne s'ouvre pas ou n'aboutit pas sur ta TV, l'essai suivant reprend l'ancienne méthode : une mise à jour ne peut pas rester bloquée.

## 4.25.0

- Touches de la télécommande sans rôle (Guide, Info, boutons de couleur, chiffres, boutons d'appli…) : quand tu appuies sur l'une d'elles depuis l'accueil, Lumen demande « Veux-tu la relier à une fonction ? ». Au choix : réglages de Lumen, retour, toutes les applis, recherche vocale, Reprendre, À l'affiche, Maison, mode veille, ambiances, paramètres de la TV, ou ouvrir l'appli de ton choix. « Ne rien faire » : Lumen ne redemande plus pour cette touche.
- Réglages › Télécommande : la liste des touches reliées, pour changer leur fonction ou les oublier, et un interrupteur pour ne plus recevoir la proposition.
- Les touches de navigation, de volume et de lecture gardent leur rôle ; celles que la TV garde pour elle (Accueil, boutons Netflix de certaines télécommandes) n'arrivent pas jusqu'à Lumen et ne peuvent pas être reliées. Avec un code PIN, il est demandé avant de relier une touche ; rien n'est proposé dans un profil enfant.

## 4.24.4

- Page Maison : un onglet pour chaque pièce de Home Assistant qui a un appareil pilotable, même si tu n'as choisi aucun appareil de cette pièce sur le téléphone ; la pièce montre alors tout ce qu'elle contient (ses groupes de lumières avant ses ampoules).
- Un groupe de lumières que Home Assistant n'a rangé dans aucune pièce va dans celle de ses ampoules, sinon dans la pièce qui porte son nom.
- Sous le titre, l'état de la maison en une phrase (« 3 lumières allumées », « Toutes les lumières sont éteintes »), tenu à jour.
- Tuiles plus grandes quand il y a peu d'appareils (quatre par ligne), pictogramme dans une pastille ronde qui se remplit de la couleur de la lumière quand elle est allumée.

## 4.24.3

- Page Maison plus lisible : quand Home Assistant a des groupes de lumières (un groupe, une pièce ou une zone Hue), seuls ces groupes sont affichés ; les ampoules une à une sont rangées derrière « Afficher chaque ampoule », en bas de la page. Si tu as choisi tes appareils sur le téléphone, c'est ton choix qui s'affiche, comme avant.
- Page Maison : en défilant, les tuiles et les scènes ne passent plus par-dessus le titre et les onglets des pièces.
- Scènes : les pastilles passent à la ligne selon leur largeur, aucune ne sort plus de l'écran.

## 4.24.2

- Home Assistant en https sur le réseau local avec un certificat auto-signé (ex. https://192.168.1.20:8123) : la liaison fonctionne. Lumen retient ce certificat à la liaison et n'accepte ensuite que lui ; refais la liaison depuis le téléphone (Plus › Home Assistant) pour en profiter.

## 4.24.1

- Fond immersif : avec plusieurs films ou épisodes en cours dans une appli, le fond suit maintenant celui qui est sélectionné dans « Reprendre » (avant, il restait sur le premier).
- ▶ sur la dernière appli d'une ligne ouvre bien « À l'affiche » (et ◀ sur la première, « Reprendre »), même quand les cartes « Reprendre » de l'appli sont affichées au-dessus : la sélection ne part plus dans ces cartes.

## 4.24.0

- Page Maison redessinée, en plein écran : chaque appareil Home Assistant est une tuile de verre avec son pictogramme ; une lumière allumée éclaire sa tuile dans sa vraie couleur et une jauge montre son intensité.
- Régler depuis la TV : OK maintenu sur une lumière, un volet ou un ventilateur, puis ◀ ▶ pour l'intensité, la position ou la vitesse.
- Un onglet par pièce (si tes appareils sont rangés par pièce dans Home Assistant) ; les scènes en pastilles en haut.
- Bouton Maison vivant : le nombre de lumières allumées s'affiche dessus et son verre s'éclaire.
- Scènes sur l'accueil : pastilles de verre, onde lumineuse au lancement, la dernière scène lancée reste marquée.

## 4.23.0

- Nouvel identifiant de l'appli : fr.lumen.accueil (au lieu de com.lumen.launcher), enregistré auprès de Google pour la vérification des développeurs Android. Pour Android, c'est une nouvelle appli : à installer une fois à la main, puis désinstaller l'ancien Lumen (voir INSTALLATION.md, section 5).

## 4.22.0

- Fond immersif : reste une seconde sur une appli qui a un épisode en cours, le paysage laisse place à l'image de cet épisode (adoucie, assombrie), avec son titre en haut à gauche ; le paysage revient dès que tu bouges. Réglages › Accueil et applis › Reprendre › Fond de l'épisode en cours.
- Couleurs accordées au fond : la sélection et les interrupteurs prennent une nuance du fond affiché (blanc chaud à l'aube, bleuté la nuit). Réglages › Accueil et applis › Vignettes et disposition › Couleurs accordées au fond.
- Les applis s'ouvrent plus vite : l'écran « lumen » ne retient plus le lancement que 0,4 s (0,9 s avant).
- Sélection fondue : une vignette ou une ligne de panneau qui prend le focus passe au blanc en douceur (fond, texte, pictogramme, interrupteur) au lieu de basculer d'un coup.
- Verre éclairé d'en haut : arête supérieure lumineuse, flancs discrets, sur les vignettes, les boutons et les panneaux.
- Défilement plus fluide : le verre ne crée plus d'objet à chaque image.

## 4.21.1

- La page du téléphone suit la langue de la TV : anglais complet, en plus du français.

## 4.21.0

- Profil enfant : seules ses applis favorites existent ; quitter le profil, régler, masquer ou désinstaller demandent le code PIN de la maison.
- État de la TV sous l'horloge : pas de réseau, pile de la télécommande faible, mise à jour prête.
- « À l'affiche » sans clé personnelle : clé partagée du projet, acceptée une fois (ta propre clé passe avant).
- Interface en anglais : Réglages › Accueil et applis › Lisibilité › Langue (système, français, English).
- Réglages « À l'affiche » : ma clé, clé partagée, tout désactiver.

## 4.20.1

- Les secrets de la TV (jeton Home Assistant, clés TMDB et Pexels) sont maintenant chiffrés avec une clé de l'Android Keystore ; ceux déjà enregistrés sont chiffrés automatiquement.
- Mise à jour : l'adresse des Releases GitHub refuse les chemins détournés.

## 4.20.0

- Assistant d'installation sur la TV (Réglages › Système › Installation, et au premier démarrage si Lumen n'est pas l'écran d'accueil) : état de l'accueil, commandes à copier avec l'adresse de la TV, QR code vers le guide.
- Recherche vocale : micro de la télécommande (touche Recherche ou Assistant) et bouton micro dans la recherche.
- Lecteur d'écran (TalkBack) : vignettes, boutons et réglages annoncés.
- Mises à jour téléchargées depuis les GitHub Releases ; les nouveautés de chaque version se lisent sur la TV.
- Compilation et tests sur un émulateur Android TV à chaque envoi, avec captures d'écran.
- Réglages redessinés : interrupteurs pour les réglages à deux états, ◀ ▶ pour les choix multiples, pages courtes avec sous-pages (Paysages Lumen, Avancé, Vignettes et disposition, Reprendre, Lisibilité, Diagnostic), phrases d'aide.
- Interface plus homogène : couleurs et durées d'animation unifiées, textes secondaires plus lisibles sur un fond clair, titres de section plus grands, bandeau d'accueil mis en avant.
- États vides qui disent quoi faire (Reprendre, À l'affiche, recherche) ; trois conseils sur les gestes aux premiers démarrages.
- Page du téléphone : thème clair quand le téléphone est en mode clair.
- « À l'affiche » a sa page de réglages : clé masquée, plateformes, sélection et type au départ, historique « Pour toi » effaçable, retrait de la clé.

## 4.19.3

_2026-11-09_ — Photos téléchargées même quand la TV a peu de place, et Lumen le dit.

## 4.19.2

_2026-11-09_ — « Qui regarde ? » s'affiche vraiment au démarrage, pastilles monochromes.

## 4.19.1

_2026-11-09_ — « Qui regarde ? » à chaque démarrage, pastilles bien rondes.

## 4.19.0

_2026-11-09_ — « Qui regarde ? » au démarrage de la TV.

## 4.18.2

_2026-11-09_ — Mise à jour fiable juste après une publication.

## 4.18.1

_2026-11-09_ — Bandeau sous le nom de la photo, mises à jour trouvées plus vite.

## 4.18.0

_2026-11-09_ — Profils par personne, secrets du téléphone chiffrés.

## 4.17.2

_2026-11-09_ — Code fiabilisé et allégé.

## 4.17.1

_2026-11-09_ — Page du téléphone, réglages et fonds, icônes.

## 4.17.0

_2026-11-09_ — Échanges téléphone ↔ TV signés.

## 4.16.1

_2026-11-09_ — Session du téléphone liée à son appareil.

## 4.16.0

_2026-11-09_ — Téléphone accepté sur la TV, accès « télécommande seulement ».

## 4.15.1

_2026-11-09_ — Retour sur Lumen après une mise à jour, en appuyant sur Accueil.

## 4.15.0

_2026-11-09_ — Télécommande instantanée (connexion permanente).

## 4.14.1

_2026-11-09_ — Télécommande du téléphone bien plus réactive.

## 4.14.0

_2026-11-09_ — « À l'affiche » plus riche.

## 4.13.2

_2026-11-09_ — Clés TMDB et Pexels saisissables directement sur la TV.

## 4.13.1

_2026-11-09_ — Filtres d'« À l'affiche » qui s'effacent au bord au lieu d'être coupés.

## 4.13.0

_2026-11-09_ — « À l'affiche » haut de gamme.

## 4.12.1

_2026-11-09_ — « À l'affiche » dans le style de Lumen.

## 4.12.0

_2026-11-09_ — « À l'affiche », films et séries du moment.

## 4.11.3

_2026-11-09_ — Redémarrage soigné.

## 4.11.2

_2026-11-09_ — « lumen » entier sur l'écran d'ouverture d'appli et de mise à jour.

## 4.11.1

_2026-11-09_ — Mise à jour sans passer par l'accueil d'origine de la TV.

## 4.11.0

_2026-11-09_ — Recherche intelligente.

## 4.10.3

_2026-11-09_ — Recherche depuis l'accueil et dans les applis.

## 4.10.2

_2026-11-09_ — Téléchargements visibles, fermer une appli qui bloque.

## 4.10.1

_2026-11-09_ — L'appli se charge derrière l'écran lumen.

## 4.10.0

_2026-11-09_ — Animations, recherche universelle, scènes et mode cinéma Home Assistant.

## 4.9.8

_2026-11-09_ — Ouverture de « Toutes les applications » plus douce.

## 4.9.7

_2026-11-09_ — Désinstallation en arrière-plan après la confirmation d'Android.

## 4.9.6

_2026-11-09_ — Fond calme derrière la désinstallation d'Android.

## 4.9.5

_2026-11-09_ — Petits menus redessinés.

## 4.9.4

_2026-11-09_ — Ouverture d'une appli comme le démarrage de Lumen.

## 4.9.3

_2026-11-09_ — Ambiances distinctes du mode veille.

## 4.9.2

_2026-11-09_ — Navigation vers le haut, stockage maîtrisé, ouverture d'appli plus belle.

## 4.9.1

_2026-11-09_ — APK publié (mise à jour automatique des TV).

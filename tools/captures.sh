#!/bin/bash
# Visite guidée pour le README : ouvre Lumen écran par écran sur une TV ou un émulateur Android TV
# branché en adb et prend une capture de chaque. Lumen doit être déjà installé et l'assistant
# d'installation déjà passé (en CI, tools/smoke.sh tourne juste avant) :
#   tools/captures.sh captures/visite
# Les captures retenues pour le README sont recopiées, réduites, dans docs/ (voir README.md).
OUT=${1:-captures/visite}
PKG=fr.lumen.accueil
mkdir -p "$OUT"
key() { adb shell input keyevent "KEYCODE_$1"; sleep "${2:-0.7}"; }
keys() { local k=$1 n=$2 i; for ((i = 0; i < n; i++)); do key "$k" 0.5; done; sleep 0.5; }
shot() { sleep "${2:-1.5}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "capture : $OUT/$1.png"; }
# Lumen repart de zéro : réglages ouverts sur leur première rubrique, accueil sur la première appli
fresh() {
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/com.lumen.launcher.MainActivity >/dev/null
  sleep "${1:-12}"
}
menu() { fresh; key MENU 2; } # réglages, première rubrique sélectionnée
# Rubriques des réglages, dans l'ordre : Fond d'écran, Accueil et applis, Mode veille, Ambiances,
# Sons, Profils, Réglages depuis le téléphone, Télécommande, Système
rubrique() { menu; keys DPAD_DOWN "$1"; key DPAD_CENTER 2; }
# Boutons ronds de l'accueil, de gauche à droite : Rechercher, Ambiances, Entrées, (Maison),
# (Profils), Toutes les applications, Sons, Réglages
bouton() { fresh; key DPAD_UP 1; keys DPAD_LEFT 8; keys DPAD_RIGHT "$1"; key DPAD_CENTER 3; }

# Interface en français : Réglages › Accueil et applis › Lisibilité › Langue → Français
rubrique 1; keys DPAD_DOWN 6; key DPAD_CENTER 2
key DPAD_CENTER 3 # « Système » → « Français » (la page se reconstruit) ; à ne lancer qu'une fois par TV

fresh 14;              shot 01-accueil
key DPAD_RIGHT;        shot 02-accueil-selection
fresh; key DPAD_DOWN 3; shot 03-applications
bouton 0;              shot 04-recherche 2
bouton 1;              shot 05-ambiances 5
bouton 2;              shot 06-entrees
menu;                  shot 07-reglages
rubrique 0;            shot 08-fond
keys DPAD_DOWN 4; key DPAD_CENTER; shot 09-galerie 12
rubrique 1;            shot 10-accueil-et-applis
keys DPAD_DOWN 4; key DPAD_CENTER; shot 11-vignettes
rubrique 2;            shot 12-veille-reglages
keys DPAD_DOWN 3; key DPAD_CENTER; shot 13-mode-veille 6
rubrique 4;            shot 14-sons
rubrique 5;            shot 15-profils 2
rubrique 6;            shot 16-telephone 3
rubrique 7;            shot 17-telecommande
rubrique 8;            shot 18-systeme

# Soir et nuit : il faut changer l'heure de la TV (émulateur avec adb root seulement)
if adb root >/dev/null 2>&1 && sleep 3 && adb wait-for-device && [ "$(adb shell id -u | tr -d '\r')" = 0 ]; then
  adb shell settings put global auto_time 0 >/dev/null 2>&1
  d=$(adb shell date +%m%d | tr -d '\r'); y=$(adb shell date +%Y | tr -d '\r')
  adb shell date "${d}1945${y}.00" >/dev/null && { fresh 14; shot 19-soir; }
  adb shell date "${d}2340${y}.00" >/dev/null && { fresh 14; shot 20-nuit; }
  adb shell settings put global auto_time 1 >/dev/null 2>&1
else
  echo "pas d'adb root : pas de captures du soir et de la nuit"
fi
adb shell am force-stop $PKG
adb shell am start -n $PKG/com.lumen.launcher.MainActivity >/dev/null
echo "Visite terminée : $(ls "$OUT"/*.png | wc -l) captures dans $OUT/"

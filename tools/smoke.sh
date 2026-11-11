#!/bin/bash
# Parcours de fumée sur une TV ou un émulateur Android TV branché en adb : installe l'APK, ouvre
# Lumen, parcourt l'accueil, la page Applications et les réglages, prend des captures, puis vérifie
# qu'aucun plantage ni blocage (ANR) n'a eu lieu. Utilisé en CI (.github/workflows/verifier.yml)
# et utilisable à la main : tools/smoke.sh build/Lumen.apk captures
set -e
APK=${1:-build/Lumen.apk}
OUT=${2:-captures}
PKG=fr.lumen.accueil
mkdir -p "$OUT"
key() { adb shell input keyevent "KEYCODE_$1"; sleep "${2:-0.8}"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "capture : $OUT/$1.png"; }
alive() { [ -n "$(adb shell pidof $PKG 2>/dev/null | tr -d '\r')" ]; }

adb wait-for-device
adb shell settings put global window_animation_scale 1 >/dev/null 2>&1 || true
adb install -r -g "$APK"
adb logcat -c
adb shell am start -W -n $PKG/com.lumen.launcher.MainActivity >/dev/null
sleep 8
alive || { echo "ÉCHEC : Lumen ne tourne plus après le démarrage"; adb logcat -d | tail -80; exit 1; }
# installation neuve : l'assistant du premier démarrage s'affiche. Lumen écrit chaque étape dans le
# journal (« assistant: etape N », « assistant: fin ») : uiautomator ne sait pas lire un écran
# toujours animé. On ne touche à rien si l'assistant n'est pas à l'écran (TV déjà installée).
etape() { adb logcat -d -s Lumen:I | grep -q "assistant: $1"; }
# tout l'assistant, de zéro : chaque étape est vérifiée dans le journal avant d'avancer
assistant() {
  shot 00-assistant;                                            key DPAD_CENTER 2    # Démarrer de zéro
  etape "etape 1" || return; shot 00-assistant-1-profil;         key DPAD_DOWN; key DPAD_DOWN; key DPAD_CENTER 2   # Continuer (prénom vide)
  etape "etape 2" || return; shot 00-assistant-2-accueil;        for i in 1 2 3 4 5 6 7; do key DPAD_DOWN 0.4; done; key DPAD_CENTER 2
  etape "etape 3" || return; shot 00-assistant-3-boutons;        for i in 1 2 3 4 5 6 7 8 9 10; do key DPAD_DOWN 0.4; done; key DPAD_CENTER 2
  etape "etape 4" || return; shot 00-assistant-4-cles;           key DPAD_CENTER 2    # Continuer (sélectionné d'office)
  etape "etape 5" || return; shot 00-assistant-5-pret;           key DPAD_DOWN; key DPAD_CENTER 2   # Terminer
}
if etape "etape 0"; then
  assistant
  # pas allé au bout (écran différent de prévu) : retour à la première page, puis « Plus tard »
  if ! etape "fin"; then
    echo "assistant : parcours incomplet, « Plus tard »"
    for i in 1 2 3 4 5 6; do key BACK 0.6; done
    key DPAD_DOWN; key DPAD_DOWN; key DPAD_CENTER 2
  fi
  etape "fin" && echo "assistant du premier démarrage parcouru jusqu'au bout" || echo "assistant : refermé avec « Plus tard »"
fi
shot 01-accueil
key DPAD_RIGHT; key DPAD_RIGHT
shot 02-accueil-selection
key DPAD_DOWN 2.5
shot 03-applications
key BACK 1.5
key MENU 2
shot 04-reglages
key DPAD_CENTER 2
shot 05-reglages-fond
key BACK 1; key BACK 1
key DPAD_UP; key DPAD_UP; key DPAD_UP
sleep 1
shot 06-retour-accueil
alive || { echo "ÉCHEC : Lumen s'est arrêté pendant le parcours"; adb logcat -d | tail -80; exit 1; }
adb logcat -d > "$OUT/logcat.txt"
if grep -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/logcat.txt" | grep -q "$PKG"; then
  echo "ÉCHEC : plantage ou ANR dans le journal"; grep -E -A 20 "FATAL EXCEPTION|ANR in $PKG" "$OUT/logcat.txt" | head -80; exit 1
fi
adb shell dumpsys meminfo $PKG | sed -n '1,12p' > "$OUT/memoire.txt" || true
echo "Parcours terminé sans erreur : $(ls "$OUT"/*.png | wc -l) captures dans $OUT/"

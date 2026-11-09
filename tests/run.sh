#!/bin/bash
# Tests JVM des parties critiques (sans Android). Nécessite un JDK ; zbarimg (optionnel) vérifie le QR.
set -e
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
S=src/com/lumen/launcher
javac -encoding UTF-8 -d "$OUT" $S/NetPolicy.java $S/MediaSniff.java $S/HttpRequest.java $S/PhoneAuth.java $S/QrCode.java $S/Sky.java $S/CosyCatalog.java $S/VideoCatalog.java $S/Pexels.java $S/WatchNextFormat.java $S/CommonsThumb.java $S/AppCategory.java $S/UpdatePolicy.java $S/Pin.java $S/Habits.java $S/Backup.java $S/RateLimit.java $S/Greeting.java $S/Messages.java $S/DeviceName.java $S/HomeAssistant.java $S/SettingsIndex.java $S/SmartSearch.java $S/TmdbPolicy.java $S/WsFrames.java $S/Signer.java $S/PhoneGate.java $S/SafeFile.java $S/Seal.java tests/LumenTests.java
RES=$(java -cp "$OUT" com.lumen.launcher.LumenTests)
echo "$RES" | grep -v '^QR_FILE'
QR=$(echo "$RES" | sed -n 's/^QR_FILE=\([^ ]*\) QR_EXPECT=\(.*\)$/\1/p')
EXP=$(echo "$RES" | sed -n 's/^QR_FILE=\([^ ]*\) QR_EXPECT=\(.*\)$/\2/p')
if command -v zbarimg >/dev/null && command -v convert >/dev/null; then
  convert "$QR" "$OUT/qr.png"
  GOT=$(zbarimg -q "$OUT/qr.png" 2>/dev/null | sed 's/^QR-Code://')
  if [ "$GOT" = "$EXP" ]; then echo "QR code relu correctement par zbarimg"; else echo "ÉCHEC : QR code illisible ($GOT)"; exit 1; fi
fi
if command -v node >/dev/null; then
  GOT=$(node tests/phone_crypto.js "cle-du-qr" "jeton secret é")
  if [ "$GOT" = "E1.000102030405060708090a0b0c0d0e0f.81e378f003adf5a9b1dfe53f7b65bb" ]; then echo "Chiffrement de la page du téléphone identique à celui de la TV"; else echo "ÉCHEC : chiffrement de phone.js différent ($GOT)"; exit 1; fi
fi

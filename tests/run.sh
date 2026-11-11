#!/bin/bash
# Tests JVM des parties critiques (sans Android). Nécessite un JDK ; zbarimg (optionnel) vérifie le QR.
set -e
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
S=src/com/lumen/launcher
javac -encoding UTF-8 -d "$OUT" $S/NetPolicy.java $S/MediaSniff.java $S/HttpRequest.java $S/PhoneAuth.java $S/QrCode.java $S/Sky.java $S/CosyCatalog.java $S/VideoCatalog.java $S/Pexels.java $S/WatchNextFormat.java $S/CommonsThumb.java $S/AppCategory.java $S/UpdatePolicy.java $S/Pin.java $S/Habits.java $S/Backup.java $S/RateLimit.java $S/Greeting.java $S/DateLine.java $S/NotifyPolicy.java $S/HomeButtons.java $S/Forecast.java $S/Messages.java $S/DeviceName.java $S/HomeAssistant.java $S/SettingsIndex.java $S/SmartSearch.java $S/TmdbPolicy.java $S/WsFrames.java $S/Signer.java $S/PhoneGate.java $S/SafeFile.java $S/Seal.java $S/Profiles.java $S/InstallPolicy.java $S/VaultCrypto.java $S/L.java $S/Palette.java $S/RemoteKeys.java $S/SoundDesign.java $S/Radius.java $S/TvSync.java $S/SyncCrypto.java $S/SyncState.java $S/SetupFlow.java tests/LumenTests.java
RES=$(java -cp "$OUT" com.lumen.launcher.LumenTests)
echo "$RES" | grep -v '^QR_FILE'
QR=$(echo "$RES" | sed -n 's/^QR_FILE=\([^ ]*\) QR_EXPECT=\(.*\)$/\1/p')
EXP=$(echo "$RES" | sed -n 's/^QR_FILE=\([^ ]*\) QR_EXPECT=\(.*\)$/\2/p')
if command -v zbarimg >/dev/null && command -v convert >/dev/null; then
  convert "$QR" "$OUT/qr.png"
  GOT=$(zbarimg -q "$OUT/qr.png" 2>/dev/null | sed 's/^QR-Code://')
  if [ "$GOT" = "$EXP" ]; then echo "QR code relu correctement par zbarimg"; else echo "ÉCHEC : QR code illisible ($GOT)"; exit 1; fi
fi
# profils de bout en bout (interface SharedPreferences d'Android) : si android.jar est installé
AJ=$(ls /usr/lib/android-sdk/platforms/*/android.jar 2>/dev/null | head -1)
if [ -n "$AJ" ]; then
  mkdir -p "$OUT/p"
  javac -encoding UTF-8 -cp "$AJ" -d "$OUT/p" $S/Profiles.java $S/ProfilePrefs.java tests/ProfilePrefsTest.java
  java -cp "$OUT/p:$AJ" com.lumen.launcher.ProfilePrefsTest
fi
# deux TV reliées dans un même programme : le vrai code de liaison et de synchronisation, Android
# remplacé par des doublures (tests/doublures), vrais sockets en 127.0.0.1 (d'où les trois
# remplacements : adresse de test, boucle locale acceptée comme réseau de la maison)
D="$OUT/deuxtv"
mkdir -p "$D/src" "$D/out"
for f in TvSync HttpRequest Signer SyncCrypto SyncState SyncStore Backup SoundDesign Profiles TmdbPolicy Pexels HomeAssistant RateLimit Palette; do cp $S/$f.java "$D/src/"; done
for f in SyncLink PairLink; do
  sed -e 's/PhoneServer\.localAddress()/FakeNet.localAddress()/g' -e 's/\.isSiteLocalAddress()/.isLoopbackAddress()/g' -e 's/TvSync\.address(ip) == null/false/' $S/$f.java > "$D/src/$f.java"
done
javac -nowarn -encoding UTF-8 -d "$D/out" $(find tests/doublures -name '*.java') "$D"/src/*.java tests/DeuxTV.java 2>&1 | grep -v '^Note:' || true
java -cp "$D/out" com.lumen.launcher.DeuxTV
if command -v python3 >/dev/null; then python3 -I tools/i18n_check.py; python3 -I tools/sync_check.py; fi
if command -v node >/dev/null; then
  GOT=$(node tests/phone_crypto.js "cle-du-qr" "jeton secret é")
  if [ "$GOT" = "E1.000102030405060708090a0b0c0d0e0f.81e378f003adf5a9b1dfe53f7b65bb" ]; then echo "Chiffrement de la page du téléphone identique à celui de la TV"; else echo "ÉCHEC : chiffrement de phone.js différent ($GOT)"; exit 1; fi
fi

#!/bin/bash
set -e
cd "$(dirname "$0")"
SDK=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
[ -f "$SDK" ] || SDK=$(ls /usr/lib/android-sdk/platforms/*/android.jar | head -1)
rm -rf build && mkdir -p build/gen build/obj build/dex
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I "$SDK"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 -bootclasspath "$SDK" -d build/obj \
  $(find src build/gen -name '*.java')
if command -v dalvik-exchange >/dev/null; then DX="dalvik-exchange"; else DX="java -cp ${DX_JAR:?dalvik-exchange absent : indique DX_JAR=chemin/dalvik-dx.jar} com.android.dx.command.Main"; fi
$DX --dex --min-sdk-version=21 --output=build/dex/classes.dex build/obj
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$SDK" -F build/lumen-unsigned.apk
(cd build/dex && aapt add ../lumen-unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/lumen-unsigned.apk build/lumen-aligned.apk
[ -f lumen.keystore ] || keytool -genkeypair -keystore lumen.keystore -storepass lumenpass -keypass lumenpass \
  -alias lumen -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Lumen" >/dev/null 2>&1
apksigner sign --ks lumen.keystore --ks-pass pass:lumenpass --key-pass pass:lumenpass --out build/Lumen.apk build/lumen-aligned.apk
apksigner verify build/Lumen.apk && ls -la build/Lumen.apk

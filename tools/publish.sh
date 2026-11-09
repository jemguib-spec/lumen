#!/bin/bash
# Prépare une mise à jour : compile, puis copie l'APK et sa description dans dist/.
# Il suffit ensuite de pousser dist/ sur la branche main : les TV la récupèrent dans la journée.
# L'APK doit être signé avec la même clé (lumen.keystore) que la version installée.
set -e
cd "$(dirname "$0")/.."
./build.sh >/dev/null
tests/run.sh >/dev/null
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)
mkdir -p dist
cp build/Lumen.apk dist/Lumen.apk
SIZE=$(stat -c %s dist/Lumen.apk)
SHA=$(sha256sum dist/Lumen.apk | cut -d' ' -f1)
printf '{\n  "version": "%s",\n  "size": %s,\n  "sha256": "%s"\n}\n' "$VERSION" "$SIZE" "$SHA" > dist/latest.json
cat dist/latest.json

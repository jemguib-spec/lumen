#!/bin/bash
# Prépare une mise à jour : compile, puis copie l'APK et sa description dans dist/.
# Il suffit ensuite de pousser dist/ sur la branche main : les TV la récupèrent dans la journée.
# L'APK doit être signé avec la même clé (lumen.keystore) que la version installée.
set -e
cd "$(dirname "$0")/.."
# sans la clé d'origine, build.sh en créerait une nouvelle : toutes les TV refuseraient la mise à jour
[ -f lumen.keystore ] || { echo "lumen.keystore introuvable : publie depuis la machine qui a la clé d'origine."; exit 1; }
NEW=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)
OLD=$(sed -n 's/.*"version": "\([^"]*\)".*/\1/p' dist/latest.json 2>/dev/null)
if [ -n "$OLD" ] && [ "$(printf '%s\n%s\n' "$OLD" "$NEW" | sort -V | tail -1)" != "$NEW" -o "$OLD" = "$NEW" ]; then
  echo "Version $NEW pas plus récente que celle déjà publiée ($OLD) : augmente versionName et versionCode."; exit 1
fi
./build.sh >/dev/null
tests/run.sh >/dev/null
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)
mkdir -p dist
# Lumen-<version>.apk : nom neuf à chaque version, jamais servi depuis un vieux cache de GitHub.
# Lumen.apk reste publié pour les TV dont la version ne connaît pas encore ce nom.
rm -f dist/Lumen-*.apk
cp build/Lumen.apk "dist/Lumen-$VERSION.apk"
cp build/Lumen.apk dist/Lumen.apk
SIZE=$(stat -c %s dist/Lumen.apk)
SHA=$(sha256sum dist/Lumen.apk | cut -d' ' -f1)
printf '{\n  "version": "%s",\n  "file": "Lumen-%s.apk",\n  "size": %s,\n  "sha256": "%s"\n}\n' "$VERSION" "$VERSION" "$SIZE" "$SHA" > dist/latest.json
cat dist/latest.json

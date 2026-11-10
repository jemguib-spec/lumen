#!/bin/bash
# Publie une version : compile, teste, crée la GitHub Release (v<version>, avec l'APK et les
# nouveautés du CHANGELOG), puis met à jour dist/latest.json. Il reste à pousser sur main.
#
# Les TV lisent dist/latest.json sur main, puis téléchargent l'APK à l'adresse « url » (fichier de
# la Release). dist/Lumen.apk n'est plus qu'un pont pour les TV encore en 4.19 ou avant, qui ne
# connaissent pas les Releases : --sans-pont l'omet quand toutes les TV sont passées.
#
# Il faut : la clé d'origine (lumen.keystore), la commande gh connectée (gh auth login), et une
# section « ## <version> » dans CHANGELOG.md (ce sont les nouveautés affichées sur la TV).
set -e
cd "$(dirname "$0")/.."
PONT=1
[ "$1" = "--sans-pont" ] && PONT=0
# sans la clé d'origine, build.sh en créerait une nouvelle : toutes les TV refuseraient la mise à jour
[ -f lumen.keystore ] || { echo "lumen.keystore introuvable : publie depuis la machine qui a la clé d'origine."; exit 1; }
command -v gh >/dev/null || { echo "La commande gh (GitHub CLI) est nécessaire pour créer la Release."; exit 1; }
NEW=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)
OLD=$(sed -n 's/.*"version": "\([^"]*\)".*/\1/p' dist/latest.json 2>/dev/null)
if [ -n "$OLD" ] && [ "$(printf '%s\n%s\n' "$OLD" "$NEW" | sort -V | tail -1)" != "$NEW" -o "$OLD" = "$NEW" ]; then
  echo "Version $NEW pas plus récente que celle déjà publiée ($OLD) : augmente versionName et versionCode."; exit 1
fi
NOTES=$(mktemp)
tools/changelog.sh "$NEW" > "$NOTES" || { echo "CHANGELOG.md : ajoute une section « ## $NEW » avec les nouveautés."; exit 1; }
[ -s "$NOTES" ] || { echo "CHANGELOG.md : la section « ## $NEW » est vide."; exit 1; }
./build.sh >/dev/null
tests/run.sh >/dev/null
APK="Lumen-$NEW.apk"
SIZE=$(stat -c %s build/Lumen.apk)
SHA=$(sha256sum build/Lumen.apk | cut -d' ' -f1)
URL="https://github.com/jemguib-spec/lumen/releases/download/v$NEW/$APK"
gh release create "v$NEW" "build/Lumen.apk#$APK" --title "Lumen $NEW" --notes-file "$NOTES"
mkdir -p dist
rm -f dist/Lumen-*.apk
if [ "$PONT" = 1 ]; then cp build/Lumen.apk dist/Lumen.apk; else rm -f dist/Lumen.apk; fi
# notes : texte brut pour l'écran de la TV (JSON échappé par python)
python3 - "$NEW" "$URL" "$SIZE" "$SHA" "$NOTES" > dist/latest.json <<'PY'
import json, sys
v, url, size, sha, notes = sys.argv[1:]
print(json.dumps({"version": v, "url": url, "size": int(size), "sha256": sha,
                  "notes": open(notes, encoding="utf-8").read().strip()}, ensure_ascii=False, indent=2))
PY
rm -f "$NOTES"
cat dist/latest.json
echo "Release v$NEW créée. Reste à pousser dist/ (et CHANGELOG.md) sur main."

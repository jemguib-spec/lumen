#!/bin/bash
# Publie une version : compile, teste, remplit dist/ (APK + latest.json avec les nouveautés du
# CHANGELOG). Il reste à pousser sur main : les TV la récupèrent dans la journée.
#
# Il faut : la clé d'origine (lumen.keystore) et une section « ## <version> » dans CHANGELOG.md
# (c'est le texte des « Nouveautés » affichées sur la TV).
#
# Facultatif : si la commande gh (GitHub CLI) est installée et connectée, l'APK est aussi déposé
# dans une GitHub Release « v<version> », et latest.json pointe dessus (« url ») : le dépôt cesse
# alors de grossir d'un APK par version. Sans gh, tout se passe dans dist/ comme avant
# (dist/Lumen.apk et dist/Lumen-<version>.apk), et les TV ne voient aucune différence.
# --sans-pont : avec gh seulement, ne plus copier dist/Lumen.apk (quand toutes les TV ont dépassé 4.19).
set -e
cd "$(dirname "$0")/.."
PONT=1
[ "$1" = "--sans-pont" ] && PONT=0
# sans la clé d'origine, build.sh en créerait une nouvelle : toutes les TV refuseraient la mise à jour
[ -f lumen.keystore ] || { echo "lumen.keystore introuvable : publie depuis la machine qui a la clé d'origine."; exit 1; }
GH=0
if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then GH=1; else echo "(gh absent ou non connecté : publication dans dist/ seulement, comme avant)"; fi
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
mkdir -p dist
rm -f dist/Lumen-*.apk
URL=""
if [ "$GH" = 1 ]; then
  URL="https://github.com/jemguib-spec/lumen/releases/download/v$NEW/$APK"
  gh release create "v$NEW" "build/Lumen.apk#$APK" --title "Lumen $NEW" --notes-file "$NOTES"
  if [ "$PONT" = 1 ]; then cp build/Lumen.apk dist/Lumen.apk; else rm -f dist/Lumen.apk; fi
else
  # sans Release : l'APK vit dans dist/, sous un nom propre à la version (jamais mis en cache par
  # GitHub) et sous Lumen.apk pour les TV en 4.19 ou avant
  cp build/Lumen.apk "dist/$APK"
  cp build/Lumen.apk dist/Lumen.apk
fi
# notes : texte brut pour l'écran de la TV (JSON échappé par python)
python3 - "$NEW" "$URL" "$SIZE" "$SHA" "$NOTES" "$APK" > dist/latest.json <<'PY'
import json, sys
v, url, size, sha, notes, apk = sys.argv[1:]
j = {"version": v, "size": int(size), "sha256": sha}
if url: j["url"] = url
else: j["file"] = apk
j["notes"] = open(notes, encoding="utf-8").read().strip()
print(json.dumps(j, ensure_ascii=False, indent=2))
PY
rm -f "$NOTES"
cat dist/latest.json
if [ "$GH" = 1 ]; then echo "Release v$NEW créée. Reste à pousser dist/ (et CHANGELOG.md) sur main."; else echo "dist/ prêt. Reste à pousser sur main."; fi

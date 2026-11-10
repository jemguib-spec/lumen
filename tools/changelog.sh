#!/bin/bash
# Affiche la section « ## <version> » de CHANGELOG.md (sans son titre). Code 1 si elle manque.
set -e
cd "$(dirname "$0")/.."
V="$1"
[ -n "$V" ] || { echo "usage : tools/changelog.sh <version>" >&2; exit 2; }
awk -v v="$V" '
  /^## / { if (found) exit; found = ($2 == v); next }
  found { print }
' CHANGELOG.md | sed -e '1{/^$/d}' | awk 'NF || keep { keep = 1; print }' | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}'
grep -q "^## $V\( \|$\)" CHANGELOG.md

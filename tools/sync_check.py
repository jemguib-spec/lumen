#!/usr/bin/env python3
# Vérifie que chaque réglage enregistré par Lumen (Prefs.java) a sa place dans la synchronisation
# entre TV (SyncState.java) : synchronisé, ou propre à la TV. Un réglage ajouté plus tard sans y
# penser ferait échouer les tests au lieu d'être oublié en silence.
import os, re, sys
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = os.path.join(root, 'src', 'com', 'lumen', 'launcher')
prefs = open(os.path.join(src, 'Prefs.java'), encoding='utf-8').read()
state = open(os.path.join(src, 'SyncState.java'), encoding='utf-8').read()
keys = set(re.findall(r'(?:get|put)(?:String|Int|Boolean|Long|Float|StringSet)\("([a-z0-9_]+)"', prefs))
keys |= set(re.findall(r'(?:remove|contains|secret|set)\("([a-z0-9_]+)"', prefs))
def block(name):
    m = re.search(name + r'\s*=\s*Collections\.unmodifiableSet\(new HashSet<>\(Arrays\.asList\((.*?)\)\)\);', state, re.S)
    return set(re.findall(r'"([^"]+)"', m.group(1))) if m else set()
synced, local = block('SYNCED'), block('LOCAL')
synced.add('profiles'); local.add('profile_active')  # Profiles.LIST_KEY / ACTIVE_KEY
prefixes = re.findall(r'"([a-z_]+)"', re.search(r'LOCAL_PREFIXES = \{(.*?)\}', state).group(1))
bad = 0
for k in sorted(keys):
    if k in synced or k in local or any(k.startswith(p) for p in prefixes): continue
    print(f'synchro entre TV : réglage « {k} » ni synchronisé ni propre à la TV (SyncState.java)'); bad += 1
for k in sorted(synced & local):
    print(f'synchro entre TV : « {k} » à la fois synchronisé et propre à la TV'); bad += 1
print(f'synchro entre TV : {len(keys)} réglages, tous classés' if not bad else f'synchro entre TV : {bad} problème(s)')
sys.exit(1 if bad else 0)

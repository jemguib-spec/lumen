#!/usr/bin/env python3
# Vérifie que chaque texte passé à L.t("…") dans le code (et les tableaux de noms affichés) a sa
# traduction dans assets/lang/*.tsv, et qu'aucune ligne du dictionnaire n'est orpheline ou vide.
import glob, re, sys, os
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = os.path.join(root, 'src', 'com', 'lumen', 'launcher')
LIT = re.compile(r'L\.t\("((?:[^"\\]|\\.)*)"\)')
def unescape(raw):
    return raw.encode('utf-8').decode('unicode_escape').encode('latin-1').decode('utf-8') if '\\' in raw else raw
keys = set()
for f in glob.glob(os.path.join(src, '*.java')):
    for m in LIT.finditer(open(f, encoding='utf-8').read()):
        keys.add(unescape(m.group(1)))
# tableaux dont les éléments passent par L.t / L.all à l'affichage
def array(file, name):
    s = open(os.path.join(src, file), encoding='utf-8').read()
    m = re.search(name + r'\s*=\s*\{(.*?)\};', s, re.S)
    return re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1)) if m else []
for file, name in [('Prefs.java', 'SRC_NAMES'), ('Prefs.java', 'VT_NAMES'), ('Prefs.java', 'TEXT_SCALE_NAMES'),
                   ('AppCategory.java', 'NAMES'), ('CosyCatalog.java', 'NAMES'), ('SettingsPanel.java', 'TITLES'), ('Tips.java', 'ALL'), ('HomeAssistant.java', 'DOMAIN_NAMES'),
                   ('SoundDesign.java', 'THEME_NAMES'), ('SoundDesign.java', 'VOLUME_NAMES'),
                   ('HomeButtons.java', 'NAMES'), ('HomeButtons.java', 'WHEN')]:
    keys.update(unescape(x) for x in array(file, name) if x)
idx = open(os.path.join(src, 'SettingsIndex.java'), encoding='utf-8').read()
keys.update(m.group(1) for m in re.finditer(r'new Entry\("([^"]+)",', idx))
m = re.search(r'String F = "([^"]+)", A = "([^"]+)", V = "([^"]+)", AM = "([^"]+)", S = "([^"]+)"', idx)
if m: keys.update(m.groups())
keys.update(['Réglages', 'Home Assistant', 'Films et séries du moment'])
# page du téléphone : t('…') dans phone.js, textes et attributs de phone.html
pjs = open(os.path.join(root, 'assets', 'phone.js'), encoding='utf-8').read()
phone = set()
for m in re.finditer(r"(?<![A-Za-z_.$])t\('((?:[^'\\]|\\.)*)'\)|(?<![A-Za-z_.$])t\(\"((?:[^\"\\]|\\.)*)\"\)", pjs):
    phone.add((m.group(1) or m.group(2) or '').replace("\\'", "'"))
phtml = open(os.path.join(root, 'assets', 'phone.html'), encoding='utf-8').read()
for m in re.finditer(r'>([^<>{}]*[A-Za-zÀ-ÿ][^<>{}]*)<', phtml):
    s_ = m.group(1).strip()
    if s_ and not re.fullmatch(r'[a-z0-9_\-]+', s_) and s_ not in ('Lumen', 'TV', 'OK', 'Home Assistant', 'iPhone de Jem'): phone.add(s_)
for m in re.finditer(r'(?:placeholder|aria-label|title)="([^"]+)"', phtml):
    if m.group(1) not in ('iPhone de Jem', 'OK', 'Volume'): phone.add(m.group(1))
bad = 0
for tsv in glob.glob(os.path.join(root, 'assets', 'lang', '*.tsv')):
    expected = phone if os.path.basename(tsv).startswith('phone-') else keys
    d = {}
    for n, line in enumerate(open(tsv, encoding='utf-8'), 1):
        line = line.rstrip('\n')
        if not line or line.startswith('#'): continue
        if '\t' not in line: print(f'{tsv}:{n}: pas de tabulation'); bad += 1; continue
        k, v = line.split('\t', 1)
        k, v = k.replace('\\n', '\n'), v.replace('\\n', '\n')
        if not v.strip(): print(f'{tsv}:{n}: traduction vide pour « {k} »'); bad += 1
        if k in d: print(f'{tsv}:{n}: doublon « {k} »'); bad += 1
        d[k] = v
    missing = sorted(expected - set(d))
    orphan = sorted(set(d) - expected)
    for k in missing: print(f'{os.path.basename(tsv)} : manque « {k.replace(chr(10), "\\n")} »')
    for k in orphan: print(f'{os.path.basename(tsv)} : orphelin « {k.replace(chr(10), "\\n")} »')
    bad += len(missing) + len(orphan)
    print(f'{os.path.basename(tsv)} : {len(d)} textes, {len(expected)} attendus')
sys.exit(1 if bad else 0)

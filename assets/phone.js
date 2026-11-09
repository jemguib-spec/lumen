"use strict";
// Page de réglages Lumen (servie par la TV sur le réseau local).
// Le jeton du QR code est lu dans le fragment (#…), échangé une seule fois contre une
// session (cookie HttpOnly), puis effacé de l'adresse. Aucune donnée ne sort du réseau local.

const H = {'X-Lumen': '1'};
let S = {apps: [], favorites: [], mine: [], ambiances: [], pexels: false};
const $ = id => document.getElementById(id);
const el = (tag, cls, txt) => { const e = document.createElement(tag); if (cls) e.className = cls; if (txt != null) e.textContent = txt; return e; };
const clear = n => { while (n.firstChild) n.removeChild(n.firstChild); };
const toast = m => { const t = $('toast'); t.textContent = m; t.classList.add('on'); clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove('on'), 1800); };
const label = key => (S.apps.find(a => a.key === key) || {label: key}).label;
const lost = () => { $('sub').textContent = 'Session terminée · rescanne le QR code sur la TV'; };

document.querySelectorAll('.tabs button').forEach(b => b.addEventListener('click', () => {
  document.querySelectorAll('.tabs button').forEach(x => x.classList.toggle('on', x === b));
  document.querySelectorAll('section').forEach(s => s.classList.toggle('on', s.id === b.dataset.t));
}));

async function api(path, opts) {
  const r = await fetch(path, Object.assign({credentials: 'same-origin', cache: 'no-store'}, opts || {}, {headers: Object.assign({}, H, (opts || {}).headers)}));
  if (r.status === 401 || r.status === 403) { lost(); throw new Error('auth'); }
  return r;
}

async function start() {
  const token = location.hash.slice(1);
  history.replaceState(null, '', location.pathname); // le jeton disparaît de l'adresse et de l'historique
  if (token) {
    const r = await fetch('/api/session', {method: 'POST', credentials: 'same-origin', headers: Object.assign({'X-Lumen-Token': token}, H)});
    if (!r.ok) { lost(); return; }
  }
  load();
}

async function load() {
  try {
    const r = await api('/api/state');
    S = await r.json();
    $('sub').textContent = 'Connecté à la TV';
    render();
  } catch (e) { if (e.message !== 'auth') $('sub').textContent = 'TV injoignable'; }
}

function iconImg(key) { const i = el('img'); i.loading = 'lazy'; i.alt = ''; i.src = '/api/icon?key=' + encodeURIComponent(key); return i; }

function render() {
  const fl = $('favList'); clear(fl);
  if (!S.favorites.length) fl.appendChild(el('div', 'empty', 'Aucun favori'));
  S.favorites.forEach((key, i) => {
    const r = el('div', 'row');
    r.appendChild(el('div', 'n', i + 1));
    r.appendChild(iconImg(key));
    r.appendChild(el('div', 'name', label(key)));
    const up = el('button', 'ib', '▲'); up.disabled = i === 0; up.addEventListener('click', () => move(i, -1));
    const dn = el('button', 'ib', '▼'); dn.disabled = i === S.favorites.length - 1; dn.addEventListener('click', () => move(i, 1));
    const rm = el('button', 'ib', '✕'); rm.addEventListener('click', () => { S.favorites.splice(i, 1); save(); });
    r.append(up, dn, rm);
    fl.appendChild(r);
  });
  renderAll();
  const d = $('dest');
  if (d.options.length === 1) (S.ambiances || []).forEach((n, i) => {
    const o = el('option', null, 'Ambiance · ' + n + ' (vidéo)'); o.value = 'cosy:' + i; d.appendChild(o);
  });
  $('pxState').textContent = S.pexels ? 'Pexels activé' : 'Pexels désactivé';
  const g = $('mineGrid'); clear(g);
  if (!S.mine.length) g.appendChild(el('div', 'empty', 'Rien pour l’instant'));
  S.mine.forEach(m => {
    const t = el('div', 'tile');
    const im = el('img'); im.loading = 'lazy'; im.alt = ''; im.src = '/api/thumb?id=' + encodeURIComponent(m.id); t.appendChild(im);
    if (m.video) t.appendChild(el('span', 'v', m.cosy ? 'Ambiance' : 'Vidéo'));
    if (m.cosy) t.appendChild(el('span', 'a', m.title.split(' · ').pop()));
    const x = el('button', 'x', '✕');
    x.addEventListener('click', async () => {
      try { await api('/api/delete?id=' + encodeURIComponent(m.id), {method: 'POST'}); toast('Supprimé'); load(); } catch (e) { }
    });
    t.appendChild(x);
    g.appendChild(t);
  });
}

function renderAll() {
  const q = $('q').value.trim().toLowerCase();
  const al = $('allList'); clear(al);
  const rest = S.apps.filter(a => !S.favorites.includes(a.key) && (!q || a.label.toLowerCase().includes(q)));
  if (!rest.length) al.appendChild(el('div', 'empty', 'Aucune appli'));
  rest.forEach(a => {
    const r = el('div', 'row');
    r.appendChild(iconImg(a.key));
    r.appendChild(el('div', 'name', a.label));
    const add = el('button', 'ib add', '+');
    add.addEventListener('click', () => { S.favorites.push(a.key); save(); toast(a.label + ' ajoutée'); });
    r.appendChild(add);
    al.appendChild(r);
  });
}
$('q').addEventListener('input', renderAll);

function move(i, d) { const f = S.favorites; [f[i], f[i + d]] = [f[i + d], f[i]]; save(); }

async function save() {
  render();
  try {
    const r = await api('/api/favorites', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(S.favorites)});
    if (!r.ok) throw new Error();
  } catch (e) { toast('Échec, réessaie'); load(); }
}

$('file').addEventListener('change', async e => {
  const files = [...e.target.files]; e.target.value = '';
  for (const f of files) await upload(f);
  load();
});

function upload(f) {
  return new Promise(res => {
    const job = el('div', 'job'); const name = el('span', null, f.name); job.appendChild(name);
    const bar = el('div', 'bar'); const fill = el('i'); bar.appendChild(fill); job.appendChild(bar);
    $('jobs').appendChild(job);
    const x = new XMLHttpRequest();
    x.open('POST', '/api/upload');
    x.withCredentials = true;
    x.setRequestHeader('X-Lumen', '1');
    x.setRequestHeader('X-Lumen-Name', encodeURIComponent(f.name.slice(0, 120)));
    x.setRequestHeader('X-Lumen-Target', $('dest').value);
    x.upload.onprogress = ev => { if (ev.lengthComputable) fill.style.width = (ev.loaded * 100 / ev.total) + '%'; };
    x.onload = () => {
      if (x.status === 401 || x.status === 403) lost();
      const ok = x.status === 200;
      name.textContent = (ok ? '✓ ' : '✕ ') + f.name + (ok ? '' : ' · refusé');
      fill.style.width = '100%';
      setTimeout(() => job.remove(), 3000); res();
    };
    x.onerror = () => { name.textContent = '✕ ' + f.name; res(); };
    x.send(f);
  });
}

async function setPexels(key) {
  try {
    const r = await api('/api/pexels', {method: 'POST', headers: {'Content-Type': 'text/plain'}, body: key});
    toast(r.ok ? (key ? 'Clé enregistrée' : 'Clé retirée') : 'Clé non valide');
    $('pxKey').value = '';
    load();
  } catch (e) { }
}
$('pxSave').addEventListener('click', () => { const k = $('pxKey').value.trim(); if (k) setPexels(k); });
$('pxDel').addEventListener('click', () => setPexels(''));

start();

"use strict";
// SPDX-License-Identifier: GPL-3.0-or-later
// Page téléphone de Lumen (servie par la TV sur le réseau local).
// Le jeton du QR code est lu dans le fragment (#…), échangé une seule fois contre une
// session (cookie HttpOnly), puis effacé de l'adresse. Aucune donnée ne sort du réseau local.
// La page n'envoie que des actions prévues (liste fermée côté TV) ; jamais de commande libre.

const H = {'X-Lumen': '1'};
let S = {apps: [], favorites: [], mine: [], ambiances: [], pexels: false, ha: '', tv: {}};
let tab = 'tv', scope = 'cosy', picks = [], pickTimer = null, poll = null, alive = true;
const $ = id => document.getElementById(id);
const el = (tag, cls, txt) => { const e = document.createElement(tag); if (cls) e.className = cls; if (txt != null) e.textContent = txt; return e; };
const clear = n => { while (n.firstChild) n.removeChild(n.firstChild); };
const toast = m => { const t = $('toast'); t.textContent = m; t.classList.add('on'); clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove('on'), 2200); };
const app = key => S.apps.find(a => a.key === key) || {key, label: key};
const TITLES = {tv: 'TV', fav: 'Accueil', vid: 'Vidéos', mine: 'Envois', opt: 'Plus'};

function lost() {
  alive = false;
  clearInterval(poll);
  $('dot').className = '';
  $('sub').textContent = 'Déconnecté · rescanne le QR code sur la TV';
}

// ------------------------------------------------------------------ onglets
document.querySelectorAll('.tabs button').forEach(b => b.addEventListener('click', () => show(b.dataset.t)));
function show(t) {
  tab = t;
  document.querySelectorAll('.tabs button').forEach(x => x.classList.toggle('on', x.dataset.t === t));
  document.querySelectorAll('section').forEach(s => s.classList.toggle('on', s.id === t));
  $('title').textContent = TITLES[t];
  window.scrollTo(0, 0);
  if (t === 'vid') loadPicks();
  if (t === 'opt') { loadLog(); if (S.ha && !haEntities) loadHa(); }
}

// ------------------------------------------------------------------ réseau
async function api(path, opts) {
  if (!alive) throw new Error('auth');
  const r = await fetch(path, Object.assign({credentials: 'same-origin', cache: 'no-store'}, opts || {}, {headers: Object.assign({}, H, (opts || {}).headers)}));
  if (r.status === 401 || r.status === 403) { lost(); throw new Error('auth'); }
  if (r.status === 429) { toast('Doucement…'); throw new Error('slow'); }
  return r;
}

/** Action vers la TV. Gère le code PIN (demandé puis action rejouée) et la TV occupée. */
async function act(path, body, okMsg) {
  try {
    const opts = {method: 'POST'};
    if (body != null) { opts.body = body; opts.headers = {'Content-Type': 'text/plain'}; }
    const r = await api(path, opts);
    const j = await r.json().catch(() => ({ok: r.ok}));
    if (j.pin) { if (await askPin()) return act(path, body, okMsg); return false; }
    if (j.busy) { toast('La TV affiche une autre appli · reviens sur l’accueil Lumen'); return false; }
    if (!j.ok) { toast('Impossible'); return false; }
    if (okMsg) toast(okMsg);
    setTimeout(load, 500);
    return j;
  } catch (e) { return false; }
}

async function start() {
  const token = location.hash.slice(1);
  history.replaceState(null, '', location.pathname); // le jeton disparaît de l'adresse et de l'historique
  if (token) {
    const r = await fetch('/api/session', {method: 'POST', credentials: 'same-origin',
      headers: Object.assign({'X-Lumen-Token': token, 'X-Lumen-Device': encodeURIComponent(savedName())}, H)});
    if (!r.ok) { lost(); return; }
  }
  await load();
  openWs();
  poll = setInterval(() => { if (!document.hidden && (tab === 'tv' || tab === 'opt')) load(); }, 5000);
  // signe de vie : sans lui pendant 45 s, la TV éteint sa pastille « téléphone connecté »
  setInterval(() => { if (alive && !document.hidden) api('/api/ping', {method: 'POST'}).catch(() => {}); }, 15000);
  document.addEventListener('visibilitychange', () => { if (alive && !document.hidden) load(); });
}

async function load() {
  try {
    const r = await api('/api/state');
    S = await r.json();
    render();
  } catch (e) { if (e.message !== 'auth' && e.message !== 'slow') { $('dot').className = ''; $('sub').textContent = 'TV injoignable'; } }
}

// ------------------------------------------------------------------ rendu
function iconImg(key, cls) {
  const i = el('img', cls); i.loading = 'lazy'; i.alt = '';
  i.addEventListener('error', () => { const p = el('div', 'ph', (app(key).label || '?').slice(0, 2)); i.replaceWith(p); });
  i.src = '/api/icon?key=' + encodeURIComponent(key);
  return i;
}

function render() {
  const tv = S.tv || {};
  const d = $('dot'), sub = $('sub');
  if (tv.veille) { d.className = 'on'; sub.textContent = 'Mode veille'; }
  else if (tv.ambiance >= 0) { d.className = 'on'; sub.textContent = 'Ambiance · ' + S.ambiances[tv.ambiance] + (tv.timer ? ' · ' + tv.timer + ' min' : ''); }
  else if (tv.home) { d.className = 'on'; sub.textContent = 'Connecté · accueil Lumen'; }
  else { d.className = 'busy'; sub.textContent = 'Connecté · une appli est ouverte'; }
  renderRemote(); renderShortcuts(); renderLaunch(); renderAmb(); renderScreen(); renderFav(); renderMine(); renderOpt();
}

function renderLaunch() {
  const q = $('appQ').value.trim().toLowerCase();
  const g = $('launchGrid'); clear(g);
  const visible = S.apps.filter(a => !a.hidden);
  const list = q ? visible.filter(a => a.label.toLowerCase().includes(q)).slice(0, 16)
                 : S.favorites.map(app).filter(a => !a.hidden && S.apps.some(x => x.key === a.key));
  if (!list.length) g.appendChild(el('div', 'empty more', q ? 'Aucune appli' : 'Aucun favori'));
  list.forEach(a => {
    const b = el('button', 'app');
    b.appendChild(iconImg(a.key));
    b.appendChild(el('span', null, a.label));
    if (a.locked) b.appendChild(el('i', 'lk', '🔒'));
    b.addEventListener('click', () => act('/api/launch?key=' + encodeURIComponent(a.key), null, a.label));
    g.appendChild(b);
  });
}
$('appQ').addEventListener('input', renderLaunch);

function renderAmb() {
  const tv = S.tv || {};
  const c = $('ambChips'); clear(c);
  (S.ambiances || []).forEach((n, i) => {
    const b = el('button', 'chip' + (tv.ambiance === i ? ' on' : ''), n);
    b.addEventListener('click', () => act('/api/ambiance?kind=' + i));
    c.appendChild(b);
  });
  if (tv.ambiance >= 0) {
    const s = el('button', 'chip stop', 'Arrêter');
    s.addEventListener('click', () => act('/api/ambiance?kind=-1'));
    c.appendChild(s);
  }
  const t = $('timerChips'); clear(t);
  if (tv.ambiance >= 0) (tv.timers || []).forEach(m => {
    const b = el('button', 'chip', m ? m + ' min' : 'Sans minuterie');
    if (m === (tv.timerSet || 0)) b.classList.add('on');
    b.addEventListener('click', () => act('/api/timer?min=' + m, null, m ? 'Minuterie : ' + m + ' min' : 'Minuterie désactivée'));
    t.appendChild(b);
  });
}

// ---- Télécommande : liste fermée de touches, la TV décide de ce qu'elle en fait
// touches mises en file : aucun appui n'est perdu, et elles partent l'une après l'autre sans attendre
const keyQueue = [];
let keyBusy = false;
const NEED_SERVICE = 'Active « Lumen télécommande » sur la TV : Paramètres › Accessibilité';
// ---- Connexion permanente avec la TV (WebSocket) : les appuis partent instantanément, sans
// attendre la réponse du précédent. Si elle n'est pas disponible, on repasse par des requêtes.
let ws = null, wsTry = 0;
function openWs() {
  if (!alive || ws) return;
  try { ws = new WebSocket('ws://' + location.host + '/ws'); } catch (e) { ws = null; return; }
  ws.onopen = () => { wsTry = 0; };
  ws.onmessage = e => {
    if (e.data === 'p') return;
    let j; try { j = JSON.parse(e.data); } catch (x) { return; }
    if (j.for === 'k') keyError(j);
    else if (j.for === 't' && !j.ok) toast(j.nofield ? 'Ouvre d’abord la recherche de l’appli sur la TV' : j.service === false ? NEED_SERVICE : 'Impossible');
  };
  ws.onclose = () => { ws = null; if (alive) setTimeout(openWs, Math.min(5000, 400 * (++wsTry))); };
  ws.onerror = () => { try { ws.close(); } catch (x) { } };
}
setInterval(() => { if (ws && ws.readyState === 1 && !document.hidden) ws.send('p'); }, 20000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) openWs(); });
const wsReady = () => ws && ws.readyState === 1;
function keyError(j) {
  toast(j.sleep === false && j.service ? 'Veille : Android 9 minimum sur la TV'
    : j.service && !j.dpad ? 'Flèches hors de Lumen : Android 13 minimum sur la TV' : NEED_SERVICE);
}

function press(k, quiet) {
  if (navigator.vibrate) navigator.vibrate(quiet ? 4 : 8);
  if (wsReady()) { ws.send('k ' + k); return; } // instantané
  // en défilement continu on ne garde que quelques appuis d'avance (sinon ça continuerait après)
  if (quiet && keyQueue.length >= 2) return;
  if (keyQueue.length >= 8) return;
  keyQueue.push(k);
  pumpKeys();
}
async function pumpKeys() {
  if (keyBusy) return;
  keyBusy = true;
  while (keyQueue.length) {
    const k = keyQueue.shift();
    try {
      const r = await api('/api/key?k=' + k, {method: 'POST', keepalive: true});
      const j = await r.json().catch(() => ({ok: r.ok}));
      if (!j.ok) { keyQueue.length = 0; keyError(j); }
    } catch (e) { keyQueue.length = 0; }
  }
  keyBusy = false;
}
// boutons hors du pavé ; ceux du pavé seulement au clavier (detail 0), le doigt passe par les gestes
document.querySelectorAll('#remote [data-k]').forEach(b => b.addEventListener('click', e => {
  if (b.closest('.pad') && e.detail !== 0) return;
  press(b.dataset.k);
}));

// ---- Pavé : glisser pour se déplacer, toucher pour OK, garder le doigt pour défiler vite
const pad = document.querySelector('.pad');
const STEP = 28; // px de glissement par case
let g = null;
function stopRepeat() { if (!g) return; clearTimeout(g.hold); clearInterval(g.rep); g.hold = g.rep = null; }
function armRepeat(k) {
  stopRepeat();
  g.hold = setTimeout(() => { g.repeated = true; g.rep = setInterval(() => press(k, true), 130); }, 420);
}
pad.addEventListener('pointerdown', e => {
  e.preventDefault();
  try { pad.setPointerCapture(e.pointerId); } catch (x) { }
  const b = e.target.closest('[data-k]');
  g = {x: e.clientX, y: e.clientY, moved: false, repeated: false, k: b ? b.dataset.k : 'ok', hold: null, rep: null};
  if (g.k !== 'ok') armRepeat(g.k); // doigt gardé sur une flèche : défilement
});
pad.addEventListener('pointermove', e => {
  if (!g) return;
  const dx = e.clientX - g.x, dy = e.clientY - g.y;
  if (Math.max(Math.abs(dx), Math.abs(dy)) < STEP) return;
  const k = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up');
  g.x = e.clientX; g.y = e.clientY; g.moved = true;
  pad.classList.add('glide');
  press(k, true);
  armRepeat(k); // doigt immobile au bout du geste : la sélection continue d'avancer
});
function endGesture() {
  if (!g) return;
  stopRepeat();
  pad.classList.remove('glide');
  if (!g.moved && !g.repeated) press(g.k);
  g = null;
}
pad.addEventListener('pointerup', endGesture);
pad.addEventListener('pointercancel', () => { stopRepeat(); pad.classList.remove('glide'); g = null; });

// ---- Veille de la TV : deux touches pour éviter un appui par erreur
$('sleepBtn').addEventListener('click', () => {
  const b = $('sleepBtn');
  if (!b.classList.contains('arm')) {
    b.classList.add('arm'); toast('Touche encore pour éteindre l’écran de la TV');
    clearTimeout(b._t); b._t = setTimeout(() => b.classList.remove('arm'), 3000);
    return;
  }
  b.classList.remove('arm');
  press('sleep');
});

// ---- Raccourcis : les trois premiers favoris
function renderShortcuts() {
  const c = $('shortcuts'); clear(c);
  S.favorites.map(app).filter(a => !a.hidden && S.apps.some(x => x.key === a.key)).slice(0, 3).forEach(a => {
    const b = el('button', 'sc');
    b.appendChild(iconImg(a.key));
    b.appendChild(el('span', null, a.label));
    b.addEventListener('click', () => { if (navigator.vibrate) navigator.vibrate(8); act('/api/launch?key=' + encodeURIComponent(a.key), null, a.label); });
    c.appendChild(b);
  });
}

function renderRemote() {
  const tv = S.tv || {}, h = $('remoteHint');
  let msg = '';
  if (!tv.home && !tv.remote) msg = 'Pour piloter les autres applis : sur la TV, Réglages › Réglages depuis le téléphone › Télécommande dans les autres applis.';
  else if (!tv.home && !tv.dpadOutside) msg = 'Dans les autres applis : Retour, Accueil, volume, lecture, texte et raccourcis (flèches à partir d’Android 13).';
  h.textContent = msg; h.hidden = !msg;
}

function renderScreen() {
  const tv = S.tv || {};
  $('veilleBtn').classList.toggle('on', !!tv.veille);
  $('veilleState').textContent = tv.veille ? 'Activé · toucher pour quitter' : 'Désactivé';
  const sel = $('srcSel');
  if (sel.options.length !== (tv.sources || []).length) {
    clear(sel);
    (tv.sources || []).forEach((n, i) => { const o = el('option', null, n); o.value = i; sel.appendChild(o); });
  }
  if (document.activeElement !== sel) sel.value = tv.source;
  $('srcName').textContent = (tv.sources || [])[tv.source] || '';
}
$('veilleBtn').addEventListener('click', () => act('/api/veille?on=' + (S.tv.veille ? 0 : 1)));
$('restartBtn').addEventListener('click', async () => {
  const b = $('restartBtn');
  if (!b.classList.contains('arm')) { b.classList.add('arm'); toast('Touche encore pour redémarrer Lumen'); setTimeout(() => b.classList.remove('arm'), 3000); return; }
  b.classList.remove('arm');
  try { await api('/api/restart', {method: 'POST'}); toast('Lumen redémarre… reconnecte-toi avec le QR code ensuite'); } catch (e) { }
});
$('closeAppBtn').addEventListener('click', async () => {
  try {
    const r = await api('/api/closeapp', {method: 'POST'});
    const j = await r.json();
    toast(j.ok ? (j.name + ' : fermeture…') : 'Aucune appli ouverte depuis Lumen');
  } catch (e) { }
});
$('homeBtn').addEventListener('click', () => press('home'));
$('nextBtn').addEventListener('click', () => act('/api/next', null, 'Fond suivant'));
$('srcSel').addEventListener('change', e => act('/api/source?n=' + e.target.value, null, 'Fond : ' + e.target.selectedOptions[0].textContent));
// ---- Texte : écrit en direct dans la recherche à l'écran (Lumen ou autre appli), Entrée valide
let typeTimer = null, typeSent = '';
// texte en direct : une seule requête à la fois ; pendant qu'elle part, on garde seulement la dernière
// version tapée (jamais d'ancienne frappe qui arriverait après la nouvelle)
let textBusy = false, textNext = null;
async function sendLive(t) {
  if (wsReady()) { ws.send('t0 ' + t); return; } // instantané, toujours dans l'ordre
  if (textBusy) { textNext = t; return; }
  textBusy = true;
  await sendText(t, false);
  textBusy = false;
  if (textNext !== null) { const n = textNext; textNext = null; sendLive(n); }
}
async function sendText(t, enter) {
  try {
    const r = await api('/api/text?live=1' + (enter ? '&enter=1' : ''), {method: 'POST', body: t, headers: {'Content-Type': 'text/plain'}});
    const j = await r.json().catch(() => ({ok: r.ok}));
    if (!j.ok) toast(j.nofield ? 'Ouvre d’abord la recherche de l’appli sur la TV' : j.service === false ? NEED_SERVICE : 'Impossible');
    return j.ok;
  } catch (e) { return false; }
}
$('typeText').addEventListener('input', () => {
  clearTimeout(typeTimer);
  typeTimer = setTimeout(() => {
    const t = $('typeText').value.trim();
    if (t === typeSent) return;
    typeSent = t; sendLive(t);
  }, wsReady() ? 60 : 160);
});
$('typeForm').addEventListener('submit', async e => {
  e.preventDefault();
  clearTimeout(typeTimer);
  const t = $('typeText').value.trim();
  if (!t) return;
  typeSent = t;
  if (await sendText(t, true)) { toast('Recherche envoyée'); $('typeText').value = ''; typeSent = ''; $('typeText').blur(); }
});

// ---- Accueil (favoris)
function renderFav() {
  const fl = $('favList'); clear(fl);
  if (!S.favorites.length) fl.appendChild(el('div', 'empty', 'Aucun favori'));
  const pin = S.tv && S.tv.pin;
  S.favorites.forEach((key, i) => {
    const a = app(key);
    const r = el('div', 'row');
    r.appendChild(iconImg(key));
    r.appendChild(el('div', 'name', a.label));
    if (pin) {
      const lk = el('button', 'ib' + (a.locked ? ' on' : ''), '🔒');
      lk.title = a.locked ? 'Verrouillée' : 'Verrouiller';
      lk.addEventListener('click', () => act('/api/lock?key=' + encodeURIComponent(key) + '&on=' + (a.locked ? 0 : 1), null, a.locked ? a.label + ' déverrouillée' : a.label + ' verrouillée'));
      r.appendChild(lk);
    }
    const up = el('button', 'ib', '↑'); up.disabled = i === 0; up.addEventListener('click', () => move(i, -1));
    const rm = el('button', 'ib', '✕'); rm.addEventListener('click', () => { S.favorites.splice(i, 1); save(); });
    r.append(up, rm);
    fl.appendChild(r);
  });
  renderAll();
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
  renderFav(); renderLaunch();
  try {
    const r = await api('/api/favorites', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(S.favorites)});
    if (!r.ok) throw new Error();
  } catch (e) { if (e.message !== 'auth') { toast('Échec, réessaie'); load(); } }
}

// ---- Vidéos (catalogue fermé, aperçus)
document.querySelectorAll('#scopeSeg button').forEach(b => b.addEventListener('click', () => {
  scope = b.dataset.s;
  document.querySelectorAll('#scopeSeg button').forEach(x => x.classList.toggle('on', x === b));
  clear($('picks'));
  loadPicks();
}));

async function loadPicks() {
  clearTimeout(pickTimer);
  try {
    const r = await api('/api/picks?scope=' + scope);
    picks = await r.json();
    renderPicks();
    // aperçus encore en préparation sur la TV : on repasse dans quelques secondes
    if (tab === 'vid' && picks.some(p => !p.img)) pickTimer = setTimeout(loadPicks, 5000);
  } catch (e) { }
}

const dur = s => s >= 60 ? Math.floor(s / 60) + ' min' + (s % 60 ? ' ' + String(s % 60).padStart(2, '0') : '') : s + ' s';

function renderPicks() {
  const box = $('picks');
  const n = picks.filter(p => p.ok).length;
  $('vidNote').textContent = picks.length ? n + (n > 1 ? ' vidéos gardées' : ' vidéo gardée') + ' · la décocher l’efface de la TV' : 'Chargement du catalogue…';
  const groups = [];
  picks.forEach(p => { let g = groups.find(x => x.name === p.group); if (!g) groups.push(g = {name: p.group, items: []}); g.items.push(p); });
  // mise à jour sur place : les images déjà affichées ne clignotent pas
  if (box.childElementCount && box.dataset.sig === sig()) { picks.forEach(updatePick); return; }
  clear(box);
  box.dataset.sig = sig();
  groups.forEach(g => {
    box.appendChild(el('h2', null, g.name || 'Vidéos'));
    const grid = el('div', 'grid');
    g.items.forEach(p => grid.appendChild(pickCell(p)));
    box.appendChild(grid);
  });
}
const sig = () => scope + picks.map(p => p.key).join('|');
const cellOf = new Map();

function pickCell(p) {
  const b = el('button', 'pick' + (p.ok ? ' on' : ''));
  const th = el('div', 'thumb');
  const im = el('img'); im.alt = ''; th.appendChild(im);
  th.appendChild(el('span', 'ring', p.ok ? '✓' : ''));
  th.appendChild(el('span', 'd', dur(p.dur) + (p.hd ? ' · ' + p.hd : '')));
  b.appendChild(th);
  b.appendChild(el('span', 't', p.title));
  b.addEventListener('click', async () => {
    const on = !b.classList.contains('on');
    setPick(b, on);
    const ok = await act('/api/pick?scope=' + scope + '&key=' + encodeURIComponent(p.key) + '&on=' + (on ? 1 : 0));
    if (!ok) setPick(b, !on); else { p.ok = on; renderPicksNote(); }
  });
  cellOf.set(p.key, b);
  updatePick(p);
  return b;
}
function setPick(b, on) { b.classList.toggle('on', on); b.querySelector('.ring').textContent = on ? '✓' : ''; }
function renderPicksNote() { const n = picks.filter(p => p.ok).length; $('vidNote').textContent = n + (n > 1 ? ' vidéos gardées' : ' vidéo gardée') + ' · la décocher l’efface de la TV'; }
function updatePick(p) {
  const b = cellOf.get(p.key); if (!b) return;
  const im = b.querySelector('img');
  if (p.img && !im.src) {
    im.addEventListener('load', () => im.classList.add('ok'), {once: true});
    im.src = '/api/pickthumb?scope=' + scope + '&key=' + encodeURIComponent(p.key);
  }
}

// ---- Envois
function renderMine() {
  const d = $('dest');
  if (d.options.length === 1) (S.ambiances || []).forEach((n, i) => {
    const o = el('option', null, 'Ambiance · ' + n + ' (vidéo)'); o.value = 'cosy:' + i; d.appendChild(o);
  });
  const g = $('mineGrid'); clear(g);
  if (!S.mine.length) g.appendChild(el('div', 'empty', 'Rien pour l’instant'));
  S.mine.forEach(m => {
    const t = el('div', 'tile');
    const im = el('img'); im.loading = 'lazy'; im.alt = ''; im.src = '/api/thumb?id=' + encodeURIComponent(m.id); t.appendChild(im);
    if (m.video) t.appendChild(el('span', 'v', m.cosy ? 'Ambiance' : 'Vidéo'));
    if (m.cosy) t.appendChild(el('span', 'a', m.title.split(' · ').pop()));
    const x = el('button', 'x', '✕');
    x.addEventListener('click', () => act('/api/delete?id=' + encodeURIComponent(m.id), null, 'Supprimé'));
    t.appendChild(x);
    g.appendChild(t);
  });
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

// ---- Home Assistant : adresse et jeton (jeton jamais relu par la page), appareils affichés sur la TV
let haEntities = null, haLoading = false;
$('haForm').addEventListener('submit', async e => {
  e.preventDefault();
  const url = $('haUrl').value.trim(), token = $('haToken').value.trim();
  if (!url) { toast('Adresse de Home Assistant ?'); return; }
  toast('Connexion à Home Assistant…');
  try {
    const r = await api('/api/ha', {method: 'POST', body: JSON.stringify({url, token}), headers: {'Content-Type': 'text/plain'}});
    const j = await r.json().catch(() => ({ok: false}));
    if (j.pin) { if (await askPin()) $('haForm').requestSubmit(); return; }
    if (!j.ok) { toast(j.error || 'Impossible'); return; }
    $('haToken').value = ''; toast('Home Assistant relié'); haEntities = null; await load(); loadHa();
  } catch (x) { }
});
$('haDel').addEventListener('click', async () => {
  if (await act('/api/ha', '', 'Home Assistant délié')) { haEntities = null; $('haUrl').value = ''; }
});
$('haQ').addEventListener('input', renderHa);
async function loadHa() {
  if (!S.ha || haLoading) return;
  haLoading = true;
  try {
    const r = await api('/api/ha/entities');
    const j = await r.json();
    if (j.ok) haEntities = j.entities; else { haEntities = []; toast(j.error || 'Home Assistant injoignable'); }
  } catch (e) { } finally { haLoading = false; }
  renderHa();
}
function renderHa() {
  const on = !!S.ha;
  $('haLinked').hidden = !on; $('haBox').hidden = !on || !haEntities;
  $('haState').textContent = on ? 'Relié · ' + S.ha.replace(/^https?:\/\//, '') : '';
  if (on && document.activeElement !== $('haUrl') && !$('haUrl').value) $('haUrl').value = S.ha.replace(/^https?:\/\//, '');
  $('haToken').placeholder = on ? 'Nouveau jeton (facultatif)' : 'Jeton d’accès longue durée';
  if (!on || !haEntities) return;
  const q = $('haQ').value.trim().toLowerCase();
  const l = $('haList'); clear(l);
  let grp = null;
  const list = haEntities.filter(x => !q || x.name.toLowerCase().includes(q));
  if (!list.length) l.appendChild(el('div', 'empty', 'Aucun appareil'));
  list.forEach(x => {
    if (x.group !== grp) { grp = x.group; l.appendChild(el('div', 'grp', grp)); }
    const r = el('div', 'row ha' + (x.pinned ? ' on' : ''));
    r.appendChild(el('span', 'ck'));
    r.appendChild(el('div', 'name', x.name));
    r.appendChild(el('span', 'st', x.state));
    if (/^(scene|script)\./.test(x.id)) {
      const hm = el('button', 'hm' + (x.home ? ' on' : ''), '✦ Accueil');
      hm.addEventListener('click', async ev => {
        ev.stopPropagation();
        x.home = !x.home; hm.classList.toggle('on', x.home);
        try { await api('/api/ha/home?id=' + encodeURIComponent(x.id) + '&name=' + encodeURIComponent(x.name.slice(0, 32)) + '&on=' + (x.home ? 1 : 0), {method: 'POST'}); toast(x.home ? x.name + ' sur l’accueil' : x.name + ' retirée de l’accueil'); }
        catch (e) { x.home = !x.home; hm.classList.toggle('on', x.home); }
      });
      r.appendChild(hm);
    }
    r.addEventListener('click', async () => {
      x.pinned = !x.pinned; r.classList.toggle('on', x.pinned);
      try { await api('/api/ha/pin?id=' + encodeURIComponent(x.id) + '&on=' + (x.pinned ? 1 : 0), {method: 'POST'}); }
      catch (e) { x.pinned = !x.pinned; r.classList.toggle('on', x.pinned); }
    });
    l.appendChild(r);
  });
  renderCine();
}

// ---- Stockage de la TV
document.querySelectorAll('[data-clear]').forEach(b => b.addEventListener('click', () => {
  if (!b.classList.contains('arm')) {
    document.querySelectorAll('[data-clear].arm').forEach(x => x.classList.remove('arm'));
    b.classList.add('arm'); toast('Touche encore pour confirmer');
    clearTimeout(b._t); b._t = setTimeout(() => b.classList.remove('arm'), 3000);
    return;
  }
  b.classList.remove('arm');
  act('/api/storage/clear?what=' + b.dataset.clear, null, 'Place libérée');
}));
function renderStorage() {
  const s = S.storage; if (!s) return;
  $('stFree').textContent = 'Libre sur la TV : ' + s.free + '. Lumen garde toujours 2 Go libres et réduit ses vidéos si la place manque.';
  $('stPhotos').textContent = s.photos; $('stVideos').textContent = s.videos; $('stCosy').textContent = s.cosy; $('stMine').textContent = s.mine;
}

// ---- Cinéma : scènes au début et en pause d'un film, applis concernées
function cineState() { return Object.assign({film: '', pause: '', onOpen: false, apps: []}, S.cinema || {}); }
async function saveCine(c, msg) {
  S.cinema = c;
  try { await api('/api/ha/cinema', {method: 'POST', body: JSON.stringify(c), headers: {'Content-Type': 'text/plain'}}); if (msg) toast(msg); } catch (e) { }
  renderCine();
}
function fillSel(sel, value) {
  const scenes = (haEntities || []).filter(x => /^(scene|script)\./.test(x.id));
  clear(sel);
  const none = el('option', null, 'Aucune'); none.value = ''; sel.appendChild(none);
  scenes.forEach(x => { const o = el('option', null, x.name); o.value = x.id; sel.appendChild(o); });
  sel.value = value || '';
}
function renderCine() {
  if (!haEntities) return;
  const c = cineState();
  if (document.activeElement !== $('cineFilm')) fillSel($('cineFilm'), c.film);
  if (document.activeElement !== $('cinePause')) fillSel($('cinePause'), c.pause);
  $('cineOpen').classList.toggle('on', !!c.onOpen);
  const l = $('cineApps'); clear(l);
  const pkgs = new Set(c.apps);
  S.apps.filter(a => a.pkg && (pkgs.has(a.pkg) || S.favorites.includes(a.key))).forEach(a => {
    const r = el('div', 'row ha' + (pkgs.has(a.pkg) ? ' on' : ''));
    r.appendChild(el('span', 'ck'));
    r.appendChild(el('div', 'name', a.label));
    r.addEventListener('click', () => {
      const cc = cineState(), s = new Set(cc.apps);
      if (s.has(a.pkg)) s.delete(a.pkg); else s.add(a.pkg);
      cc.apps = [...s]; saveCine(cc);
    });
    l.appendChild(r);
  });
}
$('cineFilm').addEventListener('change', e => { const c = cineState(); c.film = e.target.value; saveCine(c, c.film ? 'Scène « film » enregistrée' : 'Mode cinéma désactivé'); });
$('cinePause').addEventListener('change', e => { const c = cineState(); c.pause = e.target.value; saveCine(c, 'Scène « pause » enregistrée'); });
$('cineOpen').addEventListener('click', () => { const c = cineState(); c.onOpen = !c.onOpen; saveCine(c); });

// ---- Nom de ce téléphone (affiché sur la TV : « Contrôlée par … »), gardé sur ce téléphone seulement
function savedName() { try { return localStorage.getItem('lumen-name') || ''; } catch (e) { return ''; } }
$('nameForm').addEventListener('submit', async e => {
  e.preventDefault();
  const n = $('nameIn').value.trim().slice(0, 32);
  if (!n) return;
  try { localStorage.setItem('lumen-name', n); } catch (x) { }
  if (await act('/api/name', n, 'Nom enregistré')) $('nameIn').blur();
});

// ---- Plus
function renderOpt() {
  renderHa();
  renderStorage();
  if (tab === 'opt' && S.ha && !haEntities) loadHa();
  if (document.activeElement !== $('nameIn')) $('nameIn').value = savedName() || (S.tv || {}).device || '';
  const tv = S.tv || {};
  $('pinBox').hidden = !tv.pin;
  $('pinState').textContent = tv.unlocked ? 'Déverrouillé pour 5 min' : 'Déverrouiller les actions protégées';
  $('pxState').textContent = S.pexels ? 'Pexels activé' : 'Pexels désactivé';
  $('tmState').textContent = S.tmdb ? 'Films du moment activés' : 'Films du moment désactivés';
}
$('pinOpen').addEventListener('click', () => askPin());
$('pxSave').addEventListener('click', async () => {
  const k = $('pxKey').value.trim(); if (!k) return;
  if (await act('/api/pexels', k, 'Clé enregistrée')) $('pxKey').value = '';
});
$('tmSave').addEventListener('click', async () => {
  const k = $('tmKey').value.trim();
  if (!k) return;
  try {
    const r = await api('/api/tmdb', {method: 'POST', body: k, headers: {'Content-Type': 'text/plain'}});
    const j = await r.json();
    if (j.pin) { if (await askPin()) $('tmSave').click(); return; }
    if (!j.ok) { toast(j.error || 'Clé refusée'); return; }
    $('tmKey').value = ''; toast('Clé enregistrée'); load();
  } catch (e) { }
});
$('tmDel').addEventListener('click', () => act('/api/tmdb', '', 'Clé retirée'));
$('pxDel').addEventListener('click', () => act('/api/pexels', '', 'Clé retirée'));

$('bkSave').addEventListener('click', async () => {
  try {
    const r = await api('/api/backup');
    const blob = new Blob([await r.text()], {type: 'application/json'});
    const a = el('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'lumen-reglages-' + new Date().toISOString().slice(0, 10) + '.json';
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
    toast('Sauvegarde téléchargée');
  } catch (e) { }
});
$('bkLoad').addEventListener('click', () => $('bkFile').click());
$('bkFile').addEventListener('change', async () => {
  const f = $('bkFile').files[0];
  $('bkFile').value = '';
  if (!f) return;
  if (f.size > 256 * 1024) { toast('Fichier trop gros'); return; }
  const j = await act('/api/restore', await f.text());
  if (j) toast(j.count + ' réglages restaurés sur la TV');
});

async function loadLog() {
  try { const r = await api('/api/log'); const t = await r.text(); $('log').textContent = t.trim() || 'Aucune erreur'; } catch (e) { }
}
$('logClear').addEventListener('click', async () => { if (await act('/api/log/clear', null, 'Journal effacé')) loadLog(); });
$('bye').addEventListener('click', async () => {
  try { await api('/api/bye', {method: 'POST'}); } catch (e) { }
  lost();
  $('sub').textContent = 'Déconnecté';
});

// ------------------------------------------------------------------ code PIN
let pinResolve = null;
function askPin() {
  return new Promise(res => {
    pinResolve = res;
    $('pinMsg').textContent = 'Nécessaire pour cette action.';
    $('pinIn').value = '';
    $('pinSheet').hidden = false;
    setTimeout(() => $('pinIn').focus(), 50);
  });
}
function closePin(ok) { $('pinSheet').hidden = true; if (pinResolve) { pinResolve(ok); pinResolve = null; } }
$('pinCancel').addEventListener('click', () => closePin(false));
$('pinSheet').addEventListener('click', e => { if (e.target === $('pinSheet')) closePin(false); });
$('pinForm').addEventListener('submit', async e => {
  e.preventDefault();
  const v = $('pinIn').value.trim();
  if (!/^[0-9]{4}$/.test(v)) { $('pinMsg').textContent = '4 chiffres'; return; }
  try {
    const r = await api('/api/pin', {method: 'POST', headers: {'Content-Type': 'text/plain'}, body: v});
    const j = await r.json();
    if (j.ok) { closePin(true); load(); return; }
    $('pinIn').value = '';
    $('pinMsg').textContent = j.wait ? 'Code incorrect · réessaie dans ' + (j.wait >= 60 ? Math.ceil(j.wait / 60) + ' min' : j.wait + ' s') : 'Code incorrect';
  } catch (e) { closePin(false); }
});

start();

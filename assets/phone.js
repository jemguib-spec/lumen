"use strict";
// SPDX-License-Identifier: GPL-3.0-or-later
// ---- Langue : la TV dit la sienne (S.lang) ; en anglais, le dictionnaire /app.lang traduit la page
let DICT = null, DICT_LANG = 'fr';
const t = s => (DICT && DICT[s]) || s;
async function applyLang(lang) {
  if (!lang || lang === DICT_LANG) return;
  if (lang === 'fr') { DICT = null; DICT_LANG = 'fr'; location.reload(); return; }
  try {
    const txt = await (await fetch('/app.lang?l=' + encodeURIComponent(lang), {headers: {'X-Lumen': '1'}})).text();
    const d = {};
    txt.split('\n').forEach(l => { if (!l || l[0] === '#') return; const i = l.indexOf('\t'); if (i > 0) d[l.slice(0, i).replace(/\\n/g, '\n')] = l.slice(i + 1).replace(/\\n/g, '\n'); });
    DICT = d; DICT_LANG = lang;
    document.documentElement.lang = lang;
    translateDom(document.body);
  } catch (e) { }
}
function translateDom(root) {
  if (!DICT) return;
  const walk = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes = [];
  while (walk.nextNode()) nodes.push(walk.currentNode);
  nodes.forEach(nd => {
    const raw = nd.nodeValue, s = raw.trim();
    if (s && DICT[s]) nd.nodeValue = raw.replace(s, DICT[s]);
  });
  root.querySelectorAll('[placeholder],[aria-label],[title]').forEach(el => {
    ['placeholder', 'aria-label', 'title'].forEach(a => { const v = el.getAttribute(a); if (v && DICT[v]) el.setAttribute(a, DICT[v]); });
  });
}
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
const TITLES = {tv: 'TV', fav: t('Accueil'), vid: t('Vidéos'), mine: t('Envois'), opt: t('Plus')};

function lost(msg) {
  alive = false;
  clearInterval(poll);
  $('dot').className = '';
  $('sub').textContent = msg || t('Déconnecté · rescanne le QR code sur la TV');
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
  if (t === 'opt') { loadSettings(); loadLog(); if (S.ha && !haEntities) loadHa(); }
}

// ------------------------------------------------------------------ réseau
// ---- Signature des échanges (HMAC-SHA256, écrit ici car le navigateur réserve ses outils
// de chiffrement aux pages HTTPS). La clé vient du QR code et n'est jamais envoyée.
const SHA_K = new Uint32Array([0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
  0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,
  0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,
  0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,
  0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,
  0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2]);
function sha256(bytes) {
  const l = bytes.length, n = ((l + 9 + 63) >> 6) << 6, m = new Uint8Array(n);
  m.set(bytes); m[l] = 0x80;
  const bits = l * 8; m[n - 4] = bits >>> 24; m[n - 3] = bits >>> 16; m[n - 2] = bits >>> 8; m[n - 1] = bits;
  m[n - 8] = Math.floor(l / 0x20000000) >>> 0;
  const h = new Uint32Array([0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19]), w = new Uint32Array(64);
  const r = (x, k) => (x >>> k) | (x << (32 - k));
  for (let o = 0; o < n; o += 64) {
    for (let i = 0; i < 16; i++) w[i] = (m[o + i * 4] << 24) | (m[o + i * 4 + 1] << 16) | (m[o + i * 4 + 2] << 8) | m[o + i * 4 + 3];
    for (let i = 16; i < 64; i++) {
      const s0 = r(w[i - 15], 7) ^ r(w[i - 15], 18) ^ (w[i - 15] >>> 3), s1 = r(w[i - 2], 17) ^ r(w[i - 2], 19) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
    }
    let [a, b, c, d, e, f, g, hh] = h;
    for (let i = 0; i < 64; i++) {
      const t1 = (hh + (r(e, 6) ^ r(e, 11) ^ r(e, 25)) + ((e & f) ^ (~e & g)) + SHA_K[i] + w[i]) >>> 0;
      const t2 = ((r(a, 2) ^ r(a, 13) ^ r(a, 22)) + ((a & b) ^ (a & c) ^ (b & c))) >>> 0;
      hh = g; g = f; f = e; e = (d + t1) >>> 0; d = c; c = b; b = a; a = (t1 + t2) >>> 0;
    }
    h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
  }
  const out = new Uint8Array(32);
  for (let i = 0; i < 8; i++) { out[i * 4] = h[i] >>> 24; out[i * 4 + 1] = h[i] >>> 16; out[i * 4 + 2] = h[i] >>> 8; out[i * 4 + 3] = h[i]; }
  return out;
}
const utf8 = s => new TextEncoder().encode(s);
const hex = b => Array.from(b, x => x.toString(16).padStart(2, '0')).join('');
function hmacRaw(k, msg) {
  if (k.length > 64) k = sha256(k);
  const ip = new Uint8Array(64 + msg.length), op = new Uint8Array(96);
  for (let i = 0; i < 64; i++) { const x = k[i] || 0; ip[i] = x ^ 0x36; op[i] = x ^ 0x5c; }
  ip.set(msg, 64);
  op.set(sha256(ip), 64);
  return sha256(op);
}
const hmac = (key, msg) => hex(hmacRaw(utf8(key), msg));

// ---- Chiffrement des secrets (jeton Home Assistant, clés, code PIN, texte) : même calcul que
// Seal.java. Clé tirée de celle du QR code ; flux HMAC-SHA256(clé, nonce ‖ n° de bloc).
// Le résultat est ensuite signé comme le reste : impossible à modifier ou à rejouer.
const SEALED = ['/api/ha', '/api/tmdb', '/api/pexels', '/api/pin', '/api/text'];
function seal(text) {
  const key = hmacRaw(utf8(signKey), utf8('lumen-chiffrement'));
  const nonce = new Uint8Array(16); crypto.getRandomValues(nonce);
  const p = utf8(text), out = new Uint8Array(p.length), blk = new Uint8Array(20);
  blk.set(nonce);
  let ks = null;
  for (let i = 0; i < p.length; i++) {
    if (i % 32 === 0) { const n = i / 32; blk[16] = n >>> 24; blk[17] = n >>> 16; blk[18] = n >>> 8; blk[19] = n; ks = hmacRaw(key, blk); }
    out[i] = p[i] ^ ks[i % 32];
  }
  return 'E1.' + hex(nonce) + '.' + hex(out);
}

let signKey = '';
try { signKey = localStorage.getItem('lumen-k') || ''; } catch (e) { }
let seqBase = Date.now() * 1000, seqN = 0;
const nextSeq = () => seqBase + (++seqN);
/** En-têtes de signature d'une requête (méthode, adresse, numéro, contenu). */
function signHeaders(method, path, body) {
  // l'adresse exactement telle que le navigateur l'enverra (il ré-encode certains caractères)
  try { const u = new URL(path, location.href); path = u.pathname + u.search; } catch (e) { }
  const seq = nextSeq();
  const bh = body == null ? hex(sha256(new Uint8Array(0))) : typeof body === 'string' ? hex(sha256(utf8(body))) : '-';
  return {'X-Lumen-Seq': String(seq), 'X-Lumen-Sig': hmac(signKey, utf8(method + '\n' + path + '\n' + seq + '\n' + bh))};
}
/** Message signé pour la connexion permanente. */
function wsSigned(payload) {
  const seq = nextSeq();
  return 'S ' + seq + ' ' + hmac(signKey, utf8('ws\n' + seq + '\n' + payload)) + ' ' + payload;
}

// identifiant aléatoire de ce téléphone (reste sur le téléphone) : la TV peut s'en souvenir
// après acceptation, sous forme d'empreinte seulement
function deviceId() {
  try {
    let id = localStorage.getItem('lumen-id');
    if (!id || !/^[A-Za-z0-9]{32}$/.test(id)) {
      const b = new Uint8Array(24); crypto.getRandomValues(b);
      id = Array.from(b, x => 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'[x % 62]).join('').slice(0, 32);
      while (id.length < 32) id += 'x';
      localStorage.setItem('lumen-id', id);
    }
    return id;
  } catch (e) { return ''; }
}

let pendingShown = false;
function showPending(on) {
  if (on === pendingShown) return;
  pendingShown = on;
  $('dot').className = on ? 'busy' : 'on';
  $('sub').textContent = on ? t('Accepte ce téléphone sur la TV (OK sur « Autoriser »)') : t('Connecté');
  if (on) toast(t('Regarde la TV : accepte ce téléphone'));
}

async function api(path, opts) {
  if (!alive) throw new Error('auth');
  const method = (opts && opts.method) || 'GET';
  if (method === 'POST' && opts && typeof opts.body === 'string' && SEALED.includes(path.split('?')[0])) {
    opts = Object.assign({}, opts, {body: seal(opts.body)});
  }
  const body = opts && opts.body !== undefined ? opts.body : null;
  const sh = signHeaders(method, path, body);
  const r = await fetch(path, Object.assign({credentials: 'same-origin', cache: 'no-store'}, opts || {}, {headers: Object.assign({}, H, sh, (opts || {}).headers)}));
  if (r.status === 403) {
    const j = await r.clone().json().catch(() => ({}));
    if (j.pending) { showPending(true); throw new Error('pending'); }
    if (j.limited) { toast(t('Désactivé sur la TV : accès « télécommande seulement »')); throw new Error('limited'); }
  }
  if (r.status === 401) {
    const j = await r.clone().json().catch(() => ({}));
    if (j.sig) { lost(t('Connexion à revalider · rescanne le QR code sur la TV')); throw new Error('auth'); }
  }
  if (r.status === 401 || r.status === 403) { lost(); throw new Error('auth'); }
  if (r.status === 429) { toast(t('Doucement…')); throw new Error('slow'); }
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
    if (j.busy) { toast(t('La TV affiche une autre appli · reviens sur l’accueil Lumen')); return false; }
    if (!j.ok) { toast(j.error || t('Impossible')); return false; }
    if (okMsg) toast(okMsg);
    setTimeout(load, 500);
    return j;
  } catch (e) { return false; }
}

async function start() {
  // « #jeton.clé » : le jeton ouvre la session, la clé signe tout (elle ne quitte jamais le téléphone)
  const frag = location.hash.slice(1).split('.');
  const token = frag[0];
  if (frag[1] && /^[A-Za-z0-9]{32}$/.test(frag[1])) { signKey = frag[1]; try { localStorage.setItem('lumen-k', signKey); } catch (e) { } }
  history.replaceState(null, '', location.pathname); // le jeton disparaît de l'adresse et de l'historique
  if (token) {
    const r = await fetch('/api/session', {method: 'POST', credentials: 'same-origin',
      headers: Object.assign({'X-Lumen-Token': token, 'X-Lumen-Device': encodeURIComponent(savedName()), 'X-Lumen-Id': deviceId()}, H)});
    if (!r.ok) { lost(); return; }
  }
  // en attente d'acceptation sur la TV : on redemande toutes les 1,5 s
  for (let i = 0; i < 45 && alive; i++) {
    try { await api('/api/state'); break; }
    catch (e) { if (e.message !== 'pending') break; await new Promise(res => setTimeout(res, 1500)); }
  }
  showPending(false);
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
    if (S.lang && S.lang !== DICT_LANG) await applyLang(S.lang);
    render();
  } catch (e) { if (!['auth', 'slow', 'pending', 'limited'].includes(e.message)) { $('dot').className = ''; $('sub').textContent = t('TV injoignable'); } }
}

// ------------------------------------------------------------------ rendu
function iconImg(key, cls) {
  const i = el('img', cls); i.loading = 'lazy'; i.alt = '';
  // sans icône : un pictogramme sobre plutôt que deux lettres
  i.addEventListener('error', () => {
    const p = el('div', 'ph');
    p.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="4" width="6.5" height="6.5" rx="1.6"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.6"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.6"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.6"/></svg>';
    i.replaceWith(p);
  });
  i.src = '/api/icon?key=' + encodeURIComponent(key);
  return i;
}

function render() {
  const tv = S.tv || {};
  const d = $('dot'), sub = $('sub');
  if (tv.veille) { d.className = 'on'; sub.textContent = t('Mode veille'); }
  else if (tv.ambiance >= 0) { d.className = 'on'; sub.textContent = t('Ambiance · ') + S.ambiances[tv.ambiance] + (tv.timer ? ' · ' + tv.timer + ' min' : ''); }
  else if (tv.home) { d.className = 'on'; sub.textContent = t('Connecté · accueil Lumen'); }
  else { d.className = 'busy'; sub.textContent = t('Connecté · une appli est ouverte'); }
  const prof = (tv.profiles || []).find(p => p.id === tv.profile);
  if (prof && tv.profiles.length > 1) sub.textContent += ' · ' + prof.name;
  renderRemote(); renderShortcuts(); renderLaunch(); renderAmb(); renderScreen(); renderFav(); renderMine(); renderOpt();
}

function renderLaunch() {
  const q = $('appQ').value.trim().toLowerCase();
  const g = $('launchGrid'); clear(g);
  const visible = S.apps.filter(a => !a.hidden);
  const list = q ? visible.filter(a => a.label.toLowerCase().includes(q)).slice(0, 16)
                 : S.favorites.map(app).filter(a => !a.hidden && S.apps.some(x => x.key === a.key));
  if (!list.length) g.appendChild(el('div', 'empty more', q ? t('Aucune appli') : t('Aucun favori')));
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
    const s = el('button', 'chip stop', t('Arrêter'));
    s.addEventListener('click', () => act('/api/ambiance?kind=-1'));
    c.appendChild(s);
  }
  const t = $('timerChips'); clear(t);
  if (tv.ambiance >= 0) (tv.timers || []).forEach(m => {
    const b = el('button', 'chip', m ? m + ' min' : t('Sans minuterie'));
    if (m === (tv.timerSet || 0)) b.classList.add('on');
    b.addEventListener('click', () => act('/api/timer?min=' + m, null, m ? t('Minuterie : ') + m + ' min' : t('Minuterie désactivée')));
    t.appendChild(b);
  });
}

// ---- Télécommande : liste fermée de touches, la TV décide de ce qu'elle en fait
// touches mises en file : aucun appui n'est perdu, et elles partent l'une après l'autre sans attendre
const keyQueue = [];
let keyBusy = false;
const NEED_SERVICE = t('Active « Lumen télécommande » sur la TV : Paramètres › Accessibilité');
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
    else if (j.for === 't' && !j.ok) toast(j.nofield ? t('Ouvre d’abord la recherche de l’appli sur la TV') : j.service === false ? NEED_SERVICE : t('Impossible'));
  };
  ws.onclose = () => { ws = null; if (alive) setTimeout(openWs, Math.min(5000, 400 * (++wsTry))); };
  ws.onerror = () => { try { ws.close(); } catch (x) { } };
}
setInterval(() => { if (ws && ws.readyState === 1 && !document.hidden) ws.send('p'); }, 20000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) openWs(); });
const wsReady = () => ws && ws.readyState === 1;
function keyError(j) {
  toast(j.sleep === false && j.service ? t('Veille : Android 9 minimum sur la TV')
    : j.service && !j.dpad ? t('Flèches hors de Lumen : Android 13 minimum sur la TV') : NEED_SERVICE);
}

function press(k, quiet) {
  if (navigator.vibrate) navigator.vibrate(quiet ? 4 : 8);
  if (wsReady()) { ws.send(wsSigned('k ' + k)); return; } // instantané
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
// boutons hors du pavé ; ceux du pavé seulement au clavier : au doigt, le geste a déjà envoyé la
// touche, et certains navigateurs ajoutent ensuite un « clic » (la touche partait deux fois)
let lastPadTouch = 0;
document.querySelectorAll('#remote [data-k]').forEach(b => b.addEventListener('click', e => {
  if (b.closest('.pad') && (e.pointerType || Date.now() - lastPadTouch < 800)) return;
  press(b.dataset.k);
}));

// ---- Pavé : glisser pour se déplacer, toucher pour OK, garder le doigt pour défiler vite
const pad = document.querySelector('.pad');
const STEP = 40; // px de glissement par case
let g = null;
function stopRepeat() { if (!g) return; clearTimeout(g.hold); clearInterval(g.rep); g.hold = g.rep = null; }
function armRepeat(k) {
  stopRepeat();
  g.hold = setTimeout(() => { g.repeated = true; g.rep = setInterval(() => press(k, true), 130); }, 420);
}
pad.addEventListener('pointerdown', e => {
  e.preventDefault();
  lastPadTouch = Date.now();
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
  lastPadTouch = Date.now();
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
    b.classList.add('arm'); toast(t('Touche encore pour éteindre l’écran de la TV'));
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
  if (!tv.home && !tv.remote) msg = t('Pour piloter les autres applis : sur la TV, Réglages › Réglages depuis le téléphone › Télécommande dans les autres applis.');
  else if (!tv.home && !tv.dpadOutside) msg = t('Dans les autres applis : Retour, Accueil, volume, lecture, texte et raccourcis (flèches à partir d’Android 13).');
  h.textContent = msg; h.hidden = !msg;
}

function renderScreen() {
  const tv = S.tv || {};
  $('veilleBtn').classList.toggle('on', !!tv.veille);
  $('veilleState').textContent = tv.veille ? t('Activé · toucher pour quitter') : t('Désactivé');
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
  if (!b.classList.contains('arm')) { b.classList.add('arm'); toast(t('Touche encore pour redémarrer Lumen')); setTimeout(() => b.classList.remove('arm'), 3000); return; }
  b.classList.remove('arm');
  try { await api('/api/restart', {method: 'POST'}); toast(t('Lumen redémarre… reconnecte-toi avec le QR code ensuite')); } catch (e) { }
});
$('closeAppBtn').addEventListener('click', async () => {
  try {
    const r = await api('/api/closeapp', {method: 'POST'});
    const j = await r.json();
    toast(j.ok ? (j.name + t(' : fermeture…')) : t('Aucune appli ouverte depuis Lumen'));
  } catch (e) { }
});
$('homeBtn').addEventListener('click', () => press('home'));
$('nextBtn').addEventListener('click', () => act('/api/next', null, t('Fond suivant')));
$('srcSel').addEventListener('change', e => act('/api/source?n=' + e.target.value, null, t('Fond : ') + e.target.selectedOptions[0].textContent));
// ---- Texte : écrit en direct dans la recherche à l'écran (Lumen ou autre appli), Entrée valide
let typeTimer = null, typeSent = '';
// texte en direct : une seule requête à la fois ; pendant qu'elle part, on garde seulement la dernière
// version tapée (jamais d'ancienne frappe qui arriverait après la nouvelle)
let textBusy = false, textNext = null;
async function sendLive(t) {
  if (wsReady()) { ws.send(wsSigned('t0 ' + seal(t))); return; } // instantané, toujours dans l'ordre
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
    if (!j.ok) toast(j.nofield ? t('Ouvre d’abord la recherche de l’appli sur la TV') : j.service === false ? NEED_SERVICE : t('Impossible'));
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
  if (await sendText(t, true)) { toast(t('Recherche envoyée')); $('typeText').value = ''; typeSent = ''; $('typeText').blur(); }
});

// ---- Accueil (favoris)
function renderFav() {
  const fl = $('favList'); clear(fl);
  if (!S.favorites.length) fl.appendChild(el('div', 'empty', t('Aucun favori')));
  const pin = S.tv && S.tv.pin;
  S.favorites.forEach((key, i) => {
    const a = app(key);
    const r = el('div', 'row');
    r.appendChild(iconImg(key));
    r.appendChild(el('div', 'name', a.label));
    if (pin) {
      const lk = el('button', 'ib' + (a.locked ? ' on' : ''), '🔒');
      lk.title = a.locked ? t('Verrouillée') : t('Verrouiller');
      lk.addEventListener('click', () => act('/api/lock?key=' + encodeURIComponent(key) + '&on=' + (a.locked ? 0 : 1), null, a.locked ? a.label + t(' déverrouillée') : a.label + t(' verrouillée')));
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
  if (!rest.length) al.appendChild(el('div', 'empty', t('Aucune appli')));
  rest.forEach(a => {
    const r = el('div', 'row');
    r.appendChild(iconImg(a.key));
    r.appendChild(el('div', 'name', a.label));
    const add = el('button', 'ib add', '+');
    add.addEventListener('click', () => { S.favorites.push(a.key); save(); toast(a.label + t(' ajoutée')); });
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
  } catch (e) { if (e.message !== 'auth') { toast(t('Échec, réessaie')); load(); } }
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
  $('vidNote').textContent = picks.length ? n + (n > 1 ? t(' vidéos gardées') : t(' vidéo gardée')) + t(' · la décocher l’efface de la TV') : t('Chargement du catalogue…');
  const groups = [];
  picks.forEach(p => { let g = groups.find(x => x.name === p.group); if (!g) groups.push(g = {name: p.group, items: []}); g.items.push(p); });
  // mise à jour sur place : les images déjà affichées ne clignotent pas
  if (box.childElementCount && box.dataset.sig === sig()) { picks.forEach(updatePick); return; }
  clear(box);
  box.dataset.sig = sig();
  groups.forEach(g => {
    box.appendChild(el('h2', null, g.name || t('Vidéos')));
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
function renderPicksNote() { const n = picks.filter(p => p.ok).length; $('vidNote').textContent = n + (n > 1 ? t(' vidéos gardées') : t(' vidéo gardée')) + t(' · la décocher l’efface de la TV'); }
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
    const o = el('option', null, t('Ambiance · ') + n + t(' (vidéo)')); o.value = 'cosy:' + i; d.appendChild(o);
  });
  const g = $('mineGrid'); clear(g);
  if (!S.mine.length) g.appendChild(el('div', 'empty', t('Rien pour l’instant')));
  S.mine.forEach(m => {
    const t = el('div', 'tile');
    const im = el('img'); im.loading = 'lazy'; im.alt = ''; im.src = '/api/thumb?id=' + encodeURIComponent(m.id); t.appendChild(im);
    if (m.video) t.appendChild(el('span', 'v', m.cosy ? t('Ambiance') : t('Vidéo')));
    if (m.cosy) t.appendChild(el('span', 'a', m.title.split(' · ').pop()));
    const x = el('button', 'x', '✕');
    x.addEventListener('click', () => act('/api/delete?id=' + encodeURIComponent(m.id), null, t('Supprimé')));
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
    const sh = signHeaders('POST', '/api/upload', f); // fichier : adresse et numéro signés (contenu trop gros)
    x.setRequestHeader('X-Lumen-Seq', sh['X-Lumen-Seq']);
    x.setRequestHeader('X-Lumen-Sig', sh['X-Lumen-Sig']);
    x.upload.onprogress = ev => { if (ev.lengthComputable) fill.style.width = (ev.loaded * 100 / ev.total) + '%'; };
    x.onload = () => {
      if (x.status === 401 || x.status === 403) lost();
      const ok = x.status === 200;
      name.textContent = (ok ? '✓ ' : '✕ ') + f.name + (ok ? '' : t(' · refusé'));
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
  if (!url) { toast(t('Adresse de Home Assistant ?')); return; }
  toast(t('Connexion à Home Assistant…'));
  try {
    const r = await api('/api/ha', {method: 'POST', body: JSON.stringify({url, token}), headers: {'Content-Type': 'text/plain'}});
    const j = await r.json().catch(() => ({ok: false}));
    if (j.pin) { if (await askPin()) $('haForm').requestSubmit(); return; }
    if (!j.ok) { toast(j.error || t('Impossible')); return; }
    $('haToken').value = ''; toast(t('Home Assistant relié')); haEntities = null; await load(); loadHa();
  } catch (x) { }
});
$('haDel').addEventListener('click', async () => {
  if (await act('/api/ha', '', t('Home Assistant délié'))) { haEntities = null; $('haUrl').value = ''; }
});
$('haQ').addEventListener('input', renderHa);
async function loadHa() {
  if (!S.ha || haLoading) return;
  haLoading = true;
  try {
    const r = await api('/api/ha/entities');
    const j = await r.json();
    if (j.ok) haEntities = j.entities; else { haEntities = []; toast(j.error || t('Home Assistant injoignable')); }
  } catch (e) { } finally { haLoading = false; }
  renderHa();
}
function renderHa() {
  const on = !!S.ha;
  $('haLinked').hidden = !on; $('haBox').hidden = !on || !haEntities;
  $('haState').textContent = on ? t('Relié · ') + S.ha.replace(/^https?:\/\//, '') : '';
  if (on && document.activeElement !== $('haUrl') && !$('haUrl').value) $('haUrl').value = S.ha.replace(/^https?:\/\//, '');
  $('haToken').placeholder = on ? t('Nouveau jeton (facultatif)') : t('Jeton d’accès longue durée');
  if (!on || !haEntities) return;
  const q = $('haQ').value.trim().toLowerCase();
  const l = $('haList'); clear(l);
  let grp = null;
  const list = haEntities.filter(x => !q || x.name.toLowerCase().includes(q));
  if (!list.length) l.appendChild(el('div', 'empty', t('Aucun appareil')));
  list.forEach(x => {
    if (x.group !== grp) { grp = x.group; l.appendChild(el('div', 'grp', grp)); }
    const r = el('div', 'row ha' + (x.pinned ? ' on' : ''));
    r.appendChild(el('span', 'ck'));
    r.appendChild(el('div', 'name', x.name));
    r.appendChild(el('span', 'st', x.state));
    if (/^(scene|script)\./.test(x.id)) {
      const hm = el('button', 'hm' + (x.home ? ' on' : ''), t('✦ Accueil'));
      hm.addEventListener('click', async ev => {
        ev.stopPropagation();
        x.home = !x.home; hm.classList.toggle('on', x.home);
        try { await api('/api/ha/home?id=' + encodeURIComponent(x.id) + '&name=' + encodeURIComponent(x.name.slice(0, 32)) + '&on=' + (x.home ? 1 : 0), {method: 'POST'}); toast(x.home ? x.name + t(' sur l’accueil') : x.name + t(' retirée de l’accueil')); }
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
    b.classList.add('arm'); toast(t('Touche encore pour confirmer'));
    clearTimeout(b._t); b._t = setTimeout(() => b.classList.remove('arm'), 3000);
    return;
  }
  b.classList.remove('arm');
  act('/api/storage/clear?what=' + b.dataset.clear, null, t('Place libérée'));
}));
function renderStorage() {
  const s = S.storage; if (!s) return;
  $('stFree').textContent = t('Libre sur la TV : ') + s.free + '. Lumen garde toujours 2 Go libres et réduit ses vidéos si la place manque.';
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
  const none = el('option', null, t('Aucune')); none.value = ''; sel.appendChild(none);
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
$('cineFilm').addEventListener('change', e => { const c = cineState(); c.film = e.target.value; saveCine(c, c.film ? t('Scène « film » enregistrée') : t('Mode cinéma désactivé')); });
$('cinePause').addEventListener('change', e => { const c = cineState(); c.pause = e.target.value; saveCine(c, t('Scène « pause » enregistrée')); });
$('cineOpen').addEventListener('click', () => { const c = cineState(); c.onOpen = !c.onOpen; saveCine(c); });

// ---- Réglages de la TV depuis le téléphone (liste fermée, appliqués aussitôt)
let SET = null, WALLS = null;
async function loadSettings() {
  try { SET = await (await api('/api/settings')).json(); } catch (e) { return; }
  renderSettings();
  try { WALLS = await (await api('/api/walls')).json(); renderWalls(); } catch (e) { }
}
async function setSetting(k, v, msg) {
  try {
    const r = await api('/api/setting?k=' + k + '&v=' + v, {method: 'POST'});
    const j = await r.json();
    if (!j.ok) { toast(t('Impossible')); return; }
    if (msg) toast(msg);
    SET[k] = typeof SET[k] === 'boolean' ? v === 1 : v;
    if (k === 'source' || k === 'interval' || k === 'follow') setTimeout(loadSettings, 700);
    else renderSettings();
  } catch (e) { }
}
function selRow(label, key, values, names, fmt) {
  const r = el('div', 'row');
  r.appendChild(el('div', 'name', label));
  const s = el('select');
  values.forEach((v, i) => { const o = el('option', null, names ? names[i] : fmt(v)); o.value = v; s.appendChild(o); });
  s.value = SET[key];
  s.addEventListener('change', () => setSetting(key, +s.value, label + ' : ' + s.selectedOptions[0].textContent));
  r.appendChild(s);
  return r;
}
function tgRow(label, key, local) {
  const r = el('button', 'row link');
  r.appendChild(el('span', 'name', label));
  const on = local ? local() : !!SET[key];
  const t = el('span', 'tg' + (on ? ' on' : ''));
  r.appendChild(t);
  r.addEventListener('click', () => {
    if (local) { local(true); t.classList.toggle('on'); return; }
    setSetting(key, SET[key] ? 0 : 1);
  });
  return r;
}
const fmtInterval = v => v === 0 ? t('Fond fixe') : v < 60 ? v + ' s' : (v / 60) + ' min';
function renderSettings() {
  if (!SET) return;
  const w = $('setWall'); clear(w);
  w.appendChild(selRow(t('Source'), 'source', SET.sources.map((_, i) => i), SET.sources));
  w.appendChild(selRow(t('Changement'), 'interval', SET.intervals, null, fmtInterval));
  w.appendChild(tgRow(t('Paysages selon l’heure'), 'follow'));
  w.appendChild(tgRow(t('Paysages animés'), 'living'));
  w.appendChild(tgRow(t('Effets météo'), 'weatherFx'));
  const v = $('setVeille'); clear(v);
  v.appendChild(selRow(t('Images'), 'veille', SET.veilles, SET.veilleNames));
  v.appendChild(selRow(t('Activer après'), 'ambient', SET.ambients, null, x => x === 0 ? t('Jamais') : x + ' min'));
  v.appendChild(selRow(t('Écran de nuit'), 'night', SET.nights, null, x => x < 0 ? t('Désactivé') : (x === 0 ? t('De minuit') : t('De ') + x + ' h') + t(' à 7 h')));
  const h = $('setHome'); clear(h);
  h.appendChild(tgRow(t('Bandeau d’accueil'), 'greeting'));
  h.appendChild(tgRow(t('Horloge'), 'clock'));
  h.appendChild(tgRow(t('Bouton Maison'), 'haButton'));
  h.appendChild(tgRow(t('Sons de l’interface'), 'sounds'));
  if (SET.soundThemes) {
    h.appendChild(selRow(t('Jeu de sons'), 'soundTheme', SET.soundThemes.map((_, i) => i), SET.soundThemes));
    h.appendChild(selRow(t('Volume des sons'), 'soundVolume', SET.soundVolumes.map((_, i) => i), SET.soundVolumes));
  }
  h.appendChild(tgRow(t('Icônes monochromes ici'), null, set => {
    let m = true; try { m = localStorage.getItem('lumen-mono') !== '0'; } catch (e) { }
    if (set) { m = !m; try { localStorage.setItem('lumen-mono', m ? '1' : '0'); } catch (e) { } document.body.classList.toggle('mono', m); }
    return m;
  }));
}
function renderWalls() {
  const g = $('wallGrid'); clear(g);
  if (!WALLS || !WALLS.walls.length) { $('wallNote').textContent = t('Aucun fond à gérer pour cette source (paysages animés ou photos pas encore téléchargées).'); return; }
  $('wallNote').textContent = WALLS.source + t(' · touche un fond pour le retirer de la rotation (ou le remettre).');
  WALLS.walls.forEach(x => {
    const b = el('button', 'wall' + (x.hidden ? ' off' : ''));
    const i = el('img'); i.loading = 'lazy'; i.alt = x.title || ''; i.src = '/api/wallthumb?id=' + encodeURIComponent(x.id);
    b.appendChild(i);
    b.appendChild(el('span', 'eye', t('Retiré')));
    b.addEventListener('click', async () => {
      x.hidden = !x.hidden; b.classList.toggle('off', x.hidden);
      try {
        const r = await api('/api/wall?id=' + encodeURIComponent(x.id) + '&hidden=' + (x.hidden ? 1 : 0), {method: 'POST'});
        const j = await r.json(); if (!j.ok) throw 0;
        toast(x.hidden ? t('Fond retiré de la rotation') : t('Fond remis'));
      } catch (e) { x.hidden = !x.hidden; b.classList.toggle('off', x.hidden); }
    });
    g.appendChild(b);
  });
}
try { document.body.classList.toggle('mono', localStorage.getItem('lumen-mono') !== '0'); } catch (e) { document.body.classList.add('mono'); }

// ---- Nom de ce téléphone (affiché sur la TV : « Contrôlée par … »), gardé sur ce téléphone seulement
function savedName() { try { return localStorage.getItem('lumen-name') || ''; } catch (e) { return ''; } }
$('nameForm').addEventListener('submit', async e => {
  e.preventDefault();
  const n = $('nameIn').value.trim().slice(0, 32);
  if (!n) return;
  try { localStorage.setItem('lumen-name', n); } catch (x) { }
  if (await act('/api/name', n, t('Nom enregistré'))) $('nameIn').blur();
});

// ---- Profils (favoris et réglages de chacun ; clés, Maison et code PIN communs)
function renderProfiles() {
  const tv = S.tv || {}, l = $('profList'); clear(l);
  (tv.profiles || []).forEach(p => {
    const on = p.id === tv.profile;
    const r = el('div', 'row ha' + (on ? ' on' : ''));
    r.appendChild(el('span', 'ck'));
    r.appendChild(el('div', 'name', p.name));
    const ren = el('button', 'hm', t('Renommer'));
    ren.addEventListener('click', async ev => {
      ev.stopPropagation();
      const n = (prompt('Nouveau prénom', p.name) || '').trim();
      if (n && n !== p.name) await act('/api/profile/rename?id=' + p.id, n, t('Profil renommé'));
    });
    r.appendChild(ren);
    if (p.id !== 0) {
      const del = el('button', 'hm', t('Supprimer'));
      del.addEventListener('click', async ev => {
        ev.stopPropagation();
        if (confirm(t('Supprimer le profil « ') + p.name + t(' » et ses réglages ?'))) await act('/api/profile/delete?id=' + p.id, null, t('Profil supprimé'));
      });
      r.appendChild(del);
    }
    r.addEventListener('click', async () => { if (!on) await act('/api/profile?id=' + p.id, null, t('Profil : ') + p.name); });
    l.appendChild(r);
  });
}
$('profForm').addEventListener('submit', async e => {
  e.preventDefault();
  const n = $('profIn').value.trim();
  if (!n) return;
  if (await act('/api/profile/add', n, t('Profil « ') + n + t(' » ajouté'))) { $('profIn').value = ''; $('profIn').blur(); }
});

/** Clé enregistrée ou non : dit clairement, et « Retirer » seulement s'il y a une clé. */
function keyState(id, saved, label) {
  $(id + 'State').textContent = t('✓ Clé enregistrée');
  $(id + 'Del').parentElement.hidden = !saved;
  $(id + 'Key').placeholder = saved ? t('Remplacer la clé enregistrée') : label + t(' · aucune clé enregistrée');
}

// ---- Plus
function renderOpt() {
  renderProfiles();
  renderHa();
  renderStorage();
  if (tab === 'opt' && S.ha && !haEntities) loadHa();
  if (document.activeElement !== $('nameIn')) $('nameIn').value = savedName() || (S.tv || {}).device || '';
  const tv = S.tv || {};
  $('pinBox').hidden = !tv.pin;
  $('pinState').textContent = tv.unlocked ? t('Déverrouillé pour 5 min') : t('Déverrouiller les actions protégées');
  keyState('px', S.pexels, t('Clé Pexels'));
  keyState('tm', S.tmdb, t('Clé API TMDB'));
}
$('pinOpen').addEventListener('click', () => askPin());
$('pxSave').addEventListener('click', async () => {
  const k = $('pxKey').value.trim(); if (!k) return;
  if (await act('/api/pexels', k, t('Clé enregistrée'))) $('pxKey').value = '';
});
$('tmSave').addEventListener('click', async () => {
  const k = $('tmKey').value.trim();
  if (!k) return;
  try {
    const r = await api('/api/tmdb', {method: 'POST', body: k, headers: {'Content-Type': 'text/plain'}});
    const j = await r.json();
    if (j.pin) { if (await askPin()) $('tmSave').click(); return; }
    if (!j.ok) { toast(j.error || t('Clé refusée')); return; }
    $('tmKey').value = ''; toast(t('Clé enregistrée')); load();
  } catch (e) { }
});
$('tmDel').addEventListener('click', () => act('/api/tmdb', '', t('Clé retirée')));
$('pxDel').addEventListener('click', () => act('/api/pexels', '', t('Clé retirée')));

$('bkSave').addEventListener('click', async () => {
  try {
    const r = await api('/api/backup');
    const blob = new Blob([await r.text()], {type: 'application/json'});
    const a = el('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'lumen-reglages-' + new Date().toISOString().slice(0, 10) + '.json';
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
    toast(t('Sauvegarde téléchargée'));
  } catch (e) { }
});
$('bkLoad').addEventListener('click', () => $('bkFile').click());
$('bkFile').addEventListener('change', async () => {
  const f = $('bkFile').files[0];
  $('bkFile').value = '';
  if (!f) return;
  if (f.size > 256 * 1024) { toast(t('Fichier trop gros')); return; }
  const j = await act('/api/restore', await f.text());
  if (j) toast(j.count + t(' réglages restaurés sur la TV'));
});

async function loadLog() {
  try { const r = await api('/api/log'); const t = await r.text(); $('log').textContent = t.trim() || t('Aucune erreur'); } catch (e) { }
}
$('logClear').addEventListener('click', async () => { if (await act('/api/log/clear', null, t('Journal effacé'))) loadLog(); });
$('bye').addEventListener('click', async () => {
  try { await api('/api/bye', {method: 'POST'}); } catch (e) { }
  lost();
  $('sub').textContent = t('Déconnecté');
});

// ------------------------------------------------------------------ code PIN
let pinResolve = null;
function askPin() {
  return new Promise(res => {
    pinResolve = res;
    $('pinMsg').textContent = t('Nécessaire pour cette action.');
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
    $('pinMsg').textContent = j.wait ? t('Code incorrect · réessaie dans ') + (j.wait >= 60 ? Math.ceil(j.wait / 60) + ' min' : j.wait + ' s') : t('Code incorrect');
  } catch (e) { closePin(false); }
});

start();

// Vérifie que phone.js chiffre exactement comme Seal.java : exécute le vrai code de la page
// (de SHA-256 jusqu'à seal) avec un nonce fixé (0, 1, 2…).
// Usage : node tests/phone_crypto.js <clé du QR> <texte>  → affiche le message chiffré
const fs = require('fs');
const src = fs.readFileSync(__dirname + '/../assets/phone.js', 'utf8');
const code = src.slice(src.indexOf('const SHA_K'), src.indexOf('let signKey'))
    + '\nlet signKey = process.argv[2];\nconsole.log(seal(process.argv[3]));';
const crypto = {getRandomValues: a => { for (let i = 0; i < a.length; i++) a[i] = i; return a; }};
new Function('process', 'crypto', 'TextEncoder', code)(process, crypto, TextEncoder);

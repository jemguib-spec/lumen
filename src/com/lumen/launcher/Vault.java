// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.os.Build;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Coffre des secrets de la TV : jeton Home Assistant, clés TMDB et Pexels. Ils sont chiffrés
 * (VaultCrypto) avec une clé AES-256 créée dans l'Android Keystore, qui ne sort jamais du matériel
 * de la TV quand il le permet : une copie des préférences ne contient plus de secret lisible.
 * - Android 6 et plus : coffre actif. Android 5 (sans Keystore AES) : les secrets restent tels quels.
 * - Les secrets déjà enregistrés en clair sont chiffrés à la première lecture.
 * - Clé perdue (réinitialisation de la TV) : le secret est oublié, l'utilisateur le saisit de nouveau.
 */
final class Vault {
    private Vault() { }

    private static final String ALIAS = "lumen-secrets";
    private static SecretKey cached;
    private static boolean failed;

    static boolean available() { return Build.VERSION.SDK_INT >= 23 && key() != null; }

    private static synchronized SecretKey key() {
        if (cached != null || failed || Build.VERSION.SDK_INT < 23) return cached;
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            KeyStore.Entry e = ks.getEntry(ALIAS, null);
            if (e instanceof KeyStore.SecretKeyEntry) { cached = ((KeyStore.SecretKeyEntry) e).getSecretKey(); return cached; }
            KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            g.init(new android.security.keystore.KeyGenParameterSpec.Builder(ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(false) // nonce fourni par VaultCrypto (aléatoire à chaque fois)
                    .build());
            cached = g.generateKey();
        } catch (Throwable t) {
            failed = true; // Keystore indisponible sur cette TV : on ne bloque rien, on garde les valeurs telles quelles
        }
        return cached;
    }

    /** Valeur à enregistrer : chiffrée si possible, sinon telle quelle. Vide reste vide. */
    static String seal(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        SecretKey k = key();
        if (k == null) return plain;
        try { return VaultCrypto.seal(k, plain); } catch (Exception e) { return plain; }
    }

    /** Valeur lisible depuis ce qui est enregistré ; une valeur chiffrée illisible devient vide. */
    static String open(String stored) {
        if (stored == null || stored.isEmpty()) return "";
        if (!VaultCrypto.sealed(stored)) return stored; // ancienne valeur en clair (Prefs la rechiffre)
        String s = VaultCrypto.open(key(), stored);
        return s == null ? "" : s;
    }
}

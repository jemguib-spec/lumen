// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Écriture sûre d'un petit fichier (index, catalogue) : fichier temporaire à côté, écrit puis
 * forcé sur le disque, puis renommé d'un coup. Une TV débranchée en pleine écriture garde donc
 * l'ancien fichier intact au lieu d'un fichier à moitié écrit. Java pur, testé.
 */
final class SafeFile {
    private SafeFile() { }

    /** true si « target » contient maintenant exactement « data ». */
    static boolean write(File target, byte[] data) {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            os.write(data);
            os.flush();
            os.getFD().sync();
        } catch (Exception e) {
            tmp.delete();
            return false;
        }
        if (tmp.renameTo(target)) return true;
        tmp.delete();
        return false;
    }
}

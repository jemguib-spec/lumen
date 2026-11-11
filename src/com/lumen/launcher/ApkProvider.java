// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Prête à l'installateur d'Android le fichier de mise à jour déjà vérifié (files/update/Lumen.apk),
 * et rien d'autre : lecture seule, une seule adresse, accès accordé le temps de l'installation.
 */
public final class ApkProvider extends ContentProvider {
    private static final String PATH = "/Lumen.apk";
    static final String MIME = "application/vnd.android.package-archive";

    static Uri uri(Context c) { return Uri.parse("content://" + c.getPackageName() + ".update" + PATH); }

    private File file(Uri u) throws FileNotFoundException {
        if (u == null || !PATH.equals(u.getPath())) throw new FileNotFoundException();
        File f = new File(new File(getContext().getFilesDir(), "update"), "Lumen.apk");
        if (!f.isFile()) throw new FileNotFoundException();
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(u), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri u) { return MIME; }

    @Override public Cursor query(Uri u, String[] cols, String sel, String[] args, String sort) {
        if (cols == null) cols = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        try {
            File f = file(u);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = "Lumen.apk";
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
            }
            c.addRow(row);
        } catch (FileNotFoundException ignored) { }
        return c;
    }

    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String sel, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String sel, String[] args) { throw new UnsupportedOperationException(); }
}

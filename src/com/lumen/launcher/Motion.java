// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 jemguib-spec — Lumen, lanceur pour Android TV (voir LICENSE)
package com.lumen.launcher;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * Une seule échelle de mouvement pour toute l'interface : sélection (le fondu d'une ligne ou d'une
 * vignette qui prend le focus, assez court pour suivre une touche maintenue), rapide (interrupteurs),
 * moyenne (panneaux, pages), lente (fonds, apparitions). Et une seule courbe. Les animations des
 * paysages et des vidéos ont leur propre rythme, lié au contenu.
 */
final class Motion {
    private Motion() { }

    static final int FOCUS = 120, FAST = 180, MEDIUM = 320, SLOW = 700;
    static final Interpolator EASE = new DecelerateInterpolator(1.8f);
}

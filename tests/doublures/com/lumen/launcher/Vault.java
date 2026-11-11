// SPDX-License-Identifier: GPL-3.0-or-later
// Doublure de test (tests/run.sh).
package com.lumen.launcher;
final class Vault { static String seal(String p) { return p == null ? "" : "V:" + p; } static String open(String s) { return s == null || s.isEmpty() ? "" : s.startsWith("V:") ? s.substring(2) : s; } }

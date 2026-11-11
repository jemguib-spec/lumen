// SPDX-License-Identifier: GPL-3.0-or-later
// Doublure de test (tests/run.sh) : le strict nécessaire d'Android pour faire tourner deux TV dans un même programme.
package android.os;
public class Looper { static final Looper MAIN = new Looper(); static volatile Thread mainThread;
  public static Looper getMainLooper() { return MAIN; } public static Looper myLooper() { return Thread.currentThread() == mainThread ? MAIN : null; } }

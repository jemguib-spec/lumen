// SPDX-License-Identifier: GPL-3.0-or-later
// Doublure de test (tests/run.sh) : le strict nécessaire d'Android pour faire tourner deux TV dans un même programme.
package android.content;
import java.util.*;
public class Context { public static final String NSD_SERVICE = "nsd"; public static final int MODE_PRIVATE = 0;
  final Map<String, FakePrefs> prefs = new HashMap<>();
  public Context getApplicationContext() { return this; }
  public synchronized SharedPreferences getSharedPreferences(String n, int mode) { FakePrefs p = prefs.get(n); if (p == null) { p = new FakePrefs(); prefs.put(n, p); } return p; }
  public Object getSystemService(String n) { return new android.net.nsd.NsdManager(); }
  public ContentResolver getContentResolver() { return new ContentResolver(); } }

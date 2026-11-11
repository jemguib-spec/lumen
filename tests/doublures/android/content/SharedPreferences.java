// SPDX-License-Identifier: GPL-3.0-or-later
// Doublure de test (tests/run.sh) : le strict nécessaire d'Android pour faire tourner deux TV dans un même programme.
package android.content;
import java.util.*;
public interface SharedPreferences {
  interface Editor { Editor putString(String k, String v); Editor putStringSet(String k, Set<String> v); Editor putInt(String k, int v); Editor putLong(String k, long v);
    Editor putFloat(String k, float v); Editor putBoolean(String k, boolean v); Editor remove(String k); Editor clear(); boolean commit(); void apply(); }
  interface OnSharedPreferenceChangeListener { void onSharedPreferenceChanged(SharedPreferences sp, String key); }
  Map<String, ?> getAll(); String getString(String k, String d); Set<String> getStringSet(String k, Set<String> d); int getInt(String k, int d); long getLong(String k, long d);
  float getFloat(String k, float d); boolean getBoolean(String k, boolean d); boolean contains(String k); Editor edit();
  void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l); void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Doublure de test (tests/run.sh) : le strict nécessaire d'Android pour faire tourner deux TV dans un même programme.
package android.content;
import java.util.*;
public class FakePrefs implements SharedPreferences {
  final Map<String, Object> m = Collections.synchronizedMap(new HashMap<String, Object>());
  final List<OnSharedPreferenceChangeListener> ls = new ArrayList<>();
  public Map<String, ?> getAll() { synchronized (m) { return new HashMap<>(m); } }
  public String getString(String k, String d) { Object v = m.get(k); return v instanceof String ? (String) v : d; }
  @SuppressWarnings("unchecked") public Set<String> getStringSet(String k, Set<String> d) { Object v = m.get(k); return v instanceof Set ? new HashSet<>((Set<String>) v) : d; }
  public int getInt(String k, int d) { Object v = m.get(k); return v instanceof Integer ? (Integer) v : d; }
  public long getLong(String k, long d) { Object v = m.get(k); return v instanceof Long ? (Long) v : d; }
  public float getFloat(String k, float d) { Object v = m.get(k); return v instanceof Float ? (Float) v : d; }
  public boolean getBoolean(String k, boolean d) { Object v = m.get(k); return v instanceof Boolean ? (Boolean) v : d; }
  public boolean contains(String k) { return m.containsKey(k); }
  public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { synchronized (ls) { ls.add(l); } }
  public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { synchronized (ls) { ls.remove(l); } }
  public Editor edit() {
    final Map<String, Object> put = new LinkedHashMap<>(); final Set<String> rem = new LinkedHashSet<>(); final boolean[] clr = {false};
    return new Editor() {
      public Editor putString(String k, String v) { put.put(k, v); return this; } public Editor putStringSet(String k, Set<String> v) { put.put(k, new HashSet<>(v)); return this; }
      public Editor putInt(String k, int v) { put.put(k, v); return this; } public Editor putLong(String k, long v) { put.put(k, v); return this; }
      public Editor putFloat(String k, float v) { put.put(k, v); return this; } public Editor putBoolean(String k, boolean v) { put.put(k, v); return this; }
      public Editor remove(String k) { rem.add(k); return this; } public Editor clear() { clr[0] = true; return this; }
      public boolean commit() {
        List<String> changed = new ArrayList<>();
        synchronized (m) {
          if (clr[0]) m.clear();
          for (String k : rem) if (m.remove(k) != null && !put.containsKey(k)) changed.add(k);
          for (Map.Entry<String, Object> e : put.entrySet()) { Object old = m.put(e.getKey(), e.getValue()); if (!e.getValue().equals(old)) changed.add(e.getKey()); }
        }
        List<OnSharedPreferenceChangeListener> copy; synchronized (ls) { copy = new ArrayList<>(ls); }
        for (String k : changed) for (OnSharedPreferenceChangeListener l : copy) l.onSharedPreferenceChanged(FakePrefs.this, k);
        return true; }
      public void apply() { commit(); }
    }; }
}

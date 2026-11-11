// SPDX-License-Identifier: GPL-3.0-or-later
// Doublure de test : le strict nécessaire de org.json (fourni par Android sur la TV).
package org.json;

import java.util.ArrayList;
import java.util.List;

public class JSONArray {
    final List<Object> v = new ArrayList<>();

    public JSONArray() { }
    public JSONArray put(Object o) { v.add(o == null ? JSONObject.NULL : o); return this; }
    public JSONArray put(long o) { v.add(o); return this; }
    public int length() { return v.size(); }
    public boolean isNull(int i) { return i >= v.size() || v.get(i) == JSONObject.NULL; }
    public long optLong(int i, long d) { Object o = i < v.size() ? v.get(i) : null; return o instanceof Number ? ((Number) o).longValue() : d; }
    public String optString(int i, String d) { Object o = i < v.size() ? v.get(i) : null; return o == null || o == JSONObject.NULL ? d : String.valueOf(o); }

    @Override public String toString() {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < v.size(); i++) { if (i > 0) b.append(','); JSONObject.write(b, v.get(i)); }
        return b.append(']').toString();
    }
}

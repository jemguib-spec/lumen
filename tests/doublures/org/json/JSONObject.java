// SPDX-License-Identifier: GPL-3.0-or-later
// Doublure de test : le strict nécessaire de org.json (fourni par Android sur la TV).
package org.json;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class JSONObject {
    public static final Object NULL = new Object() { @Override public String toString() { return "null"; } };
    final Map<String, Object> m = new LinkedHashMap<>();

    public JSONObject() { }

    public JSONObject(String s) throws JSONException {
        int[] p = {0};
        Object o = parse(s, p);
        if (!(o instanceof JSONObject)) throw new JSONException("objet attendu");
        m.putAll(((JSONObject) o).m);
    }

    public JSONObject put(String k, Object v) throws JSONException { m.put(k, v == null ? NULL : v); return this; }
    public JSONObject put(String k, int v) throws JSONException { m.put(k, v); return this; }
    public JSONObject put(String k, long v) throws JSONException { m.put(k, v); return this; }
    public JSONObject put(String k, boolean v) throws JSONException { m.put(k, v); return this; }
    public Object remove(String k) { return m.remove(k); }
    public int length() { return m.size(); }
    public boolean has(String k) { return m.containsKey(k); }
    public Iterator<String> keys() { return m.keySet().iterator(); }
    public Object opt(String k) { return m.get(k); }
    public String optString(String k) { return optString(k, ""); }
    public String optString(String k, String d) { Object o = m.get(k); return o == null || o == NULL ? d : String.valueOf(o); }
    public int optInt(String k) { return optInt(k, 0); }
    public int optInt(String k, int d) { Object o = m.get(k); return o instanceof Number ? ((Number) o).intValue() : d; }
    public long optLong(String k) { Object o = m.get(k); return o instanceof Number ? ((Number) o).longValue() : 0; }
    public JSONObject optJSONObject(String k) { Object o = m.get(k); return o instanceof JSONObject ? (JSONObject) o : null; }
    public JSONArray optJSONArray(String k) { Object o = m.get(k); return o instanceof JSONArray ? (JSONArray) o : null; }
    public JSONObject getJSONObject(String k) throws JSONException { JSONObject o = optJSONObject(k); if (o == null) throw new JSONException(k); return o; }
    public String getString(String k) throws JSONException { Object o = m.get(k); if (o == null || o == NULL) throw new JSONException(k); return String.valueOf(o); }
    public int getInt(String k) throws JSONException { Object o = m.get(k); if (!(o instanceof Number)) throw new JSONException(k); return ((Number) o).intValue(); }

    @Override public String toString() {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) b.append(',');
            first = false;
            quote(b, e.getKey());
            b.append(':');
            write(b, e.getValue());
        }
        return b.append('}').toString();
    }

    static void write(StringBuilder b, Object v) {
        if (v == null || v == NULL) b.append("null");
        else if (v instanceof String) quote(b, (String) v);
        else b.append(v);
    }

    static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        b.append('"');
    }

    // ------------------------------------------------------------------ lecture

    private static void ws(String s, int[] p) { while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++; }

    static Object parse(String s, int[] p) throws JSONException {
        ws(s, p);
        if (p[0] >= s.length()) throw new JSONException("fin");
        char c = s.charAt(p[0]);
        if (c == '{') {
            JSONObject o = new JSONObject();
            p[0]++;
            ws(s, p);
            if (s.charAt(p[0]) == '}') { p[0]++; return o; }
            while (true) {
                ws(s, p);
                String k = (String) parse(s, p);
                ws(s, p);
                if (s.charAt(p[0]++) != ':') throw new JSONException(":");
                o.m.put(k, parse(s, p));
                ws(s, p);
                char d = s.charAt(p[0]++);
                if (d == '}') return o;
                if (d != ',') throw new JSONException(",");
            }
        }
        if (c == '[') {
            JSONArray a = new JSONArray();
            p[0]++;
            ws(s, p);
            if (s.charAt(p[0]) == ']') { p[0]++; return a; }
            while (true) {
                a.v.add(parse(s, p));
                ws(s, p);
                char d = s.charAt(p[0]++);
                if (d == ']') return a;
                if (d != ',') throw new JSONException(",");
            }
        }
        if (c == '"') {
            StringBuilder b = new StringBuilder();
            p[0]++;
            while (true) {
                char d = s.charAt(p[0]++);
                if (d == '"') return b.toString();
                if (d == '\\') {
                    char e = s.charAt(p[0]++);
                    switch (e) {
                        case 'n': b.append('\n'); break;
                        case 't': b.append('\t'); break;
                        case 'r': b.append('\r'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(p[0], p[0] + 4), 16)); p[0] += 4; break;
                        default: b.append(e);
                    }
                } else b.append(d);
            }
        }
        if (s.startsWith("null", p[0])) { p[0] += 4; return NULL; }
        if (s.startsWith("true", p[0])) { p[0] += 4; return Boolean.TRUE; }
        if (s.startsWith("false", p[0])) { p[0] += 5; return Boolean.FALSE; }
        int a = p[0];
        while (p[0] < s.length() && "+-0123456789.eE".indexOf(s.charAt(p[0])) >= 0) p[0]++;
        String n = s.substring(a, p[0]);
        if (n.isEmpty()) throw new JSONException("valeur");
        if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.valueOf(n);
        long l = Long.parseLong(n);
        return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (Object) (int) l : (Object) l;
    }
}

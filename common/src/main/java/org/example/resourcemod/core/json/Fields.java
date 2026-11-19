package org.example.resourcemod.core.json;

import org.example.resourcemod.core.hex.Hex;
import org.example.resourcemod.core.id.Id;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Typed access to one JSON object with strict checking: wrong types are errors, and {@link #finish()} turns every
 * field that was never read into an error ("unknown field"), so typos in field names are noticed immediately.
 */
public final class Fields {
    private final Map<String, Object> map;
    private final String file;
    private final String path;
    private final Set<String> used = new HashSet<>();

    @SuppressWarnings("unchecked")
    private Fields(Object value, String file, String path) {
        if (!(value instanceof Map)) {
            throw new DataException(file, path, "Expected an object");
        }
        this.map = (Map<String, Object>) value;
        this.file = file;
        this.path = path;
    }

    public static Fields of(Object value, String file) {
        return new Fields(value, file, "");
    }

    public String file() {
        return file;
    }

    public String path() {
        return path;
    }

    public String childPath(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    public DataException error(String key, String message) {
        return new DataException(file, key == null ? path : childPath(key), message);
    }

    public boolean has(String key) {
        return map.containsKey(key);
    }

    /** Marks a field as known without reading it (for pass-through data). */
    public Object raw(String key) {
        used.add(key);
        return map.get(key);
    }

    public Set<String> keys() {
        return map.keySet();
    }

    private Object require(String key) {
        if (!map.containsKey(key)) {
            throw error(null, "Missing required field '" + key + "'");
        }
        used.add(key);
        return map.get(key);
    }

    private Object optional(String key) {
        if (!map.containsKey(key)) {
            return null;
        }
        used.add(key);
        return map.get(key);
    }

    public int reqInt(String key, int min, int max) {
        return toInt(require(key), key, min, max);
    }

    public Integer optInt(String key, int min, int max) {
        Object v = optional(key);
        return v == null ? null : toInt(v, key, min, max);
    }

    private int toInt(Object v, String key, int min, int max) {
        if (!(v instanceof Long l)) {
            throw error(key, "Expected a whole number");
        }
        if (l < min || l > max) {
            throw error(key, "Value " + l + " is outside the allowed range " + min + ".." + max);
        }
        return (int) (long) l;
    }

    /** Positive number (integer or decimal). */
    public double reqNumber(String key) {
        Object v = require(key);
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Double d) {
            return d;
        }
        throw error(key, "Expected a number");
    }

    public String reqString(String key) {
        Object v = require(key);
        if (!(v instanceof String s)) {
            throw error(key, "Expected a text");
        }
        return s;
    }

    public String optString(String key) {
        Object v = optional(key);
        if (v == null) {
            return null;
        }
        if (!(v instanceof String s)) {
            throw error(key, "Expected a text");
        }
        return s;
    }

    public boolean optBoolean(String key, boolean def) {
        Object v = optional(key);
        if (v == null) {
            return def;
        }
        if (!(v instanceof Boolean b)) {
            throw error(key, "Expected true or false");
        }
        return b;
    }

    public Fields reqObject(String key) {
        return new Fields(require(key), file, childPath(key));
    }

    public Fields optObject(String key) {
        Object v = optional(key);
        return v == null ? null : new Fields(v, file, childPath(key));
    }

    @SuppressWarnings("unchecked")
    public List<Object> reqList(String key) {
        Object v = require(key);
        if (!(v instanceof List)) {
            throw error(key, "Expected a list");
        }
        return (List<Object>) v;
    }

    @SuppressWarnings("unchecked")
    public List<Object> optList(String key) {
        Object v = optional(key);
        if (v == null) {
            return null;
        }
        if (!(v instanceof List)) {
            throw error(key, "Expected a list");
        }
        return (List<Object>) v;
    }

    /** Elements of a list as objects. */
    public List<Fields> reqObjects(String key) {
        return objects(reqList(key), key);
    }

    public List<Fields> optObjects(String key) {
        List<Object> l = optList(key);
        return l == null ? null : objects(l, key);
    }

    private List<Fields> objects(List<Object> list, String key) {
        List<Fields> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            out.add(new Fields(list.get(i), file, childPath(key) + "[" + i + "]"));
        }
        return out;
    }

    public Hex reqHex(String key) {
        return toHex(require(key), childPath(key));
    }

    public Hex optHex(String key) {
        Object v = optional(key);
        return v == null ? null : toHex(v, childPath(key));
    }

    /** Hex from an already parsed [q, r] value. */
    public Hex toHex(Object v, String where) {
        if (!(v instanceof List<?> l) || l.size() != 2 || !(l.get(0) instanceof Long q) || !(l.get(1) instanceof Long r)) {
            throw new DataException(file, where, "Expected a position [q, r] of two whole numbers");
        }
        int limit = org.example.resourcemod.core.hex.HexField.COORDINATE_LIMIT;
        if (Math.abs(q) > limit || Math.abs(r) > limit) {
            throw new DataException(file, where, "Coordinate outside the limit of " + limit);
        }
        return new Hex((int) (long) q, (int) (long) r);
    }

    public Id reqId(String key) {
        return toId(reqString(key), key);
    }

    public Id optId(String key) {
        String s = optString(key);
        return s == null ? null : toId(s, key);
    }

    public Id toId(String text, String key) {
        try {
            return Id.parse(text);
        } catch (IllegalArgumentException e) {
            throw error(key, e.getMessage());
        }
    }

    /** Errors on every field that was not read. Call after reading everything an object may contain. */
    public void finish() {
        Set<String> unknown = new TreeSet<>(map.keySet());
        unknown.removeAll(used);
        if (!unknown.isEmpty()) {
            throw error(null, "Unknown field" + (unknown.size() > 1 ? "s" : "") + ": " + String.join(", ", unknown));
        }
    }
}

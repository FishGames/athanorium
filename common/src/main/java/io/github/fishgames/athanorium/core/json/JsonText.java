package io.github.fishgames.athanorium.core.json;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/** Writes values in the shapes {@link StrictJson} produces (maps, lists, String, Long, Double, Boolean) back to text. */
public final class JsonText {
    private JsonText() {
    }

    public static String write(Object value) {
        StringWriter out = new StringWriter();
        try (JsonWriter w = new JsonWriter(out)) {
            write(w, value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    public static void write(JsonWriter w, Object v) throws IOException {
        if (v instanceof Map<?, ?> m) {
            w.beginObject();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                w.name(String.valueOf(e.getKey()));
                write(w, e.getValue());
            }
            w.endObject();
        } else if (v instanceof List<?> l) {
            w.beginArray();
            for (Object o : l) {
                write(w, o);
            }
            w.endArray();
        } else if (v instanceof String s) {
            w.value(s);
        } else if (v instanceof Long n) {
            w.value(n.longValue());
        } else if (v instanceof Double d) {
            w.value(d);
        } else if (v instanceof Boolean b) {
            w.value(b);
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: " + v);
        }
    }
}

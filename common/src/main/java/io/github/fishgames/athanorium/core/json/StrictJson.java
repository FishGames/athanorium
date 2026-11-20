package io.github.fishgames.athanorium.core.json;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Strict JSON reader built directly on Gson's {@link JsonReader}.
 * <p>
 * Why not JsonObject or reflection binding: JsonObject silently overwrites duplicate keys and reflection binding
 * silently ignores unknown fields, but the data format needs both to be errors. Numbers without fraction or
 * exponent become {@link Long} (positions must be whole numbers), all others {@link Double}.
 * <p>
 * Result types: {@code LinkedHashMap<String,Object>} (field order kept), {@code ArrayList<Object>}, String, Long,
 * Double, Boolean. JSON null is rejected.
 */
public final class StrictJson {
    private static final Pattern INTEGER = Pattern.compile("-?(0|[1-9][0-9]*)");
    private static final int MAX_DEPTH = 64;

    private StrictJson() {
    }

    public static Object parse(Reader in, String fileName) {
        try (JsonReader reader = new JsonReader(in)) {
            // Minecraft 1.20.1 ships Gson 2.10.1, which has no Strictness enum yet
            reader.setLenient(false);
            Object value = readValue(reader, fileName, "", 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new DataException(fileName, "", "Unexpected content after the end of the JSON document");
            }
            return value;
        } catch (MalformedJsonException | IllegalStateException | NumberFormatException e) {
            throw new DataException(fileName, "", "Malformed JSON: " + e.getMessage());
        } catch (IOException e) {
            throw new DataException(fileName, "", "Cannot read file: " + e.getMessage());
        }
    }

    public static Object parse(String text, String fileName) {
        return parse(new java.io.StringReader(text), fileName);
    }

    private static Object readValue(JsonReader reader, String file, String path, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new DataException(file, path, "JSON nested too deeply");
        }
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT: {
                reader.beginObject();
                Map<String, Object> map = new LinkedHashMap<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    String childPath = path.isEmpty() ? name : path + "." + name;
                    if (map.containsKey(name)) {
                        throw new DataException(file, childPath, "Duplicate key '" + name + "'");
                    }
                    map.put(name, readValue(reader, file, childPath, depth + 1));
                }
                reader.endObject();
                return map;
            }
            case BEGIN_ARRAY: {
                reader.beginArray();
                List<Object> list = new ArrayList<>();
                while (reader.hasNext()) {
                    list.add(readValue(reader, file, path + "[" + list.size() + "]", depth + 1));
                }
                reader.endArray();
                return list;
            }
            case STRING:
                return reader.nextString();
            case NUMBER: {
                String text = reader.nextString();
                if (INTEGER.matcher(text).matches()) {
                    try {
                        return Long.parseLong(text);
                    } catch (NumberFormatException e) {
                        throw new DataException(file, path, "Integer out of range: " + text);
                    }
                }
                return Double.parseDouble(text);
            }
            case BOOLEAN:
                return reader.nextBoolean();
            case NULL:
                throw new DataException(file, path, "null is not allowed here");
            default:
                throw new DataException(file, path, "Unexpected JSON token " + token);
        }
    }
}

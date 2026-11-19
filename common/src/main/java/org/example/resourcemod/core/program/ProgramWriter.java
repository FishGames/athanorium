package org.example.resourcemod.core.program;

import com.google.gson.stream.JsonWriter;
import org.example.resourcemod.core.hex.Hex;
import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.part.PartType;
import org.example.resourcemod.core.registry.Registries;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Writes the "parts" list in the order of the program. */
public final class ProgramWriter {
    private ProgramWriter() {
    }

    public static void write(JsonWriter w, Program program, Registries reg) throws IOException {
        w.beginArray();
        for (Part p : program.parts()) {
            writePart(w, p, reg);
        }
        w.endArray();
    }

    private static void writePart(JsonWriter w, Part p, Registries reg) throws IOException {
        w.beginObject();
        w.name("type").value(p.type().toFileString());
        PartType pt = reg.parts.get(p.type());
        if (pt == null) {
            writePos(w, p.pos());
            for (Map.Entry<String, Object> e : p.raw().entrySet()) {
                w.name(e.getKey());
                writeRaw(w, e.getValue());
            }
            w.endObject();
            return;
        }
        if (pt.kind() == PartType.Kind.INPUT) {
            w.name("index").value(p.inputIndex());
        }
        writePos(w, p.pos());
        if (pt.hasRotation()) {
            w.name("rotation").value(p.rotation());
        }
        switch (pt.kind()) {
            case TRACK -> {
                w.name("hexes").beginArray();
                for (Hex h : p.trackHexes()) {
                    w.setIndent("");
                    w.beginArray().value(h.q()).value(h.r()).endArray();
                    w.setIndent("  ");
                }
                w.endArray();
            }
            case ARM -> {
                w.name("length").value(p.length());
                w.name("instructions").beginObject();
                for (Map.Entry<Integer, Id> e : p.instructions().entrySet()) {
                    w.name(Integer.toString(e.getKey())).value(e.getValue().toFileString());
                }
                w.endObject();
            }
            default -> {
            }
        }
        w.endObject();
    }

    private static void writePos(JsonWriter w, Hex pos) throws IOException {
        w.name("pos");
        w.setIndent("");
        w.beginArray().value(pos.q()).value(pos.r()).endArray();
        w.setIndent("  ");
    }

    @SuppressWarnings("unchecked")
    private static void writeRaw(JsonWriter w, Object v) throws IOException {
        if (v instanceof Map<?, ?> m) {
            w.beginObject();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                w.name(e.getKey());
                writeRaw(w, e.getValue());
            }
            w.endObject();
        } else if (v instanceof List<?> l) {
            w.beginArray();
            for (Object o : l) {
                writeRaw(w, o);
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
        }
    }
}

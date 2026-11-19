package org.example.resourcemod.core.program;

import com.google.gson.stream.JsonWriter;
import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.json.Fields;
import org.example.resourcemod.core.json.StrictJson;
import org.example.resourcemod.core.puzzle.PuzzleReader;
import org.example.resourcemod.core.registry.Registries;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;

/** Reads and writes reference solution files. */
public final class SolutionIo {
    private SolutionIo() {
    }

    public static SolutionFile read(Reader in, String fileName, Registries reg) {
        return read(StrictJson.parse(in, fileName), fileName, reg);
    }

    public static SolutionFile read(String json, String fileName, Registries reg) {
        return read(StrictJson.parse(json, fileName), fileName, reg);
    }

    public static SolutionFile read(Object root, String fileName, Registries reg) {
        Fields f = Fields.of(root, fileName);
        int format = PuzzleReader.readFormat(f, SolutionFile.CURRENT_FORMAT);
        Id puzzle = f.reqId("puzzle");

        HexField field;
        Object radius = f.raw("radius");
        if (radius == null) {
            throw f.error(null, "Missing required field 'radius'");
        } else if ("unbounded".equals(radius)) {
            field = HexField.UNBOUNDED;
        } else if (radius instanceof Long r && r >= 2 && r <= HexField.COORDINATE_LIMIT) {
            field = HexField.bounded((int) (long) r);
        } else {
            throw f.error("radius", "Expected a whole number between 2 and " + HexField.COORDINATE_LIMIT
                    + " or \"unbounded\"");
        }

        Fields versions = f.reqObject("versions");
        int rules = versions.reqInt("rules", 0, Integer.MAX_VALUE);
        Fields types = versions.reqObject("types");
        Map<Id, Integer> typeVersions = new TreeMap<>();
        for (String key : new java.util.ArrayList<>(types.keys())) {
            typeVersions.put(types.toId(key, key), types.reqInt(key, 0, Integer.MAX_VALUE));
        }
        types.finish();
        versions.finish();

        Fields expectedFields = f.reqObject("expected");
        Map<Id, Long> expected = new TreeMap<>();
        for (String key : new java.util.ArrayList<>(expectedFields.keys())) {
            Object v = expectedFields.raw(key);
            if (!(v instanceof Long l) || l < 0) {
                throw expectedFields.error(key, "Expected a whole number of at least 0");
            }
            expected.put(expectedFields.toId(key, key), l);
        }
        expectedFields.finish();

        Program program = ProgramReader.read(f.reqObjects("parts"), reg);
        f.finish();
        return new SolutionFile(format, puzzle, field, rules, typeVersions, expected, program);
    }

    public static String write(SolutionFile s, Registries reg) {
        StringWriter out = new StringWriter();
        try (JsonWriter w = new JsonWriter(out)) {
            w.setIndent("  ");
            w.beginObject();
            w.name("format").value(s.format());
            w.name("puzzle").value(s.puzzle().toFileString());
            if (s.field().isBounded()) {
                w.name("radius").value(s.field().radius());
            } else {
                w.name("radius").value("unbounded");
            }
            w.name("versions").beginObject();
            w.name("rules").value(s.rulesVersion());
            w.name("types").beginObject();
            for (Map.Entry<Id, Integer> e : new TreeMap<>(s.typeVersions()).entrySet()) {
                w.name(e.getKey().toFileString()).value(e.getValue());
            }
            w.endObject();
            w.endObject();
            w.name("expected").beginObject();
            for (Map.Entry<Id, Long> e : new TreeMap<>(s.expected()).entrySet()) {
                w.name(e.getKey().toFileString()).value(e.getValue());
            }
            w.endObject();
            w.name("parts");
            ProgramWriter.write(w, s.program(), reg);
            w.endObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }
}

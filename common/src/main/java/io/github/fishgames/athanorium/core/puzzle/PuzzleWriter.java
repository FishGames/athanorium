package io.github.fishgames.athanorium.core.puzzle;

import com.google.gson.stream.JsonWriter;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.molecule.Molecule;
import io.github.fishgames.athanorium.core.part.BondType;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Writes a puzzle as JSON. Omitted optional fields stay omitted, so loading and exporting a file without
 * changes gives the same content. Atoms and bonds keep their order; ids of the mod itself are written
 * without namespace.
 */
public final class PuzzleWriter {
    private PuzzleWriter() {
    }

    public static String write(Puzzle p) {
        StringWriter out = new StringWriter();
        try (JsonWriter w = new JsonWriter(out)) {
            w.setIndent("  ");
            w.beginObject();
            w.name("format").value(p.format());
            if (p.name() != null) {
                w.name("name").value(p.name());
            }
            if (p.nameFallback() != null) {
                w.name("name_fallback").value(p.nameFallback());
            }
            w.name("inputs").beginArray();
            for (Molecule m : p.inputs()) {
                writeMolecule(w, m);
            }
            w.endArray();
            w.name("product");
            writeMolecule(w, p.product());
            if (p.forbiddenParts() != null) {
                writeIds(w, "forbidden_parts", p.forbiddenParts());
            }
            if (p.forbiddenInstructions() != null) {
                writeIds(w, "forbidden_instructions", p.forbiddenInstructions());
            }
            w.name("scoring").beginObject();
            w.name("metric").value(p.metric().toFileString());
            w.name("reference").value(numberFor(p.reference()));
            w.endObject();
            if (p.testProducts() != null) {
                w.name("test_products").value(p.testProducts());
            }
            if (p.maxProgramLength() != null) {
                w.name("max_program_length").value(p.maxProgramLength());
            }
            if (p.maxCycles() != null) {
                w.name("max_cycles").value(p.maxCycles());
            }
            w.endObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    private static Number numberFor(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return (long) d;
        }
        return d;
    }

    private static void writeIds(JsonWriter w, String key, List<Id> ids) throws IOException {
        w.name(key).beginArray();
        for (Id id : ids) {
            w.value(id.toFileString());
        }
        w.endArray();
    }

    private static void writeMolecule(JsonWriter w, Molecule m) throws IOException {
        w.beginObject();
        w.name("atoms").beginArray();
        for (Molecule.Atom a : m.atoms()) {
            w.beginObject();
            w.name("element").value(a.element().toFileString());
            writePos(w, "pos", a.pos().q(), a.pos().r());
            w.endObject();
        }
        w.endArray();
        if (!m.bonds().isEmpty()) {
            w.name("bonds").beginArray();
            for (Molecule.Bond b : m.bonds()) {
                w.beginObject();
                writePos(w, "from", b.from().q(), b.from().r());
                writePos(w, "to", b.to().q(), b.to().r());
                if (!BondType.NORMAL.equals(b.type())) {
                    w.name("type").value(b.type().toFileString());
                }
                w.endObject();
            }
            w.endArray();
        }
        w.endObject();
    }

    static void writePos(JsonWriter w, String key, int q, int r) throws IOException {
        w.name(key);
        w.setIndent("");
        w.beginArray().value(q).value(r).endArray();
        w.setIndent("  ");
    }
}

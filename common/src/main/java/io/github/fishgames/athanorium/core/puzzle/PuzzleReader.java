package io.github.fishgames.athanorium.core.puzzle;

import io.github.fishgames.athanorium.core.hex.Hex;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.json.DataException;
import io.github.fishgames.athanorium.core.json.Fields;
import io.github.fishgames.athanorium.core.json.StrictJson;
import io.github.fishgames.athanorium.core.molecule.Molecule;
import io.github.fishgames.athanorium.core.part.BondType;
import io.github.fishgames.athanorium.core.registry.Registries;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads and checks a puzzle file.
 * Any error becomes a {@link DataException} that names file and field; callers skip the file and log it.
 * Unknown ids in the forbidden lists only add a warning.
 */
public final class PuzzleReader {
    private PuzzleReader() {
    }

    public static Puzzle read(Reader in, String fileName, Registries reg, List<String> warnings) {
        return fromJson(StrictJson.parse(in, fileName), fileName, reg, warnings);
    }

    public static Puzzle read(String json, String fileName, Registries reg, List<String> warnings) {
        return fromJson(StrictJson.parse(json, fileName), fileName, reg, warnings);
    }

    public static Puzzle fromJson(Object root, String fileName, Registries reg, List<String> warnings) {
        Fields f = Fields.of(root, fileName);
        int format = readFormat(f, Puzzle.CURRENT_FORMAT);

        String name = f.optString("name");
        String nameFallback = f.optString("name_fallback");

        List<Molecule> inputs = new ArrayList<>();
        List<Fields> inputFields = f.reqObjects("inputs");
        if (inputFields.isEmpty()) {
            throw f.error("inputs", "A puzzle needs at least one input");
        }
        for (Fields in : inputFields) {
            inputs.add(readMolecule(in, reg));
        }
        Molecule product = readMolecule(f.reqObject("product"), reg);

        List<Id> forbiddenParts = readIdList(f, "forbidden_parts");
        checkKnown(forbiddenParts, "forbidden_parts", f.file(), warnings, id -> reg.parts.contains(id));
        List<Id> forbiddenInstructions = readIdList(f, "forbidden_instructions");
        checkKnown(forbiddenInstructions, "forbidden_instructions", f.file(), warnings, id -> reg.instructions.contains(id));

        Fields scoring = f.reqObject("scoring");
        Id metric = scoring.reqId("metric");
        if (!reg.metrics.contains(metric)) {
            throw scoring.error("metric", "Unknown score metric '" + metric + "'");
        }
        double reference = scoring.reqNumber("reference");
        if (!(reference > 0)) {
            throw scoring.error("reference", "The reference value must be greater than 0");
        }
        scoring.finish();

        Integer testProducts = f.optInt("test_products", 1, Integer.MAX_VALUE);
        Integer maxProgramLength = f.optInt("max_program_length", 1, Integer.MAX_VALUE);
        Integer maxCycles = f.optInt("max_cycles", 1, Integer.MAX_VALUE);
        f.finish();

        return new Puzzle(format, name, nameFallback, inputs, product, forbiddenParts, forbiddenInstructions,
                metric, reference, testProducts, maxProgramLength, maxCycles);
    }

    /** Reads and checks the mandatory "format" field. */
    public static int readFormat(Fields f, int knownFormat) {
        if (!f.has("format")) {
            throw f.error(null, "Missing required field 'format'");
        }
        int format = f.reqInt("format", 1, Integer.MAX_VALUE);
        if (format > knownFormat) {
            throw f.error("format", "File format " + format + " is newer than the format " + knownFormat
                    + " this version of the mod knows");
        }
        return format;
    }

    private static List<Id> readIdList(Fields f, String key) {
        List<Object> list = f.optList(key);
        if (list == null) {
            return null;
        }
        List<Id> ids = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof String s)) {
                throw new DataException(f.file(), f.childPath(key) + "[" + i + "]", "Expected a text");
            }
            try {
                ids.add(Id.parse(s));
            } catch (IllegalArgumentException e) {
                throw new DataException(f.file(), f.childPath(key) + "[" + i + "]", e.getMessage());
            }
        }
        return ids;
    }

    private static void checkKnown(List<Id> ids, String key, String file, List<String> warnings,
                                   java.util.function.Predicate<Id> known) {
        if (ids == null) {
            return;
        }
        for (Id id : ids) {
            if (!known.test(id)) {
                warnings.add(file + " at " + key + ": unknown id '" + id + "' (kept, may come from a missing addon)");
            }
        }
    }

    /** Reads one molecule and applies the load checks. */
    public static Molecule readMolecule(Fields f, Registries reg) {
        List<Molecule.Atom> atoms = new ArrayList<>();
        Set<Hex> seen = new HashSet<>();
        List<Fields> atomFields = f.reqObjects("atoms");
        if (atomFields.isEmpty()) {
            throw f.error("atoms", "A molecule needs at least one atom");
        }
        for (Fields a : atomFields) {
            Id element = a.reqId("element");
            if (!reg.elements.contains(element)) {
                throw a.error("element", "Unknown element '" + element + "'");
            }
            Hex pos = a.reqHex("pos");
            a.finish();
            if (!seen.add(pos)) {
                throw a.error("pos", "Two atoms on the same position " + pos);
            }
            atoms.add(new Molecule.Atom(element, pos));
        }
        List<Molecule.Bond> bonds = new ArrayList<>();
        Set<Molecule.Bond> pairs = new HashSet<>();
        List<Fields> bondFields = f.optObjects("bonds");
        if (bondFields != null) {
            for (Fields b : bondFields) {
                Hex from = b.reqHex("from");
                Hex to = b.reqHex("to");
                Id type = b.optId("type");
                if (type == null) {
                    type = BondType.NORMAL;
                }
                if (!reg.bondTypes.contains(type)) {
                    throw b.error("type", "Unknown bond type '" + type + "'");
                }
                b.finish();
                if (Hex.directionOf(to.minus(from)) < 0) {
                    throw b.error(null, "A bond must connect two neighbouring positions: " + from + " and " + to);
                }
                if (!seen.contains(from) || !seen.contains(to)) {
                    throw b.error(null, "A bond must connect two existing atoms: " + from + " and " + to);
                }
                Molecule.Bond bond = new Molecule.Bond(from, to, type);
                Molecule.Bond key = new Molecule.Bond(bond.normalized().from(), bond.normalized().to(), BondType.NORMAL);
                if (!pairs.add(key)) {
                    throw b.error(null, "More than one bond between " + from + " and " + to);
                }
                bonds.add(bond);
            }
        }
        f.finish();
        Molecule m = new Molecule(atoms, bonds);
        if (!m.isConnected()) {
            throw f.error(null, "The atoms of a molecule must be connected by bonds");
        }
        return m;
    }
}

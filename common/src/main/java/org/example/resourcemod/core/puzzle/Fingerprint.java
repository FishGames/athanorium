package org.example.resourcemod.core.puzzle;

import org.example.resourcemod.core.Rules;
import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.molecule.Molecule;
import org.example.resourcemod.core.part.PartType;
import org.example.resourcemod.core.program.Part;
import org.example.resourcemod.core.program.Program;
import org.example.resourcemod.core.registry.Registered;
import org.example.resourcemod.core.registry.Registries;
import org.example.resourcemod.core.score.PartValue;
import org.example.resourcemod.core.score.PartValues;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.TreeSet;

/**
 * The two checksums stored with every computed result.
 * <ul>
 *   <li>solvability: inputs, product, forbidden lists, program length limit, effective test products and cycle
 *       limit, effective field radius, rules version and the versions of all used types;</li>
 *   <li>scoring: metric, reference value and the part values of the parts actually built.</li>
 * </ul>
 * Both are SHA-256 over a canonical text: atoms and bonds sorted, bond ends in fixed order, all ids with
 * namespace, forbidden lists sorted without duplicates, omitted fields replaced by their defaults. Input order is
 * kept because solutions address inputs by index. The display fields name and name_fallback are not included.
 */
public final class Fingerprint {
    private Fingerprint() {
    }

    public static String solvability(Puzzle puzzle, Program program, HexField field, Registries reg) {
        StringBuilder sb = new StringBuilder("solvability-v1\n");
        sb.append("rules=").append(Rules.VERSION).append('\n');
        for (int i = 0; i < puzzle.inputs().size(); i++) {
            sb.append("input ").append(i).append(": ");
            molecule(sb, puzzle.inputs().get(i));
        }
        sb.append("product: ");
        molecule(sb, puzzle.product());
        sb.append("forbidden_parts=").append(sortedIds(puzzle.forbiddenPartsOrEmpty())).append('\n');
        sb.append("forbidden_instructions=").append(sortedIds(puzzle.forbiddenInstructionsOrEmpty())).append('\n');
        sb.append("max_program_length=").append(puzzle.maxProgramLength() == null ? "none" : puzzle.maxProgramLength())
                .append('\n');
        sb.append("test_products=").append(puzzle.effectiveTestProducts()).append('\n');
        sb.append("max_cycles=").append(puzzle.effectiveMaxCycles()).append('\n');
        sb.append("field=").append(field.isBounded() ? Integer.toString(field.radius()) : "unbounded").append('\n');

        TreeSet<String> versions = new TreeSet<>();
        for (Molecule m : puzzle.inputs()) {
            addMoleculeVersions(versions, m, reg);
        }
        addMoleculeVersions(versions, puzzle.product(), reg);
        for (Part p : program.parts()) {
            PartType pt = reg.parts.get(p.type());
            if (pt != null) {
                versions.add("part " + p.type() + "=" + pt.version());
            }
            for (Id inst : p.instructions().values()) {
                var it = reg.instructions.get(inst);
                if (it != null) {
                    versions.add("instruction " + inst + "=" + it.version());
                }
            }
        }
        for (Registered metric : reg.metrics.values()) {
            versions.add("metric " + metric.id() + "=" + metric.version());
        }
        for (String v : versions) {
            sb.append(v).append('\n');
        }
        return sha256(sb.toString());
    }

    public static String scoring(Puzzle puzzle, Program program, PartValues values) {
        StringBuilder sb = new StringBuilder("scoring-v1\n");
        sb.append("metric=").append(puzzle.metric()).append('\n');
        sb.append("reference=").append(BigDecimal.valueOf(puzzle.reference()).stripTrailingZeros().toPlainString())
                .append('\n');
        TreeSet<String> built = new TreeSet<>();
        for (Part p : program.parts()) {
            PartValue v = values.get(p.type());
            if (v != null) {
                built.add(p.type() + "=" + v.value() + (v.perHex() ? "/hex" : ""));
            }
        }
        for (String b : built) {
            sb.append(b).append('\n');
        }
        return sha256(sb.toString());
    }

    private static void addMoleculeVersions(TreeSet<String> versions, Molecule m, Registries reg) {
        for (Molecule.Atom a : m.atoms()) {
            var e = reg.elements.get(a.element());
            if (e != null) {
                versions.add("element " + a.element() + "=" + e.version());
            }
        }
        for (Molecule.Bond b : m.bonds()) {
            var t = reg.bondTypes.get(b.type());
            if (t != null) {
                versions.add("bond " + b.type() + "=" + t.version());
            }
        }
    }

    private static String sortedIds(List<Id> ids) {
        return new TreeSet<>(ids).toString();
    }

    private static void molecule(StringBuilder sb, Molecule m) {
        for (Molecule.Atom a : m.canonicalAtoms()) {
            sb.append(a.element()).append('@').append(a.pos().q()).append(',').append(a.pos().r()).append(';');
        }
        sb.append('|');
        for (Molecule.Bond b : m.canonicalBonds()) {
            sb.append(b.type()).append(':').append(b.from().q()).append(',').append(b.from().r()).append('-')
                    .append(b.to().q()).append(',').append(b.to().r()).append(';');
        }
        sb.append('\n');
    }

    static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

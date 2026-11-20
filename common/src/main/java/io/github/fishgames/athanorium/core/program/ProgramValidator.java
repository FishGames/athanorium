package io.github.fishgames.athanorium.core.program;

import io.github.fishgames.athanorium.core.Rules;
import io.github.fishgames.athanorium.core.hex.Hex;
import io.github.fishgames.athanorium.core.hex.HexField;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.molecule.Molecule;
import io.github.fishgames.athanorium.core.part.InstructionType;
import io.github.fishgames.athanorium.core.part.PartType;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.registry.Registries;
import io.github.fishgames.athanorium.core.score.PartValues;
import io.github.fishgames.athanorium.core.sim.Tape;
import io.github.fishgames.athanorium.core.sim.TapeException;
import io.github.fishgames.athanorium.core.sim.TrackTable;
import io.github.fishgames.athanorium.core.sim.Vec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * The full program check. Run by the server before every
 * simulation and by the GUI while building. Returns all problems found; an empty list means the program may run.
 * Never reads packet limits or the sender; those belong to the network layer.
 */
public final class ProgramValidator {
    private ProgramValidator() {
    }

    public static List<ProgramError> validate(Program program, Puzzle puzzle, HexField field, Registries reg,
                                              PartValues values) {
        List<ProgramError> errors = new ArrayList<>();
        Set<Id> forbiddenParts = new HashSet<>(puzzle.forbiddenPartsOrEmpty());
        Set<Id> forbiddenInstr = new HashSet<>(puzzle.forbiddenInstructionsOrEmpty());

        Map<Long, Occupant> occupied = new HashMap<>();
        Set<Long> trackCells = new HashSet<>();
        boolean[] inputUsed = new boolean[puzzle.inputs().size()];
        boolean outputPlaced = false;
        List<Part> tracks = new ArrayList<>();
        List<Part> arms = new ArrayList<>();

        // first pass: types, ranges, footprints
        for (Part p : program.parts()) {
            String name = p.type() + " at " + p.pos();
            if (p.isUnknown()) {
                errors.add(new ProgramError("program.error.unknown_part", name));
                continue;
            }
            PartType pt = reg.parts.get(p.type());
            if (pt == null) {
                errors.add(new ProgramError("program.error.unknown_part", name));
                continue;
            }
            if (!inLimit(p.pos())) {
                errors.add(new ProgramError("program.error.coordinate_limit", name));
                continue;
            }
            if (forbiddenParts.contains(p.type()) && pt.kind() != PartType.Kind.INPUT
                    && pt.kind() != PartType.Kind.OUTPUT) {
                errors.add(new ProgramError("program.error.forbidden_part", name));
            }
            if (pt.purchasable() && !values.has(p.type())) {
                errors.add(new ProgramError("program.error.no_part_value", name));
            }
            if (pt.hasRotation() && (p.rotation() < 0 || p.rotation() > 5)) {
                errors.add(new ProgramError("program.error.rotation_range", name));
                continue;
            }
            List<Hex> cells = new ArrayList<>();
            switch (pt.kind()) {
                case INPUT -> {
                    int idx = p.inputIndex();
                    if (idx < 0 || idx >= inputUsed.length) {
                        errors.add(new ProgramError("program.error.input_index", name));
                        continue;
                    }
                    if (inputUsed[idx]) {
                        errors.add(new ProgramError("program.error.input_twice", name));
                    }
                    inputUsed[idx] = true;
                    for (Molecule.Atom a : puzzle.inputs().get(idx).atoms()) {
                        cells.add(p.pos().plus(a.pos().rotateCcw(p.rotation())));
                    }
                }
                case OUTPUT -> {
                    if (outputPlaced) {
                        errors.add(new ProgramError("program.error.output_twice", name));
                    }
                    outputPlaced = true;
                    for (Molecule.Atom a : puzzle.product().atoms()) {
                        cells.add(p.pos().plus(a.pos().rotateCcw(p.rotation())));
                    }
                }
                case TRACK -> {
                    if (!checkTrack(p, errors, name)) {
                        continue;
                    }
                    tracks.add(p);
                    for (Hex h : p.trackHexes()) {
                        Hex c = p.pos().plus(h);
                        cells.add(c);
                        trackCells.add(Vec.of(c).key());
                    }
                }
                case ARM -> {
                    if (p.length() < Rules.MIN_ARM_LENGTH || p.length() > Rules.MAX_ARM_LENGTH) {
                        errors.add(new ProgramError("program.error.arm_length", name));
                        continue;
                    }
                    arms.add(p);
                    cells.add(p.pos());
                    for (int d = 0; d < 6; d += pt.grabberStep()) {
                        Hex tip = p.pos().plus(Hex.dir(d + p.rotation()).times(p.length()));
                        if (!field.contains(tip)) {
                            errors.add(new ProgramError("program.error.grabber_outside_field", name));
                        }
                    }
                }
                case GLYPH -> {
                    cells.add(p.pos());
                    for (Hex h : pt.footprintBesidesOrigin()) {
                        cells.add(p.pos().plus(h.rotateCcw(p.rotation())));
                    }
                }
                default -> {
                }
            }
            for (Hex c : cells) {
                if (!field.contains(c)) {
                    errors.add(new ProgramError("program.error.outside_field", name + " -> " + c));
                    continue;
                }
                Occupant other = occupied.get(Vec.of(c).key());
                if (other == null) {
                    occupied.put(Vec.of(c).key(), new Occupant(name, pt.kind()));
                } else if (!(armOnTrack(other.kind(), pt.kind()))) {
                    errors.add(new ProgramError("program.error.overlap", name + " / " + other.name()));
                }
            }
        }

        TrackTable table = TrackTable.of(tracks);
        for (Part arm : arms) {
            String name = arm.type() + " at " + arm.pos();
            PartType pt = reg.parts.require(arm.type());
            NavigableMap<Integer, InstructionType.Kind> kinds = new TreeMap<>();
            boolean onTrack = trackCells.contains(Vec.of(arm.pos()).key());
            if (!arm.instructions().isEmpty()) {
                int last = arm.instructions().lastKey();
                if (last > Rules.MAX_TAPE_INDEX) {
                    errors.add(new ProgramError("program.error.tape_slot_limit", name));
                    continue;
                }
                if (puzzle.maxProgramLength() != null && last + 1 > puzzle.maxProgramLength()) {
                    errors.add(new ProgramError("program.error.program_too_long", name));
                }
            }
            boolean tapeOk = true;
            for (Map.Entry<Integer, Id> e : arm.instructions().entrySet()) {
                InstructionType it = reg.instructions.get(e.getValue());
                String where = name + " slot " + e.getKey();
                if (it == null) {
                    errors.add(new ProgramError("program.error.unknown_instruction", where));
                    tapeOk = false;
                    continue;
                }
                if (it.canBeForbidden() && forbiddenInstr.contains(it.id())) {
                    errors.add(new ProgramError("program.error.forbidden_instruction", where));
                }
                if ((it.kind() == InstructionType.Kind.EXTEND || it.kind() == InstructionType.Kind.RETRACT)
                        && !pt.isPiston()) {
                    errors.add(new ProgramError("program.error.instruction_needs_piston", where));
                }
                if ((it.kind() == InstructionType.Kind.TRACK_FORWARD || it.kind() == InstructionType.Kind.TRACK_BACK)
                        && !onTrack) {
                    errors.add(new ProgramError("program.error.instruction_needs_track", where));
                }
                kinds.put(e.getKey(), it.kind());
            }
            if (tapeOk) {
                try {
                    Tape.compile(kinds, Vec.of(arm.pos()), arm.length(), table);
                } catch (TapeException ex) {
                    errors.add(new ProgramError(ex.key(), name));
                }
            }
        }
        return errors;
    }

    private record Occupant(String name, PartType.Kind kind) {
    }

    /** An arm base may stand on a track hex; every other overlap is an error. */
    private static boolean armOnTrack(PartType.Kind a, PartType.Kind b) {
        return (a == PartType.Kind.ARM && b == PartType.Kind.TRACK) || (a == PartType.Kind.TRACK && b == PartType.Kind.ARM);
    }

    private static boolean inLimit(Hex h) {
        return Math.abs(h.q()) <= HexField.COORDINATE_LIMIT && Math.abs(h.r()) <= HexField.COORDINATE_LIMIT;
    }

    private static boolean checkTrack(Part p, List<ProgramError> errors, String name) {
        List<Hex> hexes = p.trackHexes();
        if (hexes.isEmpty()) {
            errors.add(new ProgramError("program.error.track_empty", name));
            return false;
        }
        boolean hasOrigin = false;
        Set<Hex> seen = new HashSet<>();
        for (int i = 0; i < hexes.size(); i++) {
            Hex h = hexes.get(i);
            if (h.equals(Hex.ZERO)) {
                hasOrigin = true;
            }
            if (!seen.add(h)) {
                errors.add(new ProgramError("program.error.track_duplicate", name + " -> " + h));
                return false;
            }
            if (i > 0 && Hex.directionOf(h.minus(hexes.get(i - 1))) < 0) {
                errors.add(new ProgramError("program.error.track_not_connected", name + " -> " + h));
                return false;
            }
            if (!inLimit(p.pos().plus(h))) {
                errors.add(new ProgramError("program.error.coordinate_limit", name));
                return false;
            }
        }
        if (!hasOrigin) {
            errors.add(new ProgramError("program.error.track_origin", name));
            return false;
        }
        return true;
    }
}

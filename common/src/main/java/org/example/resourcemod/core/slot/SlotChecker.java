package org.example.resourcemod.core.slot;

import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.part.PartType;
import org.example.resourcemod.core.program.Part;
import org.example.resourcemod.core.puzzle.Fingerprint;
import org.example.resourcemod.core.puzzle.Puzzle;
import org.example.resourcemod.core.registry.Registries;
import org.example.resourcemod.core.score.ScoreMetric;
import org.example.resourcemod.core.score.Scoring;

/**
 * Determines the validity of a confirmed state in three steps:
 * prerequisites, then comparison of the checksums, and only if the solvability checksum differs a new
 * computation. Never changes the confirmation.
 */
public final class SlotChecker {
    public static final String PUZZLE_MISSING = "slot.invalid.puzzle_missing";
    public static final String UNKNOWN_PART = "program.error.unknown_part";
    public static final String UNKNOWN_INSTRUCTION = "program.error.unknown_instruction";
    public static final String NO_PART_VALUE = "program.error.no_part_value";

    private SlotChecker() {
    }

    /** Outcome of a check. {@code needsComputation} means the state is "checking" until a new result exists. */
    public record Check(Validity validity, String reasonKey, String reasonDetail, boolean needsComputation) {
        static Check invalid(String key, String detail) {
            return new Check(Validity.INVALID, key, detail, false);
        }
    }

    /** Stage used for the field: a stored stage above the stage count maps to the last stage. */
    public static int effectiveStage(int stage, int stageCount) {
        return Math.max(1, Math.min(stage, stageCount));
    }

    public static HexField field(SlotContent content, int stageCount) {
        return HexField.forStage(effectiveStage(content.stage(), stageCount), stageCount);
    }

    public static Check check(SlotState state, Library library, Registries reg, int stageCount) {
        if (!state.confirmed()) {
            return new Check(Validity.UNCONFIRMED, "", "", false);
        }
        SlotContent c = state.content();
        Puzzle puzzle = library.puzzle(c.puzzle());
        if (puzzle == null) {
            return Check.invalid(PUZZLE_MISSING, String.valueOf(c.puzzle()));
        }
        for (Part p : c.program().parts()) {
            PartType pt = reg.parts.get(p.type());
            if (pt == null || p.isUnknown()) {
                return Check.invalid(UNKNOWN_PART, p.type().toString());
            }
            if (pt.purchasable() && !library.values().has(p.type())) {
                return Check.invalid(NO_PART_VALUE, p.type().toString());
            }
            for (Id inst : p.instructions().values()) {
                if (!reg.instructions.contains(inst)) {
                    return Check.invalid(UNKNOWN_INSTRUCTION, inst.toString());
                }
            }
        }
        CalcResult r = state.result();
        String solvability = Fingerprint.solvability(puzzle, c.program(), field(c, stageCount), reg);
        if (r == null || !solvability.equals(r.solvability())) {
            return new Check(Validity.CHECKING, "", "", true);
        }
        String scoring = Fingerprint.scoring(puzzle, c.program(), library.values());
        if (!scoring.equals(r.scoring())) {
            if (r.passed() && !r.metrics().containsKey(puzzle.metric())) {
                return new Check(Validity.CHECKING, "", "", true);
            }
            // only values or the reference changed: no simulation, the part value follows the current values
            state.setResult(r.passed()
                    ? r.withMetric(ScoreMetric.PART_VALUE, Scoring.partValue(c.program(), library.values()), scoring)
                    : new CalcResult(false, r.failKey(), r.failDetail(), r.metrics(), r.solvability(), scoring));
            r = state.result();
        }
        return r.passed() ? new Check(Validity.VALID, "", "", false) : Check.invalid(r.failKey(), r.failDetail());
    }
}

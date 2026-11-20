package io.github.fishgames.athanorium.core.slot;

import io.github.fishgames.athanorium.core.hex.HexField;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.program.Program;
import io.github.fishgames.athanorium.core.program.ProgramError;
import io.github.fishgames.athanorium.core.program.ProgramValidator;
import io.github.fishgames.athanorium.core.puzzle.Fingerprint;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.registry.Registries;
import io.github.fishgames.athanorium.core.score.Measurements;
import io.github.fishgames.athanorium.core.score.PartValues;
import io.github.fishgames.athanorium.core.score.ScoreMetric;
import io.github.fishgames.athanorium.core.score.Scoring;
import io.github.fishgames.athanorium.core.sim.SimError;
import io.github.fishgames.athanorium.core.sim.SimResult;
import io.github.fishgames.athanorium.core.sim.Simulation;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One server-side computation of a program: the full program check, then the simulation up to a cycle limit.
 * It works on an immutable snapshot and can be advanced in slices, so a long re-computation never blocks a
 * waiting submission.
 */
public final class Computation {
    public static final String CYCLE_MISMATCH = "slot.reject.cycle_mismatch";

    private final Puzzle puzzle;
    private final Program program;
    private final Registries reg;
    private final PartValues values;
    private final int cycleLimit;
    private final int expectedCycles;
    private final String solvability;
    private final String scoring;
    private Simulation sim;
    private CalcResult result;

    /**
     * @param cycleLimit     the simulation never runs longer than this
     * @param expectedCycles cycles reported by the client, or -1 for a re-computation
     */
    public Computation(Puzzle puzzle, Program program, HexField field, Registries reg, PartValues values,
                       int cycleLimit, int expectedCycles) {
        this.puzzle = puzzle;
        this.program = program;
        this.reg = reg;
        this.values = values;
        this.cycleLimit = cycleLimit;
        this.expectedCycles = expectedCycles;
        this.solvability = Fingerprint.solvability(puzzle, program, field, reg);
        this.scoring = Fingerprint.scoring(puzzle, program, values);
        List<ProgramError> errors = ProgramValidator.validate(program, puzzle, field, reg, values);
        if (!errors.isEmpty()) {
            result = CalcResult.failed(errors.get(0).key(), errors.get(0).detail(), solvability, scoring);
        } else {
            sim = new Simulation(puzzle, program, field, reg);
        }
    }

    /** Advances by at most {@code cycles} cycles. Returns true when the result is ready. */
    public boolean advance(int cycles) {
        if (result != null) {
            return true;
        }
        int target = (int) Math.min((long) sim.cycle() + cycles, cycleLimit);
        SimResult r = sim.run(target);
        if (r.solved()) {
            if (expectedCycles >= 0 && r.cycles() != expectedCycles) {
                result = CalcResult.failed(CYCLE_MISMATCH, r.cycles() + " / " + expectedCycles, solvability, scoring);
                return true;
            }
            Measurements m = Scoring.measure(r, program, values);
            Map<Id, Long> metrics = new TreeMap<>();
            for (ScoreMetric metric : reg.metrics.values()) {
                metrics.put(metric.id(), metric.value(m));
            }
            result = new CalcResult(true, "", "", metrics, solvability, scoring);
            return true;
        }
        boolean limitOnly = r.error() != null && SimError.CYCLE_LIMIT.equals(r.error().key());
        if (limitOnly && sim.cycle() < cycleLimit) {
            return false; // only the slice ended, not the run
        }
        SimError e = r.error();
        String detail = e == null ? "" : "cycle " + e.cycle() + (e.position() == null ? "" : " at " + e.position());
        result = CalcResult.failed(e == null ? SimError.CYCLE_LIMIT : e.key(), detail, solvability, scoring);
        return true;
    }

    public CalcResult result() {
        return result;
    }

    public CalcResult runToEnd() {
        while (!advance(1000)) {
            // keep going
        }
        return result;
    }

    public Puzzle puzzle() {
        return puzzle;
    }
}

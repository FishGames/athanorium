package io.github.fishgames.athanorium.core.score;

import io.github.fishgames.athanorium.core.program.Part;
import io.github.fishgames.athanorium.core.program.Program;
import io.github.fishgames.athanorium.core.sim.SimResult;

/** Builds the measurements of a solved run. */
public final class Scoring {
    private Scoring() {
    }

    public static long partValue(Program program, PartValues values) {
        long sum = 0;
        for (Part p : program.parts()) {
            sum += values.valueOf(p);
        }
        return sum;
    }

    public static Measurements measure(SimResult result, Program program, PartValues values) {
        return new Measurements(result.cycles(), partValue(program, values), result.area(), result.instructions());
    }
}

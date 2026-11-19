package org.example.resourcemod.core.score;

import org.example.resourcemod.core.program.Part;
import org.example.resourcemod.core.program.Program;
import org.example.resourcemod.core.sim.SimResult;

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

package io.github.fishgames.athanorium.core.sim;

/**
 * Result of a run. When {@code solved} the numbers are valid: cycles until the required products were made,
 * area (fields used) and the instruction count. Otherwise {@code error} says why the run ended.
 */
public record SimResult(boolean solved, int cycles, long area, long instructions, SimError error) {
    public static SimResult solved(int cycles, long area, long instructions) {
        return new SimResult(true, cycles, area, instructions, null);
    }

    public static SimResult failed(SimError error, long instructions) {
        return new SimResult(false, error.cycle(), 0, instructions, error);
    }
}

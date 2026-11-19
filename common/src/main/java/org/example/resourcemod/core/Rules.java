package org.example.resourcemod.core;

/**
 * Fixed constants that belong to the simulation rules (not configurable).
 * Every change that can alter a simulation result must raise {@link #VERSION}.
 */
public final class Rules {
    /** Version of the simulation rules. Part of the solvability checksum. */
    public static final int VERSION = 1;

    /** Highest tape slot an instruction may occupy. */
    public static final int MAX_TAPE_INDEX = 999_999;

    /** Test products if the puzzle does not say otherwise. */
    public static final int DEFAULT_TEST_PRODUCTS = 6;

    /** Cycle limit if the puzzle does not say otherwise. */
    public static final int DEFAULT_MAX_CYCLES = 100_000;

    /** Arm length range. */
    public static final int MIN_ARM_LENGTH = 1;
    public static final int MAX_ARM_LENGTH = 3;

    private Rules() {
    }
}

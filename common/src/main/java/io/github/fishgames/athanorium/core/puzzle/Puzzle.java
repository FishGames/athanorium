package io.github.fishgames.athanorium.core.puzzle;

import io.github.fishgames.athanorium.core.Rules;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.molecule.Molecule;

import java.util.List;

/**
 * A puzzle definition as read from JSON. Optional fields are null when they were omitted in the file, so that
 * an editor can write the file back unchanged (a later change of a default value then still applies).
 *
 * @param format           file format number
 * @param name             translation key of the display name or null
 * @param nameFallback     readable fallback text or null
 * @param inputs           input molecules; the order matters because solutions address inputs by index
 * @param product          the output molecule
 * @param forbiddenParts   forbidden part ids or null (nothing forbidden)
 * @param forbiddenInstructions forbidden instruction ids or null
 * @param metric           id of the score metric
 * @param reference        reference value for the metric (greater than 0)
 * @param testProducts     products in the test run or null (default {@link Rules#DEFAULT_TEST_PRODUCTS})
 * @param maxProgramLength maximum tape length or null (unlimited)
 * @param maxCycles        cycle limit for re-computation or null (default {@link Rules#DEFAULT_MAX_CYCLES})
 */
public record Puzzle(int format, String name, String nameFallback, List<Molecule> inputs, Molecule product,
                     List<Id> forbiddenParts, List<Id> forbiddenInstructions, Id metric, double reference,
                     Integer testProducts, Integer maxProgramLength, Integer maxCycles) {
    public static final int CURRENT_FORMAT = 1;

    public Puzzle {
        inputs = List.copyOf(inputs);
        forbiddenParts = forbiddenParts == null ? null : List.copyOf(forbiddenParts);
        forbiddenInstructions = forbiddenInstructions == null ? null : List.copyOf(forbiddenInstructions);
    }

    public int effectiveTestProducts() {
        return testProducts != null ? testProducts : Rules.DEFAULT_TEST_PRODUCTS;
    }

    public int effectiveMaxCycles() {
        return maxCycles != null ? maxCycles : Rules.DEFAULT_MAX_CYCLES;
    }

    public List<Id> forbiddenPartsOrEmpty() {
        return forbiddenParts == null ? List.of() : forbiddenParts;
    }

    public List<Id> forbiddenInstructionsOrEmpty() {
        return forbiddenInstructions == null ? List.of() : forbiddenInstructions;
    }
}

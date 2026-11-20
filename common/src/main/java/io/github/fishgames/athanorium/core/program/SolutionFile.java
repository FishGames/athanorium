package io.github.fishgames.athanorium.core.program;

import io.github.fishgames.athanorium.core.hex.HexField;
import io.github.fishgames.athanorium.core.id.Id;

import java.util.Map;

/**
 * A reference solution file: program plus the field radius it was solved on, the versions of the rules and
 * types the expected values were computed with, and the expected measurements.
 */
public record SolutionFile(int format, Id puzzle, HexField field, int rulesVersion, Map<Id, Integer> typeVersions,
                           Map<Id, Long> expected, Program program) {
    public static final int CURRENT_FORMAT = 1;

    public SolutionFile {
        typeVersions = Map.copyOf(typeVersions);
        expected = Map.copyOf(expected);
    }
}

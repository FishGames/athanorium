package org.example.resourcemod.core.score;

import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.registry.Registered;

import java.util.function.ToLongFunction;

/**
 * A metric a puzzle can use to rate a solution. Registered, so addons can add more (purity, symmetry, ...).
 * Each metric knows its direction: for cycles lower is better, a later "purity" would be higher is better.
 */
public record ScoreMetric(Id id, int version, boolean lowerIsBetter, ToLongFunction<Measurements> extractor)
        implements Registered {
    public static final Id CYCLES = Id.of("cycles");
    public static final Id PART_VALUE = Id.of("part_value");
    public static final Id AREA = Id.of("area");
    public static final Id INSTRUCTIONS = Id.of("instructions");

    public long value(Measurements m) {
        return extractor.applyAsLong(m);
    }
}

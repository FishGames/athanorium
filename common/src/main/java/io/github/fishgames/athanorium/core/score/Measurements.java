package io.github.fishgames.athanorium.core.score;

/**
 * Raw numbers of one simulation run. Every registered {@link ScoreMetric} reads its value from here.
 * Values are plain integers, so results are exactly reproducible.
 */
public record Measurements(long cycles, long partValue, long area, long instructions) {
}

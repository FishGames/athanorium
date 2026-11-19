package org.example.resourcemod.core.score;

/**
 * Build value of one part type (data/<namespace>/part_value/<part>.json). Tracks use perHex = true,
 * which multiplies the value by the number of track hexes.
 */
public record PartValue(long value, boolean perHex) {
}

package io.github.fishgames.athanorium.core.sim;

import io.github.fishgames.athanorium.core.hex.Hex;

/**
 * Why a simulation stopped without finishing: a translation key (game texts come only from language files),
 * the cycle and the position on the field. {@code position} is null if the error has no place.
 */
public record SimError(String key, int cycle, Hex position) {
    public static final String COLLISION = "sim.error.collision";
    public static final String OVERLAP = "sim.error.overlap";
    public static final String MOVED_TWO_WAYS = "sim.error.atom_moved_in_two_directions";
    public static final String HELD_STATIONARY = "sim.error.atom_moved_while_held";
    public static final String NOT_A_PISTON = "sim.error.not_a_piston";
    public static final String NOT_ON_TRACK = "sim.error.not_on_track";
    public static final String OUTSIDE_FIELD = "sim.error.grabber_outside_field";
    public static final String CYCLE_LIMIT = "sim.error.cycle_limit";
}

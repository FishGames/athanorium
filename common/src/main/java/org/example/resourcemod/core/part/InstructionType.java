package org.example.resourcemod.core.part;

import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.registry.Registered;

/**
 * An arm instruction. Wait, repeat and reset can never be forbidden by a puzzle.
 * <p>
 * Behaviour of the built-in kinds is implemented by the simulation; new kinds from addons would add a
 * {@link Kind} handler there (this is the only place where extension needs a code hook, not just a registration).
 */
public record InstructionType(Id id, int version, Kind kind) implements Registered {
    public enum Kind {
        GRAB, DROP, ROTATE_CCW, ROTATE_CW, PIVOT_CCW, PIVOT_CW, EXTEND, RETRACT, TRACK_FORWARD, TRACK_BACK,
        WAIT, REPEAT, RESET
    }

    public static final Id GRAB = Id.of("grab");
    public static final Id DROP = Id.of("drop");
    public static final Id ROTATE_CCW = Id.of("rotate_ccw");
    public static final Id ROTATE_CW = Id.of("rotate_cw");
    public static final Id PIVOT_CCW = Id.of("pivot_ccw");
    public static final Id PIVOT_CW = Id.of("pivot_cw");
    public static final Id EXTEND = Id.of("extend");
    public static final Id RETRACT = Id.of("retract");
    public static final Id TRACK_FORWARD = Id.of("track_forward");
    public static final Id TRACK_BACK = Id.of("track_back");
    public static final Id WAIT = Id.of("wait");
    public static final Id REPEAT = Id.of("repeat");
    public static final Id RESET = Id.of("reset");

    public boolean canBeForbidden() {
        return kind != Kind.WAIT && kind != Kind.REPEAT && kind != Kind.RESET;
    }

    /** Grab and drop happen in the first half-cycle, everything else in the second. */
    public boolean firstHalf() {
        return kind == Kind.GRAB || kind == Kind.DROP;
    }
}

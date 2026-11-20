package io.github.fishgames.athanorium.core.slot;

/** State of a slot state at the moment it is checked; never stored. */
public enum Validity {
    UNCONFIRMED, VALID, INVALID, CHECKING
}

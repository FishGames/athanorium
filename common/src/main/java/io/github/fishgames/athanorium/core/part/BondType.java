package io.github.fishgames.athanorium.core.part;

import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.registry.Registered;

/**
 * Kind of bond between two neighbouring atoms. Only the normal bond exists so far; more types must fit in without rebuilding the core.
 * <p>
 * The index selects the group of 6 direction bits inside an atom's bond word, so at most 10 types fit.
 * A bond of one type in a direction blocks adding a bond of another type in the same direction.
 */
public record BondType(Id id, int version, int index) implements Registered {
    public static final Id NORMAL = Id.of("normal");
    public static final int MAX_TYPES = 10;
}

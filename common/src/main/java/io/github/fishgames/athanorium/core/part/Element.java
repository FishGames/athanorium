package io.github.fishgames.athanorium.core.part;

import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.registry.Registered;

/** An element an atom can have. The four basic elements are built in; addons can register more. */
public record Element(Id id, int version) implements Registered {
    public static final Id AIR = Id.of("air");
    public static final Id EARTH = Id.of("earth");
    public static final Id FIRE = Id.of("fire");
    public static final Id WATER = Id.of("water");
}

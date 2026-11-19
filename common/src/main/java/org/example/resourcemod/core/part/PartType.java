package org.example.resourcemod.core.part;

import org.example.resourcemod.core.hex.Hex;
import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.registry.Registered;

import java.util.List;
import java.util.Set;

/**
 * Type of a part that can be placed on the field: input, output, track, arms and glyphs.
 * Footprints are given for rotation 0 and are rotated with the part.
 */
public final class PartType implements Registered {
    public enum Kind { INPUT, OUTPUT, TRACK, ARM, GLYPH }

    /** Effect of the built-in glyphs; addon glyphs would add a handler in the simulation. */
    public enum GlyphEffect { NONE, BIND, MULTI_BIND, UNBIND, DISPOSER }

    public static final Id INPUT = Id.of("input");
    public static final Id OUTPUT = Id.of("output");
    public static final Id TRACK = Id.of("track");
    public static final Id ARM = Id.of("arm");
    public static final Id DOUBLE_ARM = Id.of("double_arm");
    public static final Id TRIPLE_ARM = Id.of("triple_arm");
    public static final Id HEX_ARM = Id.of("hex_arm");
    public static final Id PISTON_ARM = Id.of("piston_arm");
    public static final Id BIND = Id.of("bind");
    public static final Id MULTI_BIND = Id.of("multi_bind");
    public static final Id UNBIND = Id.of("unbind");
    public static final Id DISPOSER = Id.of("disposer");

    private final Id id;
    private final int version;
    private final Kind kind;
    private final boolean hasRotation;
    private final boolean purchasable;
    private final int grabberStep;
    private final boolean piston;
    private final List<Hex> footprint;
    private final GlyphEffect effect;
    private final Set<String> extraFields;

    private PartType(Id id, int version, Kind kind, boolean hasRotation, boolean purchasable, int grabberStep,
                     boolean piston, List<Hex> footprint, GlyphEffect effect, Set<String> extraFields) {
        this.id = id;
        this.version = version;
        this.kind = kind;
        this.hasRotation = hasRotation;
        this.purchasable = purchasable;
        this.grabberStep = grabberStep;
        this.piston = piston;
        this.footprint = List.copyOf(footprint);
        this.effect = effect;
        this.extraFields = Set.copyOf(extraFields);
    }

    public static PartType input(Id id, int version) {
        return new PartType(id, version, Kind.INPUT, true, false, 0, false, List.of(), GlyphEffect.NONE, Set.of("index"));
    }

    public static PartType output(Id id, int version) {
        return new PartType(id, version, Kind.OUTPUT, true, false, 0, false, List.of(), GlyphEffect.NONE, Set.of());
    }

    public static PartType track(Id id, int version) {
        return new PartType(id, version, Kind.TRACK, false, true, 0, false, List.of(), GlyphEffect.NONE, Set.of("hexes"));
    }

    /**
     * Arm. grabberStep is the angular distance between grabbers in 60 degree steps (6 = one grabber,
     * 3 = two, 2 = three, 1 = six). A piston arm can extend and retract between lengths 1 and 3.
     */
    public static PartType arm(Id id, int version, int grabberStep, boolean piston) {
        return new PartType(id, version, Kind.ARM, true, true, grabberStep, piston, List.of(), GlyphEffect.NONE,
                Set.of("length", "instructions"));
    }

    /** Glyph with the given footprint (hexes besides the origin, rotation 0). The origin [0,0] is always part of it. */
    public static PartType glyph(Id id, int version, List<Hex> footprintBesidesOrigin, GlyphEffect effect) {
        return new PartType(id, version, Kind.GLYPH, true, true, 0, false, footprintBesidesOrigin, effect, Set.of());
    }

    @Override
    public Id id() {
        return id;
    }

    @Override
    public int version() {
        return version;
    }

    public Kind kind() {
        return kind;
    }

    /** Whether the part has a direction (and therefore a mandatory "rotation" field). */
    public boolean hasRotation() {
        return hasRotation;
    }

    /** Purchasable parts need a part value file; inputs and outputs are not purchasable. */
    public boolean purchasable() {
        return purchasable;
    }

    public int grabberStep() {
        return grabberStep;
    }

    public boolean isPiston() {
        return piston;
    }

    /** Number of grabbers of an arm. */
    public int grabberCount() {
        return 6 / grabberStep;
    }

    /** Hexes a glyph occupies at rotation 0, besides its origin. */
    public List<Hex> footprintBesidesOrigin() {
        return footprint;
    }

    public GlyphEffect glyphEffect() {
        return effect;
    }

    /** Type-specific fields besides type, pos and rotation. */
    public Set<String> extraFields() {
        return extraFields;
    }
}

package io.github.fishgames.athanorium.core.registry;

import io.github.fishgames.athanorium.core.hex.Hex;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.part.BondType;
import io.github.fishgames.athanorium.core.part.Element;
import io.github.fishgames.athanorium.core.part.InstructionType;
import io.github.fishgames.athanorium.core.part.PartType;
import io.github.fishgames.athanorium.core.score.Measurements;
import io.github.fishgames.athanorium.core.score.ScoreMetric;

import java.util.List;

/**
 * All registries the core needs. Elements, bond types, parts, instructions and metrics are registered, never a
 * fixed enumeration, so new types can come later without rebuilding the core.
 */
public final class Registries {
    public final Registry<Element> elements = new Registry<>("element");
    public final Registry<BondType> bondTypes = new Registry<>("bond type");
    public final Registry<PartType> parts = new Registry<>("part");
    public final Registry<InstructionType> instructions = new Registry<>("instruction");
    public final Registry<ScoreMetric> metrics = new Registry<>("metric");

    public BondType registerBondType(Id id, int version) {
        if (bondTypes.size() >= BondType.MAX_TYPES) {
            throw new IllegalStateException("Too many bond types");
        }
        return bondTypes.register(new BondType(id, version, bondTypes.size()));
    }

    /** Registries with everything that exists so far. */
    public static Registries createDefault() {
        Registries r = new Registries();

        r.elements.register(new Element(Element.AIR, 1));
        r.elements.register(new Element(Element.EARTH, 1));
        r.elements.register(new Element(Element.FIRE, 1));
        r.elements.register(new Element(Element.WATER, 1));

        r.registerBondType(BondType.NORMAL, 1);

        r.parts.register(PartType.input(PartType.INPUT, 1));
        r.parts.register(PartType.output(PartType.OUTPUT, 1));
        r.parts.register(PartType.track(PartType.TRACK, 1));
        r.parts.register(PartType.arm(PartType.ARM, 1, 6, false));
        r.parts.register(PartType.arm(PartType.DOUBLE_ARM, 1, 3, false));
        r.parts.register(PartType.arm(PartType.TRIPLE_ARM, 1, 2, false));
        r.parts.register(PartType.arm(PartType.HEX_ARM, 1, 1, false));
        r.parts.register(PartType.arm(PartType.PISTON_ARM, 1, 6, true));
        // Glyph footprints at rotation 0, listed as unit directions (relative to the origin).
        r.parts.register(PartType.glyph(PartType.BIND, 1, List.of(Hex.dir(0)), PartType.GlyphEffect.BIND));
        r.parts.register(PartType.glyph(PartType.MULTI_BIND, 1, List.of(Hex.dir(0), Hex.dir(2), Hex.dir(4)),
                PartType.GlyphEffect.MULTI_BIND));
        r.parts.register(PartType.glyph(PartType.UNBIND, 1, List.of(Hex.dir(0)), PartType.GlyphEffect.UNBIND));
        r.parts.register(PartType.glyph(PartType.DISPOSER, 1,
                List.of(Hex.dir(0), Hex.dir(1), Hex.dir(2), Hex.dir(3), Hex.dir(4), Hex.dir(5)),
                PartType.GlyphEffect.DISPOSER));

        for (var entry : new Object[][]{
                {InstructionType.GRAB, InstructionType.Kind.GRAB},
                {InstructionType.DROP, InstructionType.Kind.DROP},
                {InstructionType.ROTATE_CCW, InstructionType.Kind.ROTATE_CCW},
                {InstructionType.ROTATE_CW, InstructionType.Kind.ROTATE_CW},
                {InstructionType.PIVOT_CCW, InstructionType.Kind.PIVOT_CCW},
                {InstructionType.PIVOT_CW, InstructionType.Kind.PIVOT_CW},
                {InstructionType.EXTEND, InstructionType.Kind.EXTEND},
                {InstructionType.RETRACT, InstructionType.Kind.RETRACT},
                {InstructionType.TRACK_FORWARD, InstructionType.Kind.TRACK_FORWARD},
                {InstructionType.TRACK_BACK, InstructionType.Kind.TRACK_BACK},
                {InstructionType.WAIT, InstructionType.Kind.WAIT},
                {InstructionType.REPEAT, InstructionType.Kind.REPEAT},
                {InstructionType.RESET, InstructionType.Kind.RESET}}) {
            r.instructions.register(new InstructionType((Id) entry[0], 1, (InstructionType.Kind) entry[1]));
        }

        r.metrics.register(new ScoreMetric(ScoreMetric.CYCLES, 1, true, Measurements::cycles));
        r.metrics.register(new ScoreMetric(ScoreMetric.PART_VALUE, 1, true, Measurements::partValue));
        r.metrics.register(new ScoreMetric(ScoreMetric.AREA, 1, true, Measurements::area));
        r.metrics.register(new ScoreMetric(ScoreMetric.INSTRUCTIONS, 1, true, Measurements::instructions));
        return r;
    }
}

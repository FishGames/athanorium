package io.github.fishgames.athanorium.core.program;

import io.github.fishgames.athanorium.core.hex.Hex;
import io.github.fishgames.athanorium.core.id.Id;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * One part of a program (input, output, track, arm or glyph). Immutable.
 * <p>
 * Fields that a part type does not use keep their neutral value: rotation -1, inputIndex -1, length 0,
 * no track hexes, no instructions. Parts of an unregistered type keep all their fields unchecked and
 * unchanged in {@link #raw()}.
 */
public final class Part {
    private final Id type;
    private final Hex pos;
    private final int rotation;
    private final int inputIndex;
    private final int length;
    private final List<Hex> trackHexes;
    private final NavigableMap<Integer, Id> instructions;
    private final Map<String, Object> raw;

    private Part(Id type, Hex pos, int rotation, int inputIndex, int length, List<Hex> trackHexes,
                 NavigableMap<Integer, Id> instructions, Map<String, Object> raw) {
        this.type = type;
        this.pos = pos;
        this.rotation = rotation;
        this.inputIndex = inputIndex;
        this.length = length;
        this.trackHexes = List.copyOf(trackHexes);
        this.instructions = Collections.unmodifiableNavigableMap(new TreeMap<>(instructions));
        this.raw = raw == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    public static Part input(Id type, Hex pos, int rotation, int index) {
        return new Part(type, pos, rotation, index, 0, List.of(), new TreeMap<>(), null);
    }

    public static Part output(Id type, Hex pos, int rotation) {
        return new Part(type, pos, rotation, -1, 0, List.of(), new TreeMap<>(), null);
    }

    public static Part track(Id type, Hex pos, List<Hex> hexes) {
        return new Part(type, pos, -1, -1, 0, hexes, new TreeMap<>(), null);
    }

    public static Part arm(Id type, Hex pos, int rotation, int length, Map<Integer, Id> instructions) {
        return new Part(type, pos, rotation, -1, length, List.of(), new TreeMap<>(instructions), null);
    }

    public static Part glyph(Id type, Hex pos, int rotation) {
        return new Part(type, pos, rotation, -1, 0, List.of(), new TreeMap<>(), null);
    }

    /** Part of a type that is not registered; all its other fields are passed through unchanged. */
    public static Part unknown(Id type, Hex pos, Map<String, Object> raw) {
        return new Part(type, pos, -1, -1, 0, List.of(), new TreeMap<>(), raw);
    }

    public Id type() {
        return type;
    }

    public Hex pos() {
        return pos;
    }

    public int rotation() {
        return rotation;
    }

    public int inputIndex() {
        return inputIndex;
    }

    public int length() {
        return length;
    }

    public List<Hex> trackHexes() {
        return trackHexes;
    }

    /** Instruction id per tape slot, sorted by slot. */
    public NavigableMap<Integer, Id> instructions() {
        return instructions;
    }

    /** Unchecked fields of a part with unregistered type, null for all registered types. */
    public Map<String, Object> raw() {
        return raw;
    }

    public boolean isUnknown() {
        return raw != null;
    }

    public Part withPos(Hex newPos) {
        return new Part(type, newPos, rotation, inputIndex, length, trackHexes, instructions, raw);
    }

    public Part withRotation(int newRotation) {
        return new Part(type, pos, newRotation, inputIndex, length, trackHexes, instructions, raw);
    }

    public Part withLength(int newLength) {
        return new Part(type, pos, rotation, inputIndex, newLength, trackHexes, instructions, raw);
    }

    public Part withTrackHexes(List<Hex> hexes) {
        return new Part(type, pos, rotation, inputIndex, length, hexes, instructions, raw);
    }

    public Part withInstructions(Map<Integer, Id> newInstructions) {
        return new Part(type, pos, rotation, inputIndex, length, trackHexes, new TreeMap<>(newInstructions), raw);
    }

    /** Builds a part from all its fields; used by the compact codec. */
    public static Part ofFields(Id type, Hex pos, int rotation, int inputIndex, int length, List<Hex> trackHexes,
                                Map<Integer, Id> instructions) {
        return new Part(type, pos, rotation, inputIndex, length, trackHexes, new TreeMap<>(instructions), null);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Part p)) {
            return false;
        }
        return type.equals(p.type) && pos.equals(p.pos) && rotation == p.rotation && inputIndex == p.inputIndex
                && length == p.length && trackHexes.equals(p.trackHexes) && instructions.equals(p.instructions)
                && java.util.Objects.equals(raw, p.raw);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, pos, rotation, inputIndex, length, trackHexes, instructions, raw);
    }

    @Override
    public String toString() {
        return type + "@" + pos;
    }
}

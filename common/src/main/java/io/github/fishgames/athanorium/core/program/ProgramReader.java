package io.github.fishgames.athanorium.core.program;

import io.github.fishgames.athanorium.core.hex.Hex;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.json.DataException;
import io.github.fishgames.athanorium.core.json.Fields;
import io.github.fishgames.athanorium.core.part.PartType;
import io.github.fishgames.athanorium.core.registry.Registries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Reads the "parts" list of a solution. Fixed fields (type, pos, rotation) are checked here, type specific
 * fields (index, length, hexes, instructions) by the registered type. Parts of an unregistered type pass through
 * unchecked and unchanged. Value ranges (rotation 0..5, arm length 1..3, ...) belong to the program check
 * ({@link ProgramValidator}), not to reading, so the GUI can still show and edit such programs.
 */
public final class ProgramReader {
    private static final Pattern SLOT = Pattern.compile("0|[1-9][0-9]{0,8}");
    private static final int LIMIT = 1_000_000;

    private ProgramReader() {
    }

    public static Program read(List<Fields> partFields, Registries reg) {
        List<Part> parts = new ArrayList<>();
        for (Fields f : partFields) {
            parts.add(readPart(f, reg));
        }
        return new Program(parts);
    }

    public static Part readPart(Fields f, Registries reg) {
        Id type = f.reqId("type");
        Hex pos = f.reqHex("pos");
        PartType pt = reg.parts.get(type);
        if (pt == null) {
            Map<String, Object> raw = new LinkedHashMap<>();
            for (String key : new ArrayList<>(f.keys())) {
                if (!key.equals("type") && !key.equals("pos")) {
                    raw.put(key, f.raw(key));
                }
            }
            return Part.unknown(type, pos, raw);
        }
        int rotation = pt.hasRotation() ? f.reqInt("rotation", -LIMIT, LIMIT) : -1;
        Part part;
        switch (pt.kind()) {
            case INPUT -> {
                int index = f.reqInt("index", -LIMIT, LIMIT);
                part = Part.input(type, pos, rotation, index);
            }
            case OUTPUT -> part = Part.output(type, pos, rotation);
            case TRACK -> {
                List<Object> raw = f.reqList("hexes");
                List<Hex> hexes = new ArrayList<>();
                for (int i = 0; i < raw.size(); i++) {
                    hexes.add(f.toHex(raw.get(i), f.childPath("hexes") + "[" + i + "]"));
                }
                part = Part.track(type, pos, hexes);
            }
            case ARM -> {
                int length = f.reqInt("length", -LIMIT, LIMIT);
                Fields inst = f.optObject("instructions");
                Map<Integer, Id> tape = new TreeMap<>();
                if (inst != null) {
                    for (String key : new ArrayList<>(inst.keys())) {
                        if (!SLOT.matcher(key).matches()) {
                            throw inst.error(key, "Tape slot must be a decimal number without sign or leading zeros");
                        }
                        tape.put(Integer.parseInt(key), inst.reqId(key));
                    }
                    inst.finish();
                }
                part = Part.arm(type, pos, rotation, length, tape);
            }
            case GLYPH -> part = Part.glyph(type, pos, rotation);
            default -> throw new DataException(f.file(), f.path(), "Unsupported part kind " + pt.kind());
        }
        f.finish();
        return part;
    }
}

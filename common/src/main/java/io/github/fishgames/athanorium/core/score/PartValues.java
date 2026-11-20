package io.github.fishgames.athanorium.core.score;

import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.json.Fields;
import io.github.fishgames.athanorium.core.json.StrictJson;
import io.github.fishgames.athanorium.core.program.Part;

import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Build values of all part types that have a part value file. */
public final class PartValues {
    private final Map<Id, PartValue> values = new LinkedHashMap<>();

    public PartValues put(Id part, PartValue value) {
        values.put(part, value);
        return this;
    }

    public PartValue get(Id part) {
        return values.get(part);
    }

    public boolean has(Id part) {
        return values.containsKey(part);
    }

    public Map<Id, PartValue> all() {
        return Collections.unmodifiableMap(values);
    }

    /** Value of one placed part; 0 if the type has no value entry. */
    public long valueOf(Part part) {
        PartValue v = values.get(part.type());
        if (v == null) {
            return 0;
        }
        return v.perHex() ? v.value() * part.trackHexes().size() : v.value();
    }

    /** Reads a part value file: { "value": 20 } or { "value": 5, "per_hex": true }. */
    public static PartValue read(Reader in, String fileName) {
        Fields f = Fields.of(StrictJson.parse(in, fileName), fileName);
        int value = f.reqInt("value", 0, Integer.MAX_VALUE);
        boolean perHex = f.optBoolean("per_hex", false);
        f.finish();
        return new PartValue(value, perHex);
    }
}

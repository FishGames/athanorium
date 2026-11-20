package io.github.fishgames.athanorium.core.sim;

import io.github.fishgames.athanorium.core.hex.Hex;
import io.github.fishgames.athanorium.core.program.Part;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Motion of an arm along tracks. "Plus" (instruction track forward) moves to the next hex in the order of the
 * track's hex list, "minus" (track back) to the previous one; at the ends of an open track the motion is zero.
 * A track of at least three hexes whose last hex neighbours the first is a closed ring.
 * Lookups lend only; nothing iterates this table, so hash order never matters.
 */
public final class TrackTable {
    private final Map<Long, Vec> plus = new HashMap<>();
    private final Map<Long, Vec> minus = new HashMap<>();

    public static TrackTable of(List<Part> tracks) {
        TrackTable t = new TrackTable();
        for (Part track : tracks) {
            t.add(track);
        }
        return t;
    }

    private void add(Part track) {
        List<Hex> hexes = track.trackHexes();
        int n = hexes.size();
        Vec[] p = new Vec[n];
        for (int i = 0; i < n; i++) {
            p[i] = Vec.of(track.pos().plus(hexes.get(i)));
        }
        for (int i = 0; i < n; i++) {
            plus.put(p[i].key(), i < n - 1 ? p[i + 1].minus(p[i]) : Vec.ZERO);
            minus.put(p[i].key(), i > 0 ? p[i - 1].minus(p[i]) : Vec.ZERO);
        }
        if (n >= 3 && Vec.directionOf(p[n - 1].minus(p[0])) >= 0) {
            plus.put(p[n - 1].key(), p[0].minus(p[n - 1]));
            minus.put(p[0].key(), p[n - 1].minus(p[0]));
        }
    }

    public boolean isTrack(Vec position) {
        return plus.containsKey(position.key());
    }

    /** Motion for "track forward" at the position, or null if there is no track. */
    public Vec plusMotion(Vec position) {
        return plus.get(position.key());
    }

    public Vec minusMotion(Vec position) {
        return minus.get(position.key());
    }
}

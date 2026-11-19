package org.example.resourcemod.core.hex;

/**
 * The playing field: either a hexagon of a given radius (stage 1 has radius 2, every further stage one more)
 * or unbounded. A bounded field of radius R has a wall ring of fixed fields at distance R + 1
 * (like the cabinet walls in Opus Magnum). Unbounded fields are still limited by the fixed coordinate limit.
 */
public final class HexField {
    /** Fixed limit for |q| and |r| in puzzles and solutions. Part of the simulation rules, not configurable. */
    public static final int COORDINATE_LIMIT = 1000;

    public static final HexField UNBOUNDED = new HexField(-1);

    private final int radius;

    private HexField(int radius) {
        this.radius = radius;
    }

    /** Bounded field. The radius must be between 2 and the coordinate limit. */
    public static HexField bounded(int radius) {
        if (radius < 2 || radius > COORDINATE_LIMIT) {
            throw new IllegalArgumentException("Field radius must be between 2 and " + COORDINATE_LIMIT + ": " + radius);
        }
        return new HexField(radius);
    }

    /** Radius of the field for a stage: stage 1 has 2, stage 2 has 3, and so on; the last stage is unbounded. */
    public static HexField forStage(int stage, int stageCount) {
        if (stageCount < 2) {
            throw new IllegalArgumentException("There must be at least 2 stages");
        }
        if (stage < 1 || stage > stageCount) {
            throw new IllegalArgumentException("Stage out of range 1.." + stageCount + ": " + stage);
        }
        return stage == stageCount ? UNBOUNDED : bounded(stage + 1);
    }

    public boolean isBounded() {
        return radius >= 0;
    }

    /** Radius of a bounded field. */
    public int radius() {
        if (!isBounded()) {
            throw new IllegalStateException("Field is unbounded");
        }
        return radius;
    }

    /** True if the hex is part of the field (for unbounded fields: inside the coordinate limit). */
    public boolean contains(Hex h) {
        if (isBounded()) {
            return h.length() <= radius;
        }
        return Math.abs(h.q()) <= COORDINATE_LIMIT && Math.abs(h.r()) <= COORDINATE_LIMIT;
    }

    /** True if the hex is on the wall ring of a bounded field. */
    public boolean isWall(Hex h) {
        return isBounded() && h.length() == radius + 1;
    }

    /** Hexes of the wall ring in a fixed order; empty for unbounded fields. */
    public java.util.List<Hex> wallHexes() {
        java.util.List<Hex> walls = new java.util.ArrayList<>();
        if (!isBounded()) {
            return walls;
        }
        int ring = radius + 1;
        Hex h = Hex.dir(4).times(ring); // start at a corner, walk around
        for (int side = 0; side < 6; side++) {
            for (int step = 0; step < ring; step++) {
                walls.add(h);
                h = h.neighbor(side);
            }
        }
        return walls;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HexField f && f.radius == radius;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(radius);
    }

    @Override
    public String toString() {
        return isBounded() ? "radius " + radius : "unbounded";
    }
}

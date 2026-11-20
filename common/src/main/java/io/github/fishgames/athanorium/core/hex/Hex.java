package io.github.fishgames.athanorium.core.hex;

/**
 * Axial hex coordinate [q, r], pointy-top hexagons with horizontal rows.
 * <p>
 * Definition of the hex coordinates:
 * <ul>
 *   <li>q counts to the right inside a row, r counts the rows downwards; every row is shifted half a hex to the right.</li>
 *   <li>Screen position at hex size s: x = s * sqrt(3) * (q + r/2), y = s * 1.5 * r.</li>
 *   <li>Directions 0..5, counter-clockwise starting at the right:
 *       [+1,0] right, [+1,-1] up-right, [0,-1] up-left, [-1,0] left, [-1,+1] down-left, [0,+1] down-right.</li>
 *   <li>Rotation by 60 degrees counter-clockwise: [q,r] becomes [q+r, -q]; clockwise: [q,r] becomes [-r, q+r].</li>
 *   <li>Distance: (|dq| + |dr| + |dq+dr|) / 2.</li>
 * </ul>
 * Everything is integer arithmetic; there is no floating point in this class.
 */
public record Hex(int q, int r) {
    public static final Hex ZERO = new Hex(0, 0);

    private static final Hex[] DIRECTIONS = {
            new Hex(1, 0), new Hex(1, -1), new Hex(0, -1), new Hex(-1, 0), new Hex(-1, 1), new Hex(0, 1)
    };

    /** Unit vector of direction d (any integer, taken modulo 6). */
    public static Hex dir(int d) {
        return DIRECTIONS[Math.floorMod(d, 6)];
    }

    /** Index 0..5 of the unit vector, or -1 if the vector is not a unit direction. */
    public static int directionOf(Hex delta) {
        for (int i = 0; i < 6; i++) {
            if (DIRECTIONS[i].equals(delta)) {
                return i;
            }
        }
        return -1;
    }

    public Hex plus(Hex o) {
        return new Hex(q + o.q, r + o.r);
    }

    public Hex minus(Hex o) {
        return new Hex(q - o.q, r - o.r);
    }

    public Hex negate() {
        return new Hex(-q, -r);
    }

    public Hex times(int k) {
        return new Hex(q * k, r * k);
    }

    public Hex neighbor(int direction) {
        return plus(dir(direction));
    }

    /** Rotates around [0,0] by 60 degrees * steps, counter-clockwise for positive steps. */
    public Hex rotateCcw(int steps) {
        int s = Math.floorMod(steps, 6);
        int nq = q;
        int nr = r;
        for (int i = 0; i < s; i++) {
            int tq = nq + nr;
            nr = -nq;
            nq = tq;
        }
        return new Hex(nq, nr);
    }

    public Hex rotateCw(int steps) {
        return rotateCcw(-steps);
    }

    /** Rotates around the given center. */
    public Hex rotateCcwAround(Hex center, int steps) {
        return minus(center).rotateCcw(steps).plus(center);
    }

    /** Distance to [0,0]. */
    public int length() {
        return (Math.abs(q) + Math.abs(r) + Math.abs(q + r)) / 2;
    }

    public int distanceTo(Hex o) {
        return minus(o).length();
    }

    /** Unique 64-bit key, usable in hash maps. */
    public long key() {
        return ((long) q << 32) | (r & 0xFFFFFFFFL);
    }

    @Override
    public String toString() {
        return "[" + q + "," + r + "]";
    }
}

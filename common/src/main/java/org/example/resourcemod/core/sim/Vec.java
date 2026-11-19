package org.example.resourcemod.core.sim;

import org.example.resourcemod.core.hex.Hex;

/**
 * Integer vector in the coordinates of the reference rules (omsim): u points right, v points up-right.
 * The simulation computes in these coordinates so the rules can be ported without conversion mistakes; the public
 * interface of the core uses {@link Hex} (axial q/r) and converts with q = u + v, r = -v (u = q + r, v = -r).
 */
public record Vec(int u, int v) {
    public static final Vec ZERO = new Vec(0, 0);

    public static Vec of(Hex h) {
        return new Vec(h.q() + h.r(), -h.r());
    }

    public Hex toHex() {
        return new Hex(u + v, -v);
    }

    public Vec plus(Vec o) {
        return new Vec(u + o.u, v + o.v);
    }

    public Vec minus(Vec o) {
        return new Vec(u - o.u, v - o.v);
    }

    public Vec times(int k) {
        return new Vec(u * k, v * k);
    }

    public boolean isZero() {
        return u == 0 && v == 0;
    }

    /** Hex distance to the origin. */
    public int hexLength() {
        return (Math.abs(u) + Math.abs(v) + Math.abs(u + v)) / 2;
    }

    public long key() {
        return ((long) u << 32) | (v & 0xFFFFFFFFL);
    }

    /** Offset of direction d (counter-clockwise from the right): (1,0) (0,1) (-1,1) (-1,0) (0,-1) (1,-1). */
    public static Vec dirU(int d) {
        switch (Math.floorMod(d, 6)) {
            case 0: return new Vec(1, 0);
            case 1: return new Vec(0, 1);
            case 2: return new Vec(-1, 1);
            case 3: return new Vec(-1, 0);
            case 4: return new Vec(0, -1);
            default: return new Vec(1, -1);
        }
    }

    public static Vec dirV(int d) {
        return dirU(d + 1);
    }

    /** Direction index 0..5 of a unit offset, or -1. */
    public static int directionOf(Vec d) {
        for (int i = 0; i < 6; i++) {
            if (dirU(i).equals(d)) {
                return i;
            }
        }
        return -1;
    }

    /** Sign of each component (-1, 0, 1). */
    public Vec normalizeAxis() {
        return new Vec(Integer.signum(u), Integer.signum(v));
    }

    @Override
    public String toString() {
        return "(" + u + "," + v + ")";
    }
}

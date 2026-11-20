package io.github.fishgames.athanorium.core.sim;

import io.github.fishgames.athanorium.core.id.Id;

/**
 * One atom on the board. Bonds are stored as direction bits: for the bond type with registry index t, bit
 * t * 6 + d means "bonded in direction d" (directions as in {@link Vec#dirU}). "Recent" bits mark bonds that were
 * dissolved in this half-cycle; such atoms neither move nor count as one molecule until the flags are reset.
 */
final class SimAtom {
    final Id element;
    long bonds;
    int recent;
    int grabbed;

    SimAtom(Id element, long bonds) {
        this.element = element;
        this.bonds = bonds;
    }

    static long bit(int typeIndex, int dir) {
        return 1L << (typeIndex * 6 + dir);
    }

    /** All bond bits (any type) in one direction. */
    static long dirMask(int dir) {
        long m = 0;
        for (int t = 0; t < 10; t++) {
            m |= bit(t, dir);
        }
        return m;
    }

    static int rotateGroup(int group, int rotation) {
        int r = Math.floorMod(rotation, 6);
        if (r == 0) {
            return group;
        }
        return ((group << r) | (group >> (6 - r))) & 63;
    }

    static long rotateBonds(long bonds, int rotation) {
        long out = 0;
        for (int t = 0; t < 10; t++) {
            int group = (int) ((bonds >> (t * 6)) & 63);
            out |= ((long) rotateGroup(group, rotation)) << (t * 6);
        }
        return out;
    }

    boolean hasBond(int dir) {
        return (bonds & dirMask(dir)) != 0;
    }
}

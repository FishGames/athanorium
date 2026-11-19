package org.example.resourcemod.core;

import org.example.resourcemod.core.hex.Hex;
import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.molecule.Molecule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.example.resourcemod.core.Fx.h;
import static org.junit.jupiter.api.Assertions.*;

class HexTest {
    @Test
    void directionsRunCounterClockwise() {
        assertEquals(h(1, 0), Hex.dir(0));
        assertEquals(h(1, -1), Hex.dir(1));
        assertEquals(h(0, -1), Hex.dir(2));
        assertEquals(h(-1, 0), Hex.dir(3));
        assertEquals(h(-1, 1), Hex.dir(4));
        assertEquals(h(0, 1), Hex.dir(5));
        assertEquals(Hex.dir(1), Hex.dir(7));
        assertEquals(Hex.dir(5), Hex.dir(-1));
    }

    @Test
    void rotationFormulasMatchTheDefinition() {
        assertEquals(h(3 + -2, -3), h(3, -2).rotateCcw(1)); // [q,r] becomes [q+r, -q]
        assertEquals(h(2, 3 + -2), h(3, -2).rotateCw(1)); // [q,r] becomes [-r, q+r]
        for (int d = 0; d < 6; d++) {
            assertEquals(Hex.dir(d + 1), Hex.dir(d).rotateCcw(1));
            assertEquals(Hex.dir(d - 1), Hex.dir(d).rotateCw(1));
        }
    }

    @Test
    void clockwiseWalkFromBottomRight() {
        // clockwise from the bottom right: [0,+1], [-1,+1] (q only), [-1,0] (r only), [0,-1] (both)
        Hex h = h(0, 1);
        h = h.rotateCw(1);
        assertEquals(h(-1, 1), h);
        h = h.rotateCw(1);
        assertEquals(h(-1, 0), h);
        h = h.rotateCw(1);
        assertEquals(h(0, -1), h);
    }

    @Test
    void sixRotationsRestoreHex() {
        for (int d = 0; d < 6; d++) {
            for (int len = 1; len <= 3; len++) {
                Hex tip = Hex.dir(d).times(len);
                Hex h = tip;
                for (int i = 0; i < 6; i++) {
                    h = h.rotateCcw(1);
                }
                assertEquals(tip, h);
                for (int i = 0; i < 6; i++) {
                    h = h.rotateCw(1);
                }
                assertEquals(tip, h);
                assertEquals(len, tip.length());
            }
        }
    }

    @Test
    void sixRotationsRestoreMolecule() {
        Molecule m = Fx.mol(new String[]{"earth:0,0", "fire:1,0", "water:1,-1", "air:2,-1"},
                "0,0>1,0", "1,0>1,-1", "1,-1>2,-1");
        for (int pq = -3; pq <= 3; pq++) {
            for (int pr = -3; pr <= 3; pr++) {
                Hex pivot = h(pq, pr);
                for (Molecule.Atom a : m.atoms()) {
                    Hex p = a.pos();
                    for (int i = 0; i < 6; i++) {
                        p = p.rotateCcwAround(pivot, 1);
                    }
                    assertEquals(a.pos(), p);
                    // distance to the pivot never changes
                    assertEquals(a.pos().distanceTo(pivot), a.pos().rotateCcwAround(pivot, 1).distanceTo(pivot));
                }
            }
        }
    }

    @Test
    void distanceFormula() {
        assertEquals(0, Hex.ZERO.distanceTo(Hex.ZERO));
        assertEquals(1, h(1, 0).length());
        assertEquals(1, h(1, -1).length());
        assertEquals(2, h(1, 1).length());
        assertEquals(3, h(3, -3).length());
        assertEquals(3, h(2, 1).distanceTo(h(-1, 1)));
    }

    @Test
    void stageFieldsGrow() {
        assertEquals(2, HexField.forStage(1, 4).radius());
        assertEquals(3, HexField.forStage(2, 4).radius());
        assertEquals(4, HexField.forStage(3, 4).radius());
        assertFalse(HexField.forStage(4, 4).isBounded());
        // stage 1 has 19 fields, 5 from corner to corner
        int count = 0;
        for (int q = -5; q <= 5; q++) {
            for (int r = -5; r <= 5; r++) {
                if (HexField.bounded(2).contains(h(q, r))) {
                    count++;
                }
            }
        }
        assertEquals(19, count);
        assertEquals(18, HexField.bounded(2).wallHexes().size());
        for (Hex w : HexField.bounded(2).wallHexes()) {
            assertTrue(HexField.bounded(2).isWall(w));
            assertFalse(HexField.bounded(2).contains(w));
        }
        assertThrows(IllegalArgumentException.class, () -> HexField.forStage(1, 1));
    }

    @Test
    void unboundedFieldStopsAtTheCoordinateLimit() {
        assertTrue(HexField.UNBOUNDED.contains(h(1000, -1000)));
        assertFalse(HexField.UNBOUNDED.contains(h(1001, 0)));
        assertEquals(List.of(), HexField.UNBOUNDED.wallHexes());
    }
}

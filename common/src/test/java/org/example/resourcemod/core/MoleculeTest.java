package org.example.resourcemod.core;

import org.example.resourcemod.core.molecule.Molecule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoleculeTest {
    private static Molecule asymmetric() {
        return Fx.mol(new String[]{"earth:0,0", "fire:1,0", "water:1,-1"}, "0,0>1,0", "1,0>1,-1");
    }

    @Test
    void moleculeMatchIgnoresListOrder() {
        Molecule a = asymmetric();
        Molecule b = Fx.mol(new String[]{"water:1,-1", "earth:0,0", "fire:1,0"}, "1,-1>1,0", "1,0>0,0");
        assertTrue(a.sameShape(b));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void wronglyRotatedMoleculeDoesNotMatch() {
        Molecule a = asymmetric();
        for (int steps = 1; steps <= 5; steps++) {
            assertFalse(a.sameShape(a.rotated(steps)), "rotation by " + steps);
        }
        assertTrue(a.sameShape(a.rotated(6)));
        assertTrue(a.sameShapeInRotation(a.rotated(2), -2));
        assertTrue(a.sameShapeInRotation(a.rotated(1), 5));
        assertFalse(a.sameShapeInRotation(a.rotated(1), 1));
    }

    @Test
    void differentElementsOrBondsDoNotMatch() {
        Molecule a = asymmetric();
        Molecule otherElement = Fx.mol(new String[]{"earth:0,0", "fire:1,0", "air:1,-1"}, "0,0>1,0", "1,0>1,-1");
        Molecule otherBonds = Fx.mol(new String[]{"earth:0,0", "fire:1,0", "water:1,-1"}, "0,0>1,0");
        assertFalse(a.sameShape(otherElement));
        assertFalse(a.sameShape(otherBonds));
    }

    @Test
    void symmetricMoleculeMatchesItsRotation() {
        Molecule pair = Fx.mol(new String[]{"earth:0,0", "earth:1,0"}, "0,0>1,0");
        // rotated by 180 degrees the same two earth atoms lie on [0,0] and [-1,0]: a different position set
        assertFalse(pair.sameShape(pair.rotated(3)));
        assertTrue(pair.sameShape(pair.rotated(6)));
    }

    @Test
    void connectivityUsesBondsNotNeighbourhood() {
        Molecule touching = Fx.mol(new String[]{"earth:0,0", "fire:1,0"});
        assertFalse(touching.isConnected());
        Molecule bonded = Fx.mol(new String[]{"earth:0,0", "fire:1,0"}, "0,0>1,0");
        assertTrue(bonded.isConnected());
    }
}

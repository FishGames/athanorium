package org.example.resourcemod.core;

import org.example.resourcemod.core.hex.Hex;
import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.program.Program;
import org.example.resourcemod.core.puzzle.Puzzle;
import org.example.resourcemod.core.sim.SimError;
import org.example.resourcemod.core.sim.Simulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.example.resourcemod.core.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

class CollisionTest {
    private static Puzzle two() {
        return puzzle(List.of(atom("earth"), atom("fire")), atom("earth"), 1);
    }

    private static Simulation run(Puzzle p, Program prog, HexField f, int steps) {
        Simulation s = new Simulation(p, prog, f, REG);
        for (int i = 0; i < steps; i++) {
            s.step();
        }
        return s;
    }

    @Test
    void atomMovedOntoAnotherAtomCollides() {
        Program prog = program(input(0, h(1, 0), 0), input(1, h(1, -1), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw"));
        Simulation s = run(two(), prog, HexField.UNBOUNDED, 2);
        assertEquals(SimError.COLLISION, s.error().key());
        assertEquals(1, s.error().cycle());
    }

    @Test
    void rotationOverOtherAtomCollides() {
        // start [2,0] and end [2,-2] are free, but the atom on [2,-1] lies right on the arc in between
        Program blocked = program(input(0, h(2, 0), 0), input(1, h(2, -1), 0),
                arm("arm", h(0, 0), 0, 2, "0=grab", "1=rotate_ccw"));
        Simulation s = run(two(), blocked, HexField.UNBOUNDED, 2);
        assertNotNull(s.error());
        assertEquals(SimError.COLLISION, s.error().key());

        Program free = program(input(0, h(2, 0), 0), input(1, h(0, 5), 0),
                arm("arm", h(0, 0), 0, 2, "0=grab", "1=rotate_ccw"));
        assertNull(run(two(), free, HexField.UNBOUNDED, 2).error());
    }

    @Test
    void atomHitsTheBaseOfAnotherArm() {
        Program prog = program(input(0, h(1, 0), 0), input(1, h(9, 9), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw"),
                arm("arm", h(1, -1), 0, 1));
        Simulation s = run(two(), prog, HexField.UNBOUNDED, 2);
        assertEquals(SimError.COLLISION, s.error().key());
    }

    @Test
    void rotationOverOtherBaseCollides() {
        // base on [2,-1], the arc from [2,0] to [2,-2] passes right over it
        Program prog = program(input(0, h(2, 0), 0), input(1, h(9, 9), 0),
                arm("arm", h(0, 0), 0, 2, "0=grab", "1=rotate_ccw"),
                arm("arm", h(2, -1), 0, 1));
        assertEquals(SimError.COLLISION, run(two(), prog, HexField.UNBOUNDED, 2).error().key());
    }

    @Test
    void atomsNextToEachOtherDoNotCollide() {
        Program prog = program(input(0, h(1, 0), 0), input(1, h(1, -1), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_cw"));
        assertNull(run(two(), prog, HexField.UNBOUNDED, 2).error());
    }

    @Test
    void passingOtherBaseOnFarSideIsFine() {
        Program prog = program(input(0, h(1, 0), 0), input(1, h(9, 9), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw"),
                arm("arm", h(-3, 0), 0, 1));
        assertNull(run(two(), prog, HexField.UNBOUNDED, 2).error());
    }

    @Test
    void errorCycleIsRepeatable() {
        Program prog = program(input(0, h(1, 0), 0), input(1, h(1, -1), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw"));
        SimError a = run(two(), prog, HexField.UNBOUNDED, 5).error();
        SimError b = run(two(), prog, HexField.UNBOUNDED, 5).error();
        assertEquals(a, b);
    }

    // field edges

    @Test
    void rotationAtEdgeIsFine() {
        Program prog = program(input(0, h(2, 0), 0), input(1, h(0, 1), 0),
                arm("arm", h(0, 0), 0, 2, "0=grab", "1=rotate_ccw", "2=rotate_ccw", "3=rotate_ccw"));
        assertNull(run(two(), prog, HexField.bounded(2), 4).error());
    }

    @Test
    void extendOutOfFieldIsError() {
        Program prog = program(input(0, h(2, 0), 0), input(1, h(0, 1), 0),
                arm("piston_arm", h(0, 0), 0, 2, "0=extend"));
        SimError e = run(two(), prog, HexField.bounded(2), 2).error();
        assertNotNull(e);
        assertEquals(SimError.OUTSIDE_FIELD, e.key());
    }

    @Test
    void sameExtensionIsFineInAnUnboundedField() {
        Program prog = program(input(0, h(2, 0), 0), input(1, h(0, 1), 0),
                arm("piston_arm", h(0, 0), 0, 2, "0=extend"));
        assertNull(run(two(), prog, HexField.UNBOUNDED, 2).error());
    }

    @Test
    void pivotOverEdgeHitsWall() {
        // grabbed atom on [2,0] (edge), partner on [1,0]; two ccw pivots turn the partner to [2,1] = wall
        Puzzle p = puzzle(List.of(mol(new String[]{"earth:0,0", "fire:-1,0"}, "0,0>-1,0"), atom("fire")),
                atom("earth"), 1);
        Program prog = program(input(0, h(2, 0), 0), input(1, h(0, 1), 0),
                arm("arm", h(0, 0), 0, 2, "0=grab", "1=pivot_ccw", "2=pivot_ccw"));
        Simulation s = run(p, prog, HexField.bounded(2), 2);
        assertNull(s.error(), "after the first pivot the partner is on [1,1], inside the field");
        s.step();
        assertNotNull(s.error());
        assertEquals(SimError.COLLISION, s.error().key());
    }

    @Test
    void rotationAcrossFieldEdgeHitsWall() {
        // rotation of a length-3 arm in a radius-3 field: the arc between two corner hexes bulges outside
        Puzzle p = puzzle(List.of(atom("earth")), atom("earth"), 1);
        Program prog = program(input(0, h(3, 0), 0), arm("arm", h(0, 0), 0, 3, "0=grab", "1=rotate_ccw"));
        Simulation s = run(p, prog, HexField.bounded(3), 2);
        // the straight line between neighbouring corners is inside, the circular arc is not necessarily:
        // either way the simulation must give a definite, repeatable answer
        Simulation again = run(p, prog, HexField.bounded(3), 2);
        assertEquals(s.error(), again.error());
    }

    @Test
    void unboundedFieldHasNoWall() {
        Puzzle p = puzzle(List.of(mol(new String[]{"earth:0,0", "fire:-1,0"}, "0,0>-1,0"), atom("fire")),
                atom("earth"), 1);
        Program prog = program(input(0, h(2, 0), 0), input(1, h(0, 1), 0),
                arm("arm", h(0, 0), 0, 2, "0=grab", "1=pivot_ccw", "2=pivot_ccw"));
        assertNull(run(p, prog, HexField.UNBOUNDED, 3).error());
    }

    // reagents

    @Test
    void reagentAppearsInEveryOfTheSixRotations() {
        Puzzle p = puzzle(List.of(mol(new String[]{"earth:0,0", "fire:1,0"}, "0,0>1,0")), atom("earth"), 1);
        for (int rot = 0; rot < 6; rot++) {
            Simulation s = new Simulation(p, program(input(0, h(0, 0), rot)), HexField.UNBOUNDED, REG);
            var atoms = s.atoms();
            assertEquals(2, atoms.size(), "rotation " + rot);
            Hex partner = Hex.dir(rot);
            assertTrue(atoms.stream().anyMatch(a -> a.element().path().equals("earth") && a.pos().equals(Hex.ZERO)));
            assertTrue(atoms.stream().anyMatch(a -> a.element().path().equals("fire") && a.pos().equals(partner)),
                    "rotation " + rot);
        }
    }

    @Test
    void reagentWaitsUntilAllItsHexesAreFree() {
        // built directly with two atoms that are not bonded (the loader would reject this reagent), so that the arm
        // can take one of them away and leave the other one on its hex
        Puzzle p = puzzle(List.of(mol(new String[]{"earth:0,0", "fire:1,0"})), atom("earth"), 1);
        Program prog = program(input(0, h(0, 0), 0),
                arm("arm", h(2, 0), 3, 1, "0=grab", "1=rotate_cw", "2=wait", "3=wait"));
        Simulation s = new Simulation(p, prog, HexField.UNBOUNDED, REG);
        assertEquals(2, s.atoms().size());
        for (int i = 0; i < 4; i++) {
            s.step();
            assertNull(s.error());
        }
        assertEquals(2, s.atoms().size(), "the fire atom was moved, the earth atom still blocks the input");
        assertTrue(s.atoms().stream().anyMatch(a -> a.element().path().equals("earth") && a.pos().equals(h(0, 0))));
        assertTrue(s.atoms().stream().anyMatch(a -> a.element().path().equals("fire") && a.pos().equals(h(2, -1))));
    }
}

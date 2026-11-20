package io.github.fishgames.athanorium.core;

import io.github.fishgames.athanorium.core.hex.Hex;
import io.github.fishgames.athanorium.core.hex.HexField;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.sim.SimError;
import io.github.fishgames.athanorium.core.sim.Simulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.fishgames.athanorium.core.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

/** Every arm type and every movement instruction. The input atom is grabbed in cycle 0, moved afterwards. */
class ArmTest {
    private static Simulation sim(Puzzle p, io.github.fishgames.athanorium.core.program.Program prog) {
        return new Simulation(p, prog, HexField.UNBOUNDED, REG);
    }

    private static Puzzle single() {
        return puzzle(List.of(atom("earth")), atom("earth"), 1);
    }

    private static boolean has(Simulation s, Hex pos) {
        return s.atoms().stream().anyMatch(a -> a.pos().equals(pos) && a.grabbed());
    }

    private static void steps(Simulation s, int n) {
        for (int i = 0; i < n; i++) {
            s.step();
        }
    }

    @Test
    void rotateShortArmBothWays() {
        Simulation s = sim(single(), program(input(0, h(1, 0), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw", "2=rotate_ccw", "3=rotate_cw")));
        steps(s, 1);
        assertTrue(has(s, h(1, 0)));
        steps(s, 1);
        assertTrue(has(s, h(1, -1)));
        steps(s, 1);
        assertTrue(has(s, h(0, -1)));
        steps(s, 1);
        assertTrue(has(s, h(1, -1)));
    }

    @Test
    void armsOfLengthTwoAndThreeReachFurther() {
        for (int len = 2; len <= 3; len++) {
            Simulation s = sim(single(), program(input(0, h(len, 0), 0),
                    arm("arm", h(0, 0), 0, len, "0=grab", "1=rotate_ccw")));
            steps(s, 2);
            assertTrue(has(s, h(len, -len).plus(h(0, 0)).rotateCcw(0)), "length " + len);
        }
    }

    @Test
    void armRotationSetsTheDirectionOfTheGrabber() {
        for (int rot = 0; rot < 6; rot++) {
            Simulation s = sim(single(), program(input(0, Hex.dir(rot), 0),
                    arm("arm", h(0, 0), rot, 1, "0=grab", "1=rotate_cw")));
            steps(s, 2);
            assertTrue(has(s, Hex.dir(rot - 1)), "rotation " + rot);
        }
    }

    @Test
    void doubleArmSecondGrabberOpposite() {
        Simulation s = sim(single(), program(input(0, h(-1, 0), 0),
                arm("double_arm", h(0, 0), 0, 1, "0=grab", "1=rotate_cw")));
        steps(s, 2);
        assertTrue(has(s, h(0, -1)));
    }

    @Test
    void tripleArmGrabbers() {
        Simulation s = sim(single(), program(input(0, Hex.dir(2), 0),
                arm("triple_arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw")));
        steps(s, 2);
        assertTrue(has(s, Hex.dir(3)));
    }

    @Test
    void hexArmGrabsInAllSixDirections() {
        for (int d = 0; d < 6; d++) {
            Simulation s = sim(single(), program(input(0, Hex.dir(d), 0),
                    arm("hex_arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw")));
            steps(s, 2);
            assertTrue(has(s, Hex.dir(d + 1)), "direction " + d);
        }
    }

    @Test
    void pivotTurnsAMoleculeAroundTheGrabber() {
        Puzzle p = puzzle(List.of(mol(new String[]{"earth:0,0", "fire:1,0"}, "0,0>1,0")), atom("earth"), 1);
        Simulation s = sim(p, program(input(0, h(1, 0), 0), arm("arm", h(0, 0), 0, 1, "0=grab", "1=pivot_ccw")));
        steps(s, 2);
        var atoms = s.atoms();
        assertTrue(atoms.stream().anyMatch(a -> a.pos().equals(h(1, 0)) && a.grabbed()));
        assertTrue(atoms.stream().anyMatch(a -> a.pos().equals(h(2, -1))), "the second atom swung around the grabbed one");
        assertEquals(0, s.armViews().get(0).rotation(), "the arm itself did not turn");
    }

    @Test
    void pistonExtendsAndRetracts() {
        Simulation s = sim(single(), program(input(0, h(1, 0), 0),
                arm("piston_arm", h(0, 0), 0, 1, "0=grab", "1=extend", "2=extend", "3=retract")));
        steps(s, 2);
        assertTrue(has(s, h(2, 0)));
        steps(s, 1);
        assertTrue(has(s, h(3, 0)));
        steps(s, 1);
        assertTrue(has(s, h(2, 0)));
    }

    @Test
    void extendingBeyondLengthThreeDoesNothing() {
        Simulation s = sim(single(), program(input(0, h(3, 0), 0),
                arm("piston_arm", h(0, 0), 0, 3, "0=grab", "1=extend")));
        steps(s, 2);
        assertTrue(has(s, h(3, 0)));
        assertNull(s.error());
    }

    @Test
    void extendOnANormalArmIsAnError() {
        Simulation s = sim(single(), program(input(0, h(1, 0), 0), arm("arm", h(0, 0), 0, 1, "0=extend")));
        steps(s, 1);
        assertNotNull(s.error());
        assertEquals(SimError.NOT_A_PISTON, s.error().key());
    }

    @Test
    void trackMovesTheArmAlongItsPath() {
        Simulation s = sim(single(), program(input(0, h(2, 0), 0),
                track(h(0, 0), h(0, 0), h(1, 0), h(2, 0)),
                arm("arm", h(0, 0), 0, 1, "0=track_forward", "1=grab", "2=track_back")));
        steps(s, 3);
        assertTrue(has(s, h(1, 0)));
        assertEquals(h(0, 0), s.armViews().get(0).pos());
    }

    @Test
    void trackEndsAreNoOps() {
        Simulation s = sim(single(), program(input(0, h(5, 5), 0),
                track(h(0, 0), h(0, 0), h(1, 0)),
                arm("arm", h(0, 0), 0, 1, "0=track_back", "1=track_forward", "2=track_forward")));
        steps(s, 1);
        assertEquals(h(0, 0), s.armViews().get(0).pos());
        steps(s, 2);
        assertEquals(h(1, 0), s.armViews().get(0).pos());
        assertNull(s.error());
    }

    @Test
    void trackInstructionOffATrackIsAnError() {
        Simulation s = sim(single(), program(input(0, h(5, 5), 0), arm("arm", h(0, 0), 0, 1, "0=track_forward")));
        steps(s, 1);
        assertEquals(SimError.NOT_ON_TRACK, s.error().key());
    }

    @Test
    void waitDoesNothingButTakesACycle() {
        Simulation s = sim(single(), program(input(0, h(1, 0), 0),
                arm("arm", h(0, 0), 0, 1, "0=wait", "1=grab", "2=rotate_ccw")));
        steps(s, 1);
        assertFalse(has(s, h(1, 0)));
        steps(s, 1);
        assertTrue(has(s, h(1, 0)));
        steps(s, 1);
        assertTrue(has(s, h(1, -1)));
    }

    @Test
    void theTapeRepeatsAfterItsLastInstruction() {
        Simulation s = sim(single(), program(input(0, h(1, 0), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw", "2=drop", "3=rotate_cw")));
        steps(s, 4);
        assertTrue(s.atoms().stream().noneMatch(a -> a.grabbed()));
        steps(s, 1); // slot 0 again: the next atom is grabbed
        assertTrue(has(s, h(1, 0)));
    }

    @Test
    void repeatInstructionRunsTheBlockAgain() {
        // tape: grab, rotate_ccw, then repeat at slot 3 gives: grab, rotate, ., grab(no-op), rotate_ccw
        Simulation s = sim(single(), program(input(0, h(1, 0), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw", "3=repeat")));
        steps(s, 5);
        assertTrue(has(s, h(0, -1)));
    }

    @Test
    void resetBringsTheArmBackToItsStartPosition() {
        Simulation s = sim(single(), program(input(0, h(1, 0), 0),
                arm("arm", h(0, 0), 0, 1, "0=rotate_ccw", "1=rotate_ccw", "2=reset")));
        steps(s, 4);
        assertEquals(0, s.armViews().get(0).rotation());
    }

    @Test
    void armsAreExecutedInProgramOrder() {
        // both arms grab the same atom in the same cycle: the first one in the program wins
        Simulation s = sim(single(), program(input(0, h(1, 0), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw"),
                arm("arm", h(2, 0), 3, 1, "0=grab", "1=rotate_cw")));
        steps(s, 2);
        assertTrue(has(s, h(1, -1)), "the first arm moved the atom counter-clockwise");
    }
}

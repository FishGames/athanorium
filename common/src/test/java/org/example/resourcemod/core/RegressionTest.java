package org.example.resourcemod.core;

import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.program.Program;
import org.example.resourcemod.core.program.ProgramValidator;
import org.example.resourcemod.core.puzzle.Puzzle;
import org.example.resourcemod.core.sim.SimResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.example.resourcemod.core.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

/** Whole solutions with fixed expected numbers. Cycles and instruction counts were worked out by hand. */
class RegressionTest {
    /**
     * Bind at [1,0] (hexes [1,0] and [2,0]). Arm A brings fire from [3,-1] onto [2,0] (cycles 0-2). Arm B starts
     * pointing at [-1,0], grabs the earth atom there (0), swings it clockwise over three steps to [1,0] (1-3) and
     * drops it (4); the bind bonds earth and fire at the end of that half cycle. B grabs the bonded earth (5),
     * swings the molecule to [0,1]/[0,2] (6) and drops it onto the output (7): 8 cycles.
     * Instructions: A 3 (the wait is not counted), B 8.
     */
    private static Program bondingProgram() {
        return program(
                input(0, h(-1, 0), 0), input(1, h(3, -1), 0),
                glyph("bind", h(1, 0), 0),
                output(h(0, 1), 0),
                arm("arm", h(2, -1), 0, 1, "0=grab", "1=rotate_cw", "2=drop", "7=wait"),
                arm("arm", h(0, 0), 3, 1, "0=grab", "1=rotate_cw", "2=rotate_cw", "3=rotate_cw", "4=drop",
                        "5=grab", "6=rotate_cw", "7=drop"));
    }

    private static Puzzle bondingPuzzle() {
        return puzzle(List.of(atom("earth"), atom("fire")),
                mol(new String[]{"earth:0,0", "fire:0,1"}, "0,0>0,1"), 1);
    }

    @Test
    void bondingSolution() {
        Puzzle p = bondingPuzzle();
        Program prog = bondingProgram();
        assertEquals(List.of(), ProgramValidator.validate(prog, p, HexField.bounded(4), REG, values()));
        SimResult r = run(p, prog, HexField.bounded(4), 1000);
        assertTrue(r.solved(), String.valueOf(r.error()));
        assertEquals(8, r.cycles());
        assertEquals(11, r.instructions());
        assertTrue(r.area() >= 10, "ten hexes are used by parts alone, got " + r.area());
    }

    @Test
    void sameResultInLargerField() {
        Puzzle p = bondingPuzzle();
        SimResult a = run(p, bondingProgram(), HexField.bounded(5), 1000);
        SimResult b = run(p, bondingProgram(), HexField.UNBOUNDED, 1000);
        assertEquals(a, b);
        assertTrue(a.solved());
    }

    @Test
    void pistonSolution() {
        // grab 0, extend 1, extend 2, drop 3: the product is made in cycle 3, so 4 cycles
        Puzzle p = puzzle(List.of(atom("earth")), atom("earth"), 1);
        Program prog = program(input(0, h(1, 0), 0), output(h(3, 0), 0),
                arm("piston_arm", h(0, 0), 0, 1, "0=grab", "1=extend", "2=extend", "3=drop", "4=retract", "5=retract"));
        SimResult r = run(p, prog, HexField.bounded(4), 1000);
        assertTrue(r.solved(), String.valueOf(r.error()));
        assertEquals(4, r.cycles());
        assertEquals(6, r.instructions());
    }

    @Test
    void trackSolution() {
        // the arm rides to [1,0] (0), grabs the atom on [2,0] (1), swings it ccw to [2,-1] (2), drops it (3)
        Puzzle p = puzzle(List.of(atom("earth")), atom("earth"), 1);
        Program prog = program(input(0, h(2, 0), 0), output(h(2, -1), 0),
                track(h(0, 0), h(0, 0), h(1, 0)),
                arm("arm", h(0, 0), 0, 1, "0=track_forward", "1=grab", "2=rotate_ccw", "3=drop"));
        SimResult r = run(p, prog, HexField.bounded(4), 1000);
        assertTrue(r.solved(), String.valueOf(r.error()));
        assertEquals(4, r.cycles());
        assertEquals(4, r.instructions());
    }

    @Test
    void armKeepsRotationOnTapeRestart() {
        // without a reset the second round starts where the first ended: the second atom lands one step further
        Puzzle p = puzzle(List.of(atom("earth")), atom("earth"), 1);
        Program prog = program(input(0, h(2, 0), 0), output(h(1, -1), 0),
                track(h(0, 0), h(0, 0), h(1, 0)),
                arm("arm", h(0, 0), 0, 1, "0=track_forward", "1=grab", "2=rotate_ccw", "3=drop"));
        SimResult r = run(p, prog, HexField.bounded(4), 1000);
        assertTrue(r.solved(), String.valueOf(r.error()));
        assertEquals(8, r.cycles());
    }

    @Test
    void resetMakesTheTapeRepeatable() {
        Puzzle p = puzzle(List.of(atom("earth")), atom("earth"), 3);
        Program prog = program(input(0, h(1, 0), 0), output(h(1, -1), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw", "2=drop", "3=reset"));
        SimResult r = run(p, prog, HexField.bounded(3), 1000);
        assertTrue(r.solved(), String.valueOf(r.error()));
        // grab, rotate, drop, undo rotation: 4 cycles per round, last product in cycle 2 of round three
        assertEquals(4 * 2 + 3, r.cycles());
    }
}

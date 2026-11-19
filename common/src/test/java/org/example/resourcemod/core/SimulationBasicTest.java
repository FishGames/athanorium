package org.example.resourcemod.core;

import org.example.resourcemod.core.hex.HexField;
import org.example.resourcemod.core.program.ProgramValidator;
import org.example.resourcemod.core.puzzle.Puzzle;
import org.example.resourcemod.core.sim.SimResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.example.resourcemod.core.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationBasicTest {
    /** Earth atom at [1,0], arm at [0,0] grabs it, swings it to [1,-1] where the output waits, drops, swings back. */
    static org.example.resourcemod.core.program.Program transport() {
        return program(
                input(0, h(1, 0), 0),
                output(h(1, -1), 0),
                arm("arm", h(0, 0), 0, 1, "0=grab", "1=rotate_ccw", "2=drop", "3=rotate_cw"));
    }

    @Test
    void transportsOneAtom() {
        Puzzle p = puzzle(List.of(atom("earth")), atom("earth"), 1);
        assertEquals(List.of(), ProgramValidator.validate(transport(), p, HexField.bounded(3), REG, values()));
        SimResult r = run(p, transport(), HexField.bounded(3), 100);
        assertTrue(r.solved(), String.valueOf(r.error()));
        assertEquals(3, r.cycles());
        assertEquals(4, r.instructions());
        assertEquals(3, r.area());
    }

    @Test
    void producesRequestedNumberOfProducts() {
        Puzzle p = puzzle(List.of(atom("earth")), atom("earth"), 3);
        SimResult r = run(p, transport(), HexField.bounded(3), 100);
        assertTrue(r.solved(), String.valueOf(r.error()));
        assertEquals(3 + 4 * 2, r.cycles());
    }

    @Test
    void stopsAtCycleLimitWithError() {
        Puzzle p = puzzle(List.of(atom("earth")), atom("earth"), 3);
        SimResult r = run(p, transport(), HexField.bounded(3), 5);
        assertFalse(r.solved());
        assertEquals("sim.error.cycle_limit", r.error().key());
        assertEquals(5, r.error().cycle());
    }

    @Test
    void sameProgramGivesSameResult() {
        Puzzle p = puzzle(List.of(atom("earth")), atom("earth"), 6);
        SimResult a = run(p, transport(), HexField.bounded(3), 1000);
        SimResult b = run(p, transport(), HexField.bounded(3), 1000);
        assertEquals(a, b);
    }
}

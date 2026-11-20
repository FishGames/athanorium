package io.github.fishgames.athanorium.core;

import io.github.fishgames.athanorium.core.hex.HexField;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.sim.Simulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.fishgames.athanorium.core.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

/** The glyphs act on atoms lying on their hexes. Inputs are used as plain atom sources placed right on the glyph. */
class GlyphTest {
    private static Puzzle puzzleOf(io.github.fishgames.athanorium.core.molecule.Molecule input) {
        return puzzle(List.of(input), atom("earth"), 1);
    }

    private static Simulation sim(Puzzle p, io.github.fishgames.athanorium.core.program.Program prog) {
        return new Simulation(p, prog, HexField.UNBOUNDED, REG);
    }

    private static Simulation.AtomView at(Simulation s, int q, int r) {
        return s.atoms().stream().filter(a -> a.pos().equals(h(q, r))).findFirst().orElse(null);
    }

    @Test
    void bindBondsTwoAtomsOnItsHexes() {
        // two atoms that are not bonded (built directly, the loader would reject such a reagent)
        Puzzle p = puzzleOf(mol(new String[]{"earth:0,0", "fire:1,0"}));
        Simulation s = sim(p, program(input(0, h(0, 0), 0), glyph("bind", h(0, 0), 0)));
        assertFalse(at(s, 0, 0).bondedIn(0));
        s.step();
        assertTrue(at(s, 0, 0).bondedIn(0));
        assertTrue(at(s, 1, 0).bondedIn(3));
    }

    @Test
    void rotatedBindBondsAlongItsRotation() {
        Puzzle p = puzzleOf(mol(new String[]{"earth:0,0", "fire:1,-1", "water:1,0"}));
        Simulation s = sim(p, program(input(0, h(0, 0), 0), glyph("bind", h(0, 0), 1)));
        s.step();
        assertTrue(at(s, 0, 0).bondedIn(1));
        assertTrue(at(s, 1, -1).bondedIn(4));
        assertFalse(at(s, 0, 0).bondedIn(0), "no bond towards the third atom");
    }

    @Test
    void bindDoesNothingWithOnlyOneAtom() {
        Puzzle p = puzzleOf(atom("earth"));
        Simulation s = sim(p, program(input(0, h(0, 0), 0), glyph("bind", h(0, 0), 0)));
        s.step();
        assertEquals(0, at(s, 0, 0).bonds());
    }

    @Test
    void multiBindBondsCenterToNeighbours() {
        Puzzle p = puzzleOf(mol(new String[]{"earth:0,0", "fire:1,0", "water:0,-1", "air:-1,1"}));
        Simulation s = sim(p, program(input(0, h(0, 0), 0), glyph("multi_bind", h(0, 0), 0)));
        s.step();
        assertTrue(at(s, 0, 0).bondedIn(0));
        assertTrue(at(s, 0, 0).bondedIn(2));
        assertTrue(at(s, 0, 0).bondedIn(4));
        assertTrue(at(s, 1, 0).bondedIn(3));
        assertTrue(at(s, 0, -1).bondedIn(5));
        assertTrue(at(s, -1, 1).bondedIn(1));
    }

    @Test
    void multiBindBondsOnlyWhereAnAtomIs() {
        Puzzle p = puzzleOf(mol(new String[]{"earth:0,0", "water:0,-1"}));
        Simulation s = sim(p, program(input(0, h(0, 0), 0), glyph("multi_bind", h(0, 0), 0)));
        s.step();
        assertTrue(at(s, 0, 0).bondedIn(2));
        assertFalse(at(s, 0, 0).bondedIn(0));
        assertFalse(at(s, 0, 0).bondedIn(4));
    }

    @Test
    void unbindRemovesTheBondBetweenItsTwoHexes() {
        Puzzle p = puzzleOf(mol(new String[]{"earth:0,0", "fire:1,0"}, "0,0>1,0"));
        Simulation s = sim(p, program(input(0, h(0, 0), 0), glyph("unbind", h(0, 0), 0)));
        assertTrue(at(s, 0, 0).bondedIn(0));
        s.step();
        assertFalse(at(s, 0, 0).bondedIn(0));
        assertFalse(at(s, 1, 0).bondedIn(3));
    }

    @Test
    void unbindLeavesOtherBondsAlone() {
        Puzzle p = puzzleOf(mol(new String[]{"earth:0,0", "fire:1,0", "water:0,-1"}, "0,0>1,0", "0,0>0,-1"));
        Simulation s = sim(p, program(input(0, h(0, 0), 0), glyph("unbind", h(0, 0), 0)));
        s.step();
        assertFalse(at(s, 0, 0).bondedIn(0));
        assertTrue(at(s, 0, 0).bondedIn(2));
    }

    @Test
    void disposerRemovesFreeAtom() {
        Puzzle p = puzzleOf(atom("earth"));
        Simulation s = sim(p, program(input(0, h(0, 0), 0), glyph("disposer", h(0, 0), 0)));
        assertEquals(1, s.atoms().size());
        s.step();
        assertEquals(0, s.atoms().size());
    }

    @Test
    void disposerKeepsBondedAtoms() {
        Puzzle p = puzzleOf(mol(new String[]{"earth:0,0", "fire:1,0"}, "0,0>1,0"));
        Simulation s = sim(p, program(input(0, h(0, 0), 0), glyph("disposer", h(0, 0), 0)));
        s.step();
        assertEquals(2, s.atoms().size());
    }

    @Test
    void unbindThenDisposeTakesTwoHalfCycles() {
        // half cycle 1: the unbind frees both atoms, the recent-bond flag protects the one on the disposer;
        // half cycle 2: the flags are reset first, now the disposer removes it. The atom on [1,0] is no disposer center.
        Puzzle p = puzzleOf(mol(new String[]{"earth:0,0", "fire:1,0"}, "0,0>1,0"));
        Simulation s = sim(p, program(input(0, h(0, 0), 0),
                glyph("unbind", h(0, 0), 0), glyph("disposer", h(0, 0), 0)));
        s.step();
        assertNull(at(s, 0, 0));
        assertNotNull(at(s, 1, 0));
    }

    @Test
    void glyphsRunInProgramOrder() {
        Puzzle p = puzzleOf(mol(new String[]{"earth:0,0", "fire:1,0"}));
        var prog = program(input(0, h(0, 0), 0), glyph("bind", h(0, 0), 0), glyph("unbind", h(0, 0), 0));
        Simulation a = sim(p, prog);
        Simulation b = sim(p, prog);
        for (int i = 0; i < 4; i++) {
            a.step();
            b.step();
        }
        assertEquals(a.atoms(), b.atoms());
    }
}

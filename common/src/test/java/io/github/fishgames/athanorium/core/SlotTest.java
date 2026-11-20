package io.github.fishgames.athanorium.core;

import io.github.fishgames.athanorium.core.edit.Edit;
import io.github.fishgames.athanorium.core.edit.EditHistory;
import io.github.fishgames.athanorium.core.hex.HexField;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.json.DataException;
import io.github.fishgames.athanorium.core.program.Part;
import io.github.fishgames.athanorium.core.program.Program;
import io.github.fishgames.athanorium.core.program.ProgramCodec;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.slot.CalcResult;
import io.github.fishgames.athanorium.core.slot.Computation;
import io.github.fishgames.athanorium.core.slot.Library;
import io.github.fishgames.athanorium.core.slot.ProgramSlot;
import io.github.fishgames.athanorium.core.slot.SlotChecker;
import io.github.fishgames.athanorium.core.slot.SlotCodec;
import io.github.fishgames.athanorium.core.slot.SlotContent;
import io.github.fishgames.athanorium.core.slot.Validity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.github.fishgames.athanorium.core.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

/** Program slot, compact encoding and editing steps. */
class SlotTest {
    private static final Id PUZZLE = Id.of("test");
    private static final Puzzle P = puzzle(List.of(atom("earth")), atom("earth"), 1);

    private static SlotContent content(Program program) {
        return new SlotContent(PUZZLE, 2, program);
    }

    private static Library library(Puzzle p) {
        return new Library(Map.of(PUZZLE, p), values(), 1);
    }

    private static CalcResult compute(SlotContent c, Puzzle p) {
        return new Computation(p, c.program(), SlotChecker.field(c, 4), REG, values(), 1000, -1).runToEnd();
    }

    @Test
    void codecKeepsOrderAndUnknownParts() {
        Program program = new Program(List.of(
                Part.unknown(Id.parse("addon:laser"), h(7, -7), Map.of("power", 5L, "mode", "wide")),
                input(0, h(1, 0), 3), output(h(1, -1), 2), track(h(0, 2), h(0, 0), h(1, 0), h(2, 0)),
                arm("piston_arm", h(0, 0), 4, 2, "0=grab", "1=rotate_ccw", "700=drop", "999999=wait"),
                glyph("disposer", h(-3, 0), 0)));
        Program again = ProgramCodec.decode(ProgramCodec.encode(program), ProgramCodec.NO_LIMITS);
        assertEquals(program, again);
    }

    @Test
    void codecEnforcesLimitsAndRejectsGarbage() {
        Program program = SimulationBasicTest.transport();
        byte[] data = ProgramCodec.encode(program);
        assertThrows(DataException.class, () -> ProgramCodec.decode(data, new ProgramCodec.Limits(2, 100)));
        assertThrows(DataException.class, () -> ProgramCodec.decode(data, new ProgramCodec.Limits(100, 3)));
        assertThrows(DataException.class, () -> ProgramCodec.decode(new byte[]{5, 1, 2}, ProgramCodec.NO_LIMITS));
        assertThrows(DataException.class, () -> ProgramCodec.decode(new byte[0], ProgramCodec.NO_LIMITS));
    }

    @Test
    void revisionGrowsOnlyOnChange() {
        ProgramSlot slot = new ProgramSlot();
        SlotContent a = content(SimulationBasicTest.transport());
        int r1 = slot.applyInterim(a).revision();
        assertEquals(r1, slot.applyInterim(a).revision(), "same content keeps its revision");
        assertNull(slot.pending());
        int r2 = slot.applyInterim(a.withStage(3)).revision();
        assertTrue(r2 > r1);
        assertFalse(slot.saved().confirmed());
    }

    @Test
    void confirmedProgramKeepsItsPlaceUntilSaved() {
        ProgramSlot slot = new ProgramSlot();
        SlotContent a = content(SimulationBasicTest.transport());
        int rev = slot.applyInterim(a).revision();
        assertTrue(slot.confirm(rev, compute(a, P)));
        SlotContent b = a.withStage(3);
        slot.applyInterim(b);
        assertEquals(a, slot.saved().content());
        assertEquals(b, slot.pending().content());
        assertFalse(slot.pending().confirmed());
        // a passed test alone replaces nothing
        assertTrue(slot.confirm(slot.pending().revision(), compute(b, P)));
        assertEquals(a, slot.saved().content());
        // identical to the saved program again: dropped automatically, confirmation stays
        slot.applyInterim(a);
        assertNull(slot.pending());
        assertTrue(slot.saved().confirmed());
        // discard brings the old solution back, save takes the change over with its revision
        slot.applyInterim(b);
        slot.discard();
        assertEquals(a, slot.current().content());
        int pendingRev = slot.applyInterim(b).revision();
        slot.save();
        assertEquals(b, slot.saved().content());
        assertEquals(pendingRev, slot.saved().revision());
        assertFalse(slot.saved().confirmed(), "saved without a passed test: unconfirmed");
        assertFalse(slot.confirm(rev, compute(a, P)), "a result for an older revision is dropped");
    }

    @Test
    void slotCodecRoundTrip() {
        ProgramSlot slot = new ProgramSlot();
        SlotContent a = content(SimulationBasicTest.transport());
        slot.confirm(slot.applyInterim(a).revision(), compute(a, P));
        slot.applyInterim(a.withStage(3));
        ProgramSlot again = SlotCodec.decode(SlotCodec.encode(slot));
        assertEquals(slot.revisionCounter(), again.revisionCounter());
        assertEquals(slot.saved().content(), again.saved().content());
        assertEquals(slot.saved().result(), again.saved().result());
        assertTrue(again.saved().confirmed());
        assertEquals(slot.pending().content(), again.pending().content());
        assertNull(again.editor(), "the editing lock is never stored");
    }

    @Test
    void validityFollowsPuzzle() {
        ProgramSlot slot = new ProgramSlot();
        SlotContent a = content(SimulationBasicTest.transport());
        slot.confirm(slot.applyInterim(a).revision(), compute(a, P));
        assertEquals(Validity.VALID, SlotChecker.check(slot.saved(), library(P), REG, 4).validity());
        // puzzle missing: invalid, back again: valid without a new computation
        SlotChecker.Check missing = SlotChecker.check(slot.saved(), Library.EMPTY, REG, 4);
        assertEquals(Validity.INVALID, missing.validity());
        assertEquals(SlotChecker.PUZZLE_MISSING, missing.reasonKey());
        SlotChecker.Check back = SlotChecker.check(slot.saved(), library(P), REG, 4);
        assertEquals(Validity.VALID, back.validity());
        assertFalse(back.needsComputation());
        // only the reference changed: no computation
        Puzzle otherReference = new Puzzle(1, null, null, P.inputs(), P.product(), null, null, P.metric(),
                P.reference() + 5, P.testProducts(), null, null);
        assertFalse(SlotChecker.check(slot.saved(), library(otherReference), REG, 4).needsComputation());
        // the puzzle now forbids a built-in part: new computation, which fails with that reason
        Puzzle forbids = new Puzzle(1, null, null, P.inputs(), P.product(), List.of(Id.of("arm")), null, P.metric(),
                P.reference(), P.testProducts(), null, null);
        assertTrue(SlotChecker.check(slot.saved(), library(forbids), REG, 4).needsComputation());
        slot.saved().setResult(compute(a, forbids));
        SlotChecker.Check failed = SlotChecker.check(slot.saved(), library(forbids), REG, 4);
        assertEquals(Validity.INVALID, failed.validity());
        assertEquals("program.error.forbidden_part", failed.reasonKey());
        assertTrue(slot.saved().confirmed());
    }

    @Test
    void computationChecksTheReportedCycles() {
        SlotContent a = content(SimulationBasicTest.transport());
        HexField field = SlotChecker.field(a, 4);
        assertTrue(new Computation(P, a.program(), field, REG, values(), 3, 3).runToEnd().passed());
        assertEquals(Computation.CYCLE_MISMATCH,
                new Computation(P, a.program(), field, REG, values(), 5, 5).runToEnd().failKey());
        assertFalse(new Computation(P, a.program(), field, REG, values(), 2, 2).runToEnd().passed());
    }

    @Test
    void undoAndRedoRestoreEveryStepExactly() {
        EditHistory history = new EditHistory();
        SlotContent start = content(SimulationBasicTest.transport());
        SlotContent c = start;
        List<SlotContent> states = new java.util.ArrayList<>(List.of(c));
        c = history.apply(c, Edit.place(c, glyph("bind", h(2, 2), 1)));
        states.add(c);
        c = history.apply(c, Edit.change(Edit.Kind.MOVE, c, 0, c.program().parts().get(0).withPos(h(-2, 0))));
        states.add(c);
        c = history.apply(c, Edit.remove(c, 1));
        states.add(c);
        c = history.apply(c, Edit.stage(c, 3));
        states.add(c);
        c = history.apply(c, Edit.change(Edit.Kind.ARM_LENGTH, c, 1, c.program().parts().get(1).withLength(2)));
        states.add(c);
        for (int i = states.size() - 2; i >= 0; i--) {
            c = history.undo(c);
            assertEquals(states.get(i), c, "undo to state " + i);
        }
        assertEquals(start, c);
        assertFalse(history.canUndo());
        for (int i = 1; i < states.size(); i++) {
            c = history.redo(c);
            assertEquals(states.get(i), c, "redo to state " + i);
        }
        assertFalse(history.canRedo());
    }
}

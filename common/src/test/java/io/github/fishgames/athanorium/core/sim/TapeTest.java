package io.github.fishgames.athanorium.core.sim;

import io.github.fishgames.athanorium.core.part.InstructionType.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class TapeTest {
    private static String show(Tape t) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            byte c = t.code(i);
            sb.append(c == 0 ? '.' : c == ' ' ? '_' : (char) c);
        }
        return sb.toString();
    }

    private static Tape tape(Object... kv) {
        TreeMap<Integer, Kind> m = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Integer) kv[i], (Kind) kv[i + 1]);
        }
        return Tape.compile(m, Vec.ZERO, 1, TrackTable.of(List.of()));
    }

    @Test
    void emptyTapeHasNoLength() {
        assertEquals(0, tape().length());
    }

    @Test
    void tapeStartsAtTheFirstUsedSlot() {
        Tape t = tape(2, Kind.GRAB, 3, Kind.ROTATE_CCW, 5, Kind.DROP);
        assertEquals("fa.r", show(t));
        assertEquals(2, t.startSlot());
        assertEquals(3, t.instructionCount());
    }

    @Test
    void waitIsNotCountedAsInstruction() {
        Tape t = tape(0, Kind.WAIT, 1, Kind.ROTATE_CW);
        assertEquals("_d", show(t));
        assertEquals(1, t.instructionCount());
    }

    @Test
    void repeatCopiesTheBlockBeforeIt() {
        Tape t = tape(0, Kind.ROTATE_CCW, 1, Kind.ROTATE_CCW, 4, Kind.REPEAT);
        assertEquals("aa..aa", show(t));
        assertEquals(4, t.instructionCount());
    }

    @Test
    void laterInstructionOverwritesRepeat() {
        Tape t = tape(0, Kind.ROTATE_CCW, 2, Kind.ROTATE_CW, 3, Kind.REPEAT, 4, Kind.GRAB);
        assertEquals("a.dafd", show(t));
    }

    @Test
    void resetUndoesGrabAndRotation() {
        assertEquals("fard", show(tape(0, Kind.GRAB, 1, Kind.ROTATE_CCW, 2, Kind.RESET)));
    }

    @Test
    void resetOfThreeStepsTakesTheThreeStepsBack() {
        assertEquals("aaaddd", show(tape(0, Kind.ROTATE_CCW, 1, Kind.ROTATE_CCW, 2, Kind.ROTATE_CCW, 3, Kind.RESET)));
    }

    @Test
    void resetTakesShorterWay() {
        // four counter-clockwise steps are undone by two more counter-clockwise steps (a full turn is six)
        String s = show(tape(0, Kind.ROTATE_CCW, 1, Kind.ROTATE_CCW, 2, Kind.ROTATE_CCW, 3, Kind.ROTATE_CCW,
                4, Kind.RESET));
        assertEquals("aaaaaa", s);
    }

    @Test
    void repeatOverlappingAResetIsAnError() {
        TapeException e = assertThrows(TapeException.class,
                () -> tape(0, Kind.GRAB, 1, Kind.ROTATE_CCW, 2, Kind.RESET, 3, Kind.REPEAT));
        assertEquals("program.error.repeat_overlaps_reset", e.key());
    }
}

package org.example.resourcemod.core.slot;

import org.example.resourcemod.core.id.Id;
import org.example.resourcemod.core.program.Program;

import java.util.List;

/**
 * What a player edits: the chosen puzzle (null while none is chosen), the chosen stage and the program.
 * Two contents are the same state exactly when they are equal.
 */
public record SlotContent(Id puzzle, int stage, Program program) {
    public static final SlotContent EMPTY = new SlotContent(null, 1, new Program(List.of()));

    public SlotContent withProgram(Program p) {
        return new SlotContent(puzzle, stage, p);
    }

    public SlotContent withStage(int s) {
        return new SlotContent(puzzle, s, program);
    }
}

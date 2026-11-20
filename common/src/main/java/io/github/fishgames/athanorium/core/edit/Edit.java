package io.github.fishgames.athanorium.core.edit;

import io.github.fishgames.athanorium.core.program.Part;
import io.github.fishgames.athanorium.core.program.Program;
import io.github.fishgames.athanorium.core.slot.SlotContent;

import java.util.ArrayList;
import java.util.List;

/**
 * One named editing step. Every change to a program is such a step, never a free write into the program:
 * this allows undo and redo now and server-side editing, paying on placement and spectating later.
 * A step stores exactly what is needed to apply it and to restore the state before it, including the place
 * of a part in the processing order.
 */
public record Edit(Kind kind, int index, Part before, Part after, int stageBefore, int stageAfter) {
    public enum Kind {
        PLACE, REMOVE, MOVE, ROTATE, ARM_LENGTH, TRACK_EXTEND, TRACK_SHORTEN,
        INSTRUCTION_SET, INSTRUCTION_DELETE, INSTRUCTION_MOVE, STAGE
    }

    /** A new part always goes to the end of the processing order. */
    public static Edit place(SlotContent c, Part part) {
        return new Edit(Kind.PLACE, c.program().parts().size(), null, part, 0, 0);
    }

    public static Edit remove(SlotContent c, int index) {
        return new Edit(Kind.REMOVE, index, c.program().parts().get(index), null, 0, 0);
    }

    /** Replaces the part at the index; the kind names what was changed. */
    public static Edit change(Kind kind, SlotContent c, int index, Part after) {
        return new Edit(kind, index, c.program().parts().get(index), after, 0, 0);
    }

    public static Edit stage(SlotContent c, int newStage) {
        return new Edit(Kind.STAGE, -1, null, null, c.stage(), newStage);
    }

    /** True if applying the step would change nothing. */
    public boolean isNoOp() {
        if (kind == Kind.STAGE) {
            return stageBefore == stageAfter;
        }
        return before != null && before.equals(after);
    }

    public SlotContent apply(SlotContent c) {
        if (kind == Kind.STAGE) {
            return c.withStage(stageAfter);
        }
        List<Part> parts = new ArrayList<>(c.program().parts());
        switch (kind) {
            case PLACE -> parts.add(index, after);
            case REMOVE -> parts.remove(index);
            default -> parts.set(index, after);
        }
        return c.withProgram(new Program(parts));
    }

    public SlotContent revert(SlotContent c) {
        if (kind == Kind.STAGE) {
            return c.withStage(stageBefore);
        }
        List<Part> parts = new ArrayList<>(c.program().parts());
        switch (kind) {
            case PLACE -> parts.remove(index);
            case REMOVE -> parts.add(index, before);
            default -> parts.set(index, before);
        }
        return c.withProgram(new Program(parts));
    }

    /** Translation key of the step's name. */
    public String nameKey() {
        return "edit." + kind.name().toLowerCase(java.util.Locale.ROOT);
    }
}

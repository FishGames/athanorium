package org.example.resourcemod.core.edit;

import org.example.resourcemod.core.slot.SlotContent;

import java.util.ArrayDeque;
import java.util.Deque;

/** Multi-level undo and redo over the named editing steps. Lives in the client and ends when the GUI closes. */
public final class EditHistory {
    private final Deque<Edit> undo = new ArrayDeque<>();
    private final Deque<Edit> redo = new ArrayDeque<>();

    /** Applies a step and records it. No-op steps are not recorded. */
    public SlotContent apply(SlotContent content, Edit edit) {
        if (edit.isNoOp()) {
            return content;
        }
        undo.push(edit);
        redo.clear();
        return edit.apply(content);
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public Edit peekUndo() {
        return undo.peek();
    }

    public Edit peekRedo() {
        return redo.peek();
    }

    public SlotContent undo(SlotContent content) {
        if (undo.isEmpty()) {
            return content;
        }
        Edit e = undo.pop();
        redo.push(e);
        return e.revert(content);
    }

    public SlotContent redo(SlotContent content) {
        if (redo.isEmpty()) {
            return content;
        }
        Edit e = redo.pop();
        undo.push(e);
        return e.apply(content);
    }

    /** A puzzle change empties the history; it cannot be undone. */
    public void clear() {
        undo.clear();
        redo.clear();
    }
}

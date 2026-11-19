package org.example.resourcemod.core.slot;

import java.util.UUID;

/**
 * A program slot: the whole state model outside of any block entity, so a multiblock can later hold several.
 * It has at most two states: the saved program and, only while that is confirmed, an unsaved change.
 * Revisions come from a counter that only ever grows and grow only when the content of a state really changes.
 * The editing lock is transient and never serialized.
 */
public final class ProgramSlot {
    private SlotState saved;
    private SlotState pending;
    private int revisionCounter;
    private transient UUID editor;

    public ProgramSlot() {
        this.saved = new SlotState(SlotContent.EMPTY, 0, false, null);
    }

    public ProgramSlot(SlotState saved, SlotState pending, int revisionCounter) {
        this.saved = saved;
        this.pending = pending;
        this.revisionCounter = revisionCounter;
    }

    public SlotState saved() {
        return saved;
    }

    /** The unsaved change or null. */
    public SlotState pending() {
        return pending;
    }

    /** The state the editor is working on: the unsaved change if there is one, else the saved program. */
    public SlotState current() {
        return pending != null ? pending : saved;
    }

    public int revisionCounter() {
        return revisionCounter;
    }

    public UUID editor() {
        return editor;
    }

    public void setEditor(UUID player) {
        this.editor = player;
    }

    /**
     * Takes over an interim state from the editor. Returns the state that now holds this content.
     * A changed content loses confirmation and result; an unchanged one keeps its revision.
     */
    public SlotState applyInterim(SlotContent content) {
        if (!saved.confirmed()) {
            if (!saved.content().equals(content)) {
                saved = new SlotState(content, ++revisionCounter, false, null);
            }
            return saved;
        }
        if (saved.content().equals(content)) {
            pending = null; // identical to the saved program again: the change is dropped automatically
            return saved;
        }
        if (pending == null || !pending.content().equals(content)) {
            pending = new SlotState(content, ++revisionCounter, false, null);
        }
        return pending;
    }

    /** Confirms the state with the given revision, if it still exists. Returns true if it did. */
    public boolean confirm(int revision, CalcResult passed) {
        SlotState s = byRevision(revision);
        if (s == null) {
            return false;
        }
        s.confirm(passed);
        return true;
    }

    public SlotState byRevision(int revision) {
        if (pending != null && pending.revision() == revision) {
            return pending;
        }
        return saved.revision() == revision ? saved : null;
    }

    /** Save: the unsaved change replaces the saved program, with its confirmation and its revision. */
    public void save() {
        if (pending != null) {
            saved = pending;
            pending = null;
        }
    }

    public void discard() {
        pending = null;
    }

    /**
     * Maps stored stages above the stage count to the last stage (after a config change). Revision,
     * confirmation and result stay: the effective field radius, and with it the checksum, is the same.
     */
    public void normalizeStages(int stageCount) {
        saved = normalized(saved, stageCount);
        pending = pending == null ? null : normalized(pending, stageCount);
    }

    private static SlotState normalized(SlotState s, int stageCount) {
        int stage = Math.max(1, Math.min(s.content().stage(), stageCount));
        if (stage == s.content().stage()) {
            return s;
        }
        return new SlotState(s.content().withStage(stage), s.revision(), s.confirmed(), s.result());
    }
}

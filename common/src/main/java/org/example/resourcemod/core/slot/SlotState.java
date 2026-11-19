package org.example.resourcemod.core.slot;

/**
 * One state of a program slot: content, revision, confirmation and the result of the last computation.
 * Confirmed means the server has re-computed a test run of exactly this content. Validity is never stored.
 */
public final class SlotState {
    private final SlotContent content;
    private final int revision;
    private boolean confirmed;
    private CalcResult result;

    public SlotState(SlotContent content, int revision, boolean confirmed, CalcResult result) {
        this.content = content;
        this.revision = revision;
        this.confirmed = confirmed;
        this.result = result;
    }

    public SlotContent content() {
        return content;
    }

    public int revision() {
        return revision;
    }

    public boolean confirmed() {
        return confirmed;
    }

    public CalcResult result() {
        return result;
    }

    void confirm(CalcResult passed) {
        this.confirmed = true;
        this.result = passed;
    }

    /** Replaces the stored result after a re-computation. Does not change the confirmation. */
    public void setResult(CalcResult newResult) {
        this.result = newResult;
    }
}

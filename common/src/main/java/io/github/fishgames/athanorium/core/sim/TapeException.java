package io.github.fishgames.athanorium.core.sim;

/** A tape that cannot be built (repeat overlapping a reset, two instructions on one slot, ...). */
public final class TapeException extends RuntimeException {
    private final String key;

    public TapeException(String key, String message) {
        super(message);
        this.key = key;
    }

    /** Translation key of the error. */
    public String key() {
        return key;
    }
}

package org.example.resourcemod.core.id;

/**
 * Central place for the placeholder mod id. The final mod name is still open,
 * so everything that needs the id reads it from here.
 */
public final class ModIds {
    /** Working id. Must be final before the first public release, because data ids stay stable afterwards. */
    public static final String MOD_ID = "resourcemod";

    private ModIds() {
    }
}

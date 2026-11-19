package org.example.resourcemod.core.registry;

import org.example.resourcemod.core.id.Id;

/**
 * Anything that lives in a registry. The version number is part of the fingerprint: every change that can alter
 * a simulation result must raise the version of the type (or the global rules version).
 */
public interface Registered {
    Id id();

    int version();
}

package org.example.resourcemod.core.program;

import java.util.List;

/**
 * The parts of a solution. The list order is the processing order inside a cycle (arms and glyphs run in this
 * order) and is kept unchanged by loading, saving and exporting.
 */
public record Program(List<Part> parts) {
    public Program {
        parts = List.copyOf(parts);
    }
}

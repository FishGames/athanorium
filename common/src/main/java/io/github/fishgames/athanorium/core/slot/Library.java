package io.github.fishgames.athanorium.core.slot;

import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.score.PartValues;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Immutable snapshot of everything loaded from datapacks: puzzles (sorted by id) and part values. */
public record Library(Map<Id, Puzzle> puzzles, PartValues values, int generation) {
    public static final Library EMPTY = new Library(Map.of(), new PartValues(), 0);

    public Library {
        puzzles = Collections.unmodifiableMap(new TreeMap<>(puzzles));
    }

    public Puzzle puzzle(Id id) {
        return id == null ? null : puzzles.get(id);
    }
}

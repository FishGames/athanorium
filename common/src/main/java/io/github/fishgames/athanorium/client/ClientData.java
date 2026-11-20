package io.github.fishgames.athanorium.client;

import io.github.fishgames.athanorium.ModCore;
import io.github.fishgames.athanorium.base.config.ModConfig;
import io.github.fishgames.athanorium.core.Rules;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.json.DataException;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.puzzle.PuzzleReader;
import io.github.fishgames.athanorium.core.score.PartValue;
import io.github.fishgames.athanorium.core.score.PartValues;
import io.github.fishgames.athanorium.core.slot.Library;
import io.github.fishgames.athanorium.data.ServerData;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the client knows from the server's sync message: puzzles, part values, the number of stages, the packet
 * limits and the versions of the simulation rules and of all registered types. If a version differs from the
 * client's own, the GUI warns and submits no test runs (the server would reject them anyway).
 */
public final class ClientData {
    private static Library library = Library.EMPTY;
    private static int stages = ModConfig.DEFAULT.stages();
    private static int maxParts = ModConfig.DEFAULT.maxParts();
    private static int maxInstructions = ModConfig.DEFAULT.maxInstructions();
    private static List<String> mismatches = List.of();

    private ClientData() {
    }

    public static Library library() {
        return library;
    }

    public static int stages() {
        return stages;
    }

    public static int maxParts() {
        return maxParts;
    }

    public static int maxInstructions() {
        return maxInstructions;
    }

    /** Human readable list of differing versions and types; empty if client and server match. */
    public static List<String> mismatches() {
        return mismatches;
    }

    static void readSync(DataInputStream in) throws IOException {
        int generation = in.readInt();
        stages = in.readInt();
        maxParts = in.readInt();
        maxInstructions = in.readInt();
        List<String> diff = new ArrayList<>();
        int rules = in.readInt();
        if (rules != Rules.VERSION) {
            diff.add("rules: server " + rules + ", client " + Rules.VERSION);
        }
        Map<String, Integer> own = ServerData.typeVersions();
        Map<String, Integer> server = new TreeMap<>();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            server.put(in.readUTF(), in.readInt());
        }
        for (Map.Entry<String, Integer> e : server.entrySet()) {
            Integer mine = own.get(e.getKey());
            if (mine == null) {
                diff.add(e.getKey() + ": missing on client");
            } else if (!mine.equals(e.getValue())) {
                diff.add(e.getKey() + ": server " + e.getValue() + ", client " + mine);
            }
        }
        for (String key : own.keySet()) {
            if (!server.containsKey(key)) {
                diff.add(key + ": missing on server");
            }
        }
        PartValues values = new PartValues();
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            values.put(Id.parse(in.readUTF()), new PartValue(in.readInt(), in.readBoolean()));
        }
        Map<Id, Puzzle> puzzles = new TreeMap<>();
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            String id = in.readUTF();
            byte[] json = new byte[in.readInt()];
            in.readFully(json);
            try {
                puzzles.put(Id.parse(id), PuzzleReader.read(new String(json, StandardCharsets.UTF_8), id,
                        ModCore.REG, new ArrayList<>()));
            } catch (DataException | IllegalArgumentException e) {
                ModCore.LOGGER.warn("Cannot read synced puzzle {}: {}", id, e.getMessage());
            }
        }
        library = new Library(puzzles, values, generation);
        mismatches = List.copyOf(diff);
    }
}

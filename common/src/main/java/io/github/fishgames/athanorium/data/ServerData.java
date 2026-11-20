package io.github.fishgames.athanorium.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import io.github.fishgames.athanorium.ModCore;
import io.github.fishgames.athanorium.base.config.ModConfig;
import io.github.fishgames.athanorium.base.network.Net;
import io.github.fishgames.athanorium.base.platform.Services;
import io.github.fishgames.athanorium.core.Rules;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.puzzle.PuzzleWriter;
import io.github.fishgames.athanorium.core.registry.Registered;
import io.github.fishgames.athanorium.core.score.PartValue;
import io.github.fishgames.athanorium.core.slot.Library;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Server-side data: the config and the library loaded from datapacks. Datapack data only lives on the server;
 * clients get it with a sync message at login and after every /reload.
 */
public final class ServerData {
    private static volatile Library library = Library.EMPTY;
    private static volatile ModConfig config = ModConfig.DEFAULT;
    private static int generation;

    private ServerData() {
    }

    public static Library library() {
        return library;
    }

    public static ModConfig config() {
        return config;
    }

    /** Counts datapack loads. A worker result made for an older generation is discarded. */
    public static int generation() {
        return library.generation();
    }

    /** Reads the config. The number of stages is only read here, a change needs a restart. */
    public static void onServerStarting() {
        config = ModConfig.load(Services.platform().configDir());
    }

    /** Called by the reload listener of the loader. */
    public static void reload(ResourceManager manager) {
        library = PuzzleLoader.load(manager, ++generation);
    }

    /** All registered types with their versions, keyed "kind id". */
    public static Map<String, Integer> typeVersions() {
        Map<String, Integer> out = new TreeMap<>();
        put(out, "part", ModCore.REG.parts.values());
        put(out, "instruction", ModCore.REG.instructions.values());
        put(out, "element", ModCore.REG.elements.values());
        put(out, "bond", ModCore.REG.bondTypes.values());
        put(out, "metric", ModCore.REG.metrics.values());
        return out;
    }

    private static void put(Map<String, Integer> out, String kind, Iterable<? extends Registered> entries) {
        for (Registered r : entries) {
            out.put(kind + " " + r.id(), r.version());
        }
    }

    public static void syncTo(ServerPlayer player) {
        Library lib = library;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(Net.S_SYNC);
            out.writeInt(lib.generation());
            out.writeInt(config.stages());
            out.writeInt(config.maxParts());
            out.writeInt(config.maxInstructions());
            out.writeInt(Rules.VERSION);
            Map<String, Integer> versions = typeVersions();
            out.writeInt(versions.size());
            for (Map.Entry<String, Integer> e : versions.entrySet()) {
                out.writeUTF(e.getKey());
                out.writeInt(e.getValue());
            }
            out.writeInt(lib.values().all().size());
            for (Map.Entry<Id, PartValue> e : lib.values().all().entrySet()) {
                out.writeUTF(e.getKey().toString());
                out.writeInt((int) e.getValue().value());
                out.writeBoolean(e.getValue().perHex());
            }
            out.writeInt(lib.puzzles().size());
            for (Map.Entry<Id, Puzzle> e : lib.puzzles().entrySet()) {
                out.writeUTF(e.getKey().toString());
                byte[] json = PuzzleWriter.write(e.getValue()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.writeInt(json.length);
                out.write(json);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Net.sendToPlayer(player, bytes.toByteArray());
    }
}

package io.github.fishgames.athanorium.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import io.github.fishgames.athanorium.ModCore;
import io.github.fishgames.athanorium.core.id.Id;
import io.github.fishgames.athanorium.core.json.DataException;
import io.github.fishgames.athanorium.core.part.PartType;
import io.github.fishgames.athanorium.core.puzzle.Puzzle;
import io.github.fishgames.athanorium.core.puzzle.PuzzleReader;
import io.github.fishgames.athanorium.core.score.PartValues;
import io.github.fishgames.athanorium.core.slot.Library;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads puzzles (data/&lt;namespace&gt;/puzzle/&lt;path&gt;.json) and part values (data/&lt;namespace&gt;/part_value/&lt;part&gt;.json)
 * from datapacks. The files are read as raw streams by the strict reader of the core, not through vanilla's
 * JSON reload listener, which would silently lose duplicate keys. A broken file never crashes: it is skipped
 * and a message with file name and field is logged.
 */
public final class PuzzleLoader {
    private static final String PUZZLE_DIR = "puzzle";
    private static final String VALUE_DIR = "part_value";

    private PuzzleLoader() {
    }

    public static Library load(ResourceManager manager, int generation) {
        Map<Id, Puzzle> puzzles = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : list(manager, PUZZLE_DIR).entrySet()) {
            String file = e.getKey().toString();
            try (Reader in = e.getValue().openAsReader()) {
                Id id = idOf(e.getKey(), PUZZLE_DIR);
                List<String> warnings = new ArrayList<>();
                puzzles.put(id, PuzzleReader.read(in, file, ModCore.REG, warnings));
                warnings.forEach(w -> ModCore.LOGGER.warn("Puzzle {}: {}", id, w));
            } catch (DataException | IllegalArgumentException | IOException ex) {
                ModCore.LOGGER.error("Skipping puzzle file {}: {}", file, ex.getMessage());
            }
        }
        PartValues values = new PartValues();
        for (Map.Entry<ResourceLocation, Resource> e : list(manager, VALUE_DIR).entrySet()) {
            String file = e.getKey().toString();
            try (Reader in = e.getValue().openAsReader()) {
                values.put(idOf(e.getKey(), VALUE_DIR), PartValues.read(in, file));
            } catch (DataException | IllegalArgumentException | IOException ex) {
                ModCore.LOGGER.error("Skipping part value file {}: {}", file, ex.getMessage());
            }
        }
        for (PartType pt : ModCore.REG.parts.values()) {
            if (pt.purchasable() && !values.has(pt.id())) {
                ModCore.LOGGER.warn("Part {} has no part value file and is not available", pt.id());
            }
        }
        ModCore.LOGGER.info("Loaded {} puzzles and {} part values", puzzles.size(), values.all().size());
        return new Library(puzzles, values, generation);
    }

    private static Map<ResourceLocation, Resource> list(ResourceManager manager, String dir) {
        return new TreeMap<>(manager.listResources(dir, rl -> rl.getPath().endsWith(".json")));
    }

    /** data/&lt;namespace&gt;/&lt;dir&gt;/&lt;path&gt;.json becomes &lt;namespace&gt;:&lt;path&gt;. Ids with a dot are rejected by {@link Id}. */
    private static Id idOf(ResourceLocation file, String dir) {
        String path = file.getPath();
        path = path.substring(dir.length() + 1, path.length() - ".json".length());
        return new Id(file.getNamespace(), path);
    }
}

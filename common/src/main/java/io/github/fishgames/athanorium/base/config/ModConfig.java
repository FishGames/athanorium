package io.github.fishgames.athanorium.base.config;

import io.github.fishgames.athanorium.ModCore;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Config values. Nothing in here decides whether a solution is valid, with the one exception of the number
 * of stages (it moves which stage is unbounded). Values below their minimum are raised with a warning, so
 * every solution inside the minimum limits can be submitted on every server.
 */
public record ModConfig(int stages, int maxParts, int maxInstructions, int maxTransferBytes, int programsPerTenSeconds) {
    public static final ModConfig DEFAULT = new ModConfig(4, 2_000, 200_000, 8 * 1024 * 1024, 40);

    public static ModConfig load(Path configDir) {
        Path file = configDir.resolve(ModCore.MOD_ID + ".properties");
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(in);
            } catch (IOException e) {
                ModCore.LOGGER.warn("Cannot read config {}, using defaults: {}", file, e.getMessage());
            }
        } else {
            write(file);
        }
        return new ModConfig(
                value(p, "stages", DEFAULT.stages, 2),
                value(p, "max_parts_per_program", DEFAULT.maxParts, 1_000),
                value(p, "max_instructions_per_program", DEFAULT.maxInstructions, 100_000),
                value(p, "max_transfer_bytes", DEFAULT.maxTransferBytes, 1024 * 1024),
                value(p, "programs_per_ten_seconds", DEFAULT.programsPerTenSeconds, 10));
    }

    private static int value(Properties p, String key, int def, int min) {
        String raw = p.getProperty(key);
        int v = def;
        if (raw != null) {
            try {
                v = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                ModCore.LOGGER.warn("Config value {} is not a number: '{}', using {}", key, raw, def);
            }
        }
        if (v < min) {
            ModCore.LOGGER.warn("Config value {} = {} is below the minimum, using {}", key, v, min);
            v = min;
        }
        return v;
    }

    private static void write(Path file) {
        String text = """
                # Number of stages. Stage 1 has field radius 2, each further stage one more, the last stage is
                # unbounded. Minimum 2. Read only when the server starts.
                stages=4
                # Limits for programs sent by clients. They never apply to stored programs.
                max_parts_per_program=2000
                max_instructions_per_program=200000
                # Upper limit of the buffer in which split packets are put together again.
                max_transfer_bytes=8388608
                # Rate limit per player, counted per complete program, not per partial packet.
                programs_per_ten_seconds=40
                """;
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                out.write(text);
            }
        } catch (IOException e) {
            ModCore.LOGGER.warn("Cannot write default config {}: {}", file, e.getMessage());
        }
    }
}

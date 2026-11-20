package io.github.fishgames.athanorium;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import io.github.fishgames.athanorium.core.id.ModIds;
import io.github.fishgames.athanorium.core.registry.Registries;
import org.slf4j.Logger;

/** Shared constants of the Minecraft side: mod id, logger and the registries of the simulation core. */
public final class ModCore {
    public static final String MOD_ID = ModIds.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Arms, glyphs, elements, bond types, instructions and metrics. Filled once, never changed afterwards. */
    public static final Registries REG = Registries.createDefault();

    private ModCore() {
    }

    /** The one place that creates mod identifiers; other Minecraft versions only need this line changed. */
    @SuppressWarnings("removal")
    public static ResourceLocation rl(String path) {
        return new ResourceLocation(MOD_ID + ":" + path);
    }
}

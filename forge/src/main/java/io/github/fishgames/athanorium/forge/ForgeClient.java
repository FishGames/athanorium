package io.github.fishgames.athanorium.forge;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import io.github.fishgames.athanorium.base.registry.ModContent;
import io.github.fishgames.athanorium.client.ClientNet;
import io.github.fishgames.athanorium.client.PuzzleScreen;

/** Client-only part of the Forge glue. Only ever loaded on the physical client. */
public final class ForgeClient {
    private ForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(ForgeClient::clientSetup);
        if (System.getenv("RESOURCEMOD_SMOKETEST") != null) io.github.fishgames.athanorium.client.SmokeTest.init();
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModContent.DEVELOPER_MENU.get(), PuzzleScreen::new));
    }

    static void receive(byte[] frame) {
        ClientNet.receive(frame);
    }
}

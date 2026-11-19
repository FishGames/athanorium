package org.example.resourcemod.forge;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.example.resourcemod.base.registry.ModContent;
import org.example.resourcemod.client.ClientNet;
import org.example.resourcemod.client.PuzzleScreen;

/** Client-only part of the Forge glue. Only ever loaded on the physical client. */
public final class ForgeClient {
    private ForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(ForgeClient::clientSetup);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModContent.DEVELOPER_MENU.get(), PuzzleScreen::new));
    }

    static void receive(byte[] frame) {
        ClientNet.receive(frame);
    }
}

package org.example.resourcemod.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import org.example.resourcemod.base.network.Net;
import org.example.resourcemod.base.registry.ModContent;
import org.example.resourcemod.client.ClientNet;
import org.example.resourcemod.client.PuzzleScreen;

/** Fabric client entry point: registers the screen and the receiver for messages from the server. */
public final class FabricClientEntry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModContent.DEVELOPER_MENU.get(), PuzzleScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(Net.CHANNEL, (client, handler, buf, sender) -> {
            byte[] frame = new byte[buf.readableBytes()];
            buf.readBytes(frame);
            client.execute(() -> ClientNet.receive(frame));
        });
    }
}

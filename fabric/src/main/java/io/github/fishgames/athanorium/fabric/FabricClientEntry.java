package io.github.fishgames.athanorium.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import io.github.fishgames.athanorium.base.network.Net;
import io.github.fishgames.athanorium.base.registry.ModContent;
import io.github.fishgames.athanorium.client.ClientNet;
import io.github.fishgames.athanorium.client.PuzzleScreen;

/** Fabric client entry point: registers the screen and the receiver for messages from the server. */
public final class FabricClientEntry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (System.getenv("RESOURCEMOD_SMOKETEST") != null) io.github.fishgames.athanorium.client.SmokeTest.init();
        MenuScreens.register(ModContent.DEVELOPER_MENU.get(), PuzzleScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(Net.CHANNEL, (client, handler, buf, sender) -> {
            byte[] frame = new byte[buf.readableBytes()];
            buf.readBytes(frame);
            client.execute(() -> ClientNet.receive(frame));
        });
    }
}

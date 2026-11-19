package org.example.resourcemod.base;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.example.resourcemod.block.SlotServer;
import org.example.resourcemod.data.ServerData;

/** The few events the shared code needs. Each loader module forwards its own events to these methods. */
public final class CommonEvents {
    private CommonEvents() {
    }

    public static void serverStarting(MinecraftServer server) {
        ServerData.onServerStarting();
    }

    public static void serverStopping(MinecraftServer server) {
        SlotServer.onServerStopping();
    }

    public static void serverTick(MinecraftServer server) {
        SlotServer.serverTick(server);
    }

    /** Datapacks were loaded or reloaded. */
    public static void reload(ResourceManager manager) {
        ServerData.reload(manager);
    }

    /** A player logged in or datapacks were reloaded: send the datapack data. */
    public static void syncTo(ServerPlayer player) {
        ServerData.syncTo(player);
    }

    /** A frame from a client arrived; must be called on the server main thread. */
    public static void receiveFromClient(ServerPlayer player, byte[] frame) {
        SlotServer.receive(player, frame);
    }
}

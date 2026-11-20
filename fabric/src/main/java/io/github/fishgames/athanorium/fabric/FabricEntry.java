package io.github.fishgames.athanorium.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import io.github.fishgames.athanorium.ModCore;
import io.github.fishgames.athanorium.base.CommonEvents;
import io.github.fishgames.athanorium.base.network.Net;
import io.github.fishgames.athanorium.base.platform.Services;
import io.github.fishgames.athanorium.base.registry.ModContent;
import io.github.fishgames.athanorium.base.registry.Registrar;

import java.util.function.Supplier;

/** Fabric entry point: only connects Fabric's registration, events and networking to the shared code. */
public final class FabricEntry implements ModInitializer {
    @Override
    public void onInitialize() {
        Services.set(new FabricPlatform());
        ModContent.register(new Registrar() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T extends R> Supplier<T> register(ResourceKey<Registry<R>> registry, String name,
                                                         Supplier<T> factory) {
                Registry<R> target = (Registry<R>) BuiltInRegistries.REGISTRY.get(registry.location());
                T value = Registry.register(target, ModCore.rl(name), factory.get());
                return () -> value;
            }
        });

        ServerLifecycleEvents.SERVER_STARTING.register(CommonEvents::serverStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(CommonEvents::serverStopping);
        ServerTickEvents.END_SERVER_TICK.register(CommonEvents::serverTick);
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> CommonEvents.syncTo(player));

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return ModCore.rl("puzzles");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        CommonEvents.reload(manager);
                    }
                });

        ServerPlayNetworking.registerGlobalReceiver(Net.CHANNEL, (server, player, handler, buf, sender) -> {
            byte[] frame = new byte[buf.readableBytes()];
            buf.readBytes(frame);
            server.execute(() -> CommonEvents.receiveFromClient(player, frame));
        });
    }
}

package org.example.resourcemod.forge;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import org.example.resourcemod.ModCore;
import org.example.resourcemod.base.CommonEvents;
import org.example.resourcemod.base.network.Net;
import org.example.resourcemod.base.platform.Services;
import org.example.resourcemod.base.registry.ModContent;
import org.example.resourcemod.base.registry.Registrar;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Forge entry point: only connects Forge's registration, events and networking to the shared code. */
@Mod(ModCore.MOD_ID)
public final class ForgeEntry {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Net.CHANNEL, () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    /** One network frame; the shared code splits and joins messages itself. */
    public record Frame(byte[] data) {
    }

    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new HashMap<>();

    public ForgeEntry(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        Services.set(new ForgePlatform());
        ModContent.register(new Registrar() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T extends R> Supplier<T> register(ResourceKey<Registry<R>> registry, String name,
                                                         Supplier<T> factory) {
                DeferredRegister<R> register = (DeferredRegister<R>) registers.get(registry);
                if (register == null) {
                    register = DeferredRegister.create(registry, ModCore.MOD_ID);
                    register.register(modBus);
                    registers.put(registry, register);
                }
                return register.register(name, factory);
            }
        });

        CHANNEL.registerMessage(0, Frame.class, (frame, buf) -> buf.writeByteArray(frame.data()),
                buf -> new Frame(buf.readByteArray()), ForgeEntry::handle);

        MinecraftForge.EVENT_BUS.addListener(ForgeEntry::serverStarting);
        MinecraftForge.EVENT_BUS.addListener(ForgeEntry::serverStopping);
        MinecraftForge.EVENT_BUS.addListener(ForgeEntry::serverTick);
        MinecraftForge.EVENT_BUS.addListener(ForgeEntry::addReloadListener);
        MinecraftForge.EVENT_BUS.addListener(ForgeEntry::datapackSync);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeClient.init(modBus));
    }

    private static void handle(Frame frame, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            ServerPlayer sender = context.getSender();
            context.enqueueWork(() -> {
                if (sender != null) {
                    CommonEvents.receiveFromClient(sender, frame.data());
                }
            });
        } else {
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ForgeClient.receive(frame.data())));
        }
        context.setPacketHandled(true);
    }

    private static void serverStarting(ServerStartingEvent event) {
        CommonEvents.serverStarting(event.getServer());
    }

    private static void serverStopping(ServerStoppingEvent event) {
        CommonEvents.serverStopping(event.getServer());
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CommonEvents.serverTick(event.getServer());
        }
    }

    private static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) CommonEvents::reload);
    }

    private static void datapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            CommonEvents.syncTo(event.getPlayer());
        } else {
            event.getPlayerList().getPlayers().forEach(CommonEvents::syncTo);
        }
    }
}

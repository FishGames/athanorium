package io.github.fishgames.athanorium.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import io.github.fishgames.athanorium.base.platform.Platform;

import java.nio.file.Path;
import java.util.function.BiFunction;

public final class ForgePlatform implements Platform {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public void sendToServer(byte[] frame) {
        ForgeEntry.CHANNEL.sendToServer(new ForgeEntry.Frame(frame));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, byte[] frame) {
        ForgeEntry.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ForgeEntry.Frame(frame));
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory,
                                                                      Block... blocks) {
        return BlockEntityType.Builder.of(factory::apply, blocks).build(null);
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> menuType(BiFunction<Integer, Inventory, T> factory) {
        return IForgeMenuType.create((containerId, inventory, extraData) -> factory.apply(containerId, inventory));
    }
}

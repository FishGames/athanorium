package io.github.fishgames.athanorium.base.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;
import java.util.function.BiFunction;

/**
 * Everything the shared code needs from the mod loader. Each loader module provides one implementation
 * and sets it in {@link Services} before anything else runs.
 */
public interface Platform {
    /** Directory for config files. */
    Path configDir();

    /** Sends one network frame to the server (client side only). */
    void sendToServer(byte[] frame);

    /** Sends one network frame to a player. */
    void sendToPlayer(ServerPlayer player, byte[] frame);

    /** Vanilla keeps the factory interfaces of these two types private; each loader opens them in its own way. */
    <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks);

    <T extends AbstractContainerMenu> MenuType<T> menuType(BiFunction<Integer, Inventory, T> factory);
}

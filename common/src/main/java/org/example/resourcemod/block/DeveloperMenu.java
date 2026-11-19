package org.example.resourcemod.block;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.example.resourcemod.base.registry.ModContent;

/**
 * Menu of the developer block. It has no item slots; it exists so the server knows who has the GUI open.
 * The editing lock is released in exactly one place: when this menu is closed on the server. That covers
 * closing, disconnect, death and changing dimensions.
 */
public class DeveloperMenu extends AbstractContainerMenu {
    private final DeveloperBlockEntity blockEntity;

    public DeveloperMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public DeveloperMenu(int containerId, Inventory inventory, DeveloperBlockEntity blockEntity) {
        super(ModContent.DEVELOPER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
    }

    public DeveloperBlockEntity blockEntity() {
        return blockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Online state, reach and permission. If this fails the server closes the menu and the lock is released. */
    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null) {
            return true;
        }
        if (blockEntity.isRemoved() || blockEntity.getLevel() == null
                || blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) != blockEntity) {
            return false;
        }
        if (!SlotServer.mayUse(player)) {
            return false;
        }
        return player.distanceToSqr(blockEntity.getBlockPos().getX() + 0.5, blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (blockEntity != null && player instanceof ServerPlayer sp) {
            SlotServer.onMenuClosed(sp, blockEntity);
        }
    }
}

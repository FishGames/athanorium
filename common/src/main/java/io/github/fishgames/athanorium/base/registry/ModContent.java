package io.github.fishgames.athanorium.base.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import io.github.fishgames.athanorium.ModCore;
import io.github.fishgames.athanorium.base.platform.Services;
import io.github.fishgames.athanorium.block.DeveloperBlock;
import io.github.fishgames.athanorium.block.DeveloperBlockEntity;
import io.github.fishgames.athanorium.block.DeveloperMenu;

import java.util.function.Supplier;

/** All blocks, items, block entities, menus and the creative tab of the mod. */
public final class ModContent {
    public static Supplier<DeveloperBlock> DEVELOPER_BLOCK;
    public static Supplier<Item> DEVELOPER_BLOCK_ITEM;
    public static Supplier<BlockEntityType<DeveloperBlockEntity>> DEVELOPER_BLOCK_ENTITY;
    public static Supplier<MenuType<DeveloperMenu>> DEVELOPER_MENU;
    public static Supplier<CreativeModeTab> TAB;

    private ModContent() {
    }

    // CreativeModeTab.builder(Row, int) is deprecated on Forge only because Forge adds its own builder();
    // common code has to use the vanilla one so that it also compiles for Fabric.
    @SuppressWarnings("deprecation")
    public static void register(Registrar r) {
        DEVELOPER_BLOCK = r.register(Registries.BLOCK, "developer_block", () -> new DeveloperBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0f, 6.0f)
                        .sound(SoundType.AMETHYST).lightLevel(state -> 7)));
        DEVELOPER_BLOCK_ITEM = r.register(Registries.ITEM, "developer_block",
                () -> new BlockItem(DEVELOPER_BLOCK.get(), new Item.Properties()));
        DEVELOPER_BLOCK_ENTITY = r.register(Registries.BLOCK_ENTITY_TYPE, "developer_block",
                () -> Services.platform().blockEntityType(DeveloperBlockEntity::new, DEVELOPER_BLOCK.get()));
        DEVELOPER_MENU = r.register(Registries.MENU, "developer_block",
                () -> Services.platform().menuType(DeveloperMenu::new));
        TAB = r.register(Registries.CREATIVE_MODE_TAB, "main", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup." + ModCore.MOD_ID + ".main"))
                .icon(() -> new ItemStack(DEVELOPER_BLOCK_ITEM.get()))
                .displayItems((params, output) -> output.accept(DEVELOPER_BLOCK_ITEM.get()))
                .build());
    }
}

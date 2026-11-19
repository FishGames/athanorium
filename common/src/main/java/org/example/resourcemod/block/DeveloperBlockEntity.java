package org.example.resourcemod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.example.resourcemod.ModCore;
import org.example.resourcemod.base.registry.ModContent;
import org.example.resourcemod.core.json.DataException;
import org.example.resourcemod.core.slot.ProgramSlot;
import org.example.resourcemod.core.slot.SlotCodec;
import org.example.resourcemod.data.ServerData;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds exactly one program slot. The slot is stored in the block data in the same compact form as in packets.
 * Programs are never part of the update tag (they would end up in every chunk packet); the client gets them
 * only when the GUI opens. Reading and writing is kept in this one place because the storage methods change
 * between Minecraft versions.
 */
public class DeveloperBlockEntity extends BlockEntity {
    private static final String SLOT_KEY = "slot";

    private ProgramSlot slot = new ProgramSlot();
    /** Datapack generation this block was last checked against; -1 = never (checked when it is loaded). */
    private int checkedGeneration = -1;
    /** Revisions with a computation in progress. Never stored. */
    final Set<Integer> computing = new HashSet<>();

    public DeveloperBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.DEVELOPER_BLOCK_ENTITY.get(), pos, state);
    }

    public ProgramSlot slot() {
        return slot;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(SLOT_KEY)) {
            try {
                slot = SlotCodec.decode(tag.getByteArray(SLOT_KEY));
            } catch (DataException e) {
                ModCore.LOGGER.error("Developer block at {}: stored program is unreadable and was dropped: {}",
                        getBlockPos(), e.getMessage());
                slot = new ProgramSlot();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putByteArray(SLOT_KEY, SlotCodec.encode(slot));
    }

    /** Re-checks the stored solutions after the block was loaded and after every /reload. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, DeveloperBlockEntity be) {
        int generation = ServerData.generation();
        if (be.checkedGeneration != generation) {
            be.checkedGeneration = generation;
            SlotServer.recheck(be);
        }
    }
}

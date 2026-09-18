package com.kaleblangley.lockscontinue.lock;

import com.kaleblangley.lockscontinue.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.List;

/** Persistent state shared by the server-side generator and client renderer. */
public final class LockData {
    public static final String LOCK_CHECKED_TAG = "lockscontinue_lock_checked";
    public static final String LOCK_PENDING_TAG = "lockscontinue_lock_pending";
    public static final String LOCK_VARIANT_TAG = "lockscontinue_lock_variant";
    public static final String LOCK_UNLOCKED_TAG = "lockscontinue_lock_unlocked";

    private LockData() {
    }

    /** Returns whether this is a supported vanilla or Lootr loot container. */
    public static boolean isLockableContainer(BlockEntity blockEntity) {
        return blockEntity instanceof RandomizableContainerBlockEntity
                && getContainerFacing(blockEntity.getBlockState()) != null;
    }

    /** Returns the face on which the world lock visual should be placed. */
    public static Direction getContainerFacing(BlockState state) {
        if (state.getBlock() instanceof ChestBlock) {
            return state.getValue(ChestBlock.FACING);
        }
        if (state.getBlock() instanceof BarrelBlock) {
            return state.getValue(BarrelBlock.FACING);
        }
        if (state.getBlock() instanceof ShulkerBoxBlock) {
            return state.getValue(ShulkerBoxBlock.FACING);
        }
        return null;
    }

    /** Returns whether this container still has a lock variant to render. */
    public static boolean hasLockData(BlockEntity blockEntity) {
        return isLockableContainer(blockEntity)
                && blockEntity.getPersistentData().contains(LOCK_VARIANT_TAG, Tag.TAG_INT);
    }

    public static boolean hasLock(BlockEntity blockEntity) {
        return hasLockData(blockEntity) && !isUnlocked(blockEntity);
    }

    /**
     * Returns the other half of a double chest without asking the level to
     * load a chunk. Barrels, shulker boxes and single chests have no connected position.
     */
    public static BlockPos getConnectedPos(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return null;
        }
        return pos.relative(ChestBlock.getConnectedDirection(state));
    }

    /** Returns the loaded block entities that make up this container. */
    public static List<RandomizableContainerBlockEntity> getConnectedContainers(
            BlockGetter level, RandomizableContainerBlockEntity container) {
        List<RandomizableContainerBlockEntity> containers = new ArrayList<>(2);
        containers.add(container);

        if (!(container instanceof ChestBlockEntity chest)) {
            return containers;
        }

        BlockPos connectedPos = getConnectedPos(chest.getBlockPos(), chest.getBlockState());
        if (connectedPos == null) {
            return containers;
        }

        BlockEntity connected = level.getBlockEntity(connectedPos);
        if (connected instanceof RandomizableContainerBlockEntity connectedContainer
                && isLockableContainer(connectedContainer)
                && !connectedContainer.getBlockPos().equals(chest.getBlockPos())) {
            containers.add(connectedContainer);
        }
        return containers;
    }

    public static boolean isUnlocked(BlockEntity blockEntity) {
        return isLockableContainer(blockEntity)
                && blockEntity.getPersistentData().getBoolean(LOCK_UNLOCKED_TAG);
    }

    /** Applies a player-selected lock and marks the container as checked. */
    public static void applyLock(RandomizableContainerBlockEntity container, LockType type) {
        CompoundTag persistentData = container.getPersistentData();
        persistentData.putBoolean(LOCK_CHECKED_TAG, true);
        persistentData.remove(LOCK_PENDING_TAG);
        persistentData.remove(LOCK_UNLOCKED_TAG);
        persistentData.putInt(LOCK_VARIANT_TAG, type.variant());
    }

    public static int getVariant(BlockEntity blockEntity) {
        if (!isLockableContainer(blockEntity)) {
            return -1;
        }

        CompoundTag persistentData = blockEntity.getPersistentData();
        if (!hasLockData(blockEntity)) {
            return -1;
        }
        return persistentData.getInt(LOCK_VARIANT_TAG);
    }

    public static ItemStack getLockStack(BlockEntity blockEntity) {
        if (!isLockableContainer(blockEntity)) {
            return ItemStack.EMPTY;
        }

        CompoundTag persistentData = blockEntity.getPersistentData();
        if (!hasLockData(blockEntity)) {
            return ItemStack.EMPTY;
        }

        int variant = persistentData.getInt(LOCK_VARIANT_TAG);
        return new ItemStack(lockItem(variant));
    }

    public static Item lockItem(int variant) {
        return switch (Math.floorMod(variant, 4)) {
            case 0 -> ModItems.MEMORY_LOCK.get();
            case 1 -> ModItems.PIN_LOCK.get();
            case 2 -> ModItems.PIPE_LOCK.get();
            default -> ModItems.ROTARY_LOCK.get();
        };
    }

}

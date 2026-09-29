package com.kaleblangley.lockscontinue.world;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.lock.LockData;
import com.kaleblangley.lockscontinue.lock.LockInstance;
import com.kaleblangley.lockscontinue.lock.definition.LockDefinitionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Adds persistent lock data to a percentage of newly generated loot containers. */
@Mod.EventBusSubscriber(modid = LocksContinue.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LootChestLockGenerator {
    private LootChestLockGenerator() {
    }

    /**
     * Marks a loot container for processing once its world-gen level is
     * available. This is used by the instance and NBT-loading Mixin hooks.
     */
    public static void markLootChest(RandomizableContainerBlockEntity container) {
        if (!LockData.isLockableContainer(container)) {
            return;
        }

        if (container.getLevel() != null && container.getLevel().isClientSide) {
            return;
        }

        if (container.getLevel() == null) {
            markLootChestPending(container);
            return;
        }

        for (RandomizableContainerBlockEntity connectedContainer
                : getAvailableContainers(container.getLevel(), container)) {
            markLootChestPending(connectedContainer);
        }
    }

    /**
     * Handles the static vanilla loot-table helper while it still has the
     * ServerLevelAccessor used by structure generation.
     */
    public static void tryGenerateLock(BlockGetter level, RandomSource random, BlockPos pos) {
        if (!(level instanceof WorldGenRegion)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RandomizableContainerBlockEntity container
                && LockData.isLockableContainer(container)) {
            if (tryGenerateLock(level, random, container)) {
                // WorldGenRegion already owns this chunk access. Mark it
                // directly so the generated NBT is persisted without
                // entering Level.blockEntityChanged().
                WorldGenRegion region = (WorldGenRegion) level;
                region.getChunk(pos.getX() >> 4, pos.getZ() >> 4).setUnsaved(true);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        boolean changed = false;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof RandomizableContainerBlockEntity container
                    && LockData.isLockableContainer(container)) {
                changed |= LockData.migrateLegacy(container);
                if (container.getPersistentData().getBoolean(LockData.LOCK_PENDING_TAG)) {
                    changed |= tryGenerateLock(level, level.getRandom(), container);
                }
            }
        }

        if (changed) {
            // ChunkEvent.Load is fired while ChunkMap is promoting a proto
            // chunk. BlockEntity.setChanged() would call back into
            // ServerChunkCache.getChunk() at this exact point. Mark the
            // already available chunk dirty directly instead.
            chunk.setUnsaved(true);
        }
    }

    private static boolean tryGenerateLock(BlockGetter level, RandomSource random,
                                            RandomizableContainerBlockEntity container) {
        List<RandomizableContainerBlockEntity> containers =
                getAvailableContainers(level, container);
        boolean allChecked = true;
        boolean anyChecked = false;
        LockInstance existingInstance = null;
        for (RandomizableContainerBlockEntity connectedContainer : containers) {
            CompoundTag persistentData = connectedContainer.getPersistentData();
            boolean checked = persistentData.getBoolean(LockData.LOCK_CHECKED_TAG);
            allChecked &= checked;
            anyChecked |= checked;
            if (existingInstance == null) {
                existingInstance = LockData.getLockInstance(connectedContainer);
            }
        }

        if (allChecked) {
            return synchronizeInstance(containers, existingInstance);
        }

        LockInstance instance;
        if (existingInstance != null) {
            // Complete a partially processed double chest with its existing
            // decision instead of rolling a second independent result.
            instance = existingInstance;
        } else if (anyChecked) {
            // A checked half without an instance already decided that this
            // chest should remain unlocked.
            instance = null;
        } else {
            instance = LockDefinitionManager.INSTANCE.createLootLock(
                    level,
                    random,
                    container,
                    LockData.getSourceLootTable(container));
        }

        boolean changed = false;
        for (RandomizableContainerBlockEntity connectedContainer : containers) {
            CompoundTag persistentData = connectedContainer.getPersistentData();
            if (!persistentData.getBoolean(LockData.LOCK_CHECKED_TAG)) {
                changed = true;
                persistentData.putBoolean(LockData.LOCK_CHECKED_TAG, true);
            }
            if (persistentData.contains(LockData.LOCK_PENDING_TAG)) {
                changed = true;
                persistentData.remove(LockData.LOCK_PENDING_TAG);
            }
            if (instance != null) {
                if (!instance.equals(LockData.getLockInstance(connectedContainer))
                        || !persistentData.contains(LockData.LOCK_INSTANCE_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    changed = true;
                    LockData.applyLock(connectedContainer, instance);
                }
            } else if (persistentData.contains(LockData.LOCK_INSTANCE_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)
                    || persistentData.contains(LockData.LOCK_VARIANT_TAG, net.minecraft.nbt.Tag.TAG_INT)) {
                changed = true;
                persistentData.remove(LockData.LOCK_INSTANCE_TAG);
                persistentData.remove(LockData.LOCK_VARIANT_TAG);
            }
        }
        return changed;
    }

    private static boolean synchronizeInstance(
            List<RandomizableContainerBlockEntity> containers, LockInstance instance) {
        if (instance == null) {
            return false;
        }

        boolean changed = false;
        for (RandomizableContainerBlockEntity container : containers) {
            CompoundTag persistentData = container.getPersistentData();
            if (!instance.equals(LockData.getLockInstance(container))
                    || !persistentData.contains(LockData.LOCK_INSTANCE_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                LockData.applyLock(container, instance);
                changed = true;
            }
        }
        return changed;
    }

    private static void markLootChestPending(RandomizableContainerBlockEntity container) {
        CompoundTag persistentData = container.getPersistentData();
        if (persistentData.getBoolean(LockData.LOCK_CHECKED_TAG)) {
            return;
        }

        persistentData.putBoolean(LockData.LOCK_PENDING_TAG, true);
        markOwningChunkUnsaved(container);
    }

    /**
     * Gets the double-chest pair without loading a neighboring server chunk.
     * World-gen regions already provide their own bounded block-entity view.
     */
    private static List<RandomizableContainerBlockEntity> getAvailableContainers(
            BlockGetter level, RandomizableContainerBlockEntity container) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return LockData.getConnectedContainers(level, container);
        }

        List<RandomizableContainerBlockEntity> containers = new ArrayList<>(2);
        containers.add(container);
        BlockPos connectedPos = LockData.getConnectedPos(
                container.getBlockPos(), container.getBlockState());
        if (connectedPos == null) {
            return containers;
        }

        LevelChunk connectedChunk = serverLevel.getChunkSource()
                .getChunkNow(connectedPos.getX() >> 4, connectedPos.getZ() >> 4);
        if (connectedChunk == null) {
            return containers;
        }

        BlockEntity connected = connectedChunk.getBlockEntity(connectedPos);
        if (connected instanceof RandomizableContainerBlockEntity connectedContainer
                && LockData.isLockableContainer(connectedContainer)) {
            containers.add(connectedContainer);
        }
        return containers;
    }

    /**
     * Marks a currently loaded server chunk without routing through
     * Level.blockEntityChanged(), which can synchronously request a chunk.
     */
    private static void markOwningChunkUnsaved(RandomizableContainerBlockEntity container) {
        if (!(container.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = container.getBlockPos();
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null) {
            chunk.setUnsaved(true);
        }
    }
}

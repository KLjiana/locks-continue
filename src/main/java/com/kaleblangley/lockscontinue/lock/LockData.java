package com.kaleblangley.lockscontinue.lock;

import com.kaleblangley.lockscontinue.item.ModItems;
import com.kaleblangley.lockscontinue.lock.definition.LockDefinitionManager;
import com.kaleblangley.lockscontinue.lock.definition.RewardAttemptState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent state shared by the server-side generator and client renderer. */
public final class LockData {
    public static final String LOCK_CHECKED_TAG = "lockscontinue_lock_checked";
    public static final String LOCK_PENDING_TAG = "lockscontinue_lock_pending";
    public static final String LOCK_INSTANCE_TAG = "lockscontinue_lock";
    public static final String LOCK_VARIANT_TAG = "lockscontinue_lock_variant";
    public static final String LOCK_UNLOCKED_TAG = "lockscontinue_lock_unlocked";
    public static final String REWARD_ATTEMPTS_TAG = "lockscontinue_reward_attempts";
    public static final String REWARD_CLAIMED_TAG = "lockscontinue_reward_claimed";

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
        return isLockableContainer(blockEntity) && getLockInstance(blockEntity) != null;
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

    /** Returns the full persistent lock instance, including legacy migration fallback. */
    public static LockInstance getLockInstance(BlockEntity blockEntity) {
        if (!isLockableContainer(blockEntity)) {
            return null;
        }

        CompoundTag persistentData = blockEntity.getPersistentData();
        if (persistentData.contains(LOCK_INSTANCE_TAG, Tag.TAG_COMPOUND)) {
            LockInstance instance = LockInstance.load(persistentData.getCompound(LOCK_INSTANCE_TAG));
            if (instance != null) {
                return instance;
            }
        }

        if (persistentData.contains(LOCK_VARIANT_TAG, Tag.TAG_INT)) {
            return LockInstance.fromLegacyVariant(
                    persistentData.getInt(LOCK_VARIANT_TAG), legacySeed(blockEntity));
        }
        return null;
    }

    /**
     * Materializes the old variant-only format as a new instance. This is
     * called while the block entity is loaded, so the migration is persisted
     * without changing the puzzle on subsequent GUI opens.
     */
    public static boolean migrateLegacy(BlockEntity blockEntity) {
        if (!isLockableContainer(blockEntity)) {
            return false;
        }

        CompoundTag persistentData = blockEntity.getPersistentData();
        if (persistentData.contains(LOCK_INSTANCE_TAG, Tag.TAG_COMPOUND)
                || !persistentData.contains(LOCK_VARIANT_TAG, Tag.TAG_INT)) {
            return false;
        }

        persistentData.put(LOCK_INSTANCE_TAG, LockInstance.fromLegacyVariant(
                persistentData.getInt(LOCK_VARIANT_TAG), legacySeed(blockEntity)).save());
        persistentData.putBoolean(LOCK_CHECKED_TAG, true);
        persistentData.remove(LOCK_VARIANT_TAG);
        markOwningChunkUnsaved(blockEntity);
        return true;
    }

    /** Applies a player-selected lock and marks the container as checked. */
    public static void applyLock(RandomizableContainerBlockEntity container, LockType type) {
        LockInstance instance = LockInstance.create(type, LockInstance.NORMAL_DIFFICULTY,
                legacySeed(container), null, List.of());
        applyLock(container, LockDefinitionManager.INSTANCE.withRewardSnapshot(instance));
    }

    public static void applyLock(RandomizableContainerBlockEntity container, LockInstance instance) {
        CompoundTag persistentData = container.getPersistentData();
        persistentData.putBoolean(LOCK_CHECKED_TAG, true);
        persistentData.remove(LOCK_PENDING_TAG);
        persistentData.remove(LOCK_UNLOCKED_TAG);
        persistentData.remove(LOCK_VARIANT_TAG);
        persistentData.remove(REWARD_ATTEMPTS_TAG);
        persistentData.remove(REWARD_CLAIMED_TAG);
        persistentData.put(LOCK_INSTANCE_TAG, instance.save());
    }

    /**
     * Upgrades a legacy/schema-1 lock in-place without clearing an already
     * recorded player attempt. The resolved reward is deliberately written to
     * every loaded half of a double chest.
     */
    public static LockInstance materializeRewardSnapshot(
            List<RandomizableContainerBlockEntity> containers,
            LockDefinitionManager definitions) {
        if (containers == null || containers.isEmpty()) {
            return null;
        }
        LockInstance current = getLockInstance(containers.get(0));
        if (current == null) {
            return null;
        }
        LockInstance resolved = definitions.withRewardSnapshot(current);
        if (!resolved.equals(current)) {
            for (RandomizableContainerBlockEntity container : containers) {
                container.getPersistentData().put(LOCK_INSTANCE_TAG, resolved.save());
                container.setChanged();
                markOwningChunkUnsaved(container);
            }
        }
        return resolved;
    }

    public static RewardAttemptState getRewardAttempt(
            List<RandomizableContainerBlockEntity> containers, UUID playerId) {
        if (containers == null || playerId == null) {
            return null;
        }
        String key = playerId.toString();
        for (RandomizableContainerBlockEntity container : containers) {
            CompoundTag attempts = container.getPersistentData().getCompound(REWARD_ATTEMPTS_TAG);
            if (attempts.contains(key, Tag.TAG_COMPOUND)) {
                RewardAttemptState state = RewardAttemptState.load(attempts.getCompound(key), playerId);
                if (state != null) {
                    return state;
                }
            }
        }
        return null;
    }

    public static void saveRewardAttempt(
            List<RandomizableContainerBlockEntity> containers, RewardAttemptState state) {
        if (containers == null || state == null) {
            return;
        }
        String key = state.playerId().toString();
        for (RandomizableContainerBlockEntity container : containers) {
            CompoundTag persistent = container.getPersistentData();
            CompoundTag attempts = persistent.getCompound(REWARD_ATTEMPTS_TAG);
            attempts.put(key, state.save());
            persistent.put(REWARD_ATTEMPTS_TAG, attempts);
            container.setChanged();
            markOwningChunkUnsaved(container);
        }
    }

    /** Atomically claims the shared vanilla-container reward on the server thread. */
    public static boolean claimReward(List<RandomizableContainerBlockEntity> containers) {
        return claimReward(containers, null);
    }

    /**
     * Lootr keeps inventory state per player. When its marker is present, use
     * a per-player claim key instead of blocking all later player claims.
     */
    public static boolean claimReward(List<RandomizableContainerBlockEntity> containers,
                                      UUID playerId) {
        if (containers == null || containers.isEmpty()) {
            return false;
        }
        boolean lootr = containers.stream().anyMatch(LockData::isLootrContainer);
        if (!lootr && containers.stream().anyMatch(container ->
                container.getPersistentData().getBoolean(REWARD_CLAIMED_TAG))) {
            return false;
        }
        String playerKey = playerId == null ? "" : playerId.toString();
        if (lootr && playerId != null && containers.stream().anyMatch(container -> {
            CompoundTag claims = container.getPersistentData().getCompound(REWARD_CLAIMED_TAG);
            return claims.getBoolean(playerKey);
        })) {
            return false;
        }
        for (RandomizableContainerBlockEntity container : containers) {
            if (lootr && playerId != null) {
                CompoundTag claims = container.getPersistentData().getCompound(REWARD_CLAIMED_TAG);
                claims.putBoolean(playerKey, true);
                container.getPersistentData().put(REWARD_CLAIMED_TAG, claims);
            } else {
                container.getPersistentData().putBoolean(REWARD_CLAIMED_TAG, true);
            }
            container.setChanged();
            markOwningChunkUnsaved(container);
        }
        return true;
    }

    public static boolean isRewardClaimed(List<RandomizableContainerBlockEntity> containers) {
        return containers != null && containers.stream().anyMatch(container ->
                container.getPersistentData().getBoolean(REWARD_CLAIMED_TAG));
    }

    public static boolean isLootrContainer(RandomizableContainerBlockEntity container) {
        try {
            return container.saveWithFullMetadata().contains("specialLootChest_table", Tag.TAG_STRING);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static int getVariant(BlockEntity blockEntity) {
        if (!isLockableContainer(blockEntity)) {
            return -1;
        }

        LockInstance instance = getLockInstance(blockEntity);
        if (instance == null) {
            return -1;
        }
        return LockType.fromResourceId(instance.lockType()).variant();
    }

    public static ItemStack getLockStack(BlockEntity blockEntity) {
        if (!isLockableContainer(blockEntity)) {
            return ItemStack.EMPTY;
        }

        LockInstance instance = getLockInstance(blockEntity);
        if (instance == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(lockItem(LockType.fromResourceId(instance.lockType()).variant()));
    }

    /** Removes the complete lock instance after a successful unlock. */
    public static void clearLock(RandomizableContainerBlockEntity container) {
        CompoundTag persistentData = container.getPersistentData();
        persistentData.remove(LOCK_INSTANCE_TAG);
        persistentData.remove(LOCK_VARIANT_TAG);
        persistentData.remove(LOCK_PENDING_TAG);
        persistentData.putBoolean(LOCK_UNLOCKED_TAG, true);
    }

    /** Gets the source loot table without linking against Lootr classes. */
    public static ResourceLocation getSourceLootTable(RandomizableContainerBlockEntity container) {
        CompoundTag tag = container.saveWithFullMetadata();
        for (String key : new String[]{"LootTable", "specialLootChest_table"}) {
            if (tag.contains(key, Tag.TAG_STRING)) {
                ResourceLocation id = ResourceLocation.tryParse(tag.getString(key));
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    private static long legacySeed(BlockEntity blockEntity) {
        long position = blockEntity.getBlockPos().asLong();
        int variant = blockEntity.getPersistentData().getInt(LOCK_VARIANT_TAG);
        return position * 0x9E3779B97F4A7C15L ^ ((long) variant * 0xD1B54A32D192ED03L);
    }

    private static void markOwningChunkUnsaved(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null) {
            chunk.setUnsaved(true);
        }
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

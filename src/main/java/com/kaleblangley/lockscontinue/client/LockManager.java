package com.kaleblangley.lockscontinue.client;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.lock.LockData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Client-side index of lock visuals.
 *
 * <p>The index stores only immutable render data. It deliberately does not
 * retain a BlockEntity reference, because a block entity becomes invalid as
 * soon as its chunk is unloaded or the chunk is replaced.</p>
 */
@Mod.EventBusSubscriber(modid = LocksContinue.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LockManager {
    private static final ConcurrentMap<BlockPos, LockRecord> LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Long, Set<BlockPos>> LOCK_POSITIONS_BY_CHUNK = new ConcurrentHashMap<>();
    private static final Set<Long> LOADED_CHUNKS = ConcurrentHashMap.newKeySet();
    private static volatile ClientLevel CURRENT_LEVEL;

    private LockManager() {
    }

    public record LockRecord(BlockPos pos, int variant, Direction facing, long chunkKey) {
    }

    /** Returns positions known to contain a lock visual in the current client level. */
    public static Collection<BlockPos> lockedPositions() {
        return LOCKS.keySet();
    }

    /**
     * Gets a lock only after the manager confirms that its owning chunk is
     * currently loaded in the same client level.
     */
    public static LockRecord getLoadedLock(ClientLevel level, BlockPos pos) {
        if (CURRENT_LEVEL != level) {
            return null;
        }

        long chunkKey = ChunkPos.asLong(pos);
        if (!LOADED_CHUNKS.contains(chunkKey)) {
            return null;
        }

        // A double chest keeps lock data on both halves so either half remains
        // interactive, but the client stores only the deterministic visual
        // owner. Reading the already-loaded block state avoids asking the
        // level to load the connected chunk just to resolve a click.
        BlockPos visualOwner = pos;
        BlockPos connectedPos = LockData.getConnectedPos(pos, level.getBlockState(pos));
        if (connectedPos != null && connectedPos.asLong() < visualOwner.asLong()) {
            visualOwner = connectedPos;
        }

        LockRecord record = LOCKS.get(visualOwner);
        return record != null && LOADED_CHUNKS.contains(record.chunkKey()) ? record : null;
    }

    /** Refreshes one client block entity after its synced data changes. */
    public static void refresh(BlockEntity blockEntity) {
        if (!LockData.isLockableContainer(blockEntity)) {
            remove(blockEntity);
            return;
        }

        if (blockEntity.isRemoved()) {
            remove(blockEntity.getBlockPos());
            return;
        }

        if (!(blockEntity.getLevel() instanceof ClientLevel level)) {
            return;
        }

        beginLevel(level);
        BlockPos pos = blockEntity.getBlockPos().immutable();
        if (!LockData.hasLockData(blockEntity) || LockData.isUnlocked(blockEntity)) {
            remove(pos);
            return;
        }

        BlockState state = blockEntity.getBlockState();
        Direction facing = LockData.getContainerFacing(state);
        if (facing == null) {
            remove(pos);
            return;
        }

        BlockPos connectedPos = LockData.getConnectedPos(pos, state);
        if (connectedPos != null && pos.asLong() > connectedPos.asLong()) {
            // Both halves retain their lock data, but only the deterministic
            // lower position owns the single rendered lock visual.
            remove(pos);
            return;
        }
        if (connectedPos != null) {
            // The other half may have been refreshed first. Remove its stale
            // visual now that this half has been selected as the owner.
            remove(connectedPos);
        }

        long chunkKey = ChunkPos.asLong(pos);
        // A load callback proves that this block entity belongs to an active
        // client chunk even if the block-entity NBT callback runs first.
        LOADED_CHUNKS.add(chunkKey);
        register(new LockRecord(pos, LockData.getVariant(blockEntity), facing, chunkKey));
    }

    public static void remove(BlockEntity blockEntity) {
        if (blockEntity != null) {
            remove(blockEntity.getBlockPos());
        }
    }

    public static void remove(BlockPos pos) {
        LockRecord removed = LOCKS.remove(pos);
        if (removed == null) {
            return;
        }

        Set<BlockPos> positions = LOCK_POSITIONS_BY_CHUNK.get(removed.chunkKey());
        if (positions != null && positions.remove(pos) && positions.isEmpty()) {
            LOCK_POSITIONS_BY_CHUNK.remove(removed.chunkKey(), positions);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        beginLevel(level);
        long chunkKey = chunk.getPos().toLong();
        LOADED_CHUNKS.add(chunkKey);
        clearChunkLocks(chunkKey);
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            refresh(blockEntity);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ClientLevel level)
                || CURRENT_LEVEL != level
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        long chunkKey = chunk.getPos().toLong();
        LOADED_CHUNKS.remove(chunkKey);
        clearChunkLocks(chunkKey);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level && CURRENT_LEVEL == level) {
            clearAll();
            CURRENT_LEVEL = null;
        }
    }

    private static void beginLevel(ClientLevel level) {
        if (CURRENT_LEVEL == level) {
            return;
        }

        clearAll();
        CURRENT_LEVEL = level;
    }

    private static void register(LockRecord record) {
        LockRecord previous = LOCKS.put(record.pos(), record);
        if (previous != null && previous.chunkKey() != record.chunkKey()) {
            Set<BlockPos> oldPositions = LOCK_POSITIONS_BY_CHUNK.get(previous.chunkKey());
            if (oldPositions != null) {
                oldPositions.remove(record.pos());
            }
        }

        LOCK_POSITIONS_BY_CHUNK
                .computeIfAbsent(record.chunkKey(), ignored -> ConcurrentHashMap.newKeySet())
                .add(record.pos());
    }

    private static void clearChunkLocks(long chunkKey) {
        Set<BlockPos> positions = LOCK_POSITIONS_BY_CHUNK.remove(chunkKey);
        if (positions == null) {
            return;
        }

        for (BlockPos pos : positions) {
            LockRecord record = LOCKS.get(pos);
            if (record != null && record.chunkKey() == chunkKey) {
                LOCKS.remove(pos, record);
            }
        }
    }

    private static void clearAll() {
        LOCKS.clear();
        LOCK_POSITIONS_BY_CHUNK.clear();
        LOADED_CHUNKS.clear();
    }
}

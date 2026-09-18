package com.kaleblangley.lockscontinue.interaction;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.item.ModItems;
import com.kaleblangley.lockscontinue.lock.LockData;
import com.kaleblangley.lockscontinue.lock.LockType;
import com.kaleblangley.lockscontinue.lock.puzzle.LockPuzzle;
import com.kaleblangley.lockscontinue.lock.puzzle.LockActionType;
import com.kaleblangley.lockscontinue.lock.puzzle.LockPuzzleFactory;
import com.kaleblangley.lockscontinue.lock.puzzle.PuzzleActionResult;
import com.kaleblangley.lockscontinue.network.LockNetwork;
import com.kaleblangley.lockscontinue.network.LockResultType;
import com.kaleblangley.lockscontinue.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns authoritative per-player lock puzzle sessions on the logical server. */
@Mod.EventBusSubscriber(modid = LocksContinue.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LockSessionManager {
    private static final double MAX_INTERACTION_DISTANCE_SQR = 8.0D * 8.0D;
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private LockSessionManager() {
    }

    /** Validates a client request before opening the lock-selection screen. */
    public static void requestLockSelection(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        if (!player.isShiftKeyDown()
                || !isWithinInteractionRange(player, pos)
                || !player.getItemInHand(hand).is(ModItems.LOCK.get())) {
            return;
        }

        List<RandomizableContainerBlockEntity> containers =
                getLoadedContainerGroup(player.serverLevel(), pos);
        if (containers.isEmpty()) {
            return;
        }

        if (containers.stream().anyMatch(LockData::hasLock)) {
            player.displayClientMessage(Component.translatable(
                    "message.lockscontinue.already_locked"), true);
            return;
        }

        LockNetwork.sendTo(player, new LockNetwork.OpenLockSelectionPacket(pos.immutable(), hand));
    }

    /** Applies the selected lock only after revalidating the target on the server. */
    public static void applyLockSelection(ServerPlayer player, BlockPos pos,
                                           InteractionHand hand, int variant) {
        if (variant < 0 || variant >= 4
                || !isWithinInteractionRange(player, pos)
                || !player.getItemInHand(hand).is(ModItems.LOCK.get())) {
            sendSelectionResult(player, pos, false);
            return;
        }

        List<RandomizableContainerBlockEntity> containers =
                getLoadedContainerGroup(player.serverLevel(), pos);
        if (containers.isEmpty() || containers.stream().anyMatch(LockData::hasLock)) {
            if (!containers.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "message.lockscontinue.already_locked"), true);
            }
            sendSelectionResult(player, pos, false);
            return;
        }

        ServerLevel level = player.serverLevel();
        LockType type = LockType.fromVariant(variant);
        for (RandomizableContainerBlockEntity container : containers) {
            BlockPos containerPos = container.getBlockPos();
            BlockState state = level.getBlockState(containerPos);
            LockData.applyLock(container, type);
            container.setChanged();
            level.sendBlockUpdated(containerPos, state, state, 3);
        }
        if (!player.getAbilities().instabuild) {
            player.getItemInHand(hand).shrink(1);
        }

        sendSelectionResult(player, pos, true);
    }

    private static void sendSelectionResult(ServerPlayer player, BlockPos pos, boolean success) {
        LockNetwork.sendTo(player, new LockNetwork.LockSelectionResultPacket(pos.immutable(), success));
    }

    private static boolean isWithinInteractionRange(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_INTERACTION_DISTANCE_SQR;
    }

    private static RandomizableContainerBlockEntity getContainerIfLoaded(
            ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return null;
        }

        BlockEntity blockEntity = chunk.getBlockEntity(pos);
        return blockEntity instanceof RandomizableContainerBlockEntity container
                && LockData.isLockableContainer(container) ? container : null;
    }

    private static List<RandomizableContainerBlockEntity> getLoadedContainerGroup(
            ServerLevel level, BlockPos pos) {
        RandomizableContainerBlockEntity container = getContainerIfLoaded(level, pos);
        if (container == null) {
            return List.of();
        }

        List<RandomizableContainerBlockEntity> containers = new ArrayList<>(2);
        containers.add(container);
        BlockPos connectedPos = LockData.getConnectedPos(pos, container.getBlockState());
        if (connectedPos != null) {
            RandomizableContainerBlockEntity connectedContainer =
                    getContainerIfLoaded(level, connectedPos);
            if (connectedContainer == null) {
                // Never create a one-sided lock when the other half is not
                // currently available. Vanilla can only open this as a
                // double chest once both halves are loaded anyway.
                return List.of();
            }
            containers.add(connectedContainer);
        }
        return containers;
    }

    /** Finds a locked container without loading another chunk. */
    public static RandomizableContainerBlockEntity findLockedContainer(
            ServerLevel level, BlockPos pos) {
        for (RandomizableContainerBlockEntity container : getLoadedContainerGroup(level, pos)) {
            if (LockData.hasLock(container)) {
                return container;
            }
        }
        return null;
    }

    public static void begin(ServerPlayer player, BlockPos pos, LockType type, InteractionHand hand) {
        ServerLevel level = player.serverLevel();
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return;
        }

        BlockEntity blockEntity = chunk.getBlockEntity(pos);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)
                || !LockData.hasLock(container)) {
            return;
        }

        ItemStack tool = player.getItemInHand(hand);
        if (!type.canOpen(tool)) {
            return;
        }

        long sessionId = player.getRandom().nextLong();
        long puzzleSeed = player.getRandom().nextLong();
        int attemptsLeft = remainingUses(tool);
        Session session = new Session(sessionId, level.dimension(), pos.immutable(), type, hand,
                tool.getItem(), puzzleSeed, attemptsLeft,
                LockPuzzleFactory.create(type, puzzleSeed));
        Session previous = SESSIONS.put(player.getUUID(), session);
        if (previous != null) {
            sendResult(player, previous.id, LockResultType.EXPIRED, 0);
        }
        LockNetwork.sendTo(player, new LockNetwork.OpenLockPacket(
                sessionId, session.pos, type, puzzleSeed, attemptsLeft, tool.copy()));
    }

    public static void handleAction(ServerPlayer player, LockNetwork.LockActionPacket packet) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.id != packet.sessionId()) {
            sendResult(player, packet.sessionId(), LockResultType.EXPIRED, 0);
            return;
        }

        if (!isSessionValid(player, session)) {
            expire(player, session);
            return;
        }

        PuzzleActionResult result = session.puzzle.apply(
                packet.toAction(), player.serverLevel().getGameTime());
        if (result == PuzzleActionResult.SOLVED) {
            unlockAndOpen(player, session);
        } else if (result == PuzzleActionResult.WRONG) {
            consumeAttempt(player, session);
        } else if (packet.action() == LockActionType.PIN_SET) {
            sendResult(player, session.id, LockResultType.PROGRESS, session.attemptsLeft);
        }
    }

    public static void cancel(ServerPlayer player, long sessionId) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.id == sessionId) {
            SESSIONS.remove(player.getUUID(), session);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    private static boolean isSessionValid(ServerPlayer player, Session session) {
        if (!player.serverLevel().dimension().equals(session.dimension)
                || player.distanceToSqr(Vec3.atCenterOf(session.pos)) > MAX_INTERACTION_DISTANCE_SQR) {
            return false;
        }

        ItemStack tool = player.getItemInHand(session.hand);
        if (tool.isEmpty() || tool.getItem() != session.toolItem || !session.type.canOpen(tool)) {
            return false;
        }

        LevelChunk chunk = player.serverLevel().getChunkSource()
                .getChunkNow(session.pos.getX() >> 4, session.pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }

        BlockEntity blockEntity = chunk.getBlockEntity(session.pos);
        return blockEntity instanceof RandomizableContainerBlockEntity container
                && LockData.hasLock(container)
                && LockType.fromVariant(LockData.getVariant(container)) == session.type;
    }

    private static void unlockAndOpen(ServerPlayer player, Session session) {
        ServerLevel level = player.serverLevel();
        LevelChunk chunk = level.getChunkSource().getChunkNow(session.pos.getX() >> 4, session.pos.getZ() >> 4);
        if (chunk == null) {
            expire(player, session);
            return;
        }

        List<RandomizableContainerBlockEntity> containers =
                getLoadedContainerGroup(level, session.pos);
        if (containers.isEmpty() || containers.stream().noneMatch(LockData::hasLock)) {
            expire(player, session);
            return;
        }

        for (RandomizableContainerBlockEntity container : containers) {
            BlockPos containerPos = container.getBlockPos();
            BlockState containerState = level.getBlockState(containerPos);
            container.getPersistentData().remove(LockData.LOCK_VARIANT_TAG);
            container.getPersistentData().remove(LockData.LOCK_PENDING_TAG);
            container.getPersistentData().putBoolean(LockData.LOCK_UNLOCKED_TAG, true);
            container.setChanged();
            level.sendBlockUpdated(containerPos, containerState, containerState, 3);
        }

        session.completed = true;
        SESSIONS.remove(player.getUUID(), session);
        level.playSound(null, session.pos, ModSounds.LOCK_OPEN.get(),
                SoundSource.BLOCKS, 0.8F, 1.15F);
        sendResult(player, session.id, LockResultType.SUCCESS, session.attemptsLeft);

        BlockState state = level.getBlockState(session.pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(session.pos), Direction.UP, session.pos, false);
        InteractionResult interactionResult = state.use(level, player, session.hand, hit);
        if (interactionResult == InteractionResult.PASS) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.lockscontinue.unlocked"), true);
        }
    }

    private static void consumeAttempt(ServerPlayer player, Session session) {
        ItemStack tool = player.getItemInHand(session.hand);
        if (tool.isDamageableItem()) {
            tool.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(session.hand));
            session.attemptsLeft = Math.max(0, tool.getMaxDamage() - tool.getDamageValue());
        } else {
            session.attemptsLeft--;
        }

        if (session.attemptsLeft <= 0 || tool.isEmpty()) {
            SESSIONS.remove(player.getUUID(), session);
            sendResult(player, session.id, LockResultType.FAILURE, 0);
            return;
        }

        sendResult(player, session.id, LockResultType.FAILURE, session.attemptsLeft);
    }

    private static void expire(ServerPlayer player, Session session) {
        if (SESSIONS.remove(player.getUUID(), session)) {
            sendResult(player, session.id, LockResultType.EXPIRED, 0);
        }
    }

    private static void sendResult(ServerPlayer player, long sessionId, LockResultType result, int attemptsLeft) {
        LockNetwork.sendTo(player, new LockNetwork.LockResultPacket(sessionId, result, attemptsLeft));
    }

    private static int remainingUses(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return 3;
        }
        return Math.max(1, stack.getMaxDamage() - stack.getDamageValue());
    }

    private static final class Session {
        private final long id;
        private final ResourceKey<Level> dimension;
        private final BlockPos pos;
        private final LockType type;
        private final InteractionHand hand;
        private final net.minecraft.world.item.Item toolItem;
        private final long puzzleSeed;
        private final LockPuzzle puzzle;
        private int attemptsLeft;
        private boolean completed;

        private Session(long id, ResourceKey<Level> dimension, BlockPos pos, LockType type,
                        InteractionHand hand, net.minecraft.world.item.Item toolItem, long puzzleSeed,
                        int attemptsLeft, LockPuzzle puzzle) {
            this.id = id;
            this.dimension = dimension;
            this.pos = pos;
            this.type = type;
            this.hand = hand;
            this.toolItem = toolItem;
            this.puzzleSeed = puzzleSeed;
            this.attemptsLeft = attemptsLeft;
            this.puzzle = puzzle;
        }
    }
}

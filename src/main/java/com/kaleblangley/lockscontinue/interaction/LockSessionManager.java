package com.kaleblangley.lockscontinue.interaction;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.item.ModItems;
import com.kaleblangley.lockscontinue.lock.LockData;
import com.kaleblangley.lockscontinue.lock.LockInstance;
import com.kaleblangley.lockscontinue.lock.LockType;
import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;
import com.kaleblangley.lockscontinue.lock.definition.LockDefinitionManager;
import com.kaleblangley.lockscontinue.lock.definition.PipeTimeLimit;
import com.kaleblangley.lockscontinue.lock.definition.ResolvedRewardSnapshot;
import com.kaleblangley.lockscontinue.lock.definition.RewardAttemptState;
import com.kaleblangley.lockscontinue.lock.definition.RewardMetric;
import com.kaleblangley.lockscontinue.lock.puzzle.LockPuzzle;
import com.kaleblangley.lockscontinue.lock.puzzle.LockActionType;
import com.kaleblangley.lockscontinue.lock.puzzle.LockPuzzleFactory;
import com.kaleblangley.lockscontinue.lock.puzzle.PuzzleActionResult;
import com.kaleblangley.lockscontinue.network.LockNetwork;
import com.kaleblangley.lockscontinue.network.LockResultType;
import com.kaleblangley.lockscontinue.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraftforge.event.TickEvent;
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
    /** Server-side idle lifetime; this is not a client GUI timeout. */
    private static final long SESSION_IDLE_TIMEOUT_TICKS = 20L * 60L * 10L;
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

        LockDefinitionManager definitions = LockDefinitionManager.INSTANCE;
        LockNetwork.sendTo(player, new LockNetwork.OpenLockSelectionPacket(
                pos.immutable(), hand,
                definitions.selectableDifficultyIds(),
                selectableAffixOptions(definitions)));
    }

    /** Applies the selected lock only after revalidating the target on the server. */
    public static void applyLockSelection(ServerPlayer player, BlockPos pos,
                                           InteractionHand hand, int variant,
                                           net.minecraft.resources.ResourceLocation difficulty,
                                           List<net.minecraft.resources.ResourceLocation> affixes) {
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
        LockDefinitionManager definitions = LockDefinitionManager.INSTANCE;
        net.minecraft.resources.ResourceLocation normalizedDifficulty =
                definitions.normalizeDifficulty(difficulty);
        List<net.minecraft.resources.ResourceLocation> normalizedAffixes =
                definitions.normalizeManualAffixes(type, normalizedDifficulty, affixes);
        LockInstance instance = LockInstance.create(type, normalizedDifficulty,
                player.getRandom().nextLong(), null, normalizedAffixes,
                LockDefinitionManager.DEFAULT_REWARD_PROFILE, null);
        instance = definitions.withRewardSnapshot(instance);
        for (RandomizableContainerBlockEntity container : containers) {
            BlockPos containerPos = container.getBlockPos();
            BlockState state = level.getBlockState(containerPos);
            LockData.applyLock(container, instance);
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

    private static List<LockNetwork.AffixOption> selectableAffixOptions(
            LockDefinitionManager definitions) {
        List<net.minecraft.resources.ResourceLocation> difficulties =
                definitions.selectableDifficultyIds();
        List<LockNetwork.AffixOption> result = new ArrayList<>();
        for (var affix : definitions.selectableAffixes()) {
            List<net.minecraft.resources.ResourceLocation> applicableDifficulties = difficulties.stream()
                    .filter(id -> affix.appliesTo(definitions.difficulty(id).rank()))
                    .toList();
            if (!applicableDifficulties.isEmpty()) {
                result.add(new LockNetwork.AffixOption(
                        affix.id(), applicableDifficulties, affix.lockTypes()));
            }
        }
        return List.copyOf(result);
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

        List<RandomizableContainerBlockEntity> containers = getLoadedContainerGroup(level, pos);
        if (containers.isEmpty()) {
            return;
        }
        LockInstance instance = LockData.materializeRewardSnapshot(
                containers, LockDefinitionManager.INSTANCE);
        if (instance == null) {
            return;
        }

        LockType actualType = LockType.fromResourceId(instance.lockType());
        ItemStack tool = player.getItemInHand(hand);
        if (!actualType.canOpen(tool)) {
            return;
        }

        long sessionId = player.getRandom().nextLong();
        // The lock instance owns the puzzle seed. Reopening the screen must
        // never roll a different challenge for the same world lock.
        long puzzleSeed = instance.seed();
        int attemptsLeft = remainingUses(tool);
        LockChallengeSnapshot challenge = LockChallengeSnapshot.from(
                instance, LockDefinitionManager.INSTANCE, tool);
        ResolvedRewardSnapshot reward = instance.rewardSnapshot();
        if (reward == null) {
            return;
        }
        RewardAttemptState rewardAttempt = LockData.getRewardAttempt(containers, player.getUUID());
        if (rewardAttempt == null || rewardAttempt.failed()) {
            rewardAttempt = RewardAttemptState.fresh(player.getUUID());
            LockData.saveRewardAttempt(containers, rewardAttempt);
        }
        long now = level.getGameTime();
        int mistakesLeft = remainingMistakes(reward, rewardAttempt.mistakesUsed(),
                challenge.mistakesFor(actualType));
        Session session = new Session(sessionId, level.dimension(), pos.immutable(), actualType,
                instance, hand, tool.getItem(), toolTagSnapshot(tool), puzzleSeed, attemptsLeft,
                mistakesLeft, challenge, reward, rewardAttempt,
                LockPuzzleFactory.create(actualType, puzzleSeed, challenge, now),
                now + SESSION_IDLE_TIMEOUT_TICKS);
        Session previous = SESSIONS.put(player.getUUID(), session);
        if (previous != null) {
            sendResult(player, previous.id, LockResultType.REJECTED, 0, 0, 0);
        }
        LockNetwork.sendTo(player, new LockNetwork.OpenLockPacket(
                sessionId, session.pos, actualType, puzzleSeed, attemptsLeft,
                session.mistakesLeft, tool.copy(), challenge,
                rewardStatus(player, session), remainingPipeTime(player, session)));
    }

    public static void handleAction(ServerPlayer player, LockNetwork.LockActionPacket packet) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.id != packet.sessionId()) {
            sendResult(player, packet.sessionId(), LockResultType.REJECTED, 0, 0, 0,
                    new LockNetwork.RewardStatus(RewardMetric.MISTAKES_USED, 0, -1, false));
            return;
        }

        if (packet.actionSequence() <= session.lastActionSequence) {
            sendResult(player, session, LockResultType.REJECTED);
            return;
        }

        if (!isSessionValid(player, session)) {
            expire(player, session);
            return;
        }

        long now = player.serverLevel().getGameTime();
        if (timeLimitReached(session, now)) {
            failForTime(player, session);
            return;
        }

        session.lastActionSequence = packet.actionSequence();

        PuzzleActionResult result = session.puzzle.apply(
                packet.toAction(), now);
        if (result != PuzzleActionResult.REJECTED) {
            session.expiresAt = now + SESSION_IDLE_TIMEOUT_TICKS;
            if (session.type == LockType.PIPE_LOCK
                    && !session.rewardAttempt.metricStarted()
                    && packet.action() == LockActionType.PIPE_ROTATE) {
                session.rewardAttempt = session.rewardAttempt.start(now);
                persistAttempt(player, session);
            }
        }
        switch (result) {
            case SOLVED -> unlockAndOpen(player, session);
            case MISTAKE -> consumeAttempt(player, session);
            case PROGRESS -> sendResult(player, session, LockResultType.PROGRESS);
            case UPDATED -> sendResult(player, session, LockResultType.UPDATED);
            case REJECTED -> sendResult(player, session, LockResultType.REJECTED);
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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                SESSIONS.remove(entry.getKey(), entry.getValue());
                continue;
            }
            Session session = entry.getValue();
            long now = player.serverLevel().getGameTime();
            if (!isSessionValid(player, session)) {
                expire(player, session);
            } else if (timeLimitReached(session, now)) {
                failForTime(player, session);
            }
        }
    }

    private static boolean isSessionValid(ServerPlayer player, Session session) {
        long now = player.serverLevel().getGameTime();
        if (now >= session.expiresAt
                || !player.serverLevel().dimension().equals(session.dimension)
                || player.distanceToSqr(Vec3.atCenterOf(session.pos)) > MAX_INTERACTION_DISTANCE_SQR) {
            return false;
        }

        ItemStack tool = player.getItemInHand(session.hand);
        if (tool.isEmpty() || tool.getItem() != session.toolItem
                || !session.type.canOpen(tool)
                || !toolTagSnapshot(tool).equals(session.toolTag)) {
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
                && session.instance.equals(LockData.getLockInstance(container))
                && LockType.fromResourceId(session.instance.lockType()) == session.type;
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

        int metricValue = metricValue(player, session);
        boolean lootrContainer = containers.stream().anyMatch(LockData::isLootrContainer);
        // Vanilla containers can be filled before opening. Lootr creates a
        // player-specific ChestMenu during use(), so its reward is filled
        // immediately after that menu exists.
        if (!lootrContainer) {
            LockRewardDistributor.distribute(level, player, containers, session.instance,
                    session.reward, metricValue);
        }

        for (RandomizableContainerBlockEntity container : containers) {
            BlockPos containerPos = container.getBlockPos();
            BlockState containerState = level.getBlockState(containerPos);
            LockData.clearLock(container);
            container.setChanged();
            level.sendBlockUpdated(containerPos, containerState, containerState, 3);
        }

        session.completed = true;
        SESSIONS.remove(player.getUUID(), session);
        level.playSound(null, session.pos, ModSounds.LOCK_OPEN.get(),
                SoundSource.BLOCKS, 0.8F, 1.15F);
        sendResult(player, session, LockResultType.SOLVED);

        BlockState state = level.getBlockState(session.pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(session.pos), Direction.UP, session.pos, false);
        InteractionResult interactionResult = state.use(level, player, session.hand, hit);
        if (lootrContainer) {
            LockRewardDistributor.distribute(level, player, containers, session.instance,
                    session.reward, metricValue);
        }
        if (interactionResult == InteractionResult.PASS) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.lockscontinue.unlocked"), true);
        }
    }

    private static void consumeAttempt(ServerPlayer player, Session session) {
        ItemStack tool = player.getItemInHand(session.hand);
        session.rewardAttempt = session.rewardAttempt.addMistake();
        persistAttempt(player, session);
        // The difficulty, tool multiplier and strength relief were already
        // resolved once into the session snapshot. Do not apply stability or
        // another reduction here, otherwise several tools collapse to 1 point.
        int durabilityCost = Math.max(0, session.challenge.durabilityCost());
        session.durabilitySpent += durabilityCost;
        if (tool.isDamageableItem()) {
            tool.hurtAndBreak(durabilityCost, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(session.hand));
            session.attemptsLeft = Math.max(0, tool.getMaxDamage() - tool.getDamageValue());
        } else {
            session.attemptsLeft -= durabilityCost;
        }
        session.mistakesLeft = remainingMistakes(session.reward,
                session.rewardAttempt.mistakesUsed(), session.mistakesLeft);

        boolean rewardFailure = session.reward.failureAt() >= 0
                && session.rewardAttempt.mistakesUsed() >= session.reward.failureAt();
        if (session.attemptsLeft <= 0 || rewardFailure || tool.isEmpty()) {
            session.rewardAttempt = session.rewardAttempt.fail();
            persistAttempt(player, session);
            SESSIONS.remove(player.getUUID(), session);
            sendResult(player, session.id, LockResultType.MISTAKE,
                    0, session.mistakesLeft, remainingPipeTime(player, session),
                    rewardStatus(player, session));
            return;
        }

        sendResult(player, session, LockResultType.MISTAKE);
    }

    private static void failForTime(ServerPlayer player, Session session) {
        if (!SESSIONS.remove(player.getUUID(), session)) {
            return;
        }
        session.rewardAttempt = session.rewardAttempt.fail();
        persistAttempt(player, session);
        ItemStack tool = player.getItemInHand(session.hand);
        applyDurabilityCost(player, session, tool);
        sendResult(player, session.id, LockResultType.MISTAKE, 0, 0, 0,
                rewardStatus(player, session));
    }

    private static void applyDurabilityCost(ServerPlayer player, Session session, ItemStack tool) {
        int durabilityCost = Math.max(0, session.challenge.durabilityCost());
        session.durabilitySpent += durabilityCost;
        if (tool.isDamageableItem() && durabilityCost > 0) {
            tool.hurtAndBreak(durabilityCost, player,
                    brokenPlayer -> brokenPlayer.broadcastBreakEvent(session.hand));
            session.attemptsLeft = Math.max(0, tool.getMaxDamage() - tool.getDamageValue());
        } else if (!tool.isDamageableItem()) {
            session.attemptsLeft -= durabilityCost;
        }
    }

    private static void expire(ServerPlayer player, Session session) {
        if (SESSIONS.remove(player.getUUID(), session)) {
            sendResult(player, session.id, LockResultType.REJECTED, 0, 0,
                    remainingPipeTime(player, session), rewardStatus(player, session));
        }
    }

    private static void sendResult(ServerPlayer player, Session session,
                                   LockResultType result) {
        sendResult(player, session.id, result, session.attemptsLeft,
                session.mistakesLeft, remainingPipeTime(player, session),
                rewardStatus(player, session));
    }

    private static void sendResult(ServerPlayer player, long sessionId, LockResultType result,
                                   int attemptsLeft, int mistakesLeft,
                                   int pipeTimeLeftTicks,
                                   LockNetwork.RewardStatus rewardStatus) {
        LockNetwork.sendTo(player, new LockNetwork.LockResultPacket(
                sessionId, result, attemptsLeft, mistakesLeft, pipeTimeLeftTicks, rewardStatus));
    }

    private static void sendResult(ServerPlayer player, long sessionId, LockResultType result,
                                   int attemptsLeft, int mistakesLeft, int pipeTimeLeftTicks) {
        sendResult(player, sessionId, result, attemptsLeft, mistakesLeft, pipeTimeLeftTicks,
                new LockNetwork.RewardStatus(RewardMetric.MISTAKES_USED, 0, -1, false));
    }

    private static int remainingPipeTime(ServerPlayer player, Session session) {
        if (session.type != LockType.PIPE_LOCK) {
            return 0;
        }
        return PipeTimeLimit.remainingTicks(session.pipeTimeLimitTicks,
                session.rewardAttempt.elapsedTicks(player.serverLevel().getGameTime()));
    }

    private static int metricValue(ServerPlayer player, Session session) {
        if (session.reward.metric() == RewardMetric.TIME_TICKS) {
            if (!session.rewardAttempt.metricStarted()) {
                return 0;
            }
            long elapsed = session.rewardAttempt.elapsedTicks(player.serverLevel().getGameTime());
            return (int) Math.min(Integer.MAX_VALUE, elapsed);
        }
        return Math.max(0, session.rewardAttempt.mistakesUsed());
    }

    private static LockNetwork.RewardStatus rewardStatus(ServerPlayer player, Session session) {
        return new LockNetwork.RewardStatus(session.reward.metric(), metricValue(player, session),
                session.reward.failureAt(), session.rewardAttempt.metricStarted());
    }

    private static int remainingMistakes(ResolvedRewardSnapshot reward, int mistakesUsed,
                                         int fallback) {
        if (reward.failureAt() < 0) {
            return Math.max(1, fallback);
        }
        return Math.max(0, reward.failureAt() - Math.max(0, mistakesUsed));
    }

    private static boolean timeLimitReached(Session session, long now) {
        if (session.type == LockType.PIPE_LOCK) {
            return PipeTimeLimit.reached(session.pipeTimeLimitTicks, session.rewardAttempt, now);
        }
        if (session.reward.metric() != RewardMetric.TIME_TICKS
                || !session.rewardAttempt.metricStarted()
                || session.reward.failureAt() < 0) {
            return false;
        }
        return session.rewardAttempt.elapsedTicks(now) >= session.reward.failureAt();
    }

    private static void persistAttempt(ServerPlayer player, Session session) {
        List<RandomizableContainerBlockEntity> containers =
                getLoadedContainerGroup(player.serverLevel(), session.pos);
        if (!containers.isEmpty()) {
            LockData.saveRewardAttempt(containers, session.rewardAttempt);
        }
    }

    private static int remainingUses(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return 3;
        }
        return Math.max(1, stack.getMaxDamage() - stack.getDamageValue());
    }

    private static CompoundTag toolTagSnapshot(ItemStack stack) {
        CompoundTag tag = stack.getTag() == null ? new CompoundTag() : stack.getTag().copy();
        // Durability is expected to change during a session; enchantments and
        // every other tool modifier remain part of the immutable snapshot.
        tag.remove("Damage");
        return tag;
    }

    private static final class Session {
        private final long id;
        private final ResourceKey<Level> dimension;
        private final BlockPos pos;
        private final LockType type;
        private final LockInstance instance;
        private final InteractionHand hand;
        private final net.minecraft.world.item.Item toolItem;
        private final CompoundTag toolTag;
        private final long puzzleSeed;
        private final LockPuzzle puzzle;
        private int attemptsLeft;
        private int mistakesLeft;
        private long lastActionSequence;
        private final LockChallengeSnapshot challenge;
        private final int pipeTimeLimitTicks;
        private long durabilitySpent;
        private long expiresAt;
        private final ResolvedRewardSnapshot reward;
        private RewardAttemptState rewardAttempt;
        private boolean completed;

        private Session(long id, ResourceKey<Level> dimension, BlockPos pos, LockType type,
                        LockInstance instance, InteractionHand hand,
                        net.minecraft.world.item.Item toolItem, CompoundTag toolTag,
                        long puzzleSeed,
                        int attemptsLeft, int mistakesLeft, LockChallengeSnapshot challenge,
                        ResolvedRewardSnapshot reward, RewardAttemptState rewardAttempt,
                        LockPuzzle puzzle, long expiresAt) {
            this.id = id;
            this.dimension = dimension;
            this.pos = pos;
            this.type = type;
            this.instance = instance;
            this.hand = hand;
            this.toolItem = toolItem;
            this.toolTag = toolTag;
            this.puzzleSeed = puzzleSeed;
            this.attemptsLeft = attemptsLeft;
            this.mistakesLeft = mistakesLeft;
            this.challenge = challenge;
            this.reward = reward;
            this.pipeTimeLimitTicks = type == LockType.PIPE_LOCK
                    ? PipeTimeLimit.resolve(challenge.pipeTimeLimitTicks(), reward.metric(), reward.failureAt())
                    : -1;
            this.rewardAttempt = rewardAttempt;
            this.puzzle = puzzle;
            this.expiresAt = expiresAt;
        }
    }
}

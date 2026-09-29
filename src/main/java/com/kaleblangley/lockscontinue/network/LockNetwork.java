package com.kaleblangley.lockscontinue.network;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.interaction.LockSessionManager;
import com.kaleblangley.lockscontinue.lock.LockType;
import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;
import com.kaleblangley.lockscontinue.lock.definition.RewardMetric;
import com.kaleblangley.lockscontinue.lock.puzzle.LockAction;
import com.kaleblangley.lockscontinue.lock.puzzle.LockActionType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Network boundary between the client puzzle UI and the server lock session. */
public final class LockNetwork {
    private static final String PROTOCOL_VERSION = "11";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LocksContinue.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static int packetId;

    private LockNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(packetId++, OpenLockPacket.class,
                OpenLockPacket::encode, OpenLockPacket::decode, OpenLockPacket::handle);
        CHANNEL.registerMessage(packetId++, LockActionPacket.class,
                LockActionPacket::encode, LockActionPacket::decode, LockActionPacket::handle);
        CHANNEL.registerMessage(packetId++, CancelLockPacket.class,
                CancelLockPacket::encode, CancelLockPacket::decode, CancelLockPacket::handle);
        CHANNEL.registerMessage(packetId++, LockResultPacket.class,
                LockResultPacket::encode, LockResultPacket::decode, LockResultPacket::handle);
        CHANNEL.registerMessage(packetId++, RequestLockSelectionPacket.class,
                RequestLockSelectionPacket::encode, RequestLockSelectionPacket::decode,
                RequestLockSelectionPacket::handle);
        CHANNEL.registerMessage(packetId++, OpenLockSelectionPacket.class,
                OpenLockSelectionPacket::encode, OpenLockSelectionPacket::decode,
                OpenLockSelectionPacket::handle);
        CHANNEL.registerMessage(packetId++, SelectLockPacket.class,
                SelectLockPacket::encode, SelectLockPacket::decode, SelectLockPacket::handle);
        CHANNEL.registerMessage(packetId++, LockSelectionResultPacket.class,
                LockSelectionResultPacket::encode, LockSelectionResultPacket::decode,
                LockSelectionResultPacket::handle);
    }

    public static void sendTo(net.minecraft.server.level.ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public record OpenLockPacket(long sessionId, BlockPos pos, LockType type, long puzzleSeed,
                                 int attemptsLeft, int mistakesLeft, ItemStack tool,
                                 LockChallengeSnapshot challenge,
                                 RewardStatus rewardStatus) {
        public OpenLockPacket(long sessionId, BlockPos pos, LockType type, long puzzleSeed,
                              int attemptsLeft, int mistakesLeft, ItemStack tool,
                              LockChallengeSnapshot challenge) {
            this(sessionId, pos, type, puzzleSeed, attemptsLeft, mistakesLeft, tool,
                    challenge, new RewardStatus(RewardMetric.MISTAKES_USED, 0, -1, false));
        }

        public static void encode(OpenLockPacket packet, FriendlyByteBuf buffer) {
            buffer.writeLong(packet.sessionId);
            buffer.writeBlockPos(packet.pos);
            buffer.writeEnum(packet.type);
            buffer.writeLong(packet.puzzleSeed);
            buffer.writeVarInt(packet.attemptsLeft);
            buffer.writeVarInt(packet.mistakesLeft);
            buffer.writeItem(packet.tool);
            packet.challenge.encode(buffer);
            packet.rewardStatus.encode(buffer);
        }

        public static OpenLockPacket decode(FriendlyByteBuf buffer) {
            return new OpenLockPacket(buffer.readLong(), buffer.readBlockPos(),
                    buffer.readEnum(LockType.class), buffer.readLong(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readItem(), LockChallengeSnapshot.decode(buffer),
                    RewardStatus.decode(buffer));
        }

        public static void handle(OpenLockPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.kaleblangley.lockscontinue.client.LockClientPacketHandler.open(packet)));
            context.setPacketHandled(true);
        }
    }

    public record LockActionPacket(long sessionId, long actionSequence, LockActionType action,
                                   int first, int second,
                                   float value, int[] sequence) {
        public static void encode(LockActionPacket packet, FriendlyByteBuf buffer) {
            buffer.writeLong(packet.sessionId);
            buffer.writeLong(packet.actionSequence);
            buffer.writeEnum(packet.action);
            buffer.writeVarInt(packet.first);
            buffer.writeVarInt(packet.second);
            buffer.writeFloat(packet.value);
            buffer.writeVarInt(packet.sequence.length);
            for (int value : packet.sequence) {
                buffer.writeVarInt(value);
            }
        }

        public static LockActionPacket decode(FriendlyByteBuf buffer) {
            long sessionId = buffer.readLong();
            long actionSequence = buffer.readLong();
            LockActionType action = buffer.readEnum(LockActionType.class);
            int first = buffer.readVarInt();
            int second = buffer.readVarInt();
            float value = buffer.readFloat();
            int length = buffer.readVarInt();
            if (length < 0 || length > 128) {
                throw new IllegalArgumentException("Invalid lock action sequence length: " + length);
            }
            int[] sequence = new int[length];
            for (int i = 0; i < length; i++) {
                sequence[i] = buffer.readVarInt();
            }
            return new LockActionPacket(sessionId, actionSequence, action,
                    first, second, value, sequence);
        }

        public LockAction toAction() {
            return new LockAction(action, first, second, value, sequence.clone());
        }

        public static void handle(LockActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                if (context.getSender() != null) {
                    LockSessionManager.handleAction(context.getSender(), packet);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record CancelLockPacket(long sessionId) {
        public static void encode(CancelLockPacket packet, FriendlyByteBuf buffer) {
            buffer.writeLong(packet.sessionId);
        }

        public static CancelLockPacket decode(FriendlyByteBuf buffer) {
            return new CancelLockPacket(buffer.readLong());
        }

        public static void handle(CancelLockPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                if (context.getSender() != null) {
                    LockSessionManager.cancel(context.getSender(), packet.sessionId);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record LockResultPacket(long sessionId, LockResultType result,
                                   int attemptsLeft, int mistakesLeft,
                                   int pipeTimeLeftTicks, RewardStatus rewardStatus) {
        public LockResultPacket(long sessionId, LockResultType result,
                                int attemptsLeft, int mistakesLeft, int pipeTimeLeftTicks) {
            this(sessionId, result, attemptsLeft, mistakesLeft, pipeTimeLeftTicks,
                    new RewardStatus(RewardMetric.MISTAKES_USED, 0, -1, false));
        }

        public static void encode(LockResultPacket packet, FriendlyByteBuf buffer) {
            buffer.writeLong(packet.sessionId);
            buffer.writeEnum(packet.result);
            buffer.writeVarInt(packet.attemptsLeft);
            buffer.writeVarInt(packet.mistakesLeft);
            buffer.writeVarInt(packet.pipeTimeLeftTicks);
            packet.rewardStatus.encode(buffer);
        }

        public static LockResultPacket decode(FriendlyByteBuf buffer) {
            return new LockResultPacket(buffer.readLong(), buffer.readEnum(LockResultType.class),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    RewardStatus.decode(buffer));
        }

        public static void handle(LockResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.kaleblangley.lockscontinue.client.LockClientPacketHandler.result(packet)));
            context.setPacketHandled(true);
        }
    }

    /** Server-owned status for display only; clients never submit these values. */
    public record RewardStatus(RewardMetric metric, int metricValue, int failureAt,
                               boolean metricStarted) {
        public RewardStatus {
            metric = metric == null ? RewardMetric.MISTAKES_USED : metric;
            metricValue = Math.max(0, metricValue);
            failureAt = failureAt < -1 ? -1 : failureAt;
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeEnum(metric);
            buffer.writeVarInt(metricValue);
            buffer.writeInt(failureAt);
            buffer.writeBoolean(metricStarted);
        }

        public static RewardStatus decode(FriendlyByteBuf buffer) {
            return new RewardStatus(buffer.readEnum(RewardMetric.class),
                    buffer.readVarInt(), buffer.readInt(), buffer.readBoolean());
        }
    }

    public record RequestLockSelectionPacket(BlockPos pos, InteractionHand hand) {
        public static void encode(RequestLockSelectionPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.pos);
            buffer.writeEnum(packet.hand);
        }

        public static RequestLockSelectionPacket decode(FriendlyByteBuf buffer) {
            return new RequestLockSelectionPacket(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class));
        }

        public static void handle(RequestLockSelectionPacket packet,
                                  Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                if (context.getSender() != null) {
                    LockSessionManager.requestLockSelection(context.getSender(), packet.pos(), packet.hand());
                }
            });
            context.setPacketHandled(true);
        }
    }

    /** Metadata sent with the toolbox screen so the client can filter affixes safely. */
    public record AffixOption(ResourceLocation id, List<ResourceLocation> difficulties,
                              List<ResourceLocation> lockTypes) {
        public AffixOption {
            difficulties = difficulties == null ? List.of() : List.copyOf(difficulties);
            lockTypes = lockTypes == null ? List.of() : List.copyOf(lockTypes);
        }

        public boolean appliesTo(LockType type, ResourceLocation difficulty) {
            if (type == null || difficulty == null) {
                return false;
            }
            boolean difficultyMatches = difficulties.isEmpty() || difficulties.contains(difficulty);
            boolean typeMatches = lockTypes.isEmpty()
                    || lockTypes.contains(type.resourceId())
                    || lockTypes.contains(new ResourceLocation(type.resourceId().getNamespace(), type.id()));
            return difficultyMatches && typeMatches;
        }
    }

    public record OpenLockSelectionPacket(BlockPos pos, InteractionHand hand,
                                          List<ResourceLocation> difficulties,
                                          List<AffixOption> affixes) {
        public OpenLockSelectionPacket {
            difficulties = difficulties == null ? List.of() : List.copyOf(difficulties);
            affixes = affixes == null ? List.of() : List.copyOf(affixes);
        }

        public static void encode(OpenLockSelectionPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.pos);
            buffer.writeEnum(packet.hand);
            writeResourceLocations(buffer, packet.difficulties, 128);
            writeAffixOptions(buffer, packet.affixes, 256);
        }

        public static OpenLockSelectionPacket decode(FriendlyByteBuf buffer) {
            return new OpenLockSelectionPacket(buffer.readBlockPos(),
                    buffer.readEnum(InteractionHand.class),
                    readResourceLocations(buffer, 128),
                    readAffixOptions(buffer, 256));
        }

        public static void handle(OpenLockSelectionPacket packet,
                                  Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.kaleblangley.lockscontinue.client.LockClientPacketHandler.openSelection(packet)));
            context.setPacketHandled(true);
        }
    }

    public record SelectLockPacket(BlockPos pos, InteractionHand hand, int variant,
                                   ResourceLocation difficulty,
                                   List<ResourceLocation> affixes) {
        public SelectLockPacket {
            affixes = affixes == null ? List.of() : List.copyOf(affixes);
        }

        public static void encode(SelectLockPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.pos);
            buffer.writeEnum(packet.hand);
            buffer.writeVarInt(packet.variant);
            buffer.writeResourceLocation(packet.difficulty);
            writeResourceLocations(buffer, packet.affixes, 32);
        }

        public static SelectLockPacket decode(FriendlyByteBuf buffer) {
            return new SelectLockPacket(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class),
                    buffer.readVarInt(), buffer.readResourceLocation(),
                    readResourceLocations(buffer, 32));
        }

        public static void handle(SelectLockPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                if (context.getSender() != null) {
                    LockSessionManager.applyLockSelection(context.getSender(), packet.pos(),
                            packet.hand(), packet.variant(), packet.difficulty(), packet.affixes());
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record LockSelectionResultPacket(BlockPos pos, boolean success) {
        public static void encode(LockSelectionResultPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.pos);
            buffer.writeBoolean(packet.success);
        }

        public static LockSelectionResultPacket decode(FriendlyByteBuf buffer) {
            return new LockSelectionResultPacket(buffer.readBlockPos(), buffer.readBoolean());
        }

        public static void handle(LockSelectionResultPacket packet,
                                  Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.kaleblangley.lockscontinue.client.LockClientPacketHandler.selectionResult(packet)));
            context.setPacketHandled(true);
        }
    }

    private static void writeResourceLocations(FriendlyByteBuf buffer,
                                               List<ResourceLocation> values, int max) {
        int size = Math.min(max, values.size());
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buffer.writeResourceLocation(values.get(i));
        }
    }

    private static List<ResourceLocation> readResourceLocations(FriendlyByteBuf buffer, int max) {
        int size = buffer.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid lock resource list size: " + size);
        }
        List<ResourceLocation> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buffer.readResourceLocation());
        }
        return values;
    }

    private static void writeAffixOptions(FriendlyByteBuf buffer,
                                          List<AffixOption> values, int max) {
        int size = Math.min(max, values.size());
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            AffixOption option = values.get(i);
            buffer.writeResourceLocation(option.id());
            writeResourceLocations(buffer, option.difficulties(), 128);
            writeResourceLocations(buffer, option.lockTypes(), 16);
        }
    }

    private static List<AffixOption> readAffixOptions(FriendlyByteBuf buffer, int max) {
        int size = buffer.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid lock affix option count: " + size);
        }
        List<AffixOption> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(new AffixOption(buffer.readResourceLocation(),
                    readResourceLocations(buffer, 128), readResourceLocations(buffer, 16)));
        }
        return values;
    }
}

package com.kaleblangley.lockscontinue.network;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.interaction.LockSessionManager;
import com.kaleblangley.lockscontinue.lock.LockType;
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

import java.util.function.Supplier;

/** Network boundary between the client puzzle UI and the server lock session. */
public final class LockNetwork {
    private static final String PROTOCOL_VERSION = "2";
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
                                 int attemptsLeft, ItemStack tool) {
        public static void encode(OpenLockPacket packet, FriendlyByteBuf buffer) {
            buffer.writeLong(packet.sessionId);
            buffer.writeBlockPos(packet.pos);
            buffer.writeEnum(packet.type);
            buffer.writeLong(packet.puzzleSeed);
            buffer.writeVarInt(packet.attemptsLeft);
            buffer.writeItem(packet.tool);
        }

        public static OpenLockPacket decode(FriendlyByteBuf buffer) {
            return new OpenLockPacket(buffer.readLong(), buffer.readBlockPos(),
                    buffer.readEnum(LockType.class), buffer.readLong(), buffer.readVarInt(), buffer.readItem());
        }

        public static void handle(OpenLockPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.kaleblangley.lockscontinue.client.LockClientPacketHandler.open(packet)));
            context.setPacketHandled(true);
        }
    }

    public record LockActionPacket(long sessionId, LockActionType action, int first, int second,
                                   float value, int[] sequence) {
        public static void encode(LockActionPacket packet, FriendlyByteBuf buffer) {
            buffer.writeLong(packet.sessionId);
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
            LockActionType action = buffer.readEnum(LockActionType.class);
            int first = buffer.readVarInt();
            int second = buffer.readVarInt();
            float value = buffer.readFloat();
            int length = buffer.readVarInt();
            if (length < 0 || length > 32) {
                throw new IllegalArgumentException("Invalid lock action sequence length: " + length);
            }
            int[] sequence = new int[length];
            for (int i = 0; i < length; i++) {
                sequence[i] = buffer.readVarInt();
            }
            return new LockActionPacket(sessionId, action, first, second, value, sequence);
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

    public record LockResultPacket(long sessionId, LockResultType result, int attemptsLeft) {
        public static void encode(LockResultPacket packet, FriendlyByteBuf buffer) {
            buffer.writeLong(packet.sessionId);
            buffer.writeEnum(packet.result);
            buffer.writeVarInt(packet.attemptsLeft);
        }

        public static LockResultPacket decode(FriendlyByteBuf buffer) {
            return new LockResultPacket(buffer.readLong(), buffer.readEnum(LockResultType.class), buffer.readVarInt());
        }

        public static void handle(LockResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.kaleblangley.lockscontinue.client.LockClientPacketHandler.result(packet)));
            context.setPacketHandled(true);
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

    public record OpenLockSelectionPacket(BlockPos pos, InteractionHand hand) {
        public static void encode(OpenLockSelectionPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.pos);
            buffer.writeEnum(packet.hand);
        }

        public static OpenLockSelectionPacket decode(FriendlyByteBuf buffer) {
            return new OpenLockSelectionPacket(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class));
        }

        public static void handle(OpenLockSelectionPacket packet,
                                  Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.kaleblangley.lockscontinue.client.LockClientPacketHandler.openSelection(packet)));
            context.setPacketHandled(true);
        }
    }

    public record SelectLockPacket(BlockPos pos, InteractionHand hand, int variant) {
        public static void encode(SelectLockPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.pos);
            buffer.writeEnum(packet.hand);
            buffer.writeVarInt(packet.variant);
        }

        public static SelectLockPacket decode(FriendlyByteBuf buffer) {
            return new SelectLockPacket(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class),
                    buffer.readVarInt());
        }

        public static void handle(SelectLockPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                if (context.getSender() != null) {
                    LockSessionManager.applyLockSelection(context.getSender(), packet.pos(),
                            packet.hand(), packet.variant());
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
}

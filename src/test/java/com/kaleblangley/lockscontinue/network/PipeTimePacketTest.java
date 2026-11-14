package com.kaleblangley.lockscontinue.network;

import com.kaleblangley.lockscontinue.lock.LockType;
import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;
import com.kaleblangley.lockscontinue.lock.definition.LockpickStats;
import com.kaleblangley.lockscontinue.lock.definition.RewardMetric;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PipeTimePacketTest {
    @BeforeAll
    static void initializeMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void openingPacketCarriesServerTimeIndependentlyOfRewardMetricAndThreshold() {
        for (RewardMetric metric : RewardMetric.values()) {
            for (int remaining : new int[]{-1, 0, 1, 587, 1200}) {
                LockNetwork.RewardStatus status = new LockNetwork.RewardStatus(metric, 3, -1, true);
                LockNetwork.OpenLockPacket original = new LockNetwork.OpenLockPacket(
                        42L, new BlockPos(1, 64, -3), LockType.PIPE_LOCK, 123L,
                        64, 4, ItemStack.EMPTY, challenge(remaining < 0 ? -1 : 1200), status, remaining);
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    LockNetwork.OpenLockPacket.encode(original, buffer);
                    LockNetwork.OpenLockPacket decoded = LockNetwork.OpenLockPacket.decode(buffer);
                    assertEquals(remaining, decoded.pipeTimeLeftTicks());
                    assertEquals(status, decoded.rewardStatus());
                    assertEquals(original.challenge(), decoded.challenge());
                    assertEquals(0, buffer.readableBytes());
                } finally {
                    buffer.release();
                }
            }
        }
    }

    @Test
    void resultPacketPreservesUnlimitedAndExpiredTime() {
        for (int remaining : new int[]{-1, 0, 587}) {
            LockNetwork.LockResultPacket original = new LockNetwork.LockResultPacket(
                    42L, LockResultType.PROGRESS, 64, 4, remaining,
                    new LockNetwork.RewardStatus(RewardMetric.TIME_TICKS, 613, -1, true));
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                LockNetwork.LockResultPacket.encode(original, buffer);
                assertEquals(original, LockNetwork.LockResultPacket.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void compatibilityConstructorCombinesDifficultyAndRewardLimits() {
        assertEquals(800, compatibilityPacket(1200, -1).pipeTimeLeftTicks());
        assertEquals(200, compatibilityPacket(1200, 600).pipeTimeLeftTicks());
        assertEquals(-1, compatibilityPacket(-1, -1).pipeTimeLeftTicks());
    }

    private static LockNetwork.OpenLockPacket compatibilityPacket(int difficultyTicks, int rewardTicks) {
        return new LockNetwork.OpenLockPacket(42L, BlockPos.ZERO, LockType.PIPE_LOCK, 123L,
                64, 4, ItemStack.EMPTY, challenge(difficultyTicks),
                new LockNetwork.RewardStatus(RewardMetric.TIME_TICKS, 400, rewardTicks, true));
    }

    private static LockChallengeSnapshot challenge(int timeLimitTicks) {
        return new LockChallengeSnapshot(new ResourceLocation("lockscontinue", "normal"),
                2, 0xFFFFFFFF, 4, 1, 5, 2, 1, 2, 1.0F,
                5, 5, 2, 5, 9, 11, 23, 3, 0, false, false, 1, timeLimitTicks,
                9.0F, 1.5F, 1.0F, 4, 4, 15, 5, 5, 5, 1,
                List.of(), LockpickStats.EMPTY, List.of());
    }
}

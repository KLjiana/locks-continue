package com.kaleblangley.lockscontinue.lock.definition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PipeTimeLimitTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void difficultyLimitStillAppliesWhenRewardFailureIsDisabled() {
        assertEquals(1200, PipeTimeLimit.resolve(1200, RewardMetric.TIME_TICKS, -1));
        assertEquals(600, PipeTimeLimit.resolve(1200, RewardMetric.TIME_TICKS, 600));
        assertEquals(1200, PipeTimeLimit.resolve(1200, RewardMetric.TIME_TICKS, 2400));
    }

    @Test
    void mistakeGradingDoesNotDisableTheIndependentPipeTimer() {
        int limit = PipeTimeLimit.resolve(1200, RewardMetric.MISTAKES_USED, 4);
        RewardAttemptState attempt = RewardAttemptState.fresh(PLAYER).start(100L).addMistake();
        assertEquals(1200, limit);
        assertEquals(1, attempt.mistakesUsed());
        assertEquals(600, PipeTimeLimit.remainingTicks(limit, attempt.elapsedTicks(700L)));
        assertTrue(PipeTimeLimit.reached(limit, attempt, 1300L));
    }

    @Test
    void unopenedTimerKeepsItsFullAllowanceUntilTheFirstRotation() {
        RewardAttemptState attempt = RewardAttemptState.fresh(PLAYER);
        assertEquals(0L, attempt.elapsedTicks(100_000L));
        assertEquals(1200, PipeTimeLimit.remainingTicks(1200, attempt.elapsedTicks(100_000L)));
        assertFalse(PipeTimeLimit.reached(1200, attempt, 100_000L));
    }

    @Test
    void deadlineIsInclusiveAndRemainingTimeNeverBecomesNegative() {
        RewardAttemptState attempt = RewardAttemptState.fresh(PLAYER).start(100L);
        assertFalse(PipeTimeLimit.reached(1200, attempt, 1299L));
        assertEquals(1, PipeTimeLimit.remainingTicks(1200, attempt.elapsedTicks(1299L)));
        assertTrue(PipeTimeLimit.reached(1200, attempt, 1300L));
        assertEquals(0, PipeTimeLimit.remainingTicks(1200, attempt.elapsedTicks(1300L)));
        assertEquals(0, PipeTimeLimit.remainingTicks(1200, Long.MAX_VALUE));
    }

    @Test
    void savedAttemptResumesWithoutResettingItsDeadline() {
        RewardAttemptState original = RewardAttemptState.fresh(PLAYER).start(100L).addMistake();
        RewardAttemptState restored = RewardAttemptState.load(original.save(), PLAYER);
        assertNotNull(restored);
        assertEquals(original, restored);
        assertSame(restored, restored.start(700L));
        assertEquals(600, PipeTimeLimit.remainingTicks(1200, restored.elapsedTicks(700L)));
        assertTrue(PipeTimeLimit.reached(1200, restored, 1300L));

        RewardAttemptState failed = RewardAttemptState.load(restored.fail().save(), PLAYER);
        assertNotNull(failed);
        assertTrue(failed.failed());
        RewardAttemptState retry = RewardAttemptState.fresh(PLAYER);
        assertFalse(retry.metricStarted());
        assertFalse(PipeTimeLimit.reached(1200, retry, 10_000L));
    }

    @Test
    void unlimitedRequiresBothTimeLimitsToBeDisabled() {
        assertEquals(600, PipeTimeLimit.resolve(-1, RewardMetric.TIME_TICKS, 600));
        int limit = PipeTimeLimit.resolve(-1, RewardMetric.TIME_TICKS, -1);
        assertEquals(-1, limit);
        assertEquals(-1, PipeTimeLimit.remainingTicks(limit, Long.MAX_VALUE));
        assertFalse(PipeTimeLimit.reached(limit, RewardAttemptState.fresh(PLAYER).start(0L),
                Long.MAX_VALUE));
    }

    @Test
    void datapackAcceptsUnlimitedAndRejectsInvalidDifficultyLimits() {
        assertEquals(1200, difficulty(new JsonObject()).pipe().timeLimitTicks());
        for (int value : new int[]{-1, 20, 1200, 7200}) {
            assertEquals(value, difficulty(pipeJson(value)).pipe().timeLimitTicks());
        }
        for (int value : new int[]{-2, 0, 19, 7201}) {
            assertThrows(IllegalArgumentException.class, () -> difficulty(pipeJson(value)));
        }
    }

    @Test
    void affixResolutionPreservesUnlimitedAndClampsFiniteLimits() {
        assertEquals(-1, PipeTimeLimit.normalize(-1.0D));
        assertEquals(20, PipeTimeLimit.normalize(0.0D));
        assertEquals(7200, PipeTimeLimit.normalize(10_000.0D));
        assertEquals(1200, PipeTimeLimit.normalize(Double.NaN));
    }

    private static JsonObject pipeJson(int ticks) {
        return JsonParser.parseString("{\"pipe\":{\"time_limit_ticks\":" + ticks + "}}")
                .getAsJsonObject();
    }

    private static LockDifficultyDefinition difficulty(JsonObject json) {
        return LockDifficultyDefinition.fromJson(new ResourceLocation("lockscontinue", "test"), json);
    }
}

package com.kaleblangley.lockscontinue.lock.definition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/** Persistent per-player performance state for one locked container. */
public record RewardAttemptState(UUID playerId, boolean metricStarted, long startedAt,
                                 int mistakesUsed, boolean failed) {
    public static RewardAttemptState fresh(UUID playerId) {
        return new RewardAttemptState(playerId, false, 0L, 0, false);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", playerId);
        tag.putBoolean("metric_started", metricStarted);
        tag.putLong("started_at", startedAt);
        tag.putInt("mistakes_used", Math.max(0, mistakesUsed));
        tag.putBoolean("failed", failed);
        return tag;
    }

    public static RewardAttemptState load(CompoundTag tag, UUID fallbackPlayer) {
        if (tag == null) {
            return null;
        }
        UUID player = tag.hasUUID("player") ? tag.getUUID("player") : fallbackPlayer;
        if (player == null) {
            return null;
        }
        return new RewardAttemptState(player,
                tag.getBoolean("metric_started"), tag.getLong("started_at"),
                Math.max(0, tag.getInt("mistakes_used")), tag.getBoolean("failed"));
    }

    public RewardAttemptState start(long gameTime) {
        return metricStarted ? this : new RewardAttemptState(playerId, true, gameTime,
                mistakesUsed, failed);
    }

    public long elapsedTicks(long gameTime) {
        return metricStarted ? Math.max(0L, gameTime - startedAt) : 0L;
    }

    public RewardAttemptState addMistake() {
        return new RewardAttemptState(playerId, metricStarted, startedAt,
                mistakesUsed + 1, failed);
    }

    public RewardAttemptState fail() {
        return new RewardAttemptState(playerId, metricStarted, startedAt,
                mistakesUsed, true);
    }
}

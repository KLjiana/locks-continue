package com.kaleblangley.lockscontinue.lock.definition;

import com.kaleblangley.lockscontinue.lock.LockType;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/** A data-pack rule matching a lock type/difficulty pair. */
public record RewardRule(Set<ResourceLocation> lockTypes,
                         Set<ResourceLocation> difficulties,
                         RewardMetric metric,
                         int failureAt,
                         List<RewardTier> tiers) {
    public RewardRule {
        lockTypes = lockTypes == null ? Set.of() : Set.copyOf(lockTypes);
        difficulties = difficulties == null ? Set.of() : Set.copyOf(difficulties);
        tiers = tiers == null ? List.of() : List.copyOf(tiers);
    }

    public boolean matches(LockType type, ResourceLocation difficulty) {
        if (type == null || difficulty == null) {
            return false;
        }
        return (lockTypes.isEmpty() || lockTypes.contains(type.resourceId()))
                && (difficulties.isEmpty() || difficulties.contains(difficulty));
    }

    /** Selects the first inclusive tier; an unmatched value deliberately falls back safely. */
    public RewardTier tierFor(int metricValue) {
        for (RewardTier tier : tiers) {
            if (metricValue <= tier.max()) {
                return tier;
            }
        }
        return RewardTier.safe();
    }
}

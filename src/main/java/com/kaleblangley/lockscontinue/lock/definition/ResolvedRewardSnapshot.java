package com.kaleblangley.lockscontinue.lock.definition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Immutable reward rule snapshot stored inside a lock instance. */
public record ResolvedRewardSnapshot(ResourceLocation profileId,
                                    RewardMetric metric,
                                    int failureAt,
                                    List<RewardTier> tiers) {
    public ResolvedRewardSnapshot {
        profileId = profileId == null
                ? new ResourceLocation("lockscontinue", "default") : profileId;
        metric = metric == null ? RewardMetric.MISTAKES_USED : metric;
        failureAt = failureAt < -1 ? -1 : failureAt;
        tiers = tiers == null || tiers.isEmpty() ? List.of(RewardTier.safe()) : List.copyOf(tiers);
    }

    public RewardTier tierFor(int metricValue) {
        for (RewardTier tier : tiers) {
            if (metricValue <= tier.max()) {
                return tier;
            }
        }
        return RewardTier.safe();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putString("profile", profileId.toString());
        tag.putString("metric", metric.id());
        tag.putInt("failure_at", failureAt);
        ListTag tierTags = new ListTag();
        for (RewardTier tier : tiers) {
            tierTags.add(tier.save());
        }
        tag.put("tiers", tierTags);
        return tag;
    }

    public static ResolvedRewardSnapshot load(CompoundTag tag) {
        if (tag == null || !tag.contains("metric", Tag.TAG_STRING)) {
            return null;
        }
        ResourceLocation profile = tag.contains("profile", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("profile")) : null;
        RewardMetric metric;
        try {
            metric = RewardMetric.fromId(tag.getString("metric"));
        } catch (RuntimeException exception) {
            return null;
        }
        List<RewardTier> tiers = new ArrayList<>();
        ListTag tierTags = tag.getList("tiers", Tag.TAG_COMPOUND);
        for (int i = 0; i < tierTags.size(); i++) {
            RewardTier tier = RewardTier.load(tierTags.getCompound(i));
            if (tier != null) {
                tiers.add(tier);
            }
        }
        return new ResolvedRewardSnapshot(profile, metric,
                tag.contains("failure_at", Tag.TAG_INT) ? tag.getInt("failure_at") : -1,
                tiers);
    }
}

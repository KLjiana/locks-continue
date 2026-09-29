package com.kaleblangley.lockscontinue.lock;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.lock.definition.ResolvedRewardSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Immutable persistent identity of one installed lock. */
public record LockInstance(
        int schemaVersion,
        ResourceLocation lockType,
        ResourceLocation difficulty,
        long seed,
        ResourceLocation sourceLootTable,
        List<ResourceLocation> affixes,
        ResourceLocation rewardProfile,
        ResolvedRewardSnapshot rewardSnapshot
) {
    public static final int CURRENT_SCHEMA = 2;
    public static final ResourceLocation NORMAL_DIFFICULTY =
            new ResourceLocation(LocksContinue.MODID, "normal");

    public LockInstance {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA : schemaVersion;
        lockType = lockType == null ? LockType.MEMORY_LOCK.resourceId() : lockType;
        difficulty = difficulty == null ? NORMAL_DIFFICULTY : difficulty;
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
    }

    public static LockInstance create(LockType type, ResourceLocation difficulty, long seed,
                                      ResourceLocation sourceLootTable,
                                      List<ResourceLocation> affixes) {
        return new LockInstance(CURRENT_SCHEMA, type.resourceId(), difficulty, seed,
                sourceLootTable, affixes, null, null);
    }

    public static LockInstance create(LockType type, ResourceLocation difficulty, long seed,
                                      ResourceLocation sourceLootTable,
                                      List<ResourceLocation> affixes,
                                      ResourceLocation rewardProfile,
                                      ResolvedRewardSnapshot rewardSnapshot) {
        return new LockInstance(CURRENT_SCHEMA, type.resourceId(), difficulty, seed,
                sourceLootTable, affixes, rewardProfile, rewardSnapshot);
    }

    public static LockInstance fromLegacyVariant(int variant, long seed) {
        return create(LockType.fromVariant(variant), NORMAL_DIFFICULTY, seed, null, List.of());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", schemaVersion);
        tag.putString("type", lockType.toString());
        tag.putString("difficulty", difficulty.toString());
        tag.putLong("seed", seed);
        if (sourceLootTable != null) {
            tag.putString("source_loot_table", sourceLootTable.toString());
        }
        if (rewardProfile != null) {
            tag.putString("reward_profile", rewardProfile.toString());
        }
        if (rewardSnapshot != null) {
            tag.put("reward", rewardSnapshot.save());
        }

        ListTag affixTag = new ListTag();
        for (ResourceLocation affix : affixes) {
            if (affix != null) {
                affixTag.add(net.minecraft.nbt.StringTag.valueOf(affix.toString()));
            }
        }
        tag.put("affixes", affixTag);
        return tag;
    }

    /** Reads a new-format instance; invalid resource IDs are ignored safely. */
    public static LockInstance load(CompoundTag tag) {
        if (tag == null || !tag.contains("type", Tag.TAG_STRING)) {
            return null;
        }

        ResourceLocation type = ResourceLocation.tryParse(tag.getString("type"));
        if (type == null) {
            type = LockType.MEMORY_LOCK.resourceId();
        }
        ResourceLocation difficulty = tag.contains("difficulty", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("difficulty")) : NORMAL_DIFFICULTY;
        if (difficulty == null) {
            difficulty = NORMAL_DIFFICULTY;
        }
        ResourceLocation sourceLootTable = tag.contains("source_loot_table", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("source_loot_table")) : null;

        List<ResourceLocation> affixes = new ArrayList<>();
        ListTag affixTag = tag.getList("affixes", Tag.TAG_STRING);
        for (int i = 0; i < affixTag.size(); i++) {
            ResourceLocation affix = ResourceLocation.tryParse(affixTag.getString(i));
            if (affix != null) {
                affixes.add(affix);
            }
        }

        int schema = tag.contains("schema", Tag.TAG_INT) ? tag.getInt("schema") : 1;
        ResourceLocation rewardProfile = tag.contains("reward_profile", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("reward_profile")) : null;
        ResolvedRewardSnapshot rewardSnapshot = tag.contains("reward", Tag.TAG_COMPOUND)
                ? ResolvedRewardSnapshot.load(tag.getCompound("reward")) : null;

        return new LockInstance(
                schema,
                type,
                difficulty,
                tag.getLong("seed"),
                sourceLootTable,
                affixes,
                rewardProfile,
                rewardSnapshot
        );
    }

    public LockInstance withRewardSnapshot(ResourceLocation profile,
                                           ResolvedRewardSnapshot snapshot) {
        return new LockInstance(CURRENT_SCHEMA, lockType, difficulty, seed,
                sourceLootTable, affixes, profile, snapshot);
    }
}

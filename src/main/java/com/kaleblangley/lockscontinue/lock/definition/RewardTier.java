package com.kaleblangley.lockscontinue.lock.definition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** One inclusive upper-bound reward tier. */
public record RewardTier(int max, int sourceRolls,
                         ResourceLocation bonusLootTable, int bonusRolls,
                         double sourceLuckBonus) {
    public static final double DEFAULT_SOURCE_LUCK_BONUS = 2.0D;
    public static final double MAX_SOURCE_LUCK_BONUS = 20.0D;

    public RewardTier {
        max = Math.max(0, max);
        sourceRolls = Math.max(1, Math.min(5, sourceRolls));
        bonusRolls = Math.max(0, Math.min(5, bonusRolls));
        sourceLuckBonus = Double.isFinite(sourceLuckBonus)
                ? Math.max(0.0D, Math.min(MAX_SOURCE_LUCK_BONUS, sourceLuckBonus))
                : 0.0D;
    }

    public static double defaultSourceLuckBonus(int sourceRolls) {
        return sourceRolls > 1 ? DEFAULT_SOURCE_LUCK_BONUS : 0.0D;
    }

    public static RewardTier safe() {
        return new RewardTier(Integer.MAX_VALUE, 1, null, 0, 0.0D);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("max", max);
        tag.putInt("source_rolls", sourceRolls);
        if (bonusLootTable != null) {
            tag.putString("bonus_loot_table", bonusLootTable.toString());
        }
        tag.putInt("bonus_rolls", bonusRolls);
        tag.putDouble("source_luck_bonus", sourceLuckBonus);
        return tag;
    }

    public static RewardTier load(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        ResourceLocation bonus = tag.contains("bonus_loot_table", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("bonus_loot_table")) : null;
        int sourceRolls = Math.max(1, tag.contains("source_rolls", Tag.TAG_INT)
                ? tag.getInt("source_rolls") : 1);
        double sourceLuckBonus = tag.contains("source_luck_bonus", Tag.TAG_DOUBLE)
                ? tag.getDouble("source_luck_bonus") : defaultSourceLuckBonus(sourceRolls);
        return new RewardTier(Math.max(0, tag.getInt("max")), sourceRolls, bonus,
                Math.max(0, tag.getInt("bonus_rolls")), sourceLuckBonus);
    }
}

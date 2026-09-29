package com.kaleblangley.lockscontinue.lock.definition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** One inclusive upper-bound reward tier. */
public record RewardTier(int max, int sourceRolls,
                         ResourceLocation bonusLootTable, int bonusRolls) {
    public static RewardTier safe() {
        return new RewardTier(Integer.MAX_VALUE, 1, null, 0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("max", max);
        tag.putInt("source_rolls", sourceRolls);
        if (bonusLootTable != null) {
            tag.putString("bonus_loot_table", bonusLootTable.toString());
        }
        tag.putInt("bonus_rolls", bonusRolls);
        return tag;
    }

    public static RewardTier load(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        ResourceLocation bonus = tag.contains("bonus_loot_table", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("bonus_loot_table")) : null;
        return new RewardTier(Math.max(0, tag.getInt("max")),
                Math.max(1, tag.contains("source_rolls", Tag.TAG_INT)
                        ? tag.getInt("source_rolls") : 1), bonus,
                Math.max(0, tag.getInt("bonus_rolls")));
    }
}

package com.kaleblangley.lockscontinue.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class LocksContinueConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue LOOT_CHEST_LOCK_CHANCE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("world_generation");
        LOOT_CHEST_LOCK_CHANCE = builder
                .comment("Chance for a newly generated loot chest to receive a lock.",
                        "0.10 means 10 percent.")
                .defineInRange("lootChestLockChance", 0.2D, 0.0D, 1.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private LocksContinueConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}

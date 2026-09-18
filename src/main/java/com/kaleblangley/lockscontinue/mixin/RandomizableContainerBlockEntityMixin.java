package com.kaleblangley.lockscontinue.mixin;

import com.kaleblangley.lockscontinue.world.LootChestLockGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomizableContainerBlockEntity.class)
public abstract class RandomizableContainerBlockEntityMixin {
    @Inject(
            method = "setLootTable(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/resources/ResourceLocation;)V",
            at = @At("TAIL")
    )
    private static void lockscontinue$onStaticLootTableSet(
            BlockGetter level,
            RandomSource random,
            BlockPos pos,
            ResourceLocation lootTable,
            CallbackInfo ci
    ) {
        LootChestLockGenerator.tryGenerateLock(level, random, pos);
    }

    @Inject(
            method = "setLootTable(Lnet/minecraft/resources/ResourceLocation;J)V",
            at = @At("TAIL")
    )
    private void lockscontinue$onLootTableSet(
            ResourceLocation lootTable,
            long lootTableSeed,
            CallbackInfo ci
    ) {
        LootChestLockGenerator.markLootChest((RandomizableContainerBlockEntity) (Object) this);
    }

    @Inject(
            method = "tryLoadLootTable(Lnet/minecraft/nbt/CompoundTag;)Z",
            at = @At("RETURN")
    )
    private void lockscontinue$onLootTableLoaded(CompoundTag tag, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            LootChestLockGenerator.markLootChest((RandomizableContainerBlockEntity) (Object) this);
        }
    }
}

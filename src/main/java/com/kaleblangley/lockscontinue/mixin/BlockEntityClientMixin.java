package com.kaleblangley.lockscontinue.mixin;

import com.kaleblangley.lockscontinue.client.LockManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Updates the client lock registry when a block entity receives synced NBT. */
@Mixin(BlockEntity.class)
public abstract class BlockEntityClientMixin {
    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void lockscontinue$refreshLockRegistry(CompoundTag tag, CallbackInfo ci) {
        LockManager.refresh((BlockEntity) (Object) this);
    }

    @Inject(method = "setRemoved()V", at = @At("TAIL"))
    private void lockscontinue$removeFromLockRegistry(CallbackInfo ci) {
        LockManager.remove((BlockEntity) (Object) this);
    }
}

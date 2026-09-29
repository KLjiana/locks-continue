package com.kaleblangley.lockscontinue.mixin;

import com.kaleblangley.lockscontinue.lock.LockData;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides the item-handler capability while a supported container is locked. */
@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerBlockEntityMixin {
    @Inject(method = "getCapability", at = @At("HEAD"), remap = false, cancellable = true)
    private <T> void lockscontinue$denyItemHandler(
            Capability<T> capability,
            Direction side,
            CallbackInfoReturnable<LazyOptional<T>> cir) {
        if (capability == ForgeCapabilities.ITEM_HANDLER
                && LockData.hasLock((BaseContainerBlockEntity) (Object) this)) {
            cir.setReturnValue(LazyOptional.empty());
        }
    }
}

package com.kaleblangley.lockscontinue.mixin;

import com.kaleblangley.lockscontinue.lock.LockData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks vanilla hopper transfers to and from a locked container. */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void lockscontinue$denyLockedSource(
            Level level,
            Hopper hopper,
            CallbackInfoReturnable<Boolean> cir) {
        BlockPos sourcePos = BlockPos.containing(
                hopper.getLevelX(), hopper.getLevelY() + 1.0D, hopper.getLevelZ());
        if (LockData.hasLock(level.getBlockEntity(sourcePos))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "ejectItems", at = @At("HEAD"), remap = false, cancellable = true)
    private static void lockscontinue$denyLockedDestination(
            Level level,
            BlockPos pos,
            BlockState state,
            HopperBlockEntity hopper,
            CallbackInfoReturnable<Boolean> cir) {
        BlockPos destinationPos = pos.relative(state.getValue(HopperBlock.FACING));
        if (LockData.hasLock(level.getBlockEntity(destinationPos))) {
            cir.setReturnValue(false);
        }
    }
}

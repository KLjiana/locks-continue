package com.kaleblangley.lockscontinue.mixin;

import com.kaleblangley.lockscontinue.lock.LockData;
import com.kaleblangley.lockscontinue.world.LootChestLockGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Includes the Forge persistent lock data in block-entity update packets. */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {
    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void lockscontinue$markLootChestFromLoadedTag(CompoundTag tag, CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        // Migrate old worlds before any lock lookup or client sync observes
        // the legacy variant-only representation.
        if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide()) {
            LockData.migrateLegacy(blockEntity);
        }
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)
                || (!tag.contains("LootTable", Tag.TAG_STRING)
                && !tag.contains("specialLootChest_table", Tag.TAG_STRING))) {
            return;
        }

        // Lootr reads specialLootChest_table before delegating to this
        // vanilla load method, so this also covers Lootr's custom load path.
        LootChestLockGenerator.markLootChest(container);
    }

    @Inject(method = "getUpdateTag()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void lockscontinue$syncLockData(CallbackInfoReturnable<CompoundTag> cir) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (!LockData.hasLock(blockEntity) && !LockData.isUnlocked(blockEntity)) {
            return;
        }

        cir.getReturnValue().put("ForgeData", blockEntity.getPersistentData().copy());
    }

    @Inject(method = "getUpdatePacket()Lnet/minecraft/network/protocol/Packet;",
            at = @At("HEAD"), cancellable = true)
    private void lockscontinue$sendLockData(CallbackInfoReturnable<Packet<ClientGamePacketListener>> cir) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (LockData.hasLockData(blockEntity) || LockData.isUnlocked(blockEntity)) {
            cir.setReturnValue(ClientboundBlockEntityDataPacket.create(blockEntity));
        }
    }
}

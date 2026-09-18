package com.kaleblangley.lockscontinue.client;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.item.ModItems;
import com.kaleblangley.lockscontinue.lock.LockData;
import com.kaleblangley.lockscontinue.network.LockNetwork;
import com.kaleblangley.lockscontinue.sound.ModSounds;
import com.kaleblangley.lockscontinue.lock.LockType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

/** Cancels the client-side container prediction and starts the lock animation. */
@Mod.EventBusSubscriber(modid = LocksContinue.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LockClientInteractionHandler {
    private LockClientInteractionHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }

        ClientLevel level = (ClientLevel) event.getLevel();
        ItemStack heldItem = event.getEntity().getItemInHand(event.getHand());
        if (event.getEntity().isShiftKeyDown()
                && heldItem.is(ModItems.LOCK.get())
                && LockData.isLockableContainer(level.getBlockEntity(event.getPos()))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            LockNetwork.CHANNEL.sendToServer(new LockNetwork.RequestLockSelectionPacket(
                    event.getPos().immutable(), event.getHand()));
            return;
        }

        LockManager.LockRecord lock = LockManager.getLoadedLock(level, event.getPos());
        if (lock == null) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);

        LockType type = LockType.fromVariant(lock.variant());
        if (!type.canOpen(event.getEntity().getItemInHand(event.getHand()))) {
            LockSoundHelper.playUi(ModSounds.LOCK_RATTLE.get(), 0.7F, 0.8F);
            LockLevelRenderer.shake(event.getPos());
        }
    }
}

package com.kaleblangley.lockscontinue.interaction;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.item.ModItems;
import com.kaleblangley.lockscontinue.lock.LockData;
import com.kaleblangley.lockscontinue.lock.LockType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Prevents a locked loot container from opening on the logical server. */
@Mod.EventBusSubscriber(modid = LocksContinue.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LockInteractionHandler {
    private LockInteractionHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        if (player.isShiftKeyDown()
                && player.getItemInHand(event.getHand()).is(ModItems.LOCK.get())
                && LockData.isLockableContainer(level.getBlockEntity(event.getPos()))) {
            // The client sends a dedicated request for the selection screen.
            // Cancel the vanilla interaction so the chest cannot open first.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        RandomizableContainerBlockEntity lockedContainer = LockSessionManager.findLockedContainer(
                serverPlayer.serverLevel(), event.getPos());
        if (lockedContainer == null) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);

        LockType type = LockType.fromVariant(LockData.getVariant(lockedContainer));
        net.minecraft.world.item.ItemStack tool = player.getItemInHand(event.getHand());
        if (!type.canOpen(tool)) {
            player.displayClientMessage(Component.translatable(
                    "message.lockscontinue.lockpick_required"), true);
            return;
        }

        LockSessionManager.begin(serverPlayer, lockedContainer.getBlockPos(), type, event.getHand());
    }
}

package com.kaleblangley.lockscontinue.event;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.lock.LockData;
import com.kaleblangley.lockscontinue.lock.definition.LockDefinitionManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid = LocksContinue.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvent {
    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(LockDefinitionManager.INSTANCE);
    }

    /** Prevents a locked container from being destroyed by a player. */
    @SubscribeEvent
    public static void protectLockedContainerFromBreaking(BlockEvent.BreakEvent event) {
        // Creative builders can deliberately remove a lock with the explicit
        // sneak-break gesture.  Normal creative breaking and all survival
        // breaking remain protected by the lock.
        if (event.getPlayer().isCreative() && event.getPlayer().isShiftKeyDown()) {
            return;
        }
        if (LockData.hasLock(event.getLevel().getBlockEntity(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    /** Removes locked containers from an explosion's block-destruction list. */
    @SubscribeEvent
    public static void protectLockedContainerFromExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos ->
                LockData.hasLock(event.getLevel().getBlockEntity(pos)));
    }
}

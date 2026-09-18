package com.kaleblangley.lockscontinue.client.render;

import com.kaleblangley.lockscontinue.client.LockManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Renderer for vanilla and Lootr shulker boxes, raised slightly on the block. */
public final class ShulkerLockRenderer extends AbstractLockRenderer {
    private static final double HEIGHT_OFFSET = 0.10D;

    @Override
    public boolean supports(BlockState state) {
        return state.getBlock() instanceof ShulkerBoxBlock;
    }

    @Override
    protected Vec3 getWorldOffset(ClientLevel level, LockManager.LockRecord lock, BlockState state) {
        return new Vec3(0.0D, HEIGHT_OFFSET, 0.0D);
    }
}

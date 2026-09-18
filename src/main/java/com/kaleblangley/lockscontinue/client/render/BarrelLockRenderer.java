package com.kaleblangley.lockscontinue.client.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.kaleblangley.lockscontinue.client.LockManager;

/** Renderer for vanilla and Lootr barrels, raised slightly on the block. */
public final class BarrelLockRenderer extends AbstractLockRenderer {
    private static final double HEIGHT_OFFSET = 0.10D;

    @Override
    public boolean supports(BlockState state) {
        return state.getBlock() instanceof BarrelBlock;
    }

    @Override
    protected Vec3 getWorldOffset(ClientLevel level, LockManager.LockRecord lock, BlockState state) {
        return new Vec3(0.0D, HEIGHT_OFFSET, 0.0D);
    }
}

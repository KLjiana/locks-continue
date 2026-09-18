package com.kaleblangley.lockscontinue.client.render;

import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Default renderer for vanilla and Lootr chest-family blocks. */
public final class ChestLockRenderer extends AbstractLockRenderer {
    @Override
    public boolean supports(BlockState state) {
        return state.getBlock() instanceof ChestBlock;
    }
}

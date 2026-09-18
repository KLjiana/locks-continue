package com.kaleblangley.lockscontinue.client.render;

import com.kaleblangley.lockscontinue.client.LockManager;
import com.kaleblangley.lockscontinue.lock.LockData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Container-specific transform strategy for a world lock visual.
 *
 * <p>The default implementation handles the shared lock placement rules:
 * double-chest centering, face offset and orientation. A specialized
 * container only needs to override the hooks that differ, such as its world
 * offset or an additional matrix transform.</p>
 */
public abstract class AbstractLockRenderer {
    private static final double DEFAULT_FACING_OFFSET = -0.06D;
    private static final double DEFAULT_FACE_DISTANCE = 0.53D;

    /** Returns whether this renderer owns the supplied container block state. */
    public abstract boolean supports(BlockState state);

    /**
     * Returns the point used as the lock origin. Double chests use the midpoint
     * of their two block centers; other containers use their own center.
     */
    public Vec3 getRenderCenter(ClientLevel level, LockManager.LockRecord lock, BlockState state) {
        BlockPos pos = lock.pos();
        BlockPos connectedPos = LockData.getConnectedPos(pos, state);
        if (connectedPos == null) {
            return Vec3.atCenterOf(pos);
        }

        return new Vec3(
                (pos.getX() + connectedPos.getX()) * 0.5D + 0.5D,
                (pos.getY() + connectedPos.getY()) * 0.5D + 0.5D,
                (pos.getZ() + connectedPos.getZ()) * 0.5D + 0.5D
        );
    }

    /**
     * Applies the shared world-to-face transform before the item model is
     * rendered. Subclasses can override this method for a completely custom
     * matrix, or use the smaller hooks below for ordinary adjustments.
     */
    public void applyContainerTransform(PoseStack poseStack, ClientLevel level,
                                         Vec3 cameraPosition, Vec3 renderCenter,
                                         LockManager.LockRecord lock, BlockState state) {
        Direction facing = lock.facing();
        Vec3 worldOffset = getWorldOffset(level, lock, state);
        double facingOffset = getFacingOffset(level, lock, state);

        poseStack.translate(
                renderCenter.x + worldOffset.x + facing.getStepX() * facingOffset - cameraPosition.x,
                renderCenter.y + worldOffset.y + facing.getStepY() * facingOffset - cameraPosition.y,
                renderCenter.z + worldOffset.z + facing.getStepZ() * facingOffset - cameraPosition.z
        );
        poseStack.translate(
                facing.getStepX() * getFaceDistance(level, lock, state),
                facing.getStepY() * getFaceDistance(level, lock, state),
                facing.getStepZ() * getFaceDistance(level, lock, state)
        );
        orientToFacing(poseStack, facing);
        applyFaceTransform(poseStack, level, lock, state, facing);
    }

    /** Returns an offset in world coordinates before the face transform. */
    protected Vec3 getWorldOffset(ClientLevel level, LockManager.LockRecord lock, BlockState state) {
        return Vec3.ZERO;
    }

    /** Returns the small offset used before moving the model onto the face. */
    protected double getFacingOffset(ClientLevel level, LockManager.LockRecord lock,
                                     BlockState state) {
        return DEFAULT_FACING_OFFSET;
    }

    /** Returns how far the model is moved out from the container face. */
    protected double getFaceDistance(ClientLevel level, LockManager.LockRecord lock,
                                     BlockState state) {
        return DEFAULT_FACE_DISTANCE;
    }

    /** Hook for rotations or local translations after the face orientation. */
    protected void applyFaceTransform(PoseStack poseStack, ClientLevel level,
                                      LockManager.LockRecord lock, BlockState state,
                                      Direction facing) {
    }

    private static void orientToFacing(PoseStack poseStack, Direction facing) {
        if (facing.getAxis().isHorizontal()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(
                    180.0F - facing.get2DDataValue() * 90.0F));
            return;
        }

        // The fixed item model faces +Z before this transform. Rotate that
        // normal onto the top or bottom face for vertically placed containers.
        poseStack.mulPose(Axis.XP.rotationDegrees(facing == Direction.UP ? -90.0F : 90.0F));
    }
}

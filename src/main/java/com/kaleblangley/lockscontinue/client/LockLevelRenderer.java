package com.kaleblangley.lockscontinue.client;

import com.kaleblangley.lockscontinue.client.render.AbstractLockRenderer;
import com.kaleblangley.lockscontinue.client.render.BarrelLockRenderer;
import com.kaleblangley.lockscontinue.client.render.ChestLockRenderer;
import com.kaleblangley.lockscontinue.client.render.ShulkerLockRenderer;
import com.kaleblangley.lockscontinue.lock.LockData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Draws lock icons directly during LevelRenderer's world pass. */
public final class LockLevelRenderer {
    private static final double MAX_RENDER_DISTANCE_SQR = 64.0D * 64.0D;
    private static final float SHAKE_DURATION_TICKS = 8.0F;
    private static final float SHAKE_ANGLE = 65.0F;
    private static final Map<BlockPos, ShakeState> SHAKES = new ConcurrentHashMap<>();
    private static final List<AbstractLockRenderer> CONTAINER_RENDERERS = List.of(
            new ChestLockRenderer(),
            new BarrelLockRenderer(),
            new ShulkerLockRenderer()
    );

    private LockLevelRenderer() {
    }

    public static void shake(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        LockManager.LockRecord lock = LockManager.getLoadedLock(level, pos);
        BlockPos visualPos = lock == null ? pos : lock.pos();
        float direction = (visualPos.asLong() & 1L) == 0L ? 1.0F : -1.0F;
        SHAKES.put(visualPos.immutable(), new ShakeState(level.getGameTime(), direction));
    }

    public static void render(PoseStack poseStack, float partialTick, Camera camera,
                              MultiBufferSource.BufferSource bufferSource) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        Vec3 cameraPosition = camera.getPosition();
        for (BlockPos pos : LockManager.lockedPositions()) {
            LockManager.LockRecord lock = LockManager.getLoadedLock(level, pos);
            if (lock == null) {
                continue;
            }

            BlockState state = level.getBlockState(lock.pos());
            AbstractLockRenderer lockRenderer = findRenderer(state);
            if (lockRenderer == null) {
                continue;
            }

            Vec3 renderCenter = lockRenderer.getRenderCenter(level, lock, state);
            double dx = renderCenter.x - cameraPosition.x;
            double dy = renderCenter.y - cameraPosition.y;
            double dz = renderCenter.z - cameraPosition.z;
            if (dx * dx + dy * dy + dz * dz <= MAX_RENDER_DISTANCE_SQR) {
                renderLock(minecraft, level, poseStack, partialTick, cameraPosition,
                        lock, state, lockRenderer, renderCenter, bufferSource);
            }
        }
    }

    private static void renderLock(Minecraft minecraft, ClientLevel level, PoseStack poseStack,
                                   float partialTick, Vec3 cameraPosition, LockManager.LockRecord lock,
                                   BlockState state, AbstractLockRenderer lockRenderer,
                                   Vec3 renderCenter, MultiBufferSource.BufferSource bufferSource) {
        BlockPos pos = lock.pos();
        ItemStack lockStack = new ItemStack(LockData.lockItem(lock.variant()));

        float shakeAngle = getShakeAngle(level, pos, partialTick);
        poseStack.pushPose();
        // Put the item on the container's front face, then let the selected
        // strategy apply any container-specific offset or matrix change.
        lockRenderer.applyContainerTransform(
                poseStack, level, cameraPosition, renderCenter, lock, state);
        poseStack.translate(0.0F, 0.08, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(shakeAngle));
        poseStack.translate(0.0F, -0.08, 0.0F);
        poseStack.scale(0.5F, 0.5F, 0.5F);

        int light = LevelRenderer.getLightColor(level, pos);
        minecraft.getItemRenderer().renderStatic(
                lockStack,
                ItemDisplayContext.FIXED,
                light,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                level,
                pos.hashCode()
        );
        poseStack.popPose();
    }

    private static AbstractLockRenderer findRenderer(BlockState state) {
        for (AbstractLockRenderer renderer : CONTAINER_RENDERERS) {
            if (renderer.supports(state)) {
                return renderer;
            }
        }
        return null;
    }

    private static float getShakeAngle(ClientLevel level, BlockPos pos, float partialTick) {
        ShakeState shake = SHAKES.get(pos);
        if (shake == null) {
            return 0.0F;
        }

        float progress = (level.getGameTime() - shake.startTick + partialTick) / SHAKE_DURATION_TICKS;
        if (progress >= 1.0F) {
            SHAKES.remove(pos);
            return 0.0F;
        }

        // Two quadratic Bezier segments create a short left-right sway and
        // bring the icon back to its original rotation at the end.
        if (progress < 0.5F) {
            return quadraticBezier(0.0F, shake.direction * SHAKE_ANGLE, 0.0F, progress * 2.0F);
        }
        return quadraticBezier(0.0F, -shake.direction * SHAKE_ANGLE, 0.0F, (progress - 0.5F) * 2.0F);
    }

    private static float quadraticBezier(float start, float control, float end, float progress) {
        float inverse = 1.0F - progress;
        return inverse * inverse * start
                + 2.0F * inverse * progress * control
                + progress * progress * end;
    }

    private record ShakeState(long startTick, float direction) {
    }
}

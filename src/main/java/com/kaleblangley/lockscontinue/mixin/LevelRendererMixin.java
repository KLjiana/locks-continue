package com.kaleblangley.lockscontinue.mixin;

import com.kaleblangley.lockscontinue.client.LockLevelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the lock pass to the end of the vanilla level render. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;"
                    + "Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;"
                    + "Lorg/joml/Matrix4f;)V",
            at = @At("TAIL")
    )
    private void lockscontinue$renderLocks(PoseStack poseStack, float partialTick, long finishNanoTime,
                                           boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                           LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        LockLevelRenderer.render(poseStack, partialTick, camera, bufferSource);
        bufferSource.endBatch();
    }
}

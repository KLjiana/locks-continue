package com.kaleblangley.lockscontinue.client.game;

import com.mojang.math.Axis;
import com.kaleblangley.lockscontinue.client.LockScreen;
import com.kaleblangley.lockscontinue.item.ModItems;
import com.kaleblangley.lockscontinue.lock.puzzle.LockAction;
import com.kaleblangley.lockscontinue.lock.puzzle.RotaryPuzzle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Angle-and-core lock with a spring-back and failed-turn animation.
 */
public final class RotaryLockGame implements LockGame {
    private static final float LOCKPICK_OFFSET_X = 0.13F;
    private static final float LOCKPICK_OFFSET_Y = 0.10F;
    private static final float LOCKPICK_OFFSET_Z = -0.45F;
    private static final float LOCKPICK_ROTATION_X = -65.3F;
    private static final float LOCKPICK_ROTATION_Y = -59.4F;
    private static final float LOCKPICK_ROTATION_Z = 58.5F;

    private static final float SCREWDRIVER_OFFSET_X = -0.25F;
    private static final float SCREWDRIVER_OFFSET_Y = -0.55F;
    private static final float SCREWDRIVER_OFFSET_Z = -0.67F;
    private static final float SCREWDRIVER_ROTATION_X = 180.0F;
    private static final float SCREWDRIVER_ROTATION_Y = 152.3F;
    private static final float SCREWDRIVER_ROTATION_Z = 180.0F;
    private static final float SCREWDRIVER_ORBIT_X = 54.0F;
    private static final float SCREWDRIVER_ORBIT_Y = -18.0F;

    private final RotaryPuzzle puzzle;
    private final ItemStack lockPickModel;
    private final ItemStack screwdriverModel;
    private final LockActionSender sender;
    private float pickAngle = 270.0F;
    private float coreAngle;
    private int bounceTicks;
    private boolean dragging;
    private boolean awaitingResult;

    public RotaryLockGame(long seed, ItemStack tool, LockActionSender sender) {
        this.puzzle = new RotaryPuzzle(seed);
        this.lockPickModel = tool.copy();
        // This is a render-only stack. The real tool remains in LockScreen's status bar.
        this.screwdriverModel = new ItemStack(ModItems.SCREWDRIVER_RENDER.get());
        this.sender = sender;
    }

    @Override
    public void tick() {
        if (!dragging) {
            coreAngle *= 0.84F;
        }
        if (bounceTicks > 0) {
            bounceTicks--;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                       int left, int top, int width, int height) {
        int centerX = left + width / 2;
        int centerY = top + height / 2;
        float bounce = bounceTicks > 0 ? (float) Math.sin(bounceTicks * 0.75F) * 10.0F : 0.0F;
        // Keep following the cursor even after it leaves the gui.png panel.
        // The angle is still measured from the rotary lock's center.
        if (!awaitingResult) {
            float pointerAngle = pointerAngle(mouseX, mouseY, left, top, width, height);
            if (dragging) {
                updateAngle(mouseX, mouseY, left, top, width, height);
            } else {
                pickAngle = RotaryPuzzle.clampPickAngle(pointerAngle);
            }
        }
        float allowance = puzzle.rotationAllowance(pickAngle);
        if (dragging) {
            coreAngle = Math.min(allowance, coreAngle + 1.5F);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
//        graphics.pose().mulPose(Axis.ZP.rotationDegrees(pickAngle + bounce));
        graphics.blit(LockScreen.LOCKS_TEXTURE, -66, -66, 132, 132,
                14.0F, 44.0F, 22, 22, 70, 70);
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 1.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(coreAngle));
        graphics.blit(LockScreen.LOCKS_TEXTURE, -42, -42, 84, 84,
                0.0F, 45.0F, 14, 14, 70, 70);
        graphics.pose().popPose();

        // Keep the real player-held lockpick visible and attached to pickAngle.
        renderItemModel(graphics, lockPickModel, centerX, centerY, 140.0F,
                pickAngle + bounce, 54.0F, 0.0F,
                LOCKPICK_OFFSET_X, LOCKPICK_OFFSET_Y, LOCKPICK_OFFSET_Z,
                LOCKPICK_ROTATION_X, LOCKPICK_ROTATION_Y, LOCKPICK_ROTATION_Z);

        // The screwdriver is a second visual tool with its own fixed model transform.
        renderItemModel(graphics, screwdriverModel, centerX, centerY, 150.0F,
                coreAngle, SCREWDRIVER_ORBIT_X, SCREWDRIVER_ORBIT_Y,
                SCREWDRIVER_OFFSET_X, SCREWDRIVER_OFFSET_Y, SCREWDRIVER_OFFSET_Z,
                SCREWDRIVER_ROTATION_X, SCREWDRIVER_ROTATION_Y, SCREWDRIVER_ROTATION_Z);
    }

    private static void renderItemModel(GuiGraphics graphics, ItemStack stack,
                                        int centerX, int centerY, float z,
                                        float orbitAngle, float orbitX, float orbitY,
                                        float offsetX, float offsetY, float offsetZ,
                                        float rotationX, float rotationY, float rotationZ) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, z);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(orbitAngle));
        graphics.pose().translate(orbitX, orbitY, 0.0F);
        graphics.pose().mulPoseMatrix(new Matrix4f().scaling(1.0F, -1.0F, 1.0F));
        graphics.pose().scale(160.0F, 160.0F, 160.0F);
        graphics.pose().translate(offsetX, offsetY, offsetZ);
        graphics.pose().mulPose(Axis.XP.rotationDegrees(rotationX));
        graphics.pose().mulPose(Axis.YP.rotationDegrees(rotationY));
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationZ));

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                graphics.pose(),
                graphics.bufferSource(),
                minecraft.level,
                0
        );
        graphics.flush();
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int left, int top, int width, int height) {
        if (awaitingResult || button != 0) {
            return false;
        }
        if (!insideGameArea(mouseX, mouseY, left, top, width, height)) {
            return false;
        }
        dragging = true;
        pickAngle = RotaryPuzzle.clampPickAngle(pointerAngle(mouseX, mouseY, left, top, width, height));
        updateAngle(mouseX, mouseY, left, top, width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button,
                                 int left, int top, int width, int height) {
        if (!dragging || button != 0) {
            return false;
        }
        updateAngle(mouseX, mouseY, left, top, width, height);
        dragging = false;
        coreAngle = puzzle.rotationAllowance(pickAngle);
        sender.send(LockAction.rotaryAttempt(pickAngle));
        if (puzzle.isWithinTolerance(pickAngle)) {
            awaitingResult = true;
        } else {
            bounceTicks = 10;
        }
        return true;
    }

    private void updateAngle(double mouseX, double mouseY, int left, int top, int width, int height) {
        float next = pointerAngle(mouseX, mouseY, left, top, width, height);
        pickAngle = RotaryPuzzle.clampPickAngle(next);
    }

    private static float pointerAngle(double mouseX, double mouseY, int left, int top, int width, int height) {
        float centerX = left + width / 2.0F;
        float centerY = top + height / 2.0F;
        return (float) Math.toDegrees(Math.atan2(mouseY - centerY, mouseX - centerX));
    }

    private static boolean insideGameArea(double mouseX, double mouseY,
                                          int left, int top, int width, int height) {
        return mouseX >= left && mouseX <= left + width
                && mouseY >= top && mouseY <= top + height;
    }

    @Override
    public float progress() {
        return Math.min(1.0F, coreAngle / 100.0F);
    }

    @Override
    public Component status() {
        return Component.translatable("lock.lockscontinue.rotary_status");
    }

    @Override
    public void renderStatus(GuiGraphics graphics, Font font, int centerX, int centerY) {
        // Rotary lock values are represented by the lock and tool animation.
    }

    @Override
    public void onServerFailure() {
        awaitingResult = false;
        dragging = false;
        bounceTicks = 10;
        coreAngle = 0.0F;
    }
}

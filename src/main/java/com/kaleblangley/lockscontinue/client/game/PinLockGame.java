package com.kaleblangley.lockscontinue.client.game;

import com.kaleblangley.lockscontinue.client.LockScreen;
import com.kaleblangley.lockscontinue.client.LockSoundHelper;
import com.kaleblangley.lockscontinue.lock.puzzle.LockAction;
import com.kaleblangley.lockscontinue.lock.puzzle.PinPuzzle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Five-tumbler timing game modelled after Oblivion's manual lockpicking. */
public final class PinLockGame implements LockGame {
    private static final int LOGICAL_WIDTH = 70;
    private static final int LOGICAL_HEIGHT = 50;
    private static final int PIN_COUNT = PinPuzzle.PIN_COUNT;
    private static final int FIRST_SLOT_X = 17;
    private static final int SLOT_SPACING = 9;
    private static final int PICK_WIDTH = 70;
    private static final int PICK_CONTACT_OFFSET_X = 69;
    private static final float PICK_STEP_X = 2.25F;
    private static final float PICK_LIFT_PIXELS = 2.0F;
    private static final float PICK_HORIZONTAL_RESPONSE = 0.78F;
    private static final float PICK_INPUT_NUDGE = 0.35F;
    private static final float PICK_BOUNCE_DURATION_TICKS = 4.0F;
    private static final float PIN_RANGE_HALF_WIDTH = 3.0F;
    private final PinPuzzle puzzle;
    private final LockActionSender sender;
    private float pickX = slotX(0);
    private float previousPickX = pickX;
    private float pickTargetX = pickX;
    private float pickBounceTicks = PICK_BOUNCE_DURATION_TICKS;
    private int pendingPin = -1;
    private long clientTick;
    private boolean awaitingResult;

    public PinLockGame(long seed, LockActionSender sender) {
        this.puzzle = new PinPuzzle(seed);
        this.sender = sender;
    }

    @Override
    public void tick() {
        previousPickX = pickX;
        float distance = pickTargetX - pickX;
        if (Math.abs(distance) < 0.001F) {
            pickX = pickTargetX;
        } else {
            pickX += distance * PICK_HORIZONTAL_RESPONSE;
        }
        if (pickBounceTicks < PICK_BOUNCE_DURATION_TICKS) {
            pickBounceTicks = Math.min(PICK_BOUNCE_DURATION_TICKS, pickBounceTicks + 1.0F);
        }
        clientTick++;
        puzzle.finishExpiredLift(clientTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                       int left, int top, int width, int height) {
        float scale = scale(width, height);
        int originX = originX(left, width, scale);
        int originY = originY(top, height, scale);

        graphics.pose().pushPose();
        graphics.pose().translate(originX, originY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(LockScreen.LOCKS_TEXTURE, 8, 2, 54, 35,
                0.0F, 0.0F, 54, 35, 70, 70);

        double renderTick = clientTick + partialTick;
        for (int i = 0; i < PIN_COUNT; i++) {
            int x = slotX(i);
            float currentY = pinY(puzzle.position(i, renderTick));
            graphics.blit(LockScreen.LOCKS_TEXTURE, x - 3, 5, 6,
                    Math.max(1, Math.round(currentY)),
                    55.0F, 0.0F, 6, 18, 70, 70);
            if (puzzle.isLocked(i)) {
                graphics.setColor(0.55F, 0.55F, 0.55F, 1.0F);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, currentY, 0.0F);
            graphics.blit(LockScreen.LOCKS_TEXTURE, x - 3, 0, 6, 10,
                    55.0F, 18.0F, 6, 10, 70, 70);
            graphics.pose().popPose();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        float pickPosition = pickBouncePosition(pickBounceTicks + partialTick);
        float renderPickX = pickX + (previousPickX - pickX) * (1.0F - partialTick);
        float pickY = pickY(pickPosition);
        graphics.pose().pushPose();
        graphics.pose().translate(renderPickX - PICK_CONTACT_OFFSET_X, pickY, 0.0F);
        graphics.blit(LockScreen.LOCKS_TEXTURE, 0, 0, PICK_WIDTH, 8,
                0.0F, 35.0F, PICK_WIDTH, 8, 70, 70);
        graphics.pose().popPose();
//        int pinUnderPick = pinUnderPick();
//        int pickCenterX = Math.round(pickX);
//        int selectionColor = pinUnderPick >= 0 && puzzle.isLocked(pinUnderPick)
//                ? 0xAA6DE39A : puzzle.activePin() == pinUnderPick
//                ? 0xAAE8C56A : 0xAA72D6FF;
//        graphics.fill(pickCenterX - 4, pickY - 3, pickCenterX + 4, pickY - 2, selectionColor);
//        graphics.fill(pickCenterX - 4, pickY + 8, pickCenterX + 4, pickY + 9, selectionColor);
        graphics.pose().popPose();

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int left, int top, int width, int height) {
        if (awaitingResult || (button != 0 && button != 1)) {
            return false;
        }

        float scale = scale(width, height);
        float logicalX = (float) ((mouseX - originX(left, width, scale)) / scale);
        float logicalY = (float) ((mouseY - originY(top, height, scale)) / scale);
        if (logicalX < 5.0F || logicalX >= 67.0F || logicalY < 4.0F || logicalY >= 34.0F) {
            return false;
        }

        if (logicalX < FIRST_SLOT_X - PIN_RANGE_HALF_WIDTH
                || logicalX > slotX(PIN_COUNT - 1) + PIN_RANGE_HALF_WIDTH) {
            return false;
        }

        if (!awaitingResult && !puzzle.isLiftActive(clientTick)) {
            pickTargetX = clampPickX(logicalX);
        }
        if (button == 0) {
            beginLift();
        } else {
            attemptSet();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button,
                                 int left, int top, int width, int height) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (awaitingResult) {
            return false;
        }

        if ((keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_LEFT)
                && !puzzle.isLiftActive(clientTick)) {
            movePickX(-PICK_STEP_X);
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_D || keyCode == GLFW.GLFW_KEY_RIGHT)
                && !puzzle.isLiftActive(clientTick)) {
            movePickX(PICK_STEP_X);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_W || keyCode == GLFW.GLFW_KEY_UP) {
            beginLift();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_S || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            attemptSet();
            return true;
        }
        return false;
    }

    @Override
    public float progress() {
        return puzzle.lockedCount() / (float) PIN_COUNT;
    }

    @Override
    public Component status() {
        return Component.translatable("lock.lockscontinue.pin_status", Math.round(progress() * PIN_COUNT), PIN_COUNT);
    }

    @Override
    public void renderStatus(GuiGraphics graphics, Font font, int centerX, int centerY) {
        Component message = awaitingResult
                ? Component.translatable("lock.lockscontinue.waiting")
                : puzzle.isLiftActive(clientTick)
                ? Component.translatable("lock.lockscontinue.pin_timing")
                : puzzle.lockedCount() == 0
                ? Component.translatable("lock.lockscontinue.pin_controls") : status();
        graphics.drawCenteredString(font, message, centerX,
                centerY - font.lineHeight / 2, 0xFFE8E8E8);
    }

    @Override
    public void onServerProgress() {
        if (pendingPin >= 0) {
            puzzle.confirmSet(pendingPin);
            LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.PIN_FINISH.get(),
                    0.8F, 1.0F);
        }
        pickBounceTicks = PICK_BOUNCE_DURATION_TICKS;
        pendingPin = -1;
        awaitingResult = false;
    }

    @Override
    public void onServerFailure() {
        awaitingResult = false;
        pendingPin = -1;
        pickBounceTicks = PICK_BOUNCE_DURATION_TICKS;
        puzzle.resetAfterFailure();
        LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.PIN_FAIL.get(), 0.7F, 1.0F);
    }

    private static float scale(int width, int height) {
        return Math.max(1.0F, Math.min((width - 12.0F) / LOGICAL_WIDTH,
                (height - 12.0F) / LOGICAL_HEIGHT));
    }

    private static int originX(int left, int width, float scale) {
        return left + Math.round((width - LOGICAL_WIDTH * scale) / 2.0F);
    }

    private static int originY(int top, int height, float scale) {
        return top + Math.round((height - LOGICAL_HEIGHT * scale) / 2.0F);
    }

    private void beginLift() {
        int pin = pinUnderPick();
        if (awaitingResult || puzzle.isLiftActive(clientTick)
                || pin < 0 || puzzle.isLocked(pin)) {
            return;
        }

        if (puzzle.beginLift(pin, clientTick)) {
            pickBounceTicks = 0.0F;
            LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.PIN_FAIL.get(),
                    0.7F, 1.0F);
            sender.send(LockAction.pinLift(pin));
        }
    }

    private void attemptSet() {
        if (awaitingResult || puzzle.activePin() < 0
                || puzzle.activePin() != pinUnderPick()
                || !puzzle.isLiftActive(clientTick)) {
            return;
        }

        pendingPin = puzzle.activePin();
        awaitingResult = true;
        sender.send(LockAction.pinSet(pendingPin));
    }

    private void movePickX(float delta) {
        pickTargetX = clampPickX(pickTargetX + delta);
        pickX += (pickTargetX - pickX) * PICK_INPUT_NUDGE;
    }

    private int pinUnderPick() {
        for (int i = 0; i < PIN_COUNT; i++) {
            if (Math.abs(pickX - slotX(i)) <= PIN_RANGE_HALF_WIDTH) {
                return i;
            }
        }
        return -1;
    }

    private static float clampPickX(float value) {
        float min = slotX(0) - PIN_RANGE_HALF_WIDTH;
        float max = slotX(PIN_COUNT - 1) + PIN_RANGE_HALF_WIDTH;
        return Math.max(min, Math.min(max, value));
    }

    private static float pickY(float position) {
        return 25.0F - PICK_LIFT_PIXELS * position;
    }

    private static float pickBouncePosition(float elapsedTicks) {
        if (elapsedTicks <= 0.0F || elapsedTicks >= PICK_BOUNCE_DURATION_TICKS) {
            return 0.0F;
        }
        return (float) Math.sin(Math.PI * elapsedTicks / PICK_BOUNCE_DURATION_TICKS);
    }

    private static float pinY(float position) {
        return 16.0F - 11.0F * position;
    }

    private static int slotX(int index) {
        return FIRST_SLOT_X + index * SLOT_SPACING;
    }
}

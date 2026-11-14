package com.kaleblangley.lockscontinue.client.game;

import com.kaleblangley.lockscontinue.client.LockScreen;
import com.kaleblangley.lockscontinue.client.LockSoundHelper;
import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;
import com.kaleblangley.lockscontinue.lock.puzzle.LockAction;
import com.kaleblangley.lockscontinue.lock.puzzle.PinPuzzle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Tumbler timing game modelled after Oblivion's manual lockpicking. */
public final class PinLockGame implements LockGame {
    private static final int LOGICAL_HEIGHT = 50;
    private static final int BODY_X = 8;
    private static final int BODY_Y = 2;
    private static final int BODY_HEIGHT = 35;
    private static final int BODY_LEFT_WIDTH = 13;
    private static final int BODY_SLOT_SOURCE_X = 13;
    private static final int BODY_SLOT_WIDTH = 9;
    private static final int BODY_RIGHT_SOURCE_X = BODY_SLOT_SOURCE_X + BODY_SLOT_WIDTH;
    private static final int BODY_RIGHT_WIDTH = 32;
    private static final int FIRST_SLOT_X = 17;
    private static final int PICK_WIDTH = 70;
    private static final int PICK_CONTACT_OFFSET_X = 69;
    private static final float PICK_STEP_X = 1.25F * PinPuzzle.ANIMATION_SPEED;
    private static final float PICK_LIFT_PIXELS = 2.0F;
    private static final float PICK_HORIZONTAL_RESPONSE = 0.78F;
    private static final float PICK_INPUT_NUDGE = 0.35F;
    private static final float PICK_HOLD_STEP_X = 1.125F * PinPuzzle.ANIMATION_SPEED;
    private static final float PICK_BOUNCE_DURATION_TICKS = 4.0F / PinPuzzle.ANIMATION_SPEED;
    private static final float PIN_RANGE_HALF_WIDTH = 3.0F;
    private final PinPuzzle puzzle;
    private final LockActionSender sender;
    private final float horizontalResponse;
    private final float inputNudge;
    private final float soundVolumeMultiplier;
    private final int insight;
    private final int bodyWidth;
    private final int logicalWidth;
    private float pickX;
    private float previousPickX;
    private float pickTargetX;
    private float pickBounceTicks = PICK_BOUNCE_DURATION_TICKS;
    private int pendingPin = -1;
    private long clientTick;
    private boolean awaitingResult;
    private boolean speedHintShown;
    private boolean leftHeld;
    private boolean rightHeld;
    private boolean liftKeyHeld;
    private boolean setKeyHeld;

    public PinLockGame(long seed, LockActionSender sender) {
        this(seed, null, sender);
    }

    public PinLockGame(long seed, LockChallengeSnapshot challenge, LockActionSender sender) {
        this.puzzle = new PinPuzzle(seed, challenge);
        this.sender = sender;
        int speed = challenge == null ? 0 : challenge.toolStats().speed();
        this.horizontalResponse = animationResponse(
                Math.min(0.96F, PICK_HORIZONTAL_RESPONSE + speed * 0.04F));
        this.inputNudge = animationResponse(Math.min(1.0F, PICK_INPUT_NUDGE + speed * 0.1F));
        this.soundVolumeMultiplier = challenge != null && challenge.hasFlag("sound_reduced")
                ? 0.35F : 1.0F;
        this.insight = challenge == null ? 0 : challenge.toolStats().insight();
        this.bodyWidth = BODY_LEFT_WIDTH
                + middleSlotCount() * BODY_SLOT_WIDTH + rightSectionWidth();
        this.logicalWidth = BODY_X + bodyWidth + 8;
        this.pickX = slotX(0);
        this.previousPickX = pickX;
        this.pickTargetX = pickX;
    }

    @Override
    public void tick() {
        if (!awaitingResult) {
            float direction = (leftHeld ? -1.0F : 0.0F)
                    + (rightHeld ? 1.0F : 0.0F);
            if (direction != 0.0F) {
                movePickX(direction * PICK_HOLD_STEP_X);
            }
        }
        previousPickX = pickX;
        float distance = pickTargetX - pickX;
        if (Math.abs(distance) < 0.001F) {
            pickX = pickTargetX;
        } else {
            pickX += distance * horizontalResponse;
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
        float scale = scale(width, height, logicalWidth);
        int originX = originX(left, width, scale, logicalWidth);
        int originY = originY(top, height, scale);

        graphics.pose().pushPose();
        graphics.pose().translate(originX, originY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        renderLockBody(graphics);

        double renderTick = clientTick + partialTick;
        for (int i = 0; i < puzzle.pinCount(); i++) {
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
        // Pin Lock is keyboard-only: A/D move, W lifts and S/Space sets.
        return false;
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

        if (keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_LEFT) {
            if (!leftHeld) {
                movePickX(-PICK_STEP_X);
            }
            leftHeld = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_D || keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (!rightHeld) {
                movePickX(PICK_STEP_X);
            }
            rightHeld = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_W || keyCode == GLFW.GLFW_KEY_UP) {
            if (!liftKeyHeld) {
                beginLift();
            }
            liftKeyHeld = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_S || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            if (!setKeyHeld) {
                attemptSet();
            }
            setKeyHeld = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_LEFT) {
            leftHeld = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_D || keyCode == GLFW.GLFW_KEY_RIGHT) {
            rightHeld = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_W || keyCode == GLFW.GLFW_KEY_UP) {
            liftKeyHeld = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_S || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            setKeyHeld = false;
            return true;
        }
        return false;
    }

    @Override
    public float progress() {
        return puzzle.lockedCount() / (float) puzzle.pinCount();
    }

    @Override
    public Component status() {
        return Component.translatable("lock.lockscontinue.pin_status",
                puzzle.lockedCount(), puzzle.pinCount());
    }

    @Override
    public void renderStatus(GuiGraphics graphics, Font font, int centerX, int centerY) {
        Component message = awaitingResult
                ? Component.translatable("lock.lockscontinue.waiting")
                : speedHintShown && puzzle.isLiftActive(clientTick)
                ? Component.translatable("lock.lockscontinue.pin_speed_hint",
                String.format(Locale.ROOT, "%.1f", puzzle.activeRiseTicks()),
                String.format(Locale.ROOT, "%.1f", puzzle.activeHoldTicks()))
                : puzzle.isLiftActive(clientTick)
                ? Component.translatable("lock.lockscontinue.pin_timing")
                : puzzle.lockedCount() == 0
                ? Component.translatable("lock.lockscontinue.pin_controls") : status();
        graphics.drawCenteredString(font, message, centerX,
                centerY - font.lineHeight, 0xFFE8E8E8);
    }

    @Override
    public void onServerProgress() {
        if (pendingPin >= 0) {
            puzzle.confirmSet(pendingPin);
            LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.PIN_FINISH.get(),
                    soundVolumeMultiplier, 1.0F);
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
        LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.PIN_FAIL.get(),
                soundVolumeMultiplier, 1.0F);
    }

    @Override
    public void onServerRejected() {
        awaitingResult = false;
        pendingPin = -1;
        puzzle.rejectLift();
        pickBounceTicks = PICK_BOUNCE_DURATION_TICKS;
    }

    private static float scale(int width, int height, int logicalWidth) {
        return Math.max(1.0F, Math.min((width - 12.0F) / logicalWidth,
                (height - 12.0F) / LOGICAL_HEIGHT));
    }

    private static int originX(int left, int width, float scale, int logicalWidth) {
        return left + Math.round((width - logicalWidth * scale) / 2.0F);
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
            if (insight > 0) {
                speedHintShown = true;
            }
            LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.PIN_FAIL.get(),
                    soundVolumeMultiplier, 1.0F);
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
        pickX += (pickTargetX - pickX) * inputNudge;
    }

    /** Converts a per-tick easing response to the faster animation clock. */
    private static float animationResponse(float response) {
        return 1.0F - (float) Math.pow(1.0F - response, PinPuzzle.ANIMATION_SPEED);
    }

    private int pinUnderPick() {
        for (int i = 0; i < puzzle.pinCount(); i++) {
            if (Math.abs(pickX - slotX(i)) <= PIN_RANGE_HALF_WIDTH) {
                return i;
            }
        }
        return -1;
    }

    private float clampPickX(float value) {
        float min = slotX(0) - PIN_RANGE_HALF_WIDTH;
        float max = slotX(puzzle.pinCount() - 1) + PIN_RANGE_HALF_WIDTH;
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

    private int slotX(int index) {
        // The source slot at x=13 is repeated in the middle of the body, so
        // every tumbler remains aligned to the original 9px pixel grid.
        return FIRST_SLOT_X + index * BODY_SLOT_WIDTH;
    }

    private void renderLockBody(GuiGraphics graphics) {
        graphics.blit(LockScreen.LOCKS_TEXTURE, BODY_X, BODY_Y,
                BODY_LEFT_WIDTH, BODY_HEIGHT,
                0.0F, 0.0F, BODY_LEFT_WIDTH, BODY_HEIGHT, 70, 70);

        int slotCount = middleSlotCount();
        for (int slot = 0; slot < slotCount; slot++) {
            graphics.blit(LockScreen.LOCKS_TEXTURE,
                    BODY_X + BODY_LEFT_WIDTH + slot * BODY_SLOT_WIDTH, BODY_Y,
                    BODY_SLOT_WIDTH, BODY_HEIGHT,
                    BODY_SLOT_SOURCE_X, 0.0F, BODY_SLOT_WIDTH, BODY_HEIGHT, 70, 70);
        }

        graphics.blit(LockScreen.LOCKS_TEXTURE,
                BODY_X + BODY_LEFT_WIDTH + slotCount * BODY_SLOT_WIDTH, BODY_Y,
                rightSectionWidth(), BODY_HEIGHT,
                BODY_RIGHT_SOURCE_X, 0.0F, rightSectionWidth(), BODY_HEIGHT, 70, 70);
    }

    /** Number of repeatable middle slots needed after the four-slot right slice. */
    private int middleSlotCount() {
        return Math.max(0, puzzle.pinCount() - 4);
    }

    /** Crops the right slice for a three-pin data-pack configuration. */
    private int rightSectionWidth() {
        return Math.max(1, BODY_RIGHT_WIDTH
                - Math.max(0, 4 - puzzle.pinCount()) * BODY_SLOT_WIDTH);
    }
}

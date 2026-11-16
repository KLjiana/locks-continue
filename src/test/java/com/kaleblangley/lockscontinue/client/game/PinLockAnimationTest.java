package com.kaleblangley.lockscontinue.client.game;

import com.kaleblangley.lockscontinue.lock.puzzle.PinPuzzle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PinLockAnimationTest {
    private static final double BOUNCE_TICKS = 4.0D / PinPuzzle.ANIMATION_SPEED;
    private static final double TRANSITION_TICKS = 2.0D / PinPuzzle.ANIMATION_SPEED;

    @Test
    void inputChangesTheTargetWithoutJumpingPastTheInterpolationStart() {
        PinLockAnimation animation = animation();
        animation.movePick(9.0F, 14.0F, 56.0F);
        assertEquals(17.0F, animation.pickPosition(1.0F));
        animation.tick();
        assertEquals(17.0F, animation.pickPosition(0.0F));
        float end = animation.pickPosition(1.0F);
        assertTrue(end > 17.0F && end < 26.0F);
        assertEquals((17.0F + end) / 2.0F, animation.pickPosition(0.5F), 1.0E-6F);
    }

    @Test
    void holdingAndReversingDirectionAreContinuousAtEveryTickBoundary() {
        PinLockAnimation animation = animation();
        float end = animation.pickPosition(1.0F);
        for (int tick = 0; tick < 32; tick++) {
            float direction = tick < 16 ? 1.0F : -1.0F;
            animation.movePick(direction * 1.125F * PinPuzzle.ANIMATION_SPEED, 14.0F, 56.0F);
            animation.tick();
            assertEquals(end, animation.pickPosition(0.0F), 1.0E-6F,
                    "The previous tick's remaining motion must not be skipped");
            float nextEnd = animation.pickPosition(1.0F);
            for (int frame = 0; frame <= 8; frame++) {
                float x = animation.pickPosition(frame / 8.0F);
                assertTrue(x >= Math.min(end, nextEnd) - 1.0E-6F);
                assertTrue(x <= Math.max(end, nextEnd) + 1.0E-6F);
            }
            end = nextEnd;
        }
    }

    @Test
    void repeatedInputBetweenTicksDoesNotDisturbTheCurrentFrame() {
        PinLockAnimation animation = animation();
        animation.movePick(9.0F, 14.0F, 56.0F);
        animation.tick();
        float currentFrame = animation.pickPosition(0.4F);
        animation.movePick(5.0F, 14.0F, 56.0F);
        animation.movePick(-2.0F, 14.0F, 56.0F);
        assertEquals(currentFrame, animation.pickPosition(0.4F));
        assertEquals(currentFrame, animation.renderedPickX());
    }

    @Test
    void pinSelectionUsesThePositionActuallyDrawnOnScreen() {
        PinLockAnimation animation = animation();
        animation.movePick(9.0F, 14.0F, 56.0F);
        animation.tick();
        float end = animation.pickPosition(1.0F);
        float visible = animation.pickPosition(0.25F);
        assertTrue(visible < end);
        assertEquals(visible, animation.renderedPickX());
        animation.tick();
        assertEquals(visible, animation.renderedPickX(), "Logic ticks must not move the displayed selection");
    }

    @Test
    void failedPinsFallSmoothlyFromTheirLastVisiblePositions() {
        PinLockAnimation animation = animation();
        animation.beginFrame(10.0D);
        assertEquals(1.0F, animation.pinPosition(0, 1.0F));
        assertEquals(1.0F, animation.pinPosition(1, 1.0F));
        animation.transitionPins();
        assertEquals(1.0F, animation.pinPosition(0, 0.0F));
        animation.beginFrame(10.0D + TRANSITION_TICKS / 2.0D);
        assertEquals(0.5F, animation.pinPosition(0, 0.0F), 1.0E-6F);
        assertEquals(1.0F, animation.pinPosition(1, 1.0F));
        animation.beginFrame(10.0D + TRANSITION_TICKS + 1.0E-6D);
        assertEquals(0.0F, animation.pinPosition(0, 0.0F));
    }

    @Test
    void anInterruptedCorrectionRestartsFromTheVisibleHeight() {
        PinLockAnimation animation = animation();
        animation.beginFrame(10.0D);
        animation.pinPosition(0, 1.0F);
        animation.transitionPins();
        animation.beginFrame(10.0D + TRANSITION_TICKS / 2.0D);
        float interrupted = animation.pinPosition(0, 0.0F);
        animation.transitionPins();
        assertEquals(interrupted, animation.pinPosition(0, 1.0F));
        animation.beginFrame(10.0D + TRANSITION_TICKS);
        assertEquals(0.75F, animation.pinPosition(0, 1.0F), 1.0E-6F);
    }

    @Test
    void liftStartsWithoutAFrameJumpAndMeetsTheUnchangedPuzzleApex() {
        PinLockAnimation animation = animation();
        PinPuzzle puzzle = new PinPuzzle(0L);
        puzzle.beginLift(0, 0L);
        animation.beginFrame(0.75D);
        animation.transitionPin(0, Math.min(TRANSITION_TICKS,
                puzzle.activeRiseTicks() - animation.renderTime()));
        assertTrue(puzzle.position(0, 0.75D) > 0.0F);
        assertEquals(0.0F, animation.pinPosition(0, puzzle.position(0, 0.75D)));
        animation.beginFrame(puzzle.activeRiseTicks());
        assertEquals(1.0F, animation.pinPosition(0, puzzle.position(0, puzzle.activeRiseTicks())));
    }

    @Test
    void waitingForAReplyHoldsTheLastVisibleHeightDuringAnEarlyLiftBlend() {
        PinLockAnimation animation = animation();
        animation.transitionPin(0, TRANSITION_TICKS);
        animation.beginFrame(TRANSITION_TICKS / 2.0D);
        float pendingHeight = animation.pinPosition(0, 1.0F);
        animation.transitionPin(0, 0.0D);
        assertEquals(pendingHeight, animation.pinPosition(0, pendingHeight));
        animation.beginFrame(TRANSITION_TICKS * 4.0D);
        assertEquals(pendingHeight, animation.pinPosition(0, pendingHeight));
    }

    @Test
    void bounceStartsAtTheEventTimeAndIsNotCutOffByAReply() {
        PinLockAnimation animation = animation();
        animation.beginFrame(0.75D);
        animation.startBounce();
        assertEquals(0.0F, animation.bouncePosition());
        animation.beginFrame(0.75D + BOUNCE_TICKS * 0.001D);
        assertTrue(animation.bouncePosition() < 0.00002F, "Bounce must ease in with zero starting velocity");
        animation.beginFrame(0.75D + BOUNCE_TICKS / 2.0D);
        assertEquals(1.0F, animation.bouncePosition(), 1.0E-6F);
        animation.transitionPins();
        assertEquals(1.0F, animation.bouncePosition(), 1.0E-6F);
        animation.beginFrame(0.75D + BOUNCE_TICKS);
        assertEquals(0.0F, animation.bouncePosition());
    }

    private static PinLockAnimation animation() {
        return new PinLockAnimation(5, 17.0F, 0.8F, 0.35F, BOUNCE_TICKS, TRANSITION_TICKS);
    }
}

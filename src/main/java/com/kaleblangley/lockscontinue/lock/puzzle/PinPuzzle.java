package com.kaleblangley.lockscontinue.lock.puzzle;

import java.util.Arrays;
import java.util.Random;

/**
 * Oblivion-style tumbler puzzle. Each lift gets a deterministic random speed,
 * while the server independently checks whether the set input reached the top.
 */
public final class PinPuzzle implements LockPuzzle {
    public static final int PIN_COUNT = 5;
    private static final int SET_TOLERANCE_TICKS = 2;

    private final long seed;
    private final boolean[] locked = new boolean[PIN_COUNT];
    private int liftSequence;
    private int activePin = -1;
    private long activeStartTick;
    private LiftProfile activeProfile;

    public PinPuzzle(long seed) {
        this.seed = seed;
    }

    public boolean isLocked(int index) {
        return index >= 0 && index < PIN_COUNT && locked[index];
    }

    public int lockedCount() {
        int count = 0;
        for (boolean value : locked) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    public int activePin() {
        return activePin;
    }

    /** Starts one randomized rise/pause/fall cycle. */
    public boolean beginLift(int index, long gameTime) {
        if (index < 0 || index >= PIN_COUNT || locked[index]) {
            return false;
        }

        Random random = new Random(seed
                ^ 0x50494E4C4F434BL
                ^ ((long) index * 0x9E3779B97F4A7C15L)
                ^ ((long) liftSequence++ * 0xD1B54A32D192ED03L));
        int speedTier = random.nextInt(5);
        int riseTicks = switch (speedTier) {
            case 0 -> 5;
            case 1 -> 7;
            case 2 -> 9;
            case 3 -> 12;
            default -> 15;
        };
        int holdTicks = 1 + random.nextInt(speedTier >= 3 ? 3 : 2);
        int fallTicks = Math.max(5, riseTicks - 2 + random.nextInt(5));

        activePin = index;
        activeStartTick = gameTime;
        activeProfile = new LiftProfile(riseTicks, holdTicks, fallTicks);
        return true;
    }

    /** Returns a smooth 0..1 vertical position for rendering the active tumbler. */
    public float position(int index, double gameTime) {
        if (isLocked(index)) {
            return 1.0F;
        }
        if (index != activePin || activeProfile == null) {
            return 0.0F;
        }

        double elapsed = Math.max(0.0D, gameTime - activeStartTick);
        if (elapsed < activeProfile.riseTicks()) {
            double t = elapsed / activeProfile.riseTicks();
            return (float) (1.0D - (1.0D - t) * (1.0D - t));
        }
        if (elapsed < activeProfile.riseTicks() + activeProfile.holdTicks()) {
            return 1.0F;
        }

        double falling = elapsed - activeProfile.riseTicks() - activeProfile.holdTicks();
        if (falling < activeProfile.fallTicks()) {
            double t = falling / activeProfile.fallTicks();
            return (float) Math.max(0.0D, 1.0D - t * t);
        }
        return 0.0F;
    }

    public boolean isLiftActive(long gameTime) {
        return activePin >= 0 && activeProfile != null
                && gameTime - activeStartTick < activeProfile.totalTicks();
    }

    public void finishExpiredLift(long gameTime) {
        if (activePin >= 0 && !isLiftActive(gameTime)) {
            clearLift();
        }
    }

    /** Applies an accepted non-final server result on the client. */
    public void confirmSet(int index) {
        if (index >= 0 && index < PIN_COUNT) {
            locked[index] = true;
        }
        clearLift();
    }

    /** A failed timing attempt drops every previously set tumbler. */
    public void resetAfterFailure() {
        Arrays.fill(locked, false);
        clearLift();
    }

    public boolean isSolved() {
        return lockedCount() == PIN_COUNT;
    }

    @Override
    public PuzzleActionResult apply(LockAction action) {
        return apply(action, 0L);
    }

    @Override
    public PuzzleActionResult apply(LockAction action, long gameTime) {
        if (action.type() == LockActionType.PIN_LIFT) {
            return beginLift(action.first(), gameTime)
                    ? PuzzleActionResult.UPDATED : PuzzleActionResult.WRONG;
        }
        if (action.type() != LockActionType.PIN_SET
                || action.first() < 0 || action.first() >= PIN_COUNT
                || action.first() != activePin || activeProfile == null) {
            resetAfterFailure();
            return PuzzleActionResult.WRONG;
        }

        long elapsed = gameTime - activeStartTick;
        long windowStart = activeProfile.riseTicks() - SET_TOLERANCE_TICKS;
        long windowEnd = activeProfile.riseTicks()
                + activeProfile.holdTicks() + SET_TOLERANCE_TICKS;
        if (elapsed < windowStart || elapsed > windowEnd) {
            resetAfterFailure();
            return PuzzleActionResult.WRONG;
        }

        locked[action.first()] = true;
        clearLift();
        return isSolved() ? PuzzleActionResult.SOLVED : PuzzleActionResult.UPDATED;
    }

    @Override
    public boolean isComplete() {
        return isSolved();
    }

    private void clearLift() {
        activePin = -1;
        activeProfile = null;
    }

    private record LiftProfile(int riseTicks, int holdTicks, int fallTicks) {
        private int totalTicks() {
            return riseTicks + holdTicks + fallTicks;
        }
    }
}

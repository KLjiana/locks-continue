package com.kaleblangley.lockscontinue.lock.puzzle;

import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;

import java.util.Random;

/**
 * Oblivion-style tumbler puzzle. Every pin receives one deterministic lift
 * profile when the puzzle is created, so retrying a failed lift does not change
 * its timing. The variable-speed affix opts into rerolling the profile on each
 * lift instead.
 */
public final class PinPuzzle implements LockPuzzle {
    public static final int PIN_COUNT = 5;
    public static final int MIN_PIN_COUNT = 3;
    public static final int MAX_PIN_COUNT = 10;
    private static final int SET_TOLERANCE_TICKS = 2;

    private final long seed;
    private final int pinCount;
    private final int setToleranceTicks;
    private final int minHoldTicks;
    private final int resetLockedPins;
    private final float speedVariance;
    private final boolean variableSpeed;
    private final LiftProfile[] fixedProfiles;
    private final boolean[] locked;
    private int liftSequence;
    private int activePin = -1;
    private long activeStartTick;
    private LiftProfile activeProfile;

    public PinPuzzle(long seed) {
        this(seed, null);
    }

    public PinPuzzle(long seed, LockChallengeSnapshot challenge) {
        this.seed = seed;
        this.pinCount = challenge == null ? PIN_COUNT
                : Math.max(MIN_PIN_COUNT, Math.min(MAX_PIN_COUNT, challenge.pinCount()));
        this.locked = new boolean[pinCount];
        this.setToleranceTicks = challenge == null
                ? SET_TOLERANCE_TICKS
                : Math.max(0, challenge.pinToleranceTicks()
                + challenge.toolStats().precision() / 2);
        this.minHoldTicks = challenge == null
                ? 1 : Math.max(0, challenge.pinMinHoldTicks());
        int configuredReset = challenge == null
                ? pinCount : Math.max(0, Math.min(pinCount, challenge.pinResetLockedPins()));
        this.resetLockedPins = challenge == null ? configuredReset
                : Math.max(0, configuredReset - challenge.toolStats().stability() / 2);
        this.speedVariance = challenge == null
                ? 1.0F : Math.max(0.1F, Math.min(10.0F, challenge.pinSpeedVariance()));
        this.variableSpeed = challenge != null && challenge.hasFlag("pin_variable_speed");
        this.fixedProfiles = variableSpeed ? null : new LiftProfile[pinCount];
        if (fixedProfiles != null) {
            for (int index = 0; index < pinCount; index++) {
                fixedProfiles[index] = createLiftProfile(index, 0L);
            }
        }
    }

    public boolean isLocked(int index) {
        return index >= 0 && index < pinCount && locked[index];
    }

    public int pinCount() {
        return pinCount;
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

    public int activeRiseTicks() {
        return activeProfile == null ? 0 : activeProfile.riseTicks();
    }

    public int activeHoldTicks() {
        return activeProfile == null ? 0 : activeProfile.holdTicks();
    }

    /** Starts the pin's fixed cycle, or a fresh cycle when variable speed is enabled. */
    public boolean beginLift(int index, long gameTime) {
        if (index < 0 || index >= pinCount || locked[index]) {
            return false;
        }

        LiftProfile profile = variableSpeed
                ? createLiftProfile(index, liftSequence++)
                : fixedProfiles[index];

        activePin = index;
        activeStartTick = gameTime;
        activeProfile = profile;
        return true;
    }

    private LiftProfile createLiftProfile(int index, long sequence) {
        Random random = new Random(seed
                ^ 0x50494E4C4F434BL
                ^ ((long) index * 0x9E3779B97F4A7C15L)
                ^ (sequence * 0xD1B54A32D192ED03L));
        int speedTier = random.nextInt(5);
        int baseRiseTicks = switch (speedTier) {
            case 0 -> 5;
            case 1 -> 7;
            case 2 -> 9;
            case 3 -> 12;
            default -> 15;
        };
        int varianceTicks = Math.round((speedVariance - 1.0F) * 4.0F);
        int riseTicks = Math.max(3, baseRiseTicks
                + (varianceTicks == 0 ? 0 : random.nextInt(Math.abs(varianceTicks) * 2 + 1)
                - Math.abs(varianceTicks)));
        int holdTicks = minHoldTicks + random.nextInt(speedTier >= 3 ? 3 : 2);
        int fallTicks = Math.max(5, riseTicks - 2 + random.nextInt(5));
        return new LiftProfile(riseTicks, holdTicks, fallTicks);
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
        if (index >= 0 && index < pinCount) {
            locked[index] = true;
        }
        clearLift();
    }

    /** A failed timing attempt drops the configured number of set tumblers. */
    public void resetAfterFailure() {
        int remaining = resetLockedPins;
        for (int index = pinCount - 1; index >= 0 && remaining > 0; index--) {
            if (locked[index]) {
                locked[index] = false;
                remaining--;
            }
        }
        clearLift();
    }

    /** Discards a client-side lift when the authoritative server rejects it. */
    public void rejectLift() {
        clearLift();
    }

    public boolean isSolved() {
        return lockedCount() == pinCount;
    }

    @Override
    public PuzzleActionResult apply(LockAction action) {
        return apply(action, 0L);
    }

    @Override
    public PuzzleActionResult apply(LockAction action, long gameTime) {
        if (action.type() == LockActionType.PIN_LIFT) {
            return beginLift(action.first(), gameTime)
                    ? PuzzleActionResult.UPDATED : PuzzleActionResult.REJECTED;
        }
        if (action.type() != LockActionType.PIN_SET
                || action.first() < 0 || action.first() >= pinCount
                || action.first() != activePin || activeProfile == null) {
            return PuzzleActionResult.REJECTED;
        }

        long elapsed = gameTime - activeStartTick;
        long windowStart = activeProfile.riseTicks() - setToleranceTicks;
        long windowEnd = activeProfile.riseTicks()
                + activeProfile.holdTicks() + setToleranceTicks;
        if (elapsed < windowStart || elapsed > windowEnd) {
            resetAfterFailure();
            return PuzzleActionResult.MISTAKE;
        }

        locked[action.first()] = true;
        clearLift();
        return isSolved() ? PuzzleActionResult.SOLVED : PuzzleActionResult.PROGRESS;
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

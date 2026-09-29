package com.kaleblangley.lockscontinue.lock.puzzle;

import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;

import java.util.Random;

/** Hidden target angle and tolerance for the rotary lock. */
public final class RotaryPuzzle implements LockPuzzle {
    /**
     * The pick can travel across the upper arc of the lock only: 340° -> 220°.
     * In normalized screen coordinates this is the inclusive interval 220°..340°.
     */
    public static final float PICK_ANGLE_MIN = 220.0F;
    public static final float PICK_ANGLE_MAX = 340.0F;
    private static final float PICK_ANGLE_SPAN = PICK_ANGLE_MAX - PICK_ANGLE_MIN;

    private final float targetAngle;
    private final float tolerance;
    private final float feedbackFalloff;
    private final float stressMultiplier;
    private boolean complete;

    public RotaryPuzzle(long seed) {
        this(seed, null);
    }

    public RotaryPuzzle(long seed, LockChallengeSnapshot challenge) {
        Random random = new Random(seed ^ 0x524F544152594BL);
        // Keep every server-generated solution inside the angle range the client can reach.
        targetAngle = PICK_ANGLE_MIN + random.nextFloat() * PICK_ANGLE_SPAN;
        float configuredTolerance = challenge == null
                ? 12.0F + random.nextInt(9)
                : challenge.rotaryToleranceDegrees()
                + challenge.toolStats().precision() * 0.75F;
        tolerance = Math.max(0.0F, Math.min(180.0F, configuredTolerance));
        feedbackFalloff = challenge == null
                ? 1.5F : Math.max(0.01F, Math.min(100.0F, challenge.rotaryFeedbackFalloff()));
        stressMultiplier = challenge == null
                ? 1.0F
                : Math.max(0.1F, challenge.rotaryStressMultiplier()
                * (1.0F - challenge.toolStats().stability() * 0.04F));
    }

    public float tolerance() {
        return tolerance;
    }

    public float feedbackFalloff() {
        return feedbackFalloff;
    }

    public float rotationAllowance(float pickAngle) {
        float difference = angularDistance(pickAngle, targetAngle);
        if (difference <= tolerance) {
            return 100.0F;
        }
        float closeness = Math.max(0.0F, 1.0F - difference / PICK_ANGLE_SPAN);
        // Stress makes the approach less forgiving, but it must never cap the
        // core below 100% when the pick is actually on target.
        closeness = (float) Math.pow(closeness, feedbackFalloff * stressMultiplier);
        return Math.min(100.0F, 8.0F + closeness * 92.0F);
    }

    /**
     * Returns a client-safe heat value for the insight aid. It exposes only
     * how close the current pick is, never the hidden target angle itself.
     */
    public float insightHeat(float pickAngle) {
        float difference = angularDistance(pickAngle, targetAngle);
        return Math.max(0.0F, Math.min(1.0F,
                1.0F - difference / PICK_ANGLE_SPAN));
    }

    /** Returns the shortest direction from the pick towards the hidden angle. */
    public float insightDirection(float pickAngle) {
        float difference = normalize(targetAngle - normalize(pickAngle));
        if (difference > 180.0F) {
            difference -= 360.0F;
        }
        return Math.signum(difference);
    }

    public boolean isWithinTolerance(float pickAngle) {
        return angularDistance(pickAngle, targetAngle) <= tolerance;
    }

    public static float normalize(float angle) {
        float normalized = angle % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    /** Clamp an arbitrary cursor angle to the nearest endpoint of the allowed arc. */
    public static float clampPickAngle(float angle) {
        float normalized = normalize(angle);
        if (normalized >= PICK_ANGLE_MIN && normalized <= PICK_ANGLE_MAX) {
            return normalized;
        }

        float distanceToMin = angularDistance(normalized, PICK_ANGLE_MIN);
        float distanceToMax = angularDistance(normalized, PICK_ANGLE_MAX);
        return distanceToMin <= distanceToMax ? PICK_ANGLE_MIN : PICK_ANGLE_MAX;
    }

    public static float angularDistance(float first, float second) {
        float difference = Math.abs(normalize(first) - normalize(second));
        return Math.min(difference, 360.0F - difference);
    }

    @Override
    public PuzzleActionResult apply(LockAction action) {
        if (action.type() != LockActionType.ROTARY_ATTEMPT) {
            return PuzzleActionResult.REJECTED;
        }
        float submittedAngle = action.value();
        float normalized = normalize(submittedAngle);
        // The client clamps the cursor to this arc, but the server must also
        // reject forged values outside the reachable range (including NaN).
        if (!Float.isFinite(submittedAngle)
                || normalized < PICK_ANGLE_MIN || normalized > PICK_ANGLE_MAX) {
            return PuzzleActionResult.REJECTED;
        }
        if (isWithinTolerance(normalized)) {
            complete = true;
            return PuzzleActionResult.SOLVED;
        }
        return PuzzleActionResult.MISTAKE;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }
}

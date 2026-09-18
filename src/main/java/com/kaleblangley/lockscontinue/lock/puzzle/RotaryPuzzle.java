package com.kaleblangley.lockscontinue.lock.puzzle;

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
    private boolean complete;

    public RotaryPuzzle(long seed) {
        Random random = new Random(seed ^ 0x524F544152594BL);
        // Keep every server-generated solution inside the angle range the client can reach.
        targetAngle = PICK_ANGLE_MIN + random.nextFloat() * PICK_ANGLE_SPAN;
        tolerance = 12.0F + random.nextInt(9);
    }

    public float tolerance() {
        return tolerance;
    }

    public float rotationAllowance(float pickAngle) {
        float difference = angularDistance(pickAngle, targetAngle);
        float closeness = Math.max(0.0F, 1.0F - difference / PICK_ANGLE_SPAN);
        return 8.0F + closeness * 92.0F;
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
            return PuzzleActionResult.WRONG;
        }
        if (isWithinTolerance(action.value())) {
            complete = true;
            return PuzzleActionResult.SOLVED;
        }
        return PuzzleActionResult.WRONG;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }
}

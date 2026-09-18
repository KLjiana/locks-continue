package com.kaleblangley.lockscontinue.lock.puzzle;

/** Immutable input sent from a client game to the server session. */
public record LockAction(LockActionType type, int first, int second, float value, int[] sequence) {
    public static LockAction pinLift(int index) {
        return new LockAction(LockActionType.PIN_LIFT, index, 0, 0.0F, new int[0]);
    }

    public static LockAction pinSet(int index) {
        return new LockAction(LockActionType.PIN_SET, index, 0, 0.0F, new int[0]);
    }

    public static LockAction pipeRotate(int index, int direction) {
        return new LockAction(LockActionType.PIPE_ROTATE, index, direction, 0.0F, new int[0]);
    }

    public static LockAction rotaryAttempt(float angle) {
        return new LockAction(LockActionType.ROTARY_ATTEMPT, 0, 0, angle, new int[0]);
    }

    public static LockAction memorySubmit(int[] order) {
        return new LockAction(LockActionType.MEMORY_SUBMIT, 0, 0, 0.0F, order.clone());
    }
}

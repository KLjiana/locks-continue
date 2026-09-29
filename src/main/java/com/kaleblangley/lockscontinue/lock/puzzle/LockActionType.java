package com.kaleblangley.lockscontinue.lock.puzzle;

/** Actions that can be submitted by a client-side lock game. */
public enum LockActionType {
    PIN_LIFT,
    PIN_SET,
    PIPE_ROTATE,
    ROTARY_ATTEMPT,
    MEMORY_PAIR
}

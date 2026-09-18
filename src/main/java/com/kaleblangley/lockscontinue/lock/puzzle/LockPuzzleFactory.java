package com.kaleblangley.lockscontinue.lock.puzzle;

import com.kaleblangley.lockscontinue.lock.LockType;

public final class LockPuzzleFactory {
    private LockPuzzleFactory() {
    }

    public static LockPuzzle create(LockType type, long seed) {
        return switch (type) {
            case PIN_LOCK -> new PinPuzzle(seed);
            case PIPE_LOCK -> new PipePuzzle(seed);
            case ROTARY_LOCK -> new RotaryPuzzle(seed);
            case MEMORY_LOCK -> new MemoryPuzzle(seed);
        };
    }
}

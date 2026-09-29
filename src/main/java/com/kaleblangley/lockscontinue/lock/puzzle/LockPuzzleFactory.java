package com.kaleblangley.lockscontinue.lock.puzzle;

import com.kaleblangley.lockscontinue.lock.LockType;
import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;

public final class LockPuzzleFactory {
    private LockPuzzleFactory() {
    }

    public static LockPuzzle create(LockType type, long seed) {
        return create(type, seed, null);
    }

    public static LockPuzzle create(LockType type, long seed,
                                    LockChallengeSnapshot challenge) {
        return create(type, seed, challenge, 0L);
    }

    /**
     * Creates the authoritative puzzle and anchors time-sensitive state to
     * the server tick at which the lock screen was opened.
     */
    public static LockPuzzle create(LockType type, long seed,
                                    LockChallengeSnapshot challenge, long startGameTime) {
        return switch (type) {
            case PIN_LOCK -> new PinPuzzle(seed, challenge);
            case PIPE_LOCK -> new PipePuzzle(seed, challenge);
            case ROTARY_LOCK -> new RotaryPuzzle(seed, challenge);
            case MEMORY_LOCK -> new MemoryPuzzle(seed, challenge, startGameTime);
        };
    }
}

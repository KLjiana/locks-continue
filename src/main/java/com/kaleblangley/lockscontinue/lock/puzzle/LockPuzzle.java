package com.kaleblangley.lockscontinue.lock.puzzle;

/** A deterministic puzzle state shared by the server validator and client UI. */
public interface LockPuzzle {
    PuzzleActionResult apply(LockAction action);

    /** Allows timing-sensitive puzzles to validate against authoritative server time. */
    default PuzzleActionResult apply(LockAction action, long gameTime) {
        return apply(action);
    }

    boolean isComplete();
}

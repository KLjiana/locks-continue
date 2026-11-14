package com.kaleblangley.lockscontinue.lock.puzzle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PinPuzzleTest {
    // Seed 0, pin 0 has a baseline lift/hold/fall of 12/3/11 animation ticks.
    @Test
    void liftHoldAndFallRunAtOneAndAHalfSpeed() {
        PinPuzzle puzzle = new PinPuzzle(0L);
        assertTrue(puzzle.beginLift(0, 100L));

        assertEquals(8.0D, puzzle.activeRiseTicks(), 1.0E-6D);
        assertEquals(2.0D, puzzle.activeHoldTicks(), 1.0E-6D);
        assertEquals(0.75F, puzzle.position(0, 104.0D), 1.0E-6F);
        assertEquals(1.0F, puzzle.position(0, 108.0D), 1.0E-6F);
        assertEquals(1.0F, puzzle.position(0, 109.5D), 1.0E-6F);
        assertEquals(0.75F, puzzle.position(0, 113.6666666667D), 1.0E-6F);
        assertTrue(puzzle.isLiftActive(117L));
        assertFalse(puzzle.isLiftActive(118L));
        assertEquals(0.0F, puzzle.position(0, 118.0D), 1.0E-6F);
        puzzle.finishExpiredLift(118L);
        assertEquals(-1, puzzle.activePin());
    }

    @Test
    void serverAcceptsTheFasterApexShownByClientWithAnOffsetClock() {
        PinPuzzle client = new PinPuzzle(0L);
        PinPuzzle server = new PinPuzzle(0L);
        assertTrue(client.beginLift(0, 0L));
        assertEquals(PuzzleActionResult.UPDATED,
                server.apply(LockAction.pinLift(0), 10_000L));

        assertEquals(client.position(0, 4.0D), server.position(0, 10_004.0D));
        assertEquals(1.0F, client.position(0, 8.0D));
        assertEquals(PuzzleActionResult.PROGRESS,
                server.apply(LockAction.pinSet(0), 10_008L));
        client.confirmSet(0);
        assertTrue(client.isLocked(0));
        assertTrue(server.isLocked(0));
    }

    @Test
    void timingToleranceRemainsTwoRealServerTicksAroundTheFasterHold() {
        assertEquals(PuzzleActionResult.MISTAKE, setAt(5L));
        assertEquals(PuzzleActionResult.PROGRESS, setAt(6L));
        assertEquals(PuzzleActionResult.PROGRESS, setAt(12L));
        assertEquals(PuzzleActionResult.MISTAKE, setAt(13L));
    }

    private static PuzzleActionResult setAt(long elapsedTicks) {
        PinPuzzle puzzle = new PinPuzzle(0L);
        puzzle.apply(LockAction.pinLift(0), 100L);
        return puzzle.apply(LockAction.pinSet(0), 100L + elapsedTicks);
    }
}

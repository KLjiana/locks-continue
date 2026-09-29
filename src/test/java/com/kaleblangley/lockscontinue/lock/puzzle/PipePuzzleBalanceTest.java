package com.kaleblangley.lockscontinue.lock.puzzle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PipePuzzleBalanceTest {
    private static final Difficulty[] DIFFICULTIES = {
            new Difficulty("simple", 1, 5, 1, 2, 5, 7, 20, 1),
            new Difficulty("normal", 2, 5, 2, 5, 9, 11, 23, 3),
            new Difficulty("hard", 3, 6, 3, 11, 17, 17, 34, 6),
            new Difficulty("expert", 4, 7, 4, 18, 28, 24, 47, 10),
            new Difficulty("master", 5, 8, 5, 28, 42, 32, 62, 14)
    };

    @Test
    void builtInDifficultyBandsAreDeterministicAndSolvable() {
        for (Difficulty difficulty : DIFFICULTIES) {
            for (long seed = 0; seed < 64; seed++) {
                PipePuzzle first = puzzle(seed, difficulty);
                PipePuzzle second = puzzle(seed, difficulty);
                assertFalse(first.isConnected(), difficulty.name + " seed " + seed);
                assertTrue(first.minimumSolutionRotations() >= difficulty.minimumTurns,
                        difficulty.name + " seed " + seed + " was below the difficulty floor");
                assertTrue(first.minimumSolutionRotations() <= difficulty.maximumTurns,
                        difficulty.name + " seed " + seed + " exceeded the difficulty ceiling");
                assertTrue(first.routeCellCount() >= difficulty.minimumRouteCells,
                        difficulty.name + " seed " + seed + " had a route that was too short");
                assertTrue(first.filledCellCount() >= difficulty.minimumFilledCells,
                        difficulty.name + " seed " + seed + " had too many blank cells");
                int routeFloor = difficulty.terminals == 1
                        ? difficulty.minimumTurns : Math.max(2,
                        (difficulty.minimumTurns + 2) / 3);
                assertTrue(first.guaranteedMinimumRouteRotations() >= routeFloor,
                        difficulty.name + " seed " + seed + " had an easy target shortcut");
                assertEquals(difficulty.terminals, first.terminalCount());
                assertEquals(first.startIndex(), second.startIndex());
                assertEquals(first.endIndex(), second.endIndex());
                assertEquals(first.minimumSolutionRotations(), second.minimumSolutionRotations());
                for (int index = 0; index < first.cellCount(); index++) {
                    assertEquals(first.type(index), second.type(index));
                    assertEquals(first.rotation(index), second.rotation(index));
                }
                solveWithHints(first, difficulty.name + " seed " + seed);
            }
        }
    }

    @Test
    void extraTerminalAffixStillProducesABoundedPuzzle() {
        PipePuzzle puzzle = new PipePuzzle(0xE87A1L, 8, 8, 6,
                33, 49, 36, 62, 14, 0, false, false, 0);
        assertFalse(puzzle.isConnected());
        assertTrue(puzzle.minimumSolutionRotations() >= 33);
        assertTrue(puzzle.minimumSolutionRotations() <= 49);
        assertEquals(6, puzzle.terminalCount());
        solveWithHints(puzzle, "extra terminal affix");
    }

    private static PipePuzzle puzzle(long seed, Difficulty value) {
        return new PipePuzzle(seed, value.size, value.size, value.terminals,
                value.minimumTurns, value.maximumTurns, value.minimumRouteCells,
                value.minimumFilledCells, value.minimumJunctions, 0, false, false,
                Math.max(0, 2 - value.rank / 2));
    }

    private static void solveWithHints(PipePuzzle puzzle, String message) {
        int rotations = 0;
        int limit = puzzle.cellCount() * 3;
        while (!puzzle.isConnected() && rotations < limit) {
            int index = puzzle.hintIndex();
            assertTrue(index >= 0, message + " had no route to a valid shared solution");
            int direction = puzzle.hintDirection(index);
            assertTrue(direction == 1 || direction == -1,
                    message + " returned an invalid hint direction");
            puzzle.rotate(index, direction);
            rotations++;
        }
        assertTrue(puzzle.isConnected(), message + " did not connect every target");
    }

    private record Difficulty(String name, int rank, int size,
                              int terminals,
                              int minimumTurns, int maximumTurns,
                              int minimumRouteCells, int minimumFilledCells,
                              int minimumJunctions) {
    }
}

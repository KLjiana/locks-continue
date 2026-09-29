package com.kaleblangley.lockscontinue.lock.puzzle;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PipeSolverTest {
    private static final int WIDTH = 3;
    private static final int HEIGHT = 3;
    private static final int START = 3;
    private static final int END = 5;

    @Test
    void routeSolverMatchesExhaustiveSearch() {
        Random random = new Random(0x51A7E5L);
        PipePuzzle.PipeType[] choices = {
                PipePuzzle.PipeType.EMPTY,
                PipePuzzle.PipeType.STRAIGHT,
                PipePuzzle.PipeType.ELBOW,
                PipePuzzle.PipeType.T_JUNCTION
        };
        for (int sample = 0; sample < 40; sample++) {
            PipePuzzle.PipeType[] types = new PipePuzzle.PipeType[WIDTH * HEIGHT];
            int[] current = new int[types.length];
            for (int index = 0; index < types.length; index++) {
                types[index] = choices[random.nextInt(choices.length)];
                current[index] = random.nextInt(types[index].uniqueRotationCount());
            }
            types[START] = PipePuzzle.PipeType.END_CAP;
            types[END] = PipePuzzle.PipeType.END_CAP;
            current[START] = 1;
            current[END] = 3;

            PipeSolveResult result = PipeSolver.solve(types, current, WIDTH, HEIGHT,
                    START, new int[]{END}, false, false);
            int exhaustive = exhaustiveMinimum(types, current, 0, current.clone(),
                    Integer.MAX_VALUE);
            assertEquals(exhaustive == Integer.MAX_VALUE, !result.solvable(),
                    "solvability mismatch for sample " + sample);
            if (result.solvable()) {
                assertEquals(exhaustive, result.minimumRotations(),
                        "minimum rotation mismatch for sample " + sample);
            }
        }
    }

    private static int exhaustiveMinimum(PipePuzzle.PipeType[] types, int[] current,
                                         int index, int[] candidate, int best) {
        if (index == types.length) {
            if (!connected(types, candidate)) {
                return best;
            }
            int cost = 0;
            for (int cell = 0; cell < types.length; cell++) {
                cost += rotationDistance(types[cell], current[cell], candidate[cell]);
            }
            return Math.min(best, cost);
        }
        if (index == START || index == END || types[index].fixed()) {
            candidate[index] = current[index];
            return exhaustiveMinimum(types, current, index + 1, candidate, best);
        }
        for (int rotation = 0; rotation < types[index].uniqueRotationCount(); rotation++) {
            candidate[index] = rotation;
            best = exhaustiveMinimum(types, current, index + 1, candidate, best);
        }
        return best;
    }

    private static boolean connected(PipePuzzle.PipeType[] types, int[] rotations) {
        boolean[] visited = new boolean[types.length];
        int[] queue = new int[types.length];
        int head = 0;
        int tail = 0;
        queue[tail++] = START;
        visited[START] = true;
        while (head < tail) {
            int cell = queue[head++];
            int mask = types[cell].mask(rotations[cell]);
            for (int direction : new int[]{1, 2, 4, 8}) {
                if ((mask & direction) == 0) {
                    continue;
                }
                int next = neighbor(cell, direction);
                if (next >= 0 && !visited[next]
                        && (types[next].mask(rotations[next]) & opposite(direction)) != 0) {
                    visited[next] = true;
                    queue[tail++] = next;
                }
            }
        }
        return visited[END];
    }

    private static int neighbor(int cell, int direction) {
        int x = cell % WIDTH;
        int y = cell / WIDTH;
        int nextX = x + (direction == 2 ? 1 : direction == 8 ? -1 : 0);
        int nextY = y + (direction == 4 ? 1 : direction == 1 ? -1 : 0);
        return nextX >= 0 && nextX < WIDTH && nextY >= 0 && nextY < HEIGHT
                ? nextY * WIDTH + nextX : -1;
    }

    private static int opposite(int direction) {
        return switch (direction) {
            case 1 -> 4;
            case 2 -> 8;
            case 4 -> 1;
            default -> 2;
        };
    }

    private static int rotationDistance(PipePuzzle.PipeType type, int current, int target) {
        int states = type.uniqueRotationCount();
        int clockwise = Math.floorMod(target - current, states);
        int counterClockwise = Math.floorMod(current - target, states);
        return Math.min(clockwise, counterClockwise);
    }
}

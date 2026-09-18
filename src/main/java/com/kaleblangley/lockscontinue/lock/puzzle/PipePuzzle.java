package com.kaleblangley.lockscontinue.lock.puzzle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A solvable five-by-five pipe network with deterministic rotations. */
public final class PipePuzzle implements LockPuzzle {
    public static final int SIZE = 5;
    public static final int CELL_COUNT = SIZE * SIZE;

    public enum PipeType {
        STRAIGHT(1 | 4),
        CROSS(1 | 2 | 4 | 8),
        ELBOW(1 | 2),
        T_JUNCTION(1 | 2 | 8);

        private final int baseMask;

        PipeType(int baseMask) {
            this.baseMask = baseMask;
        }

        public int mask(int rotation) {
            int result = baseMask;
            for (int i = 0; i < Math.floorMod(rotation, 4); i++) {
                result = ((result << 1) & 0b1111) | ((result >> 3) & 1);
            }
            return result;
        }
    }

    private final PipeType[] types = new PipeType[CELL_COUNT];
    private final int[] rotations = new int[CELL_COUNT];
    private final int startIndex;
    private final int endIndex;

    public PipePuzzle(long seed) {
        Random random = new Random(seed ^ 0x504950454C4F434BL);
        int startRow = random.nextInt(SIZE);
        int endRow = random.nextInt(SIZE);
        startIndex = index(0, startRow);
        endIndex = index(SIZE - 1, endRow);

        List<Integer> path = buildPath(random, startRow, endRow);
        boolean[] onPath = new boolean[CELL_COUNT];
        int[] desiredMasks = new int[CELL_COUNT];
        for (int i = 0; i < path.size(); i++) {
            int current = path.get(i);
            onPath[current] = true;
            int x = current % SIZE;
            int y = current / SIZE;
            int mask = 0;
            if (i > 0) {
                mask |= directionMask(path.get(i - 1) % SIZE - x, path.get(i - 1) / SIZE - y);
            }
            if (i + 1 < path.size()) {
                mask |= directionMask(path.get(i + 1) % SIZE - x, path.get(i + 1) / SIZE - y);
            }
            desiredMasks[current] = mask;
        }

        for (int i = 0; i < CELL_COUNT; i++) {
            if (onPath[i]) {
                int desired = desiredMasks[i];
                PipeType type = pipeTypeFor(desired);
                types[i] = type;
                int correct = findRotation(type, desired, Integer.bitCount(desired) == 1);
                rotations[i] = isEndpoint(i)
                        ? correct
                        : (correct + 1 + random.nextInt(3)) % 4;
            } else {
                types[i] = PipeType.values()[random.nextInt(PipeType.values().length)];
                rotations[i] = random.nextInt(4);
            }
        }

        // A path is guaranteed by construction, but keep the initial board
        // visibly unsolved so the first click always has meaning.
        if (isConnected()) {
            int disturbance = path.get(path.size() / 2);
            rotations[disturbance] = (rotations[disturbance] + 1) % 4;
        }
    }

    public PipeType type(int index) {
        return types[index];
    }

    public int rotation(int index) {
        return rotations[index];
    }

    public int mask(int index) {
        return types[index].mask(rotations[index]);
    }

    public int startIndex() {
        return startIndex;
    }

    public int endIndex() {
        return endIndex;
    }

    public boolean isEndpoint(int index) {
        return index == startIndex || index == endIndex;
    }

    public void rotate(int index, int direction) {
        if (index >= 0 && index < CELL_COUNT && !isEndpoint(index)) {
            rotations[index] = Math.floorMod(rotations[index] + direction, 4);
        }
    }

    public boolean isConnected() {
        boolean[] visited = new boolean[CELL_COUNT];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(startIndex);
        visited[startIndex] = true;
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            if (current == endIndex) {
                return true;
            }

            int x = current % SIZE;
            int y = current / SIZE;
            int mask = mask(current);
            for (int bit : new int[]{1, 2, 4, 8}) {
                if ((mask & bit) == 0) {
                    continue;
                }
                int nx = x + (bit == 2 ? 1 : bit == 8 ? -1 : 0);
                int ny = y + (bit == 4 ? 1 : bit == 1 ? -1 : 0);
                if (nx < 0 || nx >= SIZE || ny < 0 || ny >= SIZE) {
                    continue;
                }
                int next = index(nx, ny);
                if (!visited[next] && (mask(next) & opposite(bit)) != 0) {
                    visited[next] = true;
                    queue.addLast(next);
                }
            }
        }
        return false;
    }

    @Override
    public PuzzleActionResult apply(LockAction action) {
        if (action.type() != LockActionType.PIPE_ROTATE
                || action.first() < 0 || action.first() >= CELL_COUNT
                || isEndpoint(action.first())
                || (action.second() != 1 && action.second() != -1)) {
            return PuzzleActionResult.WRONG;
        }

        rotate(action.first(), action.second());
        return isConnected() ? PuzzleActionResult.SOLVED : PuzzleActionResult.UPDATED;
    }

    @Override
    public boolean isComplete() {
        return isConnected();
    }

    private static List<Integer> buildPath(Random random, int startRow, int endRow) {
        List<Integer> path = new ArrayList<>();
        int x = 0;
        int y = startRow;
        int horizontal = SIZE - 1;
        int vertical = Math.abs(endRow - startRow);
        int verticalDirection = Integer.compare(endRow, startRow);
        path.add(index(x, y));
        while (horizontal > 0 || vertical > 0) {
            boolean moveHorizontal = horizontal > 0 && (vertical == 0 || random.nextBoolean());
            if (moveHorizontal) {
                x++;
                horizontal--;
            } else {
                y += verticalDirection;
                vertical--;
            }
            path.add(index(x, y));
        }
        return path;
    }

    private static PipeType pipeTypeFor(int mask) {
        return switch (Integer.bitCount(mask)) {
            case 2 -> (mask == 5 || mask == 10) ? PipeType.STRAIGHT : PipeType.ELBOW;
            default -> PipeType.STRAIGHT;
        };
    }

    private static int findRotation(PipeType type, int desiredMask, boolean endpoint) {
        for (int rotation = 0; rotation < 4; rotation++) {
            int mask = type.mask(rotation);
            if ((endpoint && (mask & desiredMask) != 0) || (!endpoint && mask == desiredMask)) {
                return rotation;
            }
        }
        return 0;
    }

    private static int directionMask(int dx, int dy) {
        if (dx > 0) return 2;
        if (dx < 0) return 8;
        if (dy > 0) return 4;
        return 1;
    }

    private static int opposite(int bit) {
        return switch (bit) {
            case 1 -> 4;
            case 2 -> 8;
            case 4 -> 1;
            default -> 2;
        };
    }

    private static int index(int x, int y) {
        return y * SIZE + x;
    }
}

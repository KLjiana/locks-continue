package com.kaleblangley.lockscontinue.lock.puzzle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Exact solver for the pipe board.
 *
 * <p>The board is solved row by row.  A frontier state stores the connected
 * components that still have a port entering the next row.  A component that
 * disappears before the last row can never connect to anything below it, so
 * it is rejected when the board requires one connected network.  The same
 * state also carries the required endpoint tags for the relaxed
 * {@code require_all_connected=false} data-pack mode.</p>
 */
public final class PipeSolver {
    private static final int MAX_SOLUTION_COUNT = 2;

    private PipeSolver() {
    }

    public static PipeSolveResult solve(PipePuzzle.PipeType[] types,
                                        int[] currentRotations,
                                        int width,
                                        int height,
                                        int startIndex,
                                        int[] terminalIndices,
                                        boolean requireAllConnected,
                                        boolean forbidLeaks) {
        if (types == null || currentRotations == null
                || types.length != width * height
                || currentRotations.length != types.length
                || width < 1 || height < 1
                || startIndex < 0 || startIndex >= types.length) {
            return PipeSolveResult.unsolvable(0, 0, 0);
        }

        // The normal game mode is a route puzzle: only the start-to-end path
        // matters, so do not run the full all-components frontier search over
        // decorative branches.  Keeping the old solver for strict data-pack
        // rules preserves the opt-in validation mode for integrations that
        // still want a complete leak-free network.
        if (!requireAllConnected && !forbidLeaks
                && terminalIndices != null && terminalIndices.length == 1
                && types.length <= Long.SIZE) {
            return solveRoute(types, currentRotations, width, height,
                    startIndex, terminalIndices[0]);
        }

        int requiredMask = requiredMask(terminalIndices);
        @SuppressWarnings("unchecked")
        List<RowPattern>[] patternsByRow = new List[height];
        SolverStats stats = new SolverStats();
        for (int row = 0; row < height; row++) {
            patternsByRow[row] = rowPatterns(types, currentRotations, width, height,
                    startIndex, terminalIndices, row, forbidLeaks);
            if (patternsByRow[row].isEmpty()) {
                return PipeSolveResult.unsolvable(stats.visitedNodes,
                        stats.branchNodes, stats.backtracks);
            }
        }

        Map<FrontierState, SearchNode> states = new LinkedHashMap<>();
        states.put(FrontierState.empty(width), SearchNode.initial());

        for (int row = 0; row < height; row++) {
            Map<FrontierState, SearchNode> nextStates = new LinkedHashMap<>();
            boolean lastRow = row == height - 1;
            for (Map.Entry<FrontierState, SearchNode> entry : states.entrySet()) {
                stats.visitedNodes++;
                FrontierState previousState = entry.getKey();
                SearchNode previousNode = entry.getValue();
                for (RowPattern pattern : patternsByRow[row]) {
                    stats.branchNodes++;
                    FrontierState nextState = advance(previousState, pattern,
                            width, lastRow, requireAllConnected, forbidLeaks, requiredMask);
                    if (nextState == null) {
                        stats.backtracks++;
                        continue;
                    }

                    int candidateCost = previousNode.minimumCost + pattern.cost;
                    SearchNode existing = nextStates.get(nextState);
                    if (existing == null) {
                        nextStates.put(nextState, new SearchNode(candidateCost,
                                previousNode.solutionCount, previousNode, pattern));
                    } else {
                        existing.solutionCount = cappedAdd(existing.solutionCount,
                                previousNode.solutionCount);
                        if (candidateCost < existing.minimumCost) {
                            existing.minimumCost = candidateCost;
                            existing.previous = previousNode;
                            existing.pattern = pattern;
                        }
                    }
                }
            }
            if (nextStates.isEmpty()) {
                return PipeSolveResult.unsolvable(stats.visitedNodes,
                        stats.branchNodes, stats.backtracks);
            }
            states = nextStates;
        }

        SearchNode best = null;
        int solutionCount = 0;
        for (SearchNode candidate : states.values()) {
            if (best == null || candidate.minimumCost < best.minimumCost) {
                best = candidate;
                solutionCount = candidate.solutionCount;
            } else if (candidate.minimumCost == best.minimumCost) {
                solutionCount = cappedAdd(solutionCount, candidate.solutionCount);
            }
        }
        if (best == null) {
            return PipeSolveResult.unsolvable(stats.visitedNodes,
                    stats.branchNodes, stats.backtracks);
        }

        int[] solution = reconstruct(best, width, height);
        return new PipeSolveResult(true, best.minimumCost, solutionCount,
                stats.visitedNodes, stats.branchNodes, stats.backtracks, solution);
    }

    /**
     * Finds the cheapest start-to-end route while ignoring every unrelated
     * component.  Each state stores the entry direction and chosen rotation of
     * the current tile; the rest of the board is left at its current rotation.
     */
    private static PipeSolveResult solveRoute(PipePuzzle.PipeType[] types,
                                              int[] currentRotations,
                                              int width,
                                              int height,
                                              int startIndex,
                                              int endIndex) {
        if (startIndex == endIndex
                || startIndex < 0 || endIndex < 0
                || startIndex >= types.length || endIndex >= types.length) {
            return PipeSolveResult.unsolvable(0, 0, 0);
        }

        List<RouteNode> nodes = new ArrayList<>();
        PriorityQueue<Integer> frontier = new PriorityQueue<>(
                Comparator.comparingInt(index -> nodes.get(index).cost()));
        Map<RouteKey, Integer> bestCosts = new HashMap<>();

        PipePuzzle.PipeType startType = types[startIndex];
        int startRotation = fixedRotation(startType, currentRotations[startIndex]);
        RouteNode start = new RouteNode(startIndex, 0, startRotation,
                0, -1, 1L << startIndex);
        nodes.add(start);
        frontier.add(0);
        bestCosts.put(new RouteKey(startIndex, 0, startRotation,
                start.visitedMask()), 0);

        long visitedNodes = 0;
        long branchNodes = 0;
        while (!frontier.isEmpty()) {
            int nodeIndex = frontier.remove();
            RouteNode node = nodes.get(nodeIndex);
            RouteKey nodeKey = new RouteKey(node.cell(), node.entry(),
                    node.rotation(), node.visitedMask());
            if (node.cost() != bestCosts.getOrDefault(nodeKey, node.cost())) {
                continue;
            }
            visitedNodes++;
            if (node.cell() == endIndex && node.parent() >= 0) {
                return new PipeSolveResult(true, node.cost(), 1,
                        visitedNodes, branchNodes, 0,
                        reconstructRoute(nodes, nodeIndex, currentRotations));
            }

            branchNodes++;
            int mask = node.mask(types);
            for (int direction : new int[]{1, 2, 4, 8}) {
                if ((mask & direction) == 0
                        || (node.entry() != 0 && direction == node.entry())) {
                    continue;
                }
                int next = neighbor(node.cell(), direction, width, height);
                if (next < 0 || (node.visitedMask() & (1L << next)) != 0) {
                    continue;
                }

                PipePuzzle.PipeType nextType = types[next];
                if (nextType == PipePuzzle.PipeType.EMPTY) {
                    continue;
                }
                int entry = opposite(direction);
                int[] candidates = next == endIndex || next == startIndex || nextType.fixed()
                        ? new int[]{fixedRotation(nextType, currentRotations[next])}
                        : uniqueRotations(nextType);
                for (int candidate : candidates) {
                    if ((nextType.mask(candidate) & entry) == 0) {
                        continue;
                    }
                    int cost = node.cost() + rotationDistance(nextType,
                            currentRotations[next], candidate);
                    long visitedMask = node.visitedMask() | (1L << next);
                    RouteKey key = new RouteKey(next, entry, candidate, visitedMask);
                    if (cost >= bestCosts.getOrDefault(key, Integer.MAX_VALUE)) {
                        continue;
                    }
                    bestCosts.put(key, cost);
                    RouteNode child = new RouteNode(next, entry, candidate, cost,
                            nodeIndex, visitedMask);
                    nodes.add(child);
                    frontier.add(nodes.size() - 1);
                }
            }
        }
        return PipeSolveResult.unsolvable(visitedNodes, branchNodes, 0);
    }

    private static int[] reconstructRoute(List<RouteNode> nodes, int goal,
                                          int[] currentRotations) {
        int[] solution = currentRotations.clone();
        for (int index = goal; index >= 0; index = nodes.get(index).parent()) {
            RouteNode node = nodes.get(index);
            solution[node.cell()] = node.rotation();
        }
        return solution;
    }

    private static int neighbor(int cell, int direction, int width, int height) {
        int x = cell % width;
        int y = cell / width;
        int nextX = x + (direction == 2 ? 1 : direction == 8 ? -1 : 0);
        int nextY = y + (direction == 4 ? 1 : direction == 1 ? -1 : 0);
        return nextX >= 0 && nextX < width && nextY >= 0 && nextY < height
                ? nextY * width + nextX : -1;
    }

    private static int opposite(int direction) {
        return switch (direction) {
            case 1 -> 4;
            case 2 -> 8;
            case 4 -> 1;
            default -> 2;
        };
    }

    private static int fixedRotation(PipePuzzle.PipeType type, int rotation) {
        return type.fixed()
                ? Math.floorMod(rotation, 4)
                : Math.floorMod(rotation, type.uniqueRotationCount());
    }

    private static List<RowPattern> rowPatterns(PipePuzzle.PipeType[] types,
                                                int[] currentRotations,
                                                int width,
                                                int height,
                                                int startIndex,
                                                int[] terminalIndices,
                                                int row,
                                                boolean forbidLeaks) {
        List<RowPattern> patterns = new ArrayList<>();
        int[] rotations = new int[width];
        int[] masks = new int[width];
        int[] tags = new int[width];
        enumerateRow(patterns, types, currentRotations, width, height,
                startIndex, terminalIndices, row, 0, false, 0, 0, 0,
                forbidLeaks, rotations, masks, tags);
        return patterns;
    }

    private static void enumerateRow(List<RowPattern> patterns,
                                     PipePuzzle.PipeType[] types,
                                     int[] currentRotations,
                                     int width,
                                     int height,
                                     int startIndex,
                                     int[] terminalIndices,
                                     int row,
                                     int column,
                                     boolean previousRight,
                                     int topMask,
                                     int bottomMask,
                                     int cost,
                                     boolean forbidLeaks,
                                     int[] rowRotations,
                                     int[] rowMasks,
                                     int[] rowTags) {
        if (column == width) {
            patterns.add(new RowPattern(rowRotations.clone(), rowMasks.clone(),
                    rowTags.clone(), topMask, bottomMask, cost));
            return;
        }

        int index = row * width + column;
        boolean endpoint = index == startIndex || contains(terminalIndices, index);
        boolean fixed = endpoint || types[index].fixed();
        int[] candidates = fixed
                ? new int[]{Math.floorMod(currentRotations[index], 4)}
                : uniqueRotations(types[index]);

        for (int candidateRotation : candidates) {
            int candidateMask = types[index].mask(candidateRotation);
            if (forbidLeaks
                    && ((column == 0 && hasLeft(candidateMask))
                    || (column == width - 1 && hasRight(candidateMask))
                    || (row == 0 && hasUp(candidateMask))
                    || (row == height - 1 && hasDown(candidateMask))
                    || (column > 0 && hasLeft(candidateMask) != previousRight))) {
                continue;
            }

            rowRotations[column] = candidateRotation;
            rowMasks[column] = candidateMask;
            rowTags[column] = endpointTag(index, startIndex, terminalIndices);
            enumerateRow(patterns, types, currentRotations, width, height,
                    startIndex, terminalIndices, row, column + 1,
                    hasRight(candidateMask),
                    topMask | (hasUp(candidateMask) ? 1 << column : 0),
                    bottomMask | (hasDown(candidateMask) ? 1 << column : 0),
                    cost + rotationDistance(types[index], currentRotations[index],
                            candidateRotation),
                    forbidLeaks, rowRotations, rowMasks, rowTags);
            rowTags[column] = 0;
        }
    }

    private static FrontierState advance(FrontierState previous,
                                         RowPattern pattern,
                                         int width,
                                         boolean lastRow,
                                         boolean requireAllConnected,
                                         boolean forbidLeaks,
                                         int requiredMask) {
        int previousComponentCount = maxLabel(previous.labels);
        int nodeCount = width + previousComponentCount;
        int[] parent = new int[nodeCount];
        int[] nodeTags = new int[nodeCount];
        for (int node = 0; node < nodeCount; node++) {
            parent[node] = node;
        }
        for (int column = 0; column < width; column++) {
            nodeTags[column] = pattern.tags[column];
        }
        for (int label = 1; label <= previousComponentCount; label++) {
            nodeTags[width + label - 1] = previous.tags[label - 1];
        }

        for (int column = 1; column < width; column++) {
            if (hasRight(pattern.masks[column - 1])
                    && hasLeft(pattern.masks[column])) {
                union(parent, column - 1, column);
            }
        }

        for (int column = 0; column < width; column++) {
            int previousLabel = previous.labels[column];
            boolean previousPort = previousLabel > 0;
            boolean currentPort = (pattern.topMask & (1 << column)) != 0;
            if (forbidLeaks && previousPort != currentPort) {
                return null;
            }
            if (previousPort && currentPort) {
                int previousNode = width + previousLabel - 1;
                union(parent, column, previousNode);
            }
        }

        boolean[] previousHasCurrent = new boolean[nodeCount];
        for (int column = 0; column < width; column++) {
            int previousLabel = previous.labels[column];
            if (previousLabel > 0
                    && (pattern.topMask & (1 << column)) != 0) {
                previousHasCurrent[find(parent, width + previousLabel - 1)] = true;
            }
        }

        int[] rootTags = new int[nodeCount];
        for (int node = 0; node < nodeCount; node++) {
            rootTags[find(parent, node)] |= nodeTags[node];
        }

        boolean completedRequired = previous.completedRequired;
        boolean[] previousRootsSeen = new boolean[nodeCount];
        boolean[] currentRootsSeen = new boolean[nodeCount];
        boolean[] currentRootsHaveBottom = new boolean[nodeCount];
        int currentRootCount = 0;
        for (int column = 0; column < width; column++) {
            if (pattern.masks[column] == 0) {
                continue;
            }
            int currentRoot = find(parent, column);
            if (!currentRootsSeen[currentRoot]) {
                currentRootsSeen[currentRoot] = true;
                currentRootCount++;
            }
            if ((pattern.bottomMask & (1 << column)) != 0) {
                currentRootsHaveBottom[currentRoot] = true;
            }
        }
        for (int label = 1; label <= previousComponentCount; label++) {
            int previousRoot = find(parent, width + label - 1);
            if (previousRootsSeen[previousRoot]) {
                continue;
            }
            previousRootsSeen[previousRoot] = true;
            if (previousHasCurrent[previousRoot]) {
                continue;
            }
            if ((rootTags[previousRoot] & requiredMask) != requiredMask) {
                return null;
            }
            completedRequired = true;
        }

        if (requireAllConnected && completedRequired && currentRootCount > 0) {
            return null;
        }

        boolean endedRequired = false;
        for (int root = 0; root < nodeCount; root++) {
            if (!currentRootsSeen[root] || currentRootsHaveBottom[root]) {
                continue;
            }
            if (requireAllConnected) {
                if ((rootTags[root] & requiredMask) != requiredMask) {
                    return null;
                }
                endedRequired = true;
            } else if ((rootTags[root] & requiredMask) != requiredMask) {
                // An untagged component may end here.  A component containing
                // only part of the required network may not.
                if (rootTags[root] != 0) {
                    return null;
                }
            } else {
                completedRequired = true;
            }
        }

        if (requireAllConnected && endedRequired) {
            if (currentRootCount != 1) {
                return null;
            }
            completedRequired = true;
        }

        if (lastRow) {
            if (requireAllConnected) {
                if (currentRootCount == 0) {
                    return completedRequired ? FrontierState.empty(width) : null;
                }
                return currentRootCount == 1 && completedRequired
                        ? FrontierState.empty(width) : null;
            }
            boolean requiredConnected = completedRequired;
            for (int root = 0; root < nodeCount; root++) {
                if (currentRootsSeen[root]
                        && (rootTags[root] & requiredMask) == requiredMask) {
                    requiredConnected = true;
                    break;
                }
            }
            return requiredConnected
                    ? FrontierState.completed(width) : null;
        }

        int[] labels = new int[width];
        int[] tags = new int[width];
        int[] rootLabels = new int[nodeCount];
        int nextLabel = 1;
        for (int column = 0; column < width; column++) {
            if ((pattern.bottomMask & (1 << column)) == 0) {
                continue;
            }
            int root = find(parent, column);
            if (rootLabels[root] == 0) {
                rootLabels[root] = nextLabel++;
            }
            int label = rootLabels[root];
            labels[column] = label;
            tags[label - 1] = rootTags[root];
        }
        return new FrontierState(labels, tags, completedRequired);
    }

    private static int[] reconstruct(SearchNode best, int width, int height) {
        int[] solution = new int[width * height];
        SearchNode node = best;
        for (int row = height - 1; row >= 0 && node != null; row--) {
            if (node.pattern == null) {
                break;
            }
            System.arraycopy(node.pattern.rotations, 0, solution,
                    row * width, width);
            node = node.previous;
        }
        return solution;
    }

    private static int[] uniqueRotations(PipePuzzle.PipeType type) {
        int[] rotations = new int[type.uniqueRotationCount()];
        for (int rotation = 0; rotation < rotations.length; rotation++) {
            rotations[rotation] = rotation;
        }
        return rotations;
    }

    private static int rotationDistance(PipePuzzle.PipeType type,
                                        int current,
                                        int target) {
        int best = Integer.MAX_VALUE;
        int stateCount = type.uniqueRotationCount();
        int currentState = Math.floorMod(current, stateCount);
        int targetState = Math.floorMod(target, stateCount);
        for (int state = 0; state < stateCount; state++) {
            if (type.mask(state) != type.mask(targetState)) {
                continue;
            }
            int clockwise = Math.floorMod(state - currentState, stateCount);
            int counterClockwise = Math.floorMod(currentState - state, stateCount);
            best = Math.min(best, Math.min(clockwise, counterClockwise));
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private static int requiredMask(int[] terminalIndices) {
        int tagCount = 1 + (terminalIndices == null ? 0 : terminalIndices.length);
        return tagCount >= Integer.SIZE - 1 ? -1 : (1 << tagCount) - 1;
    }

    private static int endpointTag(int index, int startIndex, int[] terminalIndices) {
        if (index == startIndex) {
            return 1;
        }
        if (terminalIndices != null) {
            for (int terminal = 0; terminal < terminalIndices.length; terminal++) {
                if (terminalIndices[terminal] == index) {
                    return 1 << (terminal + 1);
                }
            }
        }
        return 0;
    }

    private static boolean contains(int[] values, int value) {
        if (values == null) {
            return false;
        }
        for (int candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUp(int mask) {
        return (mask & 1) != 0;
    }

    private static boolean hasRight(int mask) {
        return (mask & 2) != 0;
    }

    private static boolean hasDown(int mask) {
        return (mask & 4) != 0;
    }

    private static boolean hasLeft(int mask) {
        return (mask & 8) != 0;
    }

    private static int maxLabel(int[] labels) {
        int maximum = 0;
        for (int label : labels) {
            maximum = Math.max(maximum, label);
        }
        return maximum;
    }

    private static int find(int[] parent, int value) {
        int root = value;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[value] != value) {
            int next = parent[value];
            parent[value] = root;
            value = next;
        }
        return root;
    }

    private static void union(int[] parent, int first, int second) {
        int firstRoot = find(parent, first);
        int secondRoot = find(parent, second);
        if (firstRoot != secondRoot) {
            parent[secondRoot] = firstRoot;
        }
    }

    private static int cappedAdd(int first, int second) {
        return Math.min(MAX_SOLUTION_COUNT, first + second);
    }

    private static final class SolverStats {
        private long visitedNodes;
        private long branchNodes;
        private long backtracks;
    }

    private record RouteNode(int cell, int entry, int rotation, int cost,
                             int parent, long visitedMask) {
        private int mask(PipePuzzle.PipeType[] types) {
            return types[cell].mask(rotation);
        }
    }

    private record RouteKey(int cell, int entry, int rotation, long visitedMask) {
    }

    private static final class SearchNode {
        private int minimumCost;
        private int solutionCount;
        private SearchNode previous;
        private RowPattern pattern;

        private SearchNode(int minimumCost, int solutionCount,
                           SearchNode previous, RowPattern pattern) {
            this.minimumCost = minimumCost;
            this.solutionCount = solutionCount;
            this.previous = previous;
            this.pattern = pattern;
        }

        private static SearchNode initial() {
            return new SearchNode(0, 1, null, null);
        }
    }

    private record RowPattern(int[] rotations, int[] masks, int[] tags,
                              int topMask, int bottomMask, int cost) {
        private RowPattern {
            rotations = rotations.clone();
            masks = masks.clone();
            tags = tags.clone();
        }
    }

    private static final class FrontierState {
        private final int[] labels;
        private final int[] tags;
        private final boolean completedRequired;

        private FrontierState(int[] labels, int[] tags, boolean completedRequired) {
            this.labels = labels.clone();
            this.tags = tags.clone();
            this.completedRequired = completedRequired;
        }

        private static FrontierState empty(int width) {
            return new FrontierState(new int[width], new int[width], false);
        }

        private static FrontierState completed(int width) {
            return new FrontierState(new int[width], new int[width], true);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FrontierState state
                    && completedRequired == state.completedRequired
                    && Arrays.equals(labels, state.labels)
                    && Arrays.equals(tags, state.tags);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(labels);
            result = 31 * result + Arrays.hashCode(tags);
            return 31 * result + Boolean.hashCode(completedRequired);
        }
    }
}

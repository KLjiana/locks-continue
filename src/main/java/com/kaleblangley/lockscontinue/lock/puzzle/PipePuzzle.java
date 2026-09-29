package com.kaleblangley.lockscontinue.lock.puzzle;

import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A deterministic pipe-network puzzle.
 *
 * <p>Generation deliberately has separate stages: a random spanning-tree
 * topology is created first, its node degrees become tile types, the tile
 * orientations are scrambled, and {@link PipeSolver} calculates the minimum
 * number of real player rotations over every legal solution.  The generated
 * tree is therefore only a guaranteed solution, not the answer used to fake
 * the difficulty value.</p>
 */
public final class PipePuzzle implements LockPuzzle {
    /** Compatibility constants for older callers and the normal 5x5 board. */
    public static final int SIZE = 5;
    public static final int CELL_COUNT = SIZE * SIZE;
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 8;

    private static final int MAX_GENERATION_ATTEMPTS = 64;
    private static final int TOPOLOGY_ATTEMPTS = 48;
    private static final long RANDOM_SALT = 0x504950454C4F434BL;

    /**
     * The shape is independent of its direction.  The first four masks are
     * UP, RIGHT, DOWN and LEFT respectively.
     */
    public enum PipeType {
        EMPTY(0, 1, true),
        END_CAP(1, 1, true),
        STRAIGHT(1 | 4, 2, false),
        CROSS(1 | 2 | 4 | 8, 1, true),
        ELBOW(1 | 2, 4, false),
        T_JUNCTION(1 | 2 | 8, 4, false);

        private final int baseMask;
        private final int uniqueRotationCount;
        private final boolean fixed;

        PipeType(int baseMask, int uniqueRotationCount, boolean fixed) {
            this.baseMask = baseMask;
            this.uniqueRotationCount = uniqueRotationCount;
            this.fixed = fixed;
        }

        public int mask(int rotation) {
            int result = baseMask;
            for (int step = 0; step < Math.floorMod(rotation, 4); step++) {
                result = ((result << 1) & 0b1111) | ((result >> 3) & 1);
            }
            return result;
        }

        public int uniqueRotationCount() {
            return uniqueRotationCount;
        }

        public boolean fixed() {
            return fixed;
        }

        public boolean rotatable() {
            return !fixed && uniqueRotationCount > 1;
        }
    }

    private final int width;
    private final int height;
    private final int cellCount;
    private final PipeType[] types;
    private final int[] rotations;
    private final int[] solutionRotations;
    private final int startIndex;
    private final int[] terminalIndices;
    private final int configuredMinimumTurns;
    private final int configuredMaximumTurns;
    private final int minimumRouteCells;
    private final int minimumFilledCells;
    private final int minimumJunctions;
    private final int minimumDeadEnds;
    private final int hintCount;
    private final boolean requireAllConnected;
    private final boolean forbidLeaks;
    private final PipeSolveResult initialSolveResult;
    private final int initialRouteCells;
    private final int guaranteedMinimumRouteRotations;
    private int rotationRevision;
    private int hintRevision = -1;
    private int[] hintSolution;

    public PipePuzzle(long seed) {
        this(seed, null);
    }

    public PipePuzzle(long seed, LockChallengeSnapshot challenge) {
        this(seed,
                challenge == null ? SIZE : challenge.pipeWidth(),
                challenge == null ? SIZE : challenge.pipeHeight(),
                challenge == null ? 1 : challenge.pipeTerminalCount(),
                challenge == null ? 6 : challenge.pipeMinimumTurns(),
                challenge == null ? 10 : challenge.pipeMaximumTurns(),
                challenge == null ? 8 : challenge.pipeMinimumRouteCells(),
                challenge == null ? 20 : challenge.pipeMinimumFilledCells(),
                challenge == null ? 0 : challenge.pipeMinimumJunctions(),
                challenge == null ? 0 : challenge.pipeMinimumDeadEnds(),
                challenge != null && challenge.pipeRequireAllConnected(),
                challenge != null && challenge.pipeForbidLeaks(),
                challenge == null ? 0 : challenge.pipeHintCount());
    }

    /** Package-private balance-test entry point without Minecraft network types. */
    PipePuzzle(long seed, int requestedWidth, int requestedHeight,
               int requestedTerminals, int minimumTurns, int maximumTurns,
               int requestedMinimumRouteCells, int requestedMinimumFilledCells,
               int requestedMinimumJunctions, int requestedMinimumDeadEnds,
               boolean requireAllConnected, boolean forbidLeaks, int hintCount) {
        Random random = new Random(seed ^ RANDOM_SALT);
        width = clamp(requestedWidth, MIN_SIZE, MAX_SIZE);
        height = clamp(requestedHeight, MIN_SIZE, MAX_SIZE);
        cellCount = width * height;
        configuredMinimumTurns = Math.max(0, minimumTurns);
        configuredMaximumTurns = Math.max(Math.max(1, configuredMinimumTurns), maximumTurns);
        minimumRouteCells = clamp(requestedMinimumRouteCells, 2, cellCount);
        minimumFilledCells = clamp(requestedMinimumFilledCells, 2, cellCount);
        minimumJunctions = Math.max(0, requestedMinimumJunctions);
        minimumDeadEnds = Math.max(0, requestedMinimumDeadEnds);
        this.hintCount = Math.max(0, hintCount);
        // The pipe game is a start-to-end route puzzle by default.  Optional
        // strict-network flags are still read from a data-pack challenge, but
        // the built-in/legacy constructor must not make decorative branches
        // part of the success condition.
        this.requireAllConnected = requireAllConnected;
        this.forbidLeaks = forbidLeaks;

        GeneratedNetwork generated = generate(random, requestedTerminals);
        types = generated.types();
        rotations = generated.rotations();
        solutionRotations = generated.solutionRotations();
        startIndex = generated.startIndex();
        terminalIndices = generated.terminals();
        initialSolveResult = generated.solveResult();
        initialRouteCells = generated.routeCells();
        guaranteedMinimumRouteRotations = generated.guaranteedMinimumRouteRotations();
        hintSolution = solutionRotations.clone();
        rotationRevision = 0;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int cellCount() {
        return cellCount;
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

    public boolean isRotatable(int index) {
        return index >= 0 && index < cellCount
                && !isEndpoint(index)
                && types[index].rotatable();
    }

    public int startIndex() {
        return startIndex;
    }

    /** Kept for the old two-terminal UI; it returns the first target. */
    public int endIndex() {
        return terminalIndices[0];
    }

    public int terminalCount() {
        return terminalIndices.length;
    }

    public boolean isTerminal(int index) {
        if (index == startIndex) {
            return true;
        }
        for (int terminal : terminalIndices) {
            if (terminal == index) {
                return true;
            }
        }
        return false;
    }

    public boolean isEndpoint(int index) {
        return isTerminal(index);
    }

    /** The configured lower bound used by generation. */
    public int minimumTurns() {
        return configuredMinimumTurns;
    }

    /** The configured upper bound used to keep easy boards from spiking. */
    public int maximumTurns() {
        return configuredMaximumTurns;
    }

    /** Minimum number of cells in the generated start-to-terminal route tree. */
    public int minimumRouteCells() {
        return minimumRouteCells;
    }

    /** Actual size of the generated route tree before tiles are scrambled. */
    public int routeCellCount() {
        return initialRouteCells;
    }

    public int filledCellCount() {
        int count = 0;
        for (PipeType type : types) {
            if (type != PipeType.EMPTY) {
                count++;
            }
        }
        return count;
    }

    /**
     * Exact minimum for a single target; planned valid route-tree rotations
     * for a multi-target board. Kept for data-pack/API compatibility.
     */
    public int minimumSolutionRotations() {
        return initialSolveResult.minimumRotations();
    }

    public int expectedSolutionRotations() {
        return initialSolveResult.minimumRotations();
    }

    /** Lower bound proven by solving every target route independently. */
    public int guaranteedMinimumRouteRotations() {
        return guaranteedMinimumRouteRotations;
    }

    /** Capped at two: zero, one or multiple legal solutions. */
    public int solutionCount() {
        return initialSolveResult.solutionCount();
    }

    public int hintCount() {
        return hintCount;
    }

    /**
     * Finds a cell that differs from a legal solution of the current board.
     * This is recalculated after the player rotates a tile, instead of using
     * the original tree orientation as a false answer.
     */
    public int hintIndex() {
        ensureHintSolution();
        for (int index = 0; index < cellCount; index++) {
            if (isRotatable(index)
                    && types[index].mask(rotations[index])
                    != types[index].mask(hintSolution[index])) {
                return index;
            }
        }
        return -1;
    }

    /** Returns the shortest click direction towards the current legal solution. */
    public int hintDirection(int index) {
        if (!isRotatable(index)) {
            return 0;
        }
        ensureHintSolution();
        int stateCount = types[index].uniqueRotationCount();
        int clockwise = Math.floorMod(hintSolution[index] - rotations[index], stateCount);
        int counterClockwise = Math.floorMod(rotations[index] - hintSolution[index], stateCount);
        if (clockwise == 0 && counterClockwise == 0) {
            return 0;
        }
        return clockwise <= counterClockwise ? 1 : -1;
    }

    /** Returns whether a cell has a port facing outside or an unmatched neighbor. */
    public boolean hasLeakAt(int current) {
        if (current < 0 || current >= cellCount) {
            return true;
        }
        int x = current % width;
        int y = current / width;
        int currentMask = mask(current);
        for (int bit : new int[]{1, 2, 4, 8}) {
            if ((currentMask & bit) == 0) {
                continue;
            }
            int nx = x + (bit == 2 ? 1 : bit == 8 ? -1 : 0);
            int ny = y + (bit == 4 ? 1 : bit == 1 ? -1 : 0);
            if (!inside(nx, ny)
                    || (mask(index(nx, ny)) & opposite(bit)) == 0) {
                return true;
            }
        }
        return false;
    }

    public void rotate(int index, int direction) {
        if (isRotatable(index)
                && (direction == 1 || direction == -1)) {
            int states = types[index].uniqueRotationCount();
            rotations[index] = Math.floorMod(rotations[index] + direction, states);
            rotationRevision++;
        }
    }

    /** Returns true only when all configured network rules are satisfied. */
    public boolean isConnected() {
        boolean[] visited = reachable();
        for (int terminal : terminalIndices) {
            if (!visited[terminal]) {
                return false;
            }
        }
        if (requireAllConnected) {
            for (int index = 0; index < visited.length; index++) {
                if (mask(index) != 0 && !visited[index]) {
                    return false;
                }
            }
        }
        return !forbidLeaks || !hasLeak();
    }

    @Override
    public PuzzleActionResult apply(LockAction action) {
        if (action.type() != LockActionType.PIPE_ROTATE
                || action.first() < 0 || action.first() >= cellCount
                || !isRotatable(action.first())
                || (action.second() != 1 && action.second() != -1)) {
            return PuzzleActionResult.REJECTED;
        }

        rotate(action.first(), action.second());
        return isConnected() ? PuzzleActionResult.SOLVED : PuzzleActionResult.PROGRESS;
    }

    @Override
    public boolean isComplete() {
        return isConnected();
    }

    private void ensureHintSolution() {
        if (hintRevision == rotationRevision) {
            return;
        }
        if (!requireAllConnected && !forbidLeaks && terminalIndices.length > 1) {
            // The generated route tree is a valid shared solution for every
            // target. Reusing it avoids invoking the strict whole-board solver
            // for a hint on dense multi-target boards.
            hintSolution = solutionRotations.clone();
            hintRevision = rotationRevision;
            return;
        }
        PipeSolveResult result = PipeSolver.solve(types, rotations, width, height,
                startIndex, terminalIndices, requireAllConnected, forbidLeaks);
        if (result.solvable() && result.rotations().length == cellCount) {
            hintSolution = result.rotations();
        }
        hintRevision = rotationRevision;
    }

    private boolean[] reachable() {
        boolean[] visited = new boolean[cellCount];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(startIndex);
        visited[startIndex] = true;
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            int x = current % width;
            int y = current / width;
            int currentMask = mask(current);
            for (int bit : new int[]{1, 2, 4, 8}) {
                if ((currentMask & bit) == 0) {
                    continue;
                }
                int nx = x + (bit == 2 ? 1 : bit == 8 ? -1 : 0);
                int ny = y + (bit == 4 ? 1 : bit == 1 ? -1 : 0);
                if (!inside(nx, ny)) {
                    continue;
                }
                int next = index(nx, ny);
                if (!visited[next] && (mask(next) & opposite(bit)) != 0) {
                    visited[next] = true;
                    queue.addLast(next);
                }
            }
        }
        return visited;
    }

    private boolean hasLeak() {
        for (int current = 0; current < cellCount; current++) {
            if (hasLeakAt(current)) {
                return true;
            }
        }
        return false;
    }

    private GeneratedNetwork generate(Random random, int requestedTerminals) {
        int terminalLimit = Math.max(1,
                Math.min(requestedTerminals, Math.min(16, cellCount - 1)));
        Candidate best = null;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            Topology topology = generateTopology(random, terminalLimit);
            if (topology == null) {
                continue;
            }
            Candidate candidate = buildCandidate(topology, random);
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.penalty() < best.penalty()
                    || (candidate.penalty() == best.penalty()
                    && candidate.actualMinimum() > best.actualMinimum())) {
                best = candidate;
            }
            if (matchesDifficulty(candidate)
                    && candidate.actualMinimum() > 0) {
                return candidate.network();
            }
        }

        if (best != null) {
            return best.network();
        }

        // Legality is more important than an impossible data-pack target.  A
        // deterministic comb gives the fallback several terminals and real
        // branches without throwing from the server thread.
        Topology fallback = fallbackTopology(terminalLimit);
        Candidate fallbackCandidate = buildCandidate(fallback, random);
        if (fallbackCandidate != null) {
            return fallbackCandidate.network();
        }
        return guaranteedFallback(fallback, random);
    }

    private Topology generateTopology(Random random, int terminalLimit) {
        for (int attempt = 0; attempt < TOPOLOGY_ATTEMPTS; attempt++) {
            int[] masks = carveRandomGrowthTree(random, terminalLimit);
            int junctions = countJunctions(masks);
            int deadEnds = countDeadEnds(masks);
            int filledCells = countFilledCells(masks);
            if (junctions < minimumJunctions || deadEnds < minimumDeadEnds) {
                continue;
            }

            List<Integer> leaves = new ArrayList<>();
            for (int current = 0; current < cellCount; current++) {
                if (Integer.bitCount(masks[current]) == 1) {
                    leaves.add(current);
                }
            }
            if (leaves.size() < terminalLimit + 1) {
                continue;
            }

            int start = chooseStart(leaves, random);
            int[] terminals = chooseTerminals(masks, leaves, start, terminalLimit, random);
            if (terminals == null) {
                continue;
            }
            int routeCells = countRequiredRouteCells(masks, start, terminals);
            if (routeCells < minimumRouteCells) {
                continue;
            }
            return new Topology(masks, start, terminals, junctions, deadEnds,
                    routeCells, filledCells);
        }
        return null;
    }

    /** Randomized Prim growth creates a connected tree and prunes leaves into empty cells. */
    private int[] carveRandomGrowthTree(Random random, int terminalLimit) {
        int[] masks = new int[cellCount];
        boolean[] connected = new boolean[cellCount];
        List<Edge> frontier = new ArrayList<>();
        int initial = random.nextInt(cellCount);
        connected[initial] = true;
        addFrontier(initial, connected, frontier);
        int connectedCount = 1;
        while (connectedCount < cellCount && !frontier.isEmpty()) {
            Edge edge = frontier.remove(random.nextInt(frontier.size()));
            if (connected[edge.to()]) {
                continue;
            }
            masks[edge.from()] |= edge.direction();
            masks[edge.to()] |= opposite(edge.direction());
            connected[edge.to()] = true;
            connectedCount++;
            addFrontier(edge.to(), connected, frontier);
        }
        int minimumActive = Math.max(terminalLimitForActiveCells(terminalLimit),
                Math.max(minimumRouteCells, minimumFilledCells));
        int maximumActive = cellCount;
        minimumActive = Math.min(minimumActive, maximumActive);
        int targetActive = minimumActive;
        if (maximumActive > minimumActive) {
            targetActive += random.nextInt(maximumActive - minimumActive + 1);
        }
        pruneLeaves(masks, targetActive, random);
        return masks;
    }

    private int terminalLimitForActiveCells(int terminalLimit) {
        return Math.min(Math.max(1, terminalLimit) + 1, cellCount);
    }

    /** Removes leaf nodes while keeping the remaining topology connected. */
    private void pruneLeaves(int[] masks, int targetActive, Random random) {
        int active = 0;
        for (int mask : masks) {
            if (mask != 0) {
                active++;
            }
        }
        while (active > targetActive) {
            List<Integer> leaves = new ArrayList<>();
            for (int index = 0; index < masks.length; index++) {
                if (Integer.bitCount(masks[index]) == 1) {
                    leaves.add(index);
                }
            }
            if (leaves.isEmpty()) {
                return;
            }

            int leaf = leaves.get(random.nextInt(leaves.size()));
            int direction = masks[leaf];
            int neighbor = neighborIndex(leaf, direction);
            if (neighbor < 0) {
                return;
            }
            masks[leaf] = 0;
            masks[neighbor] &= ~opposite(direction);
            active--;
        }
    }

    private void addFrontier(int current, boolean[] connected, List<Edge> frontier) {
        int x = current % width;
        int y = current / width;
        if (inside(x, y - 1) && !connected[index(x, y - 1)]) {
            frontier.add(new Edge(current, index(x, y - 1), 1));
        }
        if (inside(x + 1, y) && !connected[index(x + 1, y)]) {
            frontier.add(new Edge(current, index(x + 1, y), 2));
        }
        if (inside(x, y + 1) && !connected[index(x, y + 1)]) {
            frontier.add(new Edge(current, index(x, y + 1), 4));
        }
        if (inside(x - 1, y) && !connected[index(x - 1, y)]) {
            frontier.add(new Edge(current, index(x - 1, y), 8));
        }
    }

    private int chooseStart(List<Integer> leaves, Random random) {
        List<Integer> borderLeaves = leaves.stream()
                .filter(value -> value % width == 0)
                .toList();
        List<Integer> pool = borderLeaves.isEmpty() ? leaves : borderLeaves;
        return pool.get(random.nextInt(pool.size()));
    }

    private int[] chooseTerminals(int[] masks, List<Integer> leaves, int start,
                                  int terminalLimit, Random random) {
        List<Integer> available = new ArrayList<>(leaves);
        available.removeIf(value -> value == start);
        if (available.size() < terminalLimit) {
            return null;
        }

        List<Integer> selected = new ArrayList<>();
        int preferredDistance = terminalLimit == 1
                ? Math.max(2, minimumRouteCells - 1)
                : Math.max(3, minimumRouteCells / (terminalLimit + 1));
        for (int count = 0; count < terminalLimit; count++) {
            List<Integer> separated = available.stream()
                    .filter(value -> treeDistance(masks, value, start) >= preferredDistance
                            && selected.stream().allMatch(other ->
                            treeDistance(masks, value, other) >= Math.max(2,
                                    preferredDistance)))
                    .toList();
            List<Integer> pool = separated.isEmpty() ? available : separated;
            if (count == 0) {
                List<Integer> rightBorder = pool.stream()
                        .filter(value -> value % width == width - 1)
                        .toList();
                if (!rightBorder.isEmpty()) {
                    pool = rightBorder;
                }
            }
            int best = pool.get(0);
            int bestScore = Integer.MIN_VALUE;
            for (int candidate : pool) {
                int nearest = treeDistance(masks, candidate, start);
                for (int other : selected) {
                    nearest = Math.min(nearest, treeDistance(masks, candidate, other));
                }
                int score = nearest * 100 + random.nextInt(8);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            int selectedTerminal = best;
            selected.add(selectedTerminal);
            available.removeIf(value -> value == selectedTerminal);
        }
        return selected.stream().mapToInt(Integer::intValue).toArray();
    }

    private Candidate buildCandidate(Topology topology, Random random) {
        PipeType[] generatedTypes = new PipeType[cellCount];
        int[] desiredRotations = new int[cellCount];
        int[] scrambledRotations = new int[cellCount];
        for (int current = 0; current < cellCount; current++) {
            PipeType type = generatedPipeType(topology, current);
            generatedTypes[current] = type;
            desiredRotations[current] = type == PipeType.EMPTY
                    ? 0 : findRotation(type, topology.masks()[current]);
            scrambledRotations[current] = desiredRotations[current];
        }
        scrambleForDifficulty(generatedTypes, desiredRotations, scrambledRotations,
                topology, random);

        TerminalAssessment assessment = assessCandidate(generatedTypes,
                scrambledRotations, desiredRotations, topology);
        PipeSolveResult result = assessment.result();
        if (!result.solvable()) {
            return null;
        }
        int actualMinimum = result.minimumRotations();
        long penalty = difficultyPenalty(actualMinimum, topology.routeCells(),
                topology.filledCells(),
                topology.junctions(), topology.deadEnds(),
                assessment.guaranteedMinimum(), topology.terminals().length);
        GeneratedNetwork network = new GeneratedNetwork(generatedTypes, scrambledRotations,
                result.rotations(), topology.start(), topology.terminals(), result,
                topology.routeCells(), assessment.guaranteedMinimum());
        return new Candidate(network, actualMinimum, topology.routeCells(),
                topology.filledCells(),
                topology.junctions(), topology.deadEnds(),
                assessment.guaranteedMinimum(), penalty);
    }

    private TerminalAssessment assessCandidate(PipeType[] generatedTypes,
                                                int[] scrambledRotations,
                                                int[] desiredRotations,
                                                Topology topology) {
        if (requireAllConnected || forbidLeaks || topology.terminals().length == 1) {
            PipeSolveResult exact = PipeSolver.solve(generatedTypes, scrambledRotations,
                    width, height, topology.start(), topology.terminals(),
                    requireAllConnected, forbidLeaks);
            return new TerminalAssessment(exact,
                    exact.solvable() ? exact.minimumRotations() : -1);
        }

        int guaranteedMinimum = 0;
        long visited = 0;
        long branches = 0;
        long backtracks = 0;
        for (int terminal : topology.terminals()) {
            PipeSolveResult route = PipeSolver.solve(generatedTypes, scrambledRotations,
                    width, height, topology.start(), new int[]{terminal}, false, false);
            if (!route.solvable()) {
                return new TerminalAssessment(PipeSolveResult.unsolvable(
                        visited + route.visitedNodes(), branches + route.branchNodes(),
                        backtracks + route.backtracks()), -1);
            }
            guaranteedMinimum = Math.max(guaranteedMinimum, route.minimumRotations());
            visited += route.visitedNodes();
            branches += route.branchNodes();
            backtracks += route.backtracks();
        }

        boolean[] routeCells = requiredRouteCells(topology.masks(),
                topology.start(), topology.terminals());
        int expectedRotations = 0;
        int[] sharedSolution = scrambledRotations.clone();
        for (int index = 0; index < cellCount; index++) {
            if (routeCells[index]) {
                expectedRotations += rotationDistance(generatedTypes[index],
                        scrambledRotations[index], desiredRotations[index]);
                sharedSolution[index] = desiredRotations[index];
            }
        }
        PipeSolveResult result = new PipeSolveResult(true, expectedRotations, 1,
                visited, branches, backtracks, sharedSolution);
        return new TerminalAssessment(result, guaranteedMinimum);
    }

    /**
     * Scrambles the meaningful route towards the configured rotation band.
     * Decorative cells remain random, but can no longer make an easy lock
     * arbitrarily difficult merely because the first random candidate won.
     */
    private void scrambleForDifficulty(PipeType[] types, int[] desired, int[] current,
                                       Topology topology, Random random) {
        boolean[] requiredRoute = requiredRouteCells(topology.masks(),
                topology.start(), topology.terminals());
        boolean strictNetwork = requireAllConnected || forbidLeaks;
        List<Integer> difficultyCells = new ArrayList<>();
        int capacity = 0;
        for (int index = 0; index < cellCount; index++) {
            if (!isEndpoint(topology, index) && types[index].rotatable()
                    && (strictNetwork || requiredRoute[index])) {
                difficultyCells.add(index);
                capacity += maximumRotationDistance(types[index]);
            } else if (!isEndpoint(topology, index) && types[index].rotatable()) {
                current[index] = random.nextInt(types[index].uniqueRotationCount());
            }
        }

        if (difficultyCells.isEmpty()) {
            return;
        }

        int lower = Math.min(capacity, Math.max(1, configuredMinimumTurns));
        int upper = Math.min(capacity, configuredMaximumTurns);
        if (configuredMaximumTurns >= 1000) {
            upper = Math.min(upper, lower + Math.max(3, lower / 2));
        }
        upper = Math.max(lower, upper);
        int target = lower + (upper > lower ? random.nextInt(upper - lower + 1) : 0);

        int farthestTerminal = topology.terminals()[0];
        int farthestDistance = -1;
        for (int terminal : topology.terminals()) {
            int distance = treeDistance(topology.masks(), topology.start(), terminal);
            if (distance > farthestDistance) {
                farthestDistance = distance;
                farthestTerminal = terminal;
            }
        }
        boolean[] primaryRoute = requiredRouteCells(topology.masks(), topology.start(),
                new int[]{farthestTerminal});
        List<Integer> primaryCells = difficultyCells.stream()
                .filter(index -> primaryRoute[index])
                .toList();
        int[] assignedDistances = new int[cellCount];
        int primaryBudget = Math.min(target,
                independentRouteFloor(topology.terminals().length));
        int assigned = allocateRotationBudget(primaryCells, primaryBudget,
                types, assignedDistances, random);
        allocateRotationBudget(difficultyCells, target - assigned,
                types, assignedDistances, random);

        for (int index : difficultyCells) {
            int distance = assignedDistances[index];
            if (distance > 0) {
                int sign = distance * 2 == types[index].uniqueRotationCount()
                        ? 1 : (random.nextBoolean() ? 1 : -1);
                current[index] = Math.floorMod(desired[index] + sign * distance,
                        types[index].uniqueRotationCount());
            }
        }
    }

    private int allocateRotationBudget(List<Integer> cells, int budget,
                                       PipeType[] types, int[] assignedDistances,
                                       Random random) {
        if (budget <= 0 || cells.isEmpty()) {
            return 0;
        }
        List<Integer> shuffled = new ArrayList<>(cells);
        Collections.shuffle(shuffled, random);
        int remainingCapacity = 0;
        for (int index : shuffled) {
            remainingCapacity += maximumRotationDistance(types[index])
                    - assignedDistances[index];
        }
        int remaining = Math.min(budget, remainingCapacity);
        int target = remaining;
        for (int index : shuffled) {
            int cellCapacity = maximumRotationDistance(types[index])
                    - assignedDistances[index];
            int minimumHere = Math.max(0, remaining - (remainingCapacity - cellCapacity));
            int maximumHere = Math.min(cellCapacity, remaining);
            int distance = minimumHere;
            if (maximumHere > minimumHere) {
                distance += random.nextInt(maximumHere - minimumHere + 1);
            }
            assignedDistances[index] += distance;
            remaining -= distance;
            remainingCapacity -= cellCapacity;
        }
        return target - remaining;
    }

    private long difficultyPenalty(int actualMinimum, int routeCells, int filledCells,
                                   int junctions, int deadEnds,
                                   int guaranteedMinimum, int terminalCount) {
        long penalty = 0;
        penalty += Math.max(0, configuredMinimumTurns - actualMinimum) * 100L;
        penalty += Math.max(0, actualMinimum - configuredMaximumTurns) * 100L;
        penalty += Math.max(0, minimumRouteCells - routeCells) * 40L;
        penalty += Math.max(0, minimumFilledCells - filledCells) * 30L;
        penalty += Math.max(0, minimumJunctions - junctions) * 20L;
        penalty += Math.max(0, minimumDeadEnds - deadEnds) * 20L;
        penalty += Math.max(0, independentRouteFloor(terminalCount) - guaranteedMinimum) * 50L;
        if (actualMinimum <= 0) {
            penalty += 100_000L;
        }
        return penalty;
    }

    private boolean matchesDifficulty(Candidate candidate) {
        return candidate.actualMinimum() >= configuredMinimumTurns
                && candidate.actualMinimum() <= configuredMaximumTurns
                && candidate.routeCells() >= minimumRouteCells
                && candidate.filledCells() >= minimumFilledCells
                && candidate.junctions() >= minimumJunctions
                && candidate.deadEnds() >= minimumDeadEnds
                && candidate.guaranteedMinimum() >= independentRouteFloor(
                candidate.network().terminals().length);
    }

    private int independentRouteFloor(int terminalCount) {
        return terminalCount == 1
                ? configuredMinimumTurns
                : Math.max(2, (configuredMinimumTurns + 2) / 3);
    }

    private GeneratedNetwork guaranteedFallback(Topology topology, Random random) {
        PipeType[] types = new PipeType[cellCount];
        int[] desired = new int[cellCount];
        int[] current = new int[cellCount];
        for (int index = 0; index < cellCount; index++) {
            types[index] = generatedPipeType(topology, index);
            desired[index] = types[index] == PipeType.EMPTY
                    ? 0 : findRotation(types[index], topology.masks()[index]);
            current[index] = types[index].fixed() || isEndpoint(topology, index)
                    ? desired[index] : random.nextInt(types[index].uniqueRotationCount());
        }
        scrambleForDifficulty(types, desired, current, topology, random);
        int cost = 0;
        for (int index = 0; index < cellCount; index++) {
            cost += rotationDistance(types[index], current[index], desired[index]);
        }
        PipeSolveResult result = new PipeSolveResult(true, cost, 1, 0, 0, 0, desired);
        return new GeneratedNetwork(types, current, desired,
                topology.start(), topology.terminals(), result, topology.routeCells(), 0);
    }

    private Topology fallbackTopology(int terminalLimit) {
        int[] masks = new int[cellCount];
        // A comb: every row is a branch and the left column is the spine.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width - 1; x++) {
                int current = index(x, y);
                masks[current] |= 2;
                masks[current + 1] |= 8;
            }
        }
        for (int y = 0; y < height - 1; y++) {
            int current = index(0, y);
            masks[current] |= 4;
            masks[current + width] |= 1;
        }
        int minimumActive = Math.max(Math.max(terminalLimit + 1, minimumRouteCells),
                Math.max(8, (cellCount * 2 + 2) / 3));
        int targetActive = Math.min(cellCount,
                Math.max(minimumActive, cellCount - 1));
        pruneLeaves(masks, targetActive, new Random(0xC0B1L));
        List<Integer> leaves = new ArrayList<>();
        for (int current = 0; current < cellCount; current++) {
            if (Integer.bitCount(masks[current]) == 1) {
                leaves.add(current);
            }
        }
        int start = chooseStart(leaves, new Random(0xC0B1L));
        int count = Math.min(terminalLimit, Math.max(1, leaves.size() - 1));
        int[] terminals = chooseTerminals(masks, leaves, start, count,
                new Random(0xC0B1L));
        return new Topology(masks, start, terminals,
                countJunctions(masks), countDeadEnds(masks),
                countRequiredRouteCells(masks, start, terminals),
                countFilledCells(masks));
    }

    private PipeType generatedPipeType(Topology topology, int current) {
        /*
         * The tile shape must be derived from the same mask as its
         * neighbours.  The old relaxed-route code replaced non-terminal
         * END_CAP tiles with random elbows/straight/T pieces.  That changed
         * the tile's actual ports without changing the topology, so a
         * neighbour could still point at the old one-port tile and the two
         * sides would visibly fail to meet.  Route-only completion already
         * ignores decorative branches; there is no reason to corrupt their
         * geometry.  Keeping the original shape also means every generated
         * solution is a reciprocal graph, regardless of difficulty.
         */
        return pipeTypeFor(topology.masks()[current]);
    }

    private int countFilledCells(int[] masks) {
        int count = 0;
        for (int mask : masks) {
            if (mask != 0) {
                count++;
            }
        }
        return count;
    }

    private int countRequiredRouteCells(int[] masks, int start, int[] terminals) {
        boolean[] required = requiredRouteCells(masks, start, terminals);
        int count = 0;
        for (boolean value : required) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    /** Marks the union of the unique tree paths from the start to every terminal. */
    private boolean[] requiredRouteCells(int[] masks, int start, int[] terminals) {
        int[] parents = new int[cellCount];
        java.util.Arrays.fill(parents, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        parents[start] = start;
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int direction : new int[]{1, 2, 4, 8}) {
                if ((masks[current] & direction) == 0) {
                    continue;
                }
                int next = neighborIndex(current, direction);
                if (next >= 0 && parents[next] < 0) {
                    parents[next] = current;
                    queue.addLast(next);
                }
            }
        }

        boolean[] required = new boolean[cellCount];
        required[start] = true;
        for (int terminal : terminals) {
            for (int current = terminal; current >= 0 && !required[current];
                 current = parents[current]) {
                required[current] = true;
                if (current == start || parents[current] == current) {
                    break;
                }
            }
        }
        return required;
    }

    private int treeDistance(int[] masks, int first, int second) {
        int[] distances = new int[cellCount];
        java.util.Arrays.fill(distances, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        distances[first] = 0;
        queue.add(first);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            if (current == second) {
                return distances[current];
            }
            for (int direction : new int[]{1, 2, 4, 8}) {
                if ((masks[current] & direction) == 0) {
                    continue;
                }
                int next = neighborIndex(current, direction);
                if (next >= 0 && distances[next] < 0) {
                    distances[next] = distances[current] + 1;
                    queue.addLast(next);
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    private int countJunctions(int[] masks) {
        int count = 0;
        for (int mask : masks) {
            if (Integer.bitCount(mask) >= 3) {
                count++;
            }
        }
        return count;
    }

    private int countDeadEnds(int[] masks) {
        int count = 0;
        for (int mask : masks) {
            if (Integer.bitCount(mask) == 1) {
                count++;
            }
        }
        return count;
    }

    private static PipeType pipeTypeFor(int mask) {
        return switch (Integer.bitCount(mask)) {
            case 0 -> PipeType.EMPTY;
            case 1 -> PipeType.END_CAP;
            case 2 -> (mask == 5 || mask == 10) ? PipeType.STRAIGHT : PipeType.ELBOW;
            case 3 -> PipeType.T_JUNCTION;
            case 4 -> PipeType.CROSS;
            default -> throw new IllegalArgumentException("Pipe graph contains an empty cell");
        };
    }

    private static int findRotation(PipeType type, int desiredMask) {
        for (int rotation = 0; rotation < 4; rotation++) {
            if (type.mask(rotation) == desiredMask) {
                return rotation;
            }
        }
        return 0;
    }

    private static int rotationDistance(PipeType type, int current, int target) {
        int stateCount = type.uniqueRotationCount();
        int currentState = Math.floorMod(current, stateCount);
        int targetState = Math.floorMod(target, stateCount);
        int clockwise = Math.floorMod(targetState - currentState, stateCount);
        int counterClockwise = Math.floorMod(currentState - targetState, stateCount);
        return Math.min(clockwise, counterClockwise);
    }

    private static int maximumRotationDistance(PipeType type) {
        return type.uniqueRotationCount() / 2;
    }

    private boolean isEndpoint(Topology topology, int index) {
        if (topology.start() == index) {
            return true;
        }
        for (int terminal : topology.terminals()) {
            if (terminal == index) {
                return true;
            }
        }
        return false;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private int neighborIndex(int current, int direction) {
        int x = current % width;
        int y = current / width;
        int nextX = x + (direction == 2 ? 1 : direction == 8 ? -1 : 0);
        int nextY = y + (direction == 4 ? 1 : direction == 1 ? -1 : 0);
        return inside(nextX, nextY) ? index(nextX, nextY) : -1;
    }

    private int index(int x, int y) {
        return y * width + x;
    }

    private static int opposite(int bit) {
        return switch (bit) {
            case 1 -> 4;
            case 2 -> 8;
            case 4 -> 1;
            default -> 2;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Edge(int from, int to, int direction) {
    }

    private record Topology(int[] masks, int start, int[] terminals,
                            int junctions, int deadEnds, int routeCells,
                            int filledCells) {
        private Topology {
            masks = masks.clone();
            terminals = terminals.clone();
        }
    }

    private record Candidate(GeneratedNetwork network, int actualMinimum,
                             int routeCells, int filledCells, int junctions, int deadEnds,
                             int guaranteedMinimum, long penalty) {
    }

    private record TerminalAssessment(PipeSolveResult result, int guaranteedMinimum) {
    }

    private record GeneratedNetwork(PipeType[] types, int[] rotations,
                                    int[] solutionRotations, int startIndex,
                                    int[] terminals, PipeSolveResult solveResult,
                                    int routeCells,
                                    int guaranteedMinimumRouteRotations) {
        private GeneratedNetwork {
            types = types.clone();
            rotations = rotations.clone();
            solutionRotations = solutionRotations.clone();
            terminals = terminals.clone();
        }
    }
}

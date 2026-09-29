package com.kaleblangley.lockscontinue.lock.puzzle;

/**
 * Result returned by the exact pipe-board solver.
 *
 * <p>{@code solutionCount} is capped at two.  The caller only needs to know
 * whether the board has no solution, one solution, or multiple solutions, and
 * keeping the count bounded prevents a highly symmetric board from producing
 * an unnecessarily large number.</p>
 */
public record PipeSolveResult(
        boolean solvable,
        int minimumRotations,
        int solutionCount,
        long visitedNodes,
        long branchNodes,
        long backtracks,
        int[] rotations
) {
    public PipeSolveResult {
        rotations = rotations == null ? new int[0] : rotations.clone();
    }

    public static PipeSolveResult unsolvable(long visitedNodes,
                                             long branchNodes,
                                             long backtracks) {
        return new PipeSolveResult(false, -1, 0, visitedNodes,
                branchNodes, backtracks, new int[0]);
    }
}

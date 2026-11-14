package com.kaleblangley.lockscontinue.lock.definition;

/** Resolves the pipe game's deadline independently of its reward grading metric. */
public final class PipeTimeLimit {
    public static final int DEFAULT_TICKS = 1200;
    public static final int MIN_TICKS = 20;
    public static final int MAX_TICKS = 7200;

    private PipeTimeLimit() {
    }

    public static int validate(int ticks) {
        if (ticks != -1 && (ticks < MIN_TICKS || ticks > MAX_TICKS)) {
            throw new IllegalArgumentException("pipe.time_limit_ticks must be -1 or between 20 and 7200");
        }
        return ticks;
    }

    /** Affixes can modify the limit, with -1 remaining the explicit unlimited value. */
    public static int normalize(double ticks) {
        if (!Double.isFinite(ticks)) {
            return DEFAULT_TICKS;
        }
        long rounded = Math.round(ticks);
        return rounded == -1L ? -1 : (int) Math.max(MIN_TICKS, Math.min(MAX_TICKS, rounded));
    }

    public static int resolve(int difficultyTicks, RewardMetric rewardMetric, int rewardFailureAt) {
        int rewardTicks = rewardMetric == RewardMetric.TIME_TICKS && rewardFailureAt > 0
                ? rewardFailureAt : -1;
        if (difficultyTicks < 0) {
            return rewardTicks;
        }
        return rewardTicks < 0 ? difficultyTicks : Math.min(difficultyTicks, rewardTicks);
    }

    /** -1 means unlimited; zero means the deadline has been reached. */
    public static int remainingTicks(int limitTicks, long elapsedTicks) {
        return limitTicks < 0 ? -1 : (int) Math.max(0L, limitTicks - Math.max(0L, elapsedTicks));
    }

    public static boolean reached(int limitTicks, RewardAttemptState attempt, long gameTime) {
        return limitTicks >= 0 && attempt.metricStarted()
                && attempt.elapsedTicks(gameTime) >= limitTicks;
    }
}

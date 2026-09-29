package com.kaleblangley.lockscontinue.lock.definition;

/** Resolved tool assistance values kept separate from puzzle implementations. */
public record LockpickStats(
        int strength,
        int precision,
        int stability,
        int insight,
        int speed,
        float durabilityMultiplier
) {
    public static final LockpickStats EMPTY = new LockpickStats(0, 0, 0, 0, 0, 1.0F);

    public LockpickStats {
        strength = Math.max(0, strength);
        precision = Math.max(0, precision);
        stability = Math.max(0, stability);
        insight = Math.max(0, insight);
        speed = Math.max(0, speed);
        durabilityMultiplier = Float.isFinite(durabilityMultiplier)
                ? Math.max(0.0F, durabilityMultiplier) : 1.0F;
    }

    public LockpickStats add(LockpickStats other) {
        return new LockpickStats(
                strength + other.strength,
                precision + other.precision,
                stability + other.stability,
                insight + other.insight,
                speed + other.speed,
                durabilityMultiplier * other.durabilityMultiplier
        );
    }
}

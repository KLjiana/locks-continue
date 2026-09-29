package com.kaleblangley.lockscontinue.lock.definition;

import com.kaleblangley.lockscontinue.lock.LockInstance;
import com.kaleblangley.lockscontinue.lock.LockType;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-resolved rules for one lock session.
 *
 * <p>The context is deliberately independent from the individual games. A
 * future integration can resolve another source of tool stats or affixes and
 * still hand the games the same immutable, already-merged values.</p>
 */
public record ResolvedLockContext(
        LockInstance instance,
        ResourceLocation difficultyId,
        int difficultyRank,
        int displayColor,
        int mistakesAllowed,
        int durabilityCost,
        int pinToleranceTicks,
        int pinMinHoldTicks,
        int pinResetLockedPins,
        int pipeMinimumTurns,
        int pipeHintCount,
        float rotaryToleranceDegrees,
        float rotaryStressMultiplier,
        int memoryPreviewTicks,
        int memoryMistakes,
        int memoryHintCount,
        LockpickStats toolStats,
        List<ResourceLocation> affixes,
        Map<String, Double> numericValues,
        Set<String> flags
) {
    public ResolvedLockContext {
        toolStats = toolStats == null ? LockpickStats.EMPTY : toolStats;
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        numericValues = numericValues == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(numericValues));
        flags = flags == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(flags));
    }

    public int mistakesFor(LockType type) {
        return type == LockType.MEMORY_LOCK
                ? (int) Math.max(1, Math.round(numeric("memory.mismatch_limit", memoryMistakes)))
                : mistakesAllowed;
    }

    public double numeric(String key, double fallback) {
        return numericValues.getOrDefault(key, fallback);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }
}

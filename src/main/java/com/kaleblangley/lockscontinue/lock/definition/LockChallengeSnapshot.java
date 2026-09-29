package com.kaleblangley.lockscontinue.lock.definition;

import com.kaleblangley.lockscontinue.lock.LockInstance;
import com.kaleblangley.lockscontinue.lock.LockType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Immutable server-resolved data sent to a lock GUI for one session. */
public record LockChallengeSnapshot(
        ResourceLocation difficultyId,
        int difficultyRank,
        int displayColor,
        int mistakesAllowed,
        int durabilityCost,
        int pinCount,
        int pinToleranceTicks,
        int pinMinHoldTicks,
        int pinResetLockedPins,
        float pinSpeedVariance,
        int pipeWidth,
        int pipeHeight,
        int pipeTerminalCount,
        int pipeMinimumTurns,
        int pipeMaximumTurns,
        int pipeMinimumRouteCells,
        int pipeMinimumFilledCells,
        int pipeMinimumJunctions,
        int pipeMinimumDeadEnds,
        boolean pipeRequireAllConnected,
        boolean pipeForbidLeaks,
        int pipeHintCount,
        int pipeTimeLimitTicks,
        float rotaryToleranceDegrees,
        float rotaryFeedbackFalloff,
        float rotaryStressMultiplier,
        int memoryRows,
        int memoryColumns,
        int memoryPreviewTicks,
        int memoryPreviewTicksPerPair,
        int memoryMistakes,
        int memoryMismatchLimit,
        int memoryHintCount,
        List<String> flags,
        LockpickStats toolStats,
        List<ResourceLocation> affixes
) {
    public LockChallengeSnapshot {
        flags = flags == null ? List.of() : List.copyOf(flags);
        toolStats = toolStats == null ? LockpickStats.EMPTY : toolStats;
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
    }

    public static LockChallengeSnapshot from(LockInstance instance,
                                             LockDefinitionManager definitions,
                                             ItemStack tool) {
        return from(definitions.resolve(instance, tool));
    }

    public static LockChallengeSnapshot from(ResolvedLockContext context) {
        int memoryRows = intValue(context, "memory.rows", 4, 2, 8);
        int memoryColumns = intValue(context, "memory.columns", 4, 2, 8);
        if ((memoryRows * memoryColumns) % 2 != 0) {
            // Affixes are allowed to adjust either dimension, but a malformed
            // combination must never make the server fail while opening a GUI.
            if (memoryColumns < 8) {
                memoryColumns++;
            } else if (memoryRows < 8) {
                memoryRows++;
            } else {
                memoryColumns--;
            }
        }
        int pipeMinimumTurns = intValue(context, "pipe.minimum_solution_rotations",
                intValue(context, "pipe.minimum_turns", context.pipeMinimumTurns(), 0, 1000),
                0, 1000);
        int pipeMaximumTurns = Math.max(Math.max(1, pipeMinimumTurns),
                intValue(context, "pipe.maximum_solution_rotations", 1000, 1, 1000));
        return new LockChallengeSnapshot(
                context.difficultyId(),
                context.difficultyRank(),
                context.displayColor(),
                context.mistakesAllowed(),
                context.durabilityCost(),
                intValue(context, "pin.count", 5, 3, 10),
                intValue(context, "pin.set_tolerance_ticks", context.pinToleranceTicks(), 0, 100),
                intValue(context, "pin.apex_hold_ticks", context.pinMinHoldTicks(), 0, 100),
                intValue(context, "pin.reset_locked_pins", context.pinResetLockedPins(), 0, 10),
                (float) Math.max(0.1D, Math.min(10.0D,
                        context.numeric("pin.speed_variance", 1.0D))),
                intValue(context, "pipe.width", 5, 3, 12),
                intValue(context, "pipe.height", 5, 3, 12),
                intValue(context, "pipe.terminal_count", 1, 1, 16),
                pipeMinimumTurns,
                pipeMaximumTurns,
                intValue(context, "pipe.minimum_route_cells", 2, 2, 64),
                intValue(context, "pipe.minimum_filled_cells", 8, 2, 64),
                intValue(context, "pipe.minimum_junctions", 0, 0, 144),
                intValue(context, "pipe.minimum_dead_ends", 0, 0, 144),
                booleanValue(context, "pipe.require_all_connected", false),
                booleanValue(context, "pipe.forbid_leaks", false),
                intValue(context, "pipe.hint_count", context.pipeHintCount(), 0, 100),
                intValue(context, "pipe.time_limit_ticks", 1200, 20, 7200),
                (float) Math.max(0.0D, Math.min(180.0D,
                        context.numeric("rotary.sweet_spot_degrees", context.rotaryToleranceDegrees()))),
                (float) Math.max(0.01D, Math.min(100.0D,
                        context.numeric("rotary.feedback_falloff", 1.5D))),
                (float) Math.max(0.0D, Math.min(100.0D,
                        context.numeric("rotary.stress_multiplier", context.rotaryStressMultiplier()))),
                memoryRows,
                memoryColumns,
                intValue(context, "memory.preview_ticks", context.memoryPreviewTicks(), 0, 1200),
                intValue(context, "memory.preview_ticks_per_pair", 0, 0, 1200),
                intValue(context, "memory.mistakes", context.memoryMistakes(), 1, 100),
                intValue(context, "memory.mismatch_limit", context.memoryMistakes(), 1, 100),
                intValue(context, "memory.hint_count", context.memoryHintCount(), 0, 100),
                context.flags().stream().sorted().toList(),
                context.toolStats(),
                context.affixes()
        );
    }

    public int mistakesFor(LockType type) {
        return type == LockType.MEMORY_LOCK
                ? Math.max(1, memoryMismatchLimit > 0 ? memoryMismatchLimit : memoryMistakes)
                : mistakesAllowed;
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(difficultyId);
        buffer.writeVarInt(difficultyRank);
        buffer.writeInt(displayColor);
        buffer.writeVarInt(mistakesAllowed);
        buffer.writeVarInt(durabilityCost);
        buffer.writeVarInt(pinCount);
        buffer.writeVarInt(pinToleranceTicks);
        buffer.writeVarInt(pinMinHoldTicks);
        buffer.writeVarInt(pinResetLockedPins);
        buffer.writeFloat(pinSpeedVariance);
        buffer.writeVarInt(pipeWidth);
        buffer.writeVarInt(pipeHeight);
        buffer.writeVarInt(pipeTerminalCount);
        buffer.writeVarInt(pipeMinimumTurns);
        buffer.writeVarInt(pipeMaximumTurns);
        buffer.writeVarInt(pipeMinimumRouteCells);
        buffer.writeVarInt(pipeMinimumFilledCells);
        buffer.writeVarInt(pipeMinimumJunctions);
        buffer.writeVarInt(pipeMinimumDeadEnds);
        buffer.writeBoolean(pipeRequireAllConnected);
        buffer.writeBoolean(pipeForbidLeaks);
        buffer.writeVarInt(pipeHintCount);
        buffer.writeVarInt(pipeTimeLimitTicks);
        buffer.writeFloat(rotaryToleranceDegrees);
        buffer.writeFloat(rotaryFeedbackFalloff);
        buffer.writeFloat(rotaryStressMultiplier);
        buffer.writeVarInt(memoryRows);
        buffer.writeVarInt(memoryColumns);
        buffer.writeVarInt(memoryPreviewTicks);
        buffer.writeVarInt(memoryPreviewTicksPerPair);
        buffer.writeVarInt(memoryMistakes);
        buffer.writeVarInt(memoryMismatchLimit);
        buffer.writeVarInt(memoryHintCount);
        buffer.writeVarInt(Math.min(32, flags.size()));
        for (int i = 0; i < flags.size() && i < 32; i++) {
            buffer.writeUtf(flags.get(i), 128);
        }
        buffer.writeVarInt(toolStats.strength());
        buffer.writeVarInt(toolStats.precision());
        buffer.writeVarInt(toolStats.stability());
        buffer.writeVarInt(toolStats.insight());
        buffer.writeVarInt(toolStats.speed());
        buffer.writeFloat(toolStats.durabilityMultiplier());
        buffer.writeVarInt(Math.min(32, affixes.size()));
        for (int i = 0; i < affixes.size() && i < 32; i++) {
            buffer.writeResourceLocation(affixes.get(i));
        }
    }

    public static LockChallengeSnapshot decode(FriendlyByteBuf buffer) {
        ResourceLocation difficultyId = buffer.readResourceLocation();
        int difficultyRank = buffer.readVarInt();
        int displayColor = buffer.readInt();
        int mistakesAllowed = buffer.readVarInt();
        int durabilityCost = buffer.readVarInt();
        int pinCount = buffer.readVarInt();
        int pinToleranceTicks = buffer.readVarInt();
        int pinMinHoldTicks = buffer.readVarInt();
        int pinResetLockedPins = buffer.readVarInt();
        float pinSpeedVariance = buffer.readFloat();
        int pipeWidth = buffer.readVarInt();
        int pipeHeight = buffer.readVarInt();
        int pipeTerminalCount = buffer.readVarInt();
        int pipeMinimumTurns = buffer.readVarInt();
        int pipeMaximumTurns = buffer.readVarInt();
        int pipeMinimumRouteCells = buffer.readVarInt();
        int pipeMinimumFilledCells = buffer.readVarInt();
        int pipeMinimumJunctions = buffer.readVarInt();
        int pipeMinimumDeadEnds = buffer.readVarInt();
        boolean pipeRequireAllConnected = buffer.readBoolean();
        boolean pipeForbidLeaks = buffer.readBoolean();
        int pipeHintCount = buffer.readVarInt();
        int pipeTimeLimitTicks = buffer.readVarInt();
        float rotaryToleranceDegrees = buffer.readFloat();
        float rotaryFeedbackFalloff = buffer.readFloat();
        float rotaryStressMultiplier = buffer.readFloat();
        int memoryRows = buffer.readVarInt();
        int memoryColumns = buffer.readVarInt();
        int memoryPreviewTicks = buffer.readVarInt();
        int memoryPreviewTicksPerPair = buffer.readVarInt();
        int memoryMistakes = buffer.readVarInt();
        int memoryMismatchLimit = buffer.readVarInt();
        int memoryHintCount = buffer.readVarInt();
        int flagCount = buffer.readVarInt();
        if (flagCount < 0 || flagCount > 32) {
            throw new IllegalArgumentException("Invalid lock flag count: " + flagCount);
        }
        List<String> flags = new ArrayList<>(flagCount);
        for (int i = 0; i < flagCount; i++) {
            flags.add(buffer.readUtf(128));
        }
        LockpickStats toolStats = new LockpickStats(
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat());
        int affixCount = buffer.readVarInt();
        if (affixCount < 0 || affixCount > 32) {
            throw new IllegalArgumentException("Invalid lock affix count: " + affixCount);
        }
        List<ResourceLocation> affixes = new ArrayList<>(affixCount);
        for (int i = 0; i < affixCount; i++) {
            affixes.add(buffer.readResourceLocation());
        }
        return new LockChallengeSnapshot(
                difficultyId, difficultyRank, displayColor, mistakesAllowed, durabilityCost,
                pinCount, pinToleranceTicks, pinMinHoldTicks, pinResetLockedPins, pinSpeedVariance,
                pipeWidth, pipeHeight, pipeTerminalCount, pipeMinimumTurns,
                pipeMaximumTurns, pipeMinimumRouteCells, pipeMinimumFilledCells,
                pipeMinimumJunctions, pipeMinimumDeadEnds, pipeRequireAllConnected, pipeForbidLeaks,
                pipeHintCount, pipeTimeLimitTicks,
                rotaryToleranceDegrees, rotaryFeedbackFalloff, rotaryStressMultiplier,
                memoryRows, memoryColumns, memoryPreviewTicks, memoryPreviewTicksPerPair,
                memoryMistakes, memoryMismatchLimit, memoryHintCount,
                flags,
                toolStats, affixes);
    }

    private static int intValue(ResolvedLockContext context, String key,
                                int fallback, int min, int max) {
        double value = context.numeric(key, fallback);
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return (int) Math.max(min, Math.min(max, Math.round(value)));
    }

    private static boolean booleanValue(ResolvedLockContext context, String key, boolean fallback) {
        double value = context.numeric(key, fallback ? 1.0D : 0.0D);
        return Double.isFinite(value) ? value >= 0.5D : fallback;
    }
}

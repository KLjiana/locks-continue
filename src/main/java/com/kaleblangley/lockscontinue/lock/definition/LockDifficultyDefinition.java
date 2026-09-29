package com.kaleblangley.lockscontinue.lock.definition;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonObject;

/** Data-pack controlled rules for all four lock games. */
public final class LockDifficultyDefinition {
    private final ResourceLocation id;
    private final int rank;
    private final int displayColor;
    private final Failure failure;
    private final Pin pin;
    private final Pipe pipe;
    private final Rotary rotary;
    private final Memory memory;

    public LockDifficultyDefinition(ResourceLocation id, int rank, int displayColor,
                                    Failure failure, Pin pin, Pipe pipe,
                                    Rotary rotary, Memory memory) {
        this.id = id;
        this.rank = rank;
        this.displayColor = displayColor;
        this.failure = failure;
        this.pin = pin;
        this.pipe = pipe;
        this.rotary = rotary;
        this.memory = memory;
    }

    public ResourceLocation id() {
        return id;
    }

    public int rank() {
        return rank;
    }

    public int displayColor() {
        return displayColor;
    }

    public Failure failure() {
        return failure;
    }

    public Pin pin() {
        return pin;
    }

    public Pipe pipe() {
        return pipe;
    }

    public Rotary rotary() {
        return rotary;
    }

    public Memory memory() {
        return memory;
    }

    public static LockDifficultyDefinition fromJson(ResourceLocation id, JsonObject root) {
        JsonObject failure = object(root, "failure");
        JsonObject pin = object(root, "pin");
        JsonObject pipe = object(root, "pipe");
        JsonObject rotary = object(root, "rotary");
        JsonObject memory = object(root, "memory");
        int pipeWidth = integer(pipe, "width",
                integer(pipe, "size", 5, 3, 12), 3, 12);
        int pipeHeight = integer(pipe, "height",
                integer(pipe, "size", 5, 3, 12), 3, 12);
        int pipeMinimumRotations = integer(pipe, "minimum_solution_rotations",
                integer(pipe, "minimum_turns", 6, 0, 1000), 0, 1000);
        int pipeMaximumRotations = integer(pipe, "maximum_solution_rotations",
                1000, 1, 1000);
        if (pipeMaximumRotations < pipeMinimumRotations) {
            throw new IllegalArgumentException(
                    "maximum_solution_rotations must be at least minimum_solution_rotations");
        }
        int memoryRows = integer(memory, "rows", 4, 2, 8);
        int memoryColumns = integer(memory, "columns", 4, 2, 8);
        if ((memoryRows * memoryColumns) % 2 != 0) {
            throw new IllegalArgumentException("memory rows * columns must be even");
        }
        return new LockDifficultyDefinition(
                id,
                integer(root, "rank", 1, 0, 100),
                color(root, "display_color", 0xFFD8C8B0),
                new Failure(
                        integer(failure, "mistakes", 4, 1, 100),
                        integer(failure, "durability_cost", 1, 0, 100)),
                new Pin(
                        integer(pin, "pin_count", 5, 3, 10),
                        integer(pin, "set_tolerance_ticks", 2, 0, 100),
                        integer(pin, "apex_hold_ticks",
                                integer(pin, "min_hold_ticks", 1, 0, 100), 0, 100),
                        (float) decimal(pin, "speed_variance", 1.0D, 0.1D, 10.0D),
                        integer(pin, "reset_locked_pins", 5, 0, 10)),
                new Pipe(
                        pipeWidth,
                        pipeHeight,
                        integer(pipe, "terminal_count", 1, 1, 16),
                        pipeMinimumRotations,
                        pipeMaximumRotations,
                        integer(pipe, "minimum_route_cells",
                                Math.max(2, Math.max(pipeWidth, pipeHeight)),
                                2, pipeWidth * pipeHeight),
                        integer(pipe, "minimum_filled_cells",
                                Math.max(8, (pipeWidth * pipeHeight * 2 + 2) / 3),
                                2, pipeWidth * pipeHeight),
                        integer(pipe, "minimum_junctions", 0, 0, 144),
                        integer(pipe, "minimum_dead_ends", 0, 0, 144),
                        bool(pipe, "require_all_connected", false),
                        bool(pipe, "forbid_leaks", false),
                        integer(pipe, "hint_count", 0, 0, 100),
                        integer(pipe, "time_limit_ticks", 1200, 20, 7200)),
                new Rotary(
                        decimal(rotary, "sweet_spot_degrees",
                                decimal(rotary, "tolerance_degrees", 7.0D, 0.0D, 180.0D),
                                0.0D, 180.0D),
                        decimal(rotary, "feedback_falloff", 1.5D, 0.01D, 100.0D)),
                new Memory(
                        memoryRows,
                        memoryColumns,
                        integer(memory, "preview_ticks", 50, 0, 1200),
                        integer(memory, "preview_ticks_per_pair", 0, 0, 1200),
                        integer(memory, "mistakes", 4, 1, 100),
                        integer(memory, "mismatch_limit",
                                integer(memory, "mistakes", 4, 1, 100), 1, 100),
                        integer(memory, "hint_count", 0, 0, 100))
        );
    }

    public record Failure(int mistakes, int durabilityCost) {
    }

    public record Pin(int pinCount, int setToleranceTicks, int apexHoldTicks,
                      float speedVariance, int resetLockedPins) {
        /** Compatibility accessor for older code and data-pack terminology. */
        public int minHoldTicks() {
            return apexHoldTicks;
        }
    }

    public record Pipe(int width, int height, int terminalCount,
                       int minimumSolutionRotations, int maximumSolutionRotations,
                       int minimumRouteCells, int minimumFilledCells,
                       int minimumJunctions,
                       int minimumDeadEnds, boolean requireAllConnected,
                       boolean forbidLeaks, int hintCount, int timeLimitTicks) {
        /** Compatibility accessor for the earlier data-pack field name. */
        public int minimumTurns() {
            return minimumSolutionRotations;
        }
    }

    public record Rotary(double toleranceDegrees, double feedbackFalloff) {
    }

    public record Memory(int rows, int columns, int previewTicks,
                         int previewTicksPerPair, int mistakes, int mismatchLimit,
                         int hintCount) {
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static int integer(JsonObject object, String key, int fallback, int min, int max) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        int value = object.get(key).getAsInt();
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static double decimal(JsonObject object, String key, double fallback,
                                  double min, double max) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        double value = object.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return object.get(key).getAsBoolean();
    }

    private static int color(JsonObject object, String key, int fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        String value = object.get(key).getAsString();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6 && value.length() != 8) {
            throw new IllegalArgumentException(key + " must be #RRGGBB or #AARRGGBB");
        }
        long parsed = Long.parseLong(value, 16);
        return value.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
    }
}

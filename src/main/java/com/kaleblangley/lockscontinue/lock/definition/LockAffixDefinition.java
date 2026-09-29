package com.kaleblangley.lockscontinue.lock.definition;

import com.google.gson.JsonObject;
import com.kaleblangley.lockscontinue.lock.LockType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Data-pack definition for a numeric or flag-based lock affix. */
public final class LockAffixDefinition {
    private final ResourceLocation id;
    private final ResourceLocation pool;
    private final int weight;
    private final int minRank;
    private final int maxRank;
    private final List<ResourceLocation> lockTypes;
    private final List<ResourceLocation> conflicts;
    private final Map<String, Double> additions;
    private final Map<String, Double> multipliers;
    private final Map<String, Boolean> flags;

    public LockAffixDefinition(ResourceLocation id, ResourceLocation pool, int weight,
                               int minRank, int maxRank, List<ResourceLocation> lockTypes,
                               List<ResourceLocation> conflicts,
                               Map<String, Double> additions, Map<String, Double> multipliers,
                               Map<String, Boolean> flags) {
        this.id = id;
        this.pool = pool;
        this.weight = Math.max(0, weight);
        this.minRank = Math.max(0, minRank);
        if (maxRank < minRank) {
            throw new IllegalArgumentException("max_rank must be >= min_rank");
        }
        this.maxRank = maxRank;
        this.lockTypes = List.copyOf(lockTypes);
        this.conflicts = List.copyOf(conflicts);
        this.additions = Collections.unmodifiableMap(new HashMap<>(additions));
        this.multipliers = Collections.unmodifiableMap(new HashMap<>(multipliers));
        this.flags = Collections.unmodifiableMap(new HashMap<>(flags));
    }

    public ResourceLocation id() {
        return id;
    }

    public ResourceLocation pool() {
        return pool;
    }

    public int weight() {
        return weight;
    }

    public boolean appliesTo(int rank) {
        return rank >= minRank && rank <= maxRank;
    }

    /** An empty lock-type list means the affix is valid for every lock. */
    public boolean appliesTo(LockType type, int rank) {
        if (type == null || !appliesTo(rank)) {
            return false;
        }
        return lockTypes.isEmpty()
                || lockTypes.contains(type.resourceId())
                || lockTypes.contains(new ResourceLocation(type.resourceId().getNamespace(), type.id()));
    }

    public List<ResourceLocation> lockTypes() {
        return lockTypes;
    }

    public List<ResourceLocation> conflicts() {
        return conflicts;
    }

    public Map<String, Double> additions() {
        return additions;
    }

    public Map<String, Double> multipliers() {
        return multipliers;
    }

    public Map<String, Boolean> flags() {
        return flags;
    }

    public static LockAffixDefinition fromJson(ResourceLocation id, JsonObject root) {
        ResourceLocation pool = resource(root, "pool", new ResourceLocation(id.getNamespace(), "default"));
        List<ResourceLocation> lockTypes = resources(root, "lock_types");
        List<ResourceLocation> conflicts = resources(root, "conflicts");
        Map<String, Double> additions = numbers(root, "add");
        Map<String, Double> multipliers = numbers(root, "multiply");
        Map<String, Boolean> flags = booleans(root, "flags");
        return new LockAffixDefinition(
                id,
                pool,
                integer(root, "weight", 1, 0, 100000),
                integer(root, "min_rank", 0, 0, 100),
                integer(root, "max_rank", 100, 0, 100),
                lockTypes,
                conflicts,
                additions,
                multipliers,
                flags
        );
    }

    private static ResourceLocation resource(JsonObject root, String key, ResourceLocation fallback) {
        if (!root.has(key)) {
            return fallback;
        }
        ResourceLocation value = ResourceLocation.tryParse(root.get(key).getAsString());
        if (value == null) {
            throw new IllegalArgumentException(key + " must be a valid resource location");
        }
        return value;
    }

    private static List<ResourceLocation> resources(JsonObject root, String key) {
        List<ResourceLocation> result = new ArrayList<>();
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            return result;
        }
        root.getAsJsonArray(key).forEach(element -> {
            ResourceLocation value = ResourceLocation.tryParse(element.getAsString());
            if (value == null) {
                throw new IllegalArgumentException(key + " contains an invalid resource location: "
                        + element);
            }
            result.add(value);
        });
        return result;
    }

    private static Map<String, Double> numbers(JsonObject root, String key) {
        Map<String, Double> result = new HashMap<>();
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return result;
        }
        root.getAsJsonObject(key).entrySet().forEach(entry -> {
            double value = entry.getValue().getAsDouble();
            if (!Double.isFinite(value) || Math.abs(value) > 100000.0D) {
                throw new IllegalArgumentException(key + "." + entry.getKey()
                        + " must be a finite number within +/-100000");
            }
            result.put(entry.getKey(), value);
        });
        return result;
    }

    private static Map<String, Boolean> booleans(JsonObject root, String key) {
        Map<String, Boolean> result = new HashMap<>();
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return result;
        }
        root.getAsJsonObject(key).entrySet().forEach(entry ->
                result.put(entry.getKey(), entry.getValue().getAsBoolean()));
        return result;
    }

    private static int integer(JsonObject object, String key, int fallback, int min, int max) {
        if (!object.has(key)) {
            return fallback;
        }
        int value = object.get(key).getAsInt();
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }
}

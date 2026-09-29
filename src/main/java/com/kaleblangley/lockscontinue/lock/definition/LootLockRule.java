package com.kaleblangley.lockscontinue.lock.definition;

import com.google.gson.JsonObject;
import com.kaleblangley.lockscontinue.lock.LockType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One priority-ordered rule for selecting a generated lock instance. */
public final class LootLockRule {
    private final ResourceLocation id;
    private final int priority;
    private final Match match;
    private final double lockChance;
    private final double affixChance;
    private final Map<ResourceLocation, Integer> lockTypes;
    private final Map<ResourceLocation, Integer> difficulties;
    private final int affixMin;
    private final int affixMax;
    private final ResourceLocation affixPool;
    private final ResourceLocation rewardProfile;

    public LootLockRule(ResourceLocation id, int priority, Match match, double lockChance,
                        double affixChance,
                        Map<ResourceLocation, Integer> lockTypes,
                        Map<ResourceLocation, Integer> difficulties,
                        int affixMin, int affixMax, ResourceLocation affixPool,
                        ResourceLocation rewardProfile) {
        this.id = id;
        this.priority = priority;
        this.match = match;
        this.lockChance = Math.max(0.0D, Math.min(1.0D, lockChance));
        this.affixChance = Math.max(0.0D, Math.min(1.0D, affixChance));
        this.lockTypes = Collections.unmodifiableMap(new LinkedHashMap<>(lockTypes));
        this.difficulties = Collections.unmodifiableMap(new LinkedHashMap<>(difficulties));
        this.affixMin = Math.max(0, affixMin);
        this.affixMax = Math.max(this.affixMin, affixMax);
        this.affixPool = affixPool;
        this.rewardProfile = rewardProfile;
    }

    /** Compatibility constructor for integrations written before rewards. */
    public LootLockRule(ResourceLocation id, int priority, Match match, double lockChance,
                        double affixChance,
                        Map<ResourceLocation, Integer> lockTypes,
                        Map<ResourceLocation, Integer> difficulties,
                        int affixMin, int affixMax, ResourceLocation affixPool) {
        this(id, priority, match, lockChance, affixChance, lockTypes, difficulties,
                affixMin, affixMax, affixPool, null);
    }

    public ResourceLocation id() {
        return id;
    }

    public int priority() {
        return priority;
    }

    public double lockChance() {
        return lockChance;
    }

    public double affixChance() {
        return affixChance;
    }

    public Map<ResourceLocation, Integer> lockTypes() {
        return lockTypes;
    }

    public Map<ResourceLocation, Integer> difficulties() {
        return difficulties;
    }

    public int affixMin() {
        return affixMin;
    }

    public int affixMax() {
        return affixMax;
    }

    public ResourceLocation affixPool() {
        return affixPool;
    }

    public ResourceLocation rewardProfile() {
        return rewardProfile;
    }

    public boolean matches(ResourceLocation lootTable, ResourceLocation dimension, BlockState state) {
        if (!match.lootTables().isEmpty()
                && (lootTable == null || match.lootTables().stream().noneMatch(pattern -> matchesPattern(lootTable, pattern)))) {
            return false;
        }
        if (!match.namespaces().isEmpty()
                && (lootTable == null || !match.namespaces().contains(lootTable.getNamespace()))) {
            return false;
        }
        if (!match.dimensions().isEmpty()
                && (dimension == null || !match.dimensions().contains(dimension.toString()))) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!match.containerBlocks().isEmpty()
                && !match.containerBlocks().contains(blockId.toString())) {
            return false;
        }
        if (!match.containerTags().isEmpty()
                && match.containerTags().stream().noneMatch(tag -> {
                    ResourceLocation tagId = ResourceLocation.tryParse(tag);
                    return tagId != null && state.is(TagKey.create(Registries.BLOCK, tagId));
                })) {
            return false;
        }
        return true;
    }

    public LockType chooseType(RandomSource random) {
        ResourceLocation selected = weighted(random, lockTypes, LockType.MEMORY_LOCK.resourceId());
        return LockType.fromResourceId(selected);
    }

    public ResourceLocation chooseDifficulty(RandomSource random,
                                             ResourceLocation fallback) {
        return weighted(random, difficulties, fallback);
    }

    public static LootLockRule fromJson(ResourceLocation id, JsonObject root) {
        JsonObject match = root.has("match") && root.get("match").isJsonObject()
                ? root.getAsJsonObject("match") : new JsonObject();
        JsonObject affixCount = root.has("affix_count") && root.get("affix_count").isJsonObject()
                ? root.getAsJsonObject("affix_count") : new JsonObject();
        int maximumAffixes = boundedInteger(root, "maximum_affixes", 0, 0, 100);
        ResourceLocation affixPool = root.has("affix_pool")
                ? ResourceLocation.tryParse(root.get("affix_pool").getAsString()) : null;
        ResourceLocation rewardProfile = root.has("reward_profile")
                ? ResourceLocation.tryParse(root.get("reward_profile").getAsString()) : null;
        int affixMin = boundedInteger(affixCount, "min", 0, 0, 100);
        int affixMax = affixCount.has("max")
                ? boundedInteger(affixCount, "max", 0, 0, 100) : maximumAffixes;
        if (affixMax < affixMin) {
            throw new IllegalArgumentException("affix_count.max must be >= min");
        }
        return new LootLockRule(
                id,
                integer(root, "priority", 0),
                new Match(
                        !strings(match, "loot_tables").isEmpty()
                                ? strings(match, "loot_tables") : strings(root, "loot_tables"),
                        strings(match, "namespaces"),
                        strings(match, "dimensions"),
                        strings(match, "container_blocks"),
                        strings(match, "container_tags")
                ),
                decimal(root, "lock_chance", 1.0D),
                decimal(root, "affix_chance", 1.0D),
                weights(root, "lock_types", "lock_type_weights"),
                weights(root, "difficulties", "difficulty_weights"),
                affixMin,
                affixMax,
                affixPool,
                rewardProfile
        );
    }

    public record Match(List<String> lootTables, List<String> namespaces,
                        List<String> dimensions, List<String> containerBlocks,
                        List<String> containerTags) {
        public Match {
            lootTables = List.copyOf(lootTables);
            namespaces = List.copyOf(namespaces);
            dimensions = List.copyOf(dimensions);
            containerBlocks = List.copyOf(containerBlocks);
            containerTags = List.copyOf(containerTags);
        }
    }

    private static ResourceLocation weighted(RandomSource random,
                                            Map<ResourceLocation, Integer> values,
                                            ResourceLocation fallback) {
        int total = values.values().stream().filter(value -> value > 0)
                .mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return fallback;
        }
        int pick = random.nextInt(total);
        for (Map.Entry<ResourceLocation, Integer> entry : values.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            pick -= entry.getValue();
            if (pick < 0) {
                return entry.getKey();
            }
        }
        return fallback;
    }

    private static boolean matchesPattern(ResourceLocation id, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }
        int separator = pattern.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : pattern.substring(0, separator);
        String path = separator < 0 ? pattern : pattern.substring(separator + 1);
        return (namespace.equals("*") || namespace.equals(id.getNamespace()))
                && (path.equals("*") || (path.endsWith("*")
                ? id.getPath().startsWith(path.substring(0, path.length() - 1))
                : path.equals(id.getPath())));
    }

    private static List<String> strings(JsonObject root, String key) {
        List<String> result = new ArrayList<>();
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            return result;
        }
        root.getAsJsonArray(key).forEach(element -> result.add(element.getAsString()));
        return result;
    }

    private static Map<ResourceLocation, Integer> weights(JsonObject root, String key,
                                                          String alias) {
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
        String selectedKey = root.has(key) ? key : alias;
        if (!root.has(selectedKey) || !root.get(selectedKey).isJsonObject()) {
            return result;
        }
        root.getAsJsonObject(selectedKey).entrySet().forEach(entry -> {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) {
                throw new IllegalArgumentException(selectedKey
                        + " contains an invalid resource location: " + entry.getKey());
            }
            int weight = entry.getValue().getAsInt();
            if (weight < 0 || weight > 100000) {
                throw new IllegalArgumentException(selectedKey + " weights must be between 0 and 100000");
            }
            result.put(id, weight);
        });
        return result;
    }

    private static int integer(JsonObject root, String key, int fallback) {
        return root.has(key) ? root.get(key).getAsInt() : fallback;
    }

    private static double decimal(JsonObject root, String key, double fallback) {
        if (!root.has(key)) {
            return fallback;
        }
        double value = root.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException(key + " must be between 0 and 1");
        }
        return value;
    }

    private static int boundedInteger(JsonObject root, String key, int fallback,
                                      int min, int max) {
        if (!root.has(key)) {
            return fallback;
        }
        int value = root.get(key).getAsInt();
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }
}

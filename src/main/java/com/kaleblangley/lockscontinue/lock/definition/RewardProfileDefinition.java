package com.kaleblangley.lockscontinue.lock.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.kaleblangley.lockscontinue.lock.LockType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reloadable, un-resolved reward rules. */
public record RewardProfileDefinition(ResourceLocation id, List<RewardRule> rules) {
    public RewardProfileDefinition {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public RewardRule find(LockType type, ResourceLocation difficulty) {
        return rules.stream().filter(rule -> rule.matches(type, difficulty)).findFirst().orElse(null);
    }

    public static RewardProfileDefinition fromJson(ResourceLocation id, JsonObject root) {
        int schema = root.has("schema") ? root.get("schema").getAsInt() : 1;
        if (schema != 1) {
            throw new IllegalArgumentException("schema must be 1");
        }
        if (!root.has("rules") || !root.get("rules").isJsonArray()
                || root.getAsJsonArray("rules").isEmpty()) {
            throw new IllegalArgumentException("rules must be a non-empty array");
        }

        List<RewardRule> rules = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("rules")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("rules entries must be objects");
            }
            JsonObject object = element.getAsJsonObject();
            JsonObject match = object.has("match") && object.get("match").isJsonObject()
                    ? object.getAsJsonObject("match") : new JsonObject();
            Set<ResourceLocation> types = resourceSet(match, "lock_types");
            Set<ResourceLocation> difficulties = resourceSet(match, "difficulties");
            RewardMetric metric = RewardMetric.fromId(requiredString(object, "metric"));
            int failureAt = integer(object, "failure_at", -1);
            validateFailure(metric, failureAt);

            if (!object.has("tiers") || !object.get("tiers").isJsonArray()
                    || object.getAsJsonArray("tiers").isEmpty()) {
                throw new IllegalArgumentException("tiers must be a non-empty array");
            }
            List<RewardTier> tiers = new ArrayList<>();
            int previousMax = -1;
            for (JsonElement tierElement : object.getAsJsonArray("tiers")) {
                if (!tierElement.isJsonObject()) {
                    throw new IllegalArgumentException("tiers entries must be objects");
                }
                JsonObject tier = tierElement.getAsJsonObject();
                int max = integer(tier, "max", -1);
                if (max < 0 || max <= previousMax) {
                    throw new IllegalArgumentException("tiers.max must be strictly increasing");
                }
                int sourceRolls = integer(tier, "source_rolls", 1);
                if (sourceRolls < 1 || sourceRolls > 5) {
                    throw new IllegalArgumentException("source_rolls must be between 1 and 5");
                }
                int bonusRolls = integer(tier, "bonus_rolls", 0);
                if (bonusRolls < 0 || bonusRolls > 5) {
                    throw new IllegalArgumentException("bonus_rolls must be between 0 and 5");
                }
                ResourceLocation bonusTable = tier.has("bonus_loot_table")
                        ? ResourceLocation.tryParse(tier.get("bonus_loot_table").getAsString()) : null;
                if (bonusTable == null && bonusRolls > 0) {
                    throw new IllegalArgumentException(
                            "bonus_loot_table is required when bonus_rolls is greater than zero");
                }
                if (failureAt >= 0 && max >= failureAt) {
                    throw new IllegalArgumentException(
                            "tier.max must be smaller than failure_at for finite failures");
                }
                tiers.add(new RewardTier(max, sourceRolls, bonusTable, bonusRolls));
                previousMax = max;
            }
            rules.add(new RewardRule(types, difficulties, metric, failureAt, tiers));
        }
        return new RewardProfileDefinition(id, rules);
    }

    public static RewardProfileDefinition defaultProfile(ResourceLocation id) {
        List<RewardTier> tiers = List.of(new RewardTier(0, 1, null, 0));
        List<RewardRule> rules = new ArrayList<>();
        rules.add(new RewardRule(Set.of(LockType.PIN_LOCK.resourceId(),
                LockType.ROTARY_LOCK.resourceId(), LockType.MEMORY_LOCK.resourceId()),
                Set.of(), RewardMetric.MISTAKES_USED, -1, tiers));
        rules.add(new RewardRule(Set.of(LockType.PIPE_LOCK.resourceId()), Set.of(),
                RewardMetric.TIME_TICKS, -1, tiers));
        return new RewardProfileDefinition(id, rules);
    }

    private static void validateFailure(RewardMetric metric, int failureAt) {
        int max = metric == RewardMetric.TIME_TICKS ? 72000 : 100000;
        if (failureAt != -1 && (failureAt <= 0 || failureAt > max)) {
            throw new IllegalArgumentException("failure_at must be -1 or between 1 and " + max);
        }
    }

    private static Set<ResourceLocation> resourceSet(JsonObject root, String key) {
        if (!root.has(key)) {
            return Set.of();
        }
        if (!root.get(key).isJsonArray()) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (JsonElement element : root.getAsJsonArray(key)) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id == null) {
                throw new IllegalArgumentException(key + " contains an invalid resource location");
            }
            result.add(id);
        }
        return Set.copyOf(result);
    }

    private static String requiredString(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()
                || !root.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return root.get(key).getAsString();
    }

    private static int integer(JsonObject root, String key, int fallback) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive value = root.getAsJsonPrimitive(key);
        if (!value.isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return value.getAsInt();
    }
}

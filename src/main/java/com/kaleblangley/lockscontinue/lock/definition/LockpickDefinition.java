package com.kaleblangley.lockscontinue.lock.definition;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/** Data-pack mapping from an item ID to lockpick assistance statistics. */
public record LockpickDefinition(ResourceLocation id, ResourceLocation item, LockpickStats stats) {
    public static LockpickDefinition fromJson(ResourceLocation id, JsonObject root) {
        ResourceLocation item = ResourceLocation.tryParse(root.has("item")
                ? root.get("item").getAsString() : id.toString());
        if (item == null) {
            throw new IllegalArgumentException("item must be a valid resource location");
        }
        JsonObject stats = root.has("stats") && root.get("stats").isJsonObject()
                ? root.getAsJsonObject("stats") : root;
        return new LockpickDefinition(id, item, new LockpickStats(
                integer(stats, "strength", 0),
                integer(stats, "precision", 0),
                integer(stats, "stability", 0),
                integer(stats, "insight", 0),
                integer(stats, "speed", 0),
                (float) decimal(stats, "durability_multiplier", 1.0D)
        ));
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        int value = object.get(key).getAsInt();
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(key + " must be between 0 and 100");
        }
        return value;
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        double value = object.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < 0.0D || value > 100.0D) {
            throw new IllegalArgumentException(key + " must be between 0 and 100");
        }
        return value;
    }
}

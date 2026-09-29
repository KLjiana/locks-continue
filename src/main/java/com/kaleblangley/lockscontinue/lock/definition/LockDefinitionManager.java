package com.kaleblangley.lockscontinue.lock.definition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.config.LocksContinueConfig;
import com.kaleblangley.lockscontinue.lock.LockInstance;
import com.kaleblangley.lockscontinue.lock.LockType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server data-pack registry for lock difficulties, loot rules, affixes and
 * lockpick definitions. Invalid individual files are logged and skipped.
 */
public final class LockDefinitionManager
        extends SimplePreparableReloadListener<LockDefinitionManager.PreparedDefinitions> {
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String ROOT = "lockscontinue/";
    public static final ResourceLocation DEFAULT_REWARD_PROFILE =
            new ResourceLocation(LocksContinue.MODID, "default");

    public static final LockDefinitionManager INSTANCE = new LockDefinitionManager();

    private volatile DefinitionSnapshot snapshot = DefinitionSnapshot.defaults();

    private LockDefinitionManager() {
    }

    public DefinitionSnapshot snapshot() {
        return snapshot;
    }

    public LockDifficultyDefinition difficulty(ResourceLocation id) {
        return snapshot.difficulties().getOrDefault(id,
                snapshot.difficulties().get(LockInstance.NORMAL_DIFFICULTY));
    }

    public LockDifficultyDefinition difficultyFor(LockInstance instance) {
        return difficulty(instance.difficulty());
    }

    /** Resolves a saved reward snapshot once; callers persist the returned instance. */
    public ResolvedRewardSnapshot resolveRewardSnapshot(LockInstance instance) {
        if (instance.rewardSnapshot() != null) {
            return instance.rewardSnapshot();
        }
        LockType type = LockType.fromResourceId(instance.lockType());
        ResourceLocation requestedProfile = instance.rewardProfile() == null
                ? DEFAULT_REWARD_PROFILE : instance.rewardProfile();
        // An omitted profile means the legacy/default profile. An explicitly
        // unknown profile is a broken datapack reference and must use the
        // conservative difficulty-based fallback instead of inheriting the
        // global default reward policy.
        if (instance.rewardProfile() != null
                && !snapshot.rewards().containsKey(requestedProfile)) {
            return safeRewardSnapshot(DEFAULT_REWARD_PROFILE, instance, type);
        }
        ResourceLocation profileId = snapshot.rewards().containsKey(requestedProfile)
                ? requestedProfile : DEFAULT_REWARD_PROFILE;
        RewardProfileDefinition profile = snapshot.rewards().get(profileId);
        RewardRule rule = profile == null ? null : profile.find(type, instance.difficulty());
        if (rule != null) {
            return new ResolvedRewardSnapshot(profileId, rule.metric(), rule.failureAt(), rule.tiers());
        }
        return safeRewardSnapshot(profileId, instance, type);
    }

    public LockInstance withRewardSnapshot(LockInstance instance) {
        if (instance.rewardSnapshot() != null && instance.schemaVersion() >= LockInstance.CURRENT_SCHEMA) {
            return instance;
        }
        ResourceLocation requestedProfile = instance.rewardProfile() == null
                ? DEFAULT_REWARD_PROFILE : instance.rewardProfile();
        ResourceLocation profileId = snapshot.rewards().containsKey(requestedProfile)
                ? requestedProfile : DEFAULT_REWARD_PROFILE;
        return instance.withRewardSnapshot(profileId, resolveRewardSnapshot(instance));
    }

    private ResolvedRewardSnapshot safeRewardSnapshot(ResourceLocation profileId,
                                                      LockInstance instance, LockType type) {
        int failure = type == LockType.PIPE_LOCK
                ? -1
                : type == LockType.MEMORY_LOCK
                ? difficultyFor(instance).memory().mismatchLimit()
                : difficultyFor(instance).failure().mistakes();
        RewardMetric metric = type == LockType.PIPE_LOCK
                ? RewardMetric.TIME_TICKS : RewardMetric.MISTAKES_USED;
        return new ResolvedRewardSnapshot(profileId, metric, failure,
                List.of(new RewardTier(Integer.MAX_VALUE, 1, null, 0)));
    }

    public List<LockAffixDefinition> affixes(LockInstance instance) {
        List<LockAffixDefinition> result = new ArrayList<>();
        int rank = difficultyFor(instance).rank();
        LockType type = LockType.fromResourceId(instance.lockType());
        for (ResourceLocation id : instance.affixes()) {
            LockAffixDefinition affix = snapshot.affixes().get(id);
            if (affix != null && affix.appliesTo(type, rank)) {
                result.add(affix);
            }
        }
        return List.copyOf(result);
    }

    public LockpickStats lockpickStats(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (LockpickDefinition definition : snapshot.lockpicks().values()) {
            if (definition.item().equals(itemId)) {
                return definition.stats();
            }
        }
        return LockpickStats.EMPTY;
    }

    public boolean isLockpick(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return snapshot.lockpicks().values().stream()
                .anyMatch(definition -> definition.item().equals(itemId));
    }

    /** IDs shown by the toolbox; sorted so the client layout is deterministic. */
    public List<ResourceLocation> selectableDifficultyIds() {
        return snapshot.difficulties().keySet().stream()
                .sorted(Comparator
                        .comparingInt((ResourceLocation id) -> snapshot.difficulties().get(id).rank())
                        .thenComparing(ResourceLocation::toString))
                .toList();
    }

    /** Definitions shown by the toolbox; sorted so custom data packs are stable too. */
    public List<LockAffixDefinition> selectableAffixes() {
        return snapshot.affixes().values().stream()
                .sorted(Comparator.comparing(affix -> affix.id().toString()))
                .toList();
    }

    public ResourceLocation normalizeDifficulty(ResourceLocation id) {
        return id != null && snapshot.difficulties().containsKey(id)
                ? id : LockInstance.NORMAL_DIFFICULTY;
    }

    /**
     * Validates manual toolbox choices against the current server snapshot.
     * Unknown, duplicate, rank-incompatible and conflicting affixes are
     * ignored instead of allowing a client to write arbitrary NBT.
     */
    public List<ResourceLocation> normalizeManualAffixes(LockType type,
                                                          ResourceLocation difficultyId,
                                                          List<ResourceLocation> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }

        int rank = difficulty(normalizeDifficulty(difficultyId)).rank();
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation id : requested) {
            if (result.size() >= 8 || id == null || result.contains(id)) {
                continue;
            }
            LockAffixDefinition candidate = snapshot.affixes().get(id);
            if (candidate == null || !candidate.appliesTo(type, rank)) {
                continue;
            }
            boolean conflicts = result.stream().anyMatch(existingId -> {
                LockAffixDefinition existing = snapshot.affixes().get(existingId);
                return existing != null
                        && (existing.conflicts().contains(id)
                        || candidate.conflicts().contains(existingId));
            });
            if (!conflicts) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    /** Resolves difficulty, affixes and tool values once for one lock session. */
    public ResolvedLockContext resolve(LockInstance instance, ItemStack tool) {
        LockDifficultyDefinition difficulty = difficultyFor(instance);
        List<LockAffixDefinition> definitions = affixes(instance);
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("failure.mistakes", (double) difficulty.failure().mistakes());
        values.put("failure.durability_cost", (double) difficulty.failure().durabilityCost());
        values.put("pin.count", (double) difficulty.pin().pinCount());
        values.put("pin.set_tolerance_ticks", (double) difficulty.pin().setToleranceTicks());
        values.put("pin.apex_hold_ticks", (double) difficulty.pin().apexHoldTicks());
        values.put("pin.min_hold_ticks", (double) difficulty.pin().apexHoldTicks());
        values.put("pin.reset_locked_pins", (double) difficulty.pin().resetLockedPins());
        values.put("pin.speed_variance", (double) difficulty.pin().speedVariance());
        values.put("pipe.width", (double) difficulty.pipe().width());
        values.put("pipe.height", (double) difficulty.pipe().height());
        values.put("pipe.terminal_count", (double) difficulty.pipe().terminalCount());
        values.put("pipe.minimum_turns", (double) difficulty.pipe().minimumSolutionRotations());
        values.put("pipe.minimum_solution_rotations",
                (double) difficulty.pipe().minimumSolutionRotations());
        values.put("pipe.maximum_solution_rotations",
                (double) difficulty.pipe().maximumSolutionRotations());
        values.put("pipe.minimum_route_cells", (double) difficulty.pipe().minimumRouteCells());
        values.put("pipe.minimum_filled_cells", (double) difficulty.pipe().minimumFilledCells());
        values.put("pipe.minimum_junctions", (double) difficulty.pipe().minimumJunctions());
        values.put("pipe.minimum_dead_ends", (double) difficulty.pipe().minimumDeadEnds());
        values.put("pipe.require_all_connected", difficulty.pipe().requireAllConnected() ? 1.0D : 0.0D);
        values.put("pipe.forbid_leaks", difficulty.pipe().forbidLeaks() ? 1.0D : 0.0D);
        values.put("pipe.hint_count", (double) difficulty.pipe().hintCount());
        values.put("pipe.time_limit_ticks", (double) difficulty.pipe().timeLimitTicks());
        values.put("rotary.tolerance_degrees", difficulty.rotary().toleranceDegrees());
        values.put("rotary.sweet_spot_degrees", difficulty.rotary().toleranceDegrees());
        values.put("rotary.feedback_falloff", difficulty.rotary().feedbackFalloff());
        values.put("memory.rows", (double) difficulty.memory().rows());
        values.put("memory.columns", (double) difficulty.memory().columns());
        values.put("memory.preview_ticks", (double) difficulty.memory().previewTicks());
        values.put("memory.preview_ticks_per_pair", (double) difficulty.memory().previewTicksPerPair());
        values.put("memory.mistakes", (double) difficulty.memory().mistakes());
        values.put("memory.mismatch_limit", (double) difficulty.memory().mismatchLimit());
        values.put("memory.hint_count", (double) difficulty.memory().hintCount());

        Set<String> flags = new java.util.LinkedHashSet<>();
        for (LockAffixDefinition affix : definitions) {
            affix.additions().forEach((key, value) ->
                    values.merge(normalizeNumericKey(key), value, Double::sum));
            affix.multipliers().forEach((key, multiplier) ->
                    values.compute(normalizeNumericKey(key), (ignored, current) ->
                            (current == null ? 1.0D : current) * multiplier));
            affix.flags().forEach((key, enabled) -> {
                if (enabled) {
                    flags.add(key);
                } else {
                    flags.remove(key);
                }
            });
        }
        // Keep the old field name readable for integrations while resolving
        // both spellings to the guide's canonical key.
        values.put("pipe.minimum_turns",
                values.getOrDefault("pipe.minimum_solution_rotations", 0.0D));

        LockpickStats toolStats = lockpickStats(tool);
        int configuredDurabilityCost = intValue(values, "failure.durability_cost", 0, 100);
        int durabilityCost = configuredDurabilityCost <= 0 ? 0 : Math.max(1,
                (int) Math.ceil(configuredDurabilityCost
                        * toolStats.durabilityMultiplier()
                        * strengthDurabilityFactor(toolStats.strength())));

        return new ResolvedLockContext(
                instance,
                difficulty.id(),
                difficulty.rank(),
                difficulty.displayColor(),
                intValue(values, "failure.mistakes", 1, 100),
                durabilityCost,
                intValue(values, "pin.set_tolerance_ticks", 0, 100),
                intValue(values, "pin.min_hold_ticks", 0, 100),
                intValue(values, "pin.reset_locked_pins", 0, 10),
                intValue(values, "pipe.minimum_turns", 0, 1000),
                intValue(values, "pipe.hint_count", 0, 100),
                (float) doubleValue(values, "rotary.tolerance_degrees", 0.0D, 180.0D),
                (float) doubleValue(values, "rotary.stress_multiplier", 1.0D, 100.0D),
                intValue(values, "memory.preview_ticks", 0, 1200),
                intValue(values, "memory.mistakes", 1, 100),
                intValue(values, "memory.hint_count", 0, 100),
                toolStats,
                definitions.stream().map(LockAffixDefinition::id).toList(),
                values,
                flags
        );
    }

    /**
     * Strength is applied once, when a session snapshot is resolved. Stability
     * belongs to rotary handling and must not reduce the same durability cost a
     * second time when a server action fails.
     */
    private static double strengthDurabilityFactor(int strength) {
        return Math.max(0.55D, 1.0D - Math.max(0, strength - 1) * 0.15D);
    }

    /** Selects and rolls the first matching rule for a newly generated container. */
    public LockInstance createLootLock(BlockGetter level, RandomSource random,
                                       RandomizableContainerBlockEntity container,
                                       ResourceLocation sourceLootTable) {
        ResourceLocation dimension;
        if (level instanceof ServerLevel serverLevel) {
            dimension = serverLevel.dimension().location();
        } else if (level instanceof WorldGenRegion region) {
            dimension = region.getLevel().dimension().location();
        } else {
            dimension = null;
        }
        LootLockRule rule = findRule(dimension, container.getBlockState(), sourceLootTable);
        double chance = rule.lockChance() * LocksContinueConfig.LOOT_CHEST_LOCK_CHANCE.get();
        if (random.nextDouble() >= Math.max(0.0D, Math.min(1.0D, chance))) {
            return null;
        }

        LockType type = rule.chooseType(random);
        ResourceLocation difficulty = rule.chooseDifficulty(random, LockInstance.NORMAL_DIFFICULTY);
        if (!snapshot.difficulties().containsKey(difficulty)) {
            difficulty = LockInstance.NORMAL_DIFFICULTY;
        }

        List<ResourceLocation> selectedAffixes = selectAffixes(rule, type, difficulty, random);
        ResourceLocation rewardProfile = rule.rewardProfile() == null
                ? DEFAULT_REWARD_PROFILE : rule.rewardProfile();
        LockInstance base = LockInstance.create(type, difficulty, random.nextLong(),
                sourceLootTable, selectedAffixes, rewardProfile, null);
        return withRewardSnapshot(base);
    }

    public LootLockRule findRule(ResourceLocation dimension, BlockState state,
                                 ResourceLocation sourceLootTable) {
        for (LootLockRule rule : snapshot.lootRules()) {
            if (rule.matches(sourceLootTable, dimension, state)) {
                return rule;
            }
        }
        return snapshot.defaultRule();
    }

    private List<ResourceLocation> selectAffixes(LootLockRule rule, LockType type,
                                                 ResourceLocation difficulty,
                                                 RandomSource random) {
        if (rule.affixPool() == null || rule.affixMax() <= 0
                || random.nextDouble() >= rule.affixChance()) {
            return List.of();
        }

        int rank = difficulty(difficulty).rank();
        List<LockAffixDefinition> candidates = new ArrayList<>();
        for (LockAffixDefinition affix : snapshot.affixes().values()) {
            if (rule.affixPool().equals(affix.pool())
                    && affix.appliesTo(type, rank) && affix.weight() > 0) {
                candidates.add(affix);
            }
        }

        List<ResourceLocation> result = new ArrayList<>();
        int count = rule.affixMin() + random.nextInt(rule.affixMax() - rule.affixMin() + 1);
        while (result.size() < count && !candidates.isEmpty()) {
            int total = candidates.stream().mapToInt(LockAffixDefinition::weight).sum();
            int pick = random.nextInt(Math.max(1, total));
            LockAffixDefinition selected = candidates.get(0);
            for (LockAffixDefinition candidate : candidates) {
                pick -= candidate.weight();
                if (pick < 0) {
                    selected = candidate;
                    break;
                }
            }
            LockAffixDefinition chosen = selected;
            result.add(chosen.id());
            candidates.removeIf(candidate -> candidate.id().equals(chosen.id())
                    || chosen.conflicts().contains(candidate.id())
                    || candidate.conflicts().contains(chosen.id()));
        }
        return List.copyOf(result);
    }

    @Override
    protected PreparedDefinitions prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return new PreparedDefinitions(
                collect(resourceManager, ROOT + "difficulties"),
                collectLootRules(resourceManager),
                collect(resourceManager, ROOT + "affixes"),
                collect(resourceManager, ROOT + "lockpicks"),
                collect(resourceManager, ROOT + "rewards")
        );
    }

    @Override
    protected void apply(PreparedDefinitions prepared, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, LockDifficultyDefinition> difficulties = new LinkedHashMap<>(
                DefinitionSnapshot.defaults().difficulties());
        Map<ResourceLocation, LockAffixDefinition> affixes = new LinkedHashMap<>();
        Map<ResourceLocation, LockpickDefinition> lockpicks = new LinkedHashMap<>(
                DefinitionSnapshot.defaults().lockpicks());
        Map<ResourceLocation, RewardProfileDefinition> rewards = new LinkedHashMap<>(
                DefinitionSnapshot.defaults().rewards());
        List<LootLockRule> rules = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.difficulties().entrySet()) {
            try {
                difficulties.put(entry.getKey(), LockDifficultyDefinition.fromJson(
                        entry.getKey(), entry.getValue()));
            } catch (RuntimeException exception) {
                LocksContinue.LOGGER.error("Invalid lock difficulty {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.affixes().entrySet()) {
            try {
                affixes.put(entry.getKey(), LockAffixDefinition.fromJson(
                        entry.getKey(), entry.getValue()));
            } catch (RuntimeException exception) {
                LocksContinue.LOGGER.error("Invalid lock affix {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        for (Map.Entry<ResourceLocation, LockAffixDefinition> entry
                : new ArrayList<>(affixes.entrySet())) {
            try {
                validateAffix(entry.getValue(), affixes);
            } catch (RuntimeException exception) {
                affixes.remove(entry.getKey());
                LocksContinue.LOGGER.error("Invalid lock affix {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.lockpicks().entrySet()) {
            try {
                LockpickDefinition definition = LockpickDefinition.fromJson(
                        entry.getKey(), entry.getValue());
                if (!BuiltInRegistries.ITEM.containsKey(definition.item())) {
                    throw new IllegalArgumentException("unknown item " + definition.item());
                }
                lockpicks.put(entry.getKey(), definition);
            } catch (RuntimeException exception) {
                LocksContinue.LOGGER.error("Invalid lockpick definition {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.rewards().entrySet()) {
            try {
                RewardProfileDefinition definition = RewardProfileDefinition.fromJson(
                        entry.getKey(), entry.getValue());
                validateRewardProfile(definition, difficulties);
                rewards.put(entry.getKey(), definition);
            } catch (RuntimeException exception) {
                LocksContinue.LOGGER.error("Invalid reward profile {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.lootRules().entrySet()) {
            try {
                LootLockRule rule = LootLockRule.fromJson(entry.getKey(), entry.getValue());
                validateRule(rule, difficulties, affixes, rewards, entry.getKey());
                rules.add(rule);
            } catch (RuntimeException exception) {
                LocksContinue.LOGGER.error("Invalid loot lock rule {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        }

        rules.sort(Comparator.comparingInt(LootLockRule::priority).reversed()
                .thenComparing(rule -> rule.id().toString()));
        DefinitionSnapshot defaults = DefinitionSnapshot.defaults();
        snapshot = new DefinitionSnapshot(
                Map.copyOf(difficulties),
                List.copyOf(rules),
                Map.copyOf(affixes),
                Map.copyOf(lockpicks),
                Map.copyOf(rewards),
                defaults.defaultRule()
        );
        LocksContinue.LOGGER.info("Loaded {} lock difficulties, {} loot rules, {} affixes, {} lockpick definitions and {} reward profiles",
                difficulties.size(), rules.size(), affixes.size(), lockpicks.size(), rewards.size());
    }

    private static void validateAffix(LockAffixDefinition affix,
                                      Map<ResourceLocation, LockAffixDefinition> affixes) {
        for (ResourceLocation type : affix.lockTypes()) {
            if (!isKnownType(type)) {
                throw new IllegalArgumentException("unknown lock type " + type);
            }
        }
        for (ResourceLocation conflict : affix.conflicts()) {
            if (!affixes.containsKey(conflict)) {
                throw new IllegalArgumentException("unknown conflicting affix " + conflict);
            }
        }
    }

    private static void validateRule(LootLockRule rule,
                                     Map<ResourceLocation, LockDifficultyDefinition> difficulties,
                                     Map<ResourceLocation, LockAffixDefinition> affixes,
                                     Map<ResourceLocation, RewardProfileDefinition> rewards,
                                     ResourceLocation id) {
        for (ResourceLocation type : rule.lockTypes().keySet()) {
            if (!isKnownType(type)) {
                throw new IllegalArgumentException("unknown lock type " + type);
            }
        }
        for (ResourceLocation difficulty : rule.difficulties().keySet()) {
            if (!difficulties.containsKey(difficulty)) {
                throw new IllegalArgumentException("unknown difficulty " + difficulty);
            }
        }
        if (rule.affixPool() != null) {
            boolean poolExists = affixes.values().stream()
                    .anyMatch(affix -> rule.affixPool().equals(affix.pool()));
            if (!poolExists) {
                throw new IllegalArgumentException("unknown affix pool " + rule.affixPool());
            }
            LocksContinue.LOGGER.debug("Loot rule {} uses affix pool {}", id, rule.affixPool());
        }
        if (rule.rewardProfile() != null && !rewards.containsKey(rule.rewardProfile())) {
            // Keep the lock selection rule itself. Only its reward reference
            // falls back, otherwise one typo would silently change the lock
            // type/difficulty distribution to the global default rule.
            LocksContinue.LOGGER.error("Loot rule {} references unknown reward profile {}; using safe default",
                    id, rule.rewardProfile());
        }
    }

    private static void validateRewardProfile(RewardProfileDefinition profile,
                                              Map<ResourceLocation, LockDifficultyDefinition> difficulties) {
        for (RewardRule rule : profile.rules()) {
            for (ResourceLocation type : rule.lockTypes()) {
                if (!isKnownType(type)) {
                    throw new IllegalArgumentException("unknown lock type " + type);
                }
            }
            for (ResourceLocation difficulty : rule.difficulties()) {
                if (!difficulties.containsKey(difficulty)) {
                    throw new IllegalArgumentException("unknown difficulty " + difficulty);
                }
            }
        }
    }

    private static boolean isKnownType(ResourceLocation id) {
        for (LockType type : LockType.values()) {
            if (type.resourceId().equals(id) || type.id().equals(id.getPath())) {
                return true;
            }
        }
        return false;
    }

    private static int intValue(Map<String, Double> values, String key, int min, int max) {
        return (int) Math.max(min, Math.min(max, Math.round(values.getOrDefault(key, (double) min))));
    }

    private static double doubleValue(Map<String, Double> values, String key,
                                      double min, double max) {
        return Math.max(min, Math.min(max, values.getOrDefault(key, min)));
    }

    private static String normalizeNumericKey(String key) {
        return "pipe.minimum_turns".equals(key)
                ? "pipe.minimum_solution_rotations" : key;
    }

    private static Map<ResourceLocation, JsonObject> collect(ResourceManager resourceManager, String folder) {
        Map<ResourceLocation, JsonObject> result = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager.listResources(
                folder, location -> location.getPath().endsWith(".json")).entrySet()) {
            String prefix = folder + "/";
            String path = entry.getKey().getPath();
            if (!path.startsWith(prefix)) {
                continue;
            }
            ResourceLocation id = new ResourceLocation(entry.getKey().getNamespace(),
                    path.substring(prefix.length(), path.length() - ".json".length()));
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("root must be a JSON object");
                }
                result.put(id, element.getAsJsonObject());
            } catch (IOException | RuntimeException exception) {
                LocksContinue.LOGGER.error("Could not read lock definition {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        return result;
    }

    /** Supports the original loot_rules path and the guide's loot_lock_rules path. */
    private static Map<ResourceLocation, JsonObject> collectLootRules(ResourceManager resourceManager) {
        Map<ResourceLocation, JsonObject> result = new HashMap<>(
                collect(resourceManager, ROOT + "loot_rules"));
        result.putAll(collect(resourceManager, ROOT + "loot_lock_rules"));
        return result;
    }

    public record PreparedDefinitions(
            Map<ResourceLocation, JsonObject> difficulties,
            Map<ResourceLocation, JsonObject> lootRules,
            Map<ResourceLocation, JsonObject> affixes,
            Map<ResourceLocation, JsonObject> lockpicks,
            Map<ResourceLocation, JsonObject> rewards
    ) {
    }

    public record DefinitionSnapshot(
            Map<ResourceLocation, LockDifficultyDefinition> difficulties,
            List<LootLockRule> lootRules,
            Map<ResourceLocation, LockAffixDefinition> affixes,
            Map<ResourceLocation, LockpickDefinition> lockpicks,
            Map<ResourceLocation, RewardProfileDefinition> rewards,
            LootLockRule defaultRule
    ) {
        public static DefinitionSnapshot defaults() {
            Map<ResourceLocation, LockDifficultyDefinition> difficulties = new LinkedHashMap<>();
            String[] ids = {"simple", "normal", "hard", "expert", "master"};
            for (int i = 0; i < ids.length; i++) {
                int rank = i + 1;
                difficulties.put(new ResourceLocation(LocksContinue.MODID, ids[i]),
                        defaultDifficulty(new ResourceLocation(LocksContinue.MODID, ids[i]), rank));
            }

            Map<ResourceLocation, Integer> types = new LinkedHashMap<>();
            for (LockType type : LockType.values()) {
                types.put(type.resourceId(), 1);
            }
            Map<ResourceLocation, Integer> normal = Map.of(LockInstance.NORMAL_DIFFICULTY, 1);
            LootLockRule defaultRule = new LootLockRule(
                    new ResourceLocation(LocksContinue.MODID, "default"), Integer.MIN_VALUE,
                    new LootLockRule.Match(List.of(), List.of(), List.of(), List.of(), List.of()),
                    1.0D, 1.0D, types, normal, 0, 0, null, DEFAULT_REWARD_PROFILE);

            Map<ResourceLocation, LockpickDefinition> lockpicks = new LinkedHashMap<>();
            addDefaultLockpick(lockpicks, "wood_comb", 1, 2, 0, 0, 0, 1.0F);
            addDefaultLockpick(lockpicks, "iron_wrench", 2, 1, 2, 0, 1, 1.0F);
            addDefaultLockpick(lockpicks, "diamond_wrench", 3, 2, 2, 1, 2, 0.9F);
            addDefaultLockpick(lockpicks, "netherite_pick_gun", 4, 1, 4, 0, 3, 0.75F);

            Map<ResourceLocation, RewardProfileDefinition> rewards = new LinkedHashMap<>();
            rewards.put(DEFAULT_REWARD_PROFILE,
                    RewardProfileDefinition.defaultProfile(DEFAULT_REWARD_PROFILE));

            return new DefinitionSnapshot(Map.copyOf(difficulties), List.of(), Map.of(),
                    Map.copyOf(lockpicks), Map.copyOf(rewards), defaultRule);
        }

        private static void addDefaultLockpick(Map<ResourceLocation, LockpickDefinition> lockpicks,
                                                String itemPath, int strength, int precision,
                                                int stability, int insight, int speed,
                                                float durabilityMultiplier) {
            ResourceLocation id = new ResourceLocation(LocksContinue.MODID, itemPath);
            lockpicks.put(id, new LockpickDefinition(id, id,
                    new LockpickStats(strength, precision, stability, insight, speed,
                            durabilityMultiplier)));
        }

        private static LockDifficultyDefinition defaultDifficulty(ResourceLocation id, int rank) {
            return new LockDifficultyDefinition(
                    id,
                    rank,
                    switch (rank) {
                        case 1 -> 0xFF78C887;
                        case 2 -> 0xFFE0C66D;
                        case 3 -> 0xFFD97A46;
                        case 4 -> 0xFFC75BCB;
                        default -> 0xFFE04A4A;
                    },
                    new LockDifficultyDefinition.Failure(Math.max(2, 6 - rank / 2), Math.max(1, rank / 2)),
                    new LockDifficultyDefinition.Pin(
                            switch (rank) {
                                case 1 -> 4;
                                case 2 -> 5;
                                case 3 -> 6;
                                case 4 -> 7;
                                default -> 8;
                            },
                            Math.max(1, 3 - rank / 3),
                            1,
                            1.0F + rank * 0.08F,
                            Math.min(8, rank + 1)),
                    new LockDifficultyDefinition.Pipe(
                            switch (rank) {
                                case 1, 2 -> 5;
                                case 3 -> 6;
                                case 4 -> 7;
                                default -> 8;
                            },
                            switch (rank) {
                                case 1, 2 -> 5;
                                case 3 -> 6;
                                case 4 -> 7;
                                default -> 8;
                             },
                             Math.min(5, rank),
                             switch (rank) {
                                 case 1 -> 2;
                                 case 2 -> 5;
                                 case 3 -> 11;
                                 case 4 -> 18;
                                 default -> 28;
                             },
                             switch (rank) {
                                 case 1 -> 5;
                                 case 2 -> 9;
                                 case 3 -> 17;
                                 case 4 -> 28;
                                 default -> 42;
                             },
                             switch (rank) {
                                 case 1 -> 7;
                                 case 2 -> 11;
                                 case 3 -> 17;
                                 case 4 -> 24;
                                 default -> 32;
                             },
                             switch (rank) {
                                 case 1 -> 20;
                                 case 2 -> 23;
                                 case 3 -> 34;
                                 case 4 -> 47;
                                 default -> 62;
                             },
                             switch (rank) {
                                 case 1 -> 1;
                                 case 2 -> 3;
                                 case 3 -> 6;
                                 case 4 -> 10;
                                 default -> 14;
                             },
                             0,
                             false,
                             false,
                             Math.max(0, 2 - rank / 2),
                             1200),
                    new LockDifficultyDefinition.Rotary(
                            Math.max(3.0D, 13.0D - rank * 2.0D),
                            1.0D + rank * 0.1D),
                    new LockDifficultyDefinition.Memory(
                            rank <= 2 ? 4 : rank == 3 ? 6 : rank == 4 ? 6 : 8,
                            rank <= 2 ? 4 : rank == 3 ? 6 : rank == 4 ? 8 : 8,
                             switch (rank) {
                                 case 1 -> 6;
                                 case 2 -> 15;
                                 case 3 -> 15;
                                 case 4 -> 30;
                                 default -> 80;
                             },
                             switch (rank) {
                                 case 1 -> 8;
                                 case 2 -> 5;
                                 case 3 -> 3;
                                 default -> 2;
                             },
                             switch (rank) {
                                 case 1, 2 -> 6;
                                 default -> 4;
                             },
                             switch (rank) {
                                 case 1, 2 -> 6;
                                 default -> 4;
                             },
                            Math.max(0, 2 - rank / 2))
            );
        }
    }
}

package com.kaleblangley.lockscontinue.interaction;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.lock.LockData;
import com.kaleblangley.lockscontinue.lock.LockInstance;
import com.kaleblangley.lockscontinue.lock.definition.ResolvedRewardSnapshot;
import com.kaleblangley.lockscontinue.lock.definition.RewardTier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-only, idempotent distribution of a completed lock's rewards. */
public final class LockRewardDistributor {
    private LockRewardDistributor() {
    }

    public static void distribute(ServerLevel level, ServerPlayer player,
                                  List<RandomizableContainerBlockEntity> containers,
                                  LockInstance instance, ResolvedRewardSnapshot reward,
                                  int metricValue) {
        if (containers == null || containers.isEmpty() || instance == null || reward == null
                || !LockData.claimReward(containers, player.getUUID())) {
            return;
        }

        RewardTier tier = reward.tierFor(Math.max(0, metricValue));
        try {
            RandomizableContainerBlockEntity primary = containers.get(0);
            Container openedContainer = openedContainer(player);
            List<Container> targets = openedContainer == null
                    ? new ArrayList<>(containers) : List.of(openedContainer);
            if (instance.sourceLootTable() != null) {
                // The vanilla table attached to the container is the normal
                // first round. Only the extra source_rolls - 1 rounds are
                // generated here, so existing contents are never copied.
                if (openedContainer == null) {
                    try {
                        primary.unpackLootTable(player);
                    } catch (RuntimeException exception) {
                        LocksContinue.LOGGER.warn("Could not unpack source loot table {} at {}: {}",
                                instance.sourceLootTable(), primary.getBlockPos(), exception.getMessage());
                    }
                }
                for (int roll = 1; roll < Math.max(1, tier.sourceRolls()); roll++) {
                    generateInto(level, player, targets, primary.getBlockPos(), instance.sourceLootTable(),
                            instance, metricValue, roll, "source");
                }
            }

            if (tier.bonusLootTable() != null && tier.bonusRolls() > 0) {
                for (int roll = 0; roll < tier.bonusRolls(); roll++) {
                    generateInto(level, player, targets, primary.getBlockPos(), tier.bonusLootTable(),
                            instance, metricValue, roll, "bonus");
                }
            }
        } catch (RuntimeException exception) {
            // A broken optional table or compatibility implementation must
            // not leave a successfully solved lock permanently unopenable.
            LocksContinue.LOGGER.error("Reward distribution failed for lock at {}: {}",
                    containers.get(0).getBlockPos(), exception.getMessage());
        }
    }

    private static void generateInto(ServerLevel level, ServerPlayer player,
                                     List<? extends Container> targets, BlockPos rewardPos,
                                     net.minecraft.resources.ResourceLocation lootTableId,
                                     LockInstance instance, int metricValue, int roll,
                                     String category) {
        if (!level.getServer().getLootData().getKeys(LootDataType.TABLE).contains(lootTableId)) {
            LocksContinue.LOGGER.error("Missing reward loot table {} for {} reward at {}",
                    lootTableId, category, rewardPos);
            return;
        }
        LootTable lootTable = level.getServer().getLootData().getLootTable(lootTableId);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN,
                        Vec3.atCenterOf(rewardPos))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withOptionalParameter(LootContextParams.TOOL, player.getMainHandItem())
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        long seed = derivedSeed(instance.seed(), lootTableId, metricValue, roll, category);
        List<ItemStack> generated = lootTable.getRandomItems(params, seed);
        for (ItemStack stack : generated) {
            insertOrDrop(level, targets, rewardPos, stack);
        }
    }

    private static void insertOrDrop(ServerLevel level,
                                     List<? extends Container> containers, BlockPos rewardPos,
                                     ItemStack generated) {
        if (generated == null || generated.isEmpty()) {
            return;
        }
        ItemStack remainder = generated.copy();
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && !remainder.isEmpty(); slot++) {
                ItemStack existing = container.getItem(slot);
                if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, remainder)
                        && existing.getCount() < Math.min(existing.getMaxStackSize(),
                        container.getMaxStackSize())) {
                    int amount = Math.min(remainder.getCount(),
                            Math.min(existing.getMaxStackSize(), container.getMaxStackSize())
                                    - existing.getCount());
                    existing.grow(amount);
                    remainder.shrink(amount);
                    container.setItem(slot, existing);
                }
            }
        }
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && !remainder.isEmpty(); slot++) {
                if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, remainder)) {
                    int amount = Math.min(remainder.getCount(), remainder.getMaxStackSize());
                    container.setItem(slot, remainder.copyWithCount(amount));
                    remainder.shrink(amount);
                }
            }
            if (!remainder.isEmpty()) {
                container.setChanged();
            }
        }
        if (!remainder.isEmpty()) {
            ItemEntity entity = new ItemEntity(level, rewardPos.getX() + 0.5D,
                    rewardPos.getY() + 0.65D, rewardPos.getZ() + 0.5D, remainder);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
    }

    private static Container openedContainer(ServerPlayer player) {
        if (player.containerMenu instanceof ChestMenu menu) {
            return menu.getContainer();
        }
        return null;
    }

    private static long derivedSeed(long base, net.minecraft.resources.ResourceLocation table,
                                    int metricValue, int roll, String category) {
        long value = base ^ table.hashCode() * 0x9E3779B97F4A7C15L;
        value ^= (long) metricValue * 0xD1B54A32D192ED03L;
        value ^= (long) roll * 0x94D049BB133111EBL;
        value ^= category.hashCode() * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}

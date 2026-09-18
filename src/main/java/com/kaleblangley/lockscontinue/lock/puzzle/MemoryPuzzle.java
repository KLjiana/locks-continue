package com.kaleblangley.lockscontinue.lock.puzzle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** A deterministic sixteen-card matching-pairs board. */
public final class MemoryPuzzle implements LockPuzzle {
    public static final int CARD_COUNT = 16;
    public static final int PAIR_COUNT = 8;

    private final Item[] pairItems = new Item[PAIR_COUNT];
    private final int[] cards = new int[CARD_COUNT];
    private boolean complete;

    public MemoryPuzzle(long seed) {
        Random random = new Random(seed ^ 0x4D454D4F52594BL);
        List<Item> candidates = availableItems();
        Collections.shuffle(candidates, random);
        for (int i = 0; i < PAIR_COUNT; i++) {
            pairItems[i] = candidates.get(i);
            cards[i * 2] = i;
            cards[i * 2 + 1] = i;
        }
        for (int i = CARD_COUNT - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            int value = cards[i];
            cards[i] = cards[other];
            cards[other] = value;
        }
    }

    public ItemStack itemAt(int cardIndex) {
        if (cardIndex < 0 || cardIndex >= CARD_COUNT) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(pairItems[cards[cardIndex]]);
    }

    public int pairAt(int cardIndex) {
        return cards[cardIndex];
    }

    public boolean isValidOrder(int[] order) {
        if (order == null || order.length != CARD_COUNT) {
            return false;
        }

        boolean[] seen = new boolean[CARD_COUNT];
        for (int i = 0; i < order.length; i += 2) {
            int first = order[i];
            int second = order[i + 1];
            if (first < 0 || first >= CARD_COUNT || second < 0 || second >= CARD_COUNT
                    || first == second || seen[first] || seen[second]
                    || cards[first] != cards[second]) {
                return false;
            }
            seen[first] = true;
            seen[second] = true;
        }
        return true;
    }

    @Override
    public PuzzleActionResult apply(LockAction action) {
        if (action.type() != LockActionType.MEMORY_SUBMIT) {
            return PuzzleActionResult.WRONG;
        }
        if (isValidOrder(action.sequence())) {
            complete = true;
            return PuzzleActionResult.SOLVED;
        }
        return PuzzleActionResult.WRONG;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    private static List<Item> availableItems() {
        List<ResourceLocation> keys = new ArrayList<>(BuiltInRegistries.ITEM.keySet());
        keys.sort(Comparator.comparing(ResourceLocation::toString));
        List<Item> result = new ArrayList<>();
        for (ResourceLocation key : keys) {
            String path = key.getPath();
            if (path.contains("debug") || path.contains("command") || path.contains("structure")
                    || path.contains("jigsaw") || path.equals("air") || path.equals("barrier")
                    || path.equals("light") || path.equals("knowledge_book")) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item != null && item != Items.AIR && !item.getDefaultInstance().isEmpty()) {
                result.add(item);
            }
        }
        if (result.size() < PAIR_COUNT) {
            result.clear();
            result.add(Items.IRON_INGOT);
            result.add(Items.GOLD_INGOT);
            result.add(Items.COPPER_INGOT);
            result.add(Items.REDSTONE);
            result.add(Items.LAPIS_LAZULI);
            result.add(Items.EMERALD);
            result.add(Items.DIAMOND);
            result.add(Items.QUARTZ);
        }
        return result;
    }
}

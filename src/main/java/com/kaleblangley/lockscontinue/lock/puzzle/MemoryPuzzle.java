package com.kaleblangley.lockscontinue.lock.puzzle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** A deterministic matching-pairs board whose dimensions come from the session snapshot. */
public final class MemoryPuzzle implements LockPuzzle {
    public static final int CARD_COUNT = 16;
    public static final int PAIR_COUNT = 8;

    private final int rows;
    private final int columns;
    private final Item[] pairItems;
    private final int[] cards;
    private final boolean[] matched;
    private final long previewEndTick;
    private int matchedCount;
    private boolean complete;

    public MemoryPuzzle(long seed) {
        this(seed, null);
    }

    public MemoryPuzzle(long seed, LockChallengeSnapshot challenge) {
        this(seed, challenge, 0L);
    }

    public MemoryPuzzle(long seed, LockChallengeSnapshot challenge, long startGameTime) {
        rows = challenge == null ? 4 : Math.max(2, Math.min(8, challenge.memoryRows()));
        columns = challenge == null ? 4 : Math.max(2, Math.min(8, challenge.memoryColumns()));
        int cardCount = rows * columns;
        if ((cardCount & 1) != 0) {
            throw new IllegalArgumentException("Memory board must contain an even number of cards");
        }
        int pairCount = cardCount / 2;
        pairItems = new Item[pairCount];
        cards = new int[cardCount];
        matched = new boolean[cardCount];
        int previewTicks = challenge == null ? 0
                : Math.max(0, challenge.memoryPreviewTicks()
                + pairCount * challenge.memoryPreviewTicksPerPair());
        previewEndTick = startGameTime + previewTicks;
        Random random = new Random(seed ^ 0x4D454D4F52594BL);
        List<Item> candidates = availableItems(pairCount);
        if (candidates.size() < pairCount) {
            // Reusing an icon would create visually indistinguishable pairs
            // with different pair ids. A matching board must never do that.
            throw new IllegalStateException("Not enough distinct items for memory board: "
                    + pairCount + " pairs require " + pairCount + " items, but only "
                    + candidates.size() + " are registered");
        }
        Collections.shuffle(candidates, random);
        for (int i = 0; i < pairCount; i++) {
            // Each pair receives a different item. The pair id is still used
            // for validation, while the item identity is now unique on the
            // board as well.
            pairItems[i] = candidates.get(i);
            cards[i * 2] = i;
            cards[i * 2 + 1] = i;
        }
        for (int i = cardCount - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            int value = cards[i];
            cards[i] = cards[other];
            cards[other] = value;
        }
    }

    public int rows() {
        return rows;
    }

    public int columns() {
        return columns;
    }

    public int cardCount() {
        return cards.length;
    }

    public int pairCount() {
        return pairItems.length;
    }

    public int matchedCount() {
        return matchedCount;
    }

    public ItemStack itemAt(int cardIndex) {
        if (cardIndex < 0 || cardIndex >= cards.length) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(pairItems[cards[cardIndex]]);
    }

    public int pairAt(int cardIndex) {
        return cards[cardIndex];
    }

    @Override
    public PuzzleActionResult apply(LockAction action) {
        return applyAction(action);
    }

    @Override
    public PuzzleActionResult apply(LockAction action, long gameTime) {
        // Preview is a server-side rule. The client disables clicks during the
        // preview, but a forged packet must not be able to skip it.
        if (gameTime < previewEndTick) {
            return PuzzleActionResult.REJECTED;
        }
        return applyAction(action);
    }

    private PuzzleActionResult applyAction(LockAction action) {
        if (action.type() == LockActionType.MEMORY_PAIR) {
            int first = action.first();
            int second = action.second();
            if (first < 0 || first >= cards.length || second < 0 || second >= cards.length
                    || first == second || matched[first] || matched[second]) {
                return PuzzleActionResult.REJECTED;
            }
            if (cards[first] != cards[second]) {
                return PuzzleActionResult.MISTAKE;
            }
            matched[first] = true;
            matched[second] = true;
            matchedCount += 2;
            if (matchedCount == cards.length) {
                complete = true;
                return PuzzleActionResult.SOLVED;
            }
            return PuzzleActionResult.PROGRESS;
        }
        // Legacy bulk submissions have no current-session implementation. A
        // memory lock can only advance one pair at a time; migration data must
        // not create a network bypass.
        return PuzzleActionResult.REJECTED;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    private static List<Item> availableItems(int required) {
        List<ResourceLocation> keys = new ArrayList<>(BuiltInRegistries.ITEM.keySet());
        keys.sort(Comparator.comparing(ResourceLocation::toString));
        List<Item> result = new ArrayList<>();
        Set<Item> selected = new LinkedHashSet<>();
        for (ResourceLocation key : keys) {
            String path = key.getPath();
            if (path.contains("debug") || path.contains("command") || path.contains("structure")
                    || path.contains("jigsaw") || path.equals("air") || path.equals("barrier")
                    || path.equals("light") || path.equals("knowledge_book")) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item != null && item != Items.AIR && !item.getDefaultInstance().isEmpty()
                    && selected.add(item)) {
                result.add(item);
            }
        }

        // If a modpack has very few normal display items, use the remaining
        // registered items as a last resort instead of reusing an icon. This
        // still preserves one distinct Item object per pair.
        if (result.size() < required) {
            for (ResourceLocation key : keys) {
                Item item = BuiltInRegistries.ITEM.get(key);
                if (item != null && item != Items.AIR && selected.add(item)) {
                    result.add(item);
                }
            }
        }
        return result;
    }
}

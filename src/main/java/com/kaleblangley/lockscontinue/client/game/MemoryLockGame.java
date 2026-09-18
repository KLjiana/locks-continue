package com.kaleblangley.lockscontinue.client.game;

import com.kaleblangley.lockscontinue.client.LockScreen;
import com.kaleblangley.lockscontinue.client.LockSoundHelper;
import com.kaleblangley.lockscontinue.lock.puzzle.LockAction;
import com.kaleblangley.lockscontinue.lock.puzzle.MemoryPuzzle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Four-by-four matching-pairs game with preview and mismatch lockout. */
public final class MemoryLockGame implements LockGame {
    private static final int GRID_SIZE = 4;
    private static final int PREVIEW_TICKS = 50;
    private static final int MISMATCH_TICKS = 12;

    private final MemoryPuzzle puzzle;
    private final LockActionSender sender;
    private final boolean[] revealed = new boolean[MemoryPuzzle.CARD_COUNT];
    private final boolean[] matched = new boolean[MemoryPuzzle.CARD_COUNT];
    private final int[] matchedOrder = new int[MemoryPuzzle.CARD_COUNT];
    private int matchedOrderSize;
    private int previewTicks = PREVIEW_TICKS;
    private int mismatchTicks;
    private int firstCard = -1;
    private int secondCard = -1;
    private boolean awaitingResult;

    public MemoryLockGame(long seed, LockActionSender sender) {
        this.puzzle = new MemoryPuzzle(seed);
        this.sender = sender;
    }

    @Override
    public void tick() {
        if (previewTicks > 0) {
            previewTicks--;
        }
        if (mismatchTicks > 0 && --mismatchTicks == 0) {
            if (firstCard >= 0) revealed[firstCard] = false;
            if (secondCard >= 0) revealed[secondCard] = false;
            firstCard = -1;
            secondCard = -1;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                       int left, int top, int width, int height) {
        int side = Math.min(Math.min((width - 18) / GRID_SIZE, (height - 18) / GRID_SIZE), 58);
        side = Math.max(18, side);
        int gap = Math.max(3, side / 12);
        int boardSize = side * GRID_SIZE + gap * (GRID_SIZE - 1);
        int boardLeft = left + (width - boardSize) / 2;
        int boardTop = top + (height - boardSize) / 2;
        boolean preview = previewTicks > 0;

        for (int index = 0; index < MemoryPuzzle.CARD_COUNT; index++) {
            int x = boardLeft + (index % GRID_SIZE) * (side + gap);
            int y = boardTop + (index / GRID_SIZE) * (side + gap);
            boolean faceUp = preview || revealed[index] || matched[index];
            graphics.blit(LockScreen.GUI_TEXTURE, x, y, side, side,
                    faceUp ? 0.0F : 19.0F, 49.0F, 17, 17, 48, 66);
            if (faceUp) {
                int itemX = x + side / 2 - 8;
                int itemY = y + side / 2 - 8;
                graphics.renderFakeItem(puzzle.itemAt(index), itemX, itemY);
            }
        }

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int left, int top, int width, int height) {
        if (button != 0 || previewTicks > 0 || mismatchTicks > 0 || awaitingResult) {
            return false;
        }

        int side = Math.min(Math.min((width - 18) / GRID_SIZE, (height - 18) / GRID_SIZE), 58);
        side = Math.max(18, side);
        int gap = Math.max(3, side / 12);
        int boardSize = side * GRID_SIZE + gap * (GRID_SIZE - 1);
        int boardLeft = left + (width - boardSize) / 2;
        int boardTop = top + (height - boardSize) / 2;
        int column = (int) ((mouseX - boardLeft) / (side + gap));
        int row = (int) ((mouseY - boardTop) / (side + gap));
        if (column < 0 || column >= GRID_SIZE || row < 0 || row >= GRID_SIZE) {
            return false;
        }
        int localX = (int) (mouseX - boardLeft) - column * (side + gap);
        int localY = (int) (mouseY - boardTop) - row * (side + gap);
        if (localX >= side || localY >= side) {
            return false;
        }

        int index = row * GRID_SIZE + column;
        if (matched[index] || revealed[index]) {
            return false;
        }
        revealed[index] = true;
        LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.CARD_FLIP.get(), 5.0F, 1.0F);
        if (firstCard < 0) {
            firstCard = index;
            return true;
        }

        secondCard = index;
        if (puzzle.pairAt(firstCard) == puzzle.pairAt(secondCard)) {
            LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.CARD_WIN.get(), 0.8F, 1.0F);
            matched[firstCard] = true;
            matched[secondCard] = true;
            matchedOrder[matchedOrderSize++] = firstCard;
            matchedOrder[matchedOrderSize++] = secondCard;
            revealed[firstCard] = false;
            revealed[secondCard] = false;
            firstCard = -1;
            secondCard = -1;
            if (matchedOrderSize == MemoryPuzzle.CARD_COUNT) {
                awaitingResult = true;
                sender.send(LockAction.memorySubmit(matchedOrder));
            }
        } else {
            mismatchTicks = MISMATCH_TICKS;
            LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.CARD_FAIL.get(), 0.75F, 1.0F);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button,
                                 int left, int top, int width, int height) {
        return false;
    }

    @Override
    public float progress() {
        return matchedOrderSize / (float) MemoryPuzzle.CARD_COUNT;
    }

    @Override
    public Component status() {
        return Component.translatable("lock.lockscontinue.memory_status",
                matchedOrderSize / 2, MemoryPuzzle.PAIR_COUNT);
    }

    @Override
    public void renderStatus(GuiGraphics graphics, Font font, int centerX, int centerY) {
        Component message;
        int color;
        if (previewTicks > 0) {
            message = Component.translatable("lock.lockscontinue.memory_preview");
            color = 0xFFE8E8E8;
        } else if (mismatchTicks > 0) {
            message = Component.translatable("lock.lockscontinue.memory_mismatch");
            color = 0xFFFFB0A0;
        } else {
            message = status();
            color = 0xFFD8C8B0;
        }
        graphics.drawCenteredString(font, message, centerX,
                centerY - font.lineHeight / 2, color);
    }

    @Override
    public void onServerFailure() {
        awaitingResult = false;
        LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.CARD_FAIL.get(), 0.75F, 1.0F);
        previewTicks = 0;
        mismatchTicks = 0;
        firstCard = -1;
        secondCard = -1;
        matchedOrderSize = 0;
        for (int i = 0; i < MemoryPuzzle.CARD_COUNT; i++) {
            revealed[i] = false;
            matched[i] = false;
        }
    }
}

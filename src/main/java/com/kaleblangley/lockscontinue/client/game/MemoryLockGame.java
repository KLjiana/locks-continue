package com.kaleblangley.lockscontinue.client.game;

import com.kaleblangley.lockscontinue.client.LockScreen;
import com.kaleblangley.lockscontinue.client.LockSoundHelper;
import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;
import com.kaleblangley.lockscontinue.lock.puzzle.LockAction;
import com.kaleblangley.lockscontinue.lock.puzzle.MemoryPuzzle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

/** Matching-pairs game with a server-snapshotted, dynamic board size. */
public final class MemoryLockGame implements LockGame {
    private static final int PREVIEW_TICKS = 50;
    private static final int MISMATCH_TICKS = 12;
    private static final int HINT_DURATION_TICKS = 60;

    private final MemoryPuzzle puzzle;
    private final LockActionSender sender;
    private final float soundVolumeMultiplier;
    private final int hintPairSeed;
    private final boolean[] revealed;
    private final boolean[] matched;
    private final int[] matchedOrder;
    private final float[] flipProgress;
    private final float flipResponse;
    private int hintsRemaining;
    private int activeHintPair = -1;
    private int hintTicks;
    private int matchedOrderSize;
    private int previewTicks = PREVIEW_TICKS;
    private int mismatchTicks;
    private int firstCard = -1;
    private int secondCard = -1;
    private int pendingMatchFirst = -1;
    private int pendingMatchSecond = -1;
    private boolean awaitingResult;

    public MemoryLockGame(long seed, LockActionSender sender) {
        this(seed, null, sender);
    }

    public MemoryLockGame(long seed, LockChallengeSnapshot challenge, LockActionSender sender) {
        this.puzzle = new MemoryPuzzle(seed, challenge);
        this.sender = sender;
        this.soundVolumeMultiplier = challenge != null && challenge.hasFlag("sound_reduced")
                ? 0.35F : 1.0F;
        int availableHints = challenge == null ? 0
                : challenge.memoryHintCount() + challenge.toolStats().insight();
        this.hintsRemaining = Math.max(0, availableHints);
        this.hintPairSeed = availableHints > 0
                ? Math.floorMod((int) (seed ^ (seed >>> 32)), puzzle.pairCount()) : -1;
        int speed = challenge == null ? 0 : challenge.toolStats().speed();
        this.flipResponse = Math.min(0.9F, 0.24F + speed * 0.11F);
        this.previewTicks = challenge == null ? PREVIEW_TICKS
                : Math.max(0, challenge.memoryPreviewTicks()
                + puzzle.pairCount() * challenge.memoryPreviewTicksPerPair());
        this.revealed = new boolean[puzzle.cardCount()];
        this.matched = new boolean[puzzle.cardCount()];
        this.matchedOrder = new int[puzzle.cardCount()];
        this.flipProgress = new float[puzzle.cardCount()];
        Arrays.fill(this.flipProgress, 1.0F);
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
        if (hintTicks > 0 && --hintTicks == 0) {
            activeHintPair = -1;
        }
        if (activeHintPair >= 0 && !hasUnmatchedCardForPair(activeHintPair)) {
            activeHintPair = -1;
            hintTicks = 0;
        }
        boolean preview = previewTicks > 0;
        for (int index = 0; index < flipProgress.length; index++) {
            boolean faceUp = preview || revealed[index] || matched[index];
            float target = faceUp ? 1.0F : 0.0F;
            float delta = target - flipProgress[index];
            flipProgress[index] += delta * flipResponse;
            if (Math.abs(delta) < 0.01F) {
                flipProgress[index] = target;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                       int left, int top, int width, int height) {
        int side = cardSide(width, height);
        int gap = Math.max(3, side / 12);
        int boardWidth = side * puzzle.columns() + gap * (puzzle.columns() - 1);
        int boardHeight = side * puzzle.rows() + gap * (puzzle.rows() - 1);
        int boardLeft = left + (width - boardWidth) / 2;
        int boardTop = top + (height - boardHeight) / 2 - 12;
        boolean preview = previewTicks > 0;
        int hintPair = preview ? -1 : activeHintPair;

        for (int index = 0; index < puzzle.cardCount(); index++) {
            int x = boardLeft + (index % puzzle.columns()) * (side + gap);
            int y = boardTop + (index / puzzle.columns()) * (side + gap);
            boolean faceUp = preview || revealed[index] || matched[index];
            float progress = Math.max(0.0F, Math.min(1.0F, flipProgress[index]));
            float widthFactor = Math.max(0.06F, Math.abs(progress * 2.0F - 1.0F));
            int drawWidth = Math.max(1, Math.round(side * widthFactor));
            int drawLeft = x + (side - drawWidth) / 2;
            graphics.blit(LockScreen.GUI_TEXTURE, drawLeft, y, drawWidth, side,
                    progress >= 0.5F ? 0.0F : 19.0F, 49.0F, 17, 17, 48, 66);
            if (faceUp && progress >= 0.5F && drawWidth >= side / 2) {
                int itemX = x + side / 2 - 8;
                int itemY = y + side / 2 - 8;
                graphics.renderFakeItem(puzzle.itemAt(index), itemX, itemY);
            }
            if (hintPair >= 0 && !matched[index] && puzzle.pairAt(index) == hintPair) {
                graphics.fill(x, y, x + side, y + 1, 0xFF72D6FF);
                graphics.fill(x, y + side - 1, x + side, y + side, 0xFF72D6FF);
                graphics.fill(x, y, x + 1, y + side, 0xFF72D6FF);
                graphics.fill(x + side - 1, y, x + side, y + side, 0xFF72D6FF);
            }
        }

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int left, int top, int width, int height) {
        if (button != 0 || previewTicks > 0 || mismatchTicks > 0 || awaitingResult) {
            return false;
        }

        int side = cardSide(width, height);
        int gap = Math.max(3, side / 12);
        int boardWidth = side * puzzle.columns() + gap * (puzzle.columns() - 1);
        int boardHeight = side * puzzle.rows() + gap * (puzzle.rows() - 1);
        int boardLeft = left + (width - boardWidth) / 2;
        int boardTop = top + (height - boardHeight) / 2 - 12;
        int column = (int) ((mouseX - boardLeft) / (side + gap));
        int row = (int) ((mouseY - boardTop) / (side + gap));
        if (column < 0 || column >= puzzle.columns() || row < 0 || row >= puzzle.rows()) {
            return false;
        }
        int localX = (int) (mouseX - boardLeft) - column * (side + gap);
        int localY = (int) (mouseY - boardTop) - row * (side + gap);
        if (localX >= side || localY >= side) {
            return false;
        }

        int index = row * puzzle.columns() + column;
        if (matched[index] || revealed[index]) {
            return false;
        }
        revealed[index] = true;
        LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.CARD_FLIP.get(),
                5.0F * soundVolumeMultiplier, 1.0F);
        if (firstCard < 0) {
            firstCard = index;
            return true;
        }

        secondCard = index;
        if (puzzle.pairAt(firstCard) == puzzle.pairAt(secondCard)) {
            LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.CARD_WIN.get(),
                    0.8F * soundVolumeMultiplier, 1.0F);
            matched[firstCard] = true;
            matched[secondCard] = true;
            matchedOrder[matchedOrderSize++] = firstCard;
            matchedOrder[matchedOrderSize++] = secondCard;
            pendingMatchFirst = firstCard;
            pendingMatchSecond = secondCard;
            revealed[firstCard] = false;
            revealed[secondCard] = false;
            firstCard = -1;
            secondCard = -1;
            awaitingResult = true;
            sender.send(LockAction.memoryPair(pendingMatchFirst, pendingMatchSecond));
        } else {
            mismatchTicks = MISMATCH_TICKS;
            awaitingResult = true;
            sender.send(LockAction.memoryPair(firstCard, secondCard));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button,
                                 int left, int top, int width, int height) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_H) {
            return false;
        }
        if (previewTicks > 0 || mismatchTicks > 0 || awaitingResult
                || activeHintPair >= 0 || hintsRemaining <= 0) {
            return true;
        }
        int pair = nextHintPair();
        if (pair >= 0) {
            hintsRemaining--;
            activeHintPair = pair;
            hintTicks = HINT_DURATION_TICKS;
        }
        return true;
    }

    @Override
    public float progress() {
        return matchedOrderSize / (float) puzzle.cardCount();
    }

    @Override
    public Component status() {
        return Component.translatable("lock.lockscontinue.memory_status",
                matchedOrderSize / 2, puzzle.pairCount());
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
                centerY - font.lineHeight, color);
        Component hintMessage = Component.translatable(
                activeHintPair >= 0 ? "lock.lockscontinue.memory_hint_active"
                        : "lock.lockscontinue.memory_hint",
                hintsRemaining, hintSeconds());
        graphics.drawCenteredString(font, hintMessage, centerX,
                centerY + 2, activeHintPair >= 0 ? 0xFF72D6FF : 0xFFB9B0A0);
    }

    @Override
    public void onServerFailure() {
        awaitingResult = false;
        activeHintPair = -1;
        hintTicks = 0;
        pendingMatchFirst = -1;
        pendingMatchSecond = -1;
        LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.CARD_FAIL.get(),
                0.75F * soundVolumeMultiplier, 1.0F);
        previewTicks = 0;
        mismatchTicks = 0;
        firstCard = -1;
        secondCard = -1;
        matchedOrderSize = 0;
        for (int i = 0; i < puzzle.cardCount(); i++) {
            revealed[i] = false;
            matched[i] = false;
            flipProgress[i] = 0.0F;
        }
    }

    @Override
    public void onServerProgress() {
        pendingMatchFirst = -1;
        pendingMatchSecond = -1;
        awaitingResult = false;
    }

    @Override
    public void onServerMistake() {
        if (pendingMatchFirst >= 0) {
            rollbackPendingMatch();
        }
        pendingMatchFirst = -1;
        pendingMatchSecond = -1;
        awaitingResult = false;
        activeHintPair = -1;
        hintTicks = 0;
        LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.CARD_FAIL.get(),
                0.75F * soundVolumeMultiplier, 1.0F);
    }

    @Override
    public void onServerRejected() {
        if (pendingMatchFirst >= 0) {
            rollbackPendingMatch();
        }
        pendingMatchFirst = -1;
        pendingMatchSecond = -1;
        awaitingResult = false;
        if (firstCard >= 0) {
            revealed[firstCard] = false;
        }
        if (secondCard >= 0) {
            revealed[secondCard] = false;
        }
        firstCard = -1;
        secondCard = -1;
        mismatchTicks = 0;
    }

    private void rollbackPendingMatch() {
        if (pendingMatchFirst >= 0 && pendingMatchFirst < matched.length) {
            matched[pendingMatchFirst] = false;
        }
        if (pendingMatchSecond >= 0 && pendingMatchSecond < matched.length) {
            matched[pendingMatchSecond] = false;
        }
        matchedOrderSize = Math.max(0, matchedOrderSize - 2);
    }

    private int cardSide(int width, int height) {
        int columns = puzzle.columns();
        int rows = puzzle.rows();
        int horizontal = Math.max(1, (width - 18 - (columns - 1) * 3) / columns);
        int vertical = Math.max(1, (height - 18 - (rows - 1) * 3) / rows);
        int maximum = columns >= 8 || rows >= 8 ? 34 : 58;
        return Math.max(12, Math.min(Math.min(horizontal, vertical), maximum));
    }

    private int nextHintPair() {
        for (int offset = 0; offset < puzzle.pairCount(); offset++) {
            int pair = Math.floorMod(hintPairSeed + offset, puzzle.pairCount());
            boolean remaining = false;
            for (int index = 0; index < puzzle.cardCount(); index++) {
                if (!matched[index] && puzzle.pairAt(index) == pair) {
                    remaining = true;
                    break;
                }
            }
            if (remaining) {
                return pair;
            }
        }
        return -1;
    }

    private boolean hasUnmatchedCardForPair(int pair) {
        for (int index = 0; index < puzzle.cardCount(); index++) {
            if (!matched[index] && puzzle.pairAt(index) == pair) {
                return true;
            }
        }
        return false;
    }

    private int hintSeconds() {
        return Math.max(0, (hintTicks + 19) / 20);
    }
}

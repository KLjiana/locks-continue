package com.kaleblangley.lockscontinue.client.game;

import com.kaleblangley.lockscontinue.lock.puzzle.LockAction;
import com.kaleblangley.lockscontinue.lock.puzzle.PipePuzzle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Rotatable pipe network. Connectivity is calculated by the shared puzzle. */
public final class PipeLockGame implements LockGame {
    private final PipePuzzle puzzle;
    private final LockActionSender sender;
    private boolean awaitingResult;

    public PipeLockGame(long seed, LockActionSender sender) {
        this.puzzle = new PipePuzzle(seed);
        this.sender = sender;
    }

    @Override
    public void tick() {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                       int left, int top, int width, int height) {
        int boardSize = Math.min(Math.min(width - 12, height - 12), 300);
        int cell = Math.max(12, boardSize / PipePuzzle.SIZE);
        boardSize = cell * PipePuzzle.SIZE;
        int boardLeft = left + (width - boardSize) / 2;
        int boardTop = top + (height - boardSize) / 2;

        for (int index = 0; index < PipePuzzle.CELL_COUNT; index++) {
            int x = boardLeft + (index % PipePuzzle.SIZE) * cell;
            int y = boardTop + (index / PipePuzzle.SIZE) * cell;
            boolean start = index == puzzle.startIndex();
            boolean end = index == puzzle.endIndex();
            graphics.fill(x + 1, y + 1, x + cell - 1, y + cell - 1,
                    start ? 0xFF315D4B : end ? 0xFF6B4444 : 0xFF24252B);
            graphics.fill(x, y, x + cell, y + 1, 0xFF665E59);
            graphics.fill(x, y + cell - 1, x + cell, y + cell, 0xFF665E59);
            graphics.fill(x, y, x + 1, y + cell, 0xFF665E59);
            graphics.fill(x + cell - 1, y, x + cell, y + cell, 0xFF665E59);
            drawPipe(graphics, x, y, cell, puzzle.mask(index),
                    puzzle.isConnected() && (start || end) ? 0xFF7CFFAE : 0xFFE1B66D);
        }

    }

    private static void drawPipe(GuiGraphics graphics, int x, int y, int cell, int mask, int color) {
        int centerX = x + cell / 2;
        int centerY = y + cell / 2;
        int thickness = Math.max(3, cell / 5);
        int half = thickness / 2;
        graphics.fill(centerX - half, centerY - half, centerX + half + 1, centerY + half + 1, color);
        if ((mask & 1) != 0) {
            graphics.fill(centerX - half, y + 1, centerX + half + 1, centerY + 1, color);
        }
        if ((mask & 2) != 0) {
            graphics.fill(centerX, centerY - half, x + cell - 1, centerY + half + 1, color);
        }
        if ((mask & 4) != 0) {
            graphics.fill(centerX - half, centerY, centerX + half + 1, y + cell - 1, color);
        }
        if ((mask & 8) != 0) {
            graphics.fill(x + 1, centerY - half, centerX + 1, centerY + half + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int left, int top, int width, int height) {
        if (awaitingResult || (button != 0 && button != 1)) {
            return false;
        }

        int boardSize = Math.min(Math.min(width - 12, height - 12), 300);
        int cell = Math.max(12, boardSize / PipePuzzle.SIZE);
        boardSize = cell * PipePuzzle.SIZE;
        int boardLeft = left + (width - boardSize) / 2;
        int boardTop = top + (height - boardSize) / 2;
        int column = (int) ((mouseX - boardLeft) / cell);
        int row = (int) ((mouseY - boardTop) / cell);
        if (column < 0 || column >= PipePuzzle.SIZE || row < 0 || row >= PipePuzzle.SIZE) {
            return false;
        }

        int index = row * PipePuzzle.SIZE + column;
        if (puzzle.isEndpoint(index)) {
            return true;
        }

        int direction = button == 1 ? -1 : 1;
        puzzle.rotate(index, direction);
        sender.send(LockAction.pipeRotate(index, direction));
        if (puzzle.isConnected()) {
            awaitingResult = true;
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
        return puzzle.isConnected() ? 1.0F : 0.0F;
    }

    @Override
    public Component status() {
        return Component.translatable(puzzle.isConnected()
                ? "lock.lockscontinue.pipe_connected" : "lock.lockscontinue.pipe_status");
    }

    @Override
    public void renderStatus(GuiGraphics graphics, Font font, int centerX, int centerY) {
        Component message = awaitingResult
                ? Component.translatable("lock.lockscontinue.waiting") : status();
        graphics.drawCenteredString(font, message, centerX,
                centerY - font.lineHeight / 2, 0xFFE8E8E8);
    }

    @Override
    public void onServerFailure() {
        awaitingResult = false;
    }
}

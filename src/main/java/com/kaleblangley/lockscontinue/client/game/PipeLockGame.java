package com.kaleblangley.lockscontinue.client.game;

import com.mojang.math.Axis;
import com.kaleblangley.lockscontinue.lock.puzzle.LockAction;
import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;
import com.kaleblangley.lockscontinue.lock.puzzle.PipePuzzle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Rotatable pipe network. Connectivity is calculated by the shared puzzle. */
public final class PipeLockGame implements LockGame {
    private static final int HINT_DURATION_TICKS = 60;
    /** Draw the pipe shape in a stable logical space, then scale it with the float cell size. */
    private static final int PIPE_LOCAL_SIZE = 100;
    private static final float MIN_CELL_SIZE = 12.0F;
    private final PipePuzzle puzzle;
    private final LockActionSender sender;
    private final float rotationResponse;
    private final float[] visualRotations;
    private int hintsRemaining;
    private int hintTicks;
    private int activeHintIndex = -1;
    private boolean awaitingResult;

    public PipeLockGame(long seed, LockActionSender sender) {
        this(seed, null, sender);
    }

    public PipeLockGame(long seed, LockChallengeSnapshot challenge, LockActionSender sender) {
        this.puzzle = new PipePuzzle(seed, challenge);
        this.sender = sender;
        this.hintsRemaining = challenge == null ? 0
                : Math.max(0, challenge.pipeHintCount() + challenge.toolStats().insight());
        int speed = challenge == null ? 0 : challenge.toolStats().speed();
        this.rotationResponse = Math.min(0.9F, 0.24F + speed * 0.11F);
        this.visualRotations = new float[puzzle.cellCount()];
        for (int index = 0; index < visualRotations.length; index++) {
            visualRotations[index] = puzzle.rotation(index);
        }
    }

    @Override
    public void tick() {
        if (hintTicks > 0 && --hintTicks == 0) {
            activeHintIndex = -1;
        }
        for (int index = 0; index < visualRotations.length; index++) {
            float target = puzzle.rotation(index);
            float delta = target - visualRotations[index];
            while (delta > 2.0F) {
                delta -= 4.0F;
            }
            while (delta < -2.0F) {
                delta += 4.0F;
            }
            visualRotations[index] += delta * rotationResponse;
            if (Math.abs(delta) < 0.01F) {
                visualRotations[index] = target;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                       int left, int top, int width, int height) {
        BoardLayout board = boardLayout(left, top, width, height,
                puzzle.width(), puzzle.height());

        int hintIndex = !puzzle.isConnected() && hintTicks > 0 ? activeHintIndex : -1;

        // Keep the cell background edges in screen space so their one-pixel
        // frame remains readable at every GUI scale.
        for (int index = 0; index < puzzle.cellCount(); index++) {
            float x = board.left() + (index % puzzle.width()) * board.cell();
            float y = board.top() + (index / puzzle.width()) * board.cell();
            float cell = board.cell();
            boolean start = index == puzzle.startIndex();
            boolean end = puzzle.isTerminal(index) && !start;
            fillFloat(graphics, x + 1.0F, y + 1.0F,
                    x + cell - 1.0F, y + cell - 1.0F,
                    start ? 0xFF315D4B : end ? 0xFF6B4444 : 0xFF24252B);
            fillFloat(graphics, x, y, x + cell, y + 1.0F, 0xFF665E59);
            fillFloat(graphics, x, y + cell - 1.0F, x + cell, y + cell, 0xFF665E59);
            fillFloat(graphics, x, y, x + 1.0F, y + cell, 0xFF665E59);
            fillFloat(graphics, x + cell - 1.0F, y, x + cell, y + cell, 0xFF665E59);
        }

        /*
         * Render every tile from one shared float transform. Creating a
         * separate transform from rounded screen coordinates can make the
         * two outer edges drift apart when the board has an odd number of
         * columns and the cell size is fractional.
         */
        graphics.pose().pushPose();
        graphics.pose().translate(board.left(), board.top(), 0.0F);
        graphics.pose().scale(board.cell() / PIPE_LOCAL_SIZE,
                board.cell() / PIPE_LOCAL_SIZE, 1.0F);
        for (int index = 0; index < puzzle.cellCount(); index++) {
            int x = (index % puzzle.width()) * PIPE_LOCAL_SIZE;
            int y = (index / puzzle.width()) * PIPE_LOCAL_SIZE;
            boolean start = index == puzzle.startIndex();
            boolean end = puzzle.isTerminal(index) && !start;
            graphics.pose().pushPose();
            graphics.pose().translate(x + PIPE_LOCAL_SIZE / 2.0F,
                    y + PIPE_LOCAL_SIZE / 2.0F, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(visualRotations[index] * 90.0F));
            graphics.pose().translate(-PIPE_LOCAL_SIZE / 2.0F,
                    -PIPE_LOCAL_SIZE / 2.0F, 0.0F);
            drawPipe(graphics, PIPE_LOCAL_SIZE, puzzle.type(index).mask(0),
                    puzzle.isConnected() && (start || end) ? 0xFF7CFFAE : 0xFFE1B66D);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();

        if (hintIndex >= 0) {
            float x = board.left() + (hintIndex % puzzle.width()) * board.cell();
            float y = board.top() + (hintIndex / puzzle.width()) * board.cell();
            float cell = board.cell();
            fillFloat(graphics, x + 2.0F, y + 2.0F,
                    x + cell - 2.0F, y + 3.0F, 0xFF72D6FF);
            fillFloat(graphics, x + 2.0F, y + cell - 3.0F,
                    x + cell - 2.0F, y + cell - 2.0F, 0xFF72D6FF);
            fillFloat(graphics, x + 2.0F, y + 2.0F,
                    x + 3.0F, y + cell - 2.0F, 0xFF72D6FF);
            fillFloat(graphics, x + cell - 3.0F, y + 2.0F,
                    x + cell - 2.0F, y + cell - 2.0F, 0xFF72D6FF);
        }
    }

    private static void drawPipe(GuiGraphics graphics, int cell, int mask, int color) {
        if (mask == 0) {
            return;
        }
        int centerX = cell / 2;
        int centerY = cell / 2;
        int thickness = Math.max(3, cell / 5);
        int half = thickness / 2;
        graphics.fill(centerX - half, centerY - half, centerX + half + 1, centerY + half + 1, color);
        if ((mask & 1) != 0) {
            graphics.fill(centerX - half, 0, centerX + half + 1, centerY + 1, color);
        }
        if ((mask & 2) != 0) {
            graphics.fill(centerX, centerY - half, cell, centerY + half + 1, color);
        }
        if ((mask & 4) != 0) {
            graphics.fill(centerX - half, centerY, centerX + half + 1, cell, color);
        }
        if ((mask & 8) != 0) {
            graphics.fill(0, centerY - half, centerX + 1, centerY + half + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int left, int top, int width, int height) {
        if (awaitingResult || (button != 0 && button != 1)) {
            return false;
        }

        BoardLayout board = boardLayout(left, top, width, height,
                puzzle.width(), puzzle.height());
        int column = (int) Math.floor((mouseX - board.left()) / board.cell());
        int row = (int) Math.floor((mouseY - board.top()) / board.cell());
        if (column < 0 || column >= puzzle.width() || row < 0 || row >= puzzle.height()) {
            return false;
        }

        int index = row * puzzle.width() + column;
        if (!puzzle.isRotatable(index)) {
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_H) {
            return false;
        }
        if (awaitingResult || hintTicks > 0 || hintsRemaining <= 0) {
            return true;
        }
        int index = puzzle.hintIndex();
        if (index >= 0) {
            hintsRemaining--;
            activeHintIndex = index;
            hintTicks = HINT_DURATION_TICKS;
        }
        return true;
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
                centerY - font.lineHeight, 0xFFE8E8E8);
        Component hintMessage = Component.translatable(
                hintTicks > 0 ? "lock.lockscontinue.pipe_hint_active"
                        : "lock.lockscontinue.pipe_hint",
                hintsRemaining, hintSeconds());
        graphics.drawCenteredString(font, hintMessage, centerX,
                centerY + 2, hintTicks > 0 ? 0xFF72D6FF : 0xFFB9B0A0);
    }

    @Override
    public void onServerFailure() {
        awaitingResult = false;
        activeHintIndex = -1;
        hintTicks = 0;
    }

    private int hintSeconds() {
        return Math.max(0, (hintTicks + 19) / 20);
    }

    private static BoardLayout boardLayout(int left, int top, int width, int height,
                                           int columns, int rows) {
        float boardSize = Math.min(Math.min((float) width - 12.0F,
                (float) height - 12.0F), 300.0F);
        float cell = Math.max(MIN_CELL_SIZE,
                Math.min(boardSize / columns, boardSize / rows));
        float boardWidth = cell * columns;
        float boardHeight = cell * rows;
        return new BoardLayout(
                left + ((float) width - boardWidth) / 2.0F,
                top + ((float) height - boardHeight) / 2.0F - 12.0F,
                cell
        );
    }

    private static void fillFloat(GuiGraphics graphics, float minX, float minY,
                                  float maxX, float maxY, int color) {
        int left = Math.round(minX);
        int top = Math.round(minY);
        int right = Math.max(left + 1, Math.round(maxX));
        int bottom = Math.max(top + 1, Math.round(maxY));
        graphics.fill(left, top, right, bottom, color);
    }

    private record BoardLayout(float left, float top, float cell) {
    }
}

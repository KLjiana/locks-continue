package com.kaleblangley.lockscontinue.client.game;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Client-side presentation and input contract for one lock puzzle. */
public interface LockGame {
    void tick();

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                int left, int top, int width, int height);

    boolean mouseClicked(double mouseX, double mouseY, int button,
                         int left, int top, int width, int height);

    boolean mouseReleased(double mouseX, double mouseY, int button,
                          int left, int top, int width, int height);

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    float progress();

    Component status();

    /** Draws the game's dynamic text in the outer status bar. */
    default void renderStatus(GuiGraphics graphics, Font font, int centerX, int centerY) {
        graphics.drawCenteredString(font, status(), centerX,
                centerY - font.lineHeight / 2, 0xFFD8C8B0);
    }

    default boolean isCompleted() {
        return progress() >= 1.0F;
    }

    default boolean isFailed() {
        return false;
    }

    /** Lets the game recover after the server rejects an attempted solution. */
    default void onServerFailure() {
    }

    /** A legal puzzle mistake that consumed one configured attempt. */
    default void onServerMistake() {
        onServerFailure();
    }

    /** Confirms a non-final puzzle step accepted by the authoritative server. */
    default void onServerProgress() {
    }

    /** Confirms a legal action that changed server state without a stage update. */
    default void onServerUpdated() {
    }

    /** Reconciles an invalid or stale action without consuming durability. */
    default void onServerRejected() {
    }
}

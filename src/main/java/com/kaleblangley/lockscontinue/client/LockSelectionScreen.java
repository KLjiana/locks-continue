package com.kaleblangley.lockscontinue.client;

import com.kaleblangley.lockscontinue.lock.LockData;
import com.kaleblangley.lockscontinue.lock.LockType;
import com.kaleblangley.lockscontinue.network.LockNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Client-side selector for choosing which lock to install on a container. */
public final class LockSelectionScreen extends Screen {
    private final BlockPos pos;
    private final InteractionHand hand;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int optionTop;
    private int optionWidth;
    private int optionHeight;
    private int optionGap;
    private boolean selecting;
    private int messageTicks;

    public LockSelectionScreen(LockNetwork.OpenLockSelectionPacket packet) {
        super(Component.translatable("screen.lockscontinue.select_lock"));
        this.pos = packet.pos();
        this.hand = packet.hand();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(this.width - 20, Math.max(240, this.width * 2 / 3));
        panelHeight = Math.min(this.height - 20, Math.max(190, this.height * 2 / 3));
        panelWidth = Math.max(1, panelWidth);
        panelHeight = Math.max(1, panelHeight);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        headerHeight = LockGuiRenderer.headerHeight(panelHeight);

        int margin = Math.max(10, panelWidth / 24);
        optionGap = Math.max(6, panelWidth / 48);
        optionWidth = Math.max(1, (panelWidth - margin * 2 - optionGap) / 2);
        int availableHeight = Math.max(1, panelHeight - headerHeight - 54);
        optionHeight = Math.max(42, Math.min(64, (availableHeight - optionGap) / 2));
        optionTop = panelTop + headerHeight + 34;
    }

    @Override
    public void tick() {
        if (messageTicks > 0) {
            messageTicks--;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        renderGuiBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2,
                panelTop + headerHeight + 8, 0xFFF2D9A6);

        for (LockType type : LockType.values()) {
            int index = type.ordinal();
            int left = optionLeft(index);
            int top = optionTop + (index / 2) * (optionHeight + optionGap);
            boolean hovered = !selecting && isInside(mouseX, mouseY, left, top, optionWidth, optionHeight);
            renderOption(graphics, type, left, top, hovered);
        }

        if (selecting) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.lockscontinue.selecting"),
                    this.width / 2, panelTop + panelHeight - 18, 0xFFBFD7E8);
        } else if (messageTicks > 0) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.lockscontinue.selection_failed"),
                    this.width / 2, panelTop + panelHeight - 18, 0xFFFF9E8E);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderGuiBackground(GuiGraphics graphics) {
        LockGuiRenderer.render(graphics, panelLeft, panelTop, panelWidth, panelHeight);
    }

    private void renderOption(GuiGraphics graphics, LockType type, int left, int top, boolean hovered) {
        int background = selecting ? 0x66302B29 : hovered ? 0xAA6C5843 : 0x88433831;
        graphics.fill(left, top, left + optionWidth, top + optionHeight, background);
        graphics.fill(left, top, left + optionWidth, top + 1, 0xFF9E805A);
        graphics.fill(left, top + optionHeight - 1, left + optionWidth, top + optionHeight, 0xFF2A211E);
        graphics.fill(left, top, left + 1, top + optionHeight, 0xFF9E805A);
        graphics.fill(left + optionWidth - 1, top, left + optionWidth, top + optionHeight, 0xFF2A211E);

        ItemStack lockStack = new ItemStack(LockData.lockItem(type.variant()));
        graphics.renderItem(lockStack, left + 8, top + (optionHeight - 16) / 2);
        graphics.drawString(this.font, type.displayName(), left + 32,
                top + (optionHeight - this.font.lineHeight) / 2, 0xFFF2E8D5);
    }

    private int optionLeft(int index) {
        int margin = Math.max(10, panelWidth / 24);
        return panelLeft + margin + (index % 2) * (optionWidth + optionGap);
    }

    private static boolean isInside(double mouseX, double mouseY,
                                    int left, int top, int width, int height) {
        return mouseX >= left && mouseX < left + width
                && mouseY >= top && mouseY < top + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || selecting) {
            return selecting || super.mouseClicked(mouseX, mouseY, button);
        }

        for (LockType type : LockType.values()) {
            int index = type.ordinal();
            int left = optionLeft(index);
            int top = optionTop + (index / 2) * (optionHeight + optionGap);
            if (isInside(mouseX, mouseY, left, top, optionWidth, optionHeight)) {
                selecting = true;
                LockNetwork.CHANNEL.sendToServer(new LockNetwork.SelectLockPacket(
                        pos, hand, type.variant()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void handleServerResult(LockNetwork.LockSelectionResultPacket packet) {
        if (!pos.equals(packet.pos())) {
            return;
        }

        if (packet.success()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }

        selecting = false;
        messageTicks = 40;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

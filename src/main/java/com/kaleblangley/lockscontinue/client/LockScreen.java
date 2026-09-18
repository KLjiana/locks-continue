package com.kaleblangley.lockscontinue.client;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.client.game.LockActionSender;
import com.kaleblangley.lockscontinue.client.game.LockGame;
import com.kaleblangley.lockscontinue.client.game.MemoryLockGame;
import com.kaleblangley.lockscontinue.client.game.PinLockGame;
import com.kaleblangley.lockscontinue.client.game.PipeLockGame;
import com.kaleblangley.lockscontinue.client.game.RotaryLockGame;
import com.kaleblangley.lockscontinue.lock.LockType;
import com.kaleblangley.lockscontinue.lock.puzzle.LockAction;
import com.kaleblangley.lockscontinue.network.LockNetwork;
import com.kaleblangley.lockscontinue.network.LockResultType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Common shell for the four client-side lock games. */
public final class LockScreen extends Screen {
    public static final ResourceLocation GUI_TEXTURE =
            new ResourceLocation(LocksContinue.MODID, "textures/gui/gui.png");
    public static final ResourceLocation LOCKS_TEXTURE =
            new ResourceLocation(LocksContinue.MODID, "textures/gui/locks.png");

    private final long sessionId;
    private final LockType type;
    private final long puzzleSeed;
    private final ItemStack tool;
    private int attemptsLeft;
    private LockGame game;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int gameLeft;
    private int gameTop;
    private int gameWidth;
    private int gameHeight;
    private boolean closing;
    private int failureMessageTicks;

    public LockScreen(LockNetwork.OpenLockPacket packet) {
        super(packet.type().displayName());
        this.sessionId = packet.sessionId();
        this.type = packet.type();
        this.puzzleSeed = packet.puzzleSeed();
        this.attemptsLeft = packet.attemptsLeft();
        this.tool = packet.tool().copy();
    }

    @Override
    protected void init() {
        panelWidth = Math.max(1, this.width * 2 / 3);
        panelHeight = Math.max(1, this.height * 2 / 3);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        headerHeight = LockGuiRenderer.headerHeight(panelHeight);

        int statusHeight = Math.max(22, Math.round(panelHeight * 0.105F));
        gameLeft = panelLeft + Math.max(8, panelWidth / 24);
        gameTop = panelTop + headerHeight + statusHeight;
        gameWidth = Math.max(1, panelWidth - 2 * (gameLeft - panelLeft));
        gameHeight = Math.max(1, panelTop + panelHeight - gameTop - Math.max(8, panelHeight / 24));

        LockActionSender sender = this::sendAction;
        game = switch (type) {
            case PIN_LOCK -> new PinLockGame(puzzleSeed, sender);
            case PIPE_LOCK -> new PipeLockGame(puzzleSeed, sender);
            case ROTARY_LOCK -> new RotaryLockGame(puzzleSeed, tool, sender);
            case MEMORY_LOCK -> new MemoryLockGame(puzzleSeed, sender);
        };
    }

    @Override
    public void tick() {
        if (game != null) {
            game.tick();
        }
        if (failureMessageTicks > 0) {
            failureMessageTicks--;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        renderGuiBackground(graphics);
        renderStatusBar(graphics);
        if (game != null) {
            game.render(graphics, mouseX, mouseY, partialTick,
                    gameLeft, gameTop, gameWidth, gameHeight);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderGuiBackground(GuiGraphics graphics) {
        LockGuiRenderer.render(graphics, panelLeft, panelTop, panelWidth, panelHeight);
    }

    private void renderStatusBar(GuiGraphics graphics) {
        int statusTop = panelTop + (headerHeight/5);
        graphics.renderItem(tool, panelLeft + 8, statusTop + 4);
        graphics.renderItemDecorations(this.font, tool, panelLeft + 8, statusTop + 4);

        int textX = panelLeft + 30;
        graphics.drawString(this.font, type.displayName(), textX, statusTop + 4, 0xFFF2D9A6);
        graphics.drawString(this.font,
                Component.translatable("lock.lockscontinue.attempts", attemptsLeft),
                textX, statusTop + 15, 0xFFE8E8E8);

        if (game != null) {
            int statusCenterX = panelLeft + panelWidth / 2;
            int statusCenterY = statusTop + (gameTop - statusTop) / 2;
            game.renderStatus(graphics, this.font, statusCenterX, statusCenterY);
        }
        if (failureMessageTicks > 0) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("lock.lockscontinue.failure"),
                    panelLeft + panelWidth / 2, panelTop + panelHeight - 10, 0xFFFF9E8E);
        }
    }

    private void sendAction(LockAction action) {
        if (closing) {
            return;
        }
        LockNetwork.CHANNEL.sendToServer(new LockNetwork.LockActionPacket(
                sessionId, action.type(), action.first(), action.second(), action.value(), action.sequence()));
    }

    public void handleServerResult(LockNetwork.LockResultPacket packet) {
        if (packet.sessionId() != sessionId || closing) {
            return;
        }

        attemptsLeft = packet.attemptsLeft();
        if (tool.isDamageableItem()) {
            tool.setDamageValue(Math.max(0, tool.getMaxDamage() - attemptsLeft));
        }

        if (packet.result() == LockResultType.SUCCESS) {
            if (type == LockType.PIN_LOCK) {
                LockSoundHelper.playUi(com.kaleblangley.lockscontinue.sound.ModSounds.PIN_FINISH.get(), 0.8F, 1.0F);
            }
            closing = true;
            Minecraft.getInstance().setScreen(null);
            return;
        }
        if (packet.result() == LockResultType.PROGRESS) {
            if (game != null) {
                game.onServerProgress();
            }
            return;
        }
        if (packet.result() == LockResultType.EXPIRED || attemptsLeft <= 0) {
            closing = true;
            Minecraft.getInstance().setScreen(null);
            return;
        }

        failureMessageTicks = 20;
        if (game != null) {
            game.onServerFailure();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (game != null && game.mouseClicked(mouseX, mouseY, button,
                gameLeft, gameTop, gameWidth, gameHeight)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (game != null && game.mouseReleased(mouseX, mouseY, button,
                gameLeft, gameTop, gameWidth, gameHeight)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (game != null && game.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!closing) {
            closing = true;
            LockNetwork.CHANNEL.sendToServer(new LockNetwork.CancelLockPacket(sessionId));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.kaleblangley.lockscontinue.client;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.client.game.LockActionSender;
import com.kaleblangley.lockscontinue.client.game.LockGame;
import com.kaleblangley.lockscontinue.client.game.MemoryLockGame;
import com.kaleblangley.lockscontinue.client.game.PinLockGame;
import com.kaleblangley.lockscontinue.client.game.PipeLockGame;
import com.kaleblangley.lockscontinue.client.game.RotaryLockGame;
import com.kaleblangley.lockscontinue.lock.LockType;
import com.kaleblangley.lockscontinue.lock.definition.LockChallengeSnapshot;
import com.kaleblangley.lockscontinue.lock.definition.RewardMetric;
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
    private final LockChallengeSnapshot challenge;
    private int attemptsLeft;
    private int mistakesLeft;
    private int pipeTimeLeftTicks;
    private LockNetwork.RewardStatus rewardStatus;
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
    private int affixPage;
    private int affixPageCount;
    private int affixesPerPage;
    private int affixPageTop;
    private int affixPreviousLeft;
    private int affixPageLabelLeft;
    private int affixNextLeft;
    private int affixStatusLeft;
    private int affixStatusRight;
    private int affixStatusBottom;
    private boolean closing;
    private int failureMessageTicks;
    private long nextActionSequence;

    public LockScreen(LockNetwork.OpenLockPacket packet) {
        super(packet.type().displayName());
        this.sessionId = packet.sessionId();
        this.type = packet.type();
        this.puzzleSeed = packet.puzzleSeed();
        this.attemptsLeft = packet.attemptsLeft();
        this.mistakesLeft = packet.mistakesLeft();
        this.rewardStatus = packet.rewardStatus();
        this.pipeTimeLeftTicks = packet.type() == LockType.PIPE_LOCK
                ? initialPipeTime(packet.rewardStatus()) : 0;
        this.tool = packet.tool().copy();
        this.challenge = packet.challenge();
    }

    @Override
    protected void init() {
        panelWidth = Math.max(1, this.width * 2 / 3);
        panelHeight = Math.max(1, this.height * 2 / 3);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        // The status strip is the 48x15 upper slice of gui.png. Keep one
        // source of truth for that ratio instead of adding a second
        // percentage-based band below the texture header.
        int statusHeight = LockGuiRenderer.headerHeight(panelHeight);
        headerHeight = statusHeight;
        gameLeft = panelLeft + Math.max(8, panelWidth / 24);
        gameTop = panelTop + statusHeight;
        gameWidth = Math.max(1, panelWidth - 2 * (gameLeft - panelLeft));
        gameHeight = Math.max(1, panelTop + panelHeight - gameTop - Math.max(8, panelHeight / 24));

        if (game == null) {
            LockActionSender sender = this::sendAction;
            game = switch (type) {
                case PIN_LOCK -> new PinLockGame(puzzleSeed, challenge, sender);
                case PIPE_LOCK -> new PipeLockGame(puzzleSeed, challenge, sender);
                case ROTARY_LOCK -> new RotaryLockGame(puzzleSeed, tool, challenge, sender);
                case MEMORY_LOCK -> new MemoryLockGame(puzzleSeed, challenge, sender);
            };
        }
    }

    @Override
    public void tick() {
        if (game != null) {
            game.tick();
        }
        if (type == LockType.PIPE_LOCK && rewardStatus != null
                && rewardStatus.metric() == RewardMetric.TIME_TICKS
                && rewardStatus.metricStarted() && pipeTimeLeftTicks > 0) {
            pipeTimeLeftTicks--;
        }
        if (failureMessageTicks > 0) {
            failureMessageTicks--;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        renderGuiBackground(graphics);
        renderStatusBar(graphics, mouseX, mouseY);
        if (game != null) {
            game.render(graphics, mouseX, mouseY, partialTick,
                    gameLeft, gameTop, gameWidth, gameHeight);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderGuiBackground(GuiGraphics graphics) {
        LockGuiRenderer.render(graphics, panelLeft, panelTop, panelWidth, panelHeight);
    }

    private void renderStatusBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int statusTop = panelTop + (headerHeight/5);
        graphics.renderItem(tool, panelLeft + 8, statusTop + 4);
        graphics.renderItemDecorations(this.font, tool, panelLeft + 8, statusTop + 4);

        int textX = panelLeft + 30;
        graphics.drawString(this.font, type.displayName(), textX, statusTop + 4, 0xFFF2D9A6);
        graphics.drawString(this.font,
                Component.translatable("lock.lockscontinue.attempts", attemptsLeft),
                textX, statusTop + 15, 0xFFE8E8E8);
        if (type == LockType.PIPE_LOCK) {
            if (rewardStatus != null && !rewardStatus.metricStarted()) {
                graphics.drawString(this.font,
                        Component.translatable("lock.lockscontinue.pipe_waiting"),
                        textX, statusTop + 26, challenge.displayColor());
            } else {
                if (rewardStatus != null && rewardStatus.failureAt() < 0) {
                    graphics.drawString(this.font,
                            Component.translatable("lock.lockscontinue.pipe_infinite"),
                            textX, statusTop + 26, challenge.displayColor());
                } else {
                    int seconds = Math.max(0, (pipeTimeLeftTicks + 19) / 20);
                    int timeColor = seconds <= 10 ? 0xFFFF9E8E : challenge.displayColor();
                    graphics.drawString(this.font,
                            Component.translatable("lock.lockscontinue.pipe_time", seconds),
                            textX, statusTop + 26, timeColor);
                }
            }
        } else {
            Component mistakeText;
            if (rewardStatus != null && rewardStatus.metric() == RewardMetric.MISTAKES_USED) {
                mistakeText = rewardStatus.failureAt() < 0
                        ? Component.translatable("lock.lockscontinue.mistakes_progress_infinite",
                        rewardStatus.metricValue())
                        : Component.translatable("lock.lockscontinue.mistakes_progress",
                        rewardStatus.metricValue(), rewardStatus.failureAt());
            } else {
                mistakeText = Component.translatable("lock.lockscontinue.mistakes", mistakesLeft);
            }
            graphics.drawString(this.font,
                    mistakeText,
                    textX, statusTop + 26, challenge.displayColor());
        }

        if (game != null) {
            int statusCenterX = panelLeft + panelWidth / 2;
            // Place the game's live status at the bottom edge of the game
            // area, leaving one font line as the bottom padding.
            int statusCenterY = gameTop + gameHeight - this.font.lineHeight;
            graphics.drawCenteredString(this.font,
                    Component.translatable("lock.lockscontinue.difficulty",
                            difficultyName(challenge.difficultyId())),
                    statusCenterX, statusTop - this.font.lineHeight + 4,
                    challenge.displayColor());
            game.renderStatus(graphics, this.font, statusCenterX, statusCenterY);
        }
        renderAffixStatus(graphics, statusTop, gameTop, mouseX, mouseY);
        if (failureMessageTicks > 0) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("lock.lockscontinue.failure"),
                    panelLeft + panelWidth / 2, panelTop + panelHeight - 10, 0xFFFF9E8E);
        }
    }

    private void renderAffixStatus(GuiGraphics graphics, int statusTop, int statusBottom,
                                   int mouseX, int mouseY) {
        affixPageCount = 0;
        affixesPerPage = 0;
        if (challenge.affixes().isEmpty()) {
            return;
        }

        int maxWidth = Math.max(96, panelWidth / 4);
        affixStatusLeft = panelLeft + panelWidth - maxWidth - 10;
        affixStatusRight = panelLeft + panelWidth - 10;
        affixStatusBottom = statusBottom - 4;
        int left = affixStatusLeft + 10;
        int top = statusTop;
        int bottom = affixStatusBottom;
        if (bottom <= top) {
            return;
        }

        affixPageTop = statusTop - 4;
        affixNextLeft = affixStatusRight - 4;
        affixPageLabelLeft = affixNextLeft - 3 - 48;
        affixPreviousLeft = affixPageLabelLeft + 20;

//        graphics.drawString(this.font,
//                Component.translatable("lock.lockscontinue.affixes"),
//                left, top, challenge.displayColor());
//        top += this.font.lineHeight + 2;

        int maxEntryLines = 2;
        for (ResourceLocation id : challenge.affixes()) {
            maxEntryLines = Math.max(maxEntryLines,
                    1 + this.font.split(affixDescription(id), Math.max(1, maxWidth - 8)).size());
        }
        int entryHeight = maxEntryLines * this.font.lineHeight + 1;
        affixesPerPage = Math.max(1, (bottom - top) / entryHeight);
        affixPageCount = (challenge.affixes().size() + affixesPerPage - 1) / affixesPerPage;
        affixPage = Math.max(0, Math.min(affixPage, affixPageCount - 1));

        if (affixPageCount > 1) {
            boolean previousHovered = isInside(mouseX, mouseY,
                    affixPreviousLeft, affixPageTop, 9, 11);
            boolean nextHovered = isInside(mouseX, mouseY,
                    affixNextLeft, affixPageTop, 9, 11);
            renderAffixPageButton(graphics, affixPreviousLeft, affixPageTop,
                    "<", previousHovered, affixPage > 0);
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.lockscontinue.affix_scroll",
                            affixPage + 1, affixPageCount),
                    affixPageLabelLeft + 40, affixPageTop + 2, 0xFFAAA6A0);
            renderAffixPageButton(graphics, affixNextLeft, affixPageTop,
                    ">", nextHovered, affixPage + 1 < affixPageCount);
        }

        int first = affixPage * affixesPerPage;
        int last = Math.min(challenge.affixes().size(), first + affixesPerPage);
        for (int index = first; index < last; index++) {
            ResourceLocation id = challenge.affixes().get(index);
            if (top + this.font.lineHeight > bottom) {
                break;
            }
            graphics.drawString(this.font, affixName(id), left, top, 0xFFF2D9A6);
            top += this.font.lineHeight;

            for (var line : this.font.split(affixDescription(id), Math.max(1, maxWidth - 8))) {
                if (top + this.font.lineHeight > bottom) {
                    return;
                }
                graphics.drawString(this.font, line, left + 8, top, 0xFFBEB7A9);
                top += this.font.lineHeight;
            }
            top += 1;
        }
    }

    private void renderAffixPageButton(GuiGraphics graphics, int left, int top,
                                       String label, boolean hovered, boolean enabled) {
        int background = !enabled ? 0x66302B29
                : hovered ? 0xAA6C5843 : 0x88433831;
        graphics.fill(left, top, left + 9, top + 11, background);
        graphics.fill(left, top, left + 9, top + 1, 0xFF9E805A);
        graphics.fill(left, top + 10, left + 9, top + 11, 0xFF2A211E);
        graphics.drawCenteredString(this.font, Component.literal(label), left + 4,
                top + 2, enabled ? 0xFFF2E8D5 : 0xFF8F8B85);
    }

    private static Component affixName(ResourceLocation id) {
        if (LocksContinue.MODID.equals(id.getNamespace())) {
            return Component.translatable("affix.lockscontinue." + id.getPath());
        }
        String path = id.getPath().replace('_', ' ').replace('/', ' ');
        return Component.literal(path.isEmpty()
                ? id.toString()
                : Character.toUpperCase(path.charAt(0)) + path.substring(1));
    }

    private static Component affixDescription(ResourceLocation id) {
        return Component.translatable("affix." + id.getNamespace() + "." + id.getPath() + ".desc");
    }

    private static Component difficultyName(ResourceLocation id) {
        if (id != null && LocksContinue.MODID.equals(id.getNamespace())) {
            return Component.translatable("difficulty.lockscontinue." + id.getPath());
        }
        if (id == null) {
            return Component.literal("unknown");
        }
        String path = id.getPath().replace('_', ' ').replace('/', ' ');
        return Component.literal(path.isEmpty()
                ? id.toString()
                : Character.toUpperCase(path.charAt(0)) + path.substring(1));
    }

    private void sendAction(LockAction action) {
        if (closing) {
            return;
        }
        LockNetwork.CHANNEL.sendToServer(new LockNetwork.LockActionPacket(
                sessionId, ++nextActionSequence, action.type(), action.first(), action.second(),
                action.value(), action.sequence()));
    }

    public void handleServerResult(LockNetwork.LockResultPacket packet) {
        if (packet.sessionId() != sessionId || closing) {
            return;
        }

        attemptsLeft = packet.attemptsLeft();
        mistakesLeft = packet.mistakesLeft();
        rewardStatus = packet.rewardStatus();
        if (type == LockType.PIPE_LOCK) {
            pipeTimeLeftTicks = rewardStatus != null
                    ? initialPipeTime(rewardStatus) : Math.max(0, packet.pipeTimeLeftTicks());
        }
        if (tool.isDamageableItem()) {
            tool.setDamageValue(Math.max(0, tool.getMaxDamage() - attemptsLeft));
        }

        if (packet.result() == LockResultType.SOLVED) {
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
        if (packet.result() == LockResultType.UPDATED) {
            if (game != null) {
                game.onServerUpdated();
            }
            return;
        }
        if (packet.result() == LockResultType.MISTAKE
                && type == LockType.ROTARY_LOCK) {
            LockSoundHelper.playUi(
                    com.kaleblangley.lockscontinue.sound.ModSounds.ROTARY_FAIL.get(),
                    0.8F, 1.0F);
        }
        if (packet.result() == LockResultType.REJECTED) {
            if (attemptsLeft <= 0 && mistakesLeft <= 0) {
                closing = true;
                Minecraft.getInstance().setScreen(null);
            } else if (game != null) {
                game.onServerRejected();
            }
            return;
        }
        if (packet.result() == LockResultType.MISTAKE
                && (attemptsLeft <= 0 || mistakesLeft <= 0)) {
            closing = true;
            Minecraft.getInstance().setScreen(null);
            return;
        }

        failureMessageTicks = 20;
        if (game != null) {
            game.onServerMistake();
        }
    }

    private static int initialPipeTime(LockNetwork.RewardStatus status) {
        if (status == null || status.metric() != RewardMetric.TIME_TICKS
                || status.failureAt() < 0) {
            return 0;
        }
        return Math.max(0, status.failureAt() - status.metricValue());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleAffixPageClick(mouseX, mouseY)) {
            return true;
        }
        if (game != null && game.mouseClicked(mouseX, mouseY, button,
                gameLeft, gameTop, gameWidth, gameHeight)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleAffixPageClick(double mouseX, double mouseY) {
        if (affixPageCount <= 1) {
            return false;
        }
        if (isInside(mouseX, mouseY, affixPreviousLeft, affixPageTop, 18, 16)) {
            affixPage = Math.max(0, affixPage - 1);
            return true;
        }
        if (isInside(mouseX, mouseY, affixNextLeft, affixPageTop, 18, 16)) {
            affixPage = Math.min(affixPageCount - 1, affixPage + 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (affixPageCount > 1
                && isInside(mouseX, mouseY, affixStatusLeft, affixPageTop,
                affixStatusRight - affixStatusLeft,
                Math.max(1, affixStatusBottom - affixPageTop))) {
            affixPage = Math.max(0, Math.min(affixPageCount - 1,
                    affixPage - (delta > 0.0D ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (game != null && game.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
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

    private static boolean isInside(double mouseX, double mouseY,
                                    int left, int top, int width, int height) {
        return mouseX >= left && mouseX < left + width
                && mouseY >= top && mouseY < top + height;
    }
}

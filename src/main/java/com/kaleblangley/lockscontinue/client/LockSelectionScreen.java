package com.kaleblangley.lockscontinue.client;

import com.kaleblangley.lockscontinue.lock.LockData;
import com.kaleblangley.lockscontinue.lock.LockInstance;
import com.kaleblangley.lockscontinue.lock.LockType;
import com.kaleblangley.lockscontinue.network.LockNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Client-side toolbox screen for selecting a lock and its server-defined rules. */
public final class LockSelectionScreen extends Screen {
    private final BlockPos pos;
    private final InteractionHand hand;
    private final List<ResourceLocation> difficultyIds;
    private final List<LockNetwork.AffixOption> affixOptions;
    private final Set<ResourceLocation> selectedAffixes = new LinkedHashSet<>();
    private LockType selectedType = LockType.PIN_LOCK;
    private int selectedDifficulty;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int optionTop;
    private int optionGridLeft;
    private int optionWidth;
    private int optionHeight;
    private int optionGap;
    private int contentLeft;
    private int contentWidth;
    private int difficultyTop;
    private int difficultyWidth;
    private int difficultyHeight;
    private int difficultyColumns;
    private int affixTop;
    private int affixRowHeight;
    private int affixVisibleRows;
    private int affixScroll;
    private int affixPageTop;
    private int affixPreviousLeft;
    private int affixPageLabelLeft;
    private int affixNextLeft;
    private int affixPageButtonWidth;
    private int affixPageButtonHeight;
    private int affixPageLabelWidth;
    private int affixPageGap;
    private int confirmLeft;
    private int confirmTop;
    private int confirmWidth;
    private int confirmHeight;
    private boolean selecting;
    private int messageTicks;

    public LockSelectionScreen(LockNetwork.OpenLockSelectionPacket packet) {
        super(Component.translatable("screen.lockscontinue.select_lock"));
        this.pos = packet.pos();
        this.hand = packet.hand();
        this.difficultyIds = packet.difficulties().isEmpty()
                ? List.of(LockInstance.NORMAL_DIFFICULTY) : packet.difficulties();
        this.affixOptions = packet.affixes();
        this.selectedDifficulty = Math.max(0, this.difficultyIds.indexOf(LockInstance.NORMAL_DIFFICULTY));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(this.width - 20, Math.max(320, this.width * 3 / 4));
        // The settings screen contains more controls than the old type-only
        // screen. Use the available vertical space so the affix list does
        // not fall below the panel at normal Minecraft GUI scales.
        panelHeight = Math.max(1, this.height - 20);
        panelWidth = Math.max(1, panelWidth);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int margin = Math.max(10, panelWidth / 24);
        contentLeft = panelLeft + margin;
        contentWidth = panelWidth - margin * 2;
        optionGap = Math.max(6, Math.min(12, panelWidth / 80));
        int maximumOptionWidth = Math.max(1, (contentWidth - optionGap) / 2);
        optionWidth = Math.min(maximumOptionWidth,
                Math.max(180, Math.min(300, contentWidth / 3)));
        optionGridLeft = panelLeft + (panelWidth - (optionWidth * 2 + optionGap)) / 2;
        // This is a configuration screen, not a lock-game screen. Do not
        // reserve the 15/48 GUI header used by LockScreen; use the whole
        // panel for the selection controls instead.
        // Keep this section compact so difficulty, affixes and the confirm
        // button remain visible even on short screens.
        optionHeight = Math.max(34, Math.min(46, panelHeight / 18));
        optionTop = panelTop + 40;

        difficultyColumns = Math.min(5, Math.max(1, difficultyIds.size()));
        difficultyWidth = Math.max(1,
                (contentWidth - optionGap * (difficultyColumns - 1)) / difficultyColumns);
        difficultyHeight = 20;
        int typeBottom = optionTop + optionHeight * 2 + optionGap;
        difficultyTop = typeBottom + 14;

        int difficultyRows = Math.max(1,
                (difficultyIds.size() + difficultyColumns - 1) / difficultyColumns);
        affixTop = difficultyTop + difficultyRows * (difficultyHeight + 3) + 24;
        affixRowHeight = 18;
        affixPageButtonWidth = 16;
        affixPageButtonHeight = 16;
        affixPageLabelWidth = 64;
        affixPageGap = 4;
        affixPageTop = affixTop - affixPageButtonHeight - 5;
        affixNextLeft = contentLeft + contentWidth - affixPageButtonWidth;
        affixPageLabelLeft = affixNextLeft - affixPageGap - affixPageLabelWidth;
        affixPreviousLeft = affixPageLabelLeft - affixPageGap - affixPageButtonWidth;
        confirmHeight = 22;
        confirmWidth = Math.min(130, contentWidth);
        confirmLeft = panelLeft + (panelWidth - confirmWidth) / 2;
        confirmTop = panelTop + panelHeight - confirmHeight - 8;
        affixVisibleRows = Math.max(1,
                (confirmTop - affixTop - 4) / affixRowHeight);
        affixScroll = Math.min(affixScroll, maxAffixScroll());
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
        LockGuiRenderer.render(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        graphics.drawCenteredString(this.font, this.title, this.width / 2,
                panelTop + 10, 0xFFF2D9A6);

        graphics.drawString(this.font,
                Component.translatable("screen.lockscontinue.lock_type"),
                contentLeft, optionTop - 15, 0xFFD8C8B0);
        graphics.fill(contentLeft, optionTop - 4, contentLeft + contentWidth,
                optionTop - 3, 0x553F3A35);
        for (LockType type : LockType.values()) {
            int index = type.ordinal();
            int left = optionLeft(index);
            int top = optionTop(index);
            boolean hovered = !selecting && isInside(mouseX, mouseY, left, top, optionWidth, optionHeight);
            renderOption(graphics, type, left, top, hovered, selectedType == type);
        }

        graphics.drawString(this.font,
                Component.translatable("screen.lockscontinue.difficulty"),
                contentLeft, difficultyTop - 15, 0xFFD8C8B0);
        graphics.fill(contentLeft, difficultyTop - 4, contentLeft + contentWidth,
                difficultyTop - 3, 0x553F3A35);
        renderDifficulties(graphics, mouseX, mouseY);

        graphics.drawString(this.font,
                Component.translatable("screen.lockscontinue.affixes"),
                contentLeft, affixTop - 15, 0xFFD8C8B0);
        graphics.fill(contentLeft, affixTop - 4, contentLeft + contentWidth,
                affixTop - 3, 0x553F3A35);
        renderAffixPageControls(graphics, mouseX, mouseY);
        renderAffixes(graphics, mouseX, mouseY);

        graphics.drawCenteredString(this.font,
                Component.translatable("screen.lockscontinue.summary",
                        selectedType.displayName(),
                        displayName(difficultyIds.get(selectedDifficulty), "difficulty"),
                        selectedAffixes.size()),
                this.width / 2, confirmTop - 15, 0xFFD8C8B0);

        boolean confirmHovered = !selecting
                && isInside(mouseX, mouseY, confirmLeft, confirmTop, confirmWidth, confirmHeight);
        renderButton(graphics, confirmLeft, confirmTop, confirmWidth, confirmHeight,
                Component.translatable("screen.lockscontinue.install"), confirmHovered, selecting);

        if (selecting) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.lockscontinue.selecting"),
                    this.width / 2, panelTop + panelHeight - 10, 0xFFBFD7E8);
        } else if (messageTicks > 0) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.lockscontinue.selection_failed"),
                    this.width / 2, panelTop + panelHeight - 10, 0xFFFF9E8E);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        renderAffixTooltip(graphics, mouseX, mouseY);
    }

    private void renderDifficulties(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = 0; index < difficultyIds.size(); index++) {
            int column = index % difficultyColumns;
            int row = index / difficultyColumns;
            int left = contentLeft + column * (difficultyWidth + optionGap);
            int top = difficultyTop + row * (difficultyHeight + 3);
            boolean hovered = !selecting
                    && isInside(mouseX, mouseY, left, top, difficultyWidth, difficultyHeight);
            boolean selected = index == selectedDifficulty;
            int background = selected ? 0xAA6C5843 : hovered ? 0xAA51483D : 0x88433831;
            graphics.fill(left, top, left + difficultyWidth, top + difficultyHeight, background);
            graphics.drawCenteredString(this.font, displayName(difficultyIds.get(index), "difficulty"),
                    left + difficultyWidth / 2,
                    top + (difficultyHeight - this.font.lineHeight) / 2, 0xFFF2E8D5);
        }
    }

    private void renderAffixes(GuiGraphics graphics, int mouseX, int mouseY) {
        List<LockNetwork.AffixOption> availableAffixes = visibleAffixOptions();
        if (availableAffixes.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("screen.lockscontinue.no_affixes"),
                    contentLeft, affixTop + 2, 0xFFAAA6A0);
            return;
        }

        int first = affixScroll * 2;
        int last = Math.min(availableAffixes.size(), first + affixVisibleRows * 2);
        for (int index = first; index < last; index++) {
            int column = index % 2;
            int visibleRow = (index - first) / 2;
            int left = contentLeft + column * (contentWidth + optionGap) / 2;
            int width = (contentWidth - optionGap) / 2;
            int top = affixTop + visibleRow * affixRowHeight;
            boolean hovered = !selecting && isInside(mouseX, mouseY, left, top, width, affixRowHeight - 2);
            ResourceLocation id = availableAffixes.get(index).id();
            boolean selected = selectedAffixes.contains(id);
            int background = selected ? 0xAA45664D : hovered ? 0xAA51483D : 0x663A3634;
            graphics.fill(left, top, left + width, top + affixRowHeight - 2, background);
            graphics.drawString(this.font, selected ? "✓" : "○", left + 4,
                    top + 3, selected ? 0xFF9FE7AA : 0xFFAAA6A0);
            graphics.drawString(this.font, displayName(id, "affix"),
                    left + 16, top + 3, 0xFFE8E0D3);
        }

    }

    private void renderAffixPageControls(GuiGraphics graphics, int mouseX, int mouseY) {
        if (visibleAffixOptions().isEmpty()) {
            return;
        }

        boolean previousHovered = isInside(mouseX, mouseY, affixPreviousLeft, affixPageTop,
                affixPageButtonWidth, affixPageButtonHeight);
        boolean nextHovered = isInside(mouseX, mouseY, affixNextLeft, affixPageTop,
                affixPageButtonWidth, affixPageButtonHeight);
        renderButton(graphics, affixPreviousLeft, affixPageTop,
                affixPageButtonWidth, affixPageButtonHeight,
                Component.literal("<"), previousHovered, affixScroll <= 0);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.lockscontinue.affix_scroll",
                        affixScroll + 1, maxAffixScroll() + 1),
                affixPageLabelLeft + affixPageLabelWidth / 2,
                affixPageTop + (affixPageButtonHeight - this.font.lineHeight) / 2,
                0xFFAAA6A0);
        renderButton(graphics, affixNextLeft, affixPageTop,
                affixPageButtonWidth, affixPageButtonHeight,
                Component.literal(">"), nextHovered, affixScroll >= maxAffixScroll());
    }

    private void renderAffixTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = hoveredAffixIndex(mouseX, mouseY);
        if (index < 0) {
            return;
        }

        ResourceLocation id = visibleAffixOptions().get(index).id();
        graphics.renderComponentTooltip(this.font,
                List.of(displayName(id, "affix"), affixDescription(id)), mouseX, mouseY);
    }

    private int hoveredAffixIndex(double mouseX, double mouseY) {
        List<LockNetwork.AffixOption> availableAffixes = visibleAffixOptions();
        if (selecting || availableAffixes.isEmpty()) {
            return -1;
        }

        int first = affixScroll * 2;
        int last = Math.min(availableAffixes.size(), first + affixVisibleRows * 2);
        for (int index = first; index < last; index++) {
            int column = index % 2;
            int visibleRow = (index - first) / 2;
            int left = contentLeft + column * (contentWidth + optionGap) / 2;
            int width = (contentWidth - optionGap) / 2;
            int top = affixTop + visibleRow * affixRowHeight;
            if (isInside(mouseX, mouseY, left, top, width, affixRowHeight - 2)) {
                return index;
            }
        }
        return -1;
    }

    private void renderOption(GuiGraphics graphics, LockType type, int left, int top,
                              boolean hovered, boolean selected) {
        int background = selected ? 0xAA6C5843
                : selecting ? 0x66302B29 : hovered ? 0xAA51483D : 0x88433831;
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

    private static void renderButton(GuiGraphics graphics, int left, int top, int width, int height,
                                    Component label, boolean hovered, boolean disabled) {
        int background = disabled ? 0x66302B29 : hovered ? 0xAA6C5843 : 0x88433831;
        graphics.fill(left, top, left + width, top + height, background);
        graphics.fill(left, top, left + width, top + 1, 0xFF9E805A);
        graphics.fill(left, top + height - 1, left + width, top + height, 0xFF2A211E);
        graphics.drawCenteredString(Minecraft.getInstance().font, label,
                left + width / 2, top + (height - Minecraft.getInstance().font.lineHeight) / 2,
                disabled ? 0xFF8F8B85 : 0xFFF2E8D5);
    }

    private Component displayName(ResourceLocation id, String category) {
        if ("lockscontinue".equals(id.getNamespace())) {
            return Component.translatable(category + ".lockscontinue." + id.getPath());
        }
        String path = id.getPath().replace('_', ' ').replace('/', ' ');
        return Component.literal(Character.toUpperCase(path.charAt(0)) + path.substring(1));
    }

    private Component affixDescription(ResourceLocation id) {
        return Component.translatable("affix." + id.getNamespace() + "." + id.getPath() + ".desc");
    }

    private int optionLeft(int index) {
        return optionGridLeft + (index % 2) * (optionWidth + optionGap);
    }

    private int optionTop(int index) {
        return optionTop + (index / 2) * (optionHeight + optionGap);
    }

    private List<LockNetwork.AffixOption> visibleAffixOptions() {
        if (selectedType == null || difficultyIds.isEmpty()
                || selectedDifficulty < 0 || selectedDifficulty >= difficultyIds.size()) {
            return List.of();
        }
        ResourceLocation difficulty = difficultyIds.get(selectedDifficulty);
        return affixOptions.stream()
                .filter(option -> option.appliesTo(selectedType, difficulty))
                .toList();
    }

    private void pruneSelectedAffixes() {
        Set<ResourceLocation> available = new LinkedHashSet<>();
        for (LockNetwork.AffixOption option : visibleAffixOptions()) {
            available.add(option.id());
        }
        selectedAffixes.retainAll(available);
        affixScroll = Math.min(affixScroll, maxAffixScroll());
    }

    private int maxAffixScroll() {
        return Math.max(0, (visibleAffixOptions().size() + 1) / 2 - affixVisibleRows);
    }

    private void submitSelection() {
        if (selecting || selectedType == null || difficultyIds.isEmpty()) {
            return;
        }
        selecting = true;
        LockNetwork.CHANNEL.sendToServer(new LockNetwork.SelectLockPacket(
                pos, hand, selectedType.variant(), difficultyIds.get(selectedDifficulty),
                List.copyOf(selectedAffixes)));
    }

    private static boolean isInside(double mouseX, double mouseY,
                                    int left, int top, int width, int height) {
        return mouseX >= left && mouseX < left + width
                && mouseY >= top && mouseY < top + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return selecting || super.mouseClicked(mouseX, mouseY, button);
        }
        if (selecting) {
            return true;
        }

        for (LockType type : LockType.values()) {
            int index = type.ordinal();
            int left = optionLeft(index);
            int top = optionTop(index);
            if (isInside(mouseX, mouseY, left, top, optionWidth, optionHeight)) {
                selectedType = type;
                pruneSelectedAffixes();
                return true;
            }
        }

        for (int index = 0; index < difficultyIds.size(); index++) {
            int column = index % difficultyColumns;
            int row = index / difficultyColumns;
            int left = contentLeft + column * (difficultyWidth + optionGap);
            int top = difficultyTop + row * (difficultyHeight + 3);
            if (isInside(mouseX, mouseY, left, top, difficultyWidth, difficultyHeight)) {
                selectedDifficulty = index;
                pruneSelectedAffixes();
                return true;
            }
        }

        if (maxAffixScroll() > 0) {
            if (isInside(mouseX, mouseY, affixPreviousLeft, affixPageTop,
                    affixPageButtonWidth, affixPageButtonHeight)) {
                affixScroll = Math.max(0, affixScroll - 1);
                return true;
            }
            if (isInside(mouseX, mouseY, affixNextLeft, affixPageTop,
                    affixPageButtonWidth, affixPageButtonHeight)) {
                affixScroll = Math.min(maxAffixScroll(), affixScroll + 1);
                return true;
            }
        }

        List<LockNetwork.AffixOption> availableAffixes = visibleAffixOptions();
        int first = affixScroll * 2;
        int last = Math.min(availableAffixes.size(), first + affixVisibleRows * 2);
        for (int index = first; index < last; index++) {
            int column = index % 2;
            int visibleRow = (index - first) / 2;
            int left = contentLeft + column * (contentWidth + optionGap) / 2;
            int width = (contentWidth - optionGap) / 2;
            int top = affixTop + visibleRow * affixRowHeight;
            if (isInside(mouseX, mouseY, left, top, width, affixRowHeight - 2)) {
                ResourceLocation id = availableAffixes.get(index).id();
                if (!selectedAffixes.add(id)) {
                    selectedAffixes.remove(id);
                }
                return true;
            }
        }

        if (isInside(mouseX, mouseY, confirmLeft, confirmTop, confirmWidth, confirmHeight)) {
            submitSelection();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInside(mouseX, mouseY, contentLeft, affixTop,
                contentWidth, Math.max(1, confirmTop - affixTop))) {
            affixScroll = Math.max(0, Math.min(maxAffixScroll(),
                    affixScroll - (delta > 0.0D ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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

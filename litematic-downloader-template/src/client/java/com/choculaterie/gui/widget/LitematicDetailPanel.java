package com.choculaterie.gui.widget;

import com.choculaterie.gui.theme.UITheme;
import com.choculaterie.util.LitematicParser;
import com.choculaterie.util.LitematicBlockReplacer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Click;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LitematicDetailPanel implements Drawable, Element {

    private int x;
    private int y;
    private int width;
    private int height;

    private File litematicFile;
    private final MinecraftClient client;

    private CustomButton closeButton;
    private Runnable onClose;

    private List<LitematicParser.BlockCount> blockCounts = new ArrayList<>();
    private boolean isParsing = false;
    private boolean parseFailed = false;
    private int scrollOffset = 0;
    private ScrollBar scrollBar;
    private BlockReplacementPopup replacementPopup;

    private static final int ITEM_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 58;

    public LitematicDetailPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = MinecraftClient.getInstance();
        updateCloseButton();
        updateScrollBar();
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        updateCloseButton();
        updateScrollBar();
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    private void updateCloseButton() {
        int closeButtonSize = 20;
        closeButton = new CustomButton(
                x + width - closeButtonSize,
                y,
                closeButtonSize,
                closeButtonSize,
                net.minecraft.text.Text.of("X"),
                btn -> {
                    if (onClose != null) {
                        onClose.run();
                    }
                });
        closeButton.setRenderAsXIcon(true);
    }

    private void updateScrollBar() {
        int listY = y + HEADER_HEIGHT;
        int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING;
        scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.PADDING,
                listY, listHeight);

        int contentHeight = blockCounts.size() * ITEM_HEIGHT;
        scrollBar.setScrollData(contentHeight, listHeight);

        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            scrollBar.setScrollPercentage((double) scrollOffset / maxScroll);
        }
    }

    private int getMaxScroll() {
        int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING;
        int contentHeight = blockCounts.size() * ITEM_HEIGHT;
        return contentHeight <= listHeight ? 0 : blockCounts.size() - listHeight / ITEM_HEIGHT;
    }

    public void setFile(File file) {
        this.litematicFile = file;
        this.blockCounts.clear();
        this.scrollOffset = 0;
        this.parseFailed = false;

        if (file != null && file.getName().toLowerCase().endsWith(".litematic")) {
            this.isParsing = true;

            new Thread(() -> {
                try {
                    List<LitematicParser.BlockCount> counts = LitematicParser.parseBlockCounts(file);
                    this.blockCounts = counts;
                    this.isParsing = false;
                    updateScrollBar();
                } catch (Exception e) {
                    e.printStackTrace();
                    this.parseFailed = true;
                    this.isParsing = false;
                }
            }, "Litematic-Parser").start();
        }
    }

    public File getFile() {
        return litematicFile;
    }

    public boolean hasFile() {
        return litematicFile != null;
    }

    public boolean hasPopup() {
        return replacementPopup != null;
    }

    public boolean closePopup() {
        if (replacementPopup != null) {
            replacementPopup = null;
            return true;
        }
        return false;
    }

    public void clear() {
        this.litematicFile = null;
        this.blockCounts.clear();
        this.scrollOffset = 0;
        this.isParsing = false;
        this.parseFailed = false;
        this.replacementPopup = null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int renderMouseX = replacementPopup != null ? -1 : mouseX;
        int renderMouseY = replacementPopup != null ? -1 : mouseY;

        context.fill(x, y, x + width, y + height, UITheme.Colors.PANEL_BG);

        context.fill(x, y, x + 1, y + height, UITheme.Colors.PANEL_BORDER);
        context.fill(x, y, x + width, y + 1, UITheme.Colors.PANEL_BORDER);
        context.fill(x + width - 1, y, x + width, y + height, UITheme.Colors.PANEL_BORDER);
        context.fill(x, y + height - 1, x + width, y + height, UITheme.Colors.PANEL_BORDER);

        if (closeButton != null) {
            closeButton.render(context, renderMouseX, renderMouseY, delta);
        }

        if (litematicFile == null) {
            String emptyText = "Select a litematic file";
            int textWidth = client.textRenderer.getWidth(emptyText);
            context.drawTextWithShadow(
                    client.textRenderer,
                    emptyText,
                    x + (width - textWidth) / 2,
                    y + height / 2 - 4,
                    0xFF888888);
            return;
        }

        int contentX = x + UITheme.Dimensions.PADDING;
        int contentY = y + UITheme.Dimensions.PADDING;

        String fileName = litematicFile.getName();
        context.drawTextWithShadow(
                client.textRenderer,
                fileName,
                contentX,
                contentY,
                0xFFFFFFFF);
        contentY += 15;

        long sizeKB = litematicFile.length() / 1024;
        String sizeText = "Size: " + sizeKB + " KB";
        context.drawTextWithShadow(
                client.textRenderer,
                sizeText,
                contentX,
                contentY,
                0xFFAAAAAA);
        contentY += 15;

        if (isParsing) {
            context.drawTextWithShadow(
                    client.textRenderer,
                    "Parsing blocks...",
                    contentX,
                    contentY,
                    0xFFFFAA00);
            return;
        }

        if (parseFailed) {
            context.drawTextWithShadow(
                    client.textRenderer,
                    "Failed to parse file",
                    contentX,
                    contentY,
                    0xFFFF0000);
            return;
        }

        if (blockCounts.isEmpty()) {
            context.drawTextWithShadow(
                    client.textRenderer,
                    "No blocks found",
                    contentX,
                    contentY,
                    0xFF888888);
            return;
        }

        String blocksHeader = "Blocks (" + blockCounts.size() + " types):";
        context.drawTextWithShadow(
                client.textRenderer,
                blocksHeader,
                contentX,
                contentY,
                0xFFAAAAAA);

        int listY = y + HEADER_HEIGHT;
        int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING;
        int listRightEdge = x + width - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH - 2;

        context.fill(contentX, listY, listRightEdge, listY + listHeight, 0xFF151515);

        context.enableScissor(contentX, listY, listRightEdge, listY + listHeight);

        int maxVisibleItems = (listHeight / ITEM_HEIGHT) + 1;
        for (int i = scrollOffset; i < Math.min(blockCounts.size(), scrollOffset + maxVisibleItems); i++) {
            LitematicParser.BlockCount blockCount = blockCounts.get(i);
            int itemY = listY + (i - scrollOffset) * ITEM_HEIGHT;

            boolean isHovered = renderMouseX >= contentX && renderMouseX < listRightEdge &&
                    renderMouseY >= itemY && renderMouseY < itemY + ITEM_HEIGHT;
            int bgColor = isHovered ? 0xFF2A2A2A : 0xFF1A1A1A;
            context.fill(contentX + 2, itemY + 2, listRightEdge - 2, itemY + ITEM_HEIGHT - 2, bgColor);

            ItemStack itemStack = getItemStackForBlock(blockCount.blockId);
            if (itemStack != null && !itemStack.isEmpty()) {
                context.drawItem(itemStack, contentX + 4, itemY + 4);
            }

            String blockName = LitematicParser.getSimpleBlockName(blockCount.blockId);
            int textX = contentX + 24;
            int textY = itemY + 8;

            int maxTextWidth = listRightEdge - textX - 70;
            if (client.textRenderer.getWidth(blockName) > maxTextWidth) {
                String ellipsis = "...";
                int ellipsisWidth = client.textRenderer.getWidth(ellipsis);
                int availableWidth = maxTextWidth - ellipsisWidth;
                StringBuilder truncated = new StringBuilder();
                for (int c = 0; c < blockName.length(); c++) {
                    String test = truncated.toString() + blockName.charAt(c);
                    if (client.textRenderer.getWidth(test) > availableWidth) {
                        break;
                    }
                    truncated.append(blockName.charAt(c));
                }
                blockName = truncated + ellipsis;
            }

            context.drawTextWithShadow(client.textRenderer, blockName, textX, textY, 0xFFFFFFFF);

            String countText = "x" + blockCount.count;
            int countWidth = client.textRenderer.getWidth(countText);
            context.drawTextWithShadow(client.textRenderer, countText, listRightEdge - countWidth - 5, textY,
                    0xFFAAFF00);
        }

        context.disableScissor();

        if (scrollBar != null && scrollBar.isVisible() && client != null) {
            boolean scrollChanged = scrollBar.updateAndRender(context, renderMouseX, renderMouseY, delta,
                    client.getWindow().getHandle());
            if (scrollChanged) {
                int maxScroll = getMaxScroll();
                scrollOffset = (int) (scrollBar.getScrollPercentage() * maxScroll);
            }
        }

        if (replacementPopup != null) {
            replacementPopup.render(context, mouseX, mouseY, delta);
        }
    }

    private ItemStack getItemStackForBlock(String blockId) {
        try {
            Identifier identifier = Identifier.tryParse(blockId);
            if (identifier == null) {
                return new ItemStack(Items.BARRIER);
            }

            Block block = Registries.BLOCK.get(identifier);
            if (block == null || block == Blocks.AIR) {
                return new ItemStack(Items.BARRIER);
            }

            Item item = block.asItem();
            if (item == Items.AIR) {
                return new ItemStack(Items.BARRIER);
            }

            return new ItemStack(item);
        } catch (Exception e) {
            return new ItemStack(Items.BARRIER);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (replacementPopup != null) {
            if (replacementPopup.mouseClicked(click.x(), click.y(), 0)) {
                return true;
            }
            return true;
        }

        if (closeButton != null) {
            if (closeButton.mouseClicked(click, doubled)) {
                return true;
            }
        }

        int listY = y + HEADER_HEIGHT;
        int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING;
        int listRightEdge = x + width - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH - 2;
        int contentX = x + UITheme.Dimensions.PADDING;

        if (click.x() >= contentX && click.x() < listRightEdge &&
                click.y() >= listY && click.y() < listY + listHeight) {
            int clickedIndex = scrollOffset + (int) ((click.y() - listY) / ITEM_HEIGHT);
            if (clickedIndex >= 0 && clickedIndex < blockCounts.size()) {
                LitematicParser.BlockCount blockCount = blockCounts.get(clickedIndex);
                openReplacementPopup(blockCount.blockId);
                return true;
            }
        }

        return false;
    }

    private void openReplacementPopup(String blockId) {
        if (client == null)
            return;

        replacementPopup = new BlockReplacementPopup(
                client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight(),
                blockId);

        replacementPopup.setOnBlockSelected((oldBlockId, newBlockId) -> {
            if (litematicFile != null) {
                boolean success = LitematicBlockReplacer.replaceBlock(litematicFile, oldBlockId, newBlockId);
                if (success) {
                    setFile(litematicFile);
                }
            }
            replacementPopup = null;
        });

        replacementPopup.setOnCancel(() -> {
            replacementPopup = null;
        });
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (closeButton != null) {
            return closeButton.mouseReleased(click);
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (replacementPopup != null) {
            return replacementPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }

        int maxScroll = getMaxScroll();
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));

        if (scrollBar != null && maxScroll > 0) {
            scrollBar.setScrollPercentage((double) scrollOffset / maxScroll);
        }

        return true;
    }

    private boolean isMouseOverButton(CustomButton button, double mouseX, double mouseY) {
        return button != null &&
                mouseX >= button.getX() &&
                mouseX < button.getX() + button.getWidth() &&
                mouseY >= button.getY() &&
                mouseY < button.getY() + button.getHeight();
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }
}

package com.choculaterie.gui.widget;

import org.lwjgl.glfw.GLFW;
import com.choculaterie.gui.theme.UITheme;
import com.choculaterie.util.LitematicParser;
import com.choculaterie.util.LitematicBlockReplacer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LitematicDetailPanel implements Renderable, GuiEventListener {

    private int x;
    private int y;
    private int width;
    private int height;

    private File litematicFile;
    private final Minecraft client;

    private CustomButton closeButton;
    private CustomButton toggleViewButton;
    private CustomButton cameraButton;
    private Runnable onClose;

    private List<LitematicParser.BlockCount> blockCounts = new ArrayList<>();
    private boolean isParsing = false;
    private boolean parseFailed = false;
    private int scrollOffset = 0;
    private ScrollBar scrollBar;
    private BlockReplacementPopup replacementPopup;

    private boolean isIn3DMode = true;
    private final SchematicRenderer schematicRenderer = new SchematicRenderer();
    private volatile boolean isParsingPositions = false;
    private volatile boolean positionParseFailed = false;
    private volatile boolean positionsParsed = false;

    private final SchematicExportPanel exportPanel = new SchematicExportPanel(schematicRenderer);
    private boolean isExportPanelOpen = false;

    private static final int ITEM_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 58;

    public LitematicDetailPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = Minecraft.getInstance();
        updateButtons();
        updateScrollBar();
        updateExportPanelBounds();
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        updateButtons();
        updateScrollBar();
        updateExportPanelBounds();
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    private void updateButtons() {
        int buttonSize = 20;

        closeButton = new CustomButton(
                x + width - buttonSize,
                y,
                buttonSize,
                buttonSize,
                net.minecraft.network.chat.Component.literal("X"),
                btn -> {
                    if (onClose != null)
                        onClose.run();
                });
        closeButton.setRenderAsXIcon(true);

        toggleViewButton = new CustomButton(
                x + width - buttonSize * 2,
                y,
                buttonSize,
                buttonSize,
                net.minecraft.network.chat.Component.literal(isIn3DMode ? "☰" : "3D"),
                btn -> toggleView());

        cameraButton = new CustomButton(
                x + width - buttonSize * 3,
                y,
                buttonSize,
                buttonSize,
                net.minecraft.network.chat.Component.literal("📷"),
                btn -> toggleExportPanel());
    }

    private void updateExportPanelBounds() {
        int viewX = x + UITheme.Dimensions.PADDING;
        int viewW = width - UITheme.Dimensions.PADDING * 2;
        int panelY = y + HEADER_HEIGHT + previewHeight();
        exportPanel.setBounds(viewX, panelY, viewW);
    }

    private int previewHeight() {
        int viewH = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING;
        return isExportPanelOpen ? Math.max(40, viewH - SchematicExportPanel.PANEL_HEIGHT) : viewH;
    }

    private void toggleExportPanel() {
        isExportPanelOpen = !isExportPanelOpen;
        updateExportPanelBounds();
    }

    private void toggleView() {
        isIn3DMode = !isIn3DMode;
        isExportPanelOpen = false;
        updateButtons();
        updateExportPanelBounds();
        if (isIn3DMode && positionsParsed && !schematicRenderer.isEmpty()) {
            schematicRenderer.fitToPanel(width, height - HEADER_HEIGHT);
        }
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
        this.positionParseFailed = false;
        this.positionsParsed = false;
        this.isExportPanelOpen = false;
        exportPanel.setLitematicFile(file);
        updateExportPanelBounds();
        schematicRenderer.reset();

        if (file != null && file.getName().toLowerCase().endsWith(".litematic")) {
            this.isParsing = true;
            this.isParsingPositions = true;

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
            }, "Litematic-Parser-Counts").start();

            new Thread(() -> {
                try {
                    List<LitematicParser.BlockData> positions = LitematicParser.parseBlockPositions(file);
                    schematicRenderer.setBlocks(positions);
                    schematicRenderer.fitToPanel(width, height - HEADER_HEIGHT);
                    this.positionsParsed = true;
                    this.isParsingPositions = false;
                } catch (Exception e) {
                    e.printStackTrace();
                    this.positionParseFailed = true;
                    this.isParsingPositions = false;
                }
            }, "Litematic-Parser-Positions").start();
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
        this.positionParseFailed = false;
        this.positionsParsed = false;
        this.replacementPopup = null;
        this.isExportPanelOpen = false;
        exportPanel.setLitematicFile(null);
        schematicRenderer.reset();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int renderMouseX = replacementPopup != null ? -1 : mouseX;
        int renderMouseY = replacementPopup != null ? -1 : mouseY;

        context.fill(x, y, x + width, y + height, UITheme.Colors.PANEL_BG);
        context.fill(x, y, x + 1, y + height, UITheme.Colors.PANEL_BORDER);
        context.fill(x, y, x + width, y + 1, UITheme.Colors.PANEL_BORDER);
        context.fill(x + width - 1, y, x + width, y + height, UITheme.Colors.PANEL_BORDER);
        context.fill(x, y + height - 1, x + width, y + height, UITheme.Colors.PANEL_BORDER);

        if (closeButton != null) {
            closeButton.extractRenderState(context, renderMouseX, renderMouseY, delta);
        }
        if (toggleViewButton != null) {
            toggleViewButton.extractRenderState(context, renderMouseX, renderMouseY, delta);
        }
        if (cameraButton != null && isIn3DMode && litematicFile != null) {
            cameraButton.extractRenderState(context, renderMouseX, renderMouseY, delta);
        }

        if (litematicFile == null) {
            String emptyText = "Select a litematic file";
            int textWidth = client.font.width(emptyText);
            context.text(client.font, emptyText, x + (width - textWidth) / 2, y + height / 2 - 4, 0xFF888888);
            return;
        }

        int contentX = x + UITheme.Dimensions.PADDING;
        int contentY = y + UITheme.Dimensions.PADDING;

        String fileName = litematicFile.getName();
        context.text(client.font, fileName, contentX, contentY, 0xFFFFFFFF);
        contentY += 15;

        long sizeKB = litematicFile.length() / 1024;
        context.text(client.font, "Size: " + sizeKB + " KB", contentX, contentY, 0xFFAAAAAA);

        if (isIn3DMode) {
            render3DView(context, renderMouseX, renderMouseY, delta);
        } else {
            renderMaterialList(context, renderMouseX, renderMouseY, delta);
        }

        if (replacementPopup != null) {
            replacementPopup.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    private void render3DView(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int viewX = x + UITheme.Dimensions.PADDING;
        int viewY = y + HEADER_HEIGHT;
        int viewW = width - UITheme.Dimensions.PADDING * 2;
        int viewH = previewHeight();

        context.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xFF111111);

        if (isParsingPositions || schematicRenderer.isBuilding()) {
            String msg = "Loading 3D preview...";
            context.text(client.font, msg,
                    viewX + (viewW - client.font.width(msg)) / 2,
                    viewY + viewH / 2 - 4, 0xFFFFAA00);
        } else if (positionParseFailed) {
            String msg = "Failed to load 3D preview";
            context.text(client.font, msg,
                    viewX + (viewW - client.font.width(msg)) / 2,
                    viewY + viewH / 2 - 4, 0xFFFF4444);
        } else if (schematicRenderer.isEmpty()) {
            String msg = "No blocks found";
            context.text(client.font, msg,
                    viewX + (viewW - client.font.width(msg)) / 2,
                    viewY + viewH / 2 - 4, 0xFF888888);
        } else {
            schematicRenderer.render(context, viewX, viewY, viewW, viewH, mouseX, mouseY);

            if (!isExportPanelOpen) {
                String hint;
                String hintFull = "Drag: rotate  |  WASD: move  |  Scroll: zoom";
                String hintShort = "Drag·Rotate  WASD·Move  Scroll·Zoom";
                String hintMin = "Drag·Rot  WASD·Mov  Scrl·Zoom";
                if (client.font.width(hintFull) <= viewW - 4) {
                    hint = hintFull;
                } else if (client.font.width(hintShort) <= viewW - 4) {
                    hint = hintShort;
                } else {
                    hint = hintMin;
                }
                context.text(client.font, hint,
                        viewX + (viewW - client.font.width(hint)) / 2,
                        viewY + viewH - 12, 0xFF666666);
            }
        }

        if (isExportPanelOpen) {
            exportPanel.render(context, mouseX, mouseY, delta);
        }
    }

    private void renderMaterialList(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int contentX = x + UITheme.Dimensions.PADDING;
        int contentY = y + HEADER_HEIGHT - 12;

        if (isParsing) {
            context.text(client.font, "Parsing blocks...", contentX, contentY, 0xFFFFAA00);
            return;
        }

        if (parseFailed) {
            context.text(client.font, "Failed to parse file", contentX, contentY, 0xFFFF0000);
            return;
        }

        if (blockCounts.isEmpty()) {
            context.text(client.font, "No blocks found", contentX, contentY, 0xFF888888);
            return;
        }

        context.text(client.font, "Blocks (" + blockCounts.size() + " types):", contentX, contentY, 0xFFAAAAAA);

        int listY = y + HEADER_HEIGHT;
        int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING;
        int listRightEdge = x + width - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH - 2;

        context.fill(contentX, listY, listRightEdge, listY + listHeight, 0xFF151515);
        context.enableScissor(contentX, listY, listRightEdge, listY + listHeight);

        int maxVisibleItems = (listHeight / ITEM_HEIGHT) + 1;
        for (int i = scrollOffset; i < Math.min(blockCounts.size(), scrollOffset + maxVisibleItems); i++) {
            LitematicParser.BlockCount blockCount = blockCounts.get(i);
            int itemY = listY + (i - scrollOffset) * ITEM_HEIGHT;

            boolean isHovered = mouseX >= contentX && mouseX < listRightEdge &&
                    mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            context.fill(contentX + 2, itemY + 2, listRightEdge - 2, itemY + ITEM_HEIGHT - 2,
                    isHovered ? 0xFF2A2A2A : 0xFF1A1A1A);

            ItemStack itemStack = getItemStackForBlock(blockCount.blockId);
            if (itemStack != null && !itemStack.isEmpty()) {
                context.item(itemStack, contentX + 4, itemY + 4);
            }

            String blockName = LitematicParser.getSimpleBlockName(blockCount.blockId);
            int textX = contentX + 24;
            int textY = itemY + 8;

            int maxTextWidth = listRightEdge - textX - 70;
            if (client.font.width(blockName) > maxTextWidth) {
                String ellipsis = "...";
                int ellipsisWidth = client.font.width(ellipsis);
                int availableWidth = maxTextWidth - ellipsisWidth;
                StringBuilder truncated = new StringBuilder();
                for (int c = 0; c < blockName.length(); c++) {
                    String test = truncated.toString() + blockName.charAt(c);
                    if (client.font.width(test) > availableWidth)
                        break;
                    truncated.append(blockName.charAt(c));
                }
                blockName = truncated + ellipsis;
            }

            context.text(client.font, blockName, textX, textY, 0xFFFFFFFF);

            String countText = "x" + blockCount.count;
            int countWidth = client.font.width(countText);
            context.text(client.font, countText, listRightEdge - countWidth - 5, textY, UITheme.Colors.ACCENT_GREEN);
        }

        context.disableScissor();

        if (scrollBar != null && scrollBar.isVisible() && client != null) {
            boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta,
                    GLFW.glfwGetCurrentContext());
            if (scrollChanged) {
                int maxScroll = getMaxScroll();
                scrollOffset = (int) (scrollBar.getScrollPercentage() * maxScroll);
            }
        }
    }

    private ItemStack getItemStackForBlock(String blockId) {
        try {
            Identifier identifier = Identifier.tryParse(blockId);
            if (identifier == null)
                return new ItemStack(Items.BARRIER);

            var blockRef = BuiltInRegistries.BLOCK.get(identifier);
            if (blockRef.isEmpty())
                return new ItemStack(Items.BARRIER);

            Block block = blockRef.get().value();
            if (block == Blocks.AIR)
                return new ItemStack(Items.BARRIER);

            Item item = block.asItem();
            if (item == Items.AIR)
                return new ItemStack(Items.BARRIER);

            return new ItemStack(item);
        } catch (Exception e) {
            return new ItemStack(Items.BARRIER);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (replacementPopup != null) {
            replacementPopup.mouseClicked(click.x(), click.y(), 0);
            return true;
        }

        if (cameraButton != null && isIn3DMode && litematicFile != null
                && cameraButton.mouseClicked(click, doubled))
            return true;
        if (toggleViewButton != null && toggleViewButton.mouseClicked(click, doubled))
            return true;
        if (closeButton != null && closeButton.mouseClicked(click, doubled))
            return true;

        if (isIn3DMode && isExportPanelOpen && exportPanel.mouseClicked(click, doubled))
            return true;

        if (!isIn3DMode) {
            int listY = y + HEADER_HEIGHT;
            int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING;
            int listRightEdge = x + width - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH - 2;
            int contentX = x + UITheme.Dimensions.PADDING;

            if (click.x() >= contentX && click.x() < listRightEdge &&
                    click.y() >= listY && click.y() < listY + listHeight) {
                int clickedIndex = scrollOffset + (int) ((click.y() - listY) / ITEM_HEIGHT);
                if (clickedIndex >= 0 && clickedIndex < blockCounts.size()) {
                    openReplacementPopup(blockCounts.get(clickedIndex).blockId);
                    return true;
                }
            }
        }

        return false;
    }

    private void openReplacementPopup(String blockId) {
        if (client == null)
            return;

        replacementPopup = new BlockReplacementPopup(
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(),
                blockId);

        replacementPopup.setOnBlockSelected((oldBlockId, newBlockId) -> {
            if (litematicFile != null) {
                boolean success = LitematicBlockReplacer.replaceBlock(litematicFile, oldBlockId, newBlockId);
                if (success)
                    setFile(litematicFile);
            }
            replacementPopup = null;
        });

        replacementPopup.setOnCancel(() -> replacementPopup = null);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (closeButton != null)
            closeButton.mouseReleased(click);
        if (toggleViewButton != null)
            toggleViewButton.mouseReleased(click);
        if (cameraButton != null)
            cameraButton.mouseReleased(click);
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (isIn3DMode && litematicFile != null) {
            int viewX = x + UITheme.Dimensions.PADDING;
            int viewY = y + HEADER_HEIGHT;
            int viewW = width - UITheme.Dimensions.PADDING * 2;
            int viewH = previewHeight();

            if (event.x() >= viewX && event.x() < viewX + viewW
                    && event.y() >= viewY && event.y() < viewY + viewH) {
                schematicRenderer.onDrag(dragX, dragY, event.button());
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (replacementPopup != null) {
            return replacementPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height)
            return false;

        if (isIn3DMode) {
            schematicRenderer.onScroll(verticalAmount);
            return true;
        }

        int maxScroll = getMaxScroll();
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
        if (scrollBar != null && maxScroll > 0) {
            scrollBar.setScrollPercentage((double) scrollOffset / maxScroll);
        }
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }
}

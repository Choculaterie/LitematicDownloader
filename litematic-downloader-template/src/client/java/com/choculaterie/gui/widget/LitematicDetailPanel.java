package com.choculaterie.gui.widget;

import com.choculaterie.vanilib.gui.widget.CustomButton;
import com.choculaterie.vanilib.gui.widget.LoadingSpinner;
import com.choculaterie.vanilib.gui.widget.ScrollBar;

import com.choculaterie.vanilib.gui.theme.UIDraw;
import com.choculaterie.vanilib.gui.theme.UIIcon;
import com.choculaterie.vanilib.gui.theme.UITheme;
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
import com.mojang.blaze3d.platform.InputConstants;

public class LitematicDetailPanel implements Renderable, GuiEventListener {

    private int x;
    private int y;
    private int width;
    private int height;

    private File litematicFile;
    private final Minecraft client;

    private final LoadingSpinner loadingSpinner = new LoadingSpinner(0, 0);
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

    private static final int ITEM_HEIGHT = 25;
    private static final int HEADER_HEIGHT = 42;

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

    private static final int HEADER_INSET = 4;
    private static final int HEADER_GAP = 2;

    private void updateButtons() {
        int buttonSize = UITheme.Dimensions.BUTTON_HEIGHT;
        int buttonY = y + HEADER_INSET;
        int right = x + width - HEADER_INSET;

        closeButton = new CustomButton(
                right - buttonSize,
                buttonY,
                buttonSize,
                buttonSize,
                net.minecraft.network.chat.Component.empty(),
                btn -> {
                    if (onClose != null)
                        onClose.run();
                });
        closeButton.setIcon(UIIcon.CLOSE);

        toggleViewButton = new CustomButton(
                right - buttonSize * 2 - HEADER_GAP,
                buttonY,
                buttonSize,
                buttonSize,
                net.minecraft.network.chat.Component.literal(""),
                btn -> toggleView());
        toggleViewButton.setIcon(isIn3DMode ? UIIcon.LIST : UIIcon.CUBE);

        cameraButton = new CustomButton(
                right - buttonSize * 3 - HEADER_GAP * 2,
                buttonY,
                buttonSize,
                buttonSize,
                net.minecraft.network.chat.Component.literal(""),
                btn -> toggleExportPanel());
        cameraButton.setIcon(UIIcon.CAMERA);
    }

    private String truncateToWidth(String text, int maxWidth) {
        if (client.font.width(text) <= maxWidth) {
            return text;
        }
        while (!text.isEmpty() && client.font.width(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
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

        UIDraw.sidePanel(context, x, y, x + width, y + height);

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
            UIDraw.centeredText(context, UIDraw.ellipsize("Select a litematic file", width - 20), x + width / 2,
                    y + (height - UITheme.Typography.CAP_HEIGHT) / 2, UITheme.Colors.TEXT_MUTED);
            return;
        }

        int contentX = x + UITheme.Dimensions.PADDING;
        int contentY = UIDraw.bevelContentY(y + HEADER_INSET, UITheme.Dimensions.BUTTON_HEIGHT);

        String fileName = litematicFile.getName();
        int buttonCount = (cameraButton != null && isIn3DMode) ? 3 : 2;
        int maxTitleWidth = width - UITheme.Dimensions.PADDING - HEADER_INSET - buttonCount * (UITheme.Dimensions.BUTTON_HEIGHT + HEADER_GAP) - 4;
        UIDraw.text(context, truncateToWidth(fileName, maxTitleWidth), contentX, contentY, UITheme.Colors.TEXT_HIGHLIGHT);
        contentY += 15;

        long sizeKB = litematicFile.length() / 1024;
        UIDraw.text(context, sizeKB + " KB", contentX, contentY, UITheme.Colors.TEXT_MUTED);

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

        UIDraw.panel(context, viewX - 1, viewY - 1, viewX + viewW + 1, viewY + viewH + 1, UITheme.Colors.FIELD_BG, UITheme.Colors.FIELD_BORDER);

        if (isParsingPositions || schematicRenderer.isBuilding()) {
            loadingSpinner.setPosition(viewX + (viewW - loadingSpinner.getWidth()) / 2, viewY + (viewH - loadingSpinner.getHeight()) / 2);
            loadingSpinner.extractRenderState(context, mouseX, mouseY, delta);
        } else if (positionParseFailed) {
            String msg = "Failed to load 3D preview";
            UIDraw.centeredText(context, msg, viewX + viewW / 2, viewY + (viewH - UITheme.Typography.CAP_HEIGHT) / 2, UITheme.Colors.ERROR_TEXT);
        } else if (schematicRenderer.isEmpty()) {
            String msg = "No blocks found";
            UIDraw.centeredText(context, msg, viewX + viewW / 2, viewY + (viewH - UITheme.Typography.CAP_HEIGHT) / 2, UITheme.Colors.TEXT_MUTED);
        } else {
            schematicRenderer.render(context, viewX, viewY, viewW, viewH, mouseX, mouseY);

            if (!isExportPanelOpen) {
                String hint;
                String hintFull = "Drag: rotate  |  WASD: move  |  Scroll: zoom";
                String hintShort = "Drag rotate, WASD move, Scroll zoom";
                String hintMin = "Drag / WASD / Scroll";
                if (client.font.width(hintFull) <= viewW - 4) {
                    hint = hintFull;
                } else if (client.font.width(hintShort) <= viewW - 4) {
                    hint = hintShort;
                } else {
                    hint = hintMin;
                }
                UIDraw.centeredText(context, hint, viewX + viewW / 2, viewY + viewH - 12, UITheme.Colors.TEXT_MUTED);
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
            UIDraw.text(context, "Parsing blocks...", contentX, contentY, UITheme.Colors.WARNING_TEXT);
            return;
        }

        if (parseFailed) {
            UIDraw.text(context, "Failed to parse file", contentX, contentY, UITheme.Colors.ERROR_TEXT);
            return;
        }

        if (blockCounts.isEmpty()) {
            UIDraw.text(context, "No blocks found", contentX, contentY, UITheme.Colors.TEXT_MUTED);
            return;
        }

        UIDraw.text(context, "Blocks (" + blockCounts.size() + " types)", contentX, contentY, UITheme.Colors.TEXT_MUTED);

        int listY = y + HEADER_HEIGHT;
        int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING;
        int listRightEdge = x + width - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH - 2;

        UIDraw.panel(context, contentX, listY, listRightEdge, listY + listHeight, UITheme.Colors.FIELD_BG, UITheme.Colors.FIELD_BORDER);
        context.enableScissor(contentX + 1, listY + 1, listRightEdge - 1, listY + listHeight - 1);

        int maxVisibleItems = (listHeight / ITEM_HEIGHT) + 1;
        for (int i = scrollOffset; i < Math.min(blockCounts.size(), scrollOffset + maxVisibleItems); i++) {
            LitematicParser.BlockCount blockCount = blockCounts.get(i);
            int itemY = listY + (i - scrollOffset) * ITEM_HEIGHT;

            boolean isHovered = mouseX >= contentX && mouseX < listRightEdge &&
                    mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            if (isHovered) {
                context.fill(contentX + 1, itemY, listRightEdge - 1, itemY + ITEM_HEIGHT, UITheme.Colors.SURFACE_HOVER);
            }

            ItemStack itemStack = getItemStackForBlock(blockCount.blockId);
            if (itemStack != null && !itemStack.isEmpty()) {
                context.item(itemStack, contentX + 4, itemY + (ITEM_HEIGHT - 16) / 2);
            }

            String blockName = LitematicParser.getSimpleBlockName(blockCount.blockId);
            int textX = contentX + 24;
            int textY = UIDraw.flatContentY(itemY, ITEM_HEIGHT);

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

            UIDraw.text(context, blockName, textX, textY, UITheme.Colors.TEXT_PRIMARY);

            String countText = "x" + blockCount.count;
            int countWidth = client.font.width(countText);
            UIDraw.text(context, countText, listRightEdge - countWidth - 6, textY, UITheme.Colors.ACCENT);
        }

        context.disableScissor();

        if (scrollBar != null && scrollBar.isVisible() && client != null) {
            boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta,
                    Minecraft.getInstance().getWindow().handle());
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
            replacementPopup.mouseClicked(click.x(), click.y(), InputConstants.MOUSE_BUTTON_LEFT);
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

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
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

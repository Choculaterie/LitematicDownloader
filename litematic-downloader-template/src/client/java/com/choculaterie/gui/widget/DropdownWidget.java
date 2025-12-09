package com.choculaterie.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dropdown widget for displaying selectable items
 */
public class DropdownWidget implements Drawable, Element {
    private static final int ITEM_HEIGHT = 24;
    private static final int PADDING = 4;
    private static final int MAX_VISIBLE_ITEMS = 6;
    private static final int BG_COLOR = 0xE0252525;
    // Match CustomButton colors
    private static final int ITEM_COLOR = 0xFF3A3A3A;
    private static final int ITEM_HOVER_COLOR = 0xFF4A4A4A;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final MinecraftClient client;
    private int x;
    private int y;
    private int width;
    private final List<DropdownItem> items;
    private final Consumer<DropdownItem> onSelect;
    private boolean isOpen;
    private int hoveredIndex = -1;
    private double scrollOffset = 0;
    private int maxScrollOffset = 0;
    private String statusMessage = "";

    public DropdownWidget(int x, int y, int width, Consumer<DropdownItem> onSelect) {
        this.client = MinecraftClient.getInstance();
        this.x = x;
        this.y = y;
        this.width = width;
        this.items = new ArrayList<>();
        this.onSelect = onSelect;
        this.isOpen = false;
    }

    public void setStatusMessage(String message) {
        this.statusMessage = message != null ? message : "";
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setItems(List<DropdownItem> items) {
        this.items.clear();
        this.items.addAll(items);
        this.scrollOffset = 0;
        updateScrollBounds();
    }

    public void open() {
        this.isOpen = true;
    }

    public void close() {
        this.isOpen = false;
        this.hoveredIndex = -1;
    }

    public boolean isOpen() {
        return isOpen;
    }

    private void updateScrollBounds() {
        int visibleHeight = Math.min(items.size(), MAX_VISIBLE_ITEMS) * ITEM_HEIGHT;
        int contentHeight = items.size() * ITEM_HEIGHT;
        maxScrollOffset = Math.max(0, contentHeight - visibleHeight);
    }

    private int getDropdownHeight() {
        if (!isOpen || items.isEmpty()) return 0;
        int itemCount = Math.min(items.size(), MAX_VISIBLE_ITEMS);
        int baseHeight = itemCount * ITEM_HEIGHT + 2; // +2 for top and bottom borders
        // Add space for status message if present
        if (!statusMessage.isEmpty()) {
            baseHeight += ITEM_HEIGHT;
        }
        return baseHeight;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isOpen || items.isEmpty()) return;

        int height = getDropdownHeight();

        // Draw background
        context.fill(x, y, x + width, y + height, BG_COLOR);

        // Draw border
        context.fill(x, y, x + width, y + 1, BORDER_COLOR); // Top
        context.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR); // Bottom
        context.fill(x, y, x + 1, y + height, BORDER_COLOR); // Left
        context.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR); // Right

        // Enable scissor for scrolling - start right after outer top border
        int contentY = y + 1;
        int visibleHeight = height - 2; // Account for top and bottom borders

        context.enableScissor(x, contentY, x + width, contentY + visibleHeight);

        // Draw items
        hoveredIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            int itemY = contentY + i * ITEM_HEIGHT - (int) scrollOffset;

            // Skip items outside visible area
            if (itemY + ITEM_HEIGHT < contentY || itemY > contentY + visibleHeight) {
                continue;
            }

            DropdownItem item = items.get(i);
            boolean isHovered = mouseX >= x && mouseX < x + width &&
                              mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

            // Draw item background with CustomButton style - align with outer border
            int itemBgColor = isHovered ? ITEM_HOVER_COLOR : ITEM_COLOR;
            int itemX = x + 1; // Start right after left border
            int itemWidth = width - 2; // Full width minus left and right borders

            context.fill(itemX, itemY, itemX + itemWidth, itemY + ITEM_HEIGHT, itemBgColor);

            // Draw bottom border only if not the last item (to separate items)
            if (i < items.size() - 1) {
                context.fill(itemX, itemY + ITEM_HEIGHT - 1, itemX + itemWidth, itemY + ITEM_HEIGHT, BORDER_COLOR);
            }

            if (isHovered) {
                hoveredIndex = i;
            }

            // Draw item text
            String displayText = item.getDisplayText();
            int textX = itemX + PADDING + 2;
            int textY = itemY + (ITEM_HEIGHT - client.textRenderer.fontHeight) / 2;

            // Truncate text if too long
            int maxTextWidth = itemWidth - PADDING * 2 - 4;
            if (client.textRenderer.getWidth(displayText) > maxTextWidth) {
                displayText = client.textRenderer.trimToWidth(displayText, maxTextWidth - 10) + "...";
            }

            context.drawText(client.textRenderer, displayText, textX, textY, TEXT_COLOR, false);
        }

        context.disableScissor();

        // Draw status message if present (at the bottom, outside scroll area)
        if (!statusMessage.isEmpty()) {
            int statusY = y + height - ITEM_HEIGHT - 1; // -1 for bottom border
            int statusX = x + 1; // Align with left border
            int statusWidth = width - 2; // Full width minus borders

            // Draw separator line (top border of status area)
            context.fill(statusX, statusY, statusX + statusWidth, statusY + 1, BORDER_COLOR);

            // Draw status message with wrapping support
            int maxStatusWidth = statusWidth - PADDING * 2;
            String displayStatus = statusMessage;

            // Check if message needs truncation/wrapping
            int statusTextWidth = client.textRenderer.getWidth(statusMessage);
            if (statusTextWidth > maxStatusWidth) {
                // Truncate with ellipsis if too long
                displayStatus = client.textRenderer.trimToWidth(statusMessage, maxStatusWidth - client.textRenderer.getWidth("...")) + "...";
            }

            // Draw centered
            int finalStatusWidth = client.textRenderer.getWidth(displayStatus);
            int statusTextX = x + (width - finalStatusWidth) / 2;
            int statusTextY = statusY + 1 + (ITEM_HEIGHT - client.textRenderer.fontHeight) / 2;
            context.drawText(client.textRenderer, displayStatus, statusTextX, statusTextY, 0xFF88FF88, false);
        }

        // Draw scrollbar if needed
        if (items.size() > MAX_VISIBLE_ITEMS) {
            drawScrollbar(context, visibleHeight);
        }
    }

    private void drawScrollbar(DrawContext context, int visibleHeight) {
        int scrollbarX = x + width - 6;
        int scrollbarY = y + 1; // Start after top border
        int scrollbarHeight = visibleHeight;

        // Calculate thumb size and position
        float contentRatio = (float) visibleHeight / (items.size() * ITEM_HEIGHT);
        int thumbHeight = Math.max(20, (int) (scrollbarHeight * contentRatio));

        float scrollRatio = maxScrollOffset > 0 ? (float) scrollOffset / maxScrollOffset : 0;
        int thumbY = scrollbarY + (int) ((scrollbarHeight - thumbHeight) * scrollRatio);

        // Draw scrollbar track
        context.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0xFF1A1A1A);

        // Draw scrollbar thumb
        context.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF5A5A5A);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen || items.isEmpty()) return false;

        int height = getDropdownHeight();

        // Check if click is within dropdown bounds
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            // Check if clicking on status message area (don't select anything)
            int statusAreaStart = y + getDropdownHeight() - ITEM_HEIGHT - PADDING;
            if (!statusMessage.isEmpty() && mouseY >= statusAreaStart) {
                // Clicked on status area, don't do anything
                return true;
            }

            if (hoveredIndex >= 0 && hoveredIndex < items.size()) {
                DropdownItem selectedItem = items.get(hoveredIndex);
                if (onSelect != null) {
                    onSelect.accept(selectedItem);
                }
                // Don't close - keep dropdown open to show download status
                return true;
            }
        } else {
            // Click outside dropdown, close it
            close();
            return false;
        }

        return false;
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return isOpen;
    }

    /**
     * Represents an item in the dropdown
     */
    public static class DropdownItem {
        private final String displayText;
        private final Object data;

        public DropdownItem(String displayText, Object data) {
            this.displayText = displayText;
            this.data = data;
        }

        public String getDisplayText() {
            return displayText;
        }

        public Object getData() {
            return data;
        }
    }
}


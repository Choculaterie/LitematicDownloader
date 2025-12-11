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
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_PADDING = 2;
    private static final int SCROLLBAR_SIDE_SPACING = 2; // Space on left and right of scrollbar
    // Match CustomButton colors
    private static final int ITEM_COLOR = 0xFF3A3A3A;
    private static final int ITEM_HOVER_COLOR = 0xFF4A4A4A;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int ERROR_TEXT_COLOR = 0xFFFF4444; // Red color for errors

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
    private ScrollBar scrollBar;
    private int lastScrollBarX = -1;
    private int lastScrollBarY = -1;
    private int lastScrollBarHeight = -1;

    public DropdownWidget(int x, int y, int width, Consumer<DropdownItem> onSelect) {
        this.client = MinecraftClient.getInstance();
        this.x = x;
        this.y = y;
        this.width = width;
        this.items = new ArrayList<>();
        this.onSelect = onSelect;
        this.isOpen = false;
        this.scrollBar = new ScrollBar(x + width - 10, y + 1, 100); // Will be updated in render
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
        // ScrollBar position will be updated in render
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
        // Increase width by 1px to the right
        int renderWidth = width + 1;

        // Draw background
        context.fill(x, y, x + renderWidth, y + height, BG_COLOR);

        // Draw border
        context.fill(x, y, x + renderWidth, y + 1, BORDER_COLOR); // Top
        context.fill(x, y + height - 1, x + renderWidth, y + height, BORDER_COLOR); // Bottom
        context.fill(x, y, x + 1, y + height, BORDER_COLOR); // Left
        context.fill(x + renderWidth - 1, y, x + renderWidth, y + height, BORDER_COLOR); // Right

        // Enable scissor for scrolling - start right after outer top border
        int contentY = y + 1;
        // Calculate visible item area height (excluding status message area if present)
        int itemCount = Math.min(items.size(), MAX_VISIBLE_ITEMS);
        int visibleItemsHeight = itemCount * ITEM_HEIGHT;
        int visibleHeight = visibleItemsHeight; // Only scroll the items area

        context.enableScissor(x, contentY, x + renderWidth, contentY + visibleHeight);

        // Draw items
        hoveredIndex = -1;

        // Calculate item right edge (stop before scrollbar if visible)
        boolean hasScrollbar = items.size() > MAX_VISIBLE_ITEMS;
        int itemRightEdge;
        if (hasScrollbar) {
            // With scrollbar: leave space for scrollbar + spacing + 1px shorter
            itemRightEdge = x + renderWidth - SCROLLBAR_WIDTH - SCROLLBAR_SIDE_SPACING - 1;
        } else {
            // No scrollbar: extend to right border (just account for the 1px border)
            itemRightEdge = x + renderWidth - 1;
        }

        for (int i = 0; i < items.size(); i++) {
            int itemY = contentY + i * ITEM_HEIGHT - (int) scrollOffset;

            // Skip items outside visible area
            if (itemY + ITEM_HEIGHT < contentY || itemY > contentY + visibleHeight) {
                continue;
            }

            DropdownItem item = items.get(i);
            boolean isHovered = mouseX >= x && mouseX < itemRightEdge &&
                              mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

            // Draw item background with CustomButton style - align with outer border, stop before scrollbar
            int itemBgColor = isHovered ? ITEM_HOVER_COLOR : ITEM_COLOR;
            int itemX = x + 1; // Start right after left border
            int itemWidth = itemRightEdge - itemX; // Width up to scrollbar or right edge

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

            // Truncate text if too long - account for scrollbar space
            int maxTextWidth = itemWidth - PADDING * 2 - 4;
            if (client.textRenderer.getWidth(displayText) > maxTextWidth) {
                displayText = client.textRenderer.trimToWidth(displayText, maxTextWidth - 10) + "...";
            }

            context.drawText(client.textRenderer, displayText, textX, textY, TEXT_COLOR, false);
        }

        context.disableScissor();

        // Draw status message if present (at the bottom, after all visible items)
        if (!statusMessage.isEmpty()) {
            // Position status message right after the visible items area
            int statusY = contentY + visibleItemsHeight;
            int statusX = x + 1; // Align with left border
            int statusWidth = renderWidth - 2; // Full width minus borders

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

            // Determine color based on message content - red for errors, green for success
            int statusColor;
            String lowerMessage = statusMessage.toLowerCase();
            if (lowerMessage.contains("error") ||
                lowerMessage.contains("failed") ||
                lowerMessage.contains("fail") ||
                lowerMessage.startsWith("✗") ||
                lowerMessage.contains("could not") ||
                lowerMessage.contains("cannot") ||
                lowerMessage.contains("unable")) {
                statusColor = ERROR_TEXT_COLOR; // Red for errors
            } else if (lowerMessage.contains("success") ||
                       lowerMessage.contains("complete") ||
                       lowerMessage.contains("downloaded") ||
                       lowerMessage.startsWith("✓")) {
                statusColor = 0xFF88FF88; // Green for success
            } else {
                statusColor = 0xFFFFFFFF; // White for info
            }

            // Draw centered
            int finalStatusWidth = client.textRenderer.getWidth(displayStatus);
            int statusTextX = x + (renderWidth - finalStatusWidth) / 2;
            int statusTextY = statusY + 1 + (ITEM_HEIGHT - client.textRenderer.fontHeight) / 2;
            context.drawText(client.textRenderer, displayStatus, statusTextX, statusTextY, statusColor, false);
        }

        // Update and render scrollbar if needed
        if (items.size() > MAX_VISIBLE_ITEMS) {
            // Position scrollbar closer to the right edge
            int scrollbarX = x + renderWidth - SCROLLBAR_WIDTH - 1; // 1px from right edge
            int scrollbarY = y + 1;

            // Only recreate scrollbar if position or height changed
            if (scrollBar == null ||
                lastScrollBarX != scrollbarX ||
                lastScrollBarY != scrollbarY ||
                lastScrollBarHeight != visibleItemsHeight) {

                scrollBar = new ScrollBar(scrollbarX, scrollbarY, visibleItemsHeight);
                lastScrollBarX = scrollbarX;
                lastScrollBarY = scrollbarY;
                lastScrollBarHeight = visibleItemsHeight;
            }

            // Set scroll data
            int contentHeight = items.size() * ITEM_HEIGHT;
            scrollBar.setScrollData(contentHeight, visibleItemsHeight);

            // Set current scroll position
            if (maxScrollOffset > 0) {
                scrollBar.setScrollPercentage(scrollOffset / maxScrollOffset);
            }

            // Render scrollbar with drag support
            if (client.getWindow() != null) {
                boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta, client.getWindow().getHandle());

                // Update scroll offset from scrollbar
                if (scrollChanged || scrollBar.isDragging()) {
                    scrollOffset = (int)(scrollBar.getScrollPercentage() * maxScrollOffset);
                }
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen || items.isEmpty()) return false;

        int height = getDropdownHeight();
        int renderWidth = width + 1;

        // Check if click is within dropdown bounds
        if (mouseX >= x && mouseX < x + renderWidth && mouseY >= y && mouseY < y + height) {
            // Check if clicking on status message area (don't select anything)
            int statusAreaStart = y + (Math.min(items.size(), MAX_VISIBLE_ITEMS) * ITEM_HEIGHT) + 1;
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
     * Check if mouse is hovering over the dropdown (to block events to elements below)
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!isOpen || items.isEmpty()) return false;

        int height = getDropdownHeight();
        int renderWidth = width + 1;
        return mouseX >= x && mouseX < x + renderWidth &&
               mouseY >= y && mouseY < y + height;
    }

    /**
     * Handle scroll wheel to scroll dropdown content
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isOpen || items.isEmpty()) return false;

        // Only handle scroll if mouse is over dropdown
        if (!isMouseOver(mouseX, mouseY)) return false;

        // Only scroll if there are more items than visible
        if (items.size() <= MAX_VISIBLE_ITEMS) return true; // Still block the event

        // Scroll by one item height per scroll notch
        double scrollAmount = -verticalAmount * ITEM_HEIGHT;
        scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset + scrollAmount));

        return true; // Block scroll from propagating to elements below
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


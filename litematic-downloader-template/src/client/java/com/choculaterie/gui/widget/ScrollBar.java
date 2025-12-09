package com.choculaterie.gui.widget;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import org.lwjgl.glfw.GLFW;

/**
 * Reusable scrollbar component for scrollable content
 */
public class ScrollBar implements Drawable {
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_COLOR = 0xFF555555;
    private static final int SCROLLBAR_HANDLE_COLOR = 0xFF888888;
    private static final int SCROLLBAR_HANDLE_HOVER_COLOR = 0xFFAAAAAA;
    
    private final int x;
    private final int y;
    private final int height;
    private double scrollPercentage = 0.0;
    private double contentHeight = 0.0;
    private double visibleHeight = 0.0;
    private boolean isDragging = false;
    private double dragStartY = 0;
    private double dragStartScroll = 0;
    private boolean isHovered = false;
    private boolean wasMouseDown = false;

    public ScrollBar(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
    }
    
    public void setScrollData(double contentHeight, double visibleHeight) {
        this.contentHeight = contentHeight;
        this.visibleHeight = visibleHeight;
    }
    
    public void setScrollPercentage(double percentage) {
        this.scrollPercentage = Math.max(0.0, Math.min(1.0, percentage));
    }
    
    public double getScrollPercentage() {
        return scrollPercentage;
    }
    
    public boolean isVisible() {
        return contentHeight > visibleHeight;
    }
    
    public boolean isDragging() {
        return isDragging;
    }

    /**
     * Update and render the scrollbar, handling mouse input directly
     * @return true if the scroll position changed due to dragging
     */
    public boolean updateAndRender(DrawContext context, int mouseX, int mouseY, float delta, long windowHandle) {
        if (!isVisible()) return false;

        // Check mouse button state directly
        boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // Calculate handle size and position
        double handleHeight = Math.max(20, (visibleHeight / contentHeight) * height);
        double maxHandleY = height - handleHeight;
        double handleY = y + (scrollPercentage * maxHandleY);

        // Check if mouse is over handle
        isHovered = mouseX >= x && mouseX < x + SCROLLBAR_WIDTH &&
                    mouseY >= handleY && mouseY < handleY + handleHeight;

        // Check if mouse is over the track (but not handle)
        boolean isOverTrack = mouseX >= x && mouseX < x + SCROLLBAR_WIDTH &&
                              mouseY >= y && mouseY < y + height;

        boolean scrollChanged = false;

        // Handle mouse press start (mouse just pressed this frame)
        if (isMouseDown && !wasMouseDown) {
            if (isHovered) {
                // Clicked on handle - start dragging
                isDragging = true;
                dragStartY = mouseY;
                dragStartScroll = scrollPercentage;
            } else if (isOverTrack) {
                // Clicked on track - jump to that position
                // Center the handle at the click position
                double clickPositionInTrack = mouseY - y - (handleHeight / 2);
                double newPercentage = Math.max(0.0, Math.min(1.0, clickPositionInTrack / maxHandleY));
                if (newPercentage != scrollPercentage) {
                    scrollPercentage = newPercentage;
                    scrollChanged = true;
                }
                // Start dragging from this new position
                isDragging = true;
                dragStartY = mouseY;
                dragStartScroll = scrollPercentage;
            }
        }

        // Handle dragging
        if (isDragging && isMouseDown) {
            double deltaYMouse = mouseY - dragStartY;
            double deltaScroll = deltaYMouse / maxHandleY;
            double newPercentage = Math.max(0.0, Math.min(1.0, dragStartScroll + deltaScroll));
            if (newPercentage != scrollPercentage) {
                scrollPercentage = newPercentage;
                scrollChanged = true;
            }
        }

        // Handle mouse release
        if (!isMouseDown && isDragging) {
            isDragging = false;
        }

        wasMouseDown = isMouseDown;

        // Draw scrollbar track
        context.fill(x, y, x + SCROLLBAR_WIDTH, y + height, SCROLLBAR_COLOR);

        // Recalculate handle position after potential scroll change
        handleY = y + (scrollPercentage * maxHandleY);

        // Draw handle
        int handleColor = (isHovered || isDragging) ? SCROLLBAR_HANDLE_HOVER_COLOR : SCROLLBAR_HANDLE_COLOR;
        context.fill(x, (int)handleY, x + SCROLLBAR_WIDTH, (int)(handleY + handleHeight), handleColor);

        return scrollChanged;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Legacy render method - use updateAndRender instead for mouse handling
        if (!isVisible()) return;
        
        // Draw scrollbar track
        context.fill(x, y, x + SCROLLBAR_WIDTH, y + height, SCROLLBAR_COLOR);
        
        // Calculate handle size and position
        double handleHeight = Math.max(20, (visibleHeight / contentHeight) * height);
        double maxHandleY = height - handleHeight;
        double handleY = y + (scrollPercentage * maxHandleY);
        
        // Check if mouse is over handle
        isHovered = mouseX >= x && mouseX < x + SCROLLBAR_WIDTH &&
                    mouseY >= handleY && mouseY < handleY + handleHeight;
        
        // Draw handle
        int handleColor = (isHovered || isDragging) ? SCROLLBAR_HANDLE_HOVER_COLOR : SCROLLBAR_HANDLE_COLOR;
        context.fill(x, (int)handleY, x + SCROLLBAR_WIDTH, (int)(handleY + handleHeight), handleColor);
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || button != 0) return false;
        
        double handleHeight = Math.max(20, (visibleHeight / contentHeight) * height);
        double maxHandleY = height - handleHeight;
        double handleY = y + (scrollPercentage * maxHandleY);
        
        if (mouseX >= x && mouseX < x + SCROLLBAR_WIDTH &&
            mouseY >= handleY && mouseY < handleY + handleHeight) {
            isDragging = true;
            dragStartY = mouseY;
            dragStartScroll = scrollPercentage;
            return true;
        }
        
        return false;
    }
    
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDragging) {
            isDragging = false;
            return true;
        }
        return false;
    }
    
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!isDragging) return false;
        
        double handleHeight = Math.max(20, (visibleHeight / contentHeight) * height);
        double maxHandleY = height - handleHeight;
        
        double deltaYMouse = mouseY - dragStartY;
        double deltaScroll = deltaYMouse / maxHandleY;
        
        setScrollPercentage(dragStartScroll + deltaScroll);
        return true;
    }
    
    public int getWidth() {
        return SCROLLBAR_WIDTH;
    }
}

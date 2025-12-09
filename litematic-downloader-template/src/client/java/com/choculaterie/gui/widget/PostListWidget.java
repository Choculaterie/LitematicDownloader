package com.choculaterie.gui.widget;

import com.choculaterie.models.MinemevPostInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable list widget for displaying posts
 */
public class PostListWidget extends ClickableWidget {
    private final List<PostEntryWidget> entries;
    private double scrollAmount = 0;
    private final OnPostClickListener onPostClick;
    private ScrollBar scrollBar;
    private double lastMouseX = 0;
    private double lastMouseY = 0;

    public interface OnPostClickListener {
        void onPostClick(MinemevPostInfo post);
    }

    public PostListWidget(int x, int y, int width, int height, OnPostClickListener onPostClick) {
        super(x, y, width, height, Text.empty());
        this.entries = new ArrayList<>();
        this.onPostClick = onPostClick;
        this.scrollBar = new ScrollBar(x + width - 8, y, height);
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.setX(x);
        this.setY(y);
        this.width = width;
        this.height = height;
        this.scrollBar = new ScrollBar(x + width - 8, y, height);
        // Rebuild entries with new width
        rebuildEntries();
    }

    private void rebuildEntries() {
        List<MinemevPostInfo> posts = new ArrayList<>();
        for (PostEntryWidget entry : entries) {
            posts.add(entry.getPost());
        }
        if (!posts.isEmpty()) {
            setPosts(posts.toArray(new MinemevPostInfo[0]));
        }
    }

    public void setPosts(MinemevPostInfo[] posts) {
        this.entries.clear();
        this.scrollAmount = 0;

        int currentY = 0;
        for (MinemevPostInfo post : posts) {
            PostEntryWidget entry = new PostEntryWidget(
                post,
                getX(),
                getY() + currentY,
                width - 10, // Leave space for scrollbar
                () -> {
                    if (onPostClick != null) {
                        onPostClick.onPostClick(post);
                    }
                }
            );
            entries.add(entry);
            currentY += entry.getHeight() + 2;
        }

        updateScrollBar();
    }

    public void clear() {
        this.entries.clear();
        this.scrollAmount = 0;
        updateScrollBar();
    }

    private void updateScrollBar() {
        double contentHeight = getTotalContentHeight();
        scrollBar.setScrollData(contentHeight, height);
        scrollBar.setScrollPercentage(contentHeight > height ? scrollAmount / (contentHeight - height) : 0);
    }

    private double getTotalContentHeight() {
        int totalHeight = 0;
        for (PostEntryWidget entry : entries) {
            totalHeight += entry.getHeight() + 2;
        }
        return totalHeight;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Store mouse position for click handling
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        // Enable scissor for clipping
        context.enableScissor(getX(), getY(), getX() + width, getY() + height);

        int offsetY = (int) scrollAmount;

        for (int i = 0; i < entries.size(); i++) {
            PostEntryWidget entry = entries.get(i);
            int entryY = getY() + (i * (entry.getHeight() + 2)) - offsetY;

            // Only render if visible in the viewport
            if (entryY + entry.getHeight() >= getY() && entryY < getY() + height) {
                // Temporarily modify the entry's position for rendering
                int originalY = entry.y;
                entry.y = entryY;

                // Pass the actual mouse coordinates (no adjustment needed)
                entry.render(context, mouseX, mouseY, delta);

                // Restore original position
                entry.y = originalY;
            }
        }

        context.disableScissor();

        // Render scrollbar with direct mouse handling
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getWindow() != null) {
            long windowHandle = client.getWindow().getHandle();
            if (scrollBar.updateAndRender(context, mouseX, mouseY, delta, windowHandle)) {
                // Scroll position changed, update scroll amount
                double maxScroll = getMaxScroll();
                scrollAmount = scrollBar.getScrollPercentage() * maxScroll;
            }
        } else {
            // Fallback to regular render
            scrollBar.render(context, mouseX, mouseY, delta);
        }
    }

    protected void onPress() {
        System.out.println("[PostListWidget] onPress called!");
        // Use the stored mouse position
        handleClick(lastMouseX, lastMouseY, 0);
    }

    public void onClick(double mouseX, double mouseY) {
        System.out.println("[PostListWidget] onClick called - mouseX: " + mouseX + ", mouseY: " + mouseY);
        handleClick(mouseX, mouseY, 0);
    }

    private void handleClick(double mouseX, double mouseY, int button) {
        System.out.println("[PostListWidget] handleClick called - mouseX: " + mouseX + ", mouseY: " + mouseY + ", button: " + button);
        System.out.println("[PostListWidget] List bounds - x: " + getX() + ", y: " + getY() + ", width: " + width + ", height: " + height);

        // Check scrollbar first
        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            System.out.println("[PostListWidget] Scrollbar handled the click");
            return;
        }

        if (mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height) {
            System.out.println("[PostListWidget] Click is within list bounds");
            int offsetY = (int) scrollAmount;

            for (int i = 0; i < entries.size(); i++) {
                PostEntryWidget entry = entries.get(i);
                int entryY = getY() + (i * (entry.getHeight() + 2)) - offsetY;

                // Check if this entry is visible and the mouse is over it
                if (entryY + entry.getHeight() >= getY() && entryY < getY() + height) {
                    if (mouseY >= entryY && mouseY < entryY + entry.getHeight()) {
                        System.out.println("[PostListWidget] Forwarding click to entry #" + i + " at entryY: " + entryY);
                        // Temporarily set the entry's Y position
                        int originalY = entry.y;
                        entry.y = entryY;
                        entry.mouseClicked(mouseX, mouseY, 0);
                        entry.y = originalY;
                        System.out.println("[PostListWidget] Click forwarded to entry");
                        return;
                    }
                }
            }
            System.out.println("[PostListWidget] No entry matched the click position");
        } else {
            System.out.println("[PostListWidget] Click is outside list bounds");
        }
    }

    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        // Handle dragging for scrollbar
        if (scrollBar.isDragging() || scrollBar.mouseDragged(mouseX, mouseY, 0, deltaX, deltaY)) {
            scrollBar.mouseDragged(mouseX, mouseY, 0, deltaX, deltaY);
            // Update scroll amount based on scrollbar
            double maxScroll = getMaxScroll();
            scrollAmount = scrollBar.getScrollPercentage() * maxScroll;
        }
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Always check scrollbar for release, even if mouse moved outside
        if (scrollBar.isDragging()) {
            return scrollBar.mouseReleased(mouseX, mouseY, button);
        }

        // Forward to entries
        if (mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height) {
            int offsetY = (int) scrollAmount;

            for (int i = 0; i < entries.size(); i++) {
                PostEntryWidget entry = entries.get(i);
                int entryY = getY() + (i * (entry.getHeight() + 2)) - offsetY;

                if (entryY + entry.getHeight() >= getY() && entryY < getY() + height) {
                    int originalY = entry.y;
                    entry.y = entryY;
                    boolean result = entry.mouseReleased(mouseX, mouseY, button);
                    entry.y = originalY;
                    if (result) {
                        return true;
                    }
                }
            }
        }

        return scrollBar.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Always forward drag events to scrollbar if it's being dragged
        if (scrollBar.isDragging() || scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            // Update scroll amount based on scrollbar
            double maxScroll = getMaxScroll();
            scrollAmount = scrollBar.getScrollPercentage() * maxScroll;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height) {
            double maxScroll = getMaxScroll();
            scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount - verticalAmount * 20));
            updateScrollBar();
            return true;
        }
        return false;
    }

    private double getMaxScroll() {
        return Math.max(0, getTotalContentHeight() - height);
    }

    @Override
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        // Add narration for accessibility
    }
}


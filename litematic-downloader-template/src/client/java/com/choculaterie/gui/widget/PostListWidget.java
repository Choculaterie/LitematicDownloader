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
    private static final int ENTRY_SPACING = 2;
    private static final int SCROLLBAR_PADDING = 10;
    private final List<PostEntryButton> entries;
    private double scrollAmount = 0;
    private final OnPostClickListener onPostClick;
    private ScrollBar scrollBar;

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
        // Rebuild entries with new width, preserving scroll position
        double savedScroll = this.scrollAmount;
        rebuildEntries();
        this.scrollAmount = savedScroll;
        updateScrollBar();
    }

    public double getScrollAmount() {
        return scrollAmount;
    }

    public void setScrollAmount(double scrollAmount) {
        this.scrollAmount = scrollAmount;
        updateScrollBar();
    }

    private void rebuildEntries() {
        List<MinemevPostInfo> posts = new ArrayList<>();
        for (PostEntryButton entry : entries) {
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
            int rowWidth = Math.max(0, width - SCROLLBAR_PADDING);
            Runnable clickAction = () -> {
                if (onPostClick != null) {
                    onPostClick.onPostClick(post);
                }
            };
            PostEntryWidget visual = new PostEntryWidget(
                post,
                getX(),
                getY() + currentY,
                rowWidth,
                clickAction
            );
            PostEntryButton button = new PostEntryButton(visual, clickAction);
            entries.add(button);
            currentY += button.getRowHeight() + ENTRY_SPACING;
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
        for (PostEntryButton entry : entries) {
            totalHeight += entry.getRowHeight() + ENTRY_SPACING;
        }
        return totalHeight;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Enable scissor for clipping
        context.enableScissor(getX(), getY(), getX() + width, getY() + height);

        int offsetY = (int) scrollAmount;
        int currentY = 0;

        for (int i = 0; i < entries.size(); i++) {
            PostEntryButton entry = entries.get(i);
            int entryY = getY() + currentY - offsetY;

            if (entryY + entry.getRowHeight() >= getY() && entryY < getY() + height) {
                entry.updateBounds(getX(), entryY, Math.max(0, width - SCROLLBAR_PADDING));
                entry.render(context, mouseX, mouseY, delta);
            }

            currentY += entry.getRowHeight() + ENTRY_SPACING;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        System.out.println("[PostListWidget] mouseClicked called - mouseX: " + mouseX + ", mouseY: " + mouseY + ", button: " + button);

        // Check scrollbar first
        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            System.out.println("[PostListWidget] Scrollbar handled the click");
            return true;
        }

        // Check if click is within list bounds
        if (button == 0 && mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height) {
            int offsetY = (int) scrollAmount;
            int currentY = 0;

            for (int i = 0; i < entries.size(); i++) {
                PostEntryButton entry = entries.get(i);
                int entryY = getY() + currentY - offsetY;

                // Check if entry is visible and clicked
                if (entryY + entry.getRowHeight() >= getY() && entryY < getY() + height) {
                    entry.updateBounds(getX(), entryY, Math.max(0, width - SCROLLBAR_PADDING));
                    if (entry.handlePress(mouseX, mouseY, button)) {
                        System.out.println("[PostListWidget] Entry " + i + " handled the click and triggered callback!");
                        return true;
                    }
                }

                currentY += entry.getRowHeight() + ENTRY_SPACING;
            }
        }

        System.out.println("[PostListWidget] Click not handled by any entry");
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Always check scrollbar for release, even if mouse moved outside
        if (scrollBar.isDragging()) {
            return scrollBar.mouseReleased(mouseX, mouseY, button);
        }

        // Forward to entries
        if (mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height) {
            int offsetY = (int) scrollAmount;

            for (int i = 0; i < entries.size(); i++) {
                PostEntryButton entry = entries.get(i);
                int entryY = getY() + (i * (entry.getRowHeight() + ENTRY_SPACING)) - offsetY;

                if (entryY + entry.getRowHeight() >= getY() && entryY < getY() + height) {
                    entry.updateBounds(getX(), entryY, Math.max(0, width - SCROLLBAR_PADDING));
                    if (entry.handleRelease(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
        }

        return scrollBar.mouseReleased(mouseX, mouseY, button);
    }

    @Override
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

    private static final class PostEntryButton extends ClickableWidget {
        private final PostEntryWidget visual;
        private final MinemevPostInfo post;
        private final Runnable clickAction;
        private boolean pressed;

        private PostEntryButton(PostEntryWidget visual, Runnable clickAction) {
            super(visual.getX(), visual.y, visual.getWidth(), visual.getHeight(), Text.empty());
            this.visual = visual;
            this.post = visual.getPost();
            this.clickAction = clickAction;
        }

        private void updateBounds(int x, int y, int width) {
            this.setX(x);
            this.setY(y);
            this.setWidth(width);
            this.height = visual.getHeight();
            visual.setX(x);
            visual.setWidth(width);
            visual.setY(y);
        }

        private int getRowHeight() {
            return visual.getHeight();
        }

        private MinemevPostInfo getPost() {
            return post;
        }

        private boolean handlePress(double mouseX, double mouseY, int button) {
            if (button != 0 || !isPointInside(mouseX, mouseY)) {
                return false;
            }
            pressed = true;
            visual.setPressed(true);
            this.setFocused(true);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getSoundManager() != null) {
                this.playDownSound(client.getSoundManager());
            }
            if (clickAction != null) {
                clickAction.run();
            }
            return true;
        }

        private boolean handleRelease(double mouseX, double mouseY, int button) {
            if (!pressed) {
                return false;
            }
            pressed = false;
            visual.setPressed(false);
            return true;
        }

        private boolean isPointInside(double mouseX, double mouseY) {
            return mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight();
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            visual.render(context, mouseX, mouseY, delta);
            if (this.isHovered()) {
                context.fill(getX(), getY(), getX() + this.width, getY() + this.height, 0x30FFFFFF);
            }
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
            builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE, Text.literal("Post entry"));
        }
    }
}



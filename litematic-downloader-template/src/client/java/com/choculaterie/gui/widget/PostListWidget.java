package com.choculaterie.gui.widget;

import com.choculaterie.vanilib.gui.widget.ScrollBar;

import com.choculaterie.vanilib.gui.theme.UITheme;
import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.models.PromotionInfo;
import com.choculaterie.network.ChoculaterieNetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.mojang.blaze3d.platform.InputConstants;

public class PostListWidget extends AbstractWidget {
    private static final int ENTRY_SPACING = 2;
    private static final int SCROLL_SPEED = 20;

    private final List<PostEntryButton> entries;
    private double scrollAmount = 0;
    private final OnPostClickListener onPostClick;
    private MinemevPostInfo selectedPost;
    private ScrollBar scrollBar;
    private MinemevPostInfo[] posts = new MinemevPostInfo[0];
    private List<PromotionInfo> promotions = List.of();
    private PromotionInfo shownPromotion;
    private int promotionIndex = -1;

    public interface OnPostClickListener {
        void onPostClick(MinemevPostInfo post);
    }

    public PostListWidget(int x, int y, int width, int height, OnPostClickListener onPostClick) {
        super(x, y, width, height, Component.empty());
        this.entries = new ArrayList<>();
        this.onPostClick = onPostClick;
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH, y, height);
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.setX(x);
        this.setY(y);
        this.width = width;
        this.height = height;
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH, y, height);
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
        if (posts.length > 0) {
            buildEntries();
        }
    }

    public void setPosts(MinemevPostInfo[] posts) {
        this.posts = posts;
        this.scrollAmount = 0;
        pickPromotion();
        buildEntries();
        updateScrollBar();
    }

    public void setPromotions(List<PromotionInfo> promotions) {
        this.promotions = promotions != null ? promotions : List.of();
        if (shownPromotion == null && posts.length > 0) {
            double savedScroll = scrollAmount;
            pickPromotion();
            buildEntries();
            scrollAmount = savedScroll;
            updateScrollBar();
        }
    }

    private void pickPromotion() {
        if (promotions.isEmpty() || posts.length == 0) {
            shownPromotion = null;
            promotionIndex = -1;
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        shownPromotion = promotions.get(random.nextInt(promotions.size()));
        promotionIndex = posts.length == 1 ? 1 : 1 + random.nextInt(posts.length);
    }

    private void buildEntries() {
        this.entries.clear();
        int rowWidth = Math.max(0, width - UITheme.Dimensions.PADDING);
        for (int i = 0; i < posts.length; i++) {
            if (i == promotionIndex) {
                addPromotionEntry(rowWidth);
            }
            MinemevPostInfo post = posts[i];
            Runnable clickAction = () -> {
                selectedPost = post;
                if (onPostClick != null) {
                    onPostClick.onPostClick(post);
                }
            };
            PostEntryWidget visual = new PostEntryWidget(post, getX(), getY(), rowWidth, clickAction);
            entries.add(new PostEntryButton(visual, post, clickAction));
        }
        if (promotionIndex >= posts.length) {
            addPromotionEntry(rowWidth);
        }
    }

    private void addPromotionEntry(int rowWidth) {
        PromotionInfo promotion = shownPromotion;
        if (promotion == null) return;
        Runnable clickAction = () -> openPromotion(promotion);
        PromotionEntryWidget visual = new PromotionEntryWidget(promotion, getX(), getY(), rowWidth);
        entries.add(new PostEntryButton(visual, null, clickAction));
    }

    private static void openPromotion(PromotionInfo promotion) {
        URI uri;
        try {
            uri = URI.create(promotion.linkUrl());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return;
        ChoculaterieNetworkManager.recordPromotionClick(promotion.key());
        Minecraft client = Minecraft.getInstance();
        ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), uri);
    }

    public void clear() {
        this.entries.clear();
        this.posts = new MinemevPostInfo[0];
        this.shownPromotion = null;
        this.promotionIndex = -1;
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
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.enableScissor(getX(), getY(), getX() + width, getY() + height);

        int offsetY = (int) scrollAmount;
        int currentY = 0;
        int entryWidth = getEntryWidth();
        boolean pointerInside = mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height;
        int entryMouseY = pointerInside ? mouseY : Integer.MIN_VALUE / 2;

        for (PostEntryButton entry : entries) {
            int entryY = getY() + currentY - offsetY;

            if (isEntryVisible(entryY, entry.getRowHeight())) {
                entry.updateBounds(getX(), entryY, entryWidth);
                entry.visual.setSelected(selectedPost != null && entry.post != null && entry.post == selectedPost);
                entry.extractRenderState(context, mouseX, entryMouseY, delta);
            }

            currentY += entry.getRowHeight() + ENTRY_SPACING;
        }

        context.disableScissor();

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getWindow() != null) {
            long windowHandle = client.getWindow().handle();
            if (scrollBar.updateAndRender(context, mouseX, mouseY, delta, windowHandle)) {
                double maxScroll = getMaxScroll();
                scrollAmount = scrollBar.getScrollPercentage() * maxScroll;
            }
        } else {
            scrollBar.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == InputConstants.MOUSE_BUTTON_LEFT && isMouseOver(mouseX, mouseY)) {
            int offsetY = (int) scrollAmount;
            int currentY = 0;
            int entryWidth = getEntryWidth();

            for (PostEntryButton entry : entries) {
                int entryY = getY() + currentY - offsetY;

                if (isEntryVisible(entryY, entry.getRowHeight())) {
                    entry.updateBounds(getX(), entryY, entryWidth);
                    if (entry.handlePress(mouseX, mouseY, button)) {
                        return true;
                    }
                }

                currentY += entry.getRowHeight() + ENTRY_SPACING;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width &&
               mouseY >= getY() && mouseY < getY() + height;
    }

    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (scrollBar.isDragging() || scrollBar.mouseDragged(mouseX, mouseY, InputConstants.MOUSE_BUTTON_LEFT, deltaX, deltaY)) {
            scrollBar.mouseDragged(mouseX, mouseY, InputConstants.MOUSE_BUTTON_LEFT, deltaX, deltaY);
            double maxScroll = getMaxScroll();
            scrollAmount = scrollBar.getScrollPercentage() * maxScroll;
        }
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollBar.isDragging()) {
            return scrollBar.mouseReleased(mouseX, mouseY, button);
        }

        if (isMouseOver(mouseX, mouseY)) {
            int offsetY = (int) scrollAmount;
            int currentY = 0;
            int entryWidth = getEntryWidth();

            for (PostEntryButton entry : entries) {
                int entryY = getY() + currentY - offsetY;

                if (isEntryVisible(entryY, entry.getRowHeight())) {
                    entry.updateBounds(getX(), entryY, entryWidth);
                    if (entry.handleRelease(mouseX, mouseY, button)) {
                        return true;
                    }
                }

                currentY += entry.getRowHeight() + ENTRY_SPACING;
            }
        }

        return scrollBar.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollBar.isDragging() || scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            double maxScroll = getMaxScroll();
            scrollAmount = scrollBar.getScrollPercentage() * maxScroll;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOver(mouseX, mouseY)) {
            double maxScroll = getMaxScroll();
            scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount - verticalAmount * SCROLL_SPEED));
            updateScrollBar();
            return true;
        }
        return false;
    }

    private double getMaxScroll() {
        return Math.max(0, getTotalContentHeight() - height);
    }

    private boolean isEntryVisible(int entryY, int entryHeight) {
        return entryY + entryHeight >= getY() && entryY < getY() + height;
    }

    private int getEntryWidth() {
        return Math.max(0, width - UITheme.Dimensions.PADDING);
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
    }

    private static final class PostEntryButton extends AbstractWidget {
        private final ListEntryVisual visual;
        private final MinemevPostInfo post;
        private final Runnable clickAction;
        private boolean pressed;

        private PostEntryButton(ListEntryVisual visual, MinemevPostInfo post, Runnable clickAction) {
            super(visual.getX(), visual.getY(), visual.getWidth(), visual.getHeight(), Component.empty());
            this.visual = visual;
            this.post = post;
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

        private boolean handlePress(double mouseX, double mouseY, int button) {
            if (button != InputConstants.MOUSE_BUTTON_LEFT || !isPointInside(mouseX, mouseY)) {
                return false;
            }
            pressed = true;
            visual.setPressed(true);
            this.setFocused(true);
            Minecraft client = Minecraft.getInstance();
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
        public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            visual.extractRenderState(context, mouseX, mouseY, delta);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
            builder.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.literal("Post entry"));
        }
    }
}


package com.choculaterie.gui.widget;

import com.choculaterie.vanilib.gui.theme.UIDraw;
import com.choculaterie.vanilib.gui.theme.UIIcon;
import com.choculaterie.vanilib.gui.theme.UITheme;
import com.choculaterie.models.MinemevPostInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import com.mojang.blaze3d.platform.InputConstants;

public class PostEntryWidget implements Renderable, GuiEventListener {
    private static final int MIN_ENTRY_HEIGHT = 70;
    private static final int LINE_HEIGHT = UITheme.Typography.LINE_HEIGHT;
    private static final int CONTENT_SPACING = 5;
    private static final int ENTRY_BG_COLOR = UITheme.Colors.SURFACE;
    private static final int ENTRY_HOVER_COLOR = UITheme.Colors.SURFACE_HOVER;
    private static final int ENTRY_PRESSED_COLOR = UITheme.Colors.SURFACE_SELECTED;
    private static final int TEXT_COLOR = UITheme.Colors.TEXT_HIGHLIGHT;

    private final MinemevPostInfo post;
    private int x;
    private int y;
    private int width;
    private final Minecraft client;
    private final Runnable onClick;
    private boolean isHovered = false;
    private boolean isPressed = false;
    private boolean selected = false;
    private int calculatedHeight = MIN_ENTRY_HEIGHT;

    public PostEntryWidget(MinemevPostInfo post, int x, int y, int width, Runnable onClick) {
        this.post = post;
        this.x = x;
        this.y = y;
        this.width = width;
        this.client = Minecraft.getInstance();
        this.onClick = onClick;
        calculateHeight();
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setWidth(int width) {
        this.width = width;
        calculateHeight();
    }

    private void calculateHeight() {
        int currentY = UITheme.Dimensions.PADDING;
        int contentWidth = width - UITheme.Dimensions.PADDING * 2;

        if (post.title() != null && !post.title().isEmpty()) {
            currentY += getWrappedTextHeight(post.title(), contentWidth) + CONTENT_SPACING;
        }

        currentY += LINE_HEIGHT + CONTENT_SPACING;

        if (post.tags() != null && post.tags().length > 0) {
            StringBuilder tags = new StringBuilder();
            for (int i = 0; i < post.tags().length; i++) {
                if (i > 0) tags.append(", ");
                tags.append(post.tags()[i]);
            }
            currentY += getWrappedTextHeight(tags.toString(), contentWidth) + CONTENT_SPACING;
        }

        currentY += UITheme.Dimensions.PADDING;
        calculatedHeight = Math.max(MIN_ENTRY_HEIGHT, currentY);
    }

    private int getWrappedTextHeight(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return LINE_HEIGHT;

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lines = 1;

        for (String word : words) {
            String testLine = !line.isEmpty() ? line + " " + word : word;
            int testWidth = client.font.width(testLine);

            if (testWidth > maxWidth && !line.isEmpty()) {
                line = new StringBuilder(word);
                lines++;
            } else {
                line = new StringBuilder(testLine);
            }
        }

        return lines * LINE_HEIGHT;
    }

    private void drawWrappedText(GuiGraphicsExtractor context, String text, int textX, int textY, int maxWidth, int color) {
        if (text == null || text.isEmpty()) return;

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = textY;

        for (String word : words) {
            String testLine = !line.isEmpty() ? line + " " + word : word;
            int testWidth = client.font.width(testLine);

            if (testWidth > maxWidth && !line.isEmpty()) {
                UIDraw.text(context, line.toString(), textX, lineY, color);
                line = new StringBuilder(word);
                lineY += LINE_HEIGHT;
            } else {
                line = new StringBuilder(testLine);
            }
        }

        if (!line.isEmpty()) {
            UIDraw.text(context, line.toString(), textX, lineY, color);
        }
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateHoverState(mouseX, mouseY);

        int bgColor = getBackgroundColor();
        drawBackground(context, bgColor);
        drawBorder(context);

        renderContent(context);
    }

    private void updateHoverState(int mouseX, int mouseY) {
        this.isHovered = mouseX >= x && mouseY >= y &&
                        mouseX < x + width && mouseY < y + calculatedHeight;
    }

    private int getBackgroundColor() {
        if (isPressed && isHovered) {
            return ENTRY_PRESSED_COLOR;
        } else if (isHovered) {
            return ENTRY_HOVER_COLOR;
        }
        return ENTRY_BG_COLOR;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    private void drawBackground(GuiGraphicsExtractor context, int bgColor) {
        context.fill(x, y, x + width, y + calculatedHeight, selected ? UITheme.Colors.SURFACE_SELECTED : bgColor);
    }

    private void drawBorder(GuiGraphicsExtractor context) {
        int border = selected ? UITheme.Colors.ACCENT : (isHovered ? UITheme.Colors.SCROLLBAR_THUMB_HOVER : UITheme.Colors.PANEL_BORDER);
        UIDraw.outline(context, x, y, x + width, y + calculatedHeight, border);
        if (selected) {
            context.fill(x + 1, y + 1, x + 3, y + calculatedHeight - 1, UITheme.Colors.ACCENT);
        }
    }

    private void renderContent(GuiGraphicsExtractor context) {
        int currentY = y + UITheme.Dimensions.PADDING;
        int contentWidth = width - UITheme.Dimensions.PADDING * 2;

        currentY = renderTitle(context, currentY, contentWidth);
        currentY = renderInfo(context, currentY);
        renderTags(context, currentY, contentWidth);
    }

    private int renderTitle(GuiGraphicsExtractor context, int currentY, int contentWidth) {
        String title = post.title();
        if (title != null && !title.isEmpty()) {
            drawWrappedText(context, title, x + UITheme.Dimensions.PADDING, currentY, contentWidth, TEXT_COLOR);
            currentY += getWrappedTextHeight(title, contentWidth) + CONTENT_SPACING;
        }
        return currentY;
    }

    private int renderInfo(GuiGraphicsExtractor context, int currentY) {
        String author = "By " + (post.author() != null ? post.author() : "Unknown");
        String downloads = String.valueOf(post.downloads());
        int downloadsWidth = UIIcon.SIZE + 4 + client.font.width(downloads);
        int rightEdge = x + width - UITheme.Dimensions.PADDING;
        UIDraw.text(context, UIDraw.ellipsize(author, rightEdge - downloadsWidth - 8 - (x + UITheme.Dimensions.PADDING)),
            x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
        UIDraw.iconText(context, UIIcon.DOWNLOAD, downloads, rightEdge - downloadsWidth, currentY, UITheme.Colors.TEXT_MUTED);
        return currentY + LINE_HEIGHT + CONTENT_SPACING;
    }

    private void renderTags(GuiGraphicsExtractor context, int currentY, int contentWidth) {
        if (post.tags() != null && post.tags().length > 0) {
            StringBuilder tags = new StringBuilder();
            for (int i = 0; i < post.tags().length; i++) {
                if (i > 0) tags.append(", ");
                tags.append(post.tags()[i]);
            }
            drawWrappedText(context, tags.toString(), x + UITheme.Dimensions.PADDING, currentY, contentWidth, UITheme.Colors.TEXT_TAG);
        }
    }

    public int getHeight() {
        return calculatedHeight;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }


    public MinemevPostInfo getPost() {
        return post;
    }

    public void setPressed(boolean pressed) {
        this.isPressed = pressed;
    }

    public boolean isPressed() {
        return isPressed;
    }

    public void setHovered(boolean hovered) {
        this.isHovered = hovered;
    }

    public boolean isHovered() {
        return isHovered;
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;

        if (mouseX >= x && mouseX < x + width &&
            mouseY >= y && mouseY < y + calculatedHeight) {
            isPressed = true;
            if (onClick != null) {
                onClick.run();
            }
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isPressed) {
            isPressed = false;
            return true;
        }
        return false;
    }
}

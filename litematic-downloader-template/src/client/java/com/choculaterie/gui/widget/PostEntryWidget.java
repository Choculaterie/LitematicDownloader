package com.choculaterie.gui.widget;

import com.choculaterie.models.MinemevPostInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.text.Text;

/**
 * Widget representing a single post entry in the list
 */
public class PostEntryWidget implements Drawable, Element {
    private static final int MIN_ENTRY_HEIGHT = 70;
    private static final int PADDING = 10;
    private static final int LINE_HEIGHT = 10;
    private static final int ENTRY_BG_COLOR = 0xFF3A3A3A;
    private static final int ENTRY_HOVER_COLOR = 0xFF4A4A4A;
    private static final int ENTRY_PRESSED_COLOR = 0xFF2A2A2A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SUBTITLE_COLOR = 0xFFAAAAAA;
    private static final int BORDER_COLOR = 0xFF555555;

    private final MinemevPostInfo post;
    private int x;
    int y; // Package-private and non-final for PostListWidget to modify during rendering
    private int width;
    private final MinecraftClient client;
    private final Runnable onClick;
    private boolean isHovered = false;
    private boolean isPressed = false;
    private int calculatedHeight = MIN_ENTRY_HEIGHT;

    public PostEntryWidget(MinemevPostInfo post, int x, int y, int width, Runnable onClick) {
        this.post = post;
        this.x = x;
        this.y = y;
        this.width = width;
        this.client = MinecraftClient.getInstance();
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
        int currentY = PADDING;
        int contentWidth = width - PADDING * 2;

        // Title height (wrapped)
        if (post.getTitle() != null && !post.getTitle().isEmpty()) {
            currentY += getWrappedTextHeight(post.getTitle(), contentWidth) + 5;
        }

        // Author/downloads line
        currentY += LINE_HEIGHT + 5;

        // Tags (wrapped)
        if (post.getTags() != null && post.getTags().length > 0) {
            StringBuilder tags = new StringBuilder();
            for (int i = 0; i < post.getTags().length; i++) {
                if (i > 0) tags.append(", ");
                tags.append(post.getTags()[i]);
            }
            currentY += getWrappedTextHeight(tags.toString(), contentWidth) + 5;
        }

        currentY += PADDING;
        calculatedHeight = Math.max(MIN_ENTRY_HEIGHT, currentY);
    }

    private int getWrappedTextHeight(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return LINE_HEIGHT;

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lines = 1;

        for (String word : words) {
            String testLine = !line.isEmpty() ? line + " " + word : word;
            int testWidth = client.textRenderer.getWidth(testLine);

            if (testWidth > maxWidth && !line.isEmpty()) {
                line = new StringBuilder(word);
                lines++;
            } else {
                line = new StringBuilder(testLine);
            }
        }

        return lines * LINE_HEIGHT;
    }

    private void drawWrappedText(DrawContext context, String text, int textX, int textY, int maxWidth, int color) {
        if (text == null || text.isEmpty()) return;

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = textY;

        for (String word : words) {
            String testLine = !line.isEmpty() ? line + " " + word : word;
            int testWidth = client.textRenderer.getWidth(testLine);

            if (testWidth > maxWidth && !line.isEmpty()) {
                context.drawTextWithShadow(client.textRenderer, line.toString(), textX, lineY, color);
                line = new StringBuilder(word);
                lineY += LINE_HEIGHT;
            } else {
                line = new StringBuilder(testLine);
            }
        }

        if (!line.isEmpty()) {
            context.drawTextWithShadow(client.textRenderer, line.toString(), textX, lineY, color);
        }
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Use the y coordinate that was set by PostListWidget during rendering
        this.isHovered = mouseX >= x && mouseY >= y &&
                        mouseX < x + width && mouseY < y + calculatedHeight;

        // Draw background with button-like styling
        int bgColor;
        if (isPressed && isHovered) {
            bgColor = ENTRY_PRESSED_COLOR;
        } else if (isHovered) {
            bgColor = ENTRY_HOVER_COLOR;
        } else {
            bgColor = ENTRY_BG_COLOR;
        }

        context.fill(x, y, x + width, y + calculatedHeight, bgColor);

        // Draw borders (like CustomButton)
        context.fill(x, y, x + width, y + 1, BORDER_COLOR); // Top
        context.fill(x, y + calculatedHeight - 1, x + width, y + calculatedHeight, BORDER_COLOR); // Bottom
        context.fill(x, y, x + 1, y + calculatedHeight, BORDER_COLOR); // Left
        context.fill(x + width - 1, y, x + width, y + calculatedHeight, BORDER_COLOR); // Right

        int currentY = y + PADDING;
        int contentWidth = width - PADDING * 2;

        // Draw title (wrapped)
        String title = post.getTitle();
        if (title != null && !title.isEmpty()) {
            drawWrappedText(context, title, x + PADDING, currentY, contentWidth, TEXT_COLOR);
            currentY += getWrappedTextHeight(title, contentWidth) + 5;
        }

        // Draw author and downloads info
        String author = post.getAuthor() != null ? post.getAuthor() : "Unknown";
        String downloads = String.format("Downloads: %d", post.getDownloads());
        String info = String.format("By %s | %s", author, downloads);

        context.drawTextWithShadow(client.textRenderer, info,
            x + PADDING, currentY, SUBTITLE_COLOR);
        currentY += LINE_HEIGHT + 5;

        // Draw tags (wrapped)
        if (post.getTags() != null && post.getTags().length > 0) {
            StringBuilder tags = new StringBuilder();
            for (int i = 0; i < post.getTags().length; i++) {
                if (i > 0) tags.append(", ");
                tags.append(post.getTags()[i]);
            }
            drawWrappedText(context, tags.toString(), x + PADDING, currentY, contentWidth, SUBTITLE_COLOR);
        }
    }

    public int getHeight() {
        return calculatedHeight;
    }

    public int getWidth() {
        return width;
    }

    public int getX() {
        return x;
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
        // Check if mouse is within this entry's bounds
        System.out.println("[PostEntryWidget] mouseClicked called - mouseX: " + mouseX + ", mouseY: " + mouseY + ", button: " + button);
        System.out.println("[PostEntryWidget] Entry bounds - x: " + x + ", y: " + y + ", width: " + width + ", height: " + calculatedHeight);
        System.out.println("[PostEntryWidget] Post title: " + (post != null ? post.getTitle() : "null"));

        if (mouseX >= x && mouseX < x + width &&
            mouseY >= y && mouseY < y + calculatedHeight && button == 0) {
            isPressed = true;
            System.out.println("[PostEntryWidget] CLICK DETECTED! Calling onClick for: " + post.getTitle());
            if (onClick != null) {
                onClick.run();
                System.out.println("[PostEntryWidget] onClick.run() completed");
            } else {
                System.out.println("[PostEntryWidget] WARNING: onClick is null!");
            }
            return true;
        }
        System.out.println("[PostEntryWidget] Click outside bounds, ignoring");
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

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
    private static final int ENTRY_HEIGHT = 70;
    private static final int ENTRY_BG_COLOR = 0xFF3A3A3A;
    private static final int ENTRY_HOVER_COLOR = 0xFF4A4A4A;
    private static final int ENTRY_PRESSED_COLOR = 0xFF2A2A2A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SUBTITLE_COLOR = 0xFFAAAAAA;
    private static final int BORDER_COLOR = 0xFF555555;

    private final MinemevPostInfo post;
    private final int x;
    int y; // Package-private and non-final for PostListWidget to modify during rendering
    private final int width;
    private final MinecraftClient client;
    private final Runnable onClick;
    private boolean isHovered = false;
    private boolean isPressed = false;

    public PostEntryWidget(MinemevPostInfo post, int x, int y, int width, Runnable onClick) {
        this.post = post;
        this.x = x;
        this.y = y;
        this.width = width;
        this.client = MinecraftClient.getInstance();
        this.onClick = onClick;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Use the y coordinate that was set by PostListWidget during rendering
        this.isHovered = mouseX >= x && mouseY >= y &&
                        mouseX < x + width && mouseY < y + ENTRY_HEIGHT;

        // Draw background with button-like styling
        int bgColor;
        if (isPressed && isHovered) {
            bgColor = ENTRY_PRESSED_COLOR;
        } else if (isHovered) {
            bgColor = ENTRY_HOVER_COLOR;
        } else {
            bgColor = ENTRY_BG_COLOR;
        }

        context.fill(x, y, x + width, y + ENTRY_HEIGHT, bgColor);

        // Draw borders (like CustomButton)
        context.fill(x, y, x + width, y + 1, BORDER_COLOR); // Top
        context.fill(x, y + ENTRY_HEIGHT - 1, x + width, y + ENTRY_HEIGHT, BORDER_COLOR); // Bottom
        context.fill(x, y, x + 1, y + ENTRY_HEIGHT, BORDER_COLOR); // Left
        context.fill(x + width - 1, y, x + width, y + ENTRY_HEIGHT, BORDER_COLOR); // Right

        // Draw title
        String title = post.getTitle();
        if (title != null && !title.isEmpty()) {
            context.drawTextWithShadow(client.textRenderer,
                Text.literal(title).getString(),
                x + 10, y + 10, TEXT_COLOR);
        }

        // Draw author and downloads info
        String author = post.getAuthor() != null ? post.getAuthor() : "Unknown";
        String downloads = String.format("Downloads: %d", post.getDownloads());
        String info = String.format("By %s | %s", author, downloads);

        context.drawTextWithShadow(client.textRenderer, info,
            x + 10, y + 30, SUBTITLE_COLOR);

        // Draw tags if available
        if (post.getTags() != null && post.getTags().length > 0) {
            StringBuilder tags = new StringBuilder("Tags: ");
            for (int i = 0; i < Math.min(3, post.getTags().length); i++) {
                if (i > 0) tags.append(", ");
                tags.append(post.getTags()[i]);
            }
            if (post.getTags().length > 3) {
                tags.append("...");
            }

            context.drawTextWithShadow(client.textRenderer, tags.toString(),
                x + 10, y + 50, SUBTITLE_COLOR);
        }
    }

    public int getHeight() {
        return ENTRY_HEIGHT;
    }

    public MinemevPostInfo getPost() {
        return post;
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
        System.out.println("[PostEntryWidget] Entry bounds - x: " + x + ", y: " + y + ", width: " + width + ", height: " + ENTRY_HEIGHT);
        System.out.println("[PostEntryWidget] Post title: " + (post != null ? post.getTitle() : "null"));

        if (mouseX >= x && mouseX < x + width &&
            mouseY >= y && mouseY < y + ENTRY_HEIGHT && button == 0) {
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

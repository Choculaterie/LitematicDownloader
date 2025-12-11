package com.choculaterie.gui.widget;

import com.choculaterie.models.ModMessage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;

import java.util.function.Consumer;

/**
 * Banner widget to display mod messages from the server
 */
public class ModMessageBanner implements Drawable, Element {
    private static final int BANNER_HEIGHT = 30;
    private static final int PADDING = 8;
    private static final int CLOSE_BUTTON_SIZE = 16;

    // Colors for different message types
    private static final int INFO_BG_COLOR = 0xE02196F3;      // Blue
    private static final int WARNING_BG_COLOR = 0xE0FF9800;   // Orange
    private static final int ERROR_BG_COLOR = 0xE0F44336;     // Red
    private static final int DEFAULT_BG_COLOR = 0xE03A3A3A;   // Grey
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int CLOSE_HOVER_COLOR = 0x40FFFFFF;

    private final MinecraftClient client;
    private int x;
    private int y;
    private int width;
    private ModMessage message;
    private Consumer<ModMessage> onDismiss;
    private boolean visible = false;

    public ModMessageBanner(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.client = MinecraftClient.getInstance();
    }

    public void setMessage(ModMessage message) {
        this.message = message;
        this.visible = message != null && message.hasMessage();
    }

    public void setOnDismiss(Consumer<ModMessage> onDismiss) {
        this.onDismiss = onDismiss;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public boolean isVisible() {
        return visible;
    }

    public int getHeight() {
        return visible ? BANNER_HEIGHT : 0;
    }

    public void hide() {
        this.visible = false;
    }

    /**
     * Check if the mouse is over the banner
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) {
            return false;
        }
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BANNER_HEIGHT;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible || message == null || !message.hasMessage()) {
            return;
        }

        // Get background color based on message type
        int bgColor = getBackgroundColor(message.getType());

        // Draw background
        context.fill(x, y, x + width, y + BANNER_HEIGHT, bgColor);

        // Draw bottom border
        context.fill(x, y + BANNER_HEIGHT - 1, x + width, y + BANNER_HEIGHT, 0xFF000000);

        // Draw message text (centered vertically, left aligned with padding)
        String text = message.getMessage();
        if (text != null) {
            // Truncate text if too long
            int maxTextWidth = width - PADDING * 2 - CLOSE_BUTTON_SIZE - PADDING;
            if (client.textRenderer.getWidth(text) > maxTextWidth) {
                while (client.textRenderer.getWidth(text + "...") > maxTextWidth && text.length() > 0) {
                    text = text.substring(0, text.length() - 1);
                }
                text = text + "...";
            }
            
            int textY = y + (BANNER_HEIGHT - 8) / 2;
            context.drawTextWithShadow(client.textRenderer, text, x + PADDING, textY, TEXT_COLOR);
        }

        // Draw close button (X) on the right
        int closeX = x + width - CLOSE_BUTTON_SIZE - PADDING;
        int closeY = y + (BANNER_HEIGHT - CLOSE_BUTTON_SIZE) / 2;
        
        boolean isHoveringClose = mouseX >= closeX && mouseX < closeX + CLOSE_BUTTON_SIZE
                && mouseY >= closeY && mouseY < closeY + CLOSE_BUTTON_SIZE;
        
        if (isHoveringClose) {
            context.fill(closeX, closeY, closeX + CLOSE_BUTTON_SIZE, closeY + CLOSE_BUTTON_SIZE, CLOSE_HOVER_COLOR);
        }

        // Draw X
        String closeText = "✕";
        int closeTextWidth = client.textRenderer.getWidth(closeText);
        context.drawTextWithShadow(client.textRenderer, closeText, 
                closeX + (CLOSE_BUTTON_SIZE - closeTextWidth) / 2, 
                closeY + (CLOSE_BUTTON_SIZE - 8) / 2, 
                TEXT_COLOR);
    }

    private int getBackgroundColor(String type) {
        if (type == null) {
            return DEFAULT_BG_COLOR;
        }
        return switch (type.toLowerCase()) {
            case "info" -> INFO_BG_COLOR;
            case "warning" -> WARNING_BG_COLOR;
            case "error" -> ERROR_BG_COLOR;
            default -> DEFAULT_BG_COLOR;
        };
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || button != 0) {
            return false;
        }

        // Check close button click
        int closeX = x + width - CLOSE_BUTTON_SIZE - PADDING;
        int closeY = y + (BANNER_HEIGHT - CLOSE_BUTTON_SIZE) / 2;

        if (mouseX >= closeX && mouseX < closeX + CLOSE_BUTTON_SIZE
                && mouseY >= closeY && mouseY < closeY + CLOSE_BUTTON_SIZE) {
            visible = false;
            if (onDismiss != null && message != null) {
                onDismiss.accept(message);
            }
            return true;
        }

        return false;
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return false;
    }
}


package com.choculaterie.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * A toast notification that slides in from the right and fades away after a delay
 */
public class Toast {
    public enum Type {
        SUCCESS(0xFF44FF44),
        ERROR(0xFFFF4444),
        INFO(0xFF4488FF),
        WARNING(0xFFFFAA44);

        final int color;

        Type(int color) {
            this.color = color;
        }
    }

    private static final int TOAST_WIDTH = 250;
    private static final int TOAST_HEIGHT = 40;
    private static final int TOAST_HEIGHT_WITH_BUTTON = 60;
    private static final int PADDING = 10;
    private static final long SLIDE_DURATION = 300; // milliseconds
    private static final long DISPLAY_DURATION = 3000; // milliseconds
    private static final long ERROR_DISPLAY_DURATION = 8000; // milliseconds for errors with copy button
    private static final long FADE_DURATION = 500; // milliseconds

    private final String message;
    private final Type type;
    private final long createdTime;
    private final int screenWidth;
    private int yPosition;
    private int targetYPosition;
    private long lastYPositionChange;

    private final boolean hasCopyButton;
    private final String copyText;
    private CustomButton copyButton;
    private CustomButton closeButton;
    private final MinecraftClient client;

    private boolean dismissed = false;
    private boolean hovered = false;
    private long pausedTime = 0; // Total time the toast has been paused (hovered)
    private long hoverStartTime = 0; // When the current hover started

    public Toast(String message, Type type, int screenWidth, int yPosition, MinecraftClient client) {
        this(message, type, screenWidth, yPosition, false, null, client);
    }

    public Toast(String message, Type type, int screenWidth, int yPosition, boolean hasCopyButton, String copyText, MinecraftClient client) {
        this.message = message;
        this.type = type;
        this.screenWidth = screenWidth;
        this.yPosition = yPosition;
        this.targetYPosition = yPosition;
        this.createdTime = System.currentTimeMillis();
        this.lastYPositionChange = createdTime;
        this.hasCopyButton = hasCopyButton;
        this.copyText = copyText != null ? copyText : message;
        this.client = client;

        // Create close button (X) in top right - always present
        closeButton = new CustomButton(0, 0, 16, 16, Text.of("×"), btn -> {});

        if (hasCopyButton) {
            // Create copy button positioned at bottom right of toast - make it 18px tall for better centering
            copyButton = new CustomButton(0, 0, 50, 18, Text.of("Copy"), btn -> {});
        }
    }

    /**
     * Set target Y position for smooth sliding when toasts are removed
     */
    public void setTargetYPosition(int targetY) {
        if (this.targetYPosition != targetY) {
            this.targetYPosition = targetY;
            this.lastYPositionChange = System.currentTimeMillis();
        }
    }

    /**
     * Render the toast notification
     * @param context Draw context
     * @param textRenderer MinecraftClient textRenderer for rendering text
     * @return true if the toast should be removed, false otherwise
     */
    public boolean render(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer) {
        long now = System.currentTimeMillis();

        // Calculate effective elapsed time (excluding time spent hovered)
        long totalPausedTime = pausedTime;
        if (hovered && hoverStartTime > 0) {
            // Currently hovering, add current hover duration to paused time
            totalPausedTime += now - hoverStartTime;
        }
        long elapsed = now - createdTime - totalPausedTime;

        // Use longer display time for errors with copy button
        long displayDuration = hasCopyButton ? ERROR_DISPLAY_DURATION : DISPLAY_DURATION;

        // Check if toast should be removed
        if (dismissed || elapsed > SLIDE_DURATION + displayDuration + FADE_DURATION) {
            return true; // Remove this toast
        }

        // Smooth Y position transition when toasts shift
        if (yPosition != targetYPosition) {
            long yTransitionElapsed = now - lastYPositionChange;
            float yTransitionProgress = Math.min(1.0f, yTransitionElapsed / 400.0f); // 400ms transition for smooth shift
            yTransitionProgress = (float)(1 - Math.pow(1 - yTransitionProgress, 3)); // Ease-out cubic
            yPosition = (int)(yPosition + (targetYPosition - yPosition) * yTransitionProgress);

            // Snap to target if very close to avoid jitter
            if (Math.abs(yPosition - targetYPosition) < 1) {
                yPosition = targetYPosition;
            }
        }

        // Calculate animation states
        float slideProgress = Math.min(1.0f, elapsed / (float) SLIDE_DURATION);
        float fadeProgress = 1.0f;

        if (elapsed > SLIDE_DURATION + displayDuration) {
            // Fading out
            long fadeElapsed = elapsed - SLIDE_DURATION - displayDuration;
            fadeProgress = 1.0f - (fadeElapsed / (float) FADE_DURATION);
        }

        // Ease-out cubic for smooth slide
        slideProgress = 1 - (float) Math.pow(1 - slideProgress, 3);

        // Calculate position
        int targetX = screenWidth - TOAST_WIDTH - PADDING;
        int startX = screenWidth + TOAST_WIDTH;
        int currentX = (int) (startX + (targetX - startX) * slideProgress);

        // Use taller height if has copy button
        int toastHeight = hasCopyButton ? TOAST_HEIGHT_WITH_BUTTON : TOAST_HEIGHT;

        // Calculate alpha
        int alpha = (int) (255 * fadeProgress);
        if (alpha <= 0) return true; // Remove if fully transparent

        // Draw toast background with alpha
        int bgColor = 0xFF2A2A2A;
        int bgColorWithAlpha = (alpha << 24) | (bgColor & 0x00FFFFFF);
        context.fill(currentX, yPosition, currentX + TOAST_WIDTH, yPosition + toastHeight, bgColorWithAlpha);

        // Draw colored left border
        int borderColor = type.color;
        int borderColorWithAlpha = (alpha << 24) | (borderColor & 0x00FFFFFF);
        context.fill(currentX, yPosition, currentX + 4, yPosition + toastHeight, borderColorWithAlpha);

        // Draw top border
        int topBorderColor = 0xFF444444;
        int topBorderColorWithAlpha = (alpha << 24) | (topBorderColor & 0x00FFFFFF);
        context.fill(currentX, yPosition, currentX + TOAST_WIDTH, yPosition + 1, topBorderColorWithAlpha);

        // Draw bottom border
        context.fill(currentX, yPosition + toastHeight - 1, currentX + TOAST_WIDTH, yPosition + toastHeight, topBorderColorWithAlpha);

        // Draw right border
        context.fill(currentX + TOAST_WIDTH - 1, yPosition, currentX + TOAST_WIDTH, yPosition + toastHeight, topBorderColorWithAlpha);

        // Draw icon based on type
        String icon = switch (type) {
            case SUCCESS -> "✓";
            case ERROR -> "✗";
            case WARNING -> "⚠";
            case INFO -> "ℹ";
        };

        int iconColor = type.color;
        int iconColorWithAlpha = (alpha << 24) | (iconColor & 0x00FFFFFF);
        context.drawText(
                textRenderer,
                icon,
                currentX + 12,
                yPosition + 8,
                iconColorWithAlpha,
                false
        );

        // Draw message text
        int textX = currentX + 28;
        int textY = yPosition + 8;
        int maxTextWidth = TOAST_WIDTH - 40;

        // Trim text if too long
        String displayText = message;
        int textWidth = textRenderer.getWidth(displayText);
        if (textWidth > maxTextWidth) {
            while (textWidth > maxTextWidth - 10 && displayText.length() > 3) {
                displayText = displayText.substring(0, displayText.length() - 1);
                textWidth = textRenderer.getWidth(displayText + "...");
            }
            displayText += "...";
        }

        int textColor = 0xFFFFFFFF;
        int textColorWithAlpha = (alpha << 24) | (textColor & 0x00FFFFFF);

        context.drawText(
                textRenderer,
                displayText,
                textX,
                textY,
                textColorWithAlpha,
                false
        );

        // Draw close button (X) in top right corner
        if (closeButton != null) {
            int closeButtonSize = 16;
            int closeButtonX = currentX + TOAST_WIDTH - closeButtonSize - 4;
            int closeButtonY = yPosition + 4;

            closeButton.setX(closeButtonX);
            closeButton.setY(closeButtonY);
            closeButton.setWidth(closeButtonSize);
            closeButton.setHeight(closeButtonSize);

            closeButton.render(context, (int)mouseX, (int)mouseY, 0);
        }

        // Draw copy button if present - position at bottom right with 18px height for better centering
        if (hasCopyButton && copyButton != null) {
            int buttonWidth = 50;
            int buttonHeight = 18;
            int buttonX = currentX + TOAST_WIDTH - buttonWidth - 8;
            int buttonY = yPosition + toastHeight - buttonHeight - 6;

            // Update button position
            copyButton.setX(buttonX);
            copyButton.setY(buttonY);
            copyButton.setWidth(buttonWidth);
            copyButton.setHeight(buttonHeight);

            // Render the button with alpha
            copyButton.render(context, (int)mouseX, (int)mouseY, 0);
        }

        return false; // Keep the toast
    }

    /**
     * Check if mouse is hovering over this toast
     */
    public boolean isHovering(double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        long elapsed = now - createdTime;
        long displayDuration = hasCopyButton ? ERROR_DISPLAY_DURATION : DISPLAY_DURATION;

        // Don't block if toast is dismissed or fading out
        if (dismissed || elapsed > SLIDE_DURATION + displayDuration) {
            return false;
        }

        // Calculate current X position
        float slideProgress = Math.min(1.0f, elapsed / (float) SLIDE_DURATION);
        slideProgress = 1 - (float) Math.pow(1 - slideProgress, 3);
        int targetX = screenWidth - TOAST_WIDTH - PADDING;
        int startX = screenWidth + TOAST_WIDTH;
        int currentX = (int) (startX + (targetX - startX) * slideProgress);

        int toastHeight = hasCopyButton ? TOAST_HEIGHT_WITH_BUTTON : TOAST_HEIGHT;

        return mouseX >= currentX && mouseX < currentX + TOAST_WIDTH &&
               mouseY >= yPosition && mouseY < yPosition + toastHeight;
    }

    /**
     * Check if close button was clicked
     */
    public boolean isCloseButtonClicked(double mouseX, double mouseY) {
        if (closeButton == null) return false;
        return mouseX >= closeButton.getX() && mouseX < closeButton.getX() + closeButton.getWidth() &&
               mouseY >= closeButton.getY() && mouseY < closeButton.getY() + closeButton.getHeight();
    }

    private static double mouseX = 0;
    private static double mouseY = 0;

    public static void updateMousePosition(double x, double y) {
        mouseX = x;
        mouseY = y;
    }

    public void dismiss() {
        this.dismissed = true;
    }

    /**
     * Set hover state - pauses the timer when true
     */
    public void setHovered(boolean hovered) {
        if (hovered && !this.hovered) {
            // Started hovering - record the time
            this.hoverStartTime = System.currentTimeMillis();
        } else if (!hovered && this.hovered) {
            // Stopped hovering - add the hover duration to paused time
            this.pausedTime += System.currentTimeMillis() - this.hoverStartTime;
        }
        this.hovered = hovered;
    }

    /**
     * Check if toast is currently hovered
     */
    public boolean isHovered() {
        return hovered;
    }

    public int getHeight() {
        return (hasCopyButton ? TOAST_HEIGHT_WITH_BUTTON : TOAST_HEIGHT) + 5; // Add spacing between toasts
    }

    public boolean isCopyButtonClicked(double mouseX, double mouseY) {
        if (!hasCopyButton || copyButton == null) return false;
        return mouseX >= copyButton.getX() && mouseX < copyButton.getX() + copyButton.getWidth() &&
               mouseY >= copyButton.getY() && mouseY < copyButton.getY() + copyButton.getHeight();
    }

    public String getCopyText() {
        return copyText;
    }
}


package com.choculaterie.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Custom styled text field for search input
 */
public class CustomTextField extends TextFieldWidget {
    private static final int FIELD_BG_COLOR = 0xFF2A2A2A;
    private static final int FIELD_BORDER_COLOR = 0xFF555555;
    private static final int FIELD_BORDER_FOCUSED_COLOR = 0xFF888888;
    private static final int CLEAR_BUTTON_SIZE = 12;
    private static final int CLEAR_BUTTON_COLOR = 0xFF888888;
    private static final int CLEAR_BUTTON_HOVER_COLOR = 0xFFAAAAAA;

    private final MinecraftClient client;
    private Runnable onEnterPressed;
    private boolean wasEnterDown = false;
    private boolean wasClearButtonMouseDown = false;
    private Text placeholderText;
    private Runnable onClearPressed;

    public CustomTextField(MinecraftClient client, int x, int y, int width, int height, Text text) {
        super(client.textRenderer, x, y, width, height, text);
        this.client = client;
        this.setMaxLength(256);
        this.setDrawsBackground(false);
        this.setFocusUnlocked(true); // Allow focus to be set at any time
    }

    public void setOnEnterPressed(Runnable callback) {
        this.onEnterPressed = callback;
    }

    public void setOnClearPressed(Runnable callback) {
        this.onClearPressed = callback;
    }

    @Override
    public void setPlaceholder(Text placeholder) {
        super.setPlaceholder(placeholder);
        this.placeholderText = placeholder;
    }

    private boolean isOverClearButton(int mouseX, int mouseY) {
        if (this.getText().isEmpty()) return false;
        int clearX = this.getX() + this.getWidth() - CLEAR_BUTTON_SIZE - 4;
        int clearY = this.getY() + (this.getHeight() - CLEAR_BUTTON_SIZE) / 2;
        return mouseX >= clearX && mouseX < clearX + CLEAR_BUTTON_SIZE &&
               mouseY >= clearY && mouseY < clearY + CLEAR_BUTTON_SIZE;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        long windowHandle = client.getWindow() != null ? client.getWindow().getHandle() : 0;

        // Handle clicking on the text field to focus it using GLFW
        if (windowHandle != 0) {
            boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            // Check if mouse is over the text field (but not the clear button)
            boolean isOverField = mouseX >= this.getX() && mouseX < this.getX() + this.getWidth() &&
                                  mouseY >= this.getY() && mouseY < this.getY() + this.getHeight();

            // If mouse just clicked and is over the field (not over clear button), focus it
            if (isMouseDown && !wasClearButtonMouseDown && isOverField && !isOverClearButton(mouseX, mouseY)) {
                if (!this.isFocused()) {
                    this.setFocused(true);
                }
            }

            // Handle clear button click separately
            if (!this.getText().isEmpty() && isMouseDown && !wasClearButtonMouseDown && isOverClearButton(mouseX, mouseY)) {
                this.setText("");
                if (onClearPressed != null) {
                    onClearPressed.run();
                }
                // Don't set focus when clearing
            }

            wasClearButtonMouseDown = isMouseDown;
        } else {
            wasClearButtonMouseDown = false;
        }

        // Check for Enter key press using GLFW directly
        if (windowHandle != 0) {
            boolean isEnterDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS ||
                                  GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;

            if (this.isFocused() && onEnterPressed != null && isEnterDown && !wasEnterDown) {
                wasEnterDown = true;
                // Just trigger search, don't unfocus here
                onEnterPressed.run();
            } else if (!isEnterDown) {
                wasEnterDown = false;
            }
        }

        // Draw custom background
        context.fill(this.getX(), this.getY(),
                    this.getX() + this.getWidth(), this.getY() + this.getHeight(),
                    FIELD_BG_COLOR);

        // Draw border (highlighted when focused)
        int borderColor = this.isFocused() ? FIELD_BORDER_FOCUSED_COLOR : FIELD_BORDER_COLOR;
        context.fill(this.getX(), this.getY(),
                    this.getX() + this.getWidth(), this.getY() + 1, borderColor);
        context.fill(this.getX(), this.getY() + this.getHeight() - 1,
                    this.getX() + this.getWidth(), this.getY() + this.getHeight(), borderColor);
        context.fill(this.getX(), this.getY(), this.getX() + 1,
                    this.getY() + this.getHeight(), borderColor);
        context.fill(this.getX() + this.getWidth() - 1, this.getY(),
                    this.getX() + this.getWidth(), this.getY() + this.getHeight(), borderColor);

        // Render text vertically centered
        int textY = this.getY() + (this.getHeight() - 8) / 2; // 8 is the font height
        int textX = this.getX() + 4; // Small padding from left

        // Calculate max text width (leave space for clear button if text exists)
        int maxTextWidth = this.getWidth() - 8 - (this.getText().isEmpty() ? 0 : CLEAR_BUTTON_SIZE + 4);

        String text = this.getText();
        if (text.isEmpty() && !this.isFocused()) {
            // Draw placeholder
            if (placeholderText != null) {
                context.drawTextWithShadow(client.textRenderer, placeholderText, textX, textY, 0xFF888888);
            }
        } else {
            // Draw the actual text with cursor handling
            int color = this.isFocused() ? 0xFFFFFFFF : 0xFFE0E0E0;

            // Handle cursor
            int cursorPos = this.getCursor();
            String beforeCursor = text.substring(0, Math.min(cursorPos, text.length()));

            // Draw text (with scissor to clip if too long)
            context.enableScissor(textX, this.getY(), textX + maxTextWidth, this.getY() + this.getHeight());
            context.drawTextWithShadow(client.textRenderer, text, textX, textY, color);
            context.disableScissor();

            // Draw cursor if focused
            if (this.isFocused() && this.isActive()) {
                int cursorX = textX + client.textRenderer.getWidth(beforeCursor);
                // Blinking cursor
                if ((System.currentTimeMillis() / 500) % 2 == 0) {
                    context.fill(cursorX, textY - 1, cursorX + 1, textY + 9, 0xFFFFFFFF);
                }
            }
        }

        // Draw clear button (X) if there's text
        if (!this.getText().isEmpty()) {
            int clearX = this.getX() + this.getWidth() - CLEAR_BUTTON_SIZE - 4;
            int clearY = this.getY() + (this.getHeight() - CLEAR_BUTTON_SIZE) / 2;
            boolean isHovered = isOverClearButton(mouseX, mouseY);
            int clearColor = isHovered ? CLEAR_BUTTON_HOVER_COLOR : CLEAR_BUTTON_COLOR;

            // Draw X
            int padding = 3;
            int x1 = clearX + padding;
            int y1 = clearY + padding;
            int x2 = clearX + CLEAR_BUTTON_SIZE - padding;

            // Better X drawing with diagonal lines
            for (int j = 0; j <= (x2 - x1); j++) {
                context.fill(x1 + j, y1 + j, x1 + j + 1, y1 + j + 2, clearColor);
                context.fill(x2 - j, y1 + j, x2 - j + 1, y1 + j + 2, clearColor);
            }
        }
    }
}

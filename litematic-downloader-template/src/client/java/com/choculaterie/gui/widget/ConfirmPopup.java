package com.choculaterie.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A popup dialog for confirmation with Yes/No buttons
 */
public class ConfirmPopup implements Drawable, Element {
    private static final int POPUP_WIDTH = 300;
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int LINE_HEIGHT = 12;

    private final Screen parent;
    private final String title;
    private final String message;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    private CustomButton confirmButton;
    private CustomButton cancelButton;

    private boolean wasEnterPressed = false;
    private boolean wasEscapePressed = false;

    private final int x;
    private final int y;
    private final int popupHeight;
    private final List<String> wrappedMessage;

    public ConfirmPopup(Screen parent, String title, String message, Runnable onConfirm, Runnable onCancel) {
        this.parent = parent;
        this.title = title;
        this.message = message;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        MinecraftClient client = MinecraftClient.getInstance();

        // Wrap message text
        this.wrappedMessage = wrapText(message, POPUP_WIDTH - PADDING * 2, client);

        // Calculate popup height based on wrapped text
        int messageHeight = wrappedMessage.size() * LINE_HEIGHT;
        this.popupHeight = PADDING + LINE_HEIGHT + PADDING + messageHeight + PADDING + BUTTON_HEIGHT + PADDING;

        this.x = (client.getWindow().getScaledWidth() - POPUP_WIDTH) / 2;
        this.y = (client.getWindow().getScaledHeight() - popupHeight) / 2;

        initWidgets();
    }

    private List<String> wrapText(String text, int maxWidth, MinecraftClient client) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            int width = client.textRenderer.getWidth(testLine);

            if (width <= maxWidth) {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines.isEmpty() ? List.of(text) : lines;
    }

    private void initWidgets() {
        // Buttons
        int buttonY = y + popupHeight - PADDING - BUTTON_HEIGHT;
        int buttonWidth = (POPUP_WIDTH - PADDING * 3) / 2;

        cancelButton = new CustomButton(
                x + PADDING,
                buttonY,
                buttonWidth,
                BUTTON_HEIGHT,
                net.minecraft.text.Text.literal("Cancel"),
                button -> onCancel.run()
        );

        confirmButton = new CustomButton(
                x + PADDING * 2 + buttonWidth,
                buttonY,
                buttonWidth,
                BUTTON_HEIGHT,
                net.minecraft.text.Text.literal("Delete"),
                button -> onConfirm.run()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        long windowHandle = client.getWindow() != null ? client.getWindow().getHandle() : 0;

        // Handle Enter/Escape through GLFW polling
        if (windowHandle != 0) {
            boolean enterPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS ||
                                  GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;
            boolean escapePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;

            if (enterPressed && !wasEnterPressed) {
                onConfirm.run();
            }
            if (escapePressed && !wasEscapePressed) {
                onCancel.run();
            }

            wasEnterPressed = enterPressed;
            wasEscapePressed = escapePressed;
        }

        // Draw overlay
        context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), 0x80000000);

        // Draw popup background
        context.fill(x, y, x + POPUP_WIDTH, y + popupHeight, 0xFF2A2A2A);

        // Draw border
        context.fill(x, y, x + POPUP_WIDTH, y + 1, 0xFF555555);
        context.fill(x, y + popupHeight - 1, x + POPUP_WIDTH, y + popupHeight, 0xFF555555);
        context.fill(x, y, x + 1, y + popupHeight, 0xFF555555);
        context.fill(x + POPUP_WIDTH - 1, y, x + POPUP_WIDTH, y + popupHeight, 0xFF555555);

        // Draw title
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                title,
                x + POPUP_WIDTH / 2,
                y + PADDING,
                0xFFFFFFFF
        );

        // Draw wrapped message
        int messageY = y + PADDING + LINE_HEIGHT + PADDING;
        for (String line : wrappedMessage) {
            context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    line,
                    x + POPUP_WIDTH / 2,
                    messageY,
                    0xFFCCCCCC
            );
            messageY += LINE_HEIGHT;
        }

        // Draw buttons
        if (cancelButton != null) {
            cancelButton.render(context, mouseX, mouseY, delta);
        }
        if (confirmButton != null) {
            confirmButton.render(context, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if clicking outside popup - close it
        if (mouseX < x || mouseX > x + POPUP_WIDTH || mouseY < y || mouseY > y + popupHeight) {
            onCancel.run();
            return true;
        }

        // Check cancel button
        if (cancelButton != null) {
            boolean isOverCancel = mouseX >= cancelButton.getX() &&
                                  mouseX < cancelButton.getX() + cancelButton.getWidth() &&
                                  mouseY >= cancelButton.getY() &&
                                  mouseY < cancelButton.getY() + cancelButton.getHeight();
            if (isOverCancel) {
                onCancel.run();
                return true;
            }
        }

        // Check confirm button
        if (confirmButton != null) {
            boolean isOverConfirm = mouseX >= confirmButton.getX() &&
                                   mouseX < confirmButton.getX() + confirmButton.getWidth() &&
                                   mouseY >= confirmButton.getY() &&
                                   mouseY < confirmButton.getY() + confirmButton.getHeight();
            if (isOverConfirm) {
                onConfirm.run();
                return true;
            }
        }

        return true; // Consume all clicks within popup
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }
}


package com.choculaterie.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A popup dialog for confirmation with Yes/No buttons
 */
public class ConfirmPopup implements Drawable, Element {
    private static final int POPUP_WIDTH = 400; // Increased from 300 for tree view
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int LINE_HEIGHT = 12;
    private static final int MAX_MESSAGE_HEIGHT = 300; // Maximum height for message area

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
    private final int actualMessageHeight; // Full height of all message lines
    private final int visibleMessageHeight; // Height of visible area (capped)
    private ScrollBar scrollBar;
    private double scrollOffset = 0;
    private final String confirmButtonText;

    public ConfirmPopup(Screen parent, String title, String message, Runnable onConfirm, Runnable onCancel) {
        this(parent, title, message, onConfirm, onCancel, "Delete");
    }

    public ConfirmPopup(Screen parent, String title, String message, Runnable onConfirm, Runnable onCancel, String confirmButtonText) {
        this.parent = parent;
        this.title = title;
        this.message = message;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.confirmButtonText = confirmButtonText;

        MinecraftClient client = MinecraftClient.getInstance();

        // Wrap message text
        this.wrappedMessage = wrapText(message, POPUP_WIDTH - PADDING * 2, client);

        // Calculate screen dimensions
        int screenHeight = client.getWindow().getScaledHeight();
        int screenWidth = client.getWindow().getScaledWidth();

        // Calculate maximum available height for message (leave margins top and bottom)
        int verticalMargin = 40; // Min space from top/bottom of screen
        int popupChrome = PADDING + LINE_HEIGHT + PADDING + PADDING + BUTTON_HEIGHT + PADDING; // Title + paddings + buttons
        int maxAvailableMessageHeight = screenHeight - (verticalMargin * 2) - popupChrome;

        // Ensure we don't exceed MAX_MESSAGE_HEIGHT or available screen space
        int effectiveMaxHeight = Math.min(MAX_MESSAGE_HEIGHT, maxAvailableMessageHeight);

        // Calculate actual message height and visible height
        this.actualMessageHeight = wrappedMessage.size() * LINE_HEIGHT;
        this.visibleMessageHeight = Math.min(actualMessageHeight, effectiveMaxHeight);
        this.popupHeight = PADDING + LINE_HEIGHT + PADDING + visibleMessageHeight + PADDING + BUTTON_HEIGHT + PADDING;

        // Center on screen
        this.x = (screenWidth - POPUP_WIDTH) / 2;
        this.y = (screenHeight - popupHeight) / 2;

        // Initialize scrollbar if content exceeds visible height
        if (actualMessageHeight > visibleMessageHeight) {
            int messageAreaY = y + PADDING + LINE_HEIGHT + PADDING;
            int scrollBarX = x + POPUP_WIDTH - PADDING - 8; // 8 = scrollbar width
            this.scrollBar = new ScrollBar(scrollBarX, messageAreaY, visibleMessageHeight);
            this.scrollBar.setScrollData(actualMessageHeight, visibleMessageHeight);
        }

        initWidgets();
    }

    private List<String> wrapText(String text, int maxWidth, MinecraftClient client) {
        List<String> lines = new ArrayList<>();

        // Split by newlines first to preserve explicit line breaks
        String[] paragraphs = text.split("\n");

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add(""); // Preserve empty lines
                continue;
            }

            String[] words = paragraph.split(" ");
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
                Text.of("Cancel"),
                button -> onCancel.run()
        );

        confirmButton = new CustomButton(
                x + POPUP_WIDTH - PADDING - buttonWidth,
                buttonY,
                buttonWidth,
                BUTTON_HEIGHT,
                Text.of(confirmButtonText),
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

        // Draw wrapped message with scissor for scrolling
        int messageAreaY = y + PADDING + LINE_HEIGHT + PADDING;
        int messageAreaHeight = visibleMessageHeight;

        // Enable scissor to clip content
        context.enableScissor(x + PADDING, messageAreaY, x + POPUP_WIDTH - PADDING, messageAreaY + messageAreaHeight);

        int messageY = messageAreaY - (int)scrollOffset;
        for (String line : wrappedMessage) {
            // Only render lines that are visible
            if (messageY + LINE_HEIGHT >= messageAreaY && messageY < messageAreaY + messageAreaHeight) {
                context.drawTextWithShadow(
                        client.textRenderer,
                        line,
                        x + PADDING,
                        messageY,
                        0xFFCCCCCC
                );
            }
            messageY += LINE_HEIGHT;
        }

        context.disableScissor();

        // Render scrollbar if needed
        if (scrollBar != null && client.getWindow() != null) {
            scrollBar.setScrollPercentage(scrollOffset / Math.max(1, actualMessageHeight - visibleMessageHeight));
            boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta, client.getWindow().getHandle());

            if (scrollChanged || scrollBar.isDragging()) {
                double maxScroll = actualMessageHeight - visibleMessageHeight;
                scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
            }
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

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Only scroll if mouse is over the message area and scrollbar exists
        if (scrollBar != null) {
            int messageAreaY = y + PADDING + LINE_HEIGHT + PADDING;
            if (mouseX >= x && mouseX < x + POPUP_WIDTH &&
                mouseY >= messageAreaY && mouseY < messageAreaY + visibleMessageHeight) {

                double maxScroll = actualMessageHeight - visibleMessageHeight;
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * LINE_HEIGHT));

                return true;
            }
        }
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }
}


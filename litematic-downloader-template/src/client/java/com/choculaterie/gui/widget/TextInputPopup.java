package com.choculaterie.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A popup dialog for text input with validation
 */
public class TextInputPopup implements Drawable, Element {
    private static final int POPUP_WIDTH = 300;
    private static final int POPUP_HEIGHT = 120;
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final String title;
    private final Consumer<String> onConfirm;
    private final Runnable onCancel;
    private final String confirmButtonText;

    private CustomTextField textField;
    private CustomButton confirmButton;
    private CustomButton cancelButton;
    private String errorMessage = "";

    private boolean wasEscapePressed = false;

    private final int x;
    private final int y;

    public TextInputPopup(Screen parent, String title, Consumer<String> onConfirm, Runnable onCancel) {
        this(parent, title, "Create", onConfirm, onCancel);
    }

    public TextInputPopup(Screen parent, String title, String confirmButtonText, Consumer<String> onConfirm, Runnable onCancel) {
        this.parent = parent;
        this.title = title;
        this.confirmButtonText = confirmButtonText;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        MinecraftClient client = MinecraftClient.getInstance();
        this.x = (client.getWindow().getScaledWidth() - POPUP_WIDTH) / 2;
        this.y = (client.getWindow().getScaledHeight() - POPUP_HEIGHT) / 2;

        initWidgets();
    }

    private void initWidgets() {
        MinecraftClient client = MinecraftClient.getInstance();

        // Text field using CustomTextField which handles all input through GLFW
        int fieldY = y + PADDING * 3;
        textField = new CustomTextField(
                client,
                x + PADDING,
                fieldY,
                POPUP_WIDTH - PADDING * 2,
                20,
                Text.literal("")
        );
        textField.setPlaceholder(Text.literal("Enter name..."));
        textField.setFocused(true);
        textField.setOnChanged(() -> errorMessage = ""); // Clear error when typing
        textField.setOnEnterPressed(this::handleConfirm);

        // Buttons
        int buttonY = y + POPUP_HEIGHT - PADDING - BUTTON_HEIGHT;
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
                net.minecraft.text.Text.literal(confirmButtonText),
                button -> handleConfirm()
        );
    }

    public void setErrorMessage(String error) {
        this.errorMessage = error;
    }

    public void setText(String text) {
        if (textField != null) {
            textField.setText(text);
        }
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

    private void handleConfirm() {
        String text = textField.getText().trim();

        // Validate input
        if (text.isEmpty()) {
            setErrorMessage("Folder name cannot be empty");
            return;
        }

        // Check for invalid characters
        if (text.contains("/") || text.contains("\\") || text.contains(":") ||
            text.contains("*") || text.contains("?") || text.contains("\"") ||
            text.contains("<") || text.contains(">") || text.contains("|")) {
            setErrorMessage("Invalid characters in folder name");
            return;
        }

        // Check for reserved names (Windows)
        String upperText = text.toUpperCase();
        if (upperText.equals("CON") || upperText.equals("PRN") || upperText.equals("AUX") ||
            upperText.equals("NUL") || upperText.matches("COM[0-9]") || upperText.matches("LPT[0-9]")) {
            setErrorMessage("Reserved folder name");
            return;
        }

        onConfirm.accept(text);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        long windowHandle = client.getWindow() != null ? client.getWindow().getHandle() : 0;

        // Handle Escape through GLFW polling (Enter is handled by TextField callback)
        if (windowHandle != 0) {
            boolean escapePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;

            if (escapePressed && !wasEscapePressed) {
                onCancel.run();
            }

            wasEscapePressed = escapePressed;
        }

        // Draw overlay
        context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), 0x80000000);

        // Draw popup background
        context.fill(x, y, x + POPUP_WIDTH, y + POPUP_HEIGHT, 0xFF2A2A2A);

        // Draw border
        context.fill(x, y, x + POPUP_WIDTH, y + 1, 0xFF555555); // Top
        context.fill(x, y + POPUP_HEIGHT - 1, x + POPUP_WIDTH, y + POPUP_HEIGHT, 0xFF555555); // Bottom
        context.fill(x, y, x + 1, y + POPUP_HEIGHT, 0xFF555555); // Left
        context.fill(x + POPUP_WIDTH - 1, y, x + POPUP_WIDTH, y + POPUP_HEIGHT, 0xFF555555); // Right

        // Draw title
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                title,
                x + POPUP_WIDTH / 2,
                y + PADDING,
                0xFFFFFFFF
        );

        // Draw text field (it handles its own keyboard input through GLFW)
        if (textField != null) {
            // Ensure text field stays focused to receive character input
            if (!textField.isFocused()) {
                textField.setFocused(true);
            }
            textField.render(context, mouseX, mouseY, delta);
        }

        // Draw error message with wrapping
        if (!errorMessage.isEmpty()) {
            List<String> wrappedError = wrapText(errorMessage, POPUP_WIDTH - PADDING * 2, client);
            int errorY = y + PADDING * 3 + 25;
            for (String line : wrappedError) {
                context.drawCenteredTextWithShadow(
                        client.textRenderer,
                        line,
                        x + POPUP_WIDTH / 2,
                        errorY,
                        0xFFFF5555
                );
                errorY += 10;
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
        if (mouseX < x || mouseX > x + POPUP_WIDTH || mouseY < y || mouseY > y + POPUP_HEIGHT) {
            onCancel.run();
            return true;
        }

        // Check text field - set focus if clicked
        if (textField != null) {
            boolean isOverTextField = mouseX >= textField.getX() &&
                                     mouseX < textField.getX() + textField.getWidth() &&
                                     mouseY >= textField.getY() &&
                                     mouseY < textField.getY() + textField.getHeight();
            if (isOverTextField) {
                textField.setFocused(true);
                return true;
            }
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
                handleConfirm();
                return true;
            }
        }

        return true; // Consume all clicks within popup
    }


    @Override
    public void setFocused(boolean focused) {
        if (textField != null) {
            textField.setFocused(focused);
        }
    }

    @Override
    public boolean isFocused() {
        return textField != null && textField.isFocused();
    }
}


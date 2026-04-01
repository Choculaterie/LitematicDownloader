package com.choculaterie.gui.widget;

import com.choculaterie.gui.theme.UITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TextInputPopup implements Renderable, GuiEventListener {
    private static final int POPUP_WIDTH = 300;
    private static final int POPUP_HEIGHT = 120;
    private static final String[] INVALID_CHARS = {"/", "\\", ":", "*", "?", "\"", "<", ">", "|"};
    private static final String[] RESERVED_NAMES = {"CON", "PRN", "AUX", "NUL"};

    private final String title;
    private final Consumer<String> onConfirm;
    private final Runnable onCancel;
    private final String confirmButtonText;
    private final int x;
    private final int y;

    private CustomTextField textField;
    private CustomButton confirmButton;
    private CustomButton cancelButton;
    private String errorMessage = "";
    private boolean wasEscapePressed;

    public TextInputPopup(Screen parent, String title, Consumer<String> onConfirm, Runnable onCancel) {
        this(title, "Create", onConfirm, onCancel);
    }

    public TextInputPopup(Screen parent, String title, String confirmButtonText, Consumer<String> onConfirm, Runnable onCancel) {
        this(title, confirmButtonText, onConfirm, onCancel);
    }

    public TextInputPopup(String title, String confirmButtonText, Consumer<String> onConfirm, Runnable onCancel) {
        this.title = title;
        this.confirmButtonText = confirmButtonText;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        Minecraft client = Minecraft.getInstance();
        this.x = (client.getWindow().getGuiScaledWidth() - POPUP_WIDTH) / 2;
        this.y = (client.getWindow().getGuiScaledHeight() - POPUP_HEIGHT) / 2;

        initWidgets();
    }

    private void initWidgets() {
        Minecraft client = Minecraft.getInstance();
        int fieldY = y + UITheme.Dimensions.PADDING * 3;

        textField = new CustomTextField(
                client,
                x + UITheme.Dimensions.PADDING,
                fieldY,
                POPUP_WIDTH - UITheme.Dimensions.PADDING * 2,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Component.literal("")
        );
        textField.setPlaceholder(Component.literal("Enter name..."));
        textField.setFocused(true);
        textField.setOnChanged(() -> errorMessage = "");
        textField.setOnEnterPressed(this::handleConfirm);

        int buttonY = y + POPUP_HEIGHT - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT;
        int buttonWidth = (POPUP_WIDTH - UITheme.Dimensions.PADDING * 3) / 2;

        cancelButton = new CustomButton(
                x + UITheme.Dimensions.PADDING,
                buttonY,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Component.literal("Cancel"),
                button -> onCancel.run()
        );

        confirmButton = new CustomButton(
                x + UITheme.Dimensions.PADDING * 2 + buttonWidth,
                buttonY,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Component.literal(confirmButtonText),
                button -> handleConfirm()
        );
    }

    public void setErrorMessage(String error) {
        this.errorMessage = error;
    }

    public void setText(String text) {
        if (textField != null) {
            textField.setValue(text);
        }
    }

    private List<String> wrapText(String text, int maxWidth, Minecraft client) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            int width = client.font.width(testLine);

            if (width <= maxWidth) {
                if (!currentLine.isEmpty()) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines.isEmpty() ? List.of(text) : lines;
    }

    private void handleConfirm() {
        String text = textField.getValue().trim();

        if (text.isEmpty()) {
            setErrorMessage("Folder name cannot be empty");
            return;
        }

        if (containsInvalidChars(text)) {
            setErrorMessage("Invalid characters in folder name");
            return;
        }

        if (isReservedName(text)) {
            setErrorMessage("Reserved folder name");
            return;
        }

        onConfirm.accept(text);
    }

    private boolean containsInvalidChars(String text) {
        for (String invalidChar : INVALID_CHARS) {
            if (text.contains(invalidChar)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReservedName(String text) {
        String upperText = text.toUpperCase();

        for (String reserved : RESERVED_NAMES) {
            if (upperText.equals(reserved)) {
                return true;
            }
        }

        return upperText.matches("COM[0-9]") || upperText.matches("LPT[0-9]");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        handleEscapeKey();

        Minecraft client = Minecraft.getInstance();

        drawOverlay(context, client);
        drawPopupBackground(context);
        drawTitle(context, client);

        if (textField != null) {
            textField.setFocused(true);
            textField.extractRenderState(context, mouseX, mouseY, delta);
        }

        if (!errorMessage.isEmpty()) {
            drawErrorMessage(context, client);
        }

        if (cancelButton != null) {
            cancelButton.extractRenderState(context, mouseX, mouseY, delta);
        }

        if (confirmButton != null) {
            confirmButton.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    private void handleEscapeKey() {
        Minecraft client = Minecraft.getInstance();
        long windowHandle = GLFW.glfwGetCurrentContext();

        if (windowHandle != 0) {
            boolean escapePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;

            if (escapePressed && !wasEscapePressed) {
                onCancel.run();
            }

            wasEscapePressed = escapePressed;
        }
    }

    private void drawOverlay(GuiGraphicsExtractor context, Minecraft client) {
        context.fill(0, 0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), UITheme.Colors.OVERLAY_BG);
    }

    private void drawPopupBackground(GuiGraphicsExtractor context) {
        context.fill(x, y, x + POPUP_WIDTH, y + POPUP_HEIGHT, UITheme.Colors.BUTTON_BG_DISABLED);
        context.fill(x, y, x + POPUP_WIDTH, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        context.fill(x, y + POPUP_HEIGHT - UITheme.Dimensions.BORDER_WIDTH, x + POPUP_WIDTH, y + POPUP_HEIGHT, UITheme.Colors.BUTTON_BORDER);
        context.fill(x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + POPUP_HEIGHT, UITheme.Colors.BUTTON_BORDER);
        context.fill(x + POPUP_WIDTH - UITheme.Dimensions.BORDER_WIDTH, y, x + POPUP_WIDTH, y + POPUP_HEIGHT, UITheme.Colors.BUTTON_BORDER);
    }

    private void drawTitle(GuiGraphicsExtractor context, Minecraft client) {
        context.centeredText(
                client.font,
                title,
                x + POPUP_WIDTH / 2,
                y + UITheme.Dimensions.PADDING,
                UITheme.Colors.TEXT_PRIMARY
        );
    }

    private void drawErrorMessage(GuiGraphicsExtractor context, Minecraft client) {
        List<String> wrappedError = wrapText(errorMessage, POPUP_WIDTH - UITheme.Dimensions.PADDING * 2, client);
        int errorY = y + UITheme.Dimensions.PADDING * 3 + 25;

        for (String line : wrappedError) {
            context.centeredText(
                    client.font,
                    line,
                    x + POPUP_WIDTH / 2,
                    errorY,
                    UITheme.Colors.ERROR_TEXT
            );
            errorY += UITheme.Typography.LINE_HEIGHT - 2;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOverPopup(mouseX, mouseY)) {
            onCancel.run();
            return true;
        }

        if (isMouseOverWidget(textField, mouseX, mouseY)) {
            textField.setFocused(true);
            return true;
        }

        if (isMouseOverWidget(cancelButton, mouseX, mouseY)) {
            onCancel.run();
            return true;
        }

        if (isMouseOverWidget(confirmButton, mouseX, mouseY)) {
            handleConfirm();
            return true;
        }

        return true;
    }

    private boolean isMouseOverPopup(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + POPUP_WIDTH && mouseY >= y && mouseY <= y + POPUP_HEIGHT;
    }

    private boolean isMouseOverWidget(AbstractWidget widget, double mouseX, double mouseY) {
        if (widget == null) return false;
        return mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth() &&
               mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight();
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

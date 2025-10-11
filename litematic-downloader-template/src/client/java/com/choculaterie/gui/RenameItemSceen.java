package com.choculaterie.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.function.Consumer;

public class RenameItemSceen extends Screen {
    private final Screen parent;
    private final File fileToRename;
    private final Consumer<Boolean> callback;

    private TextFieldWidget nameField;
    private String originalFileName;
    private String errorMessage = null;
    private long errorDisplayTime = 0;

    public RenameItemSceen(Screen parent, File fileToRename, Consumer<Boolean> callback) {
        super(Text.literal("Rename Item"));
        this.parent = parent;
        this.fileToRename = fileToRename;
        this.callback = callback;
        this.originalFileName = fileToRename.getName();
    }

    @Override
    protected void init() {
        int buttonWidth = 100;
        int spacing = 10;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), button -> {
            renameFile();
        }).dimensions(this.width / 2 - buttonWidth - spacing / 2, this.height / 2 + 40, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            MinecraftClient.getInstance().setScreen(parent);
            callback.accept(false);
        }).dimensions(this.width / 2 + spacing / 2, this.height / 2 + 40, buttonWidth, 20).build());

        int fieldWidth = 300;
        nameField = new TextFieldWidget(
                this.textRenderer,
                (this.width - fieldWidth) / 2,
                this.height / 2 - 10,
                fieldWidth,
                20,
                Text.literal("")
        );

        nameField.setText(originalFileName);
        nameField.setMaxLength(255);

        if (fileToRename.isDirectory() || !originalFileName.contains(".")) {
            nameField.setSelectionStart(0);
            nameField.setSelectionEnd(originalFileName.length());
        } else {
            nameField.setSelectionStart(0);
            nameField.setSelectionEnd(originalFileName.lastIndexOf('.'));
        }

        nameField.setText(fileToRename.getName());
        nameField.setFocused(true);
        this.addDrawableChild(nameField);
        setFocused(nameField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Enter a new name:"),
                this.width / 2, this.height / 2 - 30, 0xFFCCCCCC);

        String typeInfo = fileToRename.isDirectory() ? "Folder" : "File";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(typeInfo),
                this.width / 2, this.height / 2 - 45, 0xFFAAAAAA);

        if (errorMessage != null && System.currentTimeMillis() - errorDisplayTime < 3000) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(errorMessage),
                    this.width / 2,
                    this.height / 2 + 15,
                    0xFFFF5555
            );
        }
    }

    // New input API overrides (1.21+)

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Your existing logic using click.x(), click.y(), click.button()
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        // Your existing logic
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        // Your existing logic using click.button()
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // Confirm rename on Enter and KP\_Enter
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            renameFile();
            return true;
        }
        // Defer Escape, Tab/Arrow navigation, and focused element handling to parent
        return super.keyPressed(input);
    }


    @Override
    public boolean keyReleased(KeyInput input) {
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return super.charTyped(input);
    }

    private void renameFile() {
        String newName = nameField.getText().trim();

        if (newName.isEmpty()) {
            showError("Name cannot be empty");
            return;
        }

        if (newName.contains("/") || newName.contains("\\") || newName.contains(":") ||
                newName.contains("*") || newName.contains("?") || newName.contains("\"") ||
                newName.contains("<") || newName.contains(">") || newName.contains("|")) {
            showError("Invalid characters in name");
            return;
        }

        if (newName.equals(originalFileName)) {
            MinecraftClient.getInstance().setScreen(parent);
            callback.accept(false);
            return;
        }

        File newFile = new File(fileToRename.getParentFile(), newName);
        if (newFile.exists()) {
            showError("A file with this name already exists");
            return;
        }

        boolean success = fileToRename.renameTo(newFile);
        if (success) {
            MinecraftClient.getInstance().setScreen(parent);
            callback.accept(true);
        } else {
            showError("Failed to rename file");
        }
    }

    private void showError(String message) {
        errorMessage = message;
        errorDisplayTime = System.currentTimeMillis();
    }
}

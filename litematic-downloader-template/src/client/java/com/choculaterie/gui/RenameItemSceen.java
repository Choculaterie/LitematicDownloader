package com.choculaterie.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

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
        // Add confirm and cancel buttons
        int buttonWidth = 100;
        int spacing = 10;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), button -> {
            renameFile();
        }).dimensions(this.width / 2 - buttonWidth - spacing/2, this.height / 2 + 40, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            MinecraftClient.getInstance().setScreen(parent);
            callback.accept(false);
        }).dimensions(this.width / 2 + spacing/2, this.height / 2 + 40, buttonWidth, 20).build());

        // Create text field for the new name
        int fieldWidth = 300;
        nameField = new TextFieldWidget(
                this.textRenderer,
                (this.width - fieldWidth) / 2,
                this.height / 2 - 10,
                fieldWidth,
                20,
                Text.literal("")
        );

        // Always put the complete original filename in the text field
        nameField.setText(originalFileName);
        nameField.setMaxLength(255);

        // Selection behavior: for files with extensions, select only the name part
        // This doesn't change the text content, only what part is selected
        if (fileToRename.isDirectory() || !originalFileName.contains(".")) {
            // For directories or files without extensions, select all text
            nameField.setSelectionStart(0);
            nameField.setSelectionEnd(originalFileName.length());
        } else {
            // For files with extensions, select only the name part before extension
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
        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // Draw instructions - simplified to avoid duplicating the filename
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Enter a new name:"),
                this.width / 2, this.height / 2 - 30, 0xFFCCCCCC);

        // Draw file type info
        String typeInfo = fileToRename.isDirectory() ? "Folder" : "File";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(typeInfo),
                this.width / 2, this.height / 2 - 45, 0xFFAAAAAA);

        // Draw error message if present
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter key
            renameFile();
            return true;
        }
        if (keyCode == 256) { // Escape key
            MinecraftClient.getInstance().setScreen(parent);
            callback.accept(false);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renameFile() {
        String newName = nameField.getText().trim();

        // Validate new name
        if (newName.isEmpty()) {
            showError("Name cannot be empty");
            return;
        }

        // Check for invalid characters
        if (newName.contains("/") || newName.contains("\\") || newName.contains(":") ||
                newName.contains("*") || newName.contains("?") || newName.contains("\"") ||
                newName.contains("<") || newName.contains(">") || newName.contains("|")) {
            showError("Invalid characters in name");
            return;
        }

        // If name didn't change, just return to parent
        if (newName.equals(originalFileName)) {
            MinecraftClient.getInstance().setScreen(parent);
            callback.accept(false);
            return;
        }

        // Attempt rename
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
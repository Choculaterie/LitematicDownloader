package com.choculaterie.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.util.function.Consumer;

public class ConfirmationCreateFolderScreen extends Screen {
    private final Screen parentScreen;
    private final File currentDirectory;
    private TextFieldWidget nameField;
    private final Consumer<Boolean> callback;
    private String errorMessage = null;

    public ConfirmationCreateFolderScreen(Screen parentScreen, File currentDirectory, Consumer<Boolean> callback) {
        super(Text.literal("Create New Folder"));
        this.parentScreen = parentScreen;
        this.currentDirectory = currentDirectory;
        this.callback = callback;
    }

    @Override
    protected void init() {
        super.init();

        // Add text field for folder name
        this.nameField = new TextFieldWidget(
                this.textRenderer,
                this.width / 2 - 100,
                this.height / 2 - 10,
                200,
                20,
                Text.literal("Folder Name")
        );
        nameField.setMaxLength(64);
        this.setInitialFocus(nameField);
        this.addDrawableChild(nameField);

        // Add buttons
        int buttonWidth = 100;
        int spacing = 10;
        int yPosition = this.height / 2 + 20;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Create"),
                button -> createFolder()
        ).dimensions(
                this.width / 2 - buttonWidth - spacing / 2,
                yPosition,
                buttonWidth,
                20
        ).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel"),
                button -> {
                    callback.accept(false);
                    MinecraftClient.getInstance().setScreen(parentScreen);
                }
        ).dimensions(
                this.width / 2 + spacing / 2,
                yPosition,
                buttonWidth,
                20
        ).build());
    }

    private void createFolder() {
        String folderName = nameField.getText().trim();
        if (folderName.isEmpty()) {
            errorMessage = "Folder name cannot be empty";
            return;
        }

        // Check for invalid characters in folder name
        if (folderName.contains("/") || folderName.contains("\\") || folderName.contains(":") ||
                folderName.contains("*") || folderName.contains("?") || folderName.contains("\"") ||
                folderName.contains("<") || folderName.contains(">") || folderName.contains("|")) {
            errorMessage = "Invalid characters in folder name";
            return;
        }

        File newFolder = new File(currentDirectory, folderName);
        if (newFolder.exists()) {
            errorMessage = "A file or folder with this name already exists";
            return;
        }

        boolean success = newFolder.mkdir();
        callback.accept(success);
        MinecraftClient.getInstance().setScreen(parentScreen);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);

        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);

        // Draw prompt
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Enter name for new folder:"), this.width / 2, this.height / 2 - 30, 0xCCCCCC);

        // Draw error message if any
        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(errorMessage), this.width / 2, this.height / 2 + 50, 0xFF5555);
        }


    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter key
            createFolder();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
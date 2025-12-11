package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.gui.widget.CustomButton;
import com.choculaterie.gui.widget.CustomTextField;
import com.choculaterie.gui.widget.ToastManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class SettingsPage extends Screen {
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int LABEL_HEIGHT = 20;
    private static final int TEXT_FIELD_HEIGHT = 20;

    private final Screen parentScreen;
    private CustomButton backButton;
    private CustomTextField downloadPathField;
    private CustomButton saveButton;
    private CustomButton browseButton;
    private ToastManager toastManager;

    public SettingsPage(Screen parentScreen) {
        super(Text.literal("Settings"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        // Initialize toast manager
        if (this.client != null) {
            toastManager = new ToastManager(this.client);
        }

        // Back button (top left)
        backButton = new CustomButton(
                PADDING,
                PADDING,
                BUTTON_HEIGHT,
                BUTTON_HEIGHT,
                Text.literal("←"),
                button -> goBack()
        );
        this.addDrawableChild(backButton);

        // Download path section
        int contentY = PADDING * 4 + BUTTON_HEIGHT;
        int fieldWidth = this.width - PADDING * 4 - 100; // Leave space for browse button

        // Download path text field
        if (this.client != null) {
            downloadPathField = new CustomTextField(
                    this.client,
                    PADDING * 2,
                    contentY + LABEL_HEIGHT,
                    fieldWidth,
                    TEXT_FIELD_HEIGHT,
                    Text.literal("Download Path")
            );
            downloadPathField.setText(DownloadSettings.getInstance().getDownloadPath());
            downloadPathField.setPlaceholder(Text.literal("Enter download path..."));
            this.addDrawableChild(downloadPathField);
        }

        // Browse button (next to text field)
        browseButton = new CustomButton(
                PADDING * 2 + fieldWidth + PADDING,
                contentY + LABEL_HEIGHT,
                90,
                TEXT_FIELD_HEIGHT,
                Text.literal("Browse..."),
                button -> openFileDialog()
        );
        this.addDrawableChild(browseButton);

        // Save button
        saveButton = new CustomButton(
                this.width / 2 - 40,
                this.height - PADDING - BUTTON_HEIGHT - PADDING,
                80,
                BUTTON_HEIGHT,
                Text.literal("Save"),
                button -> saveSettings()
        );
        this.addDrawableChild(saveButton);
    }

    private void goBack() {
        if (this.client != null) {
            this.client.setScreen(parentScreen);
        }
    }

    private void saveSettings() {
        if (downloadPathField != null) {
            String path = downloadPathField.getText().trim();
            if (!path.isEmpty()) {
                DownloadSettings.getInstance().setDownloadPath(path);
                System.out.println("Download path saved: " + path);
                if (toastManager != null) {
                    toastManager.showSuccess("Settings saved!");
                }
            } else {
                if (toastManager != null) {
                    toastManager.showError("Path cannot be empty");
                }
            }
        }
    }

    private void openFileDialog() {
        // Open custom directory picker screen
        if (this.client != null) {
            String startPath = DownloadSettings.getInstance().getAbsoluteDownloadPath();
            DirectoryPickerScreen picker = new DirectoryPickerScreen(
                    this,
                    startPath,
                    selectedPath -> {
                        // Update the text field with selected path
                        if (downloadPathField != null) {
                            downloadPathField.setText(selectedPath);
                        }
                        // Auto-save the selected path
                        if (!selectedPath.isEmpty()) {
                            DownloadSettings.getInstance().setDownloadPath(selectedPath);
                            System.out.println("Download path saved from directory picker: " + selectedPath);
                            if (toastManager != null) {
                                toastManager.showSuccess("Download path updated!");
                            }
                        }
                    }
            );
            this.client.setScreen(picker);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fill the entire screen with dark grey background
        context.fill(0, 0, this.width, this.height, 0xFF202020);

        super.render(context, mouseX, mouseY, delta);

        // Draw title
        String title = "Settings";
        int titleWidth = this.textRenderer.getWidth(title);
        context.drawTextWithShadow(
                this.textRenderer,
                title,
                (this.width - titleWidth) / 2,
                PADDING * 2 + BUTTON_HEIGHT,
                0xFFFFFFFF
        );

        // Draw download path label
        int contentY = PADDING * 4 + BUTTON_HEIGHT;
        context.drawTextWithShadow(
                this.textRenderer,
                "Download Path (relative to game folder):",
                PADDING * 2,
                contentY,
                0xFFFFFFFF
        );

        // Draw absolute path preview below the text field
        String absolutePath = DownloadSettings.getInstance().getAbsoluteDownloadPath();
        String previewText = "Full path: " + absolutePath;
        context.drawTextWithShadow(
                this.textRenderer,
                previewText,
                PADDING * 2,
                contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 5,
                0xFFAAAAAA
        );

        // Render toasts on top of everything
        if (toastManager != null) {
            toastManager.render(context, delta, mouseX, mouseY);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        goBack();
    }
}


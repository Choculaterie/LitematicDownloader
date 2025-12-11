package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.gui.widget.CustomButton;
import com.choculaterie.gui.widget.CustomTextField;
import com.choculaterie.gui.widget.ToastManager;
import com.choculaterie.gui.widget.ToggleButton;
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
    private CustomButton browseButton;
    private ToggleButton successToastsToggle;
    private ToggleButton errorToastsToggle;
    private ToggleButton infoToastsToggle;
    private ToggleButton warningToastsToggle;
    private ToastManager toastManager;
    private String pendingToastMessage;
    private boolean pendingToastSuccess;

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

        // Calculate responsive sizes based on screen width
        boolean isCompact = this.width < 400;
        boolean isVeryCompact = this.width < 300;

        int browseButtonWidth = isVeryCompact ? 50 : (isCompact ? 70 : 90);
        String browseLabel = isVeryCompact ? "..." : "Browse...";

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
        int fieldWidth = Math.max(100, this.width - PADDING * 4 - browseButtonWidth - PADDING);

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
                browseButtonWidth,
                TEXT_FIELD_HEIGHT,
                Text.literal(browseLabel),
                button -> openFileDialog()
        );
        this.addDrawableChild(browseButton);

        // Toasts toggle section
        int toastsY = contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 30;
        int toggleSpacing = 25;

        // Info toasts toggle
        infoToastsToggle = new ToggleButton(
                PADDING * 2 + 120,
                toastsY + LABEL_HEIGHT,
                DownloadSettings.getInstance().isInfoToastsEnabled(),
                enabled -> DownloadSettings.getInstance().setInfoToastsEnabled(enabled)
        );
        this.addDrawableChild(infoToastsToggle);

        // Success toasts toggle
        successToastsToggle = new ToggleButton(
                PADDING * 2 + 120,
                toastsY + LABEL_HEIGHT + toggleSpacing,
                DownloadSettings.getInstance().isSuccessToastsEnabled(),
                enabled -> DownloadSettings.getInstance().setSuccessToastsEnabled(enabled)
        );
        this.addDrawableChild(successToastsToggle);

        // Warning toasts toggle
        warningToastsToggle = new ToggleButton(
                PADDING * 2 + 120,
                toastsY + LABEL_HEIGHT + toggleSpacing * 2,
                DownloadSettings.getInstance().isWarningToastsEnabled(),
                enabled -> DownloadSettings.getInstance().setWarningToastsEnabled(enabled)
        );
        this.addDrawableChild(warningToastsToggle);

        // Error toasts toggle
        errorToastsToggle = new ToggleButton(
                PADDING * 2 + 120,
                toastsY + LABEL_HEIGHT + toggleSpacing * 3,
                DownloadSettings.getInstance().isErrorToastsEnabled(),
                enabled -> DownloadSettings.getInstance().setErrorToastsEnabled(enabled)
        );
        this.addDrawableChild(errorToastsToggle);
    }

    private void goBack() {
        if (this.client != null) {
            this.client.setScreen(parentScreen);
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
                        if (selectedPath != null && !selectedPath.isEmpty()) {
                            DownloadSettings.getInstance().setDownloadPath(selectedPath);
                            System.out.println("Download path saved from directory picker: " + selectedPath);
                            pendingToastMessage = "Download path updated!";
                            pendingToastSuccess = true;
                        } else {
                            pendingToastMessage = "Failed to select folder";
                            pendingToastSuccess = false;
                        }
                    }
            );
            this.client.setScreen(picker);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Show pending toast if any
        if (pendingToastMessage != null && toastManager != null) {
            if (pendingToastSuccess) {
                toastManager.showSuccess(pendingToastMessage);
            } else {
                toastManager.showError(pendingToastMessage);
            }
            pendingToastMessage = null;
        }

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

        // Draw toasts toggle section header
        int toastsY = contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 30;
        int toggleSpacing = 25;

        context.drawTextWithShadow(
                this.textRenderer,
                "Show Notifications:",
                PADDING * 2,
                toastsY,
                0xFFFFFFFF
        );

        // Draw individual toast type labels
        context.drawTextWithShadow(
                this.textRenderer,
                "Info:",
                PADDING * 2,
                toastsY + LABEL_HEIGHT + 6,
                0xFF2196F3
        );

        context.drawTextWithShadow(
                this.textRenderer,
                "Success:",
                PADDING * 2,
                toastsY + LABEL_HEIGHT + toggleSpacing + 6,
                0xFF4CAF50
        );

        context.drawTextWithShadow(
                this.textRenderer,
                "Warning:",
                PADDING * 2,
                toastsY + LABEL_HEIGHT + toggleSpacing * 2 + 6,
                0xFFFFC107
        );

        context.drawTextWithShadow(
                this.textRenderer,
                "Error:",
                PADDING * 2,
                toastsY + LABEL_HEIGHT + toggleSpacing * 3 + 6,
                0xFFFF5252
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


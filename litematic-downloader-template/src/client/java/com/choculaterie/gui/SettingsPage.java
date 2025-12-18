package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.gui.widget.ConfirmPopup;
import com.choculaterie.gui.widget.CustomButton;
import com.choculaterie.gui.widget.CustomTextField;
import com.choculaterie.gui.widget.ToastManager;
import com.choculaterie.gui.widget.ToggleButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
    private CustomButton setPathButton;
    private ToggleButton successToastsToggle;
    private ToggleButton errorToastsToggle;
    private ToggleButton infoToastsToggle;
    private ToggleButton warningToastsToggle;
    private ToastManager toastManager;
    private String pendingToastMessage;
    private boolean pendingToastSuccess;
    private ConfirmPopup activePopup;

    public SettingsPage(Screen parentScreen) {
        super(Text.of("Settings"));
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
                Text.of("←"),
                button -> goBack()
        );
        this.addDrawableChild(backButton);

        // Download path section
        int contentY = PADDING * 4 + BUTTON_HEIGHT;

        // Set button is square, same height as text field, placed far right
        int setButtonSize = TEXT_FIELD_HEIGHT;
        int setButtonX = this.width - PADDING * 2 - setButtonSize;

        // Browse button next to set button
        int browseButtonX = setButtonX - PADDING - browseButtonWidth;

        // Text field takes remaining space
        int fieldWidth = Math.max(100, browseButtonX - PADDING * 3);

        // Download path text field
        if (this.client != null) {
            downloadPathField = new CustomTextField(
                    this.client,
                    PADDING * 2,
                    contentY + LABEL_HEIGHT,
                    fieldWidth,
                    TEXT_FIELD_HEIGHT,
                    Text.of("Download Path")
            );
            downloadPathField.setText(DownloadSettings.getInstance().getDownloadPath());
            downloadPathField.setPlaceholder(Text.of("Enter download path..."));
            // Don't add as drawable child - CustomTextField handles its own input via GLFW
            // Adding it would cause double character input
        }

        // Browse button
        browseButton = new CustomButton(
                browseButtonX,
                contentY + LABEL_HEIGHT,
                browseButtonWidth,
                TEXT_FIELD_HEIGHT,
                Text.of(browseLabel),
                button -> openFileDialog()
        );
        this.addDrawableChild(browseButton);

        // Set button (square, far right) - initially hidden, shown when path changes
        setPathButton = new CustomButton(
                setButtonX,
                contentY + LABEL_HEIGHT,
                setButtonSize,
                setButtonSize,
                Text.of("✓"),
                button -> saveDownloadPath()
        );
        setPathButton.visible = false; // Hidden by default
        this.addDrawableChild(setPathButton);

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

    private void saveDownloadPath() {
        if (downloadPathField != null) {
            String newPath = downloadPathField.getText();
            if (newPath != null && !newPath.isEmpty()) {
                // Check if the directory exists
                String absolutePath = DownloadSettings.getInstance().getGameDirectory() + File.separator + newPath;
                File directory = new File(absolutePath);

                if (!directory.exists()) {
                    // Show confirmation popup to create the folder
                    activePopup = new ConfirmPopup(
                        this,
                        "Create Folder?",
                        "The folder '" + newPath + "' does not exist.\n\nDo you want to create it?",
                        () -> {
                            // User confirmed - create the folder and save
                            boolean created = directory.mkdirs();
                            if (created) {
                                completeSave(newPath);
                            } else {
                                if (toastManager != null) {
                                    toastManager.showError("Failed to create folder!");
                                }
                            }
                            activePopup = null;
                        },
                        () -> {
                            // User cancelled
                            activePopup = null;
                        },
                        "Create"  // Custom confirm button text
                    );
                } else {
                    // Directory exists, save directly
                    completeSave(newPath);
                }
            }
        }
    }

    private void completeSave(String newPath) {
        DownloadSettings.getInstance().setDownloadPath(newPath);
        System.out.println("Download path saved: " + newPath);
        if (toastManager != null) {
            toastManager.showSuccess("Download path saved!");
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

        // Render download path text field manually (not a drawable child)
        if (downloadPathField != null) {
            downloadPathField.render(context, mouseX, mouseY, delta);

            // Show/hide set button based on whether path has changed
            String currentText = downloadPathField.getText();
            String savedPath = DownloadSettings.getInstance().getDownloadPath();
            boolean pathChanged = !currentText.equals(savedPath);
            if (setPathButton != null) {
                setPathButton.visible = pathChanged;
            }
        }

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

        // Render popup on top of everything else
        if (activePopup != null) {
            activePopup.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Handle popup first if active
        if (activePopup != null) {
            return activePopup.mouseClicked(mouseX, mouseY, button);
        }

        // Handle download path text field click
        if (button == 0 && downloadPathField != null) {
            if (downloadPathField.isMouseOver(mouseX, mouseY)) {
                downloadPathField.setFocused(true);
                return true;
            } else {
                downloadPathField.setFocused(false);
            }
        }
        return super.mouseClicked(click, doubled);
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


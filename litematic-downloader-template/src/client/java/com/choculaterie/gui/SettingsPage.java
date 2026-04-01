package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.gui.widget.ConfirmPopup;
import com.choculaterie.gui.widget.CustomButton;
import com.choculaterie.gui.widget.CustomTextField;
import com.choculaterie.gui.widget.ToastManager;
import com.choculaterie.gui.widget.ToggleButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;

public class SettingsPage extends Screen {
    private static final int PADDING = 10;
    private static final int BACK_BUTTON_SIZE = 20;
    private static final int LABEL_HEIGHT = 20;
    private static final int TEXT_FIELD_HEIGHT = 20;
    private static final int TOGGLE_SPACING = 25;
    private static final String TITLE = "Settings";

    private final Screen parentScreen;
    private CustomTextField downloadPathField;
    private CustomButton setPathButton;
    private ToastManager toastManager;
    private ConfirmPopup activePopup;

    private String pendingToastMessage;
    private boolean pendingToastSuccess;
    private Runnable onApiToggleChanged;

    public SettingsPage(Screen parentScreen) {
        super(Component.literal("Settings"));
        this.parentScreen = parentScreen;
    }

    public void setOnApiToggleChanged(Runnable callback) {
        this.onApiToggleChanged = callback;
    }

    @Override
    protected void init() {
        super.init();

        if (this.minecraft != null) {
            toastManager = new ToastManager(this.minecraft);
        }

        addBackButton();
        int contentY = initDownloadPathSection();
        initToastToggles(contentY);
    }

    private void addBackButton() {
        CustomButton backButton = new CustomButton(
                PADDING, PADDING, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE,
                Component.literal("←"), button -> goBack()
        );
        this.addRenderableWidget(backButton);
    }

    private int initDownloadPathSection() {
        int contentY = PADDING * 4 + BACK_BUTTON_SIZE;
        boolean isVeryCompact = this.width < 300;
        int browseButtonWidth = isVeryCompact ? 50 : (this.width < 400 ? 70 : 90);
        String browseLabel = isVeryCompact ? "..." : "Browse...";

        int setButtonSize = TEXT_FIELD_HEIGHT;
        int setButtonX = this.width - PADDING * 2 - setButtonSize;
        int browseButtonX = setButtonX - PADDING - browseButtonWidth;
        int fieldWidth = Math.max(100, browseButtonX - PADDING * 3);

        if (this.minecraft != null) {
            downloadPathField = new CustomTextField(
                    this.minecraft, PADDING * 2, contentY + LABEL_HEIGHT,
                    fieldWidth, TEXT_FIELD_HEIGHT, Component.literal("Download Path")
            );
            downloadPathField.setValue(DownloadSettings.getInstance().getDownloadPath());
            downloadPathField.setPlaceholder(Component.literal("Enter download path..."));
        }

        CustomButton browseButton = new CustomButton(
                browseButtonX, contentY + LABEL_HEIGHT, browseButtonWidth, TEXT_FIELD_HEIGHT,
                Component.literal(browseLabel), button -> openFileDialog()
        );
        this.addRenderableWidget(browseButton);

        setPathButton = new CustomButton(
                setButtonX, contentY + LABEL_HEIGHT, setButtonSize, setButtonSize,
                Component.literal("✓"), button -> saveDownloadPath()
        );
        setPathButton.visible = false;
        this.addRenderableWidget(setPathButton);

        return contentY;
    }

    private void initToastToggles(int contentY) {
        int toastsY = contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 30;
        DownloadSettings settings = DownloadSettings.getInstance();

        createToastToggle(0, toastsY, settings.isInfoToastsEnabled(),
                settings::setInfoToastsEnabled);
        createToastToggle(1, toastsY, settings.isSuccessToastsEnabled(),
                settings::setSuccessToastsEnabled);
        createToastToggle(2, toastsY, settings.isWarningToastsEnabled(),
                settings::setWarningToastsEnabled);
        createToastToggle(3, toastsY, settings.isErrorToastsEnabled(),
                settings::setErrorToastsEnabled);

        int apiToggleY = toastsY + LABEL_HEIGHT + (TOGGLE_SPACING * 4) + 20;
        ToggleButton apiToggle = new ToggleButton(
                PADDING * 2 + 200, apiToggleY,
                settings.isUseChoculaterieAPI(),
                enabled -> {
                    settings.setUseChoculaterieAPI(enabled);
                    if (onApiToggleChanged != null) {
                        onApiToggleChanged.run();
                    }
                }
        );
        this.addRenderableWidget(apiToggle);
    }

    private void createToastToggle(int index, int baseY, boolean enabled,
                                   java.util.function.Consumer<Boolean> callback) {
        ToggleButton toggle = new ToggleButton(
                PADDING * 2 + 120, baseY + LABEL_HEIGHT + (TOGGLE_SPACING * index),
                enabled, callback
        );
        this.addRenderableWidget(toggle);
    }

    private void goBack() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }

    private void openFileDialog() {
        if (this.minecraft != null) {
            String startPath = DownloadSettings.getInstance().getAbsoluteDownloadPath();
            DirectoryPickerScreen picker = new DirectoryPickerScreen(
                    this,
                    startPath,
                    selectedPath -> {
                        if (downloadPathField != null) {
                            downloadPathField.setValue(selectedPath);
                        }
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
            this.minecraft.setScreen(picker);
        }
    }

    private void saveDownloadPath() {
        if (downloadPathField == null) return;

        String newPath = downloadPathField.getValue();
        if (newPath == null || newPath.isEmpty()) return;

        String absolutePath = DownloadSettings.getInstance().getGameDirectory() + File.separator + newPath;
        File directory = new File(absolutePath);

        if (directory.exists()) {
            completeSave(newPath);
        } else {
            showCreateFolderConfirmation(newPath, directory);
        }
    }

    private void showCreateFolderConfirmation(String relativePath, File directory) {
        activePopup = new ConfirmPopup(
                this,
                "Create Folder?",
                "The folder '" + relativePath + "' does not exist.\n\nDo you want to create it?",
                () -> createFolderAndSave(directory, relativePath),
                () -> activePopup = null,
                "Create"
        );
    }

    private void createFolderAndSave(File directory, String relativePath) {
        if (directory.mkdirs()) {
            completeSave(relativePath);
        } else {
            if (toastManager != null) {
                toastManager.showError("Failed to create folder!");
            }
        }
        activePopup = null;
    }

    private void completeSave(String newPath) {
        DownloadSettings.getInstance().setDownloadPath(newPath);
        if (toastManager != null) {
            toastManager.showSuccess("Download path saved!");
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        showPendingToast();
        context.fill(0, 0, this.width, this.height, 0xFF202020);

        super.extractRenderState(context, mouseX, mouseY, delta);

        int contentY = renderTitleAndDownloadSection(context, mouseX, mouseY, delta);
        renderToastTogglesSection(context, contentY);
        renderOverlays(context, mouseX, mouseY, delta);
    }

    private void showPendingToast() {
        if (pendingToastMessage == null || toastManager == null) return;

        if (pendingToastSuccess) {
            toastManager.showSuccess(pendingToastMessage);
        } else {
            toastManager.showError(pendingToastMessage);
        }
        pendingToastMessage = null;
    }

    private int renderTitleAndDownloadSection(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int titleX = (this.width - this.font.width(TITLE)) / 2;
        context.text(this.font, TITLE, titleX,
                PADDING * 2 + BACK_BUTTON_SIZE, 0xFFFFFFFF);

        int contentY = PADDING * 4 + BACK_BUTTON_SIZE;
        context.text(this.font,
                "Download Path (relative to game folder):",
                PADDING * 2, contentY, 0xFFFFFFFF);

        renderDownloadPathField(context, contentY, mouseX, mouseY, delta);
        return contentY;
    }

    private void renderDownloadPathField(GuiGraphicsExtractor context, int contentY, int mouseX, int mouseY, float delta) {
        if (downloadPathField == null) return;

        downloadPathField.extractRenderState(context, mouseX, mouseY, delta);

        String currentText = downloadPathField.getValue();
        String savedPath = DownloadSettings.getInstance().getDownloadPath();
        if (setPathButton != null) {
            setPathButton.visible = !currentText.equals(savedPath);
        }

        String previewText = "Full path: " + DownloadSettings.getInstance().getAbsoluteDownloadPath();
        context.text(this.font, previewText, PADDING * 2,
                contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 5, 0xFFAAAAAA);
    }

    private void renderToastTogglesSection(GuiGraphicsExtractor context, int contentY) {
        int toastsY = contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 30;

        context.text(this.font, "Show Notifications:",
                PADDING * 2, toastsY, 0xFFFFFFFF);

        renderToastToggleLabel(context, "Info:", toastsY, 0, 0xFF2196F3);
        renderToastToggleLabel(context, "Success:", toastsY, 1, 0xFF4CAF50);
        renderToastToggleLabel(context, "Warning:", toastsY, 2, 0xFFFFC107);
        renderToastToggleLabel(context, "Error:", toastsY, 3, 0xFFFF5252);

        int apiToggleY = toastsY + LABEL_HEIGHT + (TOGGLE_SPACING * 4) + 20;
        context.text(this.font, "Use Choculaterie API:",
                PADDING * 2, apiToggleY + 6, 0xFFFFFFFF);
    }

    private void renderToastToggleLabel(GuiGraphicsExtractor context, String label, int baseY, int index, int color) {
        int y = baseY + LABEL_HEIGHT + TOGGLE_SPACING * index + 6;
        context.text(this.font, label, PADDING * 2, y, color);
    }

    private void renderOverlays(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (toastManager != null) {
            toastManager.render(context, delta, mouseX, mouseY);
        }
        if (activePopup != null) {
            activePopup.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (activePopup != null) {
            return activePopup.mouseClicked(mouseX, mouseY, button);
        }

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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (activePopup != null) {
            activePopup = null;
            return false;
        }
        return super.shouldCloseOnEsc();
    }

    @Override
    public void onClose() {
        goBack();
    }
}


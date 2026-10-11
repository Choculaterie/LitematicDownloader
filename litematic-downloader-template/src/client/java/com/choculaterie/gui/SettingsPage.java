package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.vanilib.gui.screen.DirectoryPickerScreen;
import com.choculaterie.vanilib.gui.theme.ButtonStyle;
import com.choculaterie.vanilib.gui.theme.UIDraw;
import com.choculaterie.vanilib.gui.theme.UIIcon;
import com.choculaterie.vanilib.gui.theme.UITheme;
import com.choculaterie.vanilib.gui.widget.ConfirmPopup;
import com.choculaterie.vanilib.gui.widget.CustomButton;
import com.choculaterie.vanilib.gui.widget.CustomTextField;
import com.choculaterie.gui.widget.ToastManager;
import com.choculaterie.vanilib.gui.widget.ToggleButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import com.mojang.blaze3d.platform.InputConstants;

public class SettingsPage extends Screen {
    private static final int PADDING = 10;
    private static final int BACK_BUTTON_SIZE = 21;
    private static final int LABEL_HEIGHT = 21;
    private static final int TEXT_FIELD_HEIGHT = 21;
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

    private int toastsY(int contentY) {
        return contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 30;
    }

    private int apiToggleY(int contentY) {
        return toastsY(contentY) + LABEL_HEIGHT + (TOGGLE_SPACING * 4) + 20;
    }



    private void addBackButton() {
        CustomButton backButton = new CustomButton(
                PADDING, PADDING, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE,
                Component.literal(""), button -> goBack()
        );
        backButton.setIcon(UIIcon.ARROW_LEFT);
        this.addRenderableWidget(backButton);
    }

    private int initDownloadPathSection() {
        int contentY = PADDING * 4 + BACK_BUTTON_SIZE;
        boolean isVeryCompact = this.width < 300;
        int browseButtonWidth = isVeryCompact ? 50 : (this.width < 400 ? 70 : 90);
        String browseLabel = isVeryCompact ? "" : "Browse";

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
        browseButton.setIcon(UIIcon.FOLDER);
        this.addRenderableWidget(browseButton);

        setPathButton = new CustomButton(
                setButtonX, contentY + LABEL_HEIGHT, setButtonSize, setButtonSize,
                Component.literal(""), button -> saveDownloadPath()
        );
        setPathButton.setIcon(UIIcon.CHECK).setStyle(ButtonStyle.PRIMARY);
        setPathButton.visible = false;
        this.addRenderableWidget(setPathButton);

        return contentY;
    }

    private void initToastToggles(int contentY) {
        int toastsY = toastsY(contentY);
        DownloadSettings settings = DownloadSettings.getInstance();

        createToastToggle(0, toastsY, settings.isInfoToastsEnabled(),
                settings::setInfoToastsEnabled);
        createToastToggle(1, toastsY, settings.isSuccessToastsEnabled(),
                settings::setSuccessToastsEnabled);
        createToastToggle(2, toastsY, settings.isWarningToastsEnabled(),
                settings::setWarningToastsEnabled);
        createToastToggle(3, toastsY, settings.isErrorToastsEnabled(),
                settings::setErrorToastsEnabled);

        int apiToggleY = apiToggleY(contentY);
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
            this.minecraft.gui.setScreen(parentScreen);
        }
    }

    private void openFileDialog() {
        if (this.minecraft != null) {
            String startPath = DownloadSettings.getInstance().getAbsoluteDownloadPath();
            DirectoryPickerScreen picker = new DirectoryPickerScreen(
                    this,
                    new File(startPath),
                    new File(DownloadSettings.getInstance().getGameDirectory()),
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
            this.minecraft.gui.setScreen(picker);
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
        context.fill(0, 0, this.width, this.height, UITheme.Colors.BACKGROUND);

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
        UIDraw.centeredText(context, TITLE, this.width / 2, UIDraw.bevelContentY(PADDING, BACK_BUTTON_SIZE), UITheme.Colors.TEXT_HIGHLIGHT);

        int contentY = PADDING * 4 + BACK_BUTTON_SIZE;
        UIDraw.text(context, "Download path (relative to game folder)", PADDING * 2, contentY, UITheme.Colors.TEXT_SUBTITLE);

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
        UIDraw.text(context, UIDraw.ellipsize(previewText, this.width - PADDING * 4), PADDING * 2,
                contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 5, UITheme.Colors.TEXT_MUTED);
    }

    private void renderToastTogglesSection(GuiGraphicsExtractor context, int contentY) {
        int toastsY = contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 30;

        UIDraw.text(context, "Show notifications", PADDING * 2, toastsY, UITheme.Colors.TEXT_SUBTITLE);

        renderToastToggleLabel(context, "Info", toastsY, 0, UITheme.Colors.TOAST_ACCENT_INFO);
        renderToastToggleLabel(context, "Success", toastsY, 1, UITheme.Colors.TOAST_ACCENT_SUCCESS);
        renderToastToggleLabel(context, "Warning", toastsY, 2, UITheme.Colors.TOAST_ACCENT_WARNING);
        renderToastToggleLabel(context, "Error", toastsY, 3, UITheme.Colors.TOAST_ACCENT_ERROR);

        int apiToggleY = toastsY + LABEL_HEIGHT + (TOGGLE_SPACING * 4) + 20;
        UIDraw.text(context, "Use Choculaterie API", PADDING * 2, UIDraw.flatContentY(apiToggleY, UITheme.Dimensions.BUTTON_HEIGHT), UITheme.Colors.TEXT_PRIMARY);
    }


    private void renderToastToggleLabel(GuiGraphicsExtractor context, String label, int baseY, int index, int color) {
        int rowY = baseY + LABEL_HEIGHT + TOGGLE_SPACING * index;
        int y = UIDraw.flatContentY(rowY, UITheme.Dimensions.BUTTON_HEIGHT);
        context.fill(PADDING * 2, rowY + 4, PADDING * 2 + 2, rowY + UITheme.Dimensions.BUTTON_HEIGHT - 4, color);
        UIDraw.text(context, label, PADDING * 2 + 7, y, UITheme.Colors.TEXT_PRIMARY);
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

        if (button == InputConstants.MOUSE_BUTTON_LEFT && downloadPathField != null) {
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


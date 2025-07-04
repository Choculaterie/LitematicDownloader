package com.choculaterie.gui;

import com.choculaterie.config.SettingsManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.util.function.Consumer;

public class SettingsScreen extends Screen {
    private final Screen parentScreen;
    private final Consumer<Boolean> onSettingsChanged;
    private TextFieldWidget pathField;
    private ButtonWidget toggleButton;
    private ButtonWidget browseButton;
    private ButtonWidget resetButton;
    private SettingsManager.Settings settings;

    public SettingsScreen(Screen parentScreen, Consumer<Boolean> onSettingsChanged) {
        super(Text.literal("Settings"));
        this.parentScreen = parentScreen;
        this.onSettingsChanged = onSettingsChanged;
        this.settings = SettingsManager.getSettings();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 60;

        // Path field
        pathField = new TextFieldWidget(
                this.textRenderer,
                centerX - 200,
                startY,
                400,
                20,
                Text.literal("")
        );
        pathField.setMaxLength(500);
        pathField.setText(settings.useCustomPath ? settings.customSchematicsPath : SettingsManager.getDefaultSchematicsPath());
        pathField.setEditable(settings.useCustomPath);
        this.addSelectableChild(pathField);

        // Toggle button
        toggleButton = ButtonWidget.builder(
                        Text.literal(settings.useCustomPath ? "Use Custom Path" : "Use Default Path"),
                        button -> {
                            settings.useCustomPath = !settings.useCustomPath;
                            button.setMessage(Text.literal(settings.useCustomPath ? "Use Custom Path" : "Use Default Path"));
                            pathField.setEditable(settings.useCustomPath);
                            if (!settings.useCustomPath) {
                                pathField.setText(SettingsManager.getDefaultSchematicsPath());
                            }
                            updateButtonStates();
                        })
                .dimensions(centerX - 100, startY + 35, 200, 20)
                .build();
        this.addDrawableChild(toggleButton);

        // Browse button
        browseButton = ButtonWidget.builder(
                        Text.literal("Browse..."),
                        button -> openFolderDialog())
                .dimensions(centerX - 100, startY + 70, 95, 20)
                .build();
        this.addDrawableChild(browseButton);

        // Reset button
        resetButton = ButtonWidget.builder(
                        Text.literal("Reset"),
                        button -> {
                            settings.useCustomPath = false;
                            pathField.setText(SettingsManager.getDefaultSchematicsPath());
                            pathField.setEditable(false);
                            toggleButton.setMessage(Text.literal("Use Default Path"));
                            updateButtonStates();
                        })
                .dimensions(centerX + 5, startY + 70, 95, 20)
                .build();
        this.addDrawableChild(resetButton);

        // Save button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Save"),
                        button -> {
                            String path = pathField.getText().trim();
                            if (settings.useCustomPath) {
                                if (path.isEmpty()) {
                                    // Show error message
                                    return;
                                }
                                // Validate path
                                File pathFile = new File(path);
                                if (!pathFile.exists()) {
                                    pathFile.mkdirs();
                                }
                                if (!pathFile.isDirectory()) {
                                    // Show error message
                                    return;
                                }
                                settings.customSchematicsPath = path;
                            }
                            SettingsManager.saveSettings();
                            onSettingsChanged.accept(true);
                            MinecraftClient.getInstance().setScreen(parentScreen);
                        })
                .dimensions(centerX - 100, startY + 105, 95, 20)
                .build());

        // Cancel button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Cancel"),
                        button -> {
                            MinecraftClient.getInstance().setScreen(parentScreen);
                        })
                .dimensions(centerX + 5, startY + 105, 95, 20)
                .build());

        updateButtonStates();
    }

    private void updateButtonStates() {
        browseButton.active = settings.useCustomPath;
        resetButton.active = settings.useCustomPath;
    }

    private void openFolderDialog() {
        try {
            // Try using the native system file chooser first
            if (tryNativeFileChooser()) {
                return;
            }

            // If native fails, show error message instead of trying Swing in headless environment
            ToastManager.addToast("Cannot open file browser in this environment. Please type the path manually.", true);
        } catch (Exception e) {
            e.printStackTrace();
            // Show error message to user
            ToastManager.addToast("Failed to open file browser: " + e.getMessage(), true);
        }
    }

    private boolean tryNativeFileChooser() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String currentPath = pathField.getText().trim();

            // If no current path, use the Minecraft run directory as starting directory
            if (currentPath.isEmpty()) {
                File gameDir = MinecraftClient.getInstance().runDirectory;
                currentPath = gameDir != null ? gameDir.getAbsolutePath() : SettingsManager.getDefaultSchematicsPath();
            }

            ProcessBuilder pb = null;
            Process process = null;

            if (os.contains("win")) {
                // Windows - use PowerShell with folder browser
                String script = String.format(
                    "Add-Type -AssemblyName System.Windows.Forms; " +
                    "$f = New-Object System.Windows.Forms.FolderBrowserDialog; " +
                    "$f.Description = 'Select Schematics Folder'; " +
                    "$f.SelectedPath = '%s'; " +
                    "$f.ShowNewFolderButton = $true; " +
                    "if ($f.ShowDialog() -eq 'OK') { Write-Output $f.SelectedPath }",
                    currentPath.replace("'", "''")
                );

                pb = new ProcessBuilder("powershell.exe", "-Command", script);

            } else if (os.contains("mac") || os.contains("darwin")) {
                // macOS - use osascript (AppleScript)
                String script = String.format(
                    "choose folder with prompt \"Select Schematics Folder\" default location POSIX file \"%s\"",
                    currentPath.replace("\"", "\\\"")
                );

                pb = new ProcessBuilder("osascript", "-e", script);

            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux - try different dialog tools in order of preference
                String[] tools = {"zenity", "kdialog", "yad"};

                for (String tool : tools) {
                    if (isCommandAvailable(tool)) {
                        pb = createLinuxDialog(tool, currentPath);
                        if (pb != null) {
                            break;
                        }
                    }
                }

                if (pb == null) {
                    return false; // No suitable dialog tool found
                }

            } else {
                return false; // Unsupported OS
            }

            if (pb == null) {
                return false;
            }

            // Start the process
            process = pb.start();

            // Read the output
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {

                String selectedPath = reader.readLine();
                int exitCode = process.waitFor();

                if (exitCode == 0 && selectedPath != null && !selectedPath.trim().isEmpty()) {
                    // macOS returns POSIX path format, convert if needed
                    if (os.contains("mac") && selectedPath.startsWith("alias ")) {
                        selectedPath = selectedPath.substring(6); // Remove "alias " prefix
                    }

                    pathField.setText(selectedPath.trim());
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private ProcessBuilder createLinuxDialog(String tool, String currentPath) {
        switch (tool) {
            case "zenity":
                return new ProcessBuilder("zenity", "--file-selection", "--directory",
                    "--title=Select Schematics Folder", "--filename=" + currentPath);

            case "kdialog":
                return new ProcessBuilder("kdialog", "--getexistingdirectory", currentPath,
                    "--title", "Select Schematics Folder");

            case "yad":
                return new ProcessBuilder("yad", "--file", "--directory",
                    "--title=Select Schematics Folder", "--filename=" + currentPath);

            default:
                return null;
        }
    }

    private boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", command);
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAnyLinuxDialogAvailable() {
        return isCommandAvailable("zenity") || isCommandAvailable("kdialog") || isCommandAvailable("yad");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        context.drawTextWithShadow(this.textRenderer, Text.literal("Schematics Path:"), this.width / 2 - 200, 48, 0xFFFFFF);

        pathField.render(context, mouseX, mouseY, delta);

    }
}


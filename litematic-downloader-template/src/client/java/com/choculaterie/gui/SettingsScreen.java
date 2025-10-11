package com.choculaterie.gui;

import com.choculaterie.config.SettingsManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.File;
import java.net.URI;
import java.util.function.Consumer;

public class SettingsScreen extends Screen {
    private final Screen parentScreen;
    private final Consumer<Boolean> onSettingsChanged;
    private TextFieldWidget pathField;
    private TextFieldWidget tokenField;
    private TextFieldWidget imagePathField;
    private ButtonWidget toggleButton;
    private ButtonWidget browseButton;
    private ButtonWidget resetButton;
    private ButtonWidget toggleImagePathButton;
    private ButtonWidget browseImageButton;
    private ButtonWidget resetImageButton;
    private final SettingsManager.Settings settings;
    private boolean tokenVisible = false;
    private String actualToken = "";

    // Fields to preserve user input during scrolling
    private String currentPathFieldText = "";
    private String currentImagePathFieldText = "";
    private String currentTokenFieldText = "";

    // Scrollbar-related fields
    private int scrollOffset = 0;
    private final int itemHeight = 25;
    private int scrollAreaX;
    private int scrollAreaY;
    private int scrollAreaWidth;
    private int scrollAreaHeight;
    private int scrollBarX;
    private int scrollBarY;
    private int scrollBarHeight;
    private int totalContentHeight;
    private boolean isScrolling = false;
    private int lastMouseY;

    public SettingsScreen(Screen parentScreen, Consumer<Boolean> onSettingsChanged) {
        this(parentScreen, onSettingsChanged, false);
    }

    public SettingsScreen(Screen parentScreen, Consumer<Boolean> onSettingsChanged, boolean showLinkAccountMessage) {
        super(Text.literal("Settings"));
        this.parentScreen = parentScreen;
        this.onSettingsChanged = onSettingsChanged;
        this.settings = SettingsManager.getSettings();

        // Only show toast if explicitly requested (from publish button)
        if (showLinkAccountMessage && !SettingsManager.hasApiToken()) {
            ToastManager.addToast("Please link your account to publish schematics", true);
        }
    }

    @Override
    protected void init() {
        updateScrollbarDimensions();

        // Save current text field values before clearing widgets
        saveCurrentTextFieldValues();

        // Clear existing widgets
        this.clearChildren();

        int centerX = this.width / 2;
        int baseY = scrollAreaY - scrollOffset;
        int currentY = baseY; // Moved up to align with new section titles

        // Back button (always visible at top, outside scroll area)
        this.addDrawableChild(ButtonWidget.builder(Text.literal("←"), button -> {
            MinecraftClient.getInstance().setScreen(parentScreen);
        }).dimensions(10, 10, 20, 20).build());

        // === LITEMATIC PATH SECTION ===
        // Section title space
        currentY += 20;

        // Path field
        pathField = new TextFieldWidget(
                this.textRenderer,
                centerX - 200,
                currentY,
                400,
                20,
                Text.literal("")
        );
        pathField.setMaxLength(500);
        // Use saved text or default value
        if (!currentPathFieldText.isEmpty()) {
            pathField.setText(currentPathFieldText);
        } else {
            pathField.setText(settings.useCustomPath ? settings.customSchematicsPath : SettingsManager.getDefaultSchematicsPath());
        }
        pathField.setEditable(settings.useCustomPath);
        this.addSelectableChild(pathField);
        currentY += 30;

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
                .dimensions(centerX - 100, currentY, 200, 20)
                .build();
        this.addDrawableChild(toggleButton);
        currentY += 30;

        // Browse and Reset buttons
        browseButton = ButtonWidget.builder(
                        Text.literal("Browse..."),
                        button -> openFolderDialog())
                .dimensions(centerX - 100, currentY, 95, 20)
                .build();
        this.addDrawableChild(browseButton);

        resetButton = ButtonWidget.builder(
                        Text.literal("Reset"),
                        button -> {
                            settings.useCustomPath = false;
                            pathField.setText(SettingsManager.getDefaultSchematicsPath());
                            pathField.setEditable(false);
                            toggleButton.setMessage(Text.literal("Use Default Path"));
                            updateButtonStates();
                        })
                .dimensions(centerX + 5, currentY, 95, 20)
                .build();
        this.addDrawableChild(resetButton);
        currentY += 50;

        // === IMAGE PATH SECTION ===
        // Image path field
        imagePathField = new TextFieldWidget(
                this.textRenderer,
                centerX - 200,
                currentY,
                400,
                20,
                Text.literal("")
        );
        imagePathField.setMaxLength(500);
        // Use saved text or default value
        if (!currentImagePathFieldText.isEmpty()) {
            imagePathField.setText(currentImagePathFieldText);
        } else {
            imagePathField.setText(settings.useCustomImagesPath ? settings.customImagesPath : SettingsManager.getDefaultImagesPath());
        }
        imagePathField.setEditable(settings.useCustomImagesPath);
        this.addSelectableChild(imagePathField);
        currentY += 30;

        // Toggle image path button
        toggleImagePathButton = ButtonWidget.builder(
                        Text.literal(settings.useCustomImagesPath ? "Use Custom Image Path" : "Use Default Image Path"),
                        button -> {
                            settings.useCustomImagesPath = !settings.useCustomImagesPath;
                            button.setMessage(Text.literal(settings.useCustomImagesPath ? "Use Custom Image Path" : "Use Default Image Path"));
                            imagePathField.setEditable(settings.useCustomImagesPath);
                            if (!settings.useCustomImagesPath) {
                                imagePathField.setText(SettingsManager.getDefaultImagesPath());
                            }
                            updateImageButtonStates();
                        })
                .dimensions(centerX - 100, currentY, 200, 20)
                .build();
        this.addDrawableChild(toggleImagePathButton);
        currentY += 30;

        // Browse image and Reset image buttons
        browseImageButton = ButtonWidget.builder(
                        Text.literal("Browse..."),
                        button -> openImageFolderDialog())
                .dimensions(centerX - 100, currentY, 95, 20)
                .build();
        this.addDrawableChild(browseImageButton);

        resetImageButton = ButtonWidget.builder(
                        Text.literal("Reset"),
                        button -> {
                            settings.useCustomImagesPath = false;
                            imagePathField.setText(SettingsManager.getDefaultImagesPath());
                            imagePathField.setEditable(false);
                            toggleImagePathButton.setMessage(Text.literal("Use Default Image Path"));
                            updateImageButtonStates();
                        })
                .dimensions(centerX + 5, currentY, 95, 20)
                .build();
        this.addDrawableChild(resetImageButton);
        currentY += 50;

        // === API TOKEN SECTION ===
        // Link Account button
        ButtonWidget linkAccountButton = ButtonWidget.builder(
                        Text.literal("Link Account"),
                        button -> openTokenGenerationPage())
                .dimensions(centerX - 100, currentY, 200, 20)
                .build();
        this.addDrawableChild(linkAccountButton);
        currentY += 40;

        // API Token field
        tokenField = new TextFieldWidget(
                this.textRenderer,
                centerX - 200,
                currentY,
                340,
                20,
                Text.literal("")
        );
        tokenField.setMaxLength(500);

        // Load the actual token and display masked version
        actualToken = SettingsManager.getApiToken();
        // Use saved text or update display
        if (!currentTokenFieldText.isEmpty()) {
            if (tokenVisible) {
                actualToken = currentTokenFieldText;
                tokenField.setText(currentTokenFieldText);
            } else {
                tokenField.setText(currentTokenFieldText);
            }
        } else {
            updateTokenFieldDisplay();
        }

        tokenField.setChangedListener(text -> {
            currentTokenFieldText = text; // Save current input
            if (tokenVisible) {
                actualToken = text;
            } else {
                // If user types while masked, replace the actual token
                if (!text.equals(getMaskedToken(actualToken))) {
                    actualToken = text;
                    updateTokenFieldDisplay();
                }
            }
        });

        this.addSelectableChild(tokenField);

        // Toggle token visibility button
        ButtonWidget toggleTokenVisibilityButton = ButtonWidget.builder(
                        Text.literal("👁"),
                        button -> {
                            tokenVisible = !tokenVisible;
                            updateTokenFieldDisplay();
                        })
                .dimensions(centerX + 145, currentY, 25, 20)
                .build();
        this.addDrawableChild(toggleTokenVisibilityButton);

        // Reset token button
        ButtonWidget resetTokenButton = ButtonWidget.builder(
                        Text.literal("Reset"),
                        button -> {
                            actualToken = "";
                            updateTokenFieldDisplay();
                            openTokenGenerationPage();
                        })
                .dimensions(centerX + 175, currentY, 50, 20)
                .build();
        this.addDrawableChild(resetTokenButton);
        currentY += 50;

        // === SAVE/CANCEL BUTTONS ===
        // Save button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Save"),
                        button -> {
                            String path = pathField.getText().trim();
                            if (settings.useCustomPath) {
                                if (path.isEmpty()) {
                                    ToastManager.addToast("Path cannot be empty when using custom path", true);
                                    return;
                                }
                                // Validate path
                                File pathFile = new File(path);
                                if (!pathFile.exists()) {
                                    boolean created = pathFile.mkdirs();
                                    if (!created) {
                                        ToastManager.addToast("Failed to create directory", true);
                                        return;
                                    }
                                }
                                if (!pathFile.isDirectory()) {
                                    ToastManager.addToast("Invalid directory path", true);
                                    return;
                                }
                                settings.customSchematicsPath = path;
                            }

                            // Save API token
                            SettingsManager.setApiToken(actualToken);

                            // Save image path
                            String imagePath = imagePathField.getText().trim();
                            if (settings.useCustomImagesPath) {
                                if (imagePath.isEmpty()) {
                                    ToastManager.addToast("Image path cannot be empty when using custom image path", true);
                                    return;
                                }
                                File imagePathFile = new File(imagePath);
                                if (!imagePathFile.exists()) {
                                    boolean created = imagePathFile.mkdirs();
                                    if (!created) {
                                        ToastManager.addToast("Failed to create image directory", true);
                                        return;
                                    }
                                }
                                if (!imagePathFile.isDirectory()) {
                                    ToastManager.addToast("Invalid image directory path", true);
                                    return;
                                }
                                settings.customImagesPath = imagePath;
                            }

                            SettingsManager.saveSettings();
                            onSettingsChanged.accept(true);

                            ToastManager.addToast("Settings saved successfully", false);
                            MinecraftClient.getInstance().setScreen(parentScreen);
                        })
                .dimensions(centerX - 100, currentY, 95, 20)
                .build());

        // Cancel button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Cancel"),
                        button -> MinecraftClient.getInstance().setScreen(parentScreen))
                .dimensions(centerX + 5, currentY, 95, 20)
                .build());

        // Calculate total content height
        totalContentHeight = currentY + 50 - baseY;

        updateButtonStates();
        updateImageButtonStates();
    }

    private void updateScrollbarDimensions() {
        // Define content area for scrolling
        scrollAreaX = 50;
        scrollAreaY = 50;
        scrollAreaWidth = this.width - 100;
        scrollAreaHeight = this.height - 100;

        // Calculate total content height (estimated)
        totalContentHeight = 400; // Approximate height of all content

        // Reset scroll offset if it's now out of bounds
        if (totalContentHeight <= scrollAreaHeight) {
            scrollOffset = 0;
        } else {
            scrollOffset = Math.min(scrollOffset, totalContentHeight - scrollAreaHeight);
        }
    }

    private void updateButtonStates() {
        browseButton.active = settings.useCustomPath;
        resetButton.active = settings.useCustomPath;
    }

    private void updateImageButtonStates() {
        browseImageButton.active = settings.useCustomImagesPath;
        resetImageButton.active = settings.useCustomImagesPath;
    }

    private void updateTokenFieldDisplay() {
        if (tokenVisible) {
            tokenField.setText(actualToken);
        } else {
            tokenField.setText(getMaskedToken(actualToken));
        }
    }

    private String getMaskedToken(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        return "*".repeat(Math.min(token.length(), 32));
    }

    private void openTokenGenerationPage() {
        try {
            String url = "https://choculaterie.com/api/LitematicDownloaderModAPI/GenerateApiToken";
            // For production, you might want to use:
            // String url = "https://choculaterie.com/api/LitematicDownloaderModAPI/GenerateApiToken";

            Util.getOperatingSystem().open(URI.create(url));
            ToastManager.addToast("Token generation page opened in browser", false);
        } catch (Exception e) {
            System.err.println("Failed to open browser: " + e.getMessage());
            ToastManager.addToast("Failed to open browser: " + e.getMessage(), true);
        }
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
            System.err.println("Failed to open file browser: " + e.getMessage());
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

            ProcessBuilder pb;
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

                pb = null;
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

            // Start the process with proper resource management
            try {
                process = pb.start();

                // Set a timeout to prevent hanging
                boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    return false;
                }

                // Read the output with proper resource management
                String selectedPath = null;
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    selectedPath = reader.readLine();
                }

                int exitCode = process.exitValue();

                if (exitCode == 0 && selectedPath != null && !selectedPath.trim().isEmpty()) {
                    // macOS returns POSIX path format, convert if needed
                    if (os.contains("mac") && selectedPath.startsWith("alias ")) {
                        selectedPath = selectedPath.substring(6); // Remove "alias " prefix
                    }

                    pathField.setText(selectedPath.trim());
                    return true;
                }

                return false;

            } finally {
                // Ensure process cleanup
                if (process != null && process.isAlive()) {
                    try {
                        process.destroyForcibly();
                        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        System.err.println("Error cleaning up file dialog process: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Native file chooser error: " + e.getMessage());
            return false;
        }
    }

    private ProcessBuilder createLinuxDialog(String tool, String currentPath) {
        return switch (tool) {
            case "zenity" -> new ProcessBuilder("zenity", "--file-selection", "--directory",
                    "--title=Select Schematics Folder", "--filename=" + currentPath);
            case "kdialog" -> new ProcessBuilder("kdialog", "--getexistingdirectory", currentPath,
                    "--title", "Select Schematics Folder");
            case "yad" -> new ProcessBuilder("yad", "--file", "--directory",
                    "--title=Select Schematics Folder", "--filename=" + currentPath);
            default -> null;
        };
    }

    private void openImageFolderDialog() {
        try {
            // Try using the native system file chooser first
            if (tryNativeImageFileChooser()) {
                return;
            }

            // If native fails, show error message instead of trying Swing in headless environment
            ToastManager.addToast("Cannot open file browser in this environment. Please type the path manually.", true);
        } catch (Exception e) {
            System.err.println("Failed to open file browser: " + e.getMessage());
            // Show error message to user
            ToastManager.addToast("Failed to open file browser: " + e.getMessage(), true);
        }
    }

    private boolean tryNativeImageFileChooser() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String currentPath = imagePathField.getText().trim();

            // If no current path, use the default image path
            if (currentPath.isEmpty()) {
                currentPath = SettingsManager.getDefaultImagesPath();
            }

            ProcessBuilder pb;
            Process process = null;

            if (os.contains("win")) {
                // Windows - use PowerShell with folder browser
                String script = String.format(
                    "Add-Type -AssemblyName System.Windows.Forms; " +
                    "$f = New-Object System.Windows.Forms.FolderBrowserDialog; " +
                    "$f.Description = 'Select Images Folder'; " +
                    "$f.SelectedPath = '%s'; " +
                    "$f.ShowNewFolderButton = $true; " +
                    "if ($f.ShowDialog() -eq 'OK') { Write-Output $f.SelectedPath }",
                    currentPath.replace("'", "''")
                );

                pb = new ProcessBuilder("powershell.exe", "-Command", script);

            } else if (os.contains("mac") || os.contains("darwin")) {
                // macOS - use osascript (AppleScript)
                String script = String.format(
                    "choose folder with prompt \"Select Images Folder\" default location POSIX file \"%s\"",
                    currentPath.replace("\"", "\\\"")
                );

                pb = new ProcessBuilder("osascript", "-e", script);

            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux - try different dialog tools in order of preference
                String[] tools = {"zenity", "kdialog", "yad"};

                pb = null;
                for (String tool : tools) {
                    if (isCommandAvailable(tool)) {
                        pb = createLinuxImageDialog(tool, currentPath);
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

            // Start the process with proper resource management
            try {
                process = pb.start();

                // Set a timeout to prevent hanging
                boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    return false;
                }

                // Read the output with proper resource management
                String selectedPath = null;
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    selectedPath = reader.readLine();
                }

                int exitCode = process.exitValue();

                if (exitCode == 0 && selectedPath != null && !selectedPath.trim().isEmpty()) {
                    // macOS returns POSIX path format, convert if needed
                    if (os.contains("mac") && selectedPath.startsWith("alias ")) {
                        selectedPath = selectedPath.substring(6); // Remove "alias " prefix
                    }

                    imagePathField.setText(selectedPath.trim());
                    return true;
                }

                return false;

            } finally {
                // Ensure process cleanup
                if (process != null && process.isAlive()) {
                    try {
                        process.destroyForcibly();
                        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        System.err.println("Error cleaning up file dialog process: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Native image file chooser error: " + e.getMessage());
            return false;
        }
    }

    private ProcessBuilder createLinuxImageDialog(String tool, String currentPath) {
        return switch (tool) {
            case "zenity" -> new ProcessBuilder("zenity", "--file-selection", "--directory",
                    "--title=Select Images Folder", "--filename=" + currentPath);
            case "kdialog" -> new ProcessBuilder("kdialog", "--getexistingdirectory", currentPath,
                    "--title", "Select Images Folder");
            case "yad" -> new ProcessBuilder("yad", "--file", "--directory",
                    "--title=Select Images Folder", "--filename=" + currentPath);
            default -> null;
        };
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Draw title
        //context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 25, 0xFFFFFFFF);

        // Create clipping region for scrollable content
        context.enableScissor(
                scrollAreaX,
                scrollAreaY,
                scrollAreaX + scrollAreaWidth,
                scrollAreaY + scrollAreaHeight
        );

        // Draw section labels with proper positioning
        int centerX = this.width / 2;
        int baseY = scrollAreaY - scrollOffset;
        int currentY = baseY;

        // Litematic Path Section
        currentY += 10;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Litematic Path"), centerX, currentY, 0xFFFFFF00);
        currentY += 110; // Skip to next section

        // Image Path Section
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Image Path"), centerX, currentY, 0xFFFFFF00);
        currentY += 150; // Skip to next section

        // API Token Section
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("API Token"), centerX, currentY, 0xFFFFFF00);

        context.disableScissor();

        // Draw scrollbar if needed
        if (totalContentHeight > scrollAreaHeight) {
            int scrollBarWidth = 6;
            scrollBarHeight = Math.max(20, scrollAreaHeight * scrollAreaHeight / totalContentHeight);
            scrollBarX = scrollAreaX + scrollAreaWidth;
            scrollBarY = scrollAreaY + (int)((float)scrollOffset / (totalContentHeight - scrollAreaHeight)
                    * (scrollAreaHeight - scrollBarHeight));

            // Draw scroll bar background
            context.fill(scrollBarX, scrollAreaY,
                    scrollBarX + scrollBarWidth, scrollAreaY + scrollAreaHeight,
                    0x33FFFFFF);

            // Draw scroll bar handle with hover effect
            boolean isHovering = mouseX >= scrollBarX && mouseX <= scrollBarX + scrollBarWidth &&
                    mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight;

            int scrollBarColor = isHovering || isScrolling ? 0xFFFFFFFF : 0xAAFFFFFF;
            context.fill(scrollBarX, scrollBarY,
                    scrollBarX + scrollBarWidth, scrollBarY + scrollBarHeight,
                    scrollBarColor);
        }

        // Render text fields on top
        pathField.render(context, mouseX, mouseY, delta);
        imagePathField.render(context, mouseX, mouseY, delta);
        tokenField.render(context, mouseX, mouseY, delta);

        // Draw toast notifications at the end
        ToastManager.render(context, this.width);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Handle scrollbar interaction
        if (button == 0 && totalContentHeight > scrollAreaHeight) {
            // Check if click is on scroll bar
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {
                isScrolling = true;
                lastMouseY = (int) mouseY;
                return true;
            }

            // Check if click is in scroll area but not on the handle (jump scroll)
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {
                // Calculate new scroll position based on click location
                float clickPercent = ((float) mouseY - scrollAreaY) / scrollAreaHeight;
                scrollOffset = (int) (clickPercent * (totalContentHeight - scrollAreaHeight));
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset));

                // Reinitialize widgets to update their positions
                this.init();

                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
        double mouseY = click.y();

        if (isScrolling) {
            int currentMouseY = (int) mouseY;
            if (currentMouseY != lastMouseY) {
                float denom = Math.max(1, (scrollAreaHeight - scrollBarHeight));
                float dragPercentage = (float) (currentMouseY - lastMouseY) / denom;
                int scrollAmount = (int) (dragPercentage * (totalContentHeight - scrollAreaHeight));

                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset + scrollAmount));
                lastMouseY = currentMouseY;

                // Reinitialize widgets to update their positions
                this.init();
            }
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (click.button() == 0 && isScrolling) {
            isScrolling = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Only scroll if mouse is over the content area
        if (mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

            // Check if there's content that requires scrolling
            if (totalContentHeight > scrollAreaHeight) {
                // Scroll amount - adjust multiplier to change scroll speed
                int scrollAmount = (int)(verticalAmount * 3 * itemHeight);

                // Update scroll offset
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight,
                        scrollOffset - scrollAmount));

                // Reinitialize widgets to update their positions
                this.init();

                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void saveCurrentTextFieldValues() {
        if (pathField != null) {
            currentPathFieldText = pathField.getText();
        }
        if (imagePathField != null) {
            currentImagePathFieldText = imagePathField.getText();
        }
        if (tokenField != null) {
            currentTokenFieldText = tokenField.getText();
        }
    }

    // ...existing code for all other methods...
}

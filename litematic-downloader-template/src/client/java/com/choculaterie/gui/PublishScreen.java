package com.choculaterie.gui;

import com.choculaterie.config.SettingsManager;
import com.choculaterie.models.SchematicCreateDTO;
import com.choculaterie.networking.LitematicHttpClient;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.util.InputUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.client.gl.RenderPipelines;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class PublishScreen extends Screen {
    private final Screen parentScreen;
    private final File litematicFile;

    private TextFieldWidget nameField;
    private MultilineTextFieldWidget descriptionField; // Changed to multiline
    private TextFieldWidget tagsField;
    private TextFieldWidget youtubeField;
    private TextFieldWidget mediaFireField;

    private ButtonWidget publishButton;

    private final List<File> selectedImages = new ArrayList<>();
    private final List<File> selectedLitematicFiles = new ArrayList<>();
    private boolean isPublishing = false;
    private int selectedCoverImageIndex = 0; // Index of cover image

    private final Map<File, Identifier> imageTextures = new HashMap<>();
    private final List<File> failedImages = new ArrayList<>();

    // Tag management
    private final Set<String> tagBadges = new LinkedHashSet<>();

    // Fields to preserve user input during scrolling
    private String currentNameFieldText = "";
    private String currentDescriptionFieldText = "";
    private String currentTagsFieldText = "";
    private String currentYoutubeFieldText = "";
    private String currentMediaFireFieldText = "";

    // Focus preservation
    private String focusedFieldType = ""; // Track which field type was focused
    private int focusedFieldCursorPosition = 0; // Track cursor position in focused field

    // Scrollbar-related fields
    private int scrollOffset = 0;
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

    // Validation and formatting
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_TAGS = 5;
    private static final int MAX_IMAGES = 5;
    private static final int MAX_LITEMATIC_FILES = 5;
    private static final Pattern URL_PATTERN = Pattern.compile("^https://.*");

    // URL validation feedback
    private String youtubeUrlError = "";
    private String mediaFireUrlError = "";
    private String tagError = "";

    public PublishScreen(Screen parentScreen, File litematicFile) {
        super(Text.literal("Publish Schematic"));
        this.parentScreen = parentScreen;
        this.litematicFile = litematicFile;

        selectedLitematicFiles.add(litematicFile);
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
        int currentY = baseY; // Moved up by 20 pixels (was baseY + 20)
        int fieldWidth = 400;
        int fieldHeight = 20;

        // Back button (always visible at top, outside scroll area)
        this.addDrawableChild(ButtonWidget.builder(Text.literal("←"), button ->
            MinecraftClient.getInstance().setScreen(parentScreen)
        ).dimensions(10, 10, 20, 20).build());

        // === NAME FIELD ===
        currentY += 20;

        // Name field (pre-filled with filename without extension)
        String defaultName = litematicFile.getName();
        if (defaultName.toLowerCase().endsWith(".litematic")) {
            defaultName = defaultName.substring(0, defaultName.length() - 10);
        }

        nameField = new TextFieldWidget(
                this.textRenderer,
                centerX - fieldWidth / 2,
                currentY,
                fieldWidth,
                fieldHeight,
                Text.literal("")
        );
        nameField.setMaxLength(MAX_NAME_LENGTH);
        // Use saved text or default value
        if (!currentNameFieldText.isEmpty()) {
            nameField.setText(currentNameFieldText);
        } else {
            nameField.setText(defaultName);
        }
        nameField.setChangedListener(text -> currentNameFieldText = text);
        this.addSelectableChild(nameField);
        currentY += 40;

        // === DESCRIPTION FIELD (MULTILINE) ===
        int descriptionHeight = 60; // Initial height for multiline
        descriptionField = new MultilineTextFieldWidget(
                this.textRenderer,
                centerX - fieldWidth / 2,
                currentY,
                fieldWidth,
                descriptionHeight, // Use larger height for multiline
                Text.literal("")
        );
        descriptionField.setMaxLength(MAX_DESCRIPTION_LENGTH);
        if (!currentDescriptionFieldText.isEmpty()) {
            descriptionField.setText(currentDescriptionFieldText);
        }
        descriptionField.setChangedListener(text -> currentDescriptionFieldText = text);
        // Set callback to refresh layout when description field height changes
        descriptionField.setOnHeightChanged(() -> this.init());
        this.addSelectableChild(descriptionField);
        // Use the actual height of the description field (which may have grown due to multiple lines)
        currentY += descriptionField.getHeight() + 20; // Adjust spacing based on actual field height

        // === TAGS FIELD ===
        tagsField = new TextFieldWidget(
                this.textRenderer,
                centerX - fieldWidth / 2,
                currentY,
                fieldWidth - 80, // Make room for Add Tag button
                fieldHeight,
                Text.literal("")
        );
        tagsField.setMaxLength(50);
        if (!currentTagsFieldText.isEmpty()) {
            tagsField.setText(currentTagsFieldText);
        }
        tagsField.setChangedListener(text -> {
            currentTagsFieldText = text;
            tagError = ""; // Clear error when typing
        });
        this.addSelectableChild(tagsField);

        // Add Tag button
        ButtonWidget addTagButton = ButtonWidget.builder(
                Text.literal("Add Tag"),
                button -> addTagFromField())
                .dimensions(centerX + fieldWidth / 2 - 75, currentY, 75, 20)
                .build();
        this.addDrawableChild(addTagButton);

        // Add more space for tags display area and to lower subsequent fields
        currentY += 80; // Increased from 60 to 80 to lower subsequent fields

        // === EXTERNAL DOWNLOAD LINK FIELD ===
        mediaFireField = new TextFieldWidget(
                this.textRenderer,
                centerX - fieldWidth / 2,
                currentY,
                fieldWidth,
                fieldHeight,
                Text.literal("")
        );
        mediaFireField.setMaxLength(500);
        if (!currentMediaFireFieldText.isEmpty()) {
            mediaFireField.setText(currentMediaFireFieldText);
        }
        mediaFireField.setChangedListener(text -> {
            currentMediaFireFieldText = text;
            validateMediaFireUrl(text);
        });
        this.addSelectableChild(mediaFireField);
        currentY += 40;

        // === VIDEO LINK FIELD ===
        youtubeField = new TextFieldWidget(
                this.textRenderer,
                centerX - fieldWidth / 2,
                currentY,
                fieldWidth,
                fieldHeight,
                Text.literal("")
        );
        youtubeField.setMaxLength(500);
        if (!currentYoutubeFieldText.isEmpty()) {
            youtubeField.setText(currentYoutubeFieldText);
        }
        youtubeField.setChangedListener(text -> {
            currentYoutubeFieldText = text;
            validateYoutubeUrl(text);
        });
        this.addSelectableChild(youtubeField);
        currentY += 50;

        // === IMAGE SELECTION ===
        ButtonWidget selectImagesButton = ButtonWidget.builder(
                Text.literal("Select Images (" + selectedImages.size() + "/" + MAX_IMAGES + ")"),
                button -> openImageSelector())
                .dimensions(centerX - 100, currentY, 200, 20)
                .build();
        this.addDrawableChild(selectImagesButton);
        currentY += 30;

        // Image preview area (if images selected) - dynamic spacing based on number of rows
        if (!selectedImages.isEmpty()) {
            int previewsPerRow = 4;
            int imageRows = (selectedImages.size() + previewsPerRow - 1) / previewsPerRow; // Ceiling division
            int totalImageHeight = imageRows * (80 + 30) + 10; // 30px initial spacing + rows * (image height + spacing)
            currentY += totalImageHeight;
        }

        // === LITEMATIC FILES SELECTION ===
        ButtonWidget selectLitematicButton = ButtonWidget.builder(
                Text.literal("Select Litematic Files (" + selectedLitematicFiles.size() + "/" + MAX_LITEMATIC_FILES + ")"),
                button -> openLitematicSelector())
                .dimensions(centerX - 100, currentY, 200, 20)
                .build();
        this.addDrawableChild(selectLitematicButton);
        currentY += 40;

        // === ACTION BUTTONS ===
        // Preview button
        ButtonWidget previewButton = ButtonWidget.builder(
                Text.literal("Preview Post"),
                button -> showPreview())
                .dimensions(centerX - 150, currentY, 95, 20)
                .build();
        this.addDrawableChild(previewButton);

        // Publish button
        publishButton = ButtonWidget.builder(
                Text.literal(isPublishing ? "Publishing..." : "Publish"),
                button -> publishSchematic())
                .dimensions(centerX - 47, currentY, 95, 20)
                .build();
        publishButton.active = !isPublishing && isFormValid();
        this.addDrawableChild(publishButton);

        // Cancel button
        ButtonWidget cancelButton = ButtonWidget.builder(
                Text.literal("Cancel"),
                button -> MinecraftClient.getInstance().setScreen(parentScreen))
                .dimensions(centerX + 55, currentY, 95, 20)
                .build();
        this.addDrawableChild(cancelButton);

        // Calculate total content height
        totalContentHeight = currentY + 50 - baseY;

        // Restore focus to the previously focused field
        restoreFocus();
    }

    private void restoreFocus() {
        // Use a delayed approach to ensure focus is restored after UI is fully initialized
        MinecraftClient.getInstance().execute(() -> {
            // Restore focus based on the saved focused field type
            switch (focusedFieldType) {
                case "name":
                    if (nameField != null) {
                        this.setFocused(nameField);
                        nameField.setFocused(true);
                        if (focusedFieldCursorPosition >= 0 && focusedFieldCursorPosition <= nameField.getText().length()) {
                            nameField.setCursor(focusedFieldCursorPosition, false);
                        }
                    }
                    break;
                case "description":
                    if (descriptionField != null) {
                        this.setFocused(descriptionField);
                        descriptionField.setFocused(true);
                        if (focusedFieldCursorPosition >= 0 && focusedFieldCursorPosition <= descriptionField.getText().length()) {
                            descriptionField.setCursor(focusedFieldCursorPosition, false);
                        }
                    }
                    break;
                case "tags":
                    if (tagsField != null) {
                        this.setFocused(tagsField);
                        tagsField.setFocused(true);
                        if (focusedFieldCursorPosition >= 0 && focusedFieldCursorPosition <= tagsField.getText().length()) {
                            tagsField.setCursor(focusedFieldCursorPosition, false);
                        }
                    }
                    break;
                case "youtube":
                    if (youtubeField != null) {
                        this.setFocused(youtubeField);
                        youtubeField.setFocused(true);
                        if (focusedFieldCursorPosition >= 0 && focusedFieldCursorPosition <= youtubeField.getText().length()) {
                            youtubeField.setCursor(focusedFieldCursorPosition, false);
                        }
                    }
                    break;
                case "mediafire":
                    if (mediaFireField != null) {
                        this.setFocused(mediaFireField);
                        mediaFireField.setFocused(true);
                        if (focusedFieldCursorPosition >= 0 && focusedFieldCursorPosition <= mediaFireField.getText().length()) {
                            mediaFireField.setCursor(focusedFieldCursorPosition, false);
                        }
                    }
                    break;
            }
        });
    }

    // Create a simple multiline text field widget
    private static class MultilineTextFieldWidget extends TextFieldWidget {
        private int lineCount = 1;
        private final int baseHeight;
        private final int lineHeight = 12;
        private final net.minecraft.client.font.TextRenderer textRenderer;
        private Runnable onHeightChanged; // Callback for when height changes

        public MultilineTextFieldWidget(net.minecraft.client.font.TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
            super(textRenderer, x, y, width, height, text);
            this.baseHeight = height;
            this.textRenderer = textRenderer;
        }

        public void setOnHeightChanged(Runnable callback) {
            this.onHeightChanged = callback;
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
            int key = input.key();

            // Insert newline on Enter / KP_Enter
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                String currentText = this.getText();
                int cursorPos = this.getCursor();
                String newText = currentText.substring(0, cursorPos) + "\n" + currentText.substring(cursorPos);
                this.setText(newText);
                this.setCursor(cursorPos + 1, false);
                return true;
            }

            // Smart backspace handling for empty lines
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                String currentText = this.getText();
                int cursorPos = this.getCursor();

                if (cursorPos > 0 && currentText.charAt(cursorPos - 1) == '\n') {
                    String beforeCursor = currentText.substring(0, cursorPos);
                    String afterCursor = currentText.substring(cursorPos);

                    int currentLineStart = beforeCursor.lastIndexOf('\n') + 1;
                    int nextLineEnd = afterCursor.indexOf('\n');
                    if (nextLineEnd == -1) nextLineEnd = afterCursor.length();

                    String currentLineContent = currentText.substring(currentLineStart, cursorPos + nextLineEnd);

                    if (currentLineContent.trim().isEmpty() || currentLineContent.equals("\n")) {
                        String newText = beforeCursor.substring(0, beforeCursor.length() - 1) + afterCursor;
                        this.setText(newText);
                        this.setCursor(cursorPos - 1, false);
                        return true;
                    }
                }
            }

            return super.keyPressed(input);
        }

        @Override
        public void setText(String text) {
            int oldHeight = this.getHeight();
            super.setText(text);
            updateHeight();

            // Trigger layout refresh if height changed
            if (this.getHeight() != oldHeight && onHeightChanged != null) {
                onHeightChanged.run();
            }
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            // Draw the field background and border like the parent
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();

            // Draw background - always black like other text fields
            int backgroundColor = 0xFF000000; // Always black, not dependent on focus
            context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFFA0A0A0); // Border
            context.fill(x, y, x + width, y + height, backgroundColor); // Background

            // Split text into lines and render each line
            String text = this.getText();
            String[] lines = text.split("\n", -1); // -1 to keep empty trailing strings

            int textX = x + 4; // Padding from left edge
            int textY = y + 4; // Padding from top edge
            int textColor = this.active ? 0xFFE0E0E0 : 0xFF707070; // Use 'active' instead of 'isEditable()'

            // Render each line
            for (int i = 0; i < lines.length; i++) {
                int lineY = textY + (i * lineHeight);
                if (lineY + lineHeight > y + height) break; // Don't render lines outside the field

                String line = lines[i];

                // Handle cursor rendering
                if (this.isFocused()) {
                    int cursorPos = this.getCursor();
                    int lineStartPos = 0;

                    // Calculate where this line starts in the full text
                    for (int j = 0; j < i; j++) {
                        lineStartPos += lines[j].length() + 1; // +1 for the newline character
                    }

                    // Check if cursor is on this line
                    int lineEndPos = lineStartPos + line.length();
                    if (cursorPos >= lineStartPos && cursorPos <= lineEndPos) {
                        int cursorInLine = cursorPos - lineStartPos;
                        String beforeCursor = line.substring(0, Math.min(cursorInLine, line.length()));
                        int cursorX = textX + this.textRenderer.getWidth(beforeCursor);

                        // Draw cursor
                        context.fill(cursorX, lineY, cursorX + 1, lineY + lineHeight, 0xFFD0D0D0);
                    }
                }

                // Render the line text
                context.drawTextWithShadow(this.textRenderer, line, textX, lineY, textColor);
            }

            // Draw placeholder text if empty and not focused
            if (text.isEmpty() && !this.isFocused()) {
                context.drawTextWithShadow(this.textRenderer, "Enter description...", textX, textY, 0xFF808080);
            }
        }

        private void updateHeight() {
            String text = this.getText();
            lineCount = Math.max(1, text.split("\n", -1).length);
            int newHeight = Math.max(baseHeight, lineCount * lineHeight + 8); // 8px padding (4px top + 4px bottom)
            this.setHeight(newHeight);
        }
    }

    private void updateScrollbarDimensions() {
        // Define content area for scrolling
        scrollAreaX = 50;
        scrollAreaY = 50;
        scrollAreaWidth = this.width - 100;
        scrollAreaHeight = this.height - 100;

        // Don't override totalContentHeight if it's already been calculated properly in init()
        // Only set a default if it hasn't been calculated yet
        if (totalContentHeight == 0) {
            totalContentHeight = 600; // Fallback approximate height
        }

        // Reset scroll offset if it's now out of bounds
        if (totalContentHeight <= scrollAreaHeight) {
            scrollOffset = 0;
        } else {
            scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset));
        }
    }

    private void saveCurrentTextFieldValues() {
        if (nameField != null) currentNameFieldText = nameField.getText();
        if (descriptionField != null) currentDescriptionFieldText = descriptionField.getText();
        if (tagsField != null) currentTagsFieldText = tagsField.getText();
        if (youtubeField != null) currentYoutubeFieldText = youtubeField.getText();
        if (mediaFireField != null) currentMediaFireFieldText = mediaFireField.getText();

        // Save which field was focused
        focusedFieldType = "";
        if (nameField != null && nameField.isFocused()) {
            focusedFieldType = "name";
        } else if (descriptionField != null && descriptionField.isFocused()) {
            focusedFieldType = "description";
        } else if (tagsField != null && tagsField.isFocused()) {
            focusedFieldType = "tags";
        } else if (youtubeField != null && youtubeField.isFocused()) {
            focusedFieldType = "youtube";
        } else if (mediaFireField != null && mediaFireField.isFocused()) {
            focusedFieldType = "mediafire";
        }

        // Save cursor position
        focusedFieldCursorPosition = 0;
        if (nameField != null && nameField.isFocused()) {
            focusedFieldCursorPosition = nameField.getCursor();
        } else if (descriptionField != null && descriptionField.isFocused()) {
            focusedFieldCursorPosition = descriptionField.getCursor();
        } else if (tagsField != null && tagsField.isFocused()) {
            focusedFieldCursorPosition = tagsField.getCursor();
        } else if (youtubeField != null && youtubeField.isFocused()) {
            focusedFieldCursorPosition = youtubeField.getCursor();
        } else if (mediaFireField != null && mediaFireField.isFocused()) {
            focusedFieldCursorPosition = mediaFireField.getCursor();
        }
    }

    private void validateYoutubeUrl(String url) {
        if (url.trim().isEmpty()) {
            youtubeUrlError = "";
        } else if (!URL_PATTERN.matcher(url.trim()).matches()) {
            youtubeUrlError = "Must be a valid HTTPS URL";
        } else {
            youtubeUrlError = "";
        }
    }

    private void validateMediaFireUrl(String url) {
        if (url.trim().isEmpty()) {
            mediaFireUrlError = "";
        } else if (!URL_PATTERN.matcher(url.trim()).matches()) {
            mediaFireUrlError = "Must be a valid HTTPS URL";
        } else {
            mediaFireUrlError = "";
        }
    }

    private void addTagFromField() {
        String tag = tagsField.getText().trim();

        // Clear previous error
        tagError = "";

        if (tag.isEmpty()) {
            return;
        }

        // Check tag length - must be 12 characters or less
        if (tag.length() > 12) {
            tagError = "Tag must be 12 characters or less";
            ToastManager.addToast("Tag must be 12 characters or less", true);
            return; // Don't clear the text field
        }

        // Check if already at max tags - show toast and keep inline error
        if (tagBadges.size() >= MAX_TAGS) {
            tagError = "Maximum " + MAX_TAGS + " tags allowed";
            ToastManager.addToast("Maximum " + MAX_TAGS + " tags allowed", true);
            return; // Don't clear the text field
        }

        // Check if tag already exists (case-insensitive) - show toast and keep inline error
        if (tagBadges.stream().anyMatch(existing -> existing.equalsIgnoreCase(tag))) {
            tagError = "Tag already exists";
            ToastManager.addToast("Tag already exists", true);
            return; // Don't clear the text field
        }

        // Format tag: capitalize first letter
        String formattedTag = formatTag(tag);

        tagBadges.add(formattedTag);
        tagsField.setText("");
        currentTagsFieldText = "";

        // Refresh the UI to show the new tag badge
        this.init();
    }

    private String formatTag(String tag) {
        if (tag.isEmpty()) {
            return tag;
        }
        // Split by spaces and capitalize first letter of each word
        String[] words = tag.toLowerCase().split("\\s+");
        StringBuilder formattedTag = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                // Capitalize first letter, keep rest lowercase
                formattedTag.append(word.substring(0, 1).toUpperCase());
                if (word.length() > 1) {
                    formattedTag.append(word.substring(1));
                }
                // Add space between words (except for last word)
                if (i < words.length - 1) {
                    formattedTag.append(" ");
                }
            }
        }

        return formattedTag.toString();
    }

    private boolean isFormValid() {
        return !nameField.getText().trim().isEmpty() &&          // Title is required
               !selectedImages.isEmpty() &&                      // At least one image required
               (litematicFile != null || !selectedLitematicFiles.isEmpty()) && // Original file OR additional files required
               youtubeUrlError.isEmpty() &&                      // If YouTube URL provided, must be valid
               mediaFireUrlError.isEmpty() &&                    // If MediaFire URL provided, must be valid
               tagError.isEmpty();                                // If tags provided, must be valid
    }

    private void openImageSelector() {
        try {
            // Try using the native system file chooser first
            if (tryNativeImageFileChooser()) {
                return;
            }

            // If native fails, show error message
            ToastManager.addToast("Cannot open file browser in this environment.", true);
        } catch (Exception e) {
            System.err.println("Failed to open file browser: " + e.getMessage());
            ToastManager.addToast("Failed to open file browser: " + e.getMessage(), true);
        }
    }

    private void openLitematicSelector() {
        try {
            // Try using the native system file chooser first
            if (tryNativeLitematicFileChooser()) {
                return;
            }

            // If native fails, show error message
            ToastManager.addToast("Cannot open file browser in this environment.", true);
        } catch (Exception e) {
            System.err.println("Failed to open file browser: " + e.getMessage());
            ToastManager.addToast("Failed to open file browser: " + e.getMessage(), true);
        }
    }

    private boolean tryNativeImageFileChooser() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String currentPath = SettingsManager.getImagesPath();

            System.out.println("Opening image selector at: " + currentPath);

            ProcessBuilder pb;
            Process process = null;

            if (os.contains("win")) {
                // Windows - use PowerShell for multi-selection
                String script = String.format(
                        "Add-Type -AssemblyName System.Windows.Forms; " +
                                "$f = New-Object System.Windows.Forms.OpenFileDialog; " +
                                "$f.Title = 'Select Image Files'; " +
                                "$f.InitialDirectory = '%s'; " +
                                "$f.Filter = 'Image files (*.png;*.jpg;*.jpeg;*.gif;*.bmp)|*.png;*.jpg;*.jpeg;*.gif;*.bmp|All files (*.*)|*.*'; " +
                                "$f.Multiselect = $true; " +
                                "if ($f.ShowDialog() -eq 'OK') { $f.FileNames | ForEach-Object { Write-Output $_ } }",
                        currentPath.replace("'", "''")
                );

                pb = new ProcessBuilder("powershell.exe", "-Command", script);
            } else if (os.contains("mac")) {
                // macOS - modified to output each file on a separate line
                String script = String.format(
                        "set fileList to choose file with prompt \"Select Image Files\" default location POSIX file \"%s\" " +
                                "of type {\"png\", \"jpg\", \"jpeg\", \"gif\", \"bmp\"} with multiple selections allowed\n" +
                                "repeat with aFile in fileList\n" +
                                "    set filePath to POSIX path of aFile\n" +
                                "    log filePath\n" +
                                "end repeat",
                        currentPath.replace("\"", "\\\"")
                );

                pb = new ProcessBuilder("osascript", "-e", script);
            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux - improved with better error handling and environment checks
                String[] tools = {"zenity", "kdialog", "yad"};

                pb = null;
                for (String tool : tools) {
                    if (isCommandAvailable(tool)) {
                        try {
                            pb = createLinuxImageFileDialog(tool, currentPath);
                            if (pb != null) {
                                // Test if the tool can run without X11 errors
                                if (testLinuxDialogTool(tool)) {
                                    break;
                                } else {
                                    System.out.println("Tool " + tool + " failed X11 test, trying next...");
                                    pb = null;
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Tool " + tool + " failed: " + e.getMessage());
                            pb = null;
                        }
                    }
                }

                if (pb == null) {
                    System.err.println("No working file dialog tools found on Linux");
                    return false;
                }
            } else {
                return false;
            }

            // Start the process with proper resource management
            try {
                process = pb.start();

                // Set a timeout to prevent hanging
                boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    ToastManager.addToast("File chooser timed out", true);
                    return false;
                }

                // Clear previous selections and read new ones
                selectedImages.clear();
                int rejectedCount = 0;

                // Read the output with proper resource management
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()));
                     java.io.BufferedReader errorReader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getErrorStream()))) {

                    String line;
                    System.out.println("Reading file selections:");

                    // Check for errors first
                    String errorLine;
                    StringBuilder errors = new StringBuilder();
                    while ((errorLine = errorReader.readLine()) != null) {
                        errors.append(errorLine).append("\n");
                    }

                    if (errors.length() > 0) {
                        System.err.println("File chooser errors: " + errors.toString());
                        // Don't fail completely on errors, as some tools produce warnings
                    }

                    // Read output even if there were errors
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Read line: " + line); // Debug

                        String filePath = line.trim();
                        if (filePath.isEmpty()) continue;

                        // Handle macOS format
                        if (os.contains("mac") && filePath.startsWith("alias ")) {
                            filePath = filePath.substring(6);
                        }

                        // For Linux with zenity, it might return paths separated by '|'
                        if (os.contains("nix") || os.contains("nux")) {
                            if (filePath.contains("|")) {
                                String[] paths = filePath.split("\\|");
                                for (String path : paths) {
                                    if (!addImageFile(new File(path.trim()))) {
                                        rejectedCount++;
                                    }
                                }
                                continue;
                            }
                        }

                        // Normal single file handling
                        if (!addImageFile(new File(filePath))) {
                            rejectedCount++;
                        }
                    }

                    int exitCode = process.exitValue();
                    if (exitCode == 0) {
                        System.out.println("Selected " + selectedImages.size() + " images");

                        // Show appropriate message based on whether files were rejected
                        if (rejectedCount > 0) {
                            ToastManager.addToast("Only first " + MAX_IMAGES + " images selected. " + rejectedCount + " images were not loaded.", true);
                        } else {
                            ToastManager.addToast("Selected " + selectedImages.size() + " image(s)", false);
                        }

                        this.init(); // Refresh UI
                        return true;
                    } else {
                        System.err.println("File chooser exited with code: " + exitCode);
                        if (errors.length() > 0) {
                            ToastManager.addToast("File chooser failed: " + errors.toString().substring(0, Math.min(50, errors.length())), true);
                        }
                    }
                }

                return false;

            } finally {
                // Ensure process cleanup
                if (process != null && process.isAlive()) {
                    try {
                        process.destroyForcibly();
                        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        System.err.println("Error cleaning up image file dialog process: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Native image file chooser error: " + e.getMessage());
            e.printStackTrace();

            // Show user-friendly error message
            String errorMsg = "File chooser failed";
            if (e.getMessage().contains("X Error") || e.getMessage().contains("BadWindow")) {
                errorMsg = "Display error - try again or restart the application";
            }
            ToastManager.addToast(errorMsg, true);
            return false;
        }
    }

    private boolean tryNativeLitematicFileChooser() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String currentPath = SettingsManager.getSchematicsPath();

            System.out.println("Opening litematic selector at: " + currentPath);

            ProcessBuilder pb;
            Process process = null;

            if (os.contains("win")) {
                // Windows - modified to output each file on a separate line
                String script = String.format(
                        "Add-Type -AssemblyName System.Windows.Forms; " +
                                "$f = New-Object System.Windows.Forms.OpenFileDialog; " +
                                "$f.Title = 'Select Litematic Files'; " +
                                "$f.InitialDirectory = '%s'; " +
                                "$f.Filter = 'Litematic files (*.litematic)|*.litematic|All files (*.*)|*.*'; " +
                                "$f.Multiselect = $true; " +
                                "if ($f.ShowDialog() -eq 'OK') { $f.FileNames | ForEach-Object { Write-Output $_ } }",
                        currentPath.replace("'", "''")
                );

                pb = new ProcessBuilder("powershell.exe", "-Command", script);
            } else if (os.contains("mac")) {
                // macOS - modified to output each file on a separate line
                String script = String.format(
                        "set fileList to choose file with prompt \"Select Litematic Files\" default location POSIX file \"%s\" " +
                                "of type {\"litematic\"} with multiple selections allowed\n" +
                                "repeat with aFile in fileList\n" +
                                "    set filePath to POSIX path of aFile\n" +
                                "    log filePath\n" +
                                "end repeat",
                        currentPath.replace("\"", "\\\"")
                );

                pb = new ProcessBuilder("osascript", "-e", script);
            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux - improved with better error handling and environment checks
                String[] tools = {"zenity", "kdialog", "yad"};

                pb = null;
                for (String tool : tools) {
                    if (isCommandAvailable(tool)) {
                        try {
                            pb = createLinuxLitematicFileDialog(tool, currentPath);
                            if (pb != null) {
                                // Test if the tool can run without X11 errors
                                if (testLinuxDialogTool(tool)) {
                                    break;
                                } else {
                                    System.out.println("Tool " + tool + " failed X11 test, trying next...");
                                    pb = null;
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Tool " + tool + " failed: " + e.getMessage());
                            pb = null;
                        }
                    }
                }

                if (pb == null) {
                    System.err.println("No working file dialog tools found on Linux");
                    return false;
                }
            } else {
                return false;
            }

            // Start the process with proper resource management
            try {
                process = pb.start();

                // Set a timeout to prevent hanging
                boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    ToastManager.addToast("Litematic selector timed out", true);
                    return false;
                }

                // Keep the original file and read new ones
                // Create a temporary list to store all selected files
                List<File> tempSelectedFiles = new ArrayList<>();
                tempSelectedFiles.add(litematicFile); // Keep the original file
                int rejectedCount = 0;

                // Read the output with proper resource management
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()));
                     java.io.BufferedReader errorReader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getErrorStream()))) {

                    String line;
                    System.out.println("Reading litematic file selections:");

                    // Check for errors first
                    String errorLine;
                    StringBuilder errors = new StringBuilder();
                    while ((errorLine = errorReader.readLine()) != null) {
                        errors.append(errorLine).append("\n");
                    }

                    if (errors.length() > 0) {
                        System.err.println("Litematic selector errors: " + errors.toString());
                        // Don't fail completely on errors, as some tools produce warnings
                    }

                    // Read all lines from the process
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Read line: " + line); // Debug

                        String filePath = line.trim();
                        if (filePath.isEmpty()) continue;

                        // Handle macOS format
                        if (os.contains("mac") && filePath.startsWith("alias ")) {
                            filePath = filePath.substring(6);
                        }

                        // For Linux with zenity, it might return paths separated by '|'
                        if (os.contains("nix") || os.contains("nux")) {
                            if (filePath.contains("|")) {
                                String[] paths = filePath.split("\\|");
                                for (String path : paths) {
                                    if (!addLitematicFile(tempSelectedFiles, new File(path.trim()))) {
                                        rejectedCount++;
                                    }
                                }
                                continue;
                            }
                        }

                        // Normal single file handling
                        if (!addLitematicFile(tempSelectedFiles, new File(filePath))) {
                            rejectedCount++;
                        }
                    }

                    int exitCode = process.exitValue();
                    if (exitCode == 0) {
                        // Update the main list with the new selection
                        selectedLitematicFiles.clear();
                        selectedLitematicFiles.addAll(tempSelectedFiles);

                        // Show appropriate message based on whether files were rejected
                        if (rejectedCount > 0) {
                            ToastManager.addToast("Only first " + (MAX_LITEMATIC_FILES-1) + " additional files selected. " + rejectedCount + " files were not loaded.", true);
                        } else if (tempSelectedFiles.size() > 1) // More than just the original file
                            ToastManager.addToast("Selected " + (selectedLitematicFiles.size() - 1) + " additional litematic file(s)", false);


                        System.out.println("Selected " + (selectedLitematicFiles.size() - 1) + " additional litematic files");
                        this.init(); // Refresh UI
                        return true;
                    }
                }

                return false;

            } finally {
                // Ensure process cleanup
                if (process != null && process.isAlive()) {
                    try {
                        process.destroyForcibly();
                        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        System.err.println("Error cleaning up litematic file dialog process: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Native litematic file chooser error: " + e.getMessage());
            e.printStackTrace();

            // Show user-friendly error message
            String errorMsg = "Litematic selector failed";
            if (e.getMessage().contains("X Error") || e.getMessage().contains("BadWindow")) {
                errorMsg = "Display error - try again or restart the application";
            }
            ToastManager.addToast(errorMsg, true);
            return false;
        }
    }

    // Helper method to add image file if valid
    private boolean addImageFile(File file) {
        if (file.exists() && isImageFile(file)) {
            if (selectedImages.size() >= MAX_IMAGES) {
                // Don't add the file - limit reached
                return false;
            }
            // More robust duplicate detection - check by absolute path and file size
            for (File existingImage : selectedImages) {
                if (existingImage.getAbsolutePath().equals(file.getAbsolutePath()) ||
                    (existingImage.getName().equals(file.getName()) && existingImage.length() == file.length())) {
                    System.out.println("Image already selected (duplicate by path/size): " + file.getAbsolutePath());
                    return false;
                }
            }
            System.out.println("Adding image: " + file.getAbsolutePath());
            selectedImages.add(file);
            return true;
        }
        return false;
    }

    // Helper method to check if a file is an image by extension
    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") ||
                name.endsWith(".jpg") ||
                name.endsWith(".jpeg") ||
                name.endsWith(".gif") ||
                name.endsWith(".bmp") ||
                name.endsWith(".webp");
    }

    // Helper method to add litematic file if valid
    private boolean addLitematicFile(List<File> fileList, File file) {
        if (file.exists() && file.getName().toLowerCase().endsWith(".litematic") &&
                !fileList.contains(file)) {
            if (fileList.size() >= MAX_LITEMATIC_FILES) {
                // Don't add the file - limit reached
                return false;
            }
            System.out.println("Adding litematic: " + file.getAbsolutePath());
            fileList.add(file);
            return true;
        }
        return false;
    }

    private ProcessBuilder createLinuxImageFileDialog(String tool, String currentPath) {
        switch (tool) {
            case "zenity":
                return new ProcessBuilder("zenity", "--file-selection", "--multiple",
                        "--title=Select Image Files", "--filename=" + currentPath + "/",
                        "--file-filter=Image files | *.png *.jpg *.jpeg *.gif *.bmp");
            case "kdialog":
                return new ProcessBuilder("kdialog", "--getopenmultiplefiles",
                        "--title", "Select Image Files",
                        currentPath,
                        "Image files (*.png *.jpg *.jpeg *.gif *.bmp)");
            case "yad":
                return new ProcessBuilder("yad", "--file", "--multiple",
                        "--title=Select Image Files", "--filename=" + currentPath + "/",
                        "--file-filter=Image files | *.png *.jpg *.jpeg *.gif *.bmp");
            default:
                return null;
        }
    }

    private ProcessBuilder createLinuxLitematicFileDialog(String tool, String currentPath) {
        switch (tool) {
            case "zenity":
                return new ProcessBuilder("zenity", "--file-selection", "--multiple",
                        "--title=Select Litematic Files", "--filename=" + currentPath + "/",
                        "--file-filter=Litematic files | *.litematic");
            case "kdialog":
                return new ProcessBuilder("kdialog", "--getopenmultiplefiles",
                        "--title", "Select Litematic Files",
                        currentPath,
                        "Litematic files (*.litematic)");
            case "yad":
                return new ProcessBuilder("yad", "--file", "--multiple",
                        "--title=Select Litematic Files", "--filename=" + currentPath + "/",
                        "--file-filter=Litematic files | *.litematic");
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

    // Test if a Linux dialog tool can run without X11 errors
    private boolean testLinuxDialogTool(String tool) {
        try {
            ProcessBuilder testPb;

            switch (tool) {
                case "zenity":
                    // Test zenity with --version flag instead of showing a dialog
                    testPb = new ProcessBuilder("zenity", "--version");
                    break;
                case "kdialog":
                    // Test kdialog with --version flag instead of showing a dialog
                    testPb = new ProcessBuilder("kdialog", "--version");
                    break;
                case "yad":
                    // Test yad with --version flag instead of showing a dialog
                    testPb = new ProcessBuilder("yad", "--version");
                    break;
                default:
                    return false;
            }

            // Set environment to avoid X11 issues
            testPb.environment().put("DISPLAY", System.getenv("DISPLAY"));

            // Set a very short timeout for the test
            Process testProcess = testPb.start();
            boolean finished = testProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                testProcess.destroyForcibly();
                return false;
            }

            // Check exit code and stderr for X11 errors
            int exitCode = testProcess.exitValue();

            // Read stderr to check for X11 errors
            try (java.io.BufferedReader errorReader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(testProcess.getErrorStream()))) {

                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    String lowerError = errorLine.toLowerCase();
                    // Check for common X11 error patterns
                    if (lowerError.contains("x error") ||
                        lowerError.contains("badwindow") ||
                        lowerError.contains("cannot open display") ||
                        lowerError.contains("no display") ||
                        lowerError.contains("connection refused")) {
                        System.err.println("X11 error detected in " + tool + ": " + errorLine);
                        return false;
                    }
                }
            }

            return exitCode == 0;

        } catch (Exception e) {
            System.err.println("Test failed for " + tool + ": " + e.getMessage());
            return false;
        }
    }

    private void showPreview() {
        // Placeholder implementation - will need SchematicPreviewScreen class
        // For now, show a simple message
        ToastManager.addToast("Preview feature coming soon!", false);
    }

    private void publishSchematic() {
        if (isPublishing || !isFormValid()) {
            return;
        }

        // Check if user has an API token
        if (!SettingsManager.hasApiToken()) {
            ToastManager.addToast("Please link your account in settings before publishing", true);
            return;
        }

        isPublishing = true;
        publishButton.setMessage(Text.literal("Publishing..."));
        publishButton.active = false;

        // Create the DTO with proper constructor and field names
        try {
            // === COMPREHENSIVE DEBUG LOGGING ===
            System.out.println("=== PUBLISH SCHEMATIC DEBUG START ===");

            // Debug the original litematic file
            System.out.println("Original litematic file (from constructor): " + litematicFile.getName() + " (" + litematicFile.getAbsolutePath() + ")");

            // Debug selectedLitematicFiles list content
            System.out.println("selectedLitematicFiles list contains " + selectedLitematicFiles.size() + " files:");
            for (int i = 0; i < selectedLitematicFiles.size(); i++) {
                File file = selectedLitematicFiles.get(i);
                System.out.println("  [" + i + "] " + file.getName() + " (" + file.getAbsolutePath() + ")");
                System.out.println("      equals(litematicFile): " + file.equals(litematicFile));
                System.out.println("      same path: " + file.getAbsolutePath().equals(litematicFile.getAbsolutePath()));
            }

            // Debug selectedImages list content
            System.out.println("selectedImages list contains " + selectedImages.size() + " files:");
            for (int i = 0; i < selectedImages.size(); i++) {
                File file = selectedImages.get(i);
                System.out.println("  [" + i + "] " + file.getName() + " (" + file.getAbsolutePath() + ")");
            }

            // Clean up litematic files list to avoid duplicates
            List<File> litematicFilesToSend = new ArrayList<>();
            System.out.println("Starting litematic cleanup process...");

            // Always ensure the original litematic file is included first
            litematicFilesToSend.add(litematicFile);
            System.out.println("Added original litematic file: " + litematicFile.getName());

            // Add any additional litematic files from selectedLitematicFiles (excluding the original)
            System.out.println("Checking selectedLitematicFiles for additional files...");
            for (File file : selectedLitematicFiles) {
                System.out.println("  Checking file: " + file.getName());
                System.out.println("    equals(litematicFile): " + file.equals(litematicFile));
                System.out.println("    already in litematicFilesToSend: " + litematicFilesToSend.contains(file));

                // Skip if it's the original file (already added) or already in the list
                if (!file.equals(litematicFile) && !litematicFilesToSend.contains(file)) {
                    litematicFilesToSend.add(file);
                    System.out.println("    -> ADDED to litematicFilesToSend");
                } else {
                    System.out.println("    -> SKIPPED (duplicate or original)");
                }
            }

            // Clean up images list to avoid potential duplicates
            List<File> imagesToSend = new ArrayList<>();
            System.out.println("Starting images cleanup process...");
            for (File image : selectedImages) {
                System.out.println("  Checking image: " + image.getName());

                // More robust duplicate detection - check by absolute path and file size
                boolean isDuplicate = false;
                for (File existingImage : imagesToSend) {
                    if (existingImage.getAbsolutePath().equals(image.getAbsolutePath()) ||
                        (existingImage.getName().equals(image.getName()) && existingImage.length() == image.length())) {
                        isDuplicate = true;
                        break;
                    }
                }

                System.out.println("    already in imagesToSend: " + isDuplicate);

                if (!isDuplicate) {
                    imagesToSend.add(image);
                    System.out.println("    -> ADDED to imagesToSend");
                } else {
                    System.out.println("    -> SKIPPED (duplicate by path/size)");
                }
            }

            // Final debug output
            System.out.println("FINAL LISTS TO SEND:");
            System.out.println("  - litematicFilesToSend: " + litematicFilesToSend.size() + " files");
            for (int i = 0; i < litematicFilesToSend.size(); i++) {
                System.out.println("    [" + i + "] " + litematicFilesToSend.get(i).getName());
            }
            System.out.println("  - imagesToSend: " + imagesToSend.size() + " files");
            for (int i = 0; i < imagesToSend.size(); i++) {
                System.out.println("    [" + i + "] " + imagesToSend.get(i).getName());
            }
            System.out.println("  - Cover image index: " + selectedCoverImageIndex);
            System.out.println("=== PUBLISH SCHEMATIC DEBUG END ===");

            // Create DTO with the cleaned-up lists
            SchematicCreateDTO dto = new SchematicCreateDTO(
                    nameField.getText().trim(),
                    imagesToSend,
                    litematicFilesToSend
            );

            // Always set description (server requires this field, even if empty)
            String description = descriptionField.getText().trim();
            if (description.isEmpty()) {
                description = "No description provided"; // Default placeholder text
            }
            dto.setDescription(description);

            // Set optional fields using setters
            String mediaFireUrl = mediaFireField.getText().trim();
            String youtubeUrl = youtubeField.getText().trim();

            if (!mediaFireUrl.isEmpty()) {
                dto.setDownloadLinkMediaFire(mediaFireUrl);
            }
            if (!youtubeUrl.isEmpty()) {
                dto.setYoutubeLink(youtubeUrl);
            }
            if (!tagBadges.isEmpty()) {
                dto.setTags(String.join(",", tagBadges));
            }
            dto.setCoverImageIndex(selectedCoverImageIndex);

            // Get API token from settings
            String apiToken = SettingsManager.getApiToken();

            // Call the actual API
            LitematicHttpClient.createSchematic(dto, apiToken, new LitematicHttpClient.CreateSchematicCallback() {
                @Override
                public void onSuccess(JsonObject responseData) {
                    isPublishing = false;
                    publishButton.setMessage(Text.literal("Publish"));
                    publishButton.active = true;

                    // Extract schematic info from response
                    String schematicName = nameField.getText().trim();
                    String schematicId = responseData.has("id") ? responseData.get("id").getAsString() : "unknown";

                    ToastManager.addToast("✅ Successfully published \"" + schematicName + "\"!", false);

                    // Return to parent screen after successful publish
                    MinecraftClient.getInstance().setScreen(parentScreen);
                }

                @Override
                public void onError(String message) {
                    isPublishing = false;
                    publishButton.setMessage(Text.literal("Publish"));
                    publishButton.active = true;

                    // Show detailed error message
                    String errorMsg = "Publishing failed: " + message;
                    ToastManager.addToast(errorMsg, true);

                    // Log the error for debugging
                    System.err.println("Publishing error: " + message);
                }
            });

        } catch (Exception e) {
            isPublishing = false;
            publishButton.setMessage(Text.literal("Publish"));
            publishButton.active = true;

            ToastManager.addToast("Error preparing data for publishing: " + e.getMessage(), true);
            System.err.println("Error preparing schematic data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Simple data class for preview
    @SuppressWarnings("unused")
    public static class PreviewData {
        public String name;
        public String description;
        public List<String> tags;
        public String videoLink;
        public String externalDownloadLink;
        public List<File> images;
        public int coverImageIndex;
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

        // Draw field labels and content with proper positioning
        int centerX = this.width / 2;
        int baseY = scrollAreaY - scrollOffset;
        int currentY = baseY; // Moved up by 20 pixels (was baseY + 20)
        int fieldWidth = 400; // Define fieldWidth with the same value as in init method

        // === NAME FIELD LABEL ===
        currentY += 20; // Match the field positioning
        context.drawTextWithShadow(this.textRenderer, Text.literal("Name"), centerX - 200, currentY - 15, 0xFFFFFFFF);
        currentY += 40;

        // === DESCRIPTION FIELD LABEL ===
        context.drawTextWithShadow(this.textRenderer, Text.literal("Description"), centerX - 200, currentY - 15, 0xFFFFFFFF);
        // Account for the actual description field height instead of using fixed spacing
        currentY += descriptionField.getHeight() + 20; // Use actual field height plus spacing

        // === TAGS FIELD LABEL ===
        context.drawTextWithShadow(this.textRenderer, Text.literal("Tags"), centerX - 200, currentY - 15, 0xFFFFFFFF);
        // Display tag error if any
        if (!tagError.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(tagError), centerX - 200, currentY + 5, 0xFFFF5555);
        }
        currentY += 30; // Space for tags field

        // Draw tag badges - now properly positioned based on actual description field height
        int badgeY = currentY;
        if (!tagBadges.isEmpty()) {
            int badgeX = centerX - 200;
            for (String tag : tagBadges) {
                int badgeWidth = this.textRenderer.getWidth(tag) + 16;

                // Draw badge background
                context.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + 16, 0xFF4CAF50);

                // Draw tag text
                context.drawTextWithShadow(this.textRenderer, Text.literal(tag), badgeX + 4, badgeY + 4, 0xFFFFFFFF);

                // Draw X button
                boolean isXHovered = mouseX >= badgeX + badgeWidth - 12 && mouseX <= badgeX + badgeWidth - 4 &&
                                   mouseY >= badgeY + 4 && mouseY <= badgeY + 12;
                int xColor = isXHovered ? 0xFFFF5555 : 0xFFFFFFFF;
                context.drawTextWithShadow(this.textRenderer, Text.literal("×"), badgeX + badgeWidth - 10, badgeY + 4, xColor);

                badgeX += badgeWidth + 4;
                if (badgeX > centerX + 150) {
                    badgeX = centerX - 200;
                    badgeY += 20;
                }
            }
            // Update currentY based on where the badges end
            currentY = badgeY + 30;
        } else {
            currentY += 30; // Extra space when no tags
        }

        // === EXTERNAL DOWNLOAD LINK FIELD LABEL ===
        int mediaFireLabelY = mediaFireField.getY() - 15;
        context.drawTextWithShadow(this.textRenderer, Text.literal("External Download Link"),
                mediaFireField.getX(), mediaFireLabelY, 0xFFFFFFFF);
        if (!mediaFireUrlError.isEmpty()) {
            int errorX = mediaFireField.getX() + 270;
            context.drawTextWithShadow(this.textRenderer, Text.literal(mediaFireUrlError),
                    errorX, mediaFireLabelY, 0xFFFF5555);
        }
        currentY += 40;

        // === VIDEO LINK FIELD LABEL ===
        int youtubeLabelY = youtubeField.getY() - 15;
        context.drawTextWithShadow(this.textRenderer, Text.literal("Video Link"),
                youtubeField.getX(), youtubeLabelY, 0xFFFFFFFF);
        if (!youtubeUrlError.isEmpty()) {
            int errorX = youtubeField.getX() + 270;
            context.drawTextWithShadow(this.textRenderer, Text.literal(youtubeUrlError),
                    errorX, youtubeLabelY, 0xFFFF5555);
        }
        currentY += 50;

        // Draw image previews
        if (!selectedImages.isEmpty()) {
            int previewSize = 80;
            int previewsPerRow = 4;
            int previewX = centerX - (previewsPerRow * (previewSize + 10)) / 2;

            // Position images below the "Select Images" button with proper spacing
            int imageStartY = currentY + 50; // Add 50px spacing below the button

            for (int i = 0; i < selectedImages.size(); i++) {
                File imageFile = selectedImages.get(i);
                int x = previewX + (i % previewsPerRow) * (previewSize + 10);
                int y = imageStartY + (i / previewsPerRow) * (previewSize + 30);

                // Draw preview border
                boolean isCover = i == selectedCoverImageIndex;
                int borderColor = isCover ? 0xFF4CAF50 : 0xFF666666;
                context.fill(x - 2, y - 2, x + previewSize + 2, y + previewSize + 2, borderColor);
                context.fill(x, y, x + previewSize, y + previewSize, 0xFF333333);

                // Get or load texture
                Identifier textureId = getImageTexture(imageFile);

                if (textureId != null) {
                    // Use correct drawTexture method signature with RenderPipelines
                    context.drawTexture(
                            RenderPipelines.GUI_TEXTURED,
                            textureId,
                            x, y,
                            0, 0,
                            previewSize, previewSize,
                            previewSize, previewSize
                    );
                } else if (failedImages.contains(imageFile)) {
                    context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("ERROR"),
                            x + previewSize/2, y + previewSize/2, 0xFFFF0000);
                } else {
                    context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("LOADING"),
                            x + previewSize/2, y + previewSize/2, 0xFFFFFFFF);
                }

                // Draw cover label
                if (isCover) {
                    context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("COVER"),
                            x + previewSize/2, y + previewSize + 5, 0xFF4CAF50);
                }

                // Draw filename
                String filename = imageFile.getName();
                if (filename.length() > 12) {
                    filename = filename.substring(0, 9) + "...";
                }
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(filename),
                        x + previewSize/2, y + previewSize + 15, 0xFFCCCCCC);
            }

            // Calculate how much space the images actually take up
            int imageRows = (selectedImages.size() + previewsPerRow - 1) / previewsPerRow; // Ceiling division
            int totalImageHeight = imageRows * (previewSize + 30) + 20; // +20 for bottom margin
            currentY += totalImageHeight;
        }

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
        nameField.render(context, mouseX, mouseY, delta);
        descriptionField.render(context, mouseX, mouseY, delta);
        tagsField.render(context, mouseX, mouseY, delta);
        youtubeField.render(context, mouseX, mouseY, delta);
        mediaFireField.render(context, mouseX, mouseY, delta);

        // Draw toast notifications at the end
        ToastManager.render(context, this.width);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (this.tagsField != null && this.tagsField.isFocused()) {
            int key = input.key();
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                addTagFromField();
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Handle tag badge removal - use same positioning logic as rendering
        if (button == 0) {
            int centerX = this.width / 2;
            int baseY = scrollAreaY - scrollOffset;
            int currentY = baseY; // Moved up by 20 pixels (was baseY + 20)

            // Calculate exact position where tags are rendered (match render method exactly)
            currentY += 20; // Match field positioning
            currentY += 40; // Name field spacing
            // Use actual description field height instead of hardcoded spacing
            currentY += descriptionField.getHeight() + 20; // Account for actual description field height
            currentY += 30; // Tags field spacing

            // Check tag badge clicks using exact same positioning as render method
            if (!tagBadges.isEmpty()) {
                int badgeX = centerX - 200;
                int badgeY = currentY;

                for (String tag : new ArrayList<>(tagBadges)) {
                    int badgeWidth = this.textRenderer.getWidth(tag) + 16;

                    // Check if X button was clicked
                    if (mouseX >= badgeX + badgeWidth - 12 && mouseX <= badgeX + badgeWidth - 4 &&
                            mouseY >= badgeY + 4 && mouseY <= badgeY + 12) {
                        tagBadges.remove(tag);
                        ToastManager.addToast("Tag \"" + tag + "\" removed", false);
                        this.init(); // Refresh UI to update badge display
                        return true;
                    }

                    badgeX += badgeWidth + 4;
                    if (badgeX > centerX + 150) {
                        badgeX = centerX - 200;
                        badgeY += 20;
                    }
                }
            }

            // Handle image cover selection - need to update currentY calculation for proper positioning
            if (!selectedImages.isEmpty()) {
                int previewSize = 80;
                int previewsPerRow = 4;
                int previewX = centerX - (previewsPerRow * (previewSize + 10)) / 2;

                // Use the SAME calculation as in render method
                int imageStartY = currentY + 170; // Match the exact +50 from render method

                for (int i = 0; i < selectedImages.size(); i++) {
                    int x = previewX + (i % previewsPerRow) * (previewSize + 10);
                    int y = imageStartY + (i / previewsPerRow) * (previewSize + 30);

                    if (mouseX >= x && mouseX <= x + previewSize &&
                            mouseY >= y && mouseY <= y + previewSize) {
                        selectedCoverImageIndex = i;
                        ToastManager.addToast("Set image " + (i + 1) + " as cover", false);
                        return true;
                    }
                }
            }
        }

        // Handle scrollbar interaction - copied from DetailScreen
        if (button == 0 && totalContentHeight > scrollAreaHeight) { // Left click
            // Check if click is on scroll bar handle
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {
                isScrolling = true;
                lastMouseY = (int) mouseY;
                return true;
            }

            // Check if click is in scroll area but not on the handle (jump scroll)
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {
                float clickPercent = ((float) mouseY - scrollAreaY) / scrollAreaHeight;
                scrollOffset = (int) (clickPercent * (totalContentHeight - scrollAreaHeight));
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset));
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
            if (mouseY != lastMouseY) {
                // Calculate how far we've dragged as a percentage of the scroll area
                float dragPercentage = (float) (mouseY - lastMouseY) / (scrollAreaHeight - scrollBarHeight);

                // Convert that to a scroll amount
                int scrollAmount = (int) (dragPercentage * (totalContentHeight - scrollAreaHeight));

                // Update scroll position
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset + scrollAmount));

                lastMouseY = (int) mouseY;

                // Reinitialize widgets to update their positions
                this.init();
            }
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (click.button() == 0) {
            isScrolling = false;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (totalContentHeight > scrollAreaHeight) {
            scrollOffset += verticalAmount > 0 ? -20 : 20;
            scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset));
            this.init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private Identifier getImageTexture(File imageFile) {
        // Check if we already have the texture
        if (imageTextures.containsKey(imageFile)) {
            return imageTextures.get(imageFile);
        }

        // Don't try to load again if it failed before
        if (failedImages.contains(imageFile)) {
            return null;
        }

        // Check if file exists
        if (!imageFile.exists()) {
            failedImages.add(imageFile);
            return null;
        }

        try {
            // Generate a unique ID for this texture
            String uniqueId = UUID.randomUUID().toString().replace("-", "");
            Identifier textureId = Identifier.of("minecraft", "textures/dynamic/" + uniqueId);

            // Read the file as bytes first to detect format
            byte[] imageData;
            try (InputStream inputStream = new FileInputStream(imageFile)) {
                imageData = inputStream.readAllBytes();
            }

            // Validate that we have actual image data
            if (imageData.length == 0) {
                System.err.println("Image file is empty: " + imageFile.getName());
                ToastManager.addToast("Image file is empty: " + imageFile.getName(), true);
                failedImages.add(imageFile);
                return null;
            }

            // Check for minimum file size (corrupted images are often very small)
            if (imageData.length < 100) {
                System.err.println("Image data too small, likely corrupted: " + imageData.length + " bytes for " + imageFile.getName());
                ToastManager.addToast("Image file too small (corrupted): " + imageFile.getName(), true);
                return createAndRegisterPlaceholder(textureId, imageFile, "Image data too small (corrupted)");
            }

            // Detect image format from binary signature
            String detectedFormat = detectImageFormat(imageData);
            System.out.println("Detected image format for " + imageFile.getName() + ": " + detectedFormat);

            // Try to read the image and handle potential format exceptions
            NativeImage nativeImage;
            boolean isPlaceholder = false;
            String placeholderReason = "";

            try {
                // Try to convert different formats to PNG first if needed
                byte[] processedImageData = convertImageToPng(imageData, detectedFormat);
                nativeImage = NativeImage.read(new java.io.ByteArrayInputStream(processedImageData));

                // Additional validation: check if image dimensions are reasonable
                if (nativeImage.getWidth() <= 0 || nativeImage.getHeight() <= 0) {
                    System.err.println("Invalid image dimensions for " + imageFile.getName() + ": " + nativeImage.getWidth() + "x" + nativeImage.getHeight());
                    ToastManager.addToast("Invalid dimensions: " + imageFile.getName(), true);
                    nativeImage.close();
                    nativeImage = createPlaceholderImage(80, 80, "Invalid dimensions");
                    isPlaceholder = true;
                    placeholderReason = "Invalid image dimensions";
                } else if (nativeImage.getWidth() > 4096 || nativeImage.getHeight() > 4096) {
                    System.err.println("Image too large for " + imageFile.getName() + ": " + nativeImage.getWidth() + "x" + nativeImage.getHeight());
                    ToastManager.addToast("Image too large: " + imageFile.getName() + " (" + nativeImage.getWidth() + "x" + nativeImage.getHeight() + ")", true);
                    nativeImage.close();
                    nativeImage = createPlaceholderImage(80, 80, "Image too large");
                    isPlaceholder = true;
                    placeholderReason = "Image too large (" + nativeImage.getWidth() + "x" + nativeImage.getHeight() + ")";
                }
            } catch (Exception e) {
                System.err.println("Error loading image " + imageFile.getName() + " (corrupted or unsupported format '" + detectedFormat + "'): " + e.getMessage());
                ToastManager.addToast("Could not load " + detectedFormat + " image: " + imageFile.getName() + " - " + getSimpleErrorMessage(e.getMessage()), true);
                nativeImage = createPlaceholderImage(80, 80, "Image corrupted");
                isPlaceholder = true;
                placeholderReason = "Image corrupted (" + e.getMessage() + ")";
            }

            // Register texture with Minecraft's texture manager
            if (nativeImage != null) {
                MinecraftClient.getInstance().getTextureManager().registerTexture(
                        textureId,
                        new NativeImageBackedTexture(() -> "image_" + imageFile.getName(), nativeImage)
                );

                imageTextures.put(imageFile, textureId);

                if (isPlaceholder) {
                    System.out.println("Placeholder image created for " + imageFile.getName() + " due to: " + placeholderReason + " (" + nativeImage.getWidth() + "x" + nativeImage.getHeight() + ")");
                } else {
                    System.out.println("Successfully loaded image: " + imageFile.getName() + " (" + detectedFormat + "): " + nativeImage.getWidth() + "x" + nativeImage.getHeight());
                }

                return textureId;
            } else {
                failedImages.add(imageFile);
                return null;
            }

        } catch (Exception e) {
            System.err.println("Failed to process image: " + imageFile.getAbsolutePath());
            e.printStackTrace();
            failedImages.add(imageFile);
            return null;
        }
    }

    // Helper method to detect image format from binary signature
    private String detectImageFormat(byte[] imageData) {
        if (imageData.length < 8) {
            return "unknown";
        }

        // PNG signature: 89 50 4E 47 0D 0A 1A 0A
        if (imageData[0] == (byte)0x89 && imageData[1] == 0x50 &&
            imageData[2] == 0x4E && imageData[3] == 0x47) {
            return "png";
        }

        // JPEG signature: FF D8 FF
        if (imageData[0] == (byte)0xFF && imageData[1] == (byte)0xD8 && imageData[2] == (byte)0xFF) {
            return "jpeg";
        }

        // GIF signature: GIF87a or GIF89a
        if (imageData.length >= 6 &&
            imageData[0] == 0x47 && imageData[1] == 0x49 && imageData[2] == 0x46) {
            return "gif";
        }

        // BMP signature: BM
        if (imageData[0] == 0x42 && imageData[1] == 0x4D) {
            return "bmp";
        }

        // WebP signature: RIFF....WEBP
        if (imageData.length >= 12 &&
            imageData[0] == 0x52 && imageData[1] == 0x49 && imageData[2] == 0x46 && imageData[3] == 0x46 &&
            imageData[8] == 0x57 && imageData[9] == 0x45 && imageData[10] == 0x42 && imageData[11] == 0x50) {
            return "webp";
        }

        return "unknown";
    }

    // Helper method to convert different image formats to PNG-compatible format
    private byte[] convertImageToPng(byte[] imageData, String format) throws Exception {
        // If it's already PNG or unknown, try as-is first
        if (format.equals("png") || format.equals("unknown")) {
            return imageData;
        }

        // For non-PNG formats, we'll try to use Java's built-in image processing
        // Special handling for SVG - cannot be converted via standard Java ImageIO
        if (format.equals("svg")) {
            throw new Exception("SVG format is not supported for conversion");
        }

        // Special handling for AVIF and HEIF - not supported by standard Java ImageIO
        if (format.equals("avif") || format.equals("heif")) {
            throw new Exception(format.toUpperCase() + " format is not supported by Java ImageIO");
        }

        try {
            java.awt.image.BufferedImage bufferedImage = null;

            // 1) Try ImageIO auto-detection with an ImageInputStream (good for plugin readers like WebP)
            try (javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(imageData))) {
                java.util.Iterator<javax.imageio.ImageReader> autoReaders = javax.imageio.ImageIO.getImageReaders(iis);
                if (autoReaders.hasNext()) {
                    javax.imageio.ImageReader reader = autoReaders.next();
                    try {
                        reader.setInput(iis, true, true);
                        bufferedImage = reader.read(0);
                    } finally {
                        reader.dispose();
                    }
                }
            } catch (Exception e) {
                System.out.println("Auto-detect ImageIO read failed for " + format + ": " + e.getMessage());
            }

            // 2) Fallback to ImageIO.read convenience method
            if (bufferedImage == null) {
                try {
                    bufferedImage = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageData));
                } catch (Exception e) {
                    System.out.println("Standard ImageIO.read failed for " + format + ": " + e.getMessage());
                }
            }

            // 3) Try explicit readers by format name
            if (bufferedImage == null && !format.equals("unknown")) {
                try {
                    java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReadersByFormatName(format.toUpperCase());
                    if (readers.hasNext()) {
                        javax.imageio.ImageReader reader = readers.next();
                        javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(imageData));
                        reader.setInput(iis);
                        bufferedImage = reader.read(0);
                        reader.dispose();
                        iis.close();
                    }
                } catch (Exception e) {
                    System.out.println("Alternative ImageIO approach failed for " + format + ": " + e.getMessage());
                }
            }

            if (bufferedImage == null) {
                throw new Exception("Could not decode " + format + " image");
            }

            // Convert to PNG format
            java.awt.image.BufferedImage convertedImage;
            if (bufferedImage.getType() != java.awt.image.BufferedImage.TYPE_INT_RGB &&
                bufferedImage.getType() != java.awt.image.BufferedImage.TYPE_INT_ARGB) {
                convertedImage = new java.awt.image.BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2d = convertedImage.createGraphics();
                g2d.drawImage(bufferedImage, 0, 0, null);
                g2d.dispose();
            } else {
                convertedImage = bufferedImage;
            }

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            boolean success = javax.imageio.ImageIO.write(convertedImage, "PNG", baos);
            if (!success) {
                throw new Exception("Failed to write PNG output");
            }

            System.out.println("Successfully converted " + format + " to PNG format");
            return baos.toByteArray();

        } catch (Exception e) {
            System.err.println("Failed to convert " + format + " image: " + e.getMessage());
            // Fallback: try the original data anyway
            return imageData;
        }
    }

    // Helper method to simplify error messages for user display
    private String getSimpleErrorMessage(String fullError) {
        if (fullError.contains("Bad PNG Signature")) {
            return "Corrupted PNG file";
        } else if (fullError.contains("JPEG")) {
            return "JPEG format issue";
        } else if (fullError.contains("format")) {
            return "Unsupported format";
        } else if (fullError.contains("signature") || fullError.contains("Signature")) {
            return "Corrupted file";
        } else {
            // Keep it short for toast display
            return fullError.length() > 30 ? "Image corrupted" : fullError;
        }
    }

    // Helper method to create and register placeholder when image loading fails early
    private Identifier createAndRegisterPlaceholder(Identifier textureId, File imageFile, String reason) {
        try {
            NativeImage nativeImage = createPlaceholderImage(80, 80, reason);
            if (nativeImage != null) {
                // Register the texture with Minecraft's texture manager
                MinecraftClient.getInstance().getTextureManager().registerTexture(
                        textureId,
                        new NativeImageBackedTexture(() -> "placeholder_" + imageFile.getName(), nativeImage)
                );

                imageTextures.put(imageFile, textureId);
                System.out.println("Placeholder image created for " + imageFile.getName() + " due to: " + reason + " (80x80)");
                return textureId;
            }
        } catch (Exception e) {
            System.err.println("Failed to create placeholder for " + imageFile.getName() + ": " + e.getMessage());
        }

        failedImages.add(imageFile);
        return null;
    }

    // Helper method to create a placeholder image when original image loading fails
    private NativeImage createPlaceholderImage(int width, int height, String reason) {
        try {
            NativeImage placeholder = new NativeImage(NativeImage.Format.RGBA, width, height, false);

            // Since we can't easily manipulate pixels without access to setColor methods,
            // we'll create a basic transparent placeholder that won't crash the game
            // The placeholder will appear as a transparent square, which is better than crashing

            return placeholder;
        } catch (Exception e) {
            System.err.println("Failed to create placeholder image: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    @Override
    public void close() {
        // Clean up texture resources when screen is closed
        for (Identifier textureId : imageTextures.values()) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
        }
        imageTextures.clear();
        super.close();
    }


}

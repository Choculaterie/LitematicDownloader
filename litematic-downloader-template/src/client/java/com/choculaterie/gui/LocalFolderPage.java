package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.gui.widget.ConfirmPopup;
import com.choculaterie.gui.widget.CustomButton;
import com.choculaterie.gui.widget.ScrollBar;
import com.choculaterie.gui.widget.CustomTextField;
import com.choculaterie.gui.widget.TextInputPopup;
import com.choculaterie.gui.widget.ToastManager;
import com.choculaterie.network.ChoculaterieNetworkManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LocalFolderPage extends Screen {
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 25;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_PADDING = 2;

    private final Screen parentScreen;
    private CustomButton renameButton;
    private CustomButton deleteButton;
    private ScrollBar scrollBar;
    private TextInputPopup activePopup;
    private ConfirmPopup confirmPopup;

    private File currentDirectory;
    private final File baseDirectory; // The schematic folder - don't allow going above this
    private final List<FileEntry> entries = new ArrayList<>();
    private int scrollOffset = 0;

    // Multi-select support
    private final List<Integer> selectedIndices = new ArrayList<>(); // All selected indices
    private int lastClickedIndex = -1; // For shift+click range selection

    // Drag-and-drop support
    private boolean isDragging = false;
    private int dragStartIndex = -1;
    private double dragStartX = 0;
    private double dragStartY = 0;
    private int dropTargetIndex = -1; // The folder being hovered over during drag
    private File dropTargetBreadcrumb = null; // The breadcrumb folder being hovered over during drag
    private final List<Integer> preClickSelection = new ArrayList<>(); // Store selection before click for drag restoration

    // Quick share button tracking
    private final List<QuickShareButton> quickShareButtons = new ArrayList<>();
    private int uploadingIndex = -1; // Index of file currently being uploaded (-1 = none)
    private ToastManager toastManager;

    // Breadcrumb segments for click detection
    private final List<BreadcrumbSegment> breadcrumbSegments = new ArrayList<>();

    // Undo/Redo support
    private final List<FileAction> undoStack = new ArrayList<>();
    private final List<FileAction> redoStack = new ArrayList<>();
    private static final int MAX_UNDO_HISTORY = 50;
    private final File trashFolder; // Hidden folder for storing deleted files for undo

    // Search support
    private CustomTextField searchField;
    private String searchQuery = "";
    private boolean isSearchActive = false;

    // Keyboard shortcut state tracking
    private boolean wasDeleteKeyPressed = false;
    private boolean wasZKeyPressed = false;
    private boolean wasYKeyPressed = false;
    private boolean wasAKeyPressed = false;

    /**
     * Represents a file operation that can be undone/redone
     */
    private static class FileAction {
        enum Type { MOVE, DELETE, RENAME, CREATE_FOLDER }

        final Type type;
        final List<FileOperation> operations;

        FileAction(Type type, List<FileOperation> operations) {
            this.type = type;
            this.operations = operations;
        }

        FileAction(Type type, FileOperation operation) {
            this.type = type;
            this.operations = new ArrayList<>();
            this.operations.add(operation);
        }
    }

    /**
     * Represents a single file operation (source -> destination)
     */
    private static class FileOperation {
        final File source;
        final File destination;
        final boolean wasDirectory;

        FileOperation(File source, File destination, boolean wasDirectory) {
            this.source = source;
            this.destination = destination;
            this.wasDirectory = wasDirectory;
        }
    }

    private static class BreadcrumbSegment {
        final int x;
        final int width;
        final File directory;

        BreadcrumbSegment(int x, int width, File directory) {
            this.x = x;
            this.width = width;
            this.directory = directory;
        }
    }

    private static class QuickShareButton {
        final int x;
        final int y;
        final int width;
        final int height;
        final int entryIndex;

        QuickShareButton(int x, int y, int width, int height, int entryIndex) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.entryIndex = entryIndex;
        }

        boolean isHovered(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private static class FileEntry {
        final File file;
        final boolean isDirectory;
        String relativePath; // Path relative to base directory (for search results)

        FileEntry(File file) {
            this.file = file;
            this.isDirectory = file.isDirectory();
            this.relativePath = null;
        }

        FileEntry(File file, String relativePath) {
            this.file = file;
            this.isDirectory = file.isDirectory();
            this.relativePath = relativePath;
        }
    }

    public LocalFolderPage(Screen parentScreen) {
        super(Text.literal("Local Folder"));
        this.parentScreen = parentScreen;

        // Start at the configured download directory
        String downloadPath = DownloadSettings.getInstance().getAbsoluteDownloadPath();
        this.currentDirectory = new File(downloadPath);

        // Set the base directory (schematic folder) - don't allow going above this
        this.baseDirectory = this.currentDirectory;

        // If directory doesn't exist, create it
        if (!this.currentDirectory.exists()) {
            this.currentDirectory.mkdirs();
        }

        // Initialize trash folder for undo support (hidden folder)
        this.trashFolder = new File(this.baseDirectory, ".trash");
        if (!this.trashFolder.exists()) {
            this.trashFolder.mkdirs();
        }

        loadEntries();
    }

    @Override
    protected void init() {
        super.init();

        // Initialize toast manager
        if (this.client != null) {
            toastManager = new ToastManager(this.client);
        }

        // Calculate responsive button sizes based on screen width
        int availableWidth = this.width - PADDING * 2 - BUTTON_HEIGHT - PADDING; // Leave space for settings button
        boolean isCompact = availableWidth < 550; // Use compact mode for small screens
        boolean isVeryCompact = availableWidth < 450; // Use very compact mode for very small screens

        // Define button widths based on screen size
        int newFolderWidth = isVeryCompact ? 25 : (isCompact ? 70 : 100);
        int renameWidth = isVeryCompact ? 25 : (isCompact ? 50 : 70);
        int deleteWidth = isVeryCompact ? 25 : (isCompact ? 45 : 60);
        int openFolderWidth = isVeryCompact ? 25 : (isCompact ? 80 : 120);

        // Define button labels based on screen size
        String newFolderLabel = isVeryCompact ? "+" : (isCompact ? "+ New" : "+ New Folder");
        String renameLabel = isVeryCompact ? "✏" : "Rename";
        String deleteLabel = isVeryCompact ? "🗑" : "Delete";
        String openFolderLabel = isVeryCompact ? "📁" : (isCompact ? "📁 Open" : "📁 Open Folder");

        int currentX = PADDING;

        // Back button (top left)
        this.addDrawableChild(new CustomButton(
                currentX, PADDING, BUTTON_HEIGHT, BUTTON_HEIGHT,
                Text.literal("←"), button -> goBack()
        ));
        currentX += BUTTON_HEIGHT + PADDING;

        // New Folder button (next to Back button)
        this.addDrawableChild(new CustomButton(
                currentX, PADDING, newFolderWidth, BUTTON_HEIGHT,
                Text.literal(newFolderLabel), button -> openNewFolderPopup()
        ));
        currentX += newFolderWidth + PADDING;

        // Rename button (next to New Folder button)
        renameButton = new CustomButton(
                currentX,
                PADDING,
                renameWidth,
                BUTTON_HEIGHT,
                Text.literal(renameLabel),
                button -> openRenamePopup()
        );
        renameButton.active = false; // Disabled until something is selected
        this.addDrawableChild(renameButton);
        currentX += renameWidth + PADDING;

        // Delete button (next to Rename button)
        deleteButton = new CustomButton(
                currentX,
                PADDING,
                deleteWidth,
                BUTTON_HEIGHT,
                Text.literal(deleteLabel),
                button -> handleDeleteClick()
        );
        deleteButton.active = false; // Disabled until something is selected
        this.addDrawableChild(deleteButton);
        currentX += deleteWidth + PADDING;

        // Open in File Explorer button (next to Delete button)
        this.addDrawableChild(new CustomButton(
                currentX, PADDING, openFolderWidth, BUTTON_HEIGHT,
                Text.literal(openFolderLabel), button -> openInFileExplorer()
        ));
        currentX += openFolderWidth + PADDING;

        // Settings button (top right)
        int settingsX = this.width - PADDING - BUTTON_HEIGHT;
        this.addDrawableChild(new CustomButton(
                settingsX, PADDING, BUTTON_HEIGHT, BUTTON_HEIGHT,
                Text.literal("⚙"), button -> openSettings()
        ));

        // Search field (same row, between Open Folder and Settings button)
        int searchWidth = settingsX - currentX - PADDING;
        if (this.client != null && searchWidth > 30) {
            searchField = new CustomTextField(this.client, currentX, PADDING, searchWidth, BUTTON_HEIGHT, Text.literal("Search"));
            searchField.setPlaceholder(Text.literal("Search..."));
            searchField.setOnChanged(this::onSearchChanged);
            searchField.setOnClearPressed(this::onSearchCleared);
            this.addDrawableChild(searchField);
        }

        // Initialize scrollbar (no second row for search field)
        int listY = PADDING * 3 + BUTTON_HEIGHT + 18;
        int listHeight = this.height - listY - PADDING * 2;
        scrollBar = new ScrollBar(this.width - PADDING - SCROLLBAR_WIDTH, listY, listHeight);
        updateScrollBar();
    }

    private void goBack() {
        if (this.client != null) {
            this.client.setScreen(parentScreen);
        }
    }

    private void openSettings() {
        if (this.client != null) {
            this.client.setScreen(new SettingsPage(this));
        }
    }

    private void openInFileExplorer() {
        // Use Minecraft's built-in Util class to open folders cross-platform
        // This is the same method used by vanilla Minecraft to open the resource pack folder
        Util.getOperatingSystem().open(currentDirectory);
    }

    private void handleQuickShare(int entryIndex) {
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return;
        }

        FileEntry entry = entries.get(entryIndex);

        // Only allow sharing litematic files
        if (entry.isDirectory || !entry.file.getName().toLowerCase().endsWith(".litematic")) {
            if (toastManager != null) {
                toastManager.showError("Only .litematic files can be shared");
            }
            return;
        }

        // Mark as uploading
        uploadingIndex = entryIndex;
        if (toastManager != null) {
            toastManager.showInfo("Uploading " + entry.file.getName() + "...");
        }

        // Upload asynchronously
        ChoculaterieNetworkManager.uploadLitematic(entry.file)
            .thenAccept(response -> {
                // Copy to clipboard
                String shortUrl = response.getShortUrl();
                if (shortUrl == null || shortUrl.isEmpty()) {
                    if (client != null) {
                        client.execute(() -> {
                            if (toastManager != null) {
                                toastManager.showError("Upload failed: No URL returned");
                            }
                            uploadingIndex = -1;
                        });
                    }
                    System.err.println("Upload failed: No URL returned");
                    return;
                }

                if (client != null) {
                    client.execute(() -> {
                        try {
                            // Use Minecraft's native clipboard if available
                            if (client.keyboard != null) {
                                client.keyboard.setClipboard(shortUrl);
                                if (toastManager != null) {
                                    toastManager.showSuccess("Link copied to clipboard!");
                                }
                                System.out.println("Quick share URL copied: " + shortUrl);
                            } else {
                                // Fallback to AWT clipboard
                                StringSelection selection = new StringSelection(shortUrl);
                                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                                if (toastManager != null) {
                                    toastManager.showSuccess("Link copied to clipboard!");
                                }
                                System.out.println("Quick share URL copied (AWT): " + shortUrl);
                            }
                        } catch (Exception e) {
                            if (toastManager != null) {
                                toastManager.showError("Failed to copy to clipboard");
                            }
                            System.err.println("Failed to copy to clipboard: " + e.getMessage());
                            e.printStackTrace();
                        }
                        uploadingIndex = -1;
                    });
                }
            })
            .exceptionally(error -> {
                String errorMessage = error.getMessage();
                String userMessage;

                // Parse error for user-friendly message
                if (errorMessage != null) {
                    if (errorMessage.contains("UnknownHostException") ||
                        errorMessage.contains("ConnectException") ||
                        errorMessage.contains("NoRouteToHostException")) {
                        userMessage = "Upload failed: No internet connection";
                    } else if (errorMessage.contains("SocketTimeoutException")) {
                        userMessage = "Upload failed: Connection timeout";
                    } else if (errorMessage.contains("HTTP error code: 413")) {
                        userMessage = "Upload failed: File too large";
                    } else if (errorMessage.contains("HTTP error code:")) {
                        userMessage = "Upload failed: Server error";
                    } else {
                        userMessage = "Upload failed: " + errorMessage;
                    }
                } else {
                    userMessage = "Upload failed: Unknown error";
                }

                // Full error for copying
                String fullError = "Quick Share Error: " + errorMessage;
                if (error.getCause() != null) {
                    fullError += "\nCause: " + error.getCause().toString();
                }

                if (toastManager != null) {
                    toastManager.showError(userMessage, fullError);
                }
                System.err.println("Quick share failed: " + errorMessage);
                error.printStackTrace();
                uploadingIndex = -1;
                return null;
            });
    }

    private void openNewFolderPopup() {
        activePopup = new TextInputPopup(
                this,
                "Create New Folder",
                this::createNewFolder,
                this::closePopup
        );
    }

    private void createNewFolder(String folderName) {
        if (currentDirectory == null) {
            closePopup();
            return;
        }

        // Check if folder already exists
        File newFolder = new File(currentDirectory, folderName);
        if (newFolder.exists()) {
            if (activePopup != null) {
                activePopup.setErrorMessage("\"" + folderName + "\" already exists in this folder");
            }
            return;
        }

        // Try to create the folder
        boolean success = newFolder.mkdir();
        if (success) {
            // Record for undo (source is null for creation, destination is the new folder)
            addUndoAction(new FileAction(FileAction.Type.CREATE_FOLDER, new FileOperation(null, newFolder, true)));

            System.out.println("Created folder: " + newFolder.getAbsolutePath());
            if (toastManager != null) {
                toastManager.showSuccess("Created folder \"" + folderName + "\"");
            }
            closePopup();
            loadEntries(); // Refresh the list
        } else {
            if (activePopup != null) {
                activePopup.setErrorMessage("Failed to create folder - check folder permissions");
            }
        }
    }

    private void closePopup() {
        activePopup = null;
        confirmPopup = null;
    }

    private void openRenamePopup() {
        // Can only rename single items
        if (selectedIndices.size() != 1) {
            return;
        }

        int index = selectedIndices.getFirst();
        if (index < 0 || index >= entries.size()) {
            return;
        }

        FileEntry entry = entries.get(index);
        String currentName = entry.file.getName();

        activePopup = new TextInputPopup(
                this,
                "Rename",
                "Rename",
                newName -> renameFile(entry.file, newName),
                this::closePopup
        );
        activePopup.setText(currentName);
    }

    private void renameFile(File file, String newName) {
        if (file == null || newName == null || newName.trim().isEmpty()) {
            closePopup();
            return;
        }

        newName = newName.trim();

        // Check if name is the same
        if (newName.equals(file.getName())) {
            closePopup();
            return;
        }

        // Check if target already exists
        File newFile = new File(file.getParentFile(), newName);
        if (newFile.exists()) {
            if (activePopup != null) {
                activePopup.setErrorMessage("\"" + newName + "\" already exists in this folder");
            }
            return;
        }

        // Try to rename
        boolean isDirectory = file.isDirectory();
        boolean success = file.renameTo(newFile);
        if (success) {
            // Record for undo
            addUndoAction(new FileAction(FileAction.Type.RENAME, new FileOperation(file, newFile, isDirectory)));

            System.out.println("Renamed: " + file.getName() + " -> " + newName);
            if (toastManager != null) {
                toastManager.showSuccess("Renamed to \"" + newName + "\"");
            }
            closePopup();
            loadEntries();
        } else {
            if (activePopup != null) {
                activePopup.setErrorMessage("Failed to rename - file may be in use or protected");
            }
        }
    }

    private void handleDeleteClick() {
        if (selectedIndices.isEmpty()) {
            return;
        }

        // Check if shift is held - skip confirmation
        long windowHandle = this.client != null ? this.client.getWindow().getHandle() : 0;
        boolean shiftHeld = false;
        if (windowHandle != 0) {
            shiftHeld = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                       org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }

        if (shiftHeld) {
            // Shift held - delete immediately without confirmation
            deleteSelectedFiles();
        } else {
            // Show confirmation dialog
            String message;
            String title;

            if (selectedIndices.size() == 1) {
                // Single item
                FileEntry entry = entries.get(selectedIndices.getFirst());
                String itemType = entry.isDirectory ? "folder" : "file";
                title = "Delete " + itemType + "?";

                if (entry.isDirectory) {
                    // Build tree view for folder contents
                    StringBuilder messageBuilder = new StringBuilder();
                    messageBuilder.append("Are you sure you want to delete \"").append(entry.file.getName()).append("\"?\n\n");
                    messageBuilder.append("Contents:\n");
                    buildDeleteTree(entry.file, messageBuilder, "", true);
                    message = messageBuilder.toString();
                } else {
                    message = "Are you sure you want to delete \"" + entry.file.getName() + "\"?";
                }
            } else {
                // Multiple items
                title = "Delete " + selectedIndices.size() + " items?";

                // Check if any folders are selected
                boolean hasFolders = false;
                for (int idx : selectedIndices) {
                    if (idx >= 0 && idx < entries.size() && entries.get(idx).isDirectory) {
                        hasFolders = true;
                        break;
                    }
                }

                if (hasFolders) {
                    // Build tree view for all selected items
                    StringBuilder messageBuilder = new StringBuilder();
                    messageBuilder.append("Are you sure you want to delete ").append(selectedIndices.size()).append(" selected items?\n\n");
                    messageBuilder.append("Items to delete:\n");

                    // Get the selected entries and sort them (directories first, then files, both alphabetically)
                    List<FileEntry> selectedEntries = new ArrayList<>();
                    for (int idx : selectedIndices) {
                        if (idx >= 0 && idx < entries.size()) {
                            selectedEntries.add(entries.get(idx));
                        }
                    }
                    selectedEntries.sort((a, b) -> {
                        if (a.isDirectory && !b.isDirectory) return -1;
                        if (!a.isDirectory && b.isDirectory) return 1;
                        return a.file.getName().compareToIgnoreCase(b.file.getName());
                    });

                    for (int i = 0; i < selectedEntries.size(); i++) {
                        FileEntry entry = selectedEntries.get(i);
                        boolean isLast = (i == selectedEntries.size() - 1);
                        String connector = isLast ? "└── " : "├── ";

                        if (entry.isDirectory) {
                            File[] subFiles = entry.file.listFiles();
                            boolean isEmpty = subFiles == null || subFiles.length == 0;
                            messageBuilder.append(connector).append("📁 ").append(entry.file.getName()).append("/\n");
                            if (!isEmpty) {
                                String newIndent = isLast ? "    " : "│   ";
                                buildDeleteTree(entry.file, messageBuilder, newIndent, false);
                            }
                        } else {
                            messageBuilder.append(connector).append("📄 ").append(entry.file.getName()).append("\n");
                        }
                    }
                    message = messageBuilder.toString();
                } else {
                    message = "Are you sure you want to delete " + selectedIndices.size() + " selected items?";
                }
            }

            confirmPopup = new ConfirmPopup(
                    this,
                    title,
                    message,
                    () -> {
                        deleteSelectedFiles();
                        closePopup();
                    },
                    this::closePopup
            );
        }
    }

    private void deleteSelectedFiles() {
        if (selectedIndices.isEmpty()) {
            return;
        }

        // Ensure trash folder exists
        if (!trashFolder.exists()) {
            trashFolder.mkdirs();
        }

        // Sort indices in descending order to avoid index shifting issues
        List<Integer> sortedIndices = new ArrayList<>(selectedIndices);
        sortedIndices.sort((a, b) -> b - a);

        int successCount = 0;
        int failCount = 0;
        List<String> failedNames = new ArrayList<>();
        List<FileOperation> successfulOperations = new ArrayList<>();

        for (int index : sortedIndices) {
            if (index >= 0 && index < entries.size()) {
                FileEntry entry = entries.get(index);
                File sourceFile = entry.file;

                // Generate unique trash name to avoid conflicts (timestamp + original name)
                String trashName = System.currentTimeMillis() + "_" + sourceFile.getName();
                File trashFile = new File(trashFolder, trashName);

                // Move to trash instead of deleting
                boolean success = sourceFile.renameTo(trashFile);

                if (success) {
                    successCount++;
                    // Record operation: source is original location, destination is trash location
                    successfulOperations.add(new FileOperation(sourceFile, trashFile, entry.isDirectory));
                    System.out.println("Moved to trash: " + sourceFile.getName());
                } else {
                    failCount++;
                    failedNames.add(sourceFile.getName());
                    System.err.println("Failed to delete: " + sourceFile.getAbsolutePath());
                }
            }
        }

        // Record successful operations for undo
        if (!successfulOperations.isEmpty()) {
            addUndoAction(new FileAction(FileAction.Type.DELETE, successfulOperations));
        }

        // Show result toast
        if (toastManager != null) {
            if (failCount == 0) {
                if (successCount == 1) {
                    toastManager.showSuccess("Deleted 1 item (Ctrl+Z to undo)");
                } else {
                    toastManager.showSuccess("Deleted " + successCount + " items (Ctrl+Z to undo)");
                }
            } else if (successCount == 0) {
                if (failCount == 1) {
                    toastManager.showError("Failed to delete \"" + failedNames.getFirst() + "\" - file may be in use or protected");
                } else if (failCount <= 3) {
                    toastManager.showError("Failed to delete: " + String.join(", ", failedNames) + " - files may be in use or protected");
                } else {
                    toastManager.showError("Failed to delete " + failCount + " items - files may be in use or protected");
                }
            } else {
                if (failCount == 1) {
                    toastManager.showError("Deleted " + successCount + " items, failed to delete \"" + failedNames.getFirst() + "\"");
                } else {
                    toastManager.showError("Deleted " + successCount + " items, failed to delete " + failCount + " items");
                }
            }
        }

        loadEntries();
    }

    private void performMove(File targetFolder) {
        if (selectedIndices.isEmpty() || targetFolder == null || !targetFolder.isDirectory()) {
            return;
        }

        int successCount = 0;
        int conflictCount = 0;
        int otherFailCount = 0;
        List<String> conflictNames = new ArrayList<>();
        List<String> otherFailNames = new ArrayList<>();
        List<FileOperation> successfulOperations = new ArrayList<>();

        // Sort indices in descending order to avoid issues with index shifting
        List<Integer> sortedIndices = new ArrayList<>(selectedIndices);
        sortedIndices.sort((a, b) -> b - a);

        for (int index : sortedIndices) {
            if (index >= 0 && index < entries.size()) {
                FileEntry entry = entries.get(index);
                File sourceFile = entry.file;
                File destFile = new File(targetFolder, sourceFile.getName());

                // Check if destination already exists
                if (destFile.exists()) {
                    conflictCount++;
                    conflictNames.add(sourceFile.getName());
                    System.err.println("Cannot move: destination already exists: " + destFile.getAbsolutePath());
                    continue;
                }

                // Perform the move
                boolean success = sourceFile.renameTo(destFile);

                if (success) {
                    successCount++;
                    successfulOperations.add(new FileOperation(sourceFile, destFile, entry.isDirectory));
                    System.out.println("Moved: " + sourceFile.getName() + " -> " + targetFolder.getName());
                } else {
                    otherFailCount++;
                    otherFailNames.add(sourceFile.getName());
                    System.err.println("Failed to move: " + sourceFile.getAbsolutePath());
                }
            }
        }

        // Record successful operations for undo
        if (!successfulOperations.isEmpty()) {
            addUndoAction(new FileAction(FileAction.Type.MOVE, successfulOperations));
        }

        int totalFailCount = conflictCount + otherFailCount;

        // Show result toast
        if (toastManager != null) {
            if (totalFailCount == 0) {
                if (successCount == 1) {
                    toastManager.showSuccess("Moved 1 item to " + targetFolder.getName());
                } else {
                    toastManager.showSuccess("Moved " + successCount + " items to " + targetFolder.getName());
                }
            } else if (successCount == 0) {
                // All failed - provide specific error message
                if (conflictCount > 0 && otherFailCount == 0) {
                    // All failures are due to conflicts
                    if (conflictCount == 1) {
                        toastManager.showError("\"" + conflictNames.getFirst() + "\" already exists in " + targetFolder.getName());
                    } else if (conflictCount <= 3) {
                        toastManager.showError("Items already exist in " + targetFolder.getName() + ": " + String.join(", ", conflictNames));
                    } else {
                        toastManager.showError(conflictCount + " items already exist in " + targetFolder.getName());
                    }
                } else if (otherFailCount > 0 && conflictCount == 0) {
                    // All failures are due to other reasons
                    if (otherFailCount == 1) {
                        toastManager.showError("Failed to move \"" + otherFailNames.getFirst() + "\" - check file permissions");
                    } else {
                        toastManager.showError("Failed to move " + otherFailCount + " items - check file permissions");
                    }
                } else {
                    // Mixed failures
                    toastManager.showError("Failed to move " + totalFailCount + " items (" + conflictCount + " already exist, " + otherFailCount + " other errors)");
                }
            } else {
                // Partial success
                String errorDetail;
                if (conflictCount > 0 && otherFailCount == 0) {
                    errorDetail = conflictCount + " already exist";
                } else if (otherFailCount > 0 && conflictCount == 0) {
                    errorDetail = otherFailCount + " failed";
                } else {
                    errorDetail = conflictCount + " already exist, " + otherFailCount + " failed";
                }
                toastManager.showError("Moved " + successCount + " to " + targetFolder.getName() + ", " + errorDetail);
            }
        }

        loadEntries();
    }

    /**
     * Recursively count all files and folders in a directory
     */
    private int[] countFilesRecursively(File directory) {
        int[] counts = new int[2]; // [0] = folders, [1] = files

        if (!directory.isDirectory()) {
            return counts;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return counts;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                counts[0]++; // Count this folder
                // Recursively count contents
                int[] subCounts = countFilesRecursively(file);
                counts[0] += subCounts[0];
                counts[1] += subCounts[1];
            } else {
                counts[1]++; // Count this file
            }
        }

        return counts;
    }

    /**
     * Build a tree representation of folder contents for delete confirmation
     */
    private void buildDeleteTree(File directory, StringBuilder builder, String indent, boolean showCount) {
        if (!directory.isDirectory()) return;

        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            builder.append(indent).append("(empty)\n");
            return;
        }

        // Sort files: directories first, then files, both alphabetically
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        // Limit items shown to prevent huge dialogs
        int maxItems = 20;

        // Show up to maxItems
        int shown = 0;
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (shown >= maxItems) {
                int remaining = files.length - shown;
                builder.append(indent).append("└── ... and ").append(remaining).append(" more item(s)\n");
                break;
            }

            // Determine if this is the last item to show proper tree connector
            boolean isLast = (i == files.length - 1) || (shown == maxItems - 1 && files.length > maxItems);
            String connector = isLast ? "└── " : "├── ";

            if (file.isDirectory()) {
                File[] subFiles = file.listFiles();
                boolean isEmpty = subFiles == null || subFiles.length == 0;
                builder.append(indent).append(connector).append("📁 ").append(file.getName()).append("/\n");

                // Recursively show folder contents (limited depth)
                if (!isEmpty && indent.length() < 12) { // Max 3 levels deep
                    String newIndent = indent + (isLast ? "    " : "│   ");
                    buildDeleteTree(file, builder, newIndent, false);
                }
            } else {
                builder.append(indent).append(connector).append("📄 ").append(file.getName()).append("\n");
            }
            shown++;
        }

        // Show count summary if requested (recursive count)
        if (showCount && files.length > 0) {
            int[] counts = countFilesRecursively(directory);
            int totalFolders = counts[0];
            int totalFiles = counts[1];

            builder.append("\n");
            if (totalFolders > 0) {
                builder.append("Total: ").append(totalFolders).append(" folder(s), ").append(totalFiles).append(" file(s)");
            } else {
                builder.append("Total: ").append(totalFiles).append(" file(s)");
            }
        }
    }

    private boolean deleteDirectoryRecursively(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectoryRecursively(file);
                } else {
                    file.delete();
                }
            }
        }
        return directory.delete();
    }

    private void updateSelectionButtons() {
        boolean hasSelection = !selectedIndices.isEmpty();
        if (renameButton != null) {
            // Can only rename single items
            renameButton.active = selectedIndices.size() == 1;
        }
        if (deleteButton != null) {
            // Can delete multiple items
            deleteButton.active = hasSelection;
        }
    }

    private void selectAll() {
        if (entries.isEmpty()) {
            return;
        }
        selectedIndices.clear();
        for (int i = 0; i < entries.size(); i++) {
            selectedIndices.add(i);
        }
        lastClickedIndex = entries.size() - 1;
        updateSelectionButtons();
    }

    private void onSearchChanged() {
        if (searchField != null) {
            String newQuery = searchField.getText().trim().toLowerCase();
            if (!newQuery.equals(searchQuery)) {
                searchQuery = newQuery;
                isSearchActive = !searchQuery.isEmpty();
                performSearch();
            }
        }
    }

    private void onSearchCleared() {
        searchQuery = "";
        isSearchActive = false;
        loadEntries();
    }

    private void performSearch() {
        if (!isSearchActive || searchQuery.isEmpty()) {
            loadEntries();
            return;
        }

        entries.clear();
        selectedIndices.clear();
        lastClickedIndex = -1;
        scrollOffset = 0;

        // Search recursively from base directory
        searchRecursively(baseDirectory, "");

        // Sort results: directories first, then files, alphabetically by name
        entries.sort((a, b) -> {
            if (a.isDirectory && !b.isDirectory) return -1;
            if (!a.isDirectory && b.isDirectory) return 1;
            return a.file.getName().compareToIgnoreCase(b.file.getName());
        });

        updateScrollBar();
        updateSelectionButtons();
    }

    private void searchRecursively(File directory, String pathPrefix) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            // Skip hidden trash folder
            if (file.getName().equals(".trash")) {
                continue;
            }

            String fileName = file.getName().toLowerCase();
            String currentPath = pathPrefix.isEmpty() ? file.getName() : pathPrefix + "/" + file.getName();

            // Check if name matches search query
            if (fileName.contains(searchQuery)) {
                // Calculate relative path from base directory
                String relativePath = getRelativePath(file);
                entries.add(new FileEntry(file, relativePath));
            }

            // Recurse into directories
            if (file.isDirectory()) {
                searchRecursively(file, currentPath);
            }
        }
    }

    private String getRelativePath(File file) {
        if (baseDirectory == null || file == null) {
            return "";
        }
        try {
            String basePath = baseDirectory.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.startsWith(basePath)) {
                String relative = filePath.substring(basePath.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                // Remove the file name to get just the directory path
                int lastSep = relative.lastIndexOf(File.separator);
                if (lastSep > 0) {
                    return relative.substring(0, lastSep);
                }
                return "";
            }
        } catch (Exception e) {
            // Ignore
        }
        return "";
    }

    private void loadEntries() {
        entries.clear();
        selectedIndices.clear();
        lastClickedIndex = -1;

        // If search is active, perform search instead
        if (isSearchActive && !searchQuery.isEmpty()) {
            performSearch();
            return;
        }

        if (currentDirectory != null && currentDirectory.exists()) {
            File[] files = currentDirectory.listFiles();
            if (files != null) {
                // Sort: directories first, then files, alphabetically
                Arrays.sort(files, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });

                for (File file : files) {
                    // Hide the .trash folder used for undo support
                    if (file.getName().equals(".trash")) {
                        continue;
                    }
                    entries.add(new FileEntry(file));
                }
            }
        }

        updateScrollBar();
        updateSelectionButtons();
    }

    private void updateScrollBar() {
        if (scrollBar != null) {
            int listY = PADDING * 3 + BUTTON_HEIGHT + 18; // Account for search field
            int listHeight = this.height - listY - PADDING * 2;
            int maxVisibleItems = listHeight / ITEM_HEIGHT;

            scrollBar.setScrollData(entries.size() * ITEM_HEIGHT, maxVisibleItems * ITEM_HEIGHT);

            // Update scroll offset from scrollbar percentage
            int maxScroll = Math.max(0, entries.size() - maxVisibleItems);
            scrollOffset = (int)(scrollBar.getScrollPercentage() * maxScroll);
        }
    }



    private void renderBreadcrumb(DrawContext context, int mouseX, int mouseY) {
        breadcrumbSegments.clear();

        int breadcrumbY = PADDING + BUTTON_HEIGHT + PADDING + 14;
        int currentX = PADDING;

        // Draw "Current: " label
        String label = "Current: ";
        context.drawTextWithShadow(this.textRenderer, label, currentX, breadcrumbY, 0xFFAAAAAA);
        currentX += this.textRenderer.getWidth(label);

        // Build path segments from base to current
        List<File> pathSegments = new ArrayList<>();
        File dir = currentDirectory;

        try {
            String basePath = baseDirectory.getCanonicalPath();

            // Collect all directories from current to base
            while (dir != null) {
                pathSegments.addFirst(dir); // Add at beginning
                String dirPath = dir.getCanonicalPath();
                if (dirPath.equals(basePath)) {
                    break; // Stop at base directory
                }
                dir = dir.getParentFile();
            }
        } catch (Exception e) {
            pathSegments.clear();
            pathSegments.add(currentDirectory);
        }

        // Render each segment as clickable
        for (int i = 0; i < pathSegments.size(); i++) {
            File segment = pathSegments.get(i);
            String segmentName;

            if (i == 0) {
                // Root segment - show as "/"
                segmentName = "/";
            } else {
                // Show separator before folder name (but not for the first subfolder after root)
                if (i > 1) {
                    String separator = " / ";
                    context.drawTextWithShadow(this.textRenderer, separator, currentX, breadcrumbY, 0xFF888888);
                    currentX += this.textRenderer.getWidth(separator);
                } else {
                    // Just add a space after root "/"
                    String space = " ";
                    context.drawTextWithShadow(this.textRenderer, space, currentX, breadcrumbY, 0xFF888888);
                    currentX += this.textRenderer.getWidth(space);
                }
                segmentName = segment.getName();
            }

            int segmentWidth = this.textRenderer.getWidth(segmentName);

            // Check if mouse is hovering over this segment (and no popup active)
            boolean isHovered = activePopup == null &&
                              mouseX >= currentX && mouseX < currentX + segmentWidth &&
                              mouseY >= breadcrumbY - 2 && mouseY < breadcrumbY + 12;

            // Check if this is the current directory (last segment)
            boolean isCurrent = (i == pathSegments.size() - 1);

            // Check if this breadcrumb is a drop target during drag
            boolean isDropTarget = isDragging && dropTargetBreadcrumb != null &&
                                  dropTargetBreadcrumb.equals(segment);

            // Draw segment with appropriate color
            int color;
            if (isDropTarget) {
                color = 0xFF55FF55; // Green for drop target
            } else if (isCurrent) {
                color = 0xFFFFFFFF; // White for current
            } else if (isHovered) {
                color = 0xFF55AAFF; // Light blue when hovered
            } else {
                color = 0xFF88CCFF; // Blue for clickable
            }

            // Draw background highlight for drop target
            if (isDropTarget) {
                context.fill(currentX - 2, breadcrumbY - 2, currentX + segmentWidth + 2, breadcrumbY + 12, 0x88336633);
            }

            // Draw underline if hovered and not current
            if (isHovered && !isCurrent) {
                context.fill(currentX, breadcrumbY + 10, currentX + segmentWidth, breadcrumbY + 11, color);
            }

            context.drawTextWithShadow(this.textRenderer, segmentName, currentX, breadcrumbY, color);

            // Store segment for click detection (only if not current)
            if (!isCurrent) {
                breadcrumbSegments.add(new BreadcrumbSegment(currentX, segmentWidth, segment));
            }

            currentX += segmentWidth;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fill the entire screen with dark grey background
        context.fill(0, 0, this.width, this.height, 0xFF202020);

        // Handle keyboard shortcuts (only when search field is not focused and no popup is active)
        if (this.client != null && this.client.getWindow() != null) {
            long windowHandle = this.client.getWindow().getHandle();
            boolean searchFocused = searchField != null && searchField.isFocused();
            boolean popupActive = activePopup != null || confirmPopup != null;

            if (!searchFocused && !popupActive) {
                // Check modifier keys
                boolean ctrlHeld = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                                   org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                boolean shiftHeld = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                                    org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

                // Handle DELETE key for deleting selected items
                boolean isDeleteDown = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (isDeleteDown && !wasDeleteKeyPressed && !selectedIndices.isEmpty()) {
                    if (shiftHeld) {
                        // Shift+Delete - delete immediately without confirmation
                        deleteSelectedFiles();
                    } else {
                        // Delete - show confirmation dialog
                        handleDeleteClick();
                    }
                }
                wasDeleteKeyPressed = isDeleteDown;

                // Handle Ctrl+Z for Undo
                boolean isZDown = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_Z) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (isZDown && !wasZKeyPressed && ctrlHeld) {
                    performUndo();
                }
                wasZKeyPressed = isZDown;

                // Handle Ctrl+Y for Redo
                boolean isYDown = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_Y) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (isYDown && !wasYKeyPressed && ctrlHeld) {
                    performRedo();
                }
                wasYKeyPressed = isYDown;

                // Handle Ctrl+A for Select All
                boolean isADown = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_A) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (isADown && !wasAKeyPressed && ctrlHeld) {
                    selectAll();
                }
                wasAKeyPressed = isADown;
            }
        }

        // Check for drag state using GLFW
        if (dragStartIndex != -1 && this.client != null && this.client.getWindow() != null) {
            long windowHandle = this.client.getWindow().getHandle();
            boolean mouseButtonPressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (mouseButtonPressed) {
                // Check if mouse moved enough to start drag (3 pixel threshold)
                if (!isDragging) {
                    double distX = mouseX - dragStartX;
                    double distY = mouseY - dragStartY;
                    if (Math.sqrt(distX * distX + distY * distY) > 3) {
                        isDragging = true;
                        // Drag started - preClickSelection is no longer needed
                        preClickSelection.clear();
                    }
                }

                // Update drop target while dragging
                if (isDragging) {
                    int listY = PADDING * 3 + BUTTON_HEIGHT + 18;
                    int listHeight = this.height - listY - PADDING * 2;
                    int listRightEdge = this.width - PADDING - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;

                    dropTargetIndex = -1;
                    dropTargetBreadcrumb = null;

                    // Check breadcrumb drop targets first
                    int breadcrumbY = PADDING * 3 + BUTTON_HEIGHT + 18 - 14;
                    if (mouseY >= breadcrumbY - 2 && mouseY < breadcrumbY + 12) {
                        for (BreadcrumbSegment segment : breadcrumbSegments) {
                            if (mouseX >= segment.x && mouseX < segment.x + segment.width) {
                                // Found breadcrumb hover
                                dropTargetBreadcrumb = segment.directory;
                                break;
                            }
                        }
                    }

                    // Check list drop targets if not hovering breadcrumb
                    if (dropTargetBreadcrumb == null &&
                        mouseX >= PADDING && mouseX < listRightEdge &&
                        mouseY >= listY && mouseY < listY + listHeight) {

                        int hoveredIndex = scrollOffset + (int)((mouseY - listY) / ITEM_HEIGHT);

                        // Only allow dropping into folders that aren't selected
                        if (hoveredIndex >= 0 && hoveredIndex < entries.size()) {
                            FileEntry hoveredEntry = entries.get(hoveredIndex);
                            if (hoveredEntry.isDirectory && !selectedIndices.contains(hoveredIndex)) {
                                dropTargetIndex = hoveredIndex;
                            }
                        }
                    }
                }
            } else {
                // Mouse button released
                if (isDragging) {
                    // Perform drop if dragging
                    if (dropTargetBreadcrumb != null) {
                        // Drop onto breadcrumb
                        performMove(dropTargetBreadcrumb);
                    } else if (dropTargetIndex != -1 && dropTargetIndex < entries.size()) {
                        // Drop onto folder in list
                        FileEntry targetFolder = entries.get(dropTargetIndex);
                        performMove(targetFolder.file);
                    }
                } else {
                    // No drag happened - check if we need to clear to single selection
                    if (!preClickSelection.isEmpty()) {
                        // User clicked on multi-selected item but didn't drag
                        // Clear to just that item
                        int singleItem = preClickSelection.getFirst();
                        selectedIndices.clear();
                        selectedIndices.add(singleItem);
                        lastClickedIndex = singleItem;
                        updateSelectionButtons();
                    }
                }

                // Reset drag state
                isDragging = false;
                dragStartIndex = -1;
                dropTargetIndex = -1;
                dropTargetBreadcrumb = null;
                preClickSelection.clear(); // Clear saved selection
            }
        }

        // If popup is active, render buttons without hover effects
        boolean popupActive = activePopup != null || confirmPopup != null;
        int renderMouseX = popupActive ? -1 : mouseX;
        int renderMouseY = popupActive ? -1 : mouseY;

        super.render(context, renderMouseX, renderMouseY, delta);

        // Draw search field
        if (searchField != null) {
            searchField.render(context, popupActive ? -1 : mouseX, popupActive ? -1 : mouseY, delta);
        }

        // Draw current path as clickable breadcrumb (hide when search is active)
        if (!isSearchActive) {
            renderBreadcrumb(context, mouseX, mouseY);
        } else {
            // Show search results count
            int breadcrumbY = PADDING * 3 + BUTTON_HEIGHT + 18 - 14;
            String searchInfo = "Found " + entries.size() + " result" + (entries.size() != 1 ? "s" : "") + " for \"" + searchQuery + "\"";
            context.drawTextWithShadow(this.textRenderer, searchInfo, PADDING, breadcrumbY, 0xFFAAAAFF);
        }

        // Draw file/folder list
        int listY = PADDING * 3 + BUTTON_HEIGHT + 18;
        int listHeight = this.height - listY - PADDING * 2;
        int maxVisibleItems = listHeight / ITEM_HEIGHT;

        // Calculate list width (end before scrollbar)
        int listRightEdge = this.width - PADDING - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;

        // Draw list background (end before scrollbar)
        context.fill(PADDING, listY, listRightEdge, listY + listHeight, 0xFF151515);

        // Draw entries
        quickShareButtons.clear(); // Reset button positions each frame

        for (int i = scrollOffset; i < Math.min(entries.size(), scrollOffset + maxVisibleItems); i++) {
            FileEntry entry = entries.get(i);
            int itemY = listY + (i - scrollOffset) * ITEM_HEIGHT;

            // Check if mouse is over this item (only in list area, not scrollbar, and no popup active)
            boolean isHovered = !popupActive &&
                              mouseX >= PADDING && mouseX < listRightEdge &&
                              mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean isSelected = selectedIndices.contains(i);
            boolean isDropTarget = isDragging && entry.isDirectory && i == dropTargetIndex && !selectedIndices.contains(i);
            boolean isBeingDragged = isDragging && selectedIndices.contains(i);

            // Draw item background (end before scrollbar)
            int bgColor;
            if (isDropTarget) {
                bgColor = 0xFF336633; // Green tint for valid drop target
            } else if (isBeingDragged) {
                bgColor = 0xFF505050; // Lighter for items being dragged
            } else if (isSelected) {
                bgColor = 0xFF404040;
            } else if (isHovered) {
                bgColor = 0xFF2A2A2A;
            } else {
                bgColor = 0xFF1A1A1A;
            }
            context.fill(PADDING + 2, itemY + 2, listRightEdge - 2, itemY + ITEM_HEIGHT - 2, bgColor);

            // Draw icon and name
            String icon = entry.isDirectory ? "📁" : "📄";
            String fileName = entry.file.getName();
            int textX = PADDING + 5;
            int textY = itemY + (isSearchActive && entry.relativePath != null && !entry.relativePath.isEmpty() ? 4 : 8);

            // Draw icon
            context.drawTextWithShadow(this.textRenderer, icon + " ", textX, textY, 0xFFFFFFFF);
            textX += this.textRenderer.getWidth(icon + " ");

            // Draw file name with highlighting if search is active
            if (isSearchActive && !searchQuery.isEmpty()) {
                String lowerName = fileName.toLowerCase();
                int searchIndex = lowerName.indexOf(searchQuery);

                if (searchIndex >= 0) {
                    // Draw text before match
                    String beforeMatch = fileName.substring(0, searchIndex);
                    if (!beforeMatch.isEmpty()) {
                        context.drawTextWithShadow(this.textRenderer, beforeMatch, textX, textY, 0xFFFFFFFF);
                        textX += this.textRenderer.getWidth(beforeMatch);
                    }

                    // Draw highlighted match
                    String matchText = fileName.substring(searchIndex, searchIndex + searchQuery.length());
                    int matchWidth = this.textRenderer.getWidth(matchText);
                    context.fill(textX - 1, textY - 1, textX + matchWidth + 1, textY + 9, 0xFF4488FF); // Blue highlight bg
                    context.drawTextWithShadow(this.textRenderer, matchText, textX, textY, 0xFFFFFFFF);
                    textX += matchWidth;

                    // Draw text after match
                    String afterMatch = fileName.substring(searchIndex + searchQuery.length());
                    if (!afterMatch.isEmpty()) {
                        context.drawTextWithShadow(this.textRenderer, afterMatch, textX, textY, 0xFFFFFFFF);
                        textX += this.textRenderer.getWidth(afterMatch);
                    }
                } else {
                    // No match found, draw normally
                    context.drawTextWithShadow(this.textRenderer, fileName, textX, textY, 0xFFFFFFFF);
                    textX += this.textRenderer.getWidth(fileName);
                }
            } else {
                // Normal display
                context.drawTextWithShadow(this.textRenderer, fileName, textX, textY, 0xFFFFFFFF);
                textX += this.textRenderer.getWidth(fileName);
            }

            // Add file size for files
            if (!entry.isDirectory) {
                long sizeKB = entry.file.length() / 1024;
                String sizeText = " (" + sizeKB + " KB)";
                context.drawTextWithShadow(this.textRenderer, sizeText, textX, textY, 0xFFAAAAAA);
            }

            // Draw relative path for search results (below the file name)
            if (isSearchActive && entry.relativePath != null && !entry.relativePath.isEmpty()) {
                String pathDisplay = "📍 " + entry.relativePath;
                context.drawTextWithShadow(this.textRenderer, pathDisplay, PADDING + 5 + this.textRenderer.getWidth(icon + " "), itemY + 15, 0xFF888888);
            }

            // Draw quick share button for litematic files
            if (!entry.isDirectory && entry.file.getName().toLowerCase().endsWith(".litematic")) {
                int buttonWidth = 80;
                int buttonHeight = 16;
                int buttonX = listRightEdge - buttonWidth - 5;
                int buttonY = itemY + (ITEM_HEIGHT - buttonHeight) / 2;

                // Track button position for click detection
                quickShareButtons.add(new QuickShareButton(buttonX, buttonY, buttonWidth, buttonHeight, i));

                // Check if mouse is hovering over button
                boolean buttonHovered = !popupActive &&
                                       mouseX >= buttonX && mouseX < buttonX + buttonWidth &&
                                       mouseY >= buttonY && mouseY < buttonY + buttonHeight;

                // Determine button appearance based on state
                String buttonText;
                int buttonBgColor;
                int buttonTextColor = 0xFFFFFFFF;

                if (i == uploadingIndex) {
                    buttonText = "Uploading...";
                    buttonBgColor = 0xFF555555;
                } else if (buttonHovered) {
                    buttonText = "📤 Share";
                    buttonBgColor = 0xFF4488FF;
                } else {
                    buttonText = "📤 Share";
                    buttonBgColor = 0xFF3366CC;
                }

                // Draw button background
                context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonBgColor);

                // Draw button border (lighter when hovered)
                int borderColor = buttonHovered ? 0xFF66AAFF : 0xFF4477DD;
                context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + 1, borderColor);
                context.fill(buttonX, buttonY + buttonHeight - 1, buttonX + buttonWidth, buttonY + buttonHeight, borderColor);
                context.fill(buttonX, buttonY, buttonX + 1, buttonY + buttonHeight, borderColor);
                context.fill(buttonX + buttonWidth - 1, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, borderColor);

                // Draw button text centered
                int btnTextWidth = this.textRenderer.getWidth(buttonText);
                int btnTextX = buttonX + (buttonWidth - btnTextWidth) / 2;
                int btnTextY = buttonY + (buttonHeight - 8) / 2;
                context.drawTextWithShadow(this.textRenderer, buttonText, btnTextX, btnTextY, buttonTextColor);
            }
        }

        // Render scrollbar widget with drag support
        if (scrollBar != null && scrollBar.isVisible() && this.client != null) {
            boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta, this.client.getWindow().getHandle());

            // If scrollbar was dragged, update our scroll offset
            if (scrollChanged) {
                int maxScroll = Math.max(0, entries.size() - maxVisibleItems);
                scrollOffset = (int)(scrollBar.getScrollPercentage() * maxScroll);
            }
        }

        // Render popup on top of everything
        if (activePopup != null) {
            activePopup.render(context, mouseX, mouseY, delta);
        }

        // Render confirm popup on top of everything
        if (confirmPopup != null) {
            confirmPopup.render(context, mouseX, mouseY, delta);
        }

        // Render drag cursor
        if (isDragging && !selectedIndices.isEmpty()) {
            String dragText = selectedIndices.size() + " item" + (selectedIndices.size() > 1 ? "s" : "");
            int textWidth = this.textRenderer.getWidth(dragText);
            int cursorX = mouseX + 10;
            int cursorY = mouseY + 10;

            // Draw background
            context.fill(cursorX - 2, cursorY - 2, cursorX + textWidth + 2, cursorY + this.textRenderer.fontHeight + 2, 0xCC000000);

            // Draw border
            context.fill(cursorX - 2, cursorY - 2, cursorX + textWidth + 2, cursorY - 1, 0xFF888888);
            context.fill(cursorX - 2, cursorY + this.textRenderer.fontHeight + 1, cursorX + textWidth + 2, cursorY + this.textRenderer.fontHeight + 2, 0xFF888888);
            context.fill(cursorX - 2, cursorY - 2, cursorX - 1, cursorY + this.textRenderer.fontHeight + 2, 0xFF888888);
            context.fill(cursorX + textWidth + 1, cursorY - 2, cursorX + textWidth + 2, cursorY + this.textRenderer.fontHeight + 2, 0xFF888888);

            // Draw text
            context.drawText(this.textRenderer, dragText, cursorX, cursorY, 0xFFFFFFFF, false);
        }

        // Render toasts on top of everything
        if (toastManager != null) {
            toastManager.render(context, delta, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Handle confirm popup first if active
        if (confirmPopup != null) {
            confirmPopup.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        // Handle popup first if active - block all clicks to content behind
        if (activePopup != null) {
            activePopup.mouseClicked(mouseX, mouseY, button);
            return true; // Always consume click when popup is active
        }

        // Check toast clicks (copy button and close button)
        if (button == 0 && toastManager != null) {
            if (toastManager.mouseClicked(mouseX, mouseY)) {
                return true;
            }
            // Block clicks to elements below if hovering over a toast
            if (toastManager.isMouseOverToast(mouseX, mouseY)) {
                return true;
            }
        }

        // Handle search field click
        if (button == 0 && searchField != null) {
            if (searchField.isMouseOver(mouseX, mouseY)) {
                searchField.setFocused(true);
                return true;
            } else {
                searchField.setFocused(false);
            }
        }

        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        // Handle breadcrumb clicks
        int breadcrumbY = PADDING * 3 + BUTTON_HEIGHT + 18 - 14;
        if (mouseY >= breadcrumbY - 2 && mouseY < breadcrumbY + 12) {
            for (BreadcrumbSegment segment : breadcrumbSegments) {
                if (mouseX >= segment.x && mouseX < segment.x + segment.width) {
                    // Clear search when navigating
                    if (isSearchActive) {
                        searchQuery = "";
                        isSearchActive = false;
                        if (searchField != null) {
                            searchField.setText("");
                        }
                    }
                    // Navigate to this directory
                    currentDirectory = segment.directory;
                    loadEntries();
                    scrollOffset = 0;
                    return true;
                }
            }
        }

        // Handle file/folder list clicks (only in list area, not scrollbar)
        int listY = PADDING * 3 + BUTTON_HEIGHT + 18;
        int listHeight = this.height - listY - PADDING * 2;
        int listRightEdge = this.width - PADDING - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;

        if (mouseX >= PADDING && mouseX < listRightEdge &&
            mouseY >= listY && mouseY < listY + listHeight) {

            // Check if clicking on a quick share button first
            for (QuickShareButton qsButton : quickShareButtons) {
                if (qsButton.isHovered(mouseX, mouseY)) {
                    if (button == 0 && uploadingIndex == -1) { // Left click and not currently uploading
                        handleQuickShare(qsButton.entryIndex);
                        return true;
                    }
                }
            }

            int clickedIndex = scrollOffset + (int)((mouseY - listY) / ITEM_HEIGHT);

            if (clickedIndex >= 0 && clickedIndex < entries.size()) {
                if (button == 0) { // Left click
                    FileEntry entry = entries.get(clickedIndex);

                    // Check for Shift and Ctrl modifiers using GLFW
                    long windowHandle = this.client != null ? this.client.getWindow().getHandle() : 0;
                    boolean shiftHeld = false;
                    boolean ctrlHeld = false;
                    if (windowHandle != 0) {
                        shiftHeld = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                                   org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                        ctrlHeld = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                                  org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                    }

                    // Handle double-click on selected directory first
                    if (entry.isDirectory && selectedIndices.contains(clickedIndex) && doubled && !shiftHeld && !ctrlHeld) {
                        // Double click on already selected directory - enter it
                        // Clear search when navigating
                        if (isSearchActive) {
                            searchQuery = "";
                            isSearchActive = false;
                            if (searchField != null) {
                                searchField.setText("");
                            }
                        }
                        currentDirectory = entry.file;
                        loadEntries();
                        scrollOffset = 0;
                    } else if (shiftHeld && lastClickedIndex != -1) {
                        // Shift+click: Range selection from last clicked to current
                        selectedIndices.clear();
                        int start = Math.min(lastClickedIndex, clickedIndex);
                        int end = Math.max(lastClickedIndex, clickedIndex);
                        for (int i = start; i <= end; i++) {
                            selectedIndices.add(i);
                        }
                    } else if (ctrlHeld) {
                        // Ctrl+click: Toggle individual selection
                        if (selectedIndices.contains(clickedIndex)) {
                            selectedIndices.remove(Integer.valueOf(clickedIndex));
                        } else {
                            selectedIndices.add(clickedIndex);
                        }
                        lastClickedIndex = clickedIndex;
                    } else {
                        // Normal click (no modifiers)
                        if (selectedIndices.contains(clickedIndex) && selectedIndices.size() > 1) {
                            // Clicking on an already selected item in multi-selection
                            // Mark that we need to clear to single selection on mouse release (if no drag)
                            preClickSelection.clear();
                            preClickSelection.add(clickedIndex); // Store just the clicked index

                            // Keep multi-selection visible (don't clear yet)
                            lastClickedIndex = clickedIndex;

                            // Prepare for potential drag
                            dragStartIndex = clickedIndex;
                            dragStartX = mouseX;
                            dragStartY = mouseY;
                        } else {
                            // Single selection or new selection
                            preClickSelection.clear(); // No special handling needed
                            selectedIndices.clear();
                            selectedIndices.add(clickedIndex);
                            lastClickedIndex = clickedIndex;

                            // Prepare for potential drag
                            dragStartIndex = clickedIndex;
                            dragStartX = mouseX;
                            dragStartY = mouseY;
                        }
                    }
                    updateSelectionButtons();
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Handle confirm popup scroll first
        if (confirmPopup != null) {
            if (confirmPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }

        // Block scrolling when other popup is active
        if (activePopup != null) {
            return true;
        }

        int listHeight = this.height - (PADDING * 4 + BUTTON_HEIGHT + 10) - PADDING * 2;
        int maxVisibleItems = listHeight / ITEM_HEIGHT;
        int maxScroll = Math.max(0, entries.size() - maxVisibleItems);

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)verticalAmount));

        // Update scrollbar to match
        if (scrollBar != null && maxScroll > 0) {
            scrollBar.setScrollPercentage((double)scrollOffset / maxScroll);
        }

        return true;
    }

    /**
     * Perform undo operation
     */
    private void performUndo() {
        if (undoStack.isEmpty()) {
            if (toastManager != null) {
                toastManager.showInfo("Nothing to undo");
            }
            return;
        }

        FileAction action = undoStack.removeLast();
        boolean success = false;
        String actionName = "";

        switch (action.type) {
            case MOVE -> {
                actionName = "move";
                success = undoMove(action);
            }
            case DELETE -> {
                actionName = "delete";
                success = undoDelete(action);
            }
            case RENAME -> {
                actionName = "rename";
                success = undoRename(action);
            }
            case CREATE_FOLDER -> {
                actionName = "folder creation";
                success = undoCreateFolder(action);
            }
        }

        if (success) {
            redoStack.add(action);
            if (redoStack.size() > MAX_UNDO_HISTORY) {
                redoStack.removeFirst();
            }
            if (toastManager != null) {
                toastManager.showSuccess("Undid " + actionName);
            }
            loadEntries();
        } else {
            if (toastManager != null) {
                toastManager.showError("Failed to undo " + actionName);
            }
        }
    }

    /**
     * Perform redo operation
     */
    private void performRedo() {
        if (redoStack.isEmpty()) {
            if (toastManager != null) {
                toastManager.showInfo("Nothing to redo");
            }
            return;
        }

        FileAction action = redoStack.removeLast();
        boolean success = false;
        String actionName = "";

        switch (action.type) {
            case MOVE -> {
                actionName = "move";
                success = redoMove(action);
            }
            case DELETE -> {
                actionName = "delete";
                success = redoDelete(action);
            }
            case RENAME -> {
                actionName = "rename";
                success = redoRename(action);
            }
            case CREATE_FOLDER -> {
                actionName = "folder creation";
                success = redoCreateFolder(action);
            }
        }

        if (success) {
            undoStack.add(action);
            if (undoStack.size() > MAX_UNDO_HISTORY) {
                undoStack.removeFirst();
            }
            if (toastManager != null) {
                toastManager.showSuccess("Redid " + actionName);
            }
            loadEntries();
        } else {
            if (toastManager != null) {
                toastManager.showError("Failed to redo " + actionName);
            }
        }
    }

    private boolean undoMove(FileAction action) {
        // Move files back from destination to source
        boolean allSuccess = true;
        for (FileOperation op : action.operations) {
            if (op.destination.exists()) {
                boolean success = op.destination.renameTo(op.source);
                if (!success) allSuccess = false;
            } else {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private boolean redoMove(FileAction action) {
        // Move files from source to destination again
        boolean allSuccess = true;
        for (FileOperation op : action.operations) {
            if (op.source.exists()) {
                boolean success = op.source.renameTo(op.destination);
                if (!success) allSuccess = false;
            } else {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private boolean undoDelete(FileAction action) {
        // Restore files from trash back to original location
        boolean allSuccess = true;
        for (FileOperation op : action.operations) {
            // op.source is original location, op.destination is trash location
            if (op.destination.exists()) {
                // Check if something already exists at the original location
                if (op.source.exists()) {
                    System.err.println("Cannot restore - file already exists at: " + op.source.getAbsolutePath());
                    allSuccess = false;
                    continue;
                }
                boolean success = op.destination.renameTo(op.source);
                if (!success) {
                    System.err.println("Failed to restore from trash: " + op.destination.getAbsolutePath());
                    allSuccess = false;
                } else {
                    System.out.println("Restored from trash: " + op.source.getName());
                }
            } else {
                System.err.println("Trash file not found: " + op.destination.getAbsolutePath());
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private boolean redoDelete(FileAction action) {
        // Move files back to trash (re-delete)
        boolean allSuccess = true;
        for (FileOperation op : action.operations) {
            // op.source is original location, op.destination is trash location
            if (op.source.exists()) {
                boolean success = op.source.renameTo(op.destination);
                if (!success) {
                    System.err.println("Failed to re-delete: " + op.source.getAbsolutePath());
                    allSuccess = false;
                } else {
                    System.out.println("Re-deleted: " + op.source.getName());
                }
            } else {
                System.err.println("File not found for re-delete: " + op.source.getAbsolutePath());
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private boolean undoRename(FileAction action) {
        // Rename back from destination to source
        if (action.operations.isEmpty()) return false;
        FileOperation op = action.operations.getFirst();
        if (op.destination.exists()) {
            return op.destination.renameTo(op.source);
        }
        return false;
    }

    private boolean redoRename(FileAction action) {
        // Rename from source to destination again
        if (action.operations.isEmpty()) return false;
        FileOperation op = action.operations.getFirst();
        if (op.source.exists()) {
            return op.source.renameTo(op.destination);
        }
        return false;
    }

    private boolean undoCreateFolder(FileAction action) {
        // Delete the created folder (only if empty)
        if (action.operations.isEmpty()) return false;
        FileOperation op = action.operations.getFirst();
        if (op.destination.exists() && op.destination.isDirectory()) {
            File[] contents = op.destination.listFiles();
            if (contents == null || contents.length == 0) {
                return op.destination.delete();
            } else {
                if (toastManager != null) {
                    toastManager.showWarning("Cannot undo - folder is not empty");
                }
                return false;
            }
        }
        return false;
    }

    private boolean redoCreateFolder(FileAction action) {
        // Create the folder again
        if (action.operations.isEmpty()) return false;
        FileOperation op = action.operations.getFirst();
        if (!op.destination.exists()) {
            return op.destination.mkdir();
        }
        return false;
    }

    /**
     * Add an action to the undo stack and clear redo stack
     */
    private void addUndoAction(FileAction action) {
        undoStack.add(action);
        if (undoStack.size() > MAX_UNDO_HISTORY) {
            FileAction removed = undoStack.removeFirst();
            // If removing a DELETE action, permanently delete the trash files
            if (removed.type == FileAction.Type.DELETE) {
                cleanupTrashFiles(removed);
            }
        }
        // Clear redo stack and clean up any DELETE actions in it
        for (FileAction redoAction : redoStack) {
            if (redoAction.type == FileAction.Type.DELETE) {
                cleanupTrashFiles(redoAction);
            }
        }
        redoStack.clear();
    }

    /**
     * Permanently delete trash files for a DELETE action that's no longer undoable
     */
    private void cleanupTrashFiles(FileAction action) {
        if (action.type != FileAction.Type.DELETE) return;

        for (FileOperation op : action.operations) {
            // op.destination is the trash file location
            if (op.destination != null && op.destination.exists()) {
                if (op.wasDirectory) {
                    deleteDirectoryRecursively(op.destination);
                } else {
                    op.destination.delete();
                }
                System.out.println("Permanently deleted from trash: " + op.destination.getName());
            }
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


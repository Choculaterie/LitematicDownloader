package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.gui.widget.ConfirmPopup;
import com.choculaterie.gui.widget.CustomButton;
import com.choculaterie.gui.widget.ScrollBar;
import com.choculaterie.gui.widget.TextInputPopup;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

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
    private CustomButton backButton;
    private CustomButton settingsButton;
    private CustomButton upButton;
    private CustomButton newFolderButton;
    private CustomButton renameButton;
    private CustomButton deleteButton;
    private ScrollBar scrollBar;
    private TextInputPopup activePopup;
    private ConfirmPopup confirmPopup;

    private File currentDirectory;
    private File baseDirectory; // The schematic folder - don't allow going above this
    private List<FileEntry> entries = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedIndex = -1;

    // Breadcrumb segments for click detection
    private List<BreadcrumbSegment> breadcrumbSegments = new ArrayList<>();

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

    private static class FileEntry {
        final File file;
        final boolean isDirectory;

        FileEntry(File file) {
            this.file = file;
            this.isDirectory = file.isDirectory();
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

        loadEntries();
    }

    @Override
    protected void init() {
        super.init();

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

        // Up directory button (next to back button)
        upButton = new CustomButton(
                PADDING * 2 + BUTTON_HEIGHT,
                PADDING,
                60,
                BUTTON_HEIGHT,
                Text.literal("Up ↑"),
                button -> goUpDirectory()
        );
        this.addDrawableChild(upButton);

        // Update up button state (disabled at root)
        updateUpButtonState();

        // New Folder button (next to Up button)
        newFolderButton = new CustomButton(
                PADDING * 3 + BUTTON_HEIGHT + 60,
                PADDING,
                100,
                BUTTON_HEIGHT,
                Text.literal("+ New Folder"),
                button -> openNewFolderPopup()
        );
        this.addDrawableChild(newFolderButton);

        // Rename button (next to New Folder button)
        renameButton = new CustomButton(
                PADDING * 4 + BUTTON_HEIGHT + 60 + 100,
                PADDING,
                70,
                BUTTON_HEIGHT,
                Text.literal("Rename"),
                button -> openRenamePopup()
        );
        renameButton.active = false; // Disabled until something is selected
        this.addDrawableChild(renameButton);

        // Delete button (next to Rename button)
        deleteButton = new CustomButton(
                PADDING * 5 + BUTTON_HEIGHT + 60 + 100 + 70,
                PADDING,
                60,
                BUTTON_HEIGHT,
                Text.literal("Delete"),
                button -> handleDeleteClick()
        );
        deleteButton.active = false; // Disabled until something is selected
        this.addDrawableChild(deleteButton);

        // Settings button (top right)
        settingsButton = new CustomButton(
                this.width - PADDING - BUTTON_HEIGHT,
                PADDING,
                BUTTON_HEIGHT,
                BUTTON_HEIGHT,
                Text.literal("⚙"),
                button -> openSettings()
        );
        this.addDrawableChild(settingsButton);

        // Initialize scrollbar
        int listY = PADDING * 4 + BUTTON_HEIGHT + 10;
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
                activePopup.setErrorMessage("A folder with this name already exists");
            }
            return;
        }

        // Try to create the folder
        boolean success = newFolder.mkdir();
        if (success) {
            System.out.println("Created folder: " + newFolder.getAbsolutePath());
            closePopup();
            loadEntries(); // Refresh the list
        } else {
            if (activePopup != null) {
                activePopup.setErrorMessage("Failed to create folder");
            }
        }
    }

    private void closePopup() {
        activePopup = null;
        confirmPopup = null;
    }

    private void openRenamePopup() {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            return;
        }

        FileEntry entry = entries.get(selectedIndex);
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
                activePopup.setErrorMessage("A file/folder with this name already exists");
            }
            return;
        }

        // Try to rename
        boolean success = file.renameTo(newFile);
        if (success) {
            System.out.println("Renamed: " + file.getName() + " -> " + newName);
            closePopup();
            loadEntries();
        } else {
            if (activePopup != null) {
                activePopup.setErrorMessage("Failed to rename");
            }
        }
    }

    private void handleDeleteClick() {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            return;
        }

        FileEntry entry = entries.get(selectedIndex);

        // Check if shift is held - skip confirmation
        long windowHandle = this.client != null ? this.client.getWindow().getHandle() : 0;
        boolean shiftHeld = false;
        if (windowHandle != 0) {
            shiftHeld = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                       org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }

        if (shiftHeld) {
            // Delete immediately without confirmation
            deleteFile(entry.file);
        } else {
            // Show confirmation popup
            String itemType = entry.isDirectory ? "folder" : "file";
            confirmPopup = new ConfirmPopup(
                    this,
                    "Delete " + itemType + "?",
                    "Are you sure you want to delete \"" + entry.file.getName() + "\"?",
                    () -> {
                        deleteFile(entry.file);
                        closePopup();
                    },
                    this::closePopup
            );
        }
    }

    private void deleteFile(File file) {
        if (file == null) {
            return;
        }

        boolean success;
        if (file.isDirectory()) {
            // Delete directory recursively
            success = deleteDirectoryRecursively(file);
        } else {
            success = file.delete();
        }

        if (success) {
            System.out.println("Deleted: " + file.getAbsolutePath());
            loadEntries();
        } else {
            System.err.println("Failed to delete: " + file.getAbsolutePath());
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
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < entries.size();
        if (renameButton != null) {
            renameButton.active = hasSelection;
        }
        if (deleteButton != null) {
            deleteButton.active = hasSelection;
        }
    }

    private void loadEntries() {
        entries.clear();
        selectedIndex = -1;

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
                    entries.add(new FileEntry(file));
                }
            }
        }

        updateUpButtonState();
        updateScrollBar();
        updateSelectionButtons();
    }

    private void updateScrollBar() {
        if (scrollBar != null) {
            int listY = PADDING * 4 + BUTTON_HEIGHT + 10;
            int listHeight = this.height - listY - PADDING * 2;
            int maxVisibleItems = listHeight / ITEM_HEIGHT;

            scrollBar.setScrollData(entries.size() * ITEM_HEIGHT, maxVisibleItems * ITEM_HEIGHT);

            // Update scroll offset from scrollbar percentage
            int maxScroll = Math.max(0, entries.size() - maxVisibleItems);
            scrollOffset = (int)(scrollBar.getScrollPercentage() * maxScroll);
        }
    }

    private void goUpDirectory() {
        if (currentDirectory != null && baseDirectory != null) {
            // Don't allow going above the base directory
            try {
                String currentPath = currentDirectory.getCanonicalPath();
                String basePath = baseDirectory.getCanonicalPath();

                // Only go up if we're not at the base directory
                if (!currentPath.equals(basePath)) {
                    File parent = currentDirectory.getParentFile();
                    if (parent != null && parent.exists()) {
                        currentDirectory = parent;
                        loadEntries();
                        scrollOffset = 0;
                        updateUpButtonState();
                    }
                }
            } catch (Exception e) {
                System.err.println("Error navigating up: " + e.getMessage());
            }
        }
    }

    private void updateUpButtonState() {
        if (upButton != null && currentDirectory != null && baseDirectory != null) {
            try {
                String currentPath = currentDirectory.getCanonicalPath();
                String basePath = baseDirectory.getCanonicalPath();
                // Disable up button if we're at the base directory
                upButton.active = !currentPath.equals(basePath);
            } catch (Exception e) {
                upButton.active = true;
            }
        }
    }

    private String getRelativePathFromBase() {
        if (baseDirectory == null || currentDirectory == null) {
            return "";
        }

        try {
            String basePath = baseDirectory.getCanonicalPath();
            String currentPath = currentDirectory.getCanonicalPath();

            if (currentPath.equals(basePath)) {
                return "";
            }

            if (currentPath.startsWith(basePath)) {
                String relative = currentPath.substring(basePath.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return relative;
            }

            return currentPath;
        } catch (Exception e) {
            return "";
        }
    }

    private void renderBreadcrumb(DrawContext context, int mouseX, int mouseY) {
        breadcrumbSegments.clear();

        int breadcrumbY = PADDING * 3 + BUTTON_HEIGHT;
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
                pathSegments.add(0, dir); // Add at beginning
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

            // Draw segment with appropriate color
            int color;
            if (isCurrent) {
                color = 0xFFFFFFFF; // White for current
            } else if (isHovered) {
                color = 0xFF55AAFF; // Light blue when hovered
            } else {
                color = 0xFF88CCFF; // Blue for clickable
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

        // If popup is active, render buttons without hover effects
        boolean popupActive = activePopup != null || confirmPopup != null;
        int renderMouseX = popupActive ? -1 : mouseX;
        int renderMouseY = popupActive ? -1 : mouseY;

        super.render(context, renderMouseX, renderMouseY, delta);

        // Draw current path as clickable breadcrumb
        renderBreadcrumb(context, mouseX, mouseY);

        // Draw file/folder list
        int listY = PADDING * 4 + BUTTON_HEIGHT + 10;
        int listHeight = this.height - listY - PADDING * 2;
        int maxVisibleItems = listHeight / ITEM_HEIGHT;

        // Calculate list width (end before scrollbar)
        int listRightEdge = this.width - PADDING - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;

        // Draw list background (end before scrollbar)
        context.fill(PADDING, listY, listRightEdge, listY + listHeight, 0xFF151515);

        // Draw entries
        for (int i = scrollOffset; i < Math.min(entries.size(), scrollOffset + maxVisibleItems); i++) {
            FileEntry entry = entries.get(i);
            int itemY = listY + (i - scrollOffset) * ITEM_HEIGHT;

            // Check if mouse is over this item (only in list area, not scrollbar, and no popup active)
            boolean isHovered = !popupActive &&
                              mouseX >= PADDING && mouseX < listRightEdge &&
                              mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean isSelected = i == selectedIndex;

            // Draw item background (end before scrollbar)
            int bgColor = isSelected ? 0xFF404040 : (isHovered ? 0xFF2A2A2A : 0xFF1A1A1A);
            context.fill(PADDING + 2, itemY + 2, listRightEdge - 2, itemY + ITEM_HEIGHT - 2, bgColor);

            // Draw icon and name
            String icon = entry.isDirectory ? "📁" : "📄";
            String displayName = icon + " " + entry.file.getName();

            // Add file size for files
            if (!entry.isDirectory) {
                long sizeKB = entry.file.length() / 1024;
                displayName += " (" + sizeKB + " KB)";
            }

            context.drawTextWithShadow(
                    this.textRenderer,
                    displayName,
                    PADDING + 5,
                    itemY + 8,
                    0xFFFFFFFF
            );
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

        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        // Handle breadcrumb clicks
        int breadcrumbY = PADDING * 3 + BUTTON_HEIGHT;
        if (mouseY >= breadcrumbY - 2 && mouseY < breadcrumbY + 12) {
            for (BreadcrumbSegment segment : breadcrumbSegments) {
                if (mouseX >= segment.x && mouseX < segment.x + segment.width) {
                    // Navigate to this directory
                    currentDirectory = segment.directory;
                    loadEntries();
                    scrollOffset = 0;
                    return true;
                }
            }
        }

        // Handle file/folder list clicks (only in list area, not scrollbar)
        int listY = PADDING * 4 + BUTTON_HEIGHT + 10;
        int listHeight = this.height - listY - PADDING * 2;
        int listRightEdge = this.width - PADDING - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;

        if (mouseX >= PADDING && mouseX < listRightEdge &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int clickedIndex = scrollOffset + (int)((mouseY - listY) / ITEM_HEIGHT);

            if (clickedIndex >= 0 && clickedIndex < entries.size()) {
                if (button == 0) { // Left click
                    FileEntry entry = entries.get(clickedIndex);

                    if (entry.isDirectory) {
                        if (clickedIndex == selectedIndex && doubled) {
                            // Double click on directory - enter it
                            currentDirectory = entry.file;
                            loadEntries();
                            scrollOffset = 0;
                        } else {
                            // Single click - select
                            selectedIndex = clickedIndex;
                            updateSelectionButtons();
                        }
                    } else {
                        // File selected
                        selectedIndex = clickedIndex;
                        updateSelectionButtons();
                        // Could add file preview or actions here in the future
                    }
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Block scrolling when popup is active
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



    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        goBack();
    }
}


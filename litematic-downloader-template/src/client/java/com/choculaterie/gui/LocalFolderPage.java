package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.gui.widget.CustomButton;
import com.choculaterie.gui.widget.ScrollBar;
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
    private ScrollBar scrollBar;

    private File currentDirectory;
    private File baseDirectory; // The schematic folder - don't allow going above this
    private List<FileEntry> entries = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedIndex = -1;

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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fill the entire screen with dark grey background
        context.fill(0, 0, this.width, this.height, 0xFF202020);

        super.render(context, mouseX, mouseY, delta);

        // Draw current path (relative to base schematic folder)
        String relativePath = getRelativePathFromBase();
        String displayPath = relativePath.isEmpty() || relativePath.equals(".") ? "/" : "/" + relativePath;
        context.drawTextWithShadow(
                this.textRenderer,
                "Current: " + displayPath,
                PADDING,
                PADDING * 3 + BUTTON_HEIGHT,
                0xFFFFFFFF
        );

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

            // Check if mouse is over this item (only in list area, not scrollbar)
            boolean isHovered = mouseX >= PADDING && mouseX < listRightEdge &&
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
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (super.mouseClicked(click, doubled)) {
            return true;
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
                        }
                    } else {
                        // File selected
                        selectedIndex = clickedIndex;
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


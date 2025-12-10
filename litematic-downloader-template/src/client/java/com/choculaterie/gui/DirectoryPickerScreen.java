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
import java.util.function.Consumer;

public class DirectoryPickerScreen extends Screen {
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 25;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_PADDING = 2;

    private final Screen parentScreen;
    private final Consumer<String> onPathSelected;
    private File currentDirectory;
    private List<File> directories = new ArrayList<>();
    private int scrollOffset = 0;
    private CustomButton backButton;
    private CustomButton selectButton;
    private CustomButton upButton;
    private ScrollBar scrollBar;
    private int selectedIndex = -1;

    public DirectoryPickerScreen(Screen parentScreen, String startPath, Consumer<String> onPathSelected) {
        super(Text.literal("Select Directory"));
        this.parentScreen = parentScreen;
        this.onPathSelected = onPathSelected;

        // Start from the provided path or game directory
        File startDir = new File(startPath);
        if (!startDir.exists() || !startDir.isDirectory()) {
            startDir = new File(DownloadSettings.getInstance().getGameDirectory());
        }

        // Start one folder higher (parent directory)
        File parentDir = startDir.getParentFile();
        if (parentDir != null && parentDir.exists()) {
            this.currentDirectory = parentDir;
        } else {
            this.currentDirectory = startDir;
        }

        loadDirectories();
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
                button -> close()
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

        // Select current directory button (bottom)
        selectButton = new CustomButton(
                this.width / 2 - 100,
                this.height - PADDING - BUTTON_HEIGHT - PADDING,
                200,
                BUTTON_HEIGHT,
                Text.literal("Select This Folder"),
                button -> selectCurrentDirectory()
        );
        this.addDrawableChild(selectButton);

        // Initialize scrollbar
        int listY = PADDING * 4 + BUTTON_HEIGHT + 10;
        int listHeight = this.height - listY - BUTTON_HEIGHT - PADDING * 3;
        scrollBar = new ScrollBar(this.width - PADDING - SCROLLBAR_WIDTH, listY, listHeight);
        updateScrollBar();
    }

    private void loadDirectories() {
        directories.clear();
        selectedIndex = -1;

        if (currentDirectory != null && currentDirectory.exists()) {
            File[] files = currentDirectory.listFiles(File::isDirectory);
            if (files != null) {
                Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                directories.addAll(Arrays.asList(files));
            }
        }

        updateScrollBar();
    }

    private void updateScrollBar() {
        if (scrollBar != null) {
            int listY = PADDING * 4 + BUTTON_HEIGHT + 10;
            int listHeight = this.height - listY - BUTTON_HEIGHT - PADDING * 3;
            int maxVisibleItems = listHeight / ITEM_HEIGHT;

            scrollBar.setScrollData(directories.size() * ITEM_HEIGHT, maxVisibleItems * ITEM_HEIGHT);

            // Update scroll offset from scrollbar percentage
            int maxScroll = Math.max(0, directories.size() - maxVisibleItems);
            scrollOffset = (int)(scrollBar.getScrollPercentage() * maxScroll);
        }
    }

    private void goUpDirectory() {
        if (currentDirectory != null) {
            File parent = currentDirectory.getParentFile();
            if (parent != null && parent.exists()) {
                currentDirectory = parent;
                loadDirectories();
                scrollOffset = 0;
            }
        }
    }

    private void selectCurrentDirectory() {
        File directoryToSelect;

        // If a folder is highlighted (selected), use that one
        if (selectedIndex >= 0 && selectedIndex < directories.size()) {
            directoryToSelect = directories.get(selectedIndex);
        } else {
            // Otherwise, select the current directory
            directoryToSelect = currentDirectory;
        }

        if (directoryToSelect != null) {
            File gameDir = new File(DownloadSettings.getInstance().getGameDirectory());
            String relativePath = getRelativePath(gameDir, directoryToSelect);
            onPathSelected.accept(relativePath);
            close();
        }
    }

    private String getRelativePath(File base, File target) {
        try {
            String basePath = base.getCanonicalPath();
            String targetPath = target.getCanonicalPath();

            if (targetPath.startsWith(basePath)) {
                String relative = targetPath.substring(basePath.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return relative.isEmpty() ? "." : relative;
            }

            return targetPath;
        } catch (Exception e) {
            return target.getAbsolutePath();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fill background
        context.fill(0, 0, this.width, this.height, 0xFF202020);

        super.render(context, mouseX, mouseY, delta);

        // Draw current path
        String currentPath = currentDirectory != null ? currentDirectory.getAbsolutePath() : "";
        context.drawTextWithShadow(
                this.textRenderer,
                "Current: " + currentPath,
                PADDING,
                PADDING * 3 + BUTTON_HEIGHT,
                0xFFFFFFFF
        );

        // Draw directories list
        int listY = PADDING * 4 + BUTTON_HEIGHT + 10;
        int listHeight = this.height - listY - BUTTON_HEIGHT - PADDING * 3;
        int maxVisibleItems = listHeight / ITEM_HEIGHT;

        // Calculate list width (end before scrollbar)
        int listRightEdge = this.width - PADDING - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;

        // Draw list background (end before scrollbar)
        context.fill(PADDING, listY, listRightEdge, listY + listHeight, 0xFF151515);

        // Draw directories
        for (int i = scrollOffset; i < Math.min(directories.size(), scrollOffset + maxVisibleItems); i++) {
            File dir = directories.get(i);
            int itemY = listY + (i - scrollOffset) * ITEM_HEIGHT;

            // Check if mouse is over this item (only in list area, not scrollbar)
            boolean isHovered = mouseX >= PADDING && mouseX < listRightEdge &&
                              mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean isSelected = i == selectedIndex;

            // Draw item background (end before scrollbar)
            int bgColor = isSelected ? 0xFF404040 : (isHovered ? 0xFF2A2A2A : 0xFF1A1A1A);
            context.fill(PADDING + 2, itemY + 2, listRightEdge - 2, itemY + ITEM_HEIGHT - 2, bgColor);

            // Draw folder icon and name
            String displayName = "📁 " + dir.getName();
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
                int maxScroll = Math.max(0, directories.size() - maxVisibleItems);
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

        // Handle directory list clicks (only in list area, not scrollbar)
        int listY = PADDING * 4 + BUTTON_HEIGHT + 10;
        int listHeight = this.height - listY - BUTTON_HEIGHT - PADDING * 3;
        int listRightEdge = this.width - PADDING - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;

        if (mouseX >= PADDING && mouseX < listRightEdge &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int clickedIndex = scrollOffset + (int)((mouseY - listY) / ITEM_HEIGHT);

            if (clickedIndex >= 0 && clickedIndex < directories.size()) {
                if (button == 0) { // Left click
                    if (clickedIndex == selectedIndex && doubled) {
                        // Double click - enter directory
                        currentDirectory = directories.get(clickedIndex);
                        loadDirectories();
                        scrollOffset = 0;
                    } else {
                        // Single click - select
                        selectedIndex = clickedIndex;
                    }
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listHeight = this.height - (PADDING * 4 + BUTTON_HEIGHT + 10) - BUTTON_HEIGHT - PADDING * 3;
        int maxVisibleItems = listHeight / ITEM_HEIGHT;
        int maxScroll = Math.max(0, directories.size() - maxVisibleItems);

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)verticalAmount));

        // Update scrollbar to match
        if (scrollBar != null && maxScroll > 0) {
            scrollBar.setScrollPercentage((double)scrollOffset / maxScroll);
        }

        return true;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parentScreen);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}


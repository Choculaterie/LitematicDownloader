package com.choculaterie.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;


import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ConfirmationDeleteScreen extends Screen {
    private final String directoryName;
    private final List<File> filesToDelete;
    private final Consumer<Boolean> callback;
    private int scrollOffset = 0;
    private final int itemHeight = 14;

    // Tree visualization
    private final Map<File, Boolean> expanded = new HashMap<>();
    private final List<TreeEntry> treeEntries = new ArrayList<>();

    // Scrollbar-related fields
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

    public ConfirmationDeleteScreen(String directoryName, List<File> filesToDelete, Consumer<Boolean> callback) {
        super(Text.literal("Confirm Deletion"));
        this.directoryName = directoryName;
        this.filesToDelete = filesToDelete;
        this.callback = callback;

        // Build initial tree structure
        buildTreeEntries();
    }

    private static class TreeEntry {
        File file;
        int depth;
        boolean isVisible = true;

        TreeEntry(File file, int depth) {
            this.file = file;
            this.depth = depth;
        }
    }

    // In ConfirmationDeleteScreen.java, modify the buildTreeEntries() method:
    private void buildTreeEntries() {
        treeEntries.clear();

        // Find the root directory
        File rootDir = filesToDelete.get(0);
        while (rootDir.getParentFile() != null && filesToDelete.contains(rootDir.getParentFile())) {
            rootDir = rootDir.getParentFile();
        }

        // Start with all directories expanded by default
        for (File file : filesToDelete) {
            if (file.isDirectory()) {
                expanded.put(file, true);  // Expand all directories
            }
        }

        // Build the tree starting from the root
        buildTree(rootDir, 0);

        // Update visibility based on expanded state
        updateVisibility();
    }

    private void buildTree(File file, int depth) {
        treeEntries.add(new TreeEntry(file, depth));

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (filesToDelete.contains(child)) {
                        buildTree(child, depth + 1);
                    }
                }
            }
        }
    }

    private void updateVisibility() {
        boolean parentVisible = true;
        File currentParent = null;
        int currentDepth = -1;

        for (TreeEntry entry : treeEntries) {
            // If we're at a new depth level, check if parent is expanded
            if (entry.depth <= currentDepth) {
                parentVisible = true;
                currentParent = null;
            }

            // Set visibility based on parent's expanded state
            entry.isVisible = parentVisible;

            if (entry.file.isDirectory()) {
                // This becomes the new parent for next deeper level items
                currentParent = entry.file;
                currentDepth = entry.depth;

                // Check if this directory is expanded
                if (!expanded.getOrDefault(entry.file, false)) {
                    parentVisible = false;
                }
            }
        }
    }

    @Override
    protected void init() {
        int buttonWidth = 100;
        int buttonHeight = 20;
        int spacing = 10;

        // Calculate centered buttons at bottom
        int totalButtonWidth = buttonWidth * 2 + spacing;
        int startX = (this.width - totalButtonWidth) / 2;
        int buttonY = this.height - 30;

        // Confirm and Cancel buttons
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            this.callback.accept(false);
        }).dimensions(startX  + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Delete All"), button -> {
            this.callback.accept(true);
        }).dimensions(startX, buttonY, buttonWidth, buttonHeight).build());

        // Calculate scroll area
        int contentWidth = Math.min(600, this.width - 80);
        scrollAreaX = (this.width - contentWidth) / 2;
        scrollAreaY = 60;
        scrollAreaWidth = contentWidth;
        scrollAreaHeight = buttonY - scrollAreaY - 10;

        // Calculate visible tree entries for scroll height
        int visibleCount = 0;
        for (TreeEntry entry : treeEntries) {
            if (entry.isVisible) {
                visibleCount++;
            }
        }

        totalContentHeight = visibleCount * itemHeight;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {


        super.render(context, mouseX, mouseY, delta);

        // Draw title and warning
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFF5555);

        String warning = "The following items will be permanently deleted:";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(warning),
                this.width / 2, 35, 0xFFFFFFFF);

        // Count files and folders
        int fileCount = 0;
        int folderCount = 0;
        for (File file : filesToDelete) {
            if (file.isDirectory()) folderCount++;
            else fileCount++;
        }

        String summary = folderCount + " folder" + (folderCount != 1 ? "s" : "") +
                " and " + fileCount + " file" + (fileCount != 1 ? "s" : "");
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(summary),
                this.width / 2, scrollAreaY - 15, 0xFFCCCCCC);

        // Create clipping region for tree view
        context.enableScissor(
                scrollAreaX,
                scrollAreaY,
                scrollAreaX + scrollAreaWidth,
                scrollAreaY + scrollAreaHeight
        );

        // Draw tree view
        int y = scrollAreaY - scrollOffset;
        for (TreeEntry entry : treeEntries) {
            if (!entry.isVisible) continue;

            if (y + itemHeight >= scrollAreaY && y <= scrollAreaY + scrollAreaHeight) {
                // Calculate indentation
                int indent = entry.depth * 10;

                // Draw expand/collapse button for directories
                if (entry.file.isDirectory()) {
                    boolean isExpanded = expanded.getOrDefault(entry.file, false);
                    String expandChar = isExpanded ? "▼" : "▶";

                    // Check if mouse is hovering over the expand button
                    boolean isHovering = mouseX >= scrollAreaX + indent && mouseX <= scrollAreaX + indent + 10 &&
                            mouseY >= y && mouseY <= y + itemHeight;

                    context.drawTextWithShadow(this.textRenderer, Text.literal(expandChar),
                            scrollAreaX + indent, y, isHovering ? 0xFFFFFFFF : 0xFFAAAAAA);
                }

                // Draw icon
                String icon = entry.file.isDirectory() ? "📁 " : "📄 ";
                context.drawTextWithShadow(this.textRenderer, Text.literal(icon),
                        scrollAreaX + indent + 15, y, 0xFFFFFFFF);

                // Draw file name
                context.drawTextWithShadow(this.textRenderer, Text.literal(entry.file.getName()),
                        scrollAreaX + indent + 30, y, 0xFFFFFFFF);
            }

            y += itemHeight;
        }

        context.disableScissor();

        // Draw scrollbar if needed
        if (totalContentHeight > scrollAreaHeight) {
            int scrollBarWidth = 6;
            scrollBarHeight = Math.max(20, scrollAreaHeight * scrollAreaHeight / totalContentHeight);
            scrollBarX = scrollAreaX + scrollAreaWidth;
            scrollBarY = scrollAreaY + (int)((float)scrollOffset / (totalContentHeight - scrollAreaHeight)
                    * (scrollAreaHeight - scrollBarHeight));

            // Draw scrollbar background
            context.fill(scrollBarX, scrollAreaY,
                    scrollBarX + scrollBarWidth, scrollAreaY + scrollAreaHeight,
                    0x33FFFFFF);

            // Draw scrollbar handle
            boolean isHovering = mouseX >= scrollBarX && mouseX <= scrollBarX + scrollBarWidth &&
                    mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight;

            int scrollBarColor = isHovering || isScrolling ? 0xFFFFFFFF : 0xAAFFFFFF;
            context.fill(scrollBarX, scrollBarY,
                    scrollBarX + scrollBarWidth, scrollBarY + scrollBarHeight,
                    scrollBarColor);
        }


    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Handle tree expansion clicks
        if (mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {
            int index = getVisibleIndexAtY(mouseY);
            if (index >= 0 && index < treeEntries.size()) {
                TreeEntry entry = treeEntries.get(index);

                if (entry.file.isDirectory()) {
                    int indent = entry.depth * 10;

                    // Check if clicked on expand/collapse button
                    if (mouseX >= scrollAreaX + indent && mouseX <= scrollAreaX + indent + 10) {
                        expanded.put(entry.file, !expanded.getOrDefault(entry.file, false));
                        updateVisibility();

                        // Recalculate total height after visibility update
                        int visibleCount = 0;
                        for (TreeEntry e : treeEntries) {
                            if (e.isVisible) visibleCount++;
                        }
                        totalContentHeight = visibleCount * itemHeight;

                        return true;
                    }
                }
            }
        }

        // Handle scrollbar interaction
        if (totalContentHeight > scrollAreaHeight) {
            // Check if click is on scroll bar
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {

                isScrolling = true;
                lastMouseY = (int) mouseY;
                return true;
            }

            // Check if click is in scroll area but not on handle (jump scroll)
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

                // Calculate new scroll position based on click location
                float clickPercent = ((float) mouseY - scrollAreaY) / scrollAreaHeight;
                scrollOffset = (int) (clickPercent * (totalContentHeight - scrollAreaHeight));
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset));
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
                float dragPercentage = (float) (currentMouseY - lastMouseY) / Math.max(1, (scrollAreaHeight - scrollBarHeight));
                int scrollAmount = (int) (dragPercentage * (totalContentHeight - scrollAreaHeight));

                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset + scrollAmount));
                lastMouseY = currentMouseY;
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



    private int getVisibleIndexAtY(double mouseY) {
        int y = scrollAreaY - scrollOffset;
        int visibleIndex = 0;

        for (int i = 0; i < treeEntries.size(); i++) {
            if (treeEntries.get(i).isVisible) {
                if (mouseY >= y && mouseY < y + itemHeight) {
                    return i;
                }
                y += itemHeight;
                visibleIndex++;
            }
        }

        return -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

            if (totalContentHeight > scrollAreaHeight) {
                // Calculate scroll amount (10 pixels per mouse wheel tick)
                int scrollAmount = (int)(-verticalAmount * 20);

                // Update scroll position
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight,
                        scrollOffset + scrollAmount));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
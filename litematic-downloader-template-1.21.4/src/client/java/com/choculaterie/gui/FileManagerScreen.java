package com.choculaterie.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileManagerScreen extends Screen {
    private final Screen parentScreen;
    private List<File> displayedFiles = new ArrayList<>();
    private int scrollOffset = 0;
    private final int itemHeight = 24;
    private final int maxVisibleItems;
    private String schematicsRootPath;
    private File currentDirectory;
    private List<String> breadcrumbParts = new ArrayList<>();

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

    public FileManagerScreen(Screen parentScreen) {
        super(Text.literal("Schematic File Manager"));
        this.parentScreen = parentScreen;
        this.maxVisibleItems = (MinecraftClient.getInstance().getWindow().getScaledHeight() - 100) / itemHeight;
        initializeRootPath();
    }

    private void initializeRootPath() {
        try {
            // Try to get Minecraft's game directory
            File gameDir = MinecraftClient.getInstance().runDirectory;

            // Set the appropriate path based on whether we're in development or production
            if (gameDir != null) {
                schematicsRootPath = new File(gameDir, "schematics").getAbsolutePath();
            } else {
                // Fall back to hardcoded paths if game directory isn't available
                boolean isDevelopment = System.getProperty("dev.env", "false").equals("true");
                String homeDir = System.getProperty("user.home");

                if (isDevelopment) {
                    schematicsRootPath = homeDir + File.separator + "Downloads" + File.separator +
                            "litematic-downloader-template-1.21.4" + File.separator +
                            "run" + File.separator + "schematics";
                } else {
                    schematicsRootPath = homeDir + File.separator + "AppData" + File.separator +
                            "Roaming" + File.separator + ".minecraft" + File.separator +
                            "schematics";
                }
            }

            File directory = new File(schematicsRootPath);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            currentDirectory = directory;
            updateBreadcrumbs();
            loadFilesFromCurrentDirectory();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadFilesFromCurrentDirectory() {
        try {
            displayedFiles.clear();

            File[] files = currentDirectory.listFiles();
            if (files != null) {
                // Sort: directories first, then files, both alphabetically
                Arrays.sort(files, (f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                });

                // Add all directories
                for (File file : files) {
                    if (file.isDirectory()) {
                        displayedFiles.add(file);
                    }
                }

                // Add all .litematic files
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".litematic")) {
                        displayedFiles.add(file);
                    }
                }
            }

            // Reset scroll position when changing directories
            scrollOffset = 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateBreadcrumbs() {
        breadcrumbParts.clear();

        // Add "schematic" as the root
        breadcrumbParts.add("schematics");

        // If we're in the root directory, we're done
        if (currentDirectory.getAbsolutePath().equals(schematicsRootPath)) {
            return;
        }

        // Otherwise, add each part of the path after "schematics"
        String relativePath = currentDirectory.getAbsolutePath().substring(schematicsRootPath.length());
        if (relativePath.startsWith(File.separator)) {
            relativePath = relativePath.substring(1);
        }

        if (!relativePath.isEmpty()) {
            String[] parts = relativePath.split(File.separator.replace("\\", "\\\\"));
            for (String part : parts) {
                if (!part.isEmpty()) {
                    breadcrumbParts.add(part);
                }
            }
        }
    }

    private void navigateToFolder(File folder) {
        if (folder.isDirectory() && folder.exists()) {
            currentDirectory = folder;
            updateBreadcrumbs();
            loadFilesFromCurrentDirectory();
            updateScrollbarDimensions();
        }
    }

    private void navigateUp() {
        File parent = currentDirectory.getParentFile();
        // Don't go above the schematics root
        if (parent != null && currentDirectory.getAbsolutePath().startsWith(schematicsRootPath)) {
            if (parent.getAbsolutePath().startsWith(schematicsRootPath)) {
                navigateToFolder(parent);
            } else {
                // If trying to go above root, just go to root
                navigateToFolder(new File(schematicsRootPath));
            }
        }
    }

    private void navigateToBreadcrumb(int index) {
        if (index < 0 || index >= breadcrumbParts.size()) {
            return;
        }

        // Build the path up to the selected breadcrumb
        StringBuilder path = new StringBuilder(schematicsRootPath);
        for (int i = 1; i <= index; i++) { // Start at 1 to skip "schematics"
            path.append(File.separator).append(breadcrumbParts.get(i));
        }

        navigateToFolder(new File(path.toString()));
    }

    @Override
    protected void init() {
        int buttonWidth = 20;
        int buttonHeight = 20;
        int backButtonX = 10;
        int backButtonY = 10;

        // Back to parent screen button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("←"), button -> {
            MinecraftClient.getInstance().setScreen(parentScreen);
        }).dimensions(backButtonX, backButtonY, buttonWidth, buttonHeight).build());

        // Refresh button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("🔄"), button -> {
            loadFilesFromCurrentDirectory();
            updateScrollbarDimensions();
        }).dimensions(this.width - 40, 10, 20, 20).build());

        // Up directory button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("↑"), button -> {
            navigateUp();
        }).dimensions(this.width - 65, 10, 20, 20).build());

        // New folder button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("📁+"), button -> {
            showCreateFolderScreen();
        }).dimensions(this.width - 90, 10, 20, 20).build());

        updateScrollbarDimensions();
    }

    private void showCreateFolderScreen() {
        MinecraftClient.getInstance().setScreen(new ConfirmationCreateFolderScreen(
                this,
                currentDirectory,
                (success) -> {
                    if (success) {
                        loadFilesFromCurrentDirectory();
                        updateScrollbarDimensions();
                    }
                }
        ));
    }

    private void startRenaming(File file) {
        MinecraftClient.getInstance().setScreen(new RenameItemSceen(
                this,
                file,
                (success) -> {
                    if (success) {
                        loadFilesFromCurrentDirectory(); // Refresh listing
                        updateScrollbarDimensions();
                    }
                }
        ));
    }

    private void updateScrollbarDimensions() {
        // Define content width for centering
        int contentWidth = Math.min(600, this.width - 80); // Max width or screen width minus margins

        // Center the content horizontally
        scrollAreaX = (this.width - contentWidth) / 2;
        scrollAreaY = 60;
        scrollAreaWidth = contentWidth; // Fixed content width
        scrollAreaHeight = this.height - scrollAreaY - 10;

        // Calculate total content height
        totalContentHeight = displayedFiles.size() * itemHeight;

        // Reset scroll offset if it's now out of bounds
        if (totalContentHeight <= scrollAreaHeight) {
            scrollOffset = 0; // Reset scroll if content fits without scrolling
        } else {
            scrollOffset = Math.min(scrollOffset, totalContentHeight - scrollAreaHeight);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Call super.render() after our custom drawing so widgets appear on top
        super.render(context, mouseX, mouseY, delta);

        // Draw title and standard UI elements first
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        // Draw breadcrumb path (centered)
        int breadcrumbY = 40;
        int pathColor = 0xCCCCCC;
        int separatorColor = 0x999999;

        // Calculate total width of breadcrumb path for centering
        int totalBreadcrumbWidth = 0;
        for (int i = 0; i < breadcrumbParts.size(); i++) {
            totalBreadcrumbWidth += this.textRenderer.getWidth(breadcrumbParts.get(i));
            if (i < breadcrumbParts.size() - 1) {
                totalBreadcrumbWidth += this.textRenderer.getWidth(" > ");
            }
        }

        // Center the breadcrumb
        int breadcrumbX = (this.width - totalBreadcrumbWidth) / 2;

        for (int i = 0; i < breadcrumbParts.size(); i++) {
            String part = breadcrumbParts.get(i);
            boolean isClickable = true; // All parts are clickable

            // Determine color based on if it's clickable and if mouse is hovering
            int color = isClickable ? pathColor : separatorColor;
            boolean isHovering = false;

            if (isClickable) {
                int textWidth = this.textRenderer.getWidth(part);
                isHovering = mouseX >= breadcrumbX && mouseX <= breadcrumbX + textWidth &&
                        mouseY >= breadcrumbY && mouseY <= breadcrumbY + 10;
                color = isHovering ? 0xFFFFFF : pathColor;
            }

            // Draw this part
            context.drawTextWithShadow(this.textRenderer, Text.literal(part), breadcrumbX, breadcrumbY, color);
            int partWidth = this.textRenderer.getWidth(part);
            breadcrumbX += partWidth;

            // Draw separator except for last item
            if (i < breadcrumbParts.size() - 1) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(" > "), breadcrumbX, breadcrumbY, separatorColor);
                breadcrumbX += this.textRenderer.getWidth(" > ");
            }
        }

        // Create clipping region for file list
        context.enableScissor(
                scrollAreaX,
                scrollAreaY,
                scrollAreaX + scrollAreaWidth,
                scrollAreaY + scrollAreaHeight
        );

        // Draw files list
        if (displayedFiles.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No files found"),
                    this.width / 2, scrollAreaY + 20, 0xCCCCCC);
        } else {
            int y = scrollAreaY - scrollOffset;
            for (int i = 0; i < displayedFiles.size(); i++) {
                File file = displayedFiles.get(i);

                // Only render if visible in the scroll area
                if (y + itemHeight >= scrollAreaY && y <= scrollAreaY + scrollAreaHeight) {
                    // Draw folder icon or file icon
                    String icon = file.isDirectory() ? "📁 " : "📄 ";

                    // Draw icon and name
                    context.drawTextWithShadow(this.textRenderer, Text.literal(icon + file.getName()),
                            scrollAreaX + 10, y, 0xFFFFFF);

                    // For files, show file size
                    if (!file.isDirectory()) {
                        long fileSizeKB = file.length() / 1024;
                        String sizeText = fileSizeKB + " KB";
                        int sizeWidth = this.textRenderer.getWidth(sizeText);
                        context.drawTextWithShadow(this.textRenderer, Text.literal(sizeText),
                                scrollAreaX + scrollAreaWidth - sizeWidth - 25, y, 0xCCCCCC);
                    }

                    // Draw delete button (X)
                    int deleteX = scrollAreaX + scrollAreaWidth - 15;
                    int deleteY = y;
                    boolean isDeleteHovered = mouseX >= deleteX - 2 && mouseX <= deleteX + 8 &&
                            mouseY >= deleteY && mouseY <= deleteY + 10;
                    int deleteColor = isDeleteHovered ? 0xFF5555 : 0xAA5555;
                    context.drawTextWithShadow(this.textRenderer, Text.literal("✕"), deleteX, deleteY, deleteColor);

                    // Draw separator line
                    context.fill(scrollAreaX, y + itemHeight - 5, scrollAreaX + scrollAreaWidth - 10, y + itemHeight - 4, 0x22FFFFFF);
                }

                y += itemHeight;
            }
        }

        context.disableScissor();

        // Draw scrollbar if needed
        if (totalContentHeight > scrollAreaHeight) {
            int scrollBarWidth = 6;
            scrollBarHeight = Math.max(20, scrollAreaHeight * scrollAreaHeight / totalContentHeight);
            scrollBarX = scrollAreaX + scrollAreaWidth;
            scrollBarY = scrollAreaY + (int)((float)scrollOffset / (totalContentHeight - scrollAreaHeight)
                    * (scrollAreaHeight - scrollBarHeight));

            // Draw scroll bar background - only as wide as the scrollbar itself
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
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle right click (button 1)
        if (button == 1 && mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

            // Calculate which item was clicked
            int index = (int)((mouseY - scrollAreaY + scrollOffset) / itemHeight);
            if (index >= 0 && index < displayedFiles.size()) {
                File clickedFile = displayedFiles.get(index);
                startRenaming(clickedFile);
                return true;
            }
        }

        // Handle breadcrumb clicks - need to update for centered breadcrumbs
        if (button == 0 && mouseY >= 40 && mouseY <= 50) {
            // Recalculate total breadcrumb width
            int totalBreadcrumbWidth = 0;
            for (int i = 0; i < breadcrumbParts.size(); i++) {
                totalBreadcrumbWidth += this.textRenderer.getWidth(breadcrumbParts.get(i));
                if (i < breadcrumbParts.size() - 1) {
                    totalBreadcrumbWidth += this.textRenderer.getWidth(" > ");
                }
            }

            // Start position of centered breadcrumb
            int x = (this.width - totalBreadcrumbWidth) / 2;

            for (int i = 0; i < breadcrumbParts.size(); i++) {
                String part = breadcrumbParts.get(i);
                int textWidth = this.textRenderer.getWidth(part);

                if (mouseX >= x && mouseX <= x + textWidth) {
                    // Click on breadcrumb
                    navigateToBreadcrumb(i);
                    return true;
                }

                x += textWidth;

                // Skip the separator
                if (i < breadcrumbParts.size() - 1) {
                    x += this.textRenderer.getWidth(" > ");
                }
            }
        }

        if (button == 0 && mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

            // Calculate which item was clicked
            int index = (int)((mouseY - scrollAreaY + scrollOffset) / itemHeight);
            if (index >= 0 && index < displayedFiles.size()) {
                File clickedFile = displayedFiles.get(index);

                // Check if delete button was clicked
                int deleteX = scrollAreaX + scrollAreaWidth - 15;
                int lineY = scrollAreaY - scrollOffset + (index * itemHeight);

                if (mouseX >= deleteX - 2 && mouseX <= deleteX + 8 &&
                        mouseY >= lineY && mouseY <= lineY + 10) {
                    // Delete button clicked
                    handleDeleteRequest(clickedFile);
                    return true;
                } else if (clickedFile.isDirectory()) {
                    // Directory clicked - navigate into it
                    navigateToFolder(clickedFile);
                    return true;
                }
            }
        }

        // Handle scrollbar interaction
        if (button == 0 && totalContentHeight > scrollAreaHeight) { // Left click
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
                float clickPercent = ((float)mouseY - scrollAreaY) / scrollAreaHeight;
                scrollOffset = (int)(clickPercent * (totalContentHeight - scrollAreaHeight));
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleDeleteRequest(File fileToDelete) {
        if (fileToDelete.isDirectory()) {
            // For directories, always show cascade delete confirmation (even if empty)
            showCascadeDeleteConfirmation(fileToDelete);
        } else {
            // For files, show simple confirmation
            MinecraftClient.getInstance().setScreen(new ConfirmationScreen(
                    Text.literal("Confirm Delete"),
                    Text.literal("Are you sure you want to delete " + fileToDelete.getName() + "?"),
                    (confirmed) -> {
                        if (confirmed) {
                            boolean success = deleteFile(fileToDelete);
                            if (success) {
                                loadFilesFromCurrentDirectory(); // Refresh listing
                                updateScrollbarDimensions();
                            }
                        }
                        MinecraftClient.getInstance().setScreen(this); // Return to this screen
                    }
            ));
        }
    }

    private void showCascadeDeleteConfirmation(File directory) {
        List<File> filesToDelete = new ArrayList<>();
        collectFilesToDelete(directory, filesToDelete);

        MinecraftClient.getInstance().setScreen(new ConfirmationDeleteScreen(
                directory.getName(),
                filesToDelete,
                (confirmed) -> {
                    if (confirmed) {
                        // Delete all files in reverse order (deepest first)
                        boolean allDeleted = true;
                        for (int i = filesToDelete.size() - 1; i >= 0; i--) {
                            boolean success = deleteFile(filesToDelete.get(i));
                            if (!success) {
                                allDeleted = false;
                            }
                        }

                        // Refresh the file listing regardless of success
                        loadFilesFromCurrentDirectory();
                        updateScrollbarDimensions();
                    }
                    MinecraftClient.getInstance().setScreen(this); // Return to this screen
                }
        ));
    }

    private void collectFilesToDelete(File root, List<File> filesToDelete) {
        if (root.isDirectory()) {
            File[] files = root.listFiles();
            if (files != null) {
                for (File file : files) {
                    collectFilesToDelete(file, filesToDelete);
                }
            }
        }
        filesToDelete.add(root); // Add the file/directory itself after its contents
    }

    private boolean deleteFile(File file) {
        try {
            if (file.isDirectory()) {
                // Make sure directory is empty before trying to delete it
                File[] contents = file.listFiles();
                if (contents != null && contents.length > 0) {
                    // This should not happen if we're deleting in the correct order
                    // (deepest files first), but just in case, try to delete contents
                    for (File f : contents) {
                        deleteFile(f);
                    }
                }
            }
            return file.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isScrolling) {
            if (mouseY != lastMouseY) {
                // Calculate how far we've dragged as a percentage of the scroll area
                float dragPercentage = (float)(mouseY - lastMouseY) / (scrollAreaHeight - scrollBarHeight);

                // Convert that to a scroll amount
                int scrollAmount = (int)(dragPercentage * (totalContentHeight - scrollAreaHeight));

                // Update scroll position
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight,
                        scrollOffset + scrollAmount));

                lastMouseY = (int) mouseY;
            }
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isScrolling) {
            isScrolling = false;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Only handle scrolling if mouse is in the scroll area
        if (mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

            if (totalContentHeight > scrollAreaHeight) {
                // Calculate scroll amount (20 pixels per mouse wheel tick)
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
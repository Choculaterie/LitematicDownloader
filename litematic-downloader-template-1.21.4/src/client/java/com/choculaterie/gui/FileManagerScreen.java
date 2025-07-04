package com.choculaterie.gui;

import com.choculaterie.networking.LitematicHttpClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.util.InputUtil;

import java.awt.*;
import java.io.File;
import java.util.*;
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

    // Drag and drop related fields
    private File draggedFile = null;
    private boolean isDragging = false;
    private int dragStartX;
    private int dragStartY;
    private int dragCurrentX;
    private int dragCurrentY;
    private File dropTargetFolder = null;

    // Search fields
    private TextFieldWidget searchField;
    private String searchTerm = "";
    private List<File> allFiles = new ArrayList<>(); // Unfiltered file list
    private boolean isRecursiveSearch = false; // Whether current results are from recursive search
    private Map<File, String> filePathMap = new HashMap<>(); // Maps files to their relative paths

    // Litematic upload tracking
    private Set<File> filesBeingUploaded = new HashSet<>();

    public FileManagerScreen(Screen parentScreen) {
        super(Text.literal("")); //Title (removed because of search bar)
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
            allFiles.clear();
            displayedFiles.clear();

            File[] files = currentDirectory.listFiles();
            if (files != null) {
                // Sort: directories first, then files, both alphabetically
                Arrays.sort(files, (f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                });

                // Add all files and directories
                for (File file : files) {
                    allFiles.add(file);
                }
            }

            // Apply filter
            filterFiles();

            // Reset recursive search state if not searching
            if (searchTerm.isEmpty()) {
                isRecursiveSearch = false;
            }

            // Reset scroll position when changing directories
            scrollOffset = 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void filterFiles() {
        displayedFiles.clear();
        filePathMap.clear();

        if (isRecursiveSearch && !searchTerm.isEmpty()) {
            // Perform recursive search
            List<File> searchResults = new ArrayList<>();
            searchAllFiles(new File(schematicsRootPath), searchResults, "");
            displayedFiles.addAll(searchResults);
        } else {
            // Normal directory browsing - filter current directory only
            for (File file : allFiles) {
                if (searchTerm.isEmpty() || file.getName().toLowerCase().contains(searchTerm)) {
                    displayedFiles.add(file);
                }
            }
        }

        // Sort results: directories first, then files
        displayedFiles.sort((f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.getName().compareToIgnoreCase(f2.getName());
        });
    }

    private void updateBreadcrumbs() {
        breadcrumbParts.clear();
        breadcrumbItems.clear();

        // Add "schematics" as the root
        breadcrumbParts.add("schematics");
        breadcrumbItems.add(new BreadcrumbItem("schematics", new File(schematicsRootPath)));

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
            StringBuilder pathBuilder = new StringBuilder(schematicsRootPath);

            for (String part : parts) {
                if (!part.isEmpty()) {
                    breadcrumbParts.add(part);
                    pathBuilder.append(File.separator).append(part);
                    breadcrumbItems.add(new BreadcrumbItem(part, new File(pathBuilder.toString())));
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

        // Open in OS button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("📂"), button -> {
            openFolderInOS(currentDirectory);
        }).dimensions(this.width - 115, 10, 20, 20).build());

        // Add search field
        int searchFieldWidth = 200;
        searchField = new TextFieldWidget(
                this.textRenderer,
                (this.width - searchFieldWidth) / 2,
                10,
                searchFieldWidth,
                20,
                Text.literal("")
        );
        searchField.setMaxLength(50);
        searchField.setPlaceholder(Text.literal("Search files..."));
        searchField.setText(searchTerm);
        searchField.setChangedListener(this::onSearchChanged);
        this.addSelectableChild(searchField);

        // Add clear button (X) instead of toggle
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("✕"),
                        button -> {
                            searchField.setText("");
                            onSearchChanged("");
                        })
                .dimensions((this.width + searchFieldWidth) / 2 + 5, 10, 20, 20)
                .build());

        updateScrollbarDimensions();
    }

    private void onSearchChanged(String newSearchTerm) {
        searchTerm = newSearchTerm.toLowerCase().trim();

        // If search is cleared, reset to normal directory view
        if (searchTerm.isEmpty()) {
            isRecursiveSearch = false;
            loadFilesFromCurrentDirectory();
        } else {
            isRecursiveSearch = true;
            filterFiles();
        }

        scrollOffset = 0; // Reset scroll position for new search results
        updateScrollbarDimensions();
    }

    private void searchAllFiles(File directory, List<File> results, String relativePath) {
        File[] files = directory.listFiles();
        if (files != null) {
            // The directory itself is no longer added to search results

            for (File file : files) {
                String currentPath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();

                if (file.isDirectory()) {
                    // No longer add directories to search results
                    // Just continue searching recursively
                    searchAllFiles(file, results, currentPath);
                } else if (file.isFile()) {
                    // Only add files that match the search term
                    if (file.getName().toLowerCase().contains(searchTerm)) {
                        results.add(file);
                        // Include "schematics" in the path
                        String filePath = "schematics/" + relativePath;
                        filePathMap.put(file, filePath);
                    }
                }
            }
        }
    }

    private void openFolderInOS(File folder) {
        try {
            if (folder.exists()) {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(folder);
                } else {
                    // Fallback to OS-specific commands
                    String os = System.getProperty("os.name").toLowerCase();

                    ProcessBuilder builder = new ProcessBuilder();
                    if (os.contains("win")) {
                        // Windows
                        builder.command("explorer.exe", folder.getAbsolutePath());
                    } else if (os.contains("mac") || os.contains("darwin")) {
                        // macOS
                        builder.command("open", folder.getAbsolutePath());
                    } else if (os.contains("nix") || os.contains("nux")) {
                        // Linux/Unix
                        builder.command("xdg-open", folder.getAbsolutePath());
                    } else {
                        System.out.println("Cannot open folder: Unsupported OS");
                        return;
                    }

                    builder.start();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        searchField.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("🔍"),
                searchField.getX() - 15,
                searchField.getY() + 5,
                0xAAAAAA
        );

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

            // Store position information for drag and drop
            BreadcrumbItem item = breadcrumbItems.get(i);
            item.x = breadcrumbX;
            item.width = this.textRenderer.getWidth(part);

            // Determine color based on if it's clickable and if mouse is hovering
            int color = isClickable ? pathColor : separatorColor;
            boolean isHovering = false;

            if (isClickable) {
                int textWidth = item.width;
                isHovering = mouseX >= breadcrumbX && mouseX <= breadcrumbX + textWidth &&
                        mouseY >= breadcrumbY && mouseY <= breadcrumbY + 10;
                color = isHovering ? 0xFFFFFF : pathColor;

                // Highlight for potential drop target
                if (isDragging && draggedFile != null &&
                        mouseX >= breadcrumbX && mouseX <= breadcrumbX + textWidth &&
                        mouseY >= breadcrumbY - 5 && mouseY <= breadcrumbY + 15) {
                    context.fill(breadcrumbX - 2, breadcrumbY - 2,
                            breadcrumbX + textWidth + 2, breadcrumbY + 12,
                            0x3300FF00); // Green highlight
                }
            }

            // Draw this part
            context.drawTextWithShadow(this.textRenderer, Text.literal(part), breadcrumbX, breadcrumbY, color);
            breadcrumbX += item.width;

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

                // Calculate this item's height - increased for search results with paths
                int currentItemHeight = itemHeight;
                boolean hasPath = isRecursiveSearch && filePathMap.containsKey(file) &&
                        !filePathMap.get(file).isEmpty();
                if (hasPath) {
                    currentItemHeight = itemHeight + 10; // Add more space for items with paths
                }

                // Only render if visible in the scroll area
                if (y + currentItemHeight >= scrollAreaY && y <= scrollAreaY + scrollAreaHeight) {
                    // Draw folder icon or file icon
                    String icon = file.isDirectory() ? "📁 " : "📄 ";

                    // Draw icon and name with vertical padding
                    context.drawTextWithShadow(this.textRenderer, Text.literal(icon + file.getName()),
                            scrollAreaX + 10, y + 4, 0xFFFFFF);

                    // If this is a search result from a subfolder, show the folder path
                    if (hasPath) {
                        String path = filePathMap.get(file);
                        String pathText = "📂 " + path;
                        context.drawTextWithShadow(this.textRenderer, Text.literal(pathText),
                                scrollAreaX + 10, y + 16, 0xAAAAAA);
                    }

                    // For files, show file size
                    if (!file.isDirectory()) {
                        long fileSizeKB = file.length() / 1024;
                        String sizeText = fileSizeKB + " KB";
                        int sizeWidth = this.textRenderer.getWidth(sizeText);
                        context.drawTextWithShadow(this.textRenderer, Text.literal(sizeText),
                                scrollAreaX + scrollAreaWidth - sizeWidth - 35, y + 4, 0xCCCCCC);
                    }

                    // Draw delete button
                    int deleteX = scrollAreaX + scrollAreaWidth - 15;
                    int deleteY = y + 5;
                    boolean isDeleteHovered = mouseX >= deleteX - 2 && mouseX <= deleteX + 8 &&
                            mouseY >= deleteY && mouseY <= deleteY + 10;
                    int deleteColor = isDeleteHovered ? 0xFF5555 : 0xAA5555;
                    context.drawTextWithShadow(this.textRenderer, Text.literal("✕"), deleteX, deleteY - 1, deleteColor);

                    // Draw upload button for .litematic files
                    if (file.getName().toLowerCase().endsWith(".litematic")) {
                        int uploadX = scrollAreaX + scrollAreaWidth - 29; // Adjusted for better alignment
                        int uploadY = y + 5;
                        boolean isUploadHovered = mouseX >= uploadX - 2 && mouseX <= uploadX + 12 &&
                                mouseY >= uploadY && mouseY <= uploadY + 10;

                        // Show different icon if upload is in progress
                        String uploadIcon = filesBeingUploaded.contains(file) ? "⏳" : "📤";
                        int uploadColor = isUploadHovered ? 0x55AAFF : 0x5555AA;
                        context.drawTextWithShadow(this.textRenderer, Text.literal(uploadIcon), uploadX, uploadY - 1, uploadColor);
                    }

                    // Draw separator line - placed lower for items with paths
                    int separatorY = y + currentItemHeight - 5;
                    context.fill(scrollAreaX, separatorY, scrollAreaX + scrollAreaWidth - 10,
                            separatorY + 1, 0x22FFFFFF);
                }

                y += currentItemHeight; // Use adjusted height for spacing
            }
            // Draw drag visual if dragging
            if (isDragging && draggedFile != null) {
                // Highlight drop target if any
                if (dropTargetFolder != null) {
                    int targetIndex = displayedFiles.indexOf(dropTargetFolder);
                    if (targetIndex >= 0) {
                        int targetY = scrollAreaY - scrollOffset + (targetIndex * itemHeight);
                        context.fill(scrollAreaX, targetY,
                                scrollAreaX + scrollAreaWidth -8, targetY + itemHeight - 5,
                                0x3300FF00); // Green highlight
                    }
                }

                // Draw dragged item name at cursor
                String dragText = (draggedFile.isDirectory() ? "📁 " : "📄 ") + draggedFile.getName();
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal(dragText),
                        dragCurrentX + 10,
                        dragCurrentY - 5,
                        0xFFFFFF
                );
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

        // Draw toast notifications at the end
        ToastManager.render(context, this.width);
    }

    private List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();

    private static class BreadcrumbItem {
        final String name;
        final File directory;
        int x;
        int width;

        BreadcrumbItem(String name, File directory) {
            this.name = name;
            this.directory = directory;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if clicked outside the search field to unfocus it
        if (searchField.isFocused() &&
                (mouseX < searchField.getX() || mouseX > searchField.getX() + searchField.getWidth() ||
                        mouseY < searchField.getY() || mouseY > searchField.getY() + searchField.getHeight())) {
            searchField.setFocused(false);
        }

        // Handle file click in the list
        if (button == 0 && mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

            // Calculate which item was clicked
            int y = scrollAreaY - scrollOffset;
            for (int i = 0; i < displayedFiles.size(); i++) {
                File file = displayedFiles.get(i);

                // Calculate this item's height - increased for search results with paths
                int currentItemHeight = itemHeight;
                boolean hasPath = isRecursiveSearch && filePathMap.containsKey(file) && !filePathMap.get(file).isEmpty();
                if (hasPath) {
                    currentItemHeight = itemHeight + 10;
                }

                if (mouseY >= y && mouseY < y + currentItemHeight) {
                    // Check if upload button was clicked for .litematic files
                    if (file.getName().toLowerCase().endsWith(".litematic")) {
                        int uploadX = scrollAreaX + scrollAreaWidth - 28;
                        int uploadY = y + 5;

                        if (mouseX >= uploadX - 2 && mouseX <= uploadX + 12 &&
                                mouseY >= uploadY && mouseY <= uploadY + 10) {
                            // Upload button clicked
                            if (!filesBeingUploaded.contains(file)) {
                                uploadLitematicFile(file);
                            }
                            return true;
                        }
                    }

                    // Check if delete button was clicked
                    int deleteX = scrollAreaX + scrollAreaWidth - 15;
                    int deleteY = y + 5;
                    if (mouseX >= deleteX - 2 && mouseX <= deleteX + 8 &&
                            mouseY >= deleteY && mouseY <= deleteY + 10) {
                        // Delete button clicked
                        handleDeleteRequest(file);
                        return true;
                    }

                    // Check if Shift is held down to initiate drag and drop
                    if (Screen.hasShiftDown()) {
                        System.out.println("DEBUG: Shift held down, starting drag for: " + file.getName());
                        // Start drag operation
                        isDragging = true;
                        draggedFile = file;
                        dragStartX = (int)mouseX;
                        dragStartY = (int)mouseY;
                        dragCurrentX = (int)mouseX;
                        dragCurrentY = (int)mouseY;
                        return true;
                    }

                    // Handle directory navigation or file actions
                    if (file.isDirectory()) {
                        navigateToFolder(file);
                    } else {
                        // Handle file click action if needed
                    }

                    return true;
                }

                y += currentItemHeight;
            }
        }
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
                }

                // Check if upload button was clicked for .litematic files
                if (clickedFile.getName().toLowerCase().endsWith(".litematic")) {
                    int uploadX = scrollAreaX + scrollAreaWidth - 40;
                    int uploadY = lineY + 5;

                    if (mouseX >= uploadX - 2 && mouseX <= uploadX + 12 &&
                            mouseY >= uploadY && mouseY <= uploadY + 10) {
                        // Upload button clicked
                        if (!filesBeingUploaded.contains(clickedFile)) {
                            uploadLitematicFile(clickedFile);
                        }
                        return true;
                    }
                }

                // Handle directory navigation or file search result clicks
                if (clickedFile.isDirectory()) {
                    // Directory clicked - navigate into it
                    navigateToFolder(clickedFile);
                    return true;
                } else if (isRecursiveSearch && filePathMap.containsKey(clickedFile)) {
                    // If this is a search result, navigate to its parent directory first
                    navigateToFolder(clickedFile.getParentFile());
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

    // Add the new method to handle litematic file uploads
    private void uploadLitematicFile(File file) {
        filesBeingUploaded.add(file);

        LitematicHttpClient.uploadLitematicFile(file, new LitematicHttpClient.UploadCallback() {
            @Override
            public void onSuccess(String url) {
                ToastManager.addToast("URL copied to clipboard!", false);
                filesBeingUploaded.remove(file);
            }

            @Override
            public void onError(String message) {
                ToastManager.addToast(message, true);
                filesBeingUploaded.remove(file);
            }
        });
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

        // Handle file dragging
        if (isDragging && draggedFile != null) {
            dragCurrentX = (int)mouseX;
            dragCurrentY = (int)mouseY;

            // Find potential drop target
            dropTargetFolder = findDropTarget((int)mouseX, (int)mouseY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Only scroll if mouse is over the file list area
        if (mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

            // Check if there's content that requires scrolling
            if (totalContentHeight > scrollAreaHeight) {
                // Scroll amount - adjust multiplier to change scroll speed
                int scrollAmount = (int)(verticalAmount * 1 * itemHeight);

                // Update scroll offset
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight,
                        scrollOffset - scrollAmount));

                return true; // Consume the scroll event
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Handle scrollbar release
        if (button == 0 && isScrolling) {
            isScrolling = false;
            return true;
        }

        // Handle file drop
        if (button == 0 && isDragging && draggedFile != null) {
            System.out.println("DEBUG: Mouse released while dragging: " + draggedFile.getName());
            // Check if we're over a valid folder target
            File targetFolder = findDropTarget((int)mouseX, (int)mouseY);
            System.out.println("DEBUG: Drop target found: " + (targetFolder != null ? targetFolder.getName() : "null"));

            if (targetFolder != null && targetFolder.isDirectory()) {
                // Check if we're trying to move a folder into itself or one of its subdirectories
                boolean isInvalidMove = false;

                if (draggedFile.isDirectory()) {
                    // For directories, check if target is the same as source or inside source
                    if (draggedFile.equals(targetFolder)) {
                        System.out.println("DEBUG: Cannot move folder into itself");
                        isInvalidMove = true;
                    } else if (isSubdirectory(draggedFile, targetFolder)) {
                        System.out.println("DEBUG: Cannot move folder into its own subdirectory");
                        isInvalidMove = true;
                    }
                }

                if (!isInvalidMove) {
                    System.out.println("DEBUG: Valid drop target, calling moveFile");
                    // Move the file to the target folder
                    moveFile(draggedFile, targetFolder);
                } else {
                    System.out.println("DEBUG: Invalid move - would create circular reference");
                    loadFilesFromCurrentDirectory();
                    updateScrollbarDimensions();
                }
            } else {
                System.out.println("DEBUG: No valid drop target found");
                // If no valid drop, still refresh to ensure consistency
                loadFilesFromCurrentDirectory();
                updateScrollbarDimensions();
            }

            // Reset drag state
            isDragging = false;
            draggedFile = null;
            dropTargetFolder = null;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    private File findDropTarget(int mouseX, int mouseY) {
        // Check breadcrumbs first
        int breadcrumbY = 40;
        for (BreadcrumbItem item : breadcrumbItems) {
            if (mouseX >= item.x && mouseX <= item.x + item.width &&
                    mouseY >= breadcrumbY - 5 && mouseY <= breadcrumbY + 15) {
                return item.directory;
            }
        }

        // Only consider folders as drop targets in file list
        if (mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

            int index = (int)((mouseY - scrollAreaY + scrollOffset) / itemHeight);
            if (index >= 0 && index < displayedFiles.size()) {
                File potentialTarget = displayedFiles.get(index);
                if (potentialTarget.isDirectory() && !potentialTarget.equals(draggedFile)) {
                    return potentialTarget;
                }
            }
        }
        return null;
    }

    private boolean isSubdirectory(File potentialParent, File potentialChild) {
        if (potentialChild.isDirectory()) {
            File parent = potentialChild;
            while ((parent = parent.getParentFile()) != null) {
                if (parent.equals(potentialParent)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void moveFile(File sourceFile, File targetFolder) {
        try {
            System.out.println("DEBUG: moveFile called - source: " + sourceFile.getName() + ", target: " + targetFolder.getName());

            File destFile = new File(targetFolder, sourceFile.getName());

            // Check if we're trying to move to the same location
            if (sourceFile.getParentFile().equals(targetFolder)) {
                System.out.println("DEBUG: Same location, returning");
                return;
            }

            // Check if destination already exists
            if (destFile.exists()) {
                System.out.println("DEBUG: Destination exists, showing conflict dialog");
                // Show confirmation dialog for conflicts
                showMoveConflictDialog(sourceFile, destFile, targetFolder);
                return;
            }

            // For directories, show confirmation with file count
            if (sourceFile.isDirectory()) {
                System.out.println("DEBUG: Is directory, collecting files and showing confirmation");
                List<File> allFiles = new ArrayList<>();
                collectAllFiles(sourceFile, allFiles);
                showMoveConfirmationDialog(sourceFile, targetFolder, allFiles);
            } else {
                System.out.println("DEBUG: Is file, performing direct move");
                // For single files, move directly
                boolean success = performMove(sourceFile, destFile);
                System.out.println("DEBUG: Move result: " + success);
                loadFilesFromCurrentDirectory(); // Refresh after direct move
                updateScrollbarDimensions();
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Exception in moveFile: " + e.getMessage());
            e.printStackTrace();
            ToastManager.addToast("Error moving file: " + e.getMessage(), true);
            loadFilesFromCurrentDirectory(); // Refresh even after error
            updateScrollbarDimensions();
        }
    }

    private void showMoveConfirmationDialog(File sourceFolder, File targetFolder, List<File> allFiles) {
        int fileCount = (int) allFiles.stream().filter(f -> !f.isDirectory()).count();
        int folderCount = (int) allFiles.stream().filter(File::isDirectory).count();

        String message = String.format("Move folder '%s' to '%s'?\n\nThis will move:\n- %d file(s)\n- %d folder(s)",
                sourceFolder.getName(), targetFolder.getName(), fileCount, folderCount);

        MinecraftClient.getInstance().setScreen(new ConfirmationScreen(
                Text.literal("Confirm Move"),
                Text.literal(message),
                (confirmed) -> {
                    if (confirmed) {
                        File destFile = new File(targetFolder, sourceFolder.getName());
                        boolean success = performRecursiveMove(sourceFolder, destFile);
                        if (success) {
                            ToastManager.addToast("Successfully moved " + sourceFolder.getName(), false);
                        } else {
                            ToastManager.addToast("Failed to move " + sourceFolder.getName(), true);
                        }
                        loadFilesFromCurrentDirectory();
                        updateScrollbarDimensions();
                    }
                    MinecraftClient.getInstance().setScreen(this); // Return to this screen
                }
        ));
    }

    private void showMoveConflictDialog(File sourceFile, File destFile, File targetFolder) {
        String message = String.format("'%s' already exists in '%s'.\n\nWhat would you like to do?",
                sourceFile.getName(), targetFolder.getName());

        MinecraftClient.getInstance().setScreen(new ConfirmationScreen(
                Text.literal("File Already Exists"),
                Text.literal(message + "\n\nMerge contents?"),
                (confirmed) -> {
                    if (confirmed) {
                        System.out.println("DEBUG: showMoveConflictDialog - sourceFile: " + sourceFile.getAbsolutePath() + ", isDirectory: " + sourceFile.isDirectory());
                        System.out.println("DEBUG: showMoveConflictDialog - destFile: " + destFile.getAbsolutePath() + ", exists: " + destFile.exists());

                        boolean success = false;

                        // Check if we're trying to move a directory to overwrite one of its own subdirectories
                        // This would be invalid because it would create a circular reference
                        boolean isInvalidMove = false;
                        if (sourceFile.isDirectory()) {
                            // Check if the destination is inside the source directory
                            File tempParent = destFile.getParentFile();
                            while (tempParent != null) {
                                if (tempParent.equals(sourceFile)) {
                                    isInvalidMove = true;
                                    break;
                                }
                                tempParent = tempParent.getParentFile();
                            }

                            // Only check for self-conflict if we're not doing a merge operation
                            // For merge operations, we want to merge the contents, not create a conflict
                            if (!isInvalidMove && sourceFile.isDirectory() && destFile.isDirectory()) {
                                // Allow merging - the mergeDirectories method will handle this properly
                                // Only prevent if source and destination are exactly the same directory
                                if (sourceFile.getAbsolutePath().equals(destFile.getAbsolutePath())) {
                                    System.out.println("DEBUG: Cannot merge directory - source and destination are identical");
                                    isInvalidMove = true;
                                }
                            }
                        }

                        if (isInvalidMove) {
                            System.out.println("DEBUG: Invalid move - would create circular reference or self-conflict");
                            ToastManager.addToast("Cannot move " + sourceFile.getName() + " - invalid move", true);
                            loadFilesFromCurrentDirectory();
                            updateScrollbarDimensions();
                            MinecraftClient.getInstance().setScreen(this);
                            return;
                        }

                        // Handle directory merging vs file overwriting
                        if (sourceFile.isDirectory() && destFile.isDirectory()) {
                            System.out.println("DEBUG: Both source and destination are directories, merging contents");
                            // Merge contents: move all files from source directory into destination directory
                            success = mergeDirectories(sourceFile, destFile);
                        } else {
                            System.out.println("DEBUG: File overwrite scenario, deleting destination and moving source");
                            // For file-to-file conflicts, delete destination and move source
                            boolean destinationDeleted = false;
                            if (destFile.exists()) {
                                if (destFile.isDirectory()) {
                                    destinationDeleted = deleteRecursively(destFile);
                                } else {
                                    destinationDeleted = destFile.delete();
                                }
                            } else {
                                destinationDeleted = true;
                            }

                            if (destinationDeleted) {
                                if (sourceFile.isDirectory()) {
                                    success = performRecursiveMove(sourceFile, destFile);
                                } else {
                                    success = performMove(sourceFile, destFile);
                                }
                            } else {
                                ToastManager.addToast("Failed to delete existing " + destFile.getName(), true);
                            }
                        }

                        if (success) {
                            ToastManager.addToast("Successfully moved " + sourceFile.getName(), false);
                        } else {
                            ToastManager.addToast("Failed to move " + sourceFile.getName(), true);
                        }
                        loadFilesFromCurrentDirectory();
                        updateScrollbarDimensions();
                    }
                    MinecraftClient.getInstance().setScreen(this); // Return to this screen
                }
        ));
    }

    private boolean mergeDirectories(File sourceDir, File destDir) {
        try {
            System.out.println("DEBUG: mergeDirectories - source: " + sourceDir.getAbsolutePath() + ", dest: " + destDir.getAbsolutePath());

            // Check if source and destination are the same directory
            if (sourceDir.getAbsolutePath().equals(destDir.getAbsolutePath())) {
                System.out.println("DEBUG: Source and destination are the same directory, nothing to merge");
                return true; // Nothing to do, but it's not an error
            }

            // Get all files and subdirectories from source
            File[] files = sourceDir.listFiles();
            if (files == null) {
                System.out.println("DEBUG: Source directory is empty or unreadable");
                return sourceDir.delete(); // Try to delete empty directory
            }

            System.out.println("DEBUG: Found " + files.length + " items to merge");
            boolean allMovedSuccessfully = true;

            // Keep track of original files to verify they were moved
            Set<String> originalFileNames = new HashSet<>();
            for (File file : files) {
                originalFileNames.add(file.getName());
            }

            for (File file : files) {
                File destFile = new File(destDir, file.getName());
                System.out.println("DEBUG: Moving: " + file.getAbsolutePath() + " -> " + destFile.getAbsolutePath());

                boolean moveSuccess = false;

                if (file.isDirectory()) {
                    if (destFile.exists() && destFile.isDirectory()) {
                        // Destination subdirectory exists, merge recursively
                        moveSuccess = mergeDirectories(file, destFile);
                    } else {
                        // Destination subdirectory doesn't exist, move the entire directory
                        moveSuccess = file.renameTo(destFile);
                        if (!moveSuccess) {
                            // If rename fails, do recursive copy+delete
                            moveSuccess = performRecursiveMove(file, destFile);
                        }
                    }
                } else {
                    // Move file - if destination exists, overwrite it
                    if (destFile.exists()) {
                        System.out.println("DEBUG: Destination file exists, removing: " + destFile.getAbsolutePath());
                        destFile.delete(); // Remove existing file
                    }

                    moveSuccess = file.renameTo(destFile);
                    if (!moveSuccess) {
                        System.out.println("DEBUG: renameTo failed, trying copy+delete for: " + file.getName());
                        // If rename fails, try copy and delete
                        if (copyFile(file, destFile)) {
                            moveSuccess = file.delete();
                            if (!moveSuccess) {
                                System.err.println("DEBUG: Failed to delete source file after copy: " + file.getAbsolutePath());
                            }
                        } else {
                            System.err.println("DEBUG: Failed to copy file: " + file.getAbsolutePath());
                        }
                    }
                }

                if (!moveSuccess) {
                    System.err.println("DEBUG: Failed to move: " + file.getAbsolutePath());
                    allMovedSuccessfully = false;
                }
            }

            // Only delete the source directory if all items were moved successfully
            if (allMovedSuccessfully && !sourceDir.getAbsolutePath().equals(destDir.getAbsolutePath())) {
                System.out.println("DEBUG: All items moved successfully, deleting source directory: " + sourceDir.getAbsolutePath());

                // Check what files remain in the source directory
                File[] remainingFiles = sourceDir.listFiles();
                if (remainingFiles != null && remainingFiles.length > 0) {
                    System.out.println("DEBUG: Source directory contains " + remainingFiles.length + " files after merge:");

                    // Check if remaining files are files that were moved TO this directory (not FROM it)
                    boolean hasUnexpectedFiles = false;
                    for (File remaining : remainingFiles) {
                        System.out.println("DEBUG: - " + remaining.getName());

                        // If this file was one of the original files we were trying to move, it's unexpected
                        if (originalFileNames.contains(remaining.getName())) {
                            hasUnexpectedFiles = true;
                            System.err.println("DEBUG: Unexpected file still exists: " + remaining.getName());
                        }
                    }

                    if (hasUnexpectedFiles) {
                        System.err.println("DEBUG: Source directory still contains original files that should have been moved");
                        return false;
                    } else {
                        System.out.println("DEBUG: Source directory contains only files that were moved TO it, this is expected");
                        // In this case, we shouldn't delete the source directory because it now contains merged content
                        return true;
                    }
                }

                boolean deleted = sourceDir.delete();
                System.out.println("DEBUG: Source directory deletion result: " + deleted);
                return deleted;
            } else if (!allMovedSuccessfully) {
                System.err.println("DEBUG: Not all items were moved successfully, keeping source directory");
                return false;
            } else {
                System.out.println("DEBUG: Source and destination are the same, no deletion needed");
                return true;
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Exception in mergeDirectories: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void collectAllFiles(File root, List<File> allFiles) {
        if (root.isDirectory()) {
            File[] files = root.listFiles();
            if (files != null) {
                for (File file : files) {
                    collectAllFiles(file, allFiles);
                }
            }
        }
        allFiles.add(root);
    }

    private boolean performRecursiveMove(File sourceDir, File destDir) {
        try {
            System.out.println("DEBUG: performRecursiveMove - source: " + sourceDir.getAbsolutePath() + ", dest: " + destDir.getAbsolutePath());

            // Create destination directory first
            if (!destDir.exists()) {
                System.out.println("DEBUG: Creating destination directory: " + destDir.getAbsolutePath());
                if (!destDir.mkdirs()) {
                    System.err.println("DEBUG: Failed to create destination directory: " + destDir.getAbsolutePath());
                    return false;
                }
            }

            // Get all files and subdirectories
            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    File destFile = new File(destDir, file.getName());
                    System.out.println("DEBUG: Processing file: " + file.getAbsolutePath() + " -> " + destFile.getAbsolutePath());

                    if (file.isDirectory()) {
                        // Recursively move subdirectory
                        if (!performRecursiveMove(file, destFile)) {
                            return false;
                        }
                    } else {
                        // Move file - try renameTo first, then copy+delete
                        if (!file.renameTo(destFile)) {
                            System.out.println("DEBUG: renameTo failed, trying copy+delete for: " + file.getName());
                            // If rename fails, try copy and delete
                            if (!copyFile(file, destFile)) {
                                System.err.println("DEBUG: copyFile failed for: " + file.getAbsolutePath());
                                return false;
                            }
                            if (!file.delete()) {
                                System.err.println("DEBUG: Failed to delete source file: " + file.getAbsolutePath());
                                return false;
                            }
                        }
                    }
                }
            }

            // Delete the source directory after moving all contents
            System.out.println("DEBUG: Deleting source directory: " + sourceDir.getAbsolutePath());
            boolean deleted = sourceDir.delete();
            System.out.println("DEBUG: Source directory deletion result: " + deleted);
            return deleted;
        } catch (Exception e) {
            System.err.println("DEBUG: Exception in performRecursiveMove: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean performMove(File sourceFile, File destFile) {
        try {
            // Try to move the file using renameTo first (most efficient)
            if (sourceFile.renameTo(destFile)) {
                return true;
            }

            // If renameTo fails (e.g., moving across different filesystems),
            // fall back to copy and delete
            if (copyFile(sourceFile, destFile)) {
                return sourceFile.delete();
            }

            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean copyFile(File source, File dest) {
        try {
            // Ensure parent directories exist
            File parentDir = dest.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    System.err.println("Failed to create parent directories for: " + dest.getAbsolutePath());
                    return false;
                }
            }

            java.nio.file.Files.copy(source.toPath(), dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteRecursively(f);
                }
            }
        }
        return file.delete();
    }
}


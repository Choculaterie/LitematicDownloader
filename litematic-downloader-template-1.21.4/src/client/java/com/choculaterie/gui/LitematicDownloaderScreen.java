package com.choculaterie.gui;

import com.choculaterie.models.SchematicInfo;
import com.choculaterie.networking.LitematicHttpClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LitematicDownloaderScreen extends Screen {
    private List<SchematicInfo> schematics = new ArrayList<>();
    private List<SchematicInfo> filteredSchematics = new ArrayList<>();
    private TextFieldWidget searchField;
    private String searchTerm = "";

    // Scrolling related fields
    private int scrollOffset = 0;
    private final int itemHeight = 40;
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

    // Status message fields
    private String statusMessage = null;
    private int statusColor = 0xFFFFFF;
    private long statusMessageDisplayTime = 0;
    private static final long STATUS_MESSAGE_DURATION = 3000; // 3 seconds

    // For double click detection
    private int lastClickedIndex = -1;
    private long lastClickTime = 0;

    public LitematicDownloaderScreen() {
        super(Text.literal(""));
    }

    @Override
    protected void init() {
        super.init();

        // File Manager button - at the top right
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("📁"),
                button -> MinecraftClient.getInstance().setScreen(new FileManagerScreen(this))
        ).dimensions(this.width - 65, 10, 20, 20).build());

        // Refresh button - to the right of the file manager button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🔄"),
                button -> loadSchematics()
        ).dimensions(this.width - 40, 10, 20, 20).build());

        // Set up scroll area dimensions
        int contentWidth = Math.min(600, this.width - 80); // Max width or screen width minus margins
        scrollAreaX = (this.width - contentWidth) / 2;
        scrollAreaY = 35;
        scrollAreaWidth = contentWidth;
        scrollAreaHeight = this.height - scrollAreaY - 10;

        // Add search field (centered with fixed width of 200)
        int searchFieldWidth = 200;
        this.searchField = new TextFieldWidget(
                this.textRenderer,
                (this.width - searchFieldWidth) / 2,
                10,
                searchFieldWidth,
                20,
                Text.literal("")
        );
        this.searchField.setMaxLength(50);
        this.searchField.setPlaceholder(Text.literal("Search..."));
        this.searchField.setChangedListener(this::onSearchChanged);
        this.addSelectableChild(this.searchField);

        // Add clear button (X) for the search field
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("✕"),
                        button -> {
                            searchField.setText("");
                            onSearchChanged("");
                        })
                .dimensions((this.width + searchFieldWidth) / 2 + 5, 10, 20, 20)
                .build());

        // Calculate total content height (will be updated when schematics load)
        updateScrollbarDimensions();

        // Load schematics
        loadSchematics();
    }

    private void onSearchChanged(String searchText) {
        this.searchTerm = searchText.toLowerCase();
        this.filterSchematics();
        this.scrollOffset = 0; // Reset scroll position on search
        updateScrollbarDimensions();
    }

    private void filterSchematics() {
        if (searchTerm.isEmpty()) {
            // If search is empty, show all schematics
            this.filteredSchematics = new ArrayList<>(this.schematics);
            return;
        }

        // Filter schematics based on search term
        this.filteredSchematics = this.schematics.stream()
                .filter(schematic ->
                        schematic.getName().toLowerCase().contains(searchTerm) ||
                                schematic.getDescription().toLowerCase().contains(searchTerm) ||
                                schematic.getUsername().toLowerCase().contains(searchTerm))
                .toList();
    }

    private void updateScrollbarDimensions() {
        totalContentHeight = filteredSchematics.size() * itemHeight;

        // Reset scroll offset if it's now out of bounds
        if (totalContentHeight <= scrollAreaHeight) {
            scrollOffset = 0;
        } else {
            scrollOffset = Math.min(scrollOffset, totalContentHeight - scrollAreaHeight);
        }
    }

    private void loadSchematics() {
        new Thread(() -> {
            List<SchematicInfo> loadedSchematics = LitematicHttpClient.fetchSchematicList();
            MinecraftClient.getInstance().execute(() -> {
                schematics = loadedSchematics;
                filterSchematics(); // Apply any existing search filter
                updateScrollbarDimensions();
            });
        }).start();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Render other widgets
        super.render(context, mouseX, mouseY, delta);

        // Render title
        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2 - searchField.getWidth()/4, 10, 0xFFFFFF);

        // Render search field
        this.searchField.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("🔍"),
                searchField.getX() - 15,
                searchField.getY() + 5,
                0xAAAAAA
        );

        // Create clipping region for schematic list
        context.enableScissor(
                scrollAreaX,
                scrollAreaY,
                scrollAreaX + scrollAreaWidth,
                scrollAreaY + scrollAreaHeight
        );

        // Draw schematics list
        if (filteredSchematics.isEmpty()) {
            if (schematics.isEmpty()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No schematics found"),
                        this.width / 2, scrollAreaY + 20, 0xCCCCCC);
            } else {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No matches found"),
                        this.width / 2, scrollAreaY + 20, 0xCCCCCC);
            }
        } else {
            int y = scrollAreaY - scrollOffset;
            for (int i = 0; i < filteredSchematics.size(); i++) {
                SchematicInfo schematic = filteredSchematics.get(i);

                // Only render if visible in the scroll area
                if (y + itemHeight >= scrollAreaY && y <= scrollAreaY + scrollAreaHeight) {
                    renderSchematicItem(context, schematic, scrollAreaX, y, scrollAreaWidth, mouseX, mouseY, i);

                    // Draw separator line
                    context.fill(scrollAreaX, y + itemHeight - 5,
                            scrollAreaX + scrollAreaWidth - 10, y + itemHeight -4, 0x22FFFFFF);
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



        // Render status message if active
        if (hasActiveStatusMessage()) {
            // Draw a semi-transparent background for the message
            int messageWidth = this.textRenderer.getWidth(statusMessage) + 20;
            int messageHeight = 20;
            int messageX = (this.width - messageWidth) / 2;
            int messageY = this.height / 2 - 10;

            context.fill(messageX, messageY, messageX + messageWidth, messageY + messageHeight, 0x80000000);
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(statusMessage),
                    this.width / 2,
                    this.height / 2 - 4,
                    statusColor
            );
        }
    }

    private void renderSchematicItem(DrawContext context, SchematicInfo schematic, int x, int y,
                                     int width, int mouseX, int mouseY, int index) {
        // Use the full available width for rendering content
        int maxTextWidth = width - 110; // Reserve space for stats and download button

        // Left side: Title, Description, Author
        int textX = x + 10;
        int textY = y + 2;
        int originalY = textY;

        // Title (white)
        List<OrderedText> titleLines = MinecraftClient.getInstance().textRenderer.wrapLines(
                Text.literal(schematic.getName()), maxTextWidth);

        for (int i = 0; i < Math.min(titleLines.size(), 2); i++) { // Limit to 2 lines max
            if (i == 1 && titleLines.size() > 2) {
                // This is the last visible line and there are more lines
                String lastLine = getPlainText(titleLines.get(i));
                if (lastLine.length() > 3) {
                    lastLine = lastLine.substring(0, lastLine.length() - 3) + "...";
                }
                context.drawText(MinecraftClient.getInstance().textRenderer,
                        Text.literal(lastLine).asOrderedText(), textX, textY, 0xFFFFFF, true);
            } else {
                context.drawText(MinecraftClient.getInstance().textRenderer, titleLines.get(i), textX, textY, 0xFFFFFF, true);
            }
            textY += 10;
        }

        // Description (gray) with wrapping and ellipsis
        String description = schematic.getDescription().isEmpty() ? "No description" : schematic.getDescription();
        List<OrderedText> descLines = MinecraftClient.getInstance().textRenderer.wrapLines(
                Text.literal(description), maxTextWidth);

        if (!descLines.isEmpty()) {
            if (descLines.size() > 1) {
                // Show first line with ellipsis if there are more lines
                String firstLine = getPlainText(descLines.get(0));
                if (firstLine.length() > 3) {
                    firstLine = firstLine.substring(0, firstLine.length() - 3) + "...";
                }
                context.drawText(MinecraftClient.getInstance().textRenderer,
                        Text.literal(firstLine).asOrderedText(), textX, textY, 0xAAAAAA, false);
            } else {
                context.drawText(MinecraftClient.getInstance().textRenderer, descLines.get(0), textX, textY, 0xAAAAAA, false);
            }
            textY += 10;
        }

        // Author (light gray)
        String authorText = "By: " + schematic.getUsername();
        context.drawText(MinecraftClient.getInstance().textRenderer, authorText, textX, textY, 0xCCCCCC, false);

        // Right side: View and Download counts
        int rightX = x + width - 50; // Adjust position for download button
        int statsY = originalY;

        // Calculate maximum width needed for the numbers to align them right
        String viewCountStr = String.valueOf(schematic.getViewCount());
        String downloadCountStr = String.valueOf(schematic.getDownloadCount());
        int maxNumberWidth = Math.max(
                MinecraftClient.getInstance().textRenderer.getWidth(viewCountStr),
                MinecraftClient.getInstance().textRenderer.getWidth(downloadCountStr)
        );

        // View count (aligned right)
        context.drawText(MinecraftClient.getInstance().textRenderer, viewCountStr,
                rightX - maxNumberWidth, statsY, 0xFFFFFF, false);
        context.drawText(MinecraftClient.getInstance().textRenderer, " 👁",
                rightX - maxNumberWidth + MinecraftClient.getInstance().textRenderer.getWidth(viewCountStr),
                statsY, 0xFFFFFF, false);
        statsY += 10;

        // Download count (aligned right)
        context.drawText(MinecraftClient.getInstance().textRenderer, downloadCountStr,
                rightX - maxNumberWidth, statsY, 0xFFFFFF, false);
        context.drawText(MinecraftClient.getInstance().textRenderer, " ⬇",
                rightX - maxNumberWidth + MinecraftClient.getInstance().textRenderer.getWidth(downloadCountStr),
                statsY, 0xFFFFFF, false);

        // Draw download button with hover effect
        int buttonX = x + width - 30;
        int buttonY = originalY - 2;
        boolean isButtonHovered = mouseX >= buttonX && mouseX <= buttonX + 20 &&
                mouseY >= buttonY && mouseY <= buttonY + 20;

        int buttonColor = isButtonHovered ? 0x99FFFFFF : 0x66FFFFFF;
        context.fill(buttonX, buttonY, buttonX + 20, buttonY + 20, buttonColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("⬇"), buttonX + 10, buttonY + 6, 0xFFFFFF);
    }

    // Utility method to convert OrderedText to String
    private String getPlainText(OrderedText text) {
        StringBuilder sb = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if clicked outside the search field to unfocus it
        if (searchField.isFocused() &&
                (mouseX < searchField.getX() || mouseX > searchField.getX() + searchField.getWidth() ||
                        mouseY < searchField.getY() || mouseY > searchField.getY() + searchField.getHeight())) {
            searchField.setFocused(false);
        }
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
                float clickPercent = ((float)mouseY - scrollAreaY) / scrollAreaHeight;
                scrollOffset = (int)(clickPercent * (totalContentHeight - scrollAreaHeight));
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset));
                return true;
            }
        }

        // Handle item clicks
        if (mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

            int index = (int)((mouseY - scrollAreaY + scrollOffset) / itemHeight);
            if (index >= 0 && index < filteredSchematics.size()) {
                SchematicInfo schematic = filteredSchematics.get(index);

                // Check for download button click
                int buttonX = scrollAreaX + scrollAreaWidth - 30;
                int buttonY = scrollAreaY - scrollOffset + (index * itemHeight) + 5;

                if (mouseX >= buttonX && mouseX <= buttonX + 20 &&
                        mouseY >= buttonY && mouseY <= buttonY + 20) {
                    handleDownload(schematic);
                    return true;
                } else if (button == 0) {
                    // Handle double click for item detail
                    long currentTime = System.currentTimeMillis();
                    if (index == lastClickedIndex && currentTime - lastClickTime < 500) {
                        MinecraftClient.getInstance().setScreen(new DetailScreen(schematic.getId()));
                        return true;
                    }
                    lastClickedIndex = index;
                    lastClickTime = currentTime;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleDownload(SchematicInfo schematic) {
        try {
            String fileName = schematic.getName().replaceAll("[^a-zA-Z0-9.-]", "_");

            // Get file path before downloading to check if it exists
            File gameDir = MinecraftClient.getInstance().runDirectory;
            String savePath;

            if (gameDir != null) {
                savePath = new File(gameDir, "schematics").getAbsolutePath() + File.separator;
            } else {
                boolean isDevelopment = System.getProperty("dev.env", "false").equals("true");
                String homeDir = System.getProperty("user.home");

                if (isDevelopment) {
                    savePath = homeDir + File.separator + "Downloads" + File.separator +
                            "litematic-downloader-template-1.21.4" + File.separator +
                            "run" + File.separator + "schematics" + File.separator;
                } else {
                    savePath = homeDir + File.separator + "AppData" + File.separator +
                            "Roaming" + File.separator + ".minecraft" + File.separator +
                            "schematics" + File.separator;
                }
            }

            File potentialFile = new File(savePath + fileName + ".litematic");

            if (potentialFile.exists()) {
                // Show confirmation dialog
                ConfirmationScreen confirmationScreen = new ConfirmationScreen(
                        Text.literal("File already exists"),
                        Text.literal("The file \"" + fileName + ".litematic\" already exists. Do you want to replace it?"),
                        (confirmed) -> {
                            if (confirmed) {
                                downloadSchematic(schematic, fileName);
                            }
                            MinecraftClient.getInstance().setScreen(this);
                        }
                );
                MinecraftClient.getInstance().setScreen(confirmationScreen);
            } else {
                // No existing file, download directly
                downloadSchematic(schematic, fileName);
            }
        } catch (Exception e) {
            setStatusMessage("Failed to download schematic: " + e.getMessage(), false);
        }
    }

    private void downloadSchematic(SchematicInfo schematic, String fileName) {
        try {
            String filePath = LitematicHttpClient.fetchAndDownloadSchematic(schematic.getId(), fileName);
            // Extract the schematic name for display
            String displayPath = "/schematic/" + fileName + ".litematic";

            setStatusMessage("Schematic downloaded to: " + displayPath, true);
        } catch (Exception e) {
            setStatusMessage("Failed to download schematic: " + e.getMessage(), false);
        }
    }

    // Status message methods
    public void setStatusMessage(String message, boolean isSuccess) {
        this.statusMessage = message;
        this.statusColor = isSuccess ? 0x55FF55 : 0xFF5555; // Green for success, red for error
        this.statusMessageDisplayTime = System.currentTimeMillis();
    }

    public boolean hasActiveStatusMessage() {
        return statusMessage != null &&
                (System.currentTimeMillis() - statusMessageDisplayTime) < STATUS_MESSAGE_DURATION;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isScrolling) {
            // Calculate the relative position directly based on mouse position
            float relativeY = (float)(mouseY - scrollAreaY) / scrollAreaHeight;
            scrollOffset = (int)(relativeY * (totalContentHeight - scrollAreaHeight));

            // Ensure we stay within bounds
            scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset));
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
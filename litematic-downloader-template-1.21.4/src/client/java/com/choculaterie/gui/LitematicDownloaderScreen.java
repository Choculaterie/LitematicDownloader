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
    private TextFieldWidget searchField;
    private String searchTerm = "";
    private boolean isSearchMode = false;

    // Pagination fields
    private int currentPage = 1;
    private int totalPages = 1;
    private int totalItems = 0;
    private final int pageSize = 15;

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
    private int scrollDragOffset = 0;

    // Status message fields
    private String statusMessage = null;
    private int statusColor = 0xFFFFFF;
    private long statusMessageDisplayTime = 0;
    private static final long STATUS_MESSAGE_DURATION = 3000;

    // For double click detection
    private int lastClickedIndex = -1;
    private long lastClickTime = 0;

    private boolean isLoading = false;
    private long loadingStartTime = 0;

    // Search debounce
    private long lastSearchTime = 0;
    private static final long SEARCH_DEBOUNCE_MS = 500;

    public LitematicDownloaderScreen() {
        super(Text.literal(""));
    }

    @Override
    protected void init() {
        super.init();

        // File Manager button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("📁"),
                button -> MinecraftClient.getInstance().setScreen(new FileManagerScreen(this))
        ).dimensions(this.width - 65, 10, 20, 20).build());

        // Refresh button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🔄"),
                button -> {
                    if (isSearchMode) {
                        performSearch();
                    } else {
                        loadSchematics();
                    }
                }
        ).dimensions(this.width - 40, 10, 20, 20).build());

        // Set up scroll area dimensions
        int contentWidth = Math.min(600, this.width - 80);
        scrollAreaX = (this.width - contentWidth) / 2;
        scrollAreaY = 70; // Make room for pagination
        scrollAreaWidth = contentWidth;
        scrollAreaHeight = this.height - scrollAreaY - 40; // Make room for pagination at bottom

        // Add search field
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
        this.addSelectableChild(this.searchField);

        // Add clear button for search field
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("✕"),
                        button -> {
                            searchField.setText("");
                        })
                .dimensions((this.width + searchFieldWidth) / 2 + 5, 10, 20, 20)
                .build());

        // Add pagination controls
        setupPaginationControls();

        updateScrollbarDimensions();
        loadSchematics();
    }

    private void setupPaginationControls() {
        int centerX = this.width / 2;
        int paginationY = this.height - 30;

        // Previous page button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("◀"),
                        button -> {
                            if (currentPage > 1) {
                                currentPage--;
                                if (isSearchMode) {
                                    performSearch();
                                } else {
                                    loadSchematics();
                                }
                            }
                        })
                .dimensions(centerX - 100, paginationY, 20, 20)
                .build());

        // Next page button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("▶"),
                        button -> {
                            if (currentPage < totalPages) {
                                currentPage++;
                                if (isSearchMode) {
                                    performSearch();
                                } else {
                                    loadSchematics();
                                }
                            }
                        })
                .dimensions(centerX + 80, paginationY, 20, 20)
                .build());

        // First page button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("⏮"),
                        button -> {
                            if (currentPage > 1) {
                                currentPage = 1;
                                if (isSearchMode) {
                                    performSearch();
                                } else {
                                    loadSchematics();
                                }
                            }
                        })
                .dimensions(centerX - 125, paginationY, 20, 20)
                .build());

        // Last page button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("⏭"),
                        button -> {
                            if (currentPage < totalPages) {
                                currentPage = totalPages;
                                if (isSearchMode) {
                                    performSearch();
                                } else {
                                    loadSchematics();
                                }
                            }
                        })
                .dimensions(centerX + 105, paginationY, 20, 20)
                .build());
    }

    private void performSearch() {
        if (searchTerm.isEmpty()) return;

        schematics.clear();
        updateScrollbarDimensions();
        scrollOffset = 0;

        isLoading = true;
        loadingStartTime = System.currentTimeMillis();

        new Thread(() -> {
            List<SchematicInfo> searchResults = LitematicHttpClient.searchSchematics(searchTerm);
            MinecraftClient.getInstance().execute(() -> {
                schematics = searchResults;
                // For search, we don't have pagination info, so reset these
                totalPages = 1;
                totalItems = searchResults.size();
                updateScrollbarDimensions();
                isLoading = false;
            });
        }).start();
    }

    private void loadSchematics() {
        schematics.clear();
        updateScrollbarDimensions();
        scrollOffset = 0;

        isLoading = true;
        loadingStartTime = System.currentTimeMillis();

        new Thread(() -> {
            LitematicHttpClient.PaginatedResult result = LitematicHttpClient.fetchSchematicsPaginated(currentPage, pageSize);
            MinecraftClient.getInstance().execute(() -> {
                schematics = result.getItems();
                totalPages = result.getTotalPages();
                totalItems = result.getTotalItems();
                updateScrollbarDimensions();
                isLoading = false;
            });
        }).start();
    }

    private void updateScrollbarDimensions() {
        totalContentHeight = schematics.size() * itemHeight;

        if (totalContentHeight <= scrollAreaHeight) {
            scrollOffset = 0;
        } else {
            scrollOffset = Math.min(scrollOffset, totalContentHeight - scrollAreaHeight);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
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

        // Render pagination info
        String paginationText;
        if (isSearchMode) {
            paginationText = "Found " + totalItems + " results";
        } else {
            paginationText = "Page " + currentPage + " of " + totalPages + " (" + totalItems + " total)";
        }
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(paginationText),
                this.width / 2,
                40,
                0xCCCCCC
        );

        // Create clipping region for schematic list
        context.enableScissor(
                scrollAreaX,
                scrollAreaY,
                scrollAreaX + scrollAreaWidth,
                scrollAreaY + scrollAreaHeight
        );

        // Draw schematics list
        if (schematics.isEmpty()) {
            if (isLoading) {
                int centerY = scrollAreaY + (scrollAreaHeight / 2);
                drawLoadingAnimation(context, this.width / 2, centerY - 15);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Loading..."),
                        this.width / 2, centerY + 15, 0xCCCCCC);
            } else if (isSearchMode) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No results found for: " + searchTerm),
                        this.width / 2, scrollAreaY + (scrollAreaHeight / 2), 0xCCCCCC);
            } else {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No schematics found"),
                        this.width / 2, scrollAreaY + (scrollAreaHeight / 2), 0xCCCCCC);
            }
        } else {
            int y = scrollAreaY - scrollOffset;
            for (int i = 0; i < schematics.size(); i++) {
                SchematicInfo schematic = schematics.get(i);

                if (y + itemHeight >= scrollAreaY && y <= scrollAreaY + scrollAreaHeight) {
                    renderSchematicItem(context, schematic, scrollAreaX, y, scrollAreaWidth, mouseX, mouseY, i);
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

            context.fill(scrollBarX, scrollAreaY,
                    scrollBarX + scrollBarWidth, scrollAreaY + scrollAreaHeight,
                    0x33FFFFFF);

            boolean isHovering = mouseX >= scrollBarX && mouseX <= scrollBarX + scrollBarWidth &&
                    mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight;

            int scrollBarColor = isHovering || isScrolling ? 0xFFFFFFFF : 0xAAFFFFFF;
            context.fill(scrollBarX, scrollBarY,
                    scrollBarX + scrollBarWidth, scrollBarY + scrollBarHeight,
                    scrollBarColor);
        }

        // Render status message if active
        if (hasActiveStatusMessage()) {
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

        ToastManager.render(context, this.width);
    }

    private void drawLoadingAnimation(DrawContext context, int centerX, int centerY) {
        int radius = 12;
        int segments = 8;
        int animationDuration = 1600;

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - loadingStartTime;
        float rotation = (elapsedTime % animationDuration) / (float) animationDuration;

        for (int i = 0; i < segments; i++) {
            float angle = (float) (i * 2 * Math.PI / segments);
            angle += rotation * 2 * Math.PI;

            int x1 = centerX + (int)(Math.sin(angle) * (radius - 3));
            int y1 = centerY + (int)(Math.cos(angle) * (radius - 3));
            int x2 = centerX + (int)(Math.sin(angle) * radius);
            int y2 = centerY + (int)(Math.cos(angle) * radius);

            int alpha = 255 - (i * 255 / segments);
            int color = 0xFFFFFF | (alpha << 24);

            context.fill(x1, y1, x2, y2, color);
        }
    }

    private void renderSchematicItem(DrawContext context, SchematicInfo schematic, int x, int y,
                                     int width, int mouseX, int mouseY, int index) {
        int maxTextWidth = width - 110;

        int textX = x + 10;
        int textY = y + 2;
        int originalY = textY;

        // Title
        List<OrderedText> titleLines = MinecraftClient.getInstance().textRenderer.wrapLines(
                Text.literal(schematic.getName()), maxTextWidth);

        for (int i = 0; i < Math.min(titleLines.size(), 2); i++) {
            if (i == 1 && titleLines.size() > 2) {
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

        // Description
        String description = schematic.getDescription().isEmpty() ? "No description" : schematic.getDescription();
        List<OrderedText> descLines = MinecraftClient.getInstance().textRenderer.wrapLines(
                Text.literal(description), maxTextWidth);

        if (!descLines.isEmpty()) {
            if (descLines.size() > 1) {
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

        // Author
        String authorText = "By: " + schematic.getUsername();
        context.drawText(MinecraftClient.getInstance().textRenderer, authorText, textX, textY, 0xCCCCCC, false);

        // Right side stats
        int rightX = x + width - 50;
        int statsY = originalY;

        String viewCountStr = String.valueOf(schematic.getViewCount());
        String downloadCountStr = String.valueOf(schematic.getDownloadCount());
        int maxNumberWidth = Math.max(
                MinecraftClient.getInstance().textRenderer.getWidth(viewCountStr),
                MinecraftClient.getInstance().textRenderer.getWidth(downloadCountStr)
        );

        context.drawText(MinecraftClient.getInstance().textRenderer, viewCountStr,
                rightX - maxNumberWidth, statsY, 0xFFFFFF, false);
        context.drawText(MinecraftClient.getInstance().textRenderer, " 👁",
                rightX - maxNumberWidth + MinecraftClient.getInstance().textRenderer.getWidth(viewCountStr),
                statsY, 0xFFFFFF, false);
        statsY += 10;

        context.drawText(MinecraftClient.getInstance().textRenderer, downloadCountStr,
                rightX - maxNumberWidth, statsY, 0xFFFFFF, false);
        context.drawText(MinecraftClient.getInstance().textRenderer, " ⬇",
                rightX - maxNumberWidth + MinecraftClient.getInstance().textRenderer.getWidth(downloadCountStr),
                statsY, 0xFFFFFF, false);

        // Download button
        int buttonX = x + width - 30;
        int buttonY = originalY - 2;
        boolean isButtonHovered = mouseX >= buttonX && mouseX <= buttonX + 20 &&
                mouseY >= buttonY && mouseY <= buttonY + 20;

        int buttonColor = isButtonHovered ? 0x99FFFFFF : 0x66FFFFFF;
        context.fill(buttonX, buttonY, buttonX + 20, buttonY + 20, buttonColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("⬇"), buttonX + 10, buttonY + 6, 0xFFFFFF);
    }

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
        if (searchField.isFocused() &&
                (mouseX < searchField.getX() || mouseX > searchField.getX() + searchField.getWidth() ||
                        mouseY < searchField.getY() || mouseY > searchField.getY() + searchField.getHeight())) {
            searchField.setFocused(false);
        }

        // Handle scrollbar interaction
        if (button == 0 && totalContentHeight > scrollAreaHeight) {
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {
                isScrolling = true;
                lastMouseY = (int) mouseY;
                scrollDragOffset = (int)(mouseY - scrollBarY);
                return true;
            }

            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {
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
            if (index >= 0 && index < schematics.size()) {
                SchematicInfo schematic = schematics.get(index);

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
                downloadSchematic(schematic, fileName);
            }
        } catch (Exception e) {
            setStatusMessage("Failed to download schematic: " + e.getMessage(), false);
        }
    }

    private void downloadSchematic(SchematicInfo schematic, String fileName) {
        try {
            String filePath = LitematicHttpClient.fetchAndDownloadSchematic(schematic.getId(), fileName);
            String displayPath = "/schematic/" + fileName + ".litematic";
            setStatusMessage("Schematic downloaded to: " + displayPath, true);
        } catch (Exception e) {
            setStatusMessage("Failed to download schematic: " + e.getMessage(), false);
        }
    }

    public void setStatusMessage(String message, boolean isSuccess) {
        ToastManager.addToast(message, !isSuccess);
    }

    public boolean hasActiveStatusMessage() {
        return statusMessage != null &&
                (System.currentTimeMillis() - statusMessageDisplayTime) < STATUS_MESSAGE_DURATION;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isScrolling) {
            float dragPosition = (float)(mouseY - scrollDragOffset - scrollAreaY) / (scrollAreaHeight - scrollBarHeight);
            scrollOffset = (int)(dragPosition * (totalContentHeight - scrollAreaHeight));
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
        if (mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

            if (totalContentHeight > scrollAreaHeight) {
                int scrollAmount = (int)(-verticalAmount * 20);
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight,
                        scrollOffset + scrollAmount));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
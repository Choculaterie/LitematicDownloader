package com.choculaterie.gui;

import com.choculaterie.models.SchematicInfo;
import com.choculaterie.networking.LitematicHttpClient;
import it.unimi.dsi.fastutil.objects.ReferenceImmutableList;
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

import com.choculaterie.config.SettingsManager;
import org.jetbrains.annotations.Nullable;

public class LitematicDownloaderScreen extends Screen {
    private ReferenceImmutableList<SchematicInfo> schematics = new ReferenceImmutableList<>(new ArrayList<>()); // absolute peak I know
    private TextFieldWidget searchField;
    private String searchTerm = "";
    private boolean isSearchMode = false;

    // Pagination fields
    private int currentPage = 1;
    private int totalPages = 1;
    private int totalItems = 0;
    private final int pageSize = 15;

    // Unverified toggle field
    private boolean showUnverified = false;

    // Enhanced cache fields with multipage support
    private static final CacheManager cacheManager = new CacheManager();
    private static final long CACHE_DURATION_MS = 15 * 60 * 1000; // 15 minutes
    private static final long DETAIL_CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes for details

    // Request management to prevent race conditions
    private volatile int activeRequestPage = -1; // Track which page is currently being loaded
    private volatile long lastRequestTime = 0; // Track when the last request was made
    private static final long MIN_REQUEST_INTERVAL_MS = 100; // Minimum time between requests

    // Static method to access cache manager from other screens
    public static CacheManager getCacheManager() {
        return cacheManager;
    }

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
    private int statusColor = 0xFFFFFFFF;
    private long statusMessageDisplayTime = 0;
    private static final long STATUS_MESSAGE_DURATION = 3000;

    // For double-click detection
    private int lastClickedIndex = -1;
    private long lastClickTime = 0;

    private boolean isLoading = false;
    private long loadingStartTime = 0;

    // Search debounce
    private long lastSearchTime = 0;
    private static final long SEARCH_DEBOUNCE_MS = 500;
    private String lastSearchedTerm = "";

    // Hover preloading fields
    private int hoveredItemIndex = -1;
    private long hoverStartTime = 0;
    private static final long HOVER_DELAY_MS = 200; // 0.5 seconds
    private boolean isPreloadingHoveredItem = false;
    private @Nullable String currentlyPreloadingId = null;

    // Pagination preloading fields
    private boolean isPreloadingPagination = false;
    private int preloadingPageNumber = -1;

    public LitematicDownloaderScreen() {
        super(Text.literal(""));
    }

    // Constructor that accepts a flag to restore navigation state
    public LitematicDownloaderScreen(boolean restoreState) {
        super(Text.literal(""));
        if (restoreState) {
            // State will be restored in init() method
            // TODO(?)
        }
    }

    // Method to restore navigation state (called by NavigationState)
    public void restoreNavigationState(int currentPage, int totalPages, int totalItems,
                                       boolean isSearchMode, String searchTerm, String lastSearchedTerm,
                                       int scrollOffset) {
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.totalItems = totalItems;
        this.isSearchMode = isSearchMode;
        this.searchTerm = searchTerm;
        this.lastSearchedTerm = lastSearchedTerm;
        this.scrollOffset = scrollOffset;

        System.out.println("Restored navigation state in screen: page=" + currentPage + ", scroll=" + scrollOffset);
    }

    @Override
    protected void init() {
        super.init();

        // File Manager button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("📁"),
                button -> MinecraftClient.getInstance().setScreen(new FileManagerScreen(this))
        ).dimensions(this.width - 65, 10, 20, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("⚙"),
                button -> MinecraftClient.getInstance().setScreen(new SettingsScreen(this, (changed) -> {
                    if (changed) {
                        // Reload schematics with new path if needed
                        loadSchematics();
                    }
                }))
        ).dimensions(this.width - 90, 10, 20, 20).build());

        // Refresh button - now forces reload by clearing ALL cache
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🔄"),
                button -> {
                    // Clear all cache when refresh is clicked
                    cacheManager.clearAllCache();
                    System.out.println("Cleared all cache - forcing fresh data load");

                    if (isSearchMode) {
                        performSearch();
                    } else {
                        loadSchematics();
                    }
                }
        ).dimensions(this.width - 40, 10, 20, 20).build());

        // Unverified toggle button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(showUnverified ? "✓" : "✗"),
                button -> {
                    showUnverified = !showUnverified;
                    button.setMessage(Text.literal(showUnverified ? "✓" : "✗"));

                    // Clear cache and reload with new unverified setting
                    cacheManager.clearAllCache();
                    currentPage = 1; // Reset to first page

                    if (isSearchMode) {
                        performSearch();
                    } else {
                        loadSchematics();
                    }
                }
        ).dimensions(this.width - 115, 10, 20, 20).build());

        // Set up scroll area dimensions
        int contentWidth = Math.min(600, this.width - 80);
        scrollAreaX = (this.width - contentWidth) / 2;
        scrollAreaY = 70;
        scrollAreaWidth = contentWidth;
        scrollAreaHeight = this.height - scrollAreaY - 40;

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


        // Add key press handler for Enter key
        this.searchField.setChangedListener(text -> {
            searchTerm = text.trim();
            // Don't perform search here - only on Enter key press
            // TODO(?)
        });

        this.addSelectableChild(this.searchField);

        // Add clear button for search field
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("✕"),
                        button -> {
                            searchField.setText("");
                            isSearchMode = false;
                            currentPage = 1;
                            loadSchematics();
                        })
                .dimensions((this.width + searchFieldWidth) / 2 + 5, 10, 20, 20)
                .build());

        // Add pagination controls
        setupPaginationControls();

        updateScrollbarDimensions();

        // Check if we need to restore navigation state
        NavigationState navState = NavigationState.getInstance();
        if (this instanceof LitematicDownloaderScreen && navState.getSavedCurrentPage() > 0) {
            // Restore state if this screen was created with restore flag
            navState.restoreState(this);

            // Set the search field text if we were in search mode
            if (isSearchMode && !searchTerm.isEmpty()) {
                this.searchField.setText(searchTerm);
            }
        }

        loadSchematics();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (this.searchField.isFocused()) {
            int key = input.key();
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                String text = this.searchField.getText().trim();
                if (!text.isEmpty()) {
                    searchTerm = text;
                    lastSearchedTerm = text; // Save the actual searched term
                    isSearchMode = true;
                    currentPage = 1;
                    performSearch();
                    return true;
                }
            }
        }
        return super.keyPressed(input);
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

        // Check if we have valid cached search data
        if (cacheManager.hasValidSearchCache(searchTerm, CACHE_DURATION_MS)) {
            schematics = cacheManager.getSearchCache(searchTerm).getItems();
            totalPages = 1;
            totalItems = schematics.size();
            updateScrollbarDimensions();
            return;
        }

        schematics = new ReferenceImmutableList<>(new ArrayList<>());
        updateScrollbarDimensions();
        scrollOffset = 0;

        isLoading = true;
        loadingStartTime = System.currentTimeMillis();

        new Thread(() -> {
            ReferenceImmutableList<SchematicInfo> searchResults = new ReferenceImmutableList<>(LitematicHttpClient.searchSchematics(searchTerm));
            MinecraftClient.getInstance().execute(() -> {
                schematics = searchResults;
                totalPages = 1;
                totalItems = searchResults.size();

                // Cache the search response
                cacheManager.putSearchCache(searchTerm, searchResults);

                updateScrollbarDimensions();
                isLoading = false;
            });
        }).start();
    }

    private void performSearchForced() {
        // Force search by clearing cache first
        cacheManager.clearSearchCache(searchTerm);
        performSearch();
    }

    private void loadSchematics() {
        System.out.println("=== loadSchematics() called for page " + currentPage + " ===");

        // Prevent race conditions from rapid clicking
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRequestTime < MIN_REQUEST_INTERVAL_MS) {
            System.out.println("Request throttled - too soon after last request");
            return;
        }

        // If there's already a request in progress for a different page, ignore this request
        if (activeRequestPage != -1 && activeRequestPage != currentPage) {
            System.out.println("Ignoring request for page " + currentPage + " - already loading page " + activeRequestPage);
            return;
        }

        cacheManager.debugPrintCachedPages();

        // Check if we have valid cached data
        if (cacheManager.hasValidSchematicCache(currentPage, CACHE_DURATION_MS)) {
            System.out.println("Using cached data for page " + currentPage);
            CacheManager.SchematicCacheEntry cachedEntry = cacheManager.getSchematicCache(currentPage);

            schematics = cachedEntry.getItems();
            totalPages = cachedEntry.getTotalPages();
            totalItems = cachedEntry.getTotalItems();
            updateScrollbarDimensions();
            System.out.println("Loaded " + schematics.size() + " items from cache for page " + currentPage);
            return;
        }

        // If we're already loading this exact page, don't start another request
        if (activeRequestPage == currentPage && isLoading) {
            System.out.println("Already loading page " + currentPage + " - ignoring duplicate request");
            return;
        }

        System.out.println("No valid cache for page " + currentPage + ", fetching from server...");

        // Mark this page as being actively loaded
        activeRequestPage = currentPage;
        lastRequestTime = currentTime;

        schematics = new ReferenceImmutableList<>(new ArrayList<>());
        updateScrollbarDimensions();
        scrollOffset = 0;

        isLoading = true;
        loadingStartTime = System.currentTimeMillis();

        // Capture the page number to ensure we handle the correct response
        final int requestedPage = currentPage;

        new Thread(() -> {
            try {
                System.out.println("Fetching page " + requestedPage + " from server...");
                LitematicHttpClient.PaginatedResult result = LitematicHttpClient.fetchSchematicsPaginated(requestedPage, pageSize, showUnverified);

                MinecraftClient.getInstance().execute(() -> {
                    // Verify this response is still relevant (user might have navigated away)
                    if (activeRequestPage != requestedPage) {
                        System.out.println("Discarding response for page " + requestedPage + " - user navigated to page " + activeRequestPage);
                        // Clear loading state if this was the active request
                        if (isLoading && activeRequestPage == -1) {
                            isLoading = false;
                            System.out.println("Cleared loading state after discarding response");
                        }
                        return;
                    }

                    // If the current page changed while we were loading, discard this response
                    if (currentPage != requestedPage) {
                        System.out.println("Discarding response for page " + requestedPage + " - current page is now " + currentPage);
                        activeRequestPage = -1; // Clear the active request
                        isLoading = false; // Clear loading state since we're discarding this response
                        System.out.println("Cleared loading state after page change");

                        // CRITICAL FIX: Check if current page has cached data and load it immediately
                        if (cacheManager.hasValidSchematicCache(currentPage, CACHE_DURATION_MS)) {
                            System.out.println("Loading cached data for current page " + currentPage + " after discarding response");
                            CacheManager.SchematicCacheEntry cachedEntry = cacheManager.getSchematicCache(currentPage);
                            schematics = cachedEntry.getItems();
                            totalPages = cachedEntry.getTotalPages();
                            totalItems = cachedEntry.getTotalItems();
                            updateScrollbarDimensions();
                            System.out.println("Restored " + schematics.size() + " items from cache for current page " + currentPage);
                        } else if (!cacheManager.hasValidSchematicCache(currentPage, CACHE_DURATION_MS)) {
                            // If the current page needs loading and no request is active, start loading it
                            System.out.println("Current page " + currentPage + " needs loading - starting request");
                            // Use a small delay to avoid immediate re-request
                            new Thread(() -> {
                                try {
                                    Thread.sleep(50); // Small delay to avoid race conditions
                                    MinecraftClient.getInstance().execute(this::loadSchematics);
                                } catch (InterruptedException ignored) {
                                }
                            }).start();
                        }
                        return;
                    }

                    System.out.println("Server response received for page " + requestedPage + ": " + result.getItems().size() + " items");

                    // Create a defensive copy before assigning to schematics
                    schematics = result.getItems();
                    totalPages = result.getTotalPages();
                    totalItems = result.getTotalItems();

                    // Cache the response with proper error handling
                    try {
                        // Pass the original result items to cache (cache will make its own copy)
                        cacheManager.putSchematicCache(result);
                        System.out.println("Successfully cached page " + requestedPage);
                    } catch (Exception e) {
                        System.err.println("Failed to cache page " + requestedPage + ": " + e.getMessage());
                        e.printStackTrace();
                    }

                    updateScrollbarDimensions();
                    isLoading = false;
                    activeRequestPage = -1; // Clear the active request marker

                    // Debug print after caching
                    cacheManager.debugPrintCachedPages();
                });
            } catch (Exception e) {
                System.err.println("Error fetching page " + requestedPage + ": " + e.getMessage());
                e.printStackTrace();
                MinecraftClient.getInstance().execute(() -> {
                    isLoading = false;
                    activeRequestPage = -1; // Clear the active request marker
                    // Handle error case - could show error message to user
                });
            }
        }).start();
    }

    private void loadSchematicsForced() {
        // Force reload by clearing cache first
        cacheManager.clearSchematicCache(currentPage);
        loadSchematics();
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

        super.render(context, mouseX, mouseY, delta);

        // Handle hover tracking for preloading
        handleHoverPreloading(mouseX, mouseY);
        handlePaginationHoverPreloading(mouseX, mouseY);

        // Render title
        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2 - searchField.getWidth() / 4, 10, 0xFFFFFFFF);

        // Render search field
        this.searchField.render(context, mouseX, mouseY, delta);

        // Draw your icon text (you can do this without extra push/pop now)
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("🔍"),
                searchField.getX() - 15,
                searchField.getY() + 5,
                0xFFAAAAAA
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
                0xFFFFFFFF
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
                        this.width / 2, centerY + 15, 0xFFFFFFFF
                );
            } else if (isSearchMode) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No results found for: " + lastSearchedTerm),
                        this.width / 2, scrollAreaY + (scrollAreaHeight / 2), 0xFFFFFFFF);
            } else {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No schematics found"),
                        this.width / 2, scrollAreaY + (scrollAreaHeight / 2), 0xFFFFFFFF);
            }
        } else {
            int y = scrollAreaY - scrollOffset;
            for (int i = 0; i < schematics.size(); i++) {
                SchematicInfo schematic = schematics.get(i);

                if (y + itemHeight >= scrollAreaY && y <= scrollAreaY + scrollAreaHeight) {
                    renderSchematicItem(context, schematic, scrollAreaX, y, scrollAreaWidth, mouseX, mouseY, i);
                    context.fill(scrollAreaX, y + itemHeight - 5,
                            scrollAreaX + scrollAreaWidth - 10, y + itemHeight - 4, 0x22FFFFFF);
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
            scrollBarY = scrollAreaY + (int) ((float) scrollOffset / (totalContentHeight - scrollAreaHeight)
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
            final float angle = (float) ((i * 2 * Math.PI / segments) + (rotation * 2 * Math.PI));

            int x1 = centerX + (int) (Math.sin(angle) * (radius - 3));
            int y1 = centerY + (int) (Math.cos(angle) * (radius - 3));
            int x2 = centerX + (int) (Math.sin(angle) * radius);
            int y2 = centerY + (int) (Math.cos(angle) * radius);

            int alpha = 255 - (i * 255 / segments);
            int color = 0xFFFFFFFF | (alpha << 24);

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
                        Text.literal(lastLine).asOrderedText(), textX, textY, 0xFFFFFFFF, true);
            } else {
                context.drawText(MinecraftClient.getInstance().textRenderer, titleLines.get(i), textX, textY, 0xFFFFFFFF, true);
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
                        Text.literal(firstLine).asOrderedText(), textX, textY, 0xFFAAAAAA, false);
            } else {
                context.drawText(MinecraftClient.getInstance().textRenderer, descLines.get(0), textX, textY, 0xFFAAAAAA, false);
            }
            textY += 10;
        }

        // Author
        String authorText = "By: " + schematic.getUsername();
        context.drawText(MinecraftClient.getInstance().textRenderer, authorText, textX, textY, 0xFFD5D5D5, false);

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
                rightX - maxNumberWidth, statsY, 0xFFFFFFFF, false);
        context.drawText(MinecraftClient.getInstance().textRenderer, " 👁",
                rightX - maxNumberWidth + MinecraftClient.getInstance().textRenderer.getWidth(viewCountStr),
                statsY, 0xFFFFFFFF, false);
        statsY += 10;

        context.drawText(MinecraftClient.getInstance().textRenderer, downloadCountStr,
                rightX - maxNumberWidth, statsY, 0xFFFFFFFF, false);
        context.drawText(MinecraftClient.getInstance().textRenderer, " ⬇",
                rightX - maxNumberWidth + MinecraftClient.getInstance().textRenderer.getWidth(downloadCountStr),
                statsY, 0xFFFFFFFF, false);

        // Download button
        int buttonX = x + width - 30;
        int buttonY = originalY - 2;
        boolean isButtonHovered = mouseX >= buttonX && mouseX <= buttonX + 20 &&
                mouseY >= buttonY && mouseY <= buttonY + 20;

        int buttonColor = isButtonHovered ? 0x99FFFFFF : 0x66FFFFFF;
        context.fill(buttonX, buttonY, buttonX + 20, buttonY + 20, buttonColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("⬇"), buttonX + 10, buttonY + 6, 0xFFFFFFFF);
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
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Unfocus search when clicking outside it
        if (searchField.isFocused() &&
                (mouseX < searchField.getX() || mouseX > searchField.getX() + searchField.getWidth() ||
                        mouseY < searchField.getY() || mouseY > searchField.getY() + searchField.getHeight())) {
            searchField.setFocused(false);
        }

        // Scrollbar interaction
        if (button == 0 && totalContentHeight > scrollAreaHeight) {
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {
                isScrolling = true;
                lastMouseY = (int) mouseY;
                scrollDragOffset = (int) (mouseY - scrollBarY);
                return true;
            }
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {
                float clickPercent = ((float) mouseY - scrollAreaY) / scrollAreaHeight;
                scrollOffset = (int) (clickPercent * (totalContentHeight - scrollAreaHeight));
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset));
                return true;
            }
        }

        // List area interaction
        if (mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight &&
                !schematics.isEmpty()) {

            int index = (int) ((mouseY - scrollAreaY + scrollOffset) / itemHeight);
            if (index >= 0 && index < schematics.size()) {
                SchematicInfo schematic = schematics.get(index);

                // Download button hit test (aligned with render: buttonY == y)
                int buttonX = scrollAreaX + scrollAreaWidth - 30;
                int buttonY = scrollAreaY - scrollOffset + (index * itemHeight);
                if (mouseX >= buttonX && mouseX <= buttonX + 20 &&
                        mouseY >= buttonY && mouseY <= buttonY + 20) {
                    handleDownload(schematic);
                    // Prevent accidental double‑open after download click
                    lastClickedIndex = -1;
                    lastClickTime = 0;
                    return true;
                }

                // Robust double‑click detection
                boolean isDouble = (button == 0 && doubled);
                if (!isDouble) {
                    long now = System.currentTimeMillis();
                    isDouble = (button == 0 && index == lastClickedIndex && now - lastClickTime <= 300);
                    lastClickedIndex = index;
                    lastClickTime = now;
                }

                if (isDouble) {
                    NavigationState.getInstance().saveState(
                            currentPage, totalPages, totalItems,
                            isSearchMode, searchTerm, lastSearchedTerm,
                            scrollOffset
                    );
                    MinecraftClient.getInstance().setScreen(new DetailScreen(schematic.getId()));
                    return true;
                }
            }
        } else {
            // Clicked outside list; reset manual double‑click tracking
            lastClickedIndex = -1;
            lastClickTime = 0;
        }

        return super.mouseClicked(click, doubled);
    }

    private void handleDownload(SchematicInfo schematic) {
        try {
            String fileName = schematic.getName().replaceAll("[^a-zA-Z0-9.-]", "_");

            // Get file path using SettingsManager
            String savePath = SettingsManager.getSchematicsPath() + File.separator;
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
            // Show relative path from schematics base folder with forward slashes
            String schematicsPath = SettingsManager.getSchematicsPath();
            String relativePath;
            if (filePath.startsWith(schematicsPath)) {
                String pathAfterBase = filePath.substring(schematicsPath.length());
                // Remove leading separator if present
                if (pathAfterBase.startsWith(File.separator)) {
                    pathAfterBase = pathAfterBase.substring(File.separator.length());
                }
                // Use the folder name from the settings instead of hardcoding "schematics"
                String folderName = new File(schematicsPath).getName();
                relativePath = folderName + "/" + pathAfterBase.replace(File.separator, "/");
            } else {
                // Fallback - just show filename
                String folderName = new File(schematicsPath).getName();
                relativePath = folderName + "/" + fileName + ".litematic";
            }
            setStatusMessage("Schematic downloaded to: " + relativePath, true);
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
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
        if (isScrolling) {
            float dragPosition = (float) (click.y() - scrollDragOffset - scrollAreaY) / (scrollAreaHeight - scrollBarHeight);
            scrollOffset = (int) (dragPosition * (totalContentHeight - scrollAreaHeight));
            scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight, scrollOffset));
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight) {

            if (totalContentHeight > scrollAreaHeight) {
                int scrollAmount = (int) (-verticalAmount * 20);
                scrollOffset = Math.max(0, Math.min(totalContentHeight - scrollAreaHeight,
                        scrollOffset + scrollAmount));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Handle hover tracking for preloading schematic details after 2 seconds
     */
    private void handleHoverPreloading(int mouseX, int mouseY) {
        // Only track hover if mouse is in the scroll area and we have schematics
        if (mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight &&
                !schematics.isEmpty()) {

            // Calculate which item the mouse is over
            int itemIndex = (int) ((mouseY - scrollAreaY + scrollOffset) / itemHeight);

            // Check if the index is valid
            if (itemIndex >= 0 && itemIndex < schematics.size()) {
                SchematicInfo hoveredSchematic = schematics.get(itemIndex);

                // If this is a new item being hovered
                if (itemIndex != hoveredItemIndex) {
                    hoveredItemIndex = itemIndex;
                    hoverStartTime = System.currentTimeMillis();

                    // Cancel any ongoing preload if we're hovering a different item
                    if (isPreloadingHoveredItem && !hoveredSchematic.getId().equals(currentlyPreloadingId)) {
                        System.out.println("Cancelled preload for " + currentlyPreloadingId + " - now hovering " + hoveredSchematic.getId());
                        currentlyPreloadingId = null;
                        isPreloadingHoveredItem = false;
                    }
                }
                // If we've been hovering the same item for 0.5+ seconds and haven't started preloading yet
                else if (System.currentTimeMillis() - hoverStartTime >= HOVER_DELAY_MS && !isPreloadingHoveredItem && !cacheManager.hasValidDetailCache(hoveredSchematic.getId(), DETAIL_CACHE_DURATION_MS)) {

                    // Start preloading this schematic's details
                    startHoverPreload(hoveredSchematic);
                }
            } else {
                // Mouse is not over a valid item, reset hover state
                resetHoverState();
            }
        } else {
            // Mouse is not in the scroll area, reset hover state
            resetHoverState();
        }
    }

    /**
     * Start preloading schematic details for the hovered item
     */
    private void startHoverPreload(SchematicInfo schematic) {
        if (isPreloadingHoveredItem || schematic == null) {
            return;
        }

        String schematicId = schematic.getId();

        // Don't preload if already cached
        if (cacheManager.hasValidDetailCache(schematicId, DETAIL_CACHE_DURATION_MS)) {
            System.out.println("Skipping preload for " + schematicId + " - already cached");
            return;
        }

        isPreloadingHoveredItem = true;
        currentlyPreloadingId = schematicId;

        System.out.println("Starting hover preload for schematic: " + schematic.getName() + " (ID: " + schematicId + ")");

        // Fetch schematic details in background thread
        new Thread(() -> {
            try {
                // Check if we should still preload (user might have moved mouse away)
                if (!isPreloadingHoveredItem || !schematicId.equals(currentlyPreloadingId)) {
                    System.out.println("Preload cancelled during fetch for " + schematicId);
                    return;
                }

                com.choculaterie.models.SchematicDetailInfo detailInfo =
                        LitematicHttpClient.fetchSchematicDetail(schematicId);

                // Update on main thread
                MinecraftClient.getInstance().execute(() -> {
                    // Double-check we should still cache this (user might have moved away)
                    if (isPreloadingHoveredItem && schematicId.equals(currentlyPreloadingId)) {
                        // Cache the preloaded detail
                        cacheManager.putDetailCache(schematicId, detailInfo, DETAIL_CACHE_DURATION_MS);
                        System.out.println("Successfully preloaded and cached details for: " + detailInfo.getName());
                    } else {
                        System.out.println("Discarded preload result for " + schematicId + " - user moved away");
                    }

                    // Reset preload state only if this was the current preload
                    if (schematicId.equals(currentlyPreloadingId)) {
                        isPreloadingHoveredItem = false;
                        currentlyPreloadingId = null;
                    }
                });

            } catch (Exception e) {
                System.err.println("Error during hover preload for " + schematicId + ": " + e.getMessage());
                MinecraftClient.getInstance().execute(() -> {
                    // Reset preload state only if this was the current preload
                    if (schematicId.equals(currentlyPreloadingId)) {
                        isPreloadingHoveredItem = false;
                        currentlyPreloadingId = null;
                    }
                });
            }
        }).start();
    }

    /**
     * Reset hover tracking state
     */
    private void resetHoverState() {
        if (hoveredItemIndex != -1) {
            hoveredItemIndex = -1;
            hoverStartTime = 0;

            // Don't cancel ongoing preloads immediately - let them complete
            // This prevents unnecessary cancellations from brief mouse movements
        }
    }

    /**
     * Handle hover tracking for pagination buttons - instant preloading
     */
    private void handlePaginationHoverPreloading(int mouseX, int mouseY) {
        // Only track hover if not in search mode (pagination preloading only applies to regular browsing)
        if (isSearchMode) {
            return;
        }

        int centerX = this.width / 2;
        int paginationY = this.height - 30;
        int buttonWidth = 20;
        int buttonHeight = 20;

        // Check each pagination button individually
        boolean isHoveringAnyButton = false;
        int targetPage = -1;

        // Previous page button (◀)
        if (mouseX >= centerX - 100 && mouseX <= centerX - 100 + buttonWidth &&
                mouseY >= paginationY && mouseY <= paginationY + buttonHeight) {
            if (currentPage > 1) {
                targetPage = currentPage - 1;
                isHoveringAnyButton = true;
            }
        }
        // Next page button (▶)
        else if (mouseX >= centerX + 80 && mouseX <= centerX + 80 + buttonWidth &&
                mouseY >= paginationY && mouseY <= paginationY + buttonHeight) {
            if (currentPage < totalPages) {
                targetPage = currentPage + 1;
                isHoveringAnyButton = true;
            }
        }
        // First page button (⏮)
        else if (mouseX >= centerX - 125 && mouseX <= centerX - 125 + buttonWidth &&
                mouseY >= paginationY && mouseY <= paginationY + buttonHeight) {
            if (currentPage > 1) {
                targetPage = 1;
                isHoveringAnyButton = true;
            }
        }
        // Last page button (⏭)
        else if (mouseX >= centerX + 105 && mouseX <= centerX + 105 + buttonWidth &&
                mouseY >= paginationY && mouseY <= paginationY + buttonHeight) {
            if (currentPage < totalPages) {
                targetPage = totalPages;
                isHoveringAnyButton = true;
            }
        }

        // If hovering over a valid button and target page is different from what we're preloading
        if (isHoveringAnyButton && targetPage != -1) {
            if (!isPreloadingPagination || preloadingPageNumber != targetPage) {
                // Cancel any existing preload if switching to different page
                if (isPreloadingPagination) {
                    System.out.println("Switching pagination preload from page " + preloadingPageNumber + " to " + targetPage);
                }

                // Check if target page is already cached
                if (!cacheManager.hasValidSchematicCache(targetPage, CACHE_DURATION_MS)) {
                    startPaginationPreload(targetPage);
                } else {
                    System.out.println("Page " + targetPage + " already cached - no preload needed");
                }
            }
        } else {
            // Not hovering over any pagination button - reset state but don't cancel ongoing preloads
            // This prevents cancelling preloads when mouse briefly moves outside button bounds
            // TODO(?)
        }
    }

    /**
     * Start preloading a specific page for pagination
     */
    private void startPaginationPreload(int pageNumber) {
        if (pageNumber == currentPage) {
            return; // Don't preload current page
        }

        isPreloadingPagination = true;
        preloadingPageNumber = pageNumber;

        System.out.println("Starting instant pagination preload for page: " + pageNumber);

        new Thread(() -> {
            try {
                // Double-check we should still preload this page
                if (!isPreloadingPagination || preloadingPageNumber != pageNumber) {
                    System.out.println("Pagination preload cancelled for page " + pageNumber);
                    return;
                }

                // Fetch and cache the target page
                LitematicHttpClient.PaginatedResult result = LitematicHttpClient.fetchSchematicsPaginated(pageNumber, pageSize, showUnverified);

                MinecraftClient.getInstance().execute(() -> {
                    // Final check before caching
                    if (isPreloadingPagination && preloadingPageNumber == pageNumber) {
                        cacheManager.putSchematicCache(result);
                        System.out.println("Successfully preloaded and cached page: " + pageNumber + " (" + result.getItems().size() + " items)");
                    } else {
                        System.out.println("Discarded pagination preload result for page " + pageNumber + " - cancelled");
                    }

                    // Reset preload state only if this was the current preload
                    if (preloadingPageNumber == pageNumber) {
                        isPreloadingPagination = false;
                        preloadingPageNumber = -1;
                    }
                });

            } catch (Exception e) {
                System.err.println("Error during pagination preload for page " + pageNumber + ": " + e.getMessage());
                MinecraftClient.getInstance().execute(() -> {
                    // Reset preload state only if this was the current preload
                    if (preloadingPageNumber == pageNumber) {
                        isPreloadingPagination = false;
                        preloadingPageNumber = -1;
                    }
                });
            }
        }).start();
    }
}

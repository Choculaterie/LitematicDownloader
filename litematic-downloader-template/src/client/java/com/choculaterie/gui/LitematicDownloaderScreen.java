package com.choculaterie.gui;

import com.choculaterie.models.SchematicInfo;
import com.choculaterie.networking.LitematicHttpClient;
import com.choculaterie.networking.MinemevHttpClient;
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

    private enum ServerMode { CHOCULATERIE, MINEMEV, BOTH }
    private ServerMode serverMode = ServerMode.CHOCULATERIE;

    // Replace carousel with persistent source selections
    private boolean chocEnabled = true;
    private boolean mineEnabled = true;
    private boolean showSourcesDropdown = false;
    private int sourcesDropdownX = 0;
    private int sourcesDropdownY = 0;
    private int sourcesDropdownWidth = 130;
    private int sourcesDropdownHeight = 0;

    // Dropdown for Minemev file selection
    private boolean showFileDropdown = false;
    private java.util.List<com.choculaterie.models.MinemevFileInfo> fileDropdownItems = new ArrayList<>();
    private int fileDropdownX = 0;
    private int fileDropdownY = 0;
    private int fileDropdownWidth = 320;
    private int fileDropdownHeight = 0;
    private int fileDropdownScroll = 0;
    private final int fileDropdownItemHeight = 18;
    private int fileDropdownContentHeight = 0;
    private @Nullable String fileDropdownPostId = null;
    private String fileDropdownBaseName = "";

    // Add fields to track previous state for debug logging
    private String lastPaginationText = "";
    private boolean lastIsLoading = false;
    private boolean lastSchematicsEmpty = true;
    private int lastSchematicsSize = 0;
    private int lastCurrentPage = 0;
    private int lastTotalPages = 0;
    private int lastTotalItems = 0;

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

        // Load persisted source selections (default both true)
        this.chocEnabled = com.choculaterie.config.SettingsManager.isChoculaterieEnabled();
        this.mineEnabled = com.choculaterie.config.SettingsManager.isMinemevEnabled();
        recalcServerMode();

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
                    cacheManager.clearAllCache();
                    com.choculaterie.networking.LitematicHttpClient.clearCaches();
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
                    cacheManager.clearAllCache();
                    com.choculaterie.networking.LitematicHttpClient.clearCaches();
                    currentPage = 1; // Reset to first page
                    if (isSearchMode) {
                        performSearch();
                    } else {
                        loadSchematics();
                    }
                }
        ).dimensions(this.width - 115, 10, 20, 20).build());

        // New: Sites dropdown opener (replaces carousel)
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Sites"),
                        button -> {
                            showSourcesDropdown = !showSourcesDropdown;
                            sourcesDropdownWidth = 150;
                            sourcesDropdownX = this.width - 165; // near old toggle
                            sourcesDropdownY = 35; // below top buttons
                            sourcesDropdownHeight = 2 * 20 + 8; // two items + padding
                        })
                .dimensions(this.width - 165, 10, 45, 20)
                .build());

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

    private void recalcServerMode() {
        if (chocEnabled && mineEnabled) serverMode = ServerMode.BOTH;
        else if (chocEnabled) serverMode = ServerMode.CHOCULATERIE;
        else if (mineEnabled) serverMode = ServerMode.MINEMEV;
        else {
            // Enforce at least one
            chocEnabled = true;
            com.choculaterie.config.SettingsManager.setSourcesEnabled(chocEnabled, mineEnabled);
            serverMode = ServerMode.CHOCULATERIE;
        }
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

        recalcServerMode();

        // If searching only on Choculaterie, keep old cache behavior
        if (serverMode == ServerMode.CHOCULATERIE) {
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
            return;
        }

        // For Minemev or Both, fetch results from respective APIs without caching for now
        schematics = new ReferenceImmutableList<>(new ArrayList<>());
        updateScrollbarDimensions();
        scrollOffset = 0;
        isLoading = true;
        loadingStartTime = System.currentTimeMillis();

        final String term = searchTerm;
        new Thread(() -> {
            List<SchematicInfo> merged = new ArrayList<>();
            int totalCount = 0;

            try {
                if (mineEnabled) {
                    MinemevHttpClient.MinemevSearchResult mres = MinemevHttpClient.searchPosts(term, 1);
                    for (var post : mres.getPosts()) {
                        merged.add(new SchematicInfo(
                                post.getUuid(),
                                post.getTitle(),
                                post.getDescription(),
                                0,
                                post.getDownloads(),
                                post.getAuthor(),
                                SchematicInfo.SourceServer.MINEMEV,
                                post.getVendor()
                        ));
                    }
                    totalCount += mres.getTotalResults();
                }
                if (chocEnabled) {
                    List<SchematicInfo> cres = LitematicHttpClient.searchSchematics(term);
                    merged.addAll(cres);
                    totalCount += cres.size();
                }
            } catch (Exception e) {
                System.err.println("Search error: " + e.getMessage());
            }

            ReferenceImmutableList<SchematicInfo> finalList = new ReferenceImmutableList<>(merged);
            int finalTotal = totalCount > 0 ? totalCount : merged.size();
            MinecraftClient.getInstance().execute(() -> {
                schematics = finalList;
                totalPages = 1;
                totalItems = finalTotal;
                updateScrollbarDimensions();
                isLoading = false;
            });
        }).start();
    }

    private void loadSchematics() {
        recalcServerMode();
        System.out.println("=== loadSchematics() called for page " + currentPage + " ===");

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRequestTime < MIN_REQUEST_INTERVAL_MS) {
            System.out.println("Request throttled - too soon after last request");
            return;
        }

        // If there's already an active request for a different page, cancel it and start new one
        if (activeRequestPage != -1 && activeRequestPage != currentPage) {
            System.out.println("Cancelling request for page " + activeRequestPage + " to load page " + currentPage);
            activeRequestPage = -1; // Cancel the previous request
        }

        cacheManager.debugPrintCachedPages();

        // NEW: Check BOTH Choculaterie AND Minemev caches - use cache if BOTH are available
        if (serverMode == ServerMode.BOTH) {
            boolean hasChocCache = chocEnabled && cacheManager.hasValidSchematicCache(currentPage, CACHE_DURATION_MS);
            boolean hasMinemevCache = mineEnabled && cacheManager.hasValidMinemevCache(currentPage, CACHE_DURATION_MS);

            if (hasChocCache && hasMinemevCache) {
                // Both caches available - merge them immediately
                CacheManager.SchematicCacheEntry chocEntry = cacheManager.getSchematicCache(currentPage);
                CacheManager.MinemevCacheEntry minemevEntry = cacheManager.getMinemevCache(currentPage);

                if (!chocEntry.getItems().isEmpty() || !minemevEntry.getItems().isEmpty()) {
                    System.out.println("Using cached data from BOTH sources for page " + currentPage);

                    // Merge cached items
                    List<SchematicInfo> merged = new ArrayList<>();
                    merged.addAll(chocEntry.getItems());

                    // Convert Minemev posts to SchematicInfo
                    for (var post : minemevEntry.getItems()) {
                        merged.add(new SchematicInfo(
                                post.getUuid(),
                                post.getTitle(),
                                post.getDescription(),
                                0,
                                post.getDownloads(),
                                post.getAuthor(),
                                SchematicInfo.SourceServer.MINEMEV,
                                post.getVendor()
                        ));
                    }

                    schematics = new ReferenceImmutableList<>(merged);
                    totalPages = Math.max(chocEntry.getTotalPages(), minemevEntry.getTotalPages());
                    totalItems = chocEntry.getTotalItems() + minemevEntry.getTotalItems();
                    updateScrollbarDimensions();
                    System.out.println("Loaded " + schematics.size() + " items from BOTH caches for page " + currentPage);
                    return;
                }
            }
        }

        // Check individual caches for single-source modes
        if (cacheManager.hasValidSchematicCache(currentPage, CACHE_DURATION_MS) && chocEnabled) {
            CacheManager.SchematicCacheEntry cachedEntry = cacheManager.getSchematicCache(currentPage);

            // Only use cache if it has actual items, otherwise fall through to fetch from server
            if (!cachedEntry.getItems().isEmpty()) {
                System.out.println("Using cached Choculaterie data for page " + currentPage);

                if (serverMode == ServerMode.CHOCULATERIE) {
                    schematics = cachedEntry.getItems();
                    totalPages = cachedEntry.getTotalPages();
                    totalItems = cachedEntry.getTotalItems();
                    updateScrollbarDimensions();
                    System.out.println("Loaded " + schematics.size() + " items from cache for page " + currentPage);
                    return;
                }

                // For BOTH mode, show cached Choc items and fetch Minemev in background (unchanged)
                if (serverMode == ServerMode.BOTH) {
                    schematics = cachedEntry.getItems();
                    updateScrollbarDimensions();
                    System.out.println("Displayed cached Choc items for page " + currentPage + " while merging Minemev in background");

                    // Fetch Minemev portion in background and merge when ready (non-blocking)
                    if (mineEnabled) {
                        final CacheManager.SchematicCacheEntry ce = cachedEntry;
                        final int requestedPage = currentPage; // Capture current page for this request
                        new Thread(() -> {
                            try {
                                // Check if this request is still valid before making API call
                                if (currentPage != requestedPage) {
                                    System.out.println("Minemev merge cancelled - page changed from " + requestedPage + " to " + currentPage);
                                    return;
                                }

                                MinemevHttpClient.MinemevSearchResult mres = MinemevHttpClient.searchPosts(null, null, null, "newest", requestedPage);

                                // Cache the Minemev result
                                MinecraftClient.getInstance().execute(() -> {
                                    cacheManager.putMinemevCache(mres);
                                });

                                List<SchematicInfo> merged = new ArrayList<>();
                                merged.addAll(ce.getItems());
                                for (var post : mres.getPosts()) {
                                    merged.add(new SchematicInfo(
                                            post.getUuid(),
                                            post.getTitle(),
                                            post.getDescription(),
                                            0,
                                            post.getDownloads(),
                                            post.getAuthor(),
                                            SchematicInfo.SourceServer.MINEMEV,
                                            post.getVendor()
                                    ));
                                }

                                ReferenceImmutableList<SchematicInfo> items = new ReferenceImmutableList<>(merged);
                                int pagesChoc = Math.max(1, ce.getTotalPages());
                                int pagesMine = Math.max(1, mres.getTotalPages());
                                int finalPages = Math.max(pagesChoc, pagesMine);
                                int finalTotal = ce.getTotalItems() + Math.max(mres.getTotalResults(), mres.getPosts().size());

                                MinecraftClient.getInstance().execute(() -> {
                                    // Only update if we're still on the same page
                                    if (currentPage == requestedPage) {
                                        schematics = items;
                                        totalPages = finalPages;
                                        totalItems = finalTotal > 0 ? finalTotal : items.size();
                                        updateScrollbarDimensions();
                                        System.out.println("Merged Minemev items into cached Choc list for page " + requestedPage);
                                    } else {
                                        System.out.println("Discarded Minemev merge for page " + requestedPage + " - now on page " + currentPage);
                                    }
                                });
                            } catch (Exception e) {
                                System.err.println("Failed to fetch Minemev portion while merging cached Choc data: " + e.getMessage());
                            }
                        }).start();
                    } else {
                        // If Minemev is disabled, update pagination normally
                        totalPages = cachedEntry.getTotalPages();
                        totalItems = cachedEntry.getTotalItems();
                    }

                    return;
                }
            } else {
                System.out.println("Cache entry exists but is empty for page " + currentPage + ", fetching from server...");
            }
        }

        // NEW: Check Minemev cache for MINEMEV mode (same as Choculaterie above)
        if (cacheManager.hasValidMinemevCache(currentPage, CACHE_DURATION_MS) && mineEnabled) {
            CacheManager.MinemevCacheEntry cachedEntry = cacheManager.getMinemevCache(currentPage);

            if (!cachedEntry.getItems().isEmpty()) {
                System.out.println("Using cached Minemev data for page " + currentPage);

                if (serverMode == ServerMode.MINEMEV) {
                    // Convert cached Minemev posts to SchematicInfo
                    List<SchematicInfo> converted = new ArrayList<>();
                    for (var post : cachedEntry.getItems()) {
                        converted.add(new SchematicInfo(
                                post.getUuid(),
                                post.getTitle(),
                                post.getDescription(),
                                0,
                                post.getDownloads(),
                                post.getAuthor(),
                                SchematicInfo.SourceServer.MINEMEV,
                                post.getVendor()
                        ));
                    }

                    schematics = new ReferenceImmutableList<>(converted);
                    totalPages = cachedEntry.getTotalPages();
                    totalItems = cachedEntry.getTotalItems();
                    updateScrollbarDimensions();
                    System.out.println("Loaded " + schematics.size() + " Minemev items from cache for page " + currentPage);
                    return;
                }
            } else {
                System.out.println("Minemev cache entry exists but is empty for page " + currentPage + ", fetching from server...");
            }
        }

        if (activeRequestPage == currentPage && isLoading) {
            System.out.println("Already loading page " + currentPage + " - ignoring duplicate request");
            return;
        }

        System.out.println("No valid cache for page " + currentPage + ", fetching from server...");

        activeRequestPage = currentPage;
        lastRequestTime = currentTime;

        // DON'T clear schematics immediately - preserve current display during loading
        // The schematics will be updated when the new data arrives

        // Reset scroll only if we're not preserving content
        if (schematics.isEmpty()) {
            scrollOffset = 0;
        }

        isLoading = true;
        loadingStartTime = System.currentTimeMillis();

        final int requestedPage = currentPage;
        final ReferenceImmutableList<SchematicInfo> previousSchematics = schematics; // Keep reference to current schematics

        new Thread(() -> {
            try {
                // Check if this request is still valid before making API calls
                if (activeRequestPage != requestedPage) {
                    System.out.println("Request for page " + requestedPage + " cancelled - now loading page " + activeRequestPage);
                    MinecraftClient.getInstance().execute(() -> isLoading = false);
                    return;
                }

                List<SchematicInfo> merged = new ArrayList<>();
                int pagesChoc = 1;
                int pagesMine = 1;
                int totalCount = 0;

                if (serverMode == ServerMode.CHOCULATERIE) {
                    // Check again before API call
                    if (activeRequestPage != requestedPage) {
                        System.out.println("Choculaterie request for page " + requestedPage + " cancelled before API call");
                        MinecraftClient.getInstance().execute(() -> isLoading = false);
                        return;
                    }

                    // Use configured pageSize for Choculaterie-only
                    LitematicHttpClient.PaginatedResult result = LitematicHttpClient.fetchSchematicsPaginated(requestedPage, pageSize, showUnverified);
                    merged.addAll(result.getItems());
                    pagesChoc = Math.max(1, result.getTotalPages());
                    totalCount = result.getTotalItems();
                    cacheManager.putSchematicCache(result);
                } else if (serverMode == ServerMode.MINEMEV) {
                    // Check again before API call
                    if (activeRequestPage != requestedPage) {
                        System.out.println("Minemev request for page " + requestedPage + " cancelled before API call");
                        MinecraftClient.getInstance().execute(() -> isLoading = false);
                        return;
                    }

                    // Minemev default page size is defined server-side (usually 10)
                    MinemevHttpClient.MinemevSearchResult mres = MinemevHttpClient.searchPosts(null, null, null, "newest", requestedPage);

                    // NEW: Cache the Minemev result (same as Choculaterie)
                    cacheManager.putMinemevCache(mres);

                    for (var post : mres.getPosts()) {
                        merged.add(new SchematicInfo(
                                post.getUuid(),
                                post.getTitle(),
                                post.getDescription(),
                                0,
                                post.getDownloads(),
                                post.getAuthor(),
                                SchematicInfo.SourceServer.MINEMEV,
                                post.getVendor()
                        ));
                    }
                    pagesMine = Math.max(1, mres.getTotalPages());
                    totalCount = Math.max(mres.getTotalResults(), merged.size());
                } else { // BOTH
                    int chocPageSize = 10;
                    if (chocEnabled) {
                        // Check before Choculaterie API call
                        if (activeRequestPage != requestedPage) {
                            System.out.println("BOTH mode Choculaterie request for page " + requestedPage + " cancelled");
                            MinecraftClient.getInstance().execute(() -> isLoading = false);
                            return;
                        }

                        System.out.println("Fetching page " + requestedPage + " from Choculaterie (10 items)...");
                        LitematicHttpClient.PaginatedResult result = LitematicHttpClient.fetchSchematicsPaginated(requestedPage, chocPageSize, showUnverified);
                        merged.addAll(result.getItems());
                        pagesChoc = Math.max(1, result.getTotalPages());
                        int chocTotal = result.getTotalItems();
                        totalCount += chocTotal;

                        // Cache Choculaterie result
                        cacheManager.putSchematicCache(result);
                    }
                    if (mineEnabled) {
                        // Check before Minemev API call
                        if (activeRequestPage != requestedPage) {
                            System.out.println("BOTH mode Minemev request for page " + requestedPage + " cancelled");
                            MinecraftClient.getInstance().execute(() -> isLoading = false);
                            return;
                        }

                        System.out.println("Fetching page " + requestedPage + " from Minemev (10 items)...");
                        MinemevHttpClient.MinemevSearchResult mres = MinemevHttpClient.searchPosts(null, null, null, "newest", requestedPage);

                        // NEW: Cache the Minemev result (same as Choculaterie)
                        cacheManager.putMinemevCache(mres);

                        for (var post : mres.getPosts()) {
                            merged.add(new SchematicInfo(
                                    post.getUuid(),
                                    post.getTitle(),
                                    post.getDescription(),
                                    0,
                                    post.getDownloads(),
                                    post.getAuthor(),
                                    SchematicInfo.SourceServer.MINEMEV,
                                    post.getVendor()
                            ));
                        }
                        pagesMine = Math.max(1, mres.getTotalPages());
                        int mineTotal = Math.max(mres.getTotalResults(), mres.getPosts().size());
                        totalCount += mineTotal;
                    }
                }

                ReferenceImmutableList<SchematicInfo> items = new ReferenceImmutableList<>(merged);
                int finalPages = (serverMode == ServerMode.BOTH) ? Math.max(pagesChoc, pagesMine)
                        : (serverMode == ServerMode.CHOCULATERIE ? pagesChoc : pagesMine);

                int finalTotal = totalCount > 0 ? totalCount : items.size();

                MinecraftClient.getInstance().execute(() -> {
                    // Final check - only update if this request is still active and for the current page
                    if (activeRequestPage != requestedPage || currentPage != requestedPage) {
                        System.out.println("Discarding results for page " + requestedPage + " - current page is " + currentPage + ", active request is " + activeRequestPage);
                        isLoading = false;
                        return;
                    }

                    schematics = items;
                    totalPages = finalPages;
                    totalItems = finalTotal;

                    // Reset scroll to top when new content is loaded
                    scrollOffset = 0;
                    updateScrollbarDimensions();
                    isLoading = false;
                    activeRequestPage = -1;

                    System.out.println("Successfully loaded page " + requestedPage + " with " + items.size() + " items");
                    cacheManager.debugPrintCachedPages();
                });
            } catch (Exception e) {
                System.err.println("Error fetching page " + requestedPage + ": " + e.getMessage());
                e.printStackTrace();
                MinecraftClient.getInstance().execute(() -> {
                    isLoading = false;
                    if (activeRequestPage == requestedPage) {
                        activeRequestPage = -1;
                    }
                });
            }
        }).start();
    }

    private void performSearchForced() {
        // Force search by clearing cache first
        cacheManager.clearSearchCache(searchTerm);
        com.choculaterie.networking.LitematicHttpClient.clearCaches();
        performSearch();
    }

    private void loadSchematicsForced() {
        // Force reload by clearing cache first
        cacheManager.clearSchematicCache(currentPage);
        com.choculaterie.networking.LitematicHttpClient.clearCaches();
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

    // Helper method to check for state changes and log debug info only when needed
    private void checkAndLogStateChanges() {
        // Check pagination text changes
        String currentPaginationText;
        if (isSearchMode) {
            currentPaginationText = "Found " + totalItems + " results";
        } else {
            currentPaginationText = "Page " + currentPage + " of " + totalPages + " (" + totalItems + " total)";
        }

        if (!currentPaginationText.equals(lastPaginationText)) {
            System.out.println("DEBUG PAGINATION CHANGE: '" + lastPaginationText + "' -> '" + currentPaginationText + "'");
            System.out.println("  currentPage: " + lastCurrentPage + " -> " + currentPage);
            System.out.println("  totalPages: " + lastTotalPages + " -> " + totalPages);
            System.out.println("  totalItems: " + lastTotalItems + " -> " + totalItems);
            System.out.println("  isSearchMode: " + isSearchMode + ", serverMode: " + serverMode);
            lastPaginationText = currentPaginationText;
            lastCurrentPage = currentPage;
            lastTotalPages = totalPages;
            lastTotalItems = totalItems;
        }

        // Check loading/empty state changes
        boolean currentSchematicsEmpty = schematics.isEmpty();
        int currentSchematicsSize = schematics.size();

        if (currentSchematicsEmpty != lastSchematicsEmpty ||
            isLoading != lastIsLoading ||
            currentSchematicsSize != lastSchematicsSize) {

            System.out.println("DEBUG RENDER STATE CHANGE:");
            System.out.println("  schematics.isEmpty(): " + lastSchematicsEmpty + " -> " + currentSchematicsEmpty);
            System.out.println("  schematics.size(): " + lastSchematicsSize + " -> " + currentSchematicsSize);
            System.out.println("  isLoading: " + lastIsLoading + " -> " + isLoading);
            System.out.println("  isSearchMode: " + isSearchMode + ", serverMode: " + serverMode);

            if (currentSchematicsEmpty) {
                if (isLoading) {
                    System.out.println("  -> Will display: 'Loading...'");
                } else if (isSearchMode) {
                    System.out.println("  -> Will display: 'No results found for: " + lastSearchedTerm + "'");
                } else {
                    System.out.println("  -> Will display: 'No schematics found'");
                }
            } else {
                System.out.println("  -> Will display: " + currentSchematicsSize + " schematic items");
            }

            lastSchematicsEmpty = currentSchematicsEmpty;
            lastSchematicsSize = currentSchematicsSize;
            lastIsLoading = isLoading;
        }
    }
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        super.render(context, mouseX, mouseY, delta);

        // Check for state changes and log debug info only when values change
        checkAndLogStateChanges();

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

        ToastManager.render(context, this.width);

        // Render file dropdown overlay on top
        if (showFileDropdown && !fileDropdownItems.isEmpty()) {
            renderFileDropdown(context, mouseX, mouseY);
        }

        // Render sources dropdown on top as well
        if (showSourcesDropdown) {
            renderSourcesDropdown(context, mouseX, mouseY);
        }
    }

    // Reintroduced: loading spinner
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

    // Reintroduced: list item renderer
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

        String downloadCountStr = String.valueOf(schematic.getDownloadCount());
        // Only show view count for sources that actually have it (exclude Minemev)
        if (schematic.getSource() != SchematicInfo.SourceServer.MINEMEV) {
            String viewCountStr = String.valueOf(schematic.getViewCount());
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
        } else {
            // Minemev: show downloads only
            int numberWidth = MinecraftClient.getInstance().textRenderer.getWidth(downloadCountStr);
            context.drawText(MinecraftClient.getInstance().textRenderer, downloadCountStr,
                    rightX - numberWidth, statsY, 0xFFFFFFFF, false);
            context.drawText(MinecraftClient.getInstance().textRenderer, " ⬇",
                    rightX - numberWidth + numberWidth,
                    statsY, 0xFFFFFFFF, false);
        }

        // Download button
        int buttonX = x + width - 30;
        int buttonY = originalY - 2;
        boolean isButtonHovered = mouseX >= buttonX && mouseX <= buttonX + 20 &&
                mouseY >= buttonY && mouseY <= buttonY + 20;

        int buttonColor = isButtonHovered ? 0x99FFFFFF : 0x66FFFFFF;
        context.fill(buttonX, buttonY, buttonX + 20, buttonY + 20, buttonColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("⬇"), buttonX + 10, buttonY + 6, 0xFFFFFFFF);
    }

    // Reintroduced: persist and react to sources change
    private void onSourcesChanged() {
        com.choculaterie.config.SettingsManager.setSourcesEnabled(chocEnabled, mineEnabled);
        recalcServerMode();
        cacheManager.clearAllCache();
        currentPage = 1;
        if (isSearchMode) {
            performSearch();
        } else {
            loadSchematics();
        }
    }

    private void renderSourcesDropdown(DrawContext context, int mouseX, int mouseY) {
        int x1 = sourcesDropdownX;
        int y1 = sourcesDropdownY;
        int x2 = sourcesDropdownX + sourcesDropdownWidth;
        int y2 = sourcesDropdownY + sourcesDropdownHeight;
        context.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xAA000000);
        context.fill(x1, y1, x2, y2, 0xEE101010);

        int itemH = 20;
        // Choculaterie row
        int cy = y1 + 4;
        boolean hoverChoc = mouseX >= x1 && mouseX <= x2 && mouseY >= cy && mouseY <= cy + itemH;
        if (hoverChoc) context.fill(x1, cy, x2, cy + itemH, 0x22FFFFFF);
        drawCheckboxRow(context, x1 + 6, cy + 5, chocEnabled, "Choculaterie");

        // Minemev row
        int my = cy + itemH;
        boolean hoverMine = mouseX >= x1 && mouseX <= x2 && mouseY >= my && mouseY <= my + itemH;
        if (hoverMine) context.fill(x1, my, x2, my + itemH, 0x22FFFFFF);
        drawCheckboxRow(context, x1 + 6, my + 5, mineEnabled, "Minemev");
    }

    private void drawCheckboxRow(DrawContext ctx, int x, int y, boolean checked, String label) {
        int box = 10;
        int color = 0xFFFFFFFF;
        ctx.fill(x, y, x + box, y + box, 0x33FFFFFF);
        if (checked) ctx.fill(x + 2, y + 2, x + box - 2, y + box - 2, color);
        ctx.drawText(this.textRenderer, label, x + box + 6, y + 1, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Handle sources dropdown first
        if (showSourcesDropdown) {
            int x1 = sourcesDropdownX;
            int y1 = sourcesDropdownY;
            int x2 = sourcesDropdownX + sourcesDropdownWidth;
            int y2 = sourcesDropdownY + sourcesDropdownHeight;
            if (mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2) {
                // Inside dropdown: toggle items
                if (button == 0) {
                    int relY = (int)(mouseY - y1);
                    if (relY >= 4 && relY < 24) {
                        // Choculaterie
                        boolean newChoc = !chocEnabled;
                        if (!newChoc && !mineEnabled) {
                            // prevent disabling last one
                            ToastManager.addToast("At least one site must be selected", true);
                        } else {
                            chocEnabled = newChoc;
                            onSourcesChanged();
                        }
                        return true;
                    } else if (relY >= 24 && relY < 44) {
                        // Minemev
                        boolean newMine = !mineEnabled;
                        if (!newMine && !chocEnabled) {
                            ToastManager.addToast("At least one site must be selected", true);
                        } else {
                            mineEnabled = newMine;
                            onSourcesChanged();
                        }
                        return true;
                    }
                }
                return true; // consume inside clicks
            } else {
                // Click outside closes
                showSourcesDropdown = false;
            }
        }

        // If dropdown is open, handle it first
        if (showFileDropdown) {
            if (handleDropdownClick(mouseX, mouseY, button)) {
                return true;
            }
        }

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
                    // For Minemev, open dropdown to select file
                    if (schematic.getSource() == SchematicInfo.SourceServer.MINEMEV) {
                        openMinemevFileDropdown(schematic, buttonX, buttonY + 20);
                        // Prevent accidental double-open
                        lastClickedIndex = -1;
                        lastClickTime = 0;
                        return true;
                    } else {
                        handleDownload(schematic, buttonX, buttonY);
                        lastClickedIndex = -1;
                        lastClickTime = 0;
                        return true;
                    }
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
                    if (schematic.getSource() == SchematicInfo.SourceServer.MINEMEV) {
                        MinecraftClient.getInstance().setScreen(new MinemevDetailScreen(schematic.getId(), schematic.getVendor()));
                    } else {
                        MinecraftClient.getInstance().setScreen(new DetailScreen(schematic.getId()));
                    }
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

    private void openMinemevFileDropdown(SchematicInfo schematic, int anchorX, int anchorY) {
        showFileDropdown = false;
        fileDropdownItems = new ArrayList<>();
        fileDropdownPostId = schematic.getId();
        fileDropdownBaseName = schematic.getName().replaceAll("[^a-zA-Z0-9.-]", "_");
        fileDropdownX = Math.min(anchorX, this.width - fileDropdownWidth - 10);
        fileDropdownY = anchorY;
        fileDropdownScroll = 0;

        new Thread(() -> {
            try {
                var files = MinemevHttpClient.fetchPostFiles(schematic.getId(), schematic.getVendor());
                MinecraftClient.getInstance().execute(() -> {
                    // If no files
                    if (files == null || files.isEmpty()) {
                        ToastManager.addToast("No files available for this post", true);
                        showFileDropdown = false;
                        return;
                    }

                    // If exactly one file, download directly (with confirmation if needed)
                    if (files.size() == 1) {
                        com.choculaterie.models.MinemevFileInfo only = files.get(0);
                        String chosenName = sanitizeFileNameForSave(only.getFileName());
                        String schematicsPath = SettingsManager.getSchematicsPath();
                        File potentialFile = new File(schematicsPath + File.separator + chosenName + ".litematic");

                        if (potentialFile.exists()) {
                            ConfirmationScreen confirmationScreen = new ConfirmationScreen(
                                    Text.literal("File already exists"),
                                    Text.literal("The file \"" + chosenName + ".litematic\" already exists. Replace it?"),
                                    (confirmed) -> {
                                        if (confirmed) {
                                            performMinemevFileDownload(only, chosenName);
                                        }
                                        MinecraftClient.getInstance().setScreen(this);
                                    }
                            );
                            MinecraftClient.getInstance().setScreen(confirmationScreen);
                        } else {
                            performMinemevFileDownload(only, chosenName);
                        }

                        showFileDropdown = false;
                        return;
                    }

                    // Otherwise, show dropdown with all files
                    fileDropdownItems = files;
                    fileDropdownContentHeight = fileDropdownItems.size() * fileDropdownItemHeight;
                    int maxHeight = Math.min(200, this.height - fileDropdownY - 20);
                    fileDropdownHeight = Math.min(fileDropdownContentHeight, maxHeight);
                    showFileDropdown = !fileDropdownItems.isEmpty();
                    if (!showFileDropdown) {
                        ToastManager.addToast("No files available for this post", true);
                    }
                });
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() -> {
                    ToastManager.addToast("Failed to load files: " + e.getMessage(), true);
                    showFileDropdown = false;
                });
            }
        }).start();
    }

    private boolean handleDropdownClick(double mouseX, double mouseY, int button) {
        // Click outside closes
        if (mouseX < fileDropdownX || mouseX > fileDropdownX + fileDropdownWidth ||
                mouseY < fileDropdownY || mouseY > fileDropdownY + fileDropdownHeight) {
            showFileDropdown = false;
            return false; // let others handle outside click
        }
        if (button != 0) return true; // consume non-left clicks inside

        int relativeY = (int) (mouseY - fileDropdownY + fileDropdownScroll);
        int index = relativeY / fileDropdownItemHeight;
        if (index >= 0 && index < fileDropdownItems.size()) {
            com.choculaterie.models.MinemevFileInfo file = fileDropdownItems.get(index);
            // Confirm overwrite using selected filename
            String chosenName = sanitizeFileNameForSave(file.getFileName());
            String schematicsPath = SettingsManager.getSchematicsPath();
            File potentialFile = new File(schematicsPath + File.separator + chosenName + ".litematic");

            if (potentialFile.exists()) {
                ConfirmationScreen confirmationScreen = new ConfirmationScreen(
                        Text.literal("File already exists"),
                        Text.literal("The file \"" + chosenName + ".litematic\" already exists. Replace it?"),
                        (confirmed) -> {
                            if (confirmed) {
                                performMinemevFileDownload(file, chosenName);
                            }
                            MinecraftClient.getInstance().setScreen(this);
                        }
                );
                MinecraftClient.getInstance().setScreen(confirmationScreen);
            } else {
                performMinemevFileDownload(file, chosenName);
            }
            showFileDropdown = false; // close after selection
            return true;
        }
        return true; // clicks inside consumed
    }

    private void renderFileDropdown(DrawContext context, int mouseX, int mouseY) {
        // Background and border
        int x1 = fileDropdownX;
        int y1 = fileDropdownY;
        int x2 = fileDropdownX + fileDropdownWidth;
        int y2 = fileDropdownY + fileDropdownHeight;
        context.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xAA000000);
        context.fill(x1, y1, x2, y2, 0xEE101010);

        // Clip area
        context.enableScissor(x1, y1, x2, y2);

        int startIndex = Math.max(0, fileDropdownScroll / fileDropdownItemHeight);
        int endIndex = Math.min(fileDropdownItems.size(), startIndex + (fileDropdownHeight / fileDropdownItemHeight) + 1);
        int drawY = y1 - (fileDropdownScroll % fileDropdownItemHeight);

        for (int i = startIndex; i < endIndex; i++) {
            com.choculaterie.models.MinemevFileInfo f = fileDropdownItems.get(i);
            int itemTop = drawY + (i - startIndex) * fileDropdownItemHeight;
            boolean hovered = mouseX >= x1 && mouseX <= x2 && mouseY >= itemTop && mouseY <= itemTop + fileDropdownItemHeight;
            if (hovered) {
                context.fill(x1, itemTop, x2, itemTop + fileDropdownItemHeight, 0x33FFFFFF);
            }
            String name = (f.getFileName() != null && !f.getFileName().isEmpty()) ? f.getFileName() : "Unnamed";
            String sizeStr = f.getFileSize() > 0 ? (" • " + formatSize(f.getFileSize())) : "";
            String ver = joinArrayShort(f.getVersions());
            String line = name + sizeStr + (ver.isEmpty() ? "" : (" • " + ver));
            context.drawText(this.textRenderer, line, x1 + 6, itemTop + 4, 0xFFFFFFFF, false);
        }

        context.disableScissor();

        // Scrollbar
        if (fileDropdownContentHeight > fileDropdownHeight) {
            int scrollBarWidth = 6;
            int barHeight = Math.max(20, fileDropdownHeight * fileDropdownHeight / fileDropdownContentHeight);
            int barX = x2 - scrollBarWidth - 2;
            int barY = y1 + (int)((float)fileDropdownScroll / (fileDropdownContentHeight - fileDropdownHeight)
                    * (fileDropdownHeight - barHeight));
            context.fill(barX, y1, barX + scrollBarWidth, y2, 0x33FFFFFF);
            boolean isHovering = mouseX >= barX && mouseX <= barX + scrollBarWidth && mouseY >= barY && mouseY <= barY + barHeight;
            int color = isHovering ? 0xFFFFFFFF : 0xAAFFFFFF;
            context.fill(barX, barY, barX + scrollBarWidth, barY + barHeight, color);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Dropdown scroll handling takes precedence
        if (showFileDropdown && mouseX >= fileDropdownX && mouseX <= fileDropdownX + fileDropdownWidth &&
                mouseY >= fileDropdownY && mouseY <= fileDropdownY + fileDropdownHeight &&
                fileDropdownContentHeight > fileDropdownHeight) {
            int delta = (int) (-verticalAmount * 20);
            fileDropdownScroll = Math.max(0, Math.min(fileDropdownContentHeight - fileDropdownHeight, fileDropdownScroll + delta));
            return true;
        }

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

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
        if (isScrolling && totalContentHeight > scrollAreaHeight) {
            int mouseY = (int) click.y();
            int newBarTop = mouseY - scrollDragOffset;
            int minBarTop = scrollAreaY;
            int maxBarTop = scrollAreaY + scrollAreaHeight - scrollBarHeight;
            newBarTop = Math.max(minBarTop, Math.min(maxBarTop, newBarTop));

            float percent = (float) (newBarTop - scrollAreaY) / (float) (scrollAreaHeight - scrollBarHeight);
            scrollOffset = (int) (percent * (totalContentHeight - scrollAreaHeight));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (isScrolling) {
            isScrolling = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void performMinemevFileDownload(com.choculaterie.models.MinemevFileInfo file, String chosenName) {
        try {
            String filenameForResolver = (file.getFileName() != null && !file.getFileName().isEmpty())
                    ? file.getFileName() : (chosenName + ".litematic");
            String postId = this.fileDropdownPostId != null ? this.fileDropdownPostId : "";
            String resolvedUrl = com.choculaterie.networking.MinemevHttpClient.getDownloadUrl(postId, filenameForResolver, file.getDownloadUrl());
            String filePath = LitematicHttpClient.downloadFileFromUrl(resolvedUrl, chosenName);
            String schematicsPath = SettingsManager.getSchematicsPath();
            String relativePath;
            if (filePath.startsWith(schematicsPath)) {
                String pathAfterBase = filePath.substring(schematicsPath.length());
                if (pathAfterBase.startsWith(File.separator)) {
                    pathAfterBase = pathAfterBase.substring(File.separator.length());
                }
                String folderName = new File(schematicsPath).getName();
                relativePath = folderName + "/" + pathAfterBase.replace(File.separator, "/");
            } else {
                String folderName = new File(schematicsPath).getName();
                relativePath = folderName + "/" + chosenName + ".litematic";
            }
            setStatusMessage("Schematic downloaded to: " + relativePath, true);
        } catch (Exception e) {
            setStatusMessage("Failed to download: " + e.getMessage(), false);
        }
    }

    private String sanitizeFileNameForSave(String name) {
        String n = (name == null || name.isEmpty()) ? fileDropdownBaseName : name;
        n = n.replaceAll("[^a-zA-Z0-9.-]", "_");
        if (n.toLowerCase().endsWith(".litematic")) {
            n = n.substring(0, n.length() - ".litematic".length());
        }
        return n;
    }

    private String joinArrayShort(String[] arr) {
        if (arr == null || arr.length == 0) return "";
        java.util.List<String> items = new java.util.ArrayList<>();
        for (String s : arr) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) items.add(t);
        }
        return String.join(", ", items);
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "";
        String[] units = {"B", "KB", "MB", "GB"};
        double b = (double) bytes;
        int idx = 0;
        while (b >= 1024 && idx < units.length - 1) { b /= 1024.0; idx++; }
        return String.format(java.util.Locale.ROOT, "%.1f %s", b, units[idx]);
    }

    /**
     * Handle hover tracking for preloading schematic details after 2 seconds
     */
    private void handleHoverPreloading(int mouseX, int mouseY) {
        // Only track hover if mouse is in the scroll area and we have schematics
        if (mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaWidth &&
                mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaHeight &&
                !schematics.isEmpty()) {

            int itemIndex = (int) ((mouseY - scrollAreaY + scrollOffset) / itemHeight);
            if (itemIndex >= 0 && itemIndex < schematics.size()) {
                SchematicInfo hoveredSchematic = schematics.get(itemIndex);

                if (itemIndex != hoveredItemIndex) {
                    hoveredItemIndex = itemIndex;
                    hoverStartTime = System.currentTimeMillis();

                    if (isPreloadingHoveredItem && !hoveredSchematic.getId().equals(currentlyPreloadingId)) {
                        System.out.println("Cancelled preload for " + currentlyPreloadingId + " - now hovering " + hoveredSchematic.getId());
                        currentlyPreloadingId = null;
                        isPreloadingHoveredItem = false;
                    }
                }
                else if (System.currentTimeMillis() - hoverStartTime >= HOVER_DELAY_MS && !isPreloadingHoveredItem) {
                    // Dispatch to appropriate preload per source and cache status
                    if (hoveredSchematic.getSource() == SchematicInfo.SourceServer.CHOCULATERIE) {
                        if (!cacheManager.hasValidDetailCache(hoveredSchematic.getId(), DETAIL_CACHE_DURATION_MS)) {
                            startHoverPreload(hoveredSchematic);
                        }
                    } else {
                        if (!cacheManager.hasValidMinemevDetailCache(hoveredSchematic.getId(), DETAIL_CACHE_DURATION_MS)) {
                            startHoverPreloadMinemev(hoveredSchematic);
                        }
                    }
                }
            } else {
                resetHoverState();
            }
        } else {
            resetHoverState();
        }
    }

    private void startHoverPreloadMinemev(SchematicInfo schematic) {
        if (isPreloadingHoveredItem || schematic == null) return;
        if (schematic.getSource() != SchematicInfo.SourceServer.MINEMEV) return;

        String uuid = schematic.getId();
        String vendor = schematic.getVendor();
        if (cacheManager.hasValidMinemevDetailCache(uuid, DETAIL_CACHE_DURATION_MS)) {
            //System.out.println("Skipping Minemev preload for " + uuid + " - already cached");
            return;
        }

        isPreloadingHoveredItem = true;
        currentlyPreloadingId = uuid;
        System.out.println("Starting hover preload for Minemev post: " + schematic.getName() + " (UUID: " + uuid + ")");

        new Thread(() -> {
            try {
                if (!isPreloadingHoveredItem || !uuid.equals(currentlyPreloadingId)) {
                    System.out.println("Preload cancelled during fetch for Minemev " + uuid);
                    return;
                }

                var detail = MinemevHttpClient.fetchPostDetails(uuid, vendor);
                MinecraftClient.getInstance().execute(() -> {
                    if (isPreloadingHoveredItem && uuid.equals(currentlyPreloadingId)) {
                        cacheManager.putMinemevDetailCache(uuid, detail, DETAIL_CACHE_DURATION_MS);
                        System.out.println("Successfully preloaded Minemev details for: " + detail.getTitle());
                    }
                    if (uuid.equals(currentlyPreloadingId)) {
                        isPreloadingHoveredItem = false;
                        currentlyPreloadingId = null;
                    }
                });
            } catch (Exception e) {
                System.err.println("Error during Minemev hover preload for " + uuid + ": " + e.getMessage());
                MinecraftClient.getInstance().execute(() -> {
                    if (uuid.equals(currentlyPreloadingId)) {
                        isPreloadingHoveredItem = false;
                        currentlyPreloadingId = null;
                    }
                });
            }
        }).start();
    }

    private void startHoverPreload(SchematicInfo schematic) {
        if (isPreloadingHoveredItem || schematic == null) return;
        if (schematic.getSource() != SchematicInfo.SourceServer.CHOCULATERIE) return;

        String schematicId = schematic.getId();
        if (cacheManager.hasValidDetailCache(schematicId, DETAIL_CACHE_DURATION_MS)) {
            //System.out.println("Skipping preload for " + schematicId + " - already cached");
            return;
        }

        isPreloadingHoveredItem = true;
        currentlyPreloadingId = schematicId;
        System.out.println("Starting hover preload for schematic: " + schematic.getName() + " (ID: " + schematicId + ")");

        new Thread(() -> {
            try {
                if (!isPreloadingHoveredItem || !schematicId.equals(currentlyPreloadingId)) {
                    System.out.println("Preload cancelled during fetch for " + schematicId);
                    return;
                }

                com.choculaterie.models.SchematicDetailInfo detailInfo =
                        LitematicHttpClient.fetchSchematicDetail(schematicId);

                MinecraftClient.getInstance().execute(() -> {
                    if (isPreloadingHoveredItem && schematicId.equals(currentlyPreloadingId)) {
                        cacheManager.putDetailCache(schematicId, detailInfo, DETAIL_CACHE_DURATION_MS);
                        System.out.println("Successfully preloaded and cached details for: " + detailInfo.getName());
                    }

                    if (schematicId.equals(currentlyPreloadingId)) {
                        isPreloadingHoveredItem = false;
                        currentlyPreloadingId = null;
                    }
                });

            } catch (Exception e) {
                System.err.println("Error during hover preload for " + schematicId + ": " + e.getMessage());
                MinecraftClient.getInstance().execute(() -> {
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

                // NEW: Check if we need to preload based on current server mode and what's already cached
                boolean needsChoculateriePreload = chocEnabled && !cacheManager.hasValidSchematicCache(targetPage, CACHE_DURATION_MS);
                boolean needsMinemevPreload = mineEnabled && !cacheManager.hasValidMinemevCache(targetPage, CACHE_DURATION_MS);

                if (needsChoculateriePreload || needsMinemevPreload) {
                    startPaginationPreload(targetPage);
                } else {
                    //System.out.println("Page " + targetPage + " already fully cached - no preload needed");
                }
            }
        } else {
            // Not hovering over any pagination button - reset state but don't cancel ongoing preloads
            // This prevents cancelling preloads when mouse briefly moves outside button bounds
        }
    }

    /**
     * Start preloading a specific page for pagination - now handles both sources
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

                // Determine what needs to be preloaded based on server mode and current cache state
                boolean needsChoculateriePreload = chocEnabled && !cacheManager.hasValidSchematicCache(pageNumber, CACHE_DURATION_MS);
                boolean needsMinemevPreload = mineEnabled && !cacheManager.hasValidMinemevCache(pageNumber, CACHE_DURATION_MS);

                // Preload Choculaterie if needed
                if (needsChoculateriePreload) {
                    if (!isPreloadingPagination || preloadingPageNumber != pageNumber) {
                        System.out.println("Choculaterie pagination preload cancelled for page " + pageNumber);
                        return;
                    }

                    System.out.println("Preloading Choculaterie data for page " + pageNumber);
                    LitematicHttpClient.PaginatedResult result = LitematicHttpClient.fetchSchematicsPaginated(pageNumber, pageSize, showUnverified);

                    MinecraftClient.getInstance().execute(() -> {
                        if (isPreloadingPagination && preloadingPageNumber == pageNumber) {
                            cacheManager.putSchematicCache(result);
                            System.out.println("Successfully preloaded and cached Choculaterie page: " + pageNumber + " (" + result.getItems().size() + " items)");
                        } else {
                            System.out.println("Discarded Choculaterie pagination preload result for page " + pageNumber + " - cancelled");
                        }
                    });
                } else if (chocEnabled) {
                    System.out.println("Choculaterie page " + pageNumber + " already cached - skipping preload");
                }

                // Preload Minemev if needed
                if (needsMinemevPreload) {
                    if (!isPreloadingPagination || preloadingPageNumber != pageNumber) {
                        System.out.println("Minemev pagination preload cancelled for page " + pageNumber);
                        return;
                    }

                    System.out.println("Preloading Minemev data for page " + pageNumber);
                    MinemevHttpClient.MinemevSearchResult mres = MinemevHttpClient.searchPosts(null, null, null, "newest", pageNumber);

                    MinecraftClient.getInstance().execute(() -> {
                        if (isPreloadingPagination && preloadingPageNumber == pageNumber) {
                            cacheManager.putMinemevCache(mres);
                            System.out.println("Successfully preloaded and cached Minemev page: " + pageNumber + " (" + mres.getPosts().size() + " items)");
                        } else {
                            System.out.println("Discarded Minemev pagination preload result for page " + pageNumber + " - cancelled");
                        }
                    });
                } else if (mineEnabled) {
                    System.out.println("Minemev page " + pageNumber + " already cached - skipping preload");
                }

                MinecraftClient.getInstance().execute(() -> {
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

    private void handleDownload(SchematicInfo schematic, int anchorX, int anchorY) {
        try {
            String fileName = schematic.getName().replaceAll("[^a-zA-Z0-9.-]", "_");
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

    // Download helper used by non-Minemev flow (and as fallback)
    private void downloadSchematic(SchematicInfo schematic, String fileName) {
        try {
            String filePath;
            if (schematic.getSource() == SchematicInfo.SourceServer.MINEMEV) {
                var files = MinemevHttpClient.fetchPostFiles(schematic.getId(), schematic.getVendor());
                String chosenUrl = null;
                String chosenName = fileName;
                String resolverFileName = null;
                for (var f : files) {
                    String lower = (f.getFileName() != null ? f.getFileName().toLowerCase() : "");
                    boolean isLite = ("litematic".equalsIgnoreCase(f.getFileType())) || lower.endsWith(".litematic");
                    if (isLite) {
                        chosenUrl = f.getDownloadUrl();
                        resolverFileName = (f.getFileName() != null && !f.getFileName().isEmpty()) ? f.getFileName() : (chosenName + ".litematic");
                        if (f.getFileName() != null && !f.getFileName().isEmpty()) {
                            chosenName = f.getFileName().replaceAll("[^a-zA-Z0-9.-]", "_");
                            if (chosenName.toLowerCase().endsWith(".litematic")) {
                                chosenName = chosenName.substring(0, chosenName.length() - ".litematic".length());
                            }
                        }
                        break;
                    }
                }
                if (chosenUrl == null && !files.isEmpty()) {
                    var f = files.get(0);
                    chosenUrl = f.getDownloadUrl();
                    resolverFileName = (f.getFileName() != null && !f.getFileName().isEmpty()) ? f.getFileName() : (chosenName + ".litematic");
                }
                // Resolve the actual URL using Minemev API helper (handles empty/relative URLs)
                String resolvedUrl = MinemevHttpClient.getDownloadUrl(schematic.getId(), resolverFileName != null ? resolverFileName : (chosenName + ".litematic"), chosenUrl);
                if (resolvedUrl == null || resolvedUrl.isEmpty()) {
                    throw new RuntimeException("No downloadable file found for this Minemev post");
                }
                filePath = LitematicHttpClient.downloadFileFromUrl(resolvedUrl, chosenName);
            } else {
                filePath = LitematicHttpClient.fetchAndDownloadSchematic(schematic.getId(), fileName);
            }

            // Show relative path from schematics base folder with forward slashes
            String schematicsPath = SettingsManager.getSchematicsPath();
            String relativePath;
            if (filePath.startsWith(schematicsPath)) {
                String pathAfterBase = filePath.substring(schematicsPath.length());
                if (pathAfterBase.startsWith(File.separator)) {
                    pathAfterBase = pathAfterBase.substring(File.separator.length());
                }
                String folderName = new File(schematicsPath).getName();
                relativePath = folderName + "/" + pathAfterBase.replace(File.separator, "/");
            } else {
                String folderName = new File(schematicsPath).getName();
                relativePath = folderName + "/" + fileName + ".litematic";
            }
            setStatusMessage("Schematic downloaded to: " + relativePath, true);
        } catch (Exception e) {
            setStatusMessage("Failed to download schematic: " + e.getMessage(), false);
        }
    }

    private void setStatusMessage(String message, boolean isSuccess) {
        this.statusMessage = message;
        this.statusColor = isSuccess ? 0xFF55FF55 : 0xFFFF5555;
        this.statusMessageDisplayTime = System.currentTimeMillis();
        ToastManager.addToast(message, !isSuccess);
    }

    private boolean hasActiveStatusMessage() {
        return statusMessage != null &&
                (System.currentTimeMillis() - statusMessageDisplayTime) < STATUS_MESSAGE_DURATION;
    }

    // Missing helper added back
    private String getPlainText(OrderedText text) {
        StringBuilder sb = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}

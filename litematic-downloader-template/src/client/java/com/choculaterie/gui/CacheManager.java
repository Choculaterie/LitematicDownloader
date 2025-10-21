package com.choculaterie.gui;

import com.choculaterie.models.SchematicInfo;
import com.choculaterie.models.SchematicDetailInfo;
import com.choculaterie.models.MinemevPostDetailInfo;
import com.choculaterie.networking.LitematicHttpClient;
import it.unimi.dsi.fastutil.objects.ReferenceImmutableList;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CacheManager {
    // Cache for paginated schematic lists
    private final Map<Integer, SchematicCacheEntry> schematicCache = new HashMap<>();

    // Cache for search results
    private final Map<String, SearchCacheEntry> searchCache = new HashMap<>();

    // Cache for detailed schematic information
    private final Map<String, DetailCacheEntry> detailCache = new HashMap<>();

    // New: Cache for Minemev detailed information
    private final Map<String, MinemevDetailCacheEntry> minemevDetailCache = new HashMap<>();

    // Static initialization flag to prevent multiple pre-loading
    private static boolean hasPreloaded = false;
    private static boolean isPreloading = false;

    public CacheManager() {
        // Don't start preloading in constructor to avoid blocking
        // It will be started when first accessed
    }

    // Initialize cache system at game startup - called from mod initialization
    public static void initializeAtGameStartup() {
        if (hasPreloaded || isPreloading) {
            return;
        }

        System.out.println("Initializing schematic cache system at game startup...");

        // Start preloading in background immediately - but only cache page 1 initially
        CompletableFuture.runAsync(() -> {
            try {
                isPreloading = true;

                // Get the static cache manager instance that will be used by the screen
                CacheManager staticCacheManager = LitematicDownloaderScreen.getCacheManager();

                // Only preload first page for instant access
                // Other pages will be cached as they are accessed
                LitematicHttpClient.PaginatedResult result = LitematicHttpClient.fetchSchematicsPaginated(1, 15);

                // Cache the result directly in the static cache manager
                if (MinecraftClient.getInstance() != null) {
                    MinecraftClient.getInstance().execute(() -> {
                        staticCacheManager.putSchematicCache(result); // 15 minutes
                        System.out.println("Pre-cached page 1 with " + result.getItems().size() + " items (Total pages: " + result.getTotalPages() + ")");
                    });
                }

                hasPreloaded = true;
                isPreloading = false;
                System.out.println("Background cache pre-loading completed successfully!");

            } catch (Exception e) {
                isPreloading = false;
                System.err.println("Error during background pre-loading: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // Preload first few pages of schematics at game startup for instant access
    public void preloadInitialData() {
        if (hasPreloaded || isPreloading) {
            return; // Already preloaded or in progress
        }

        initializeAtGameStartup();
    }

    // Schematic cache methods
    public boolean hasValidSchematicCache(int page, long maxAge) {
        SchematicCacheEntry entry = schematicCache.get(page);
        return entry != null && !entry.isExpired(maxAge);
    }

    public SchematicCacheEntry getSchematicCache(int page) {
        return schematicCache.get(page);
    }

    public void putSchematicCache(LitematicHttpClient.PaginatedResult result) {
        var cache = new SchematicCacheEntry(result);
        int page = cache.getCurrentPage();
        schematicCache.put(page, cache);
        System.out.println("Cached schematics page " + page + " (" + result.getItems().size() + " items)");

        // Debug: verify the cache is correct after insertion
        SchematicCacheEntry verifyEntry = schematicCache.get(page);
        if (verifyEntry != null) {
            System.out.println("Cache verification: Page " + page + " now has " + verifyEntry.getItems().size() + " items");
        }
    }

    public void clearSchematicCache(int page) {
        schematicCache.remove(page);
        System.out.println("Cleared schematic cache for page " + page);
    }

    // Search cache methods
    public boolean hasValidSearchCache(String searchTerm, long maxAge) {
        SearchCacheEntry entry = searchCache.get(searchTerm.toLowerCase());
        return entry != null && !entry.isExpired(maxAge);
    }

    public SearchCacheEntry getSearchCache(String searchTerm) {
        return searchCache.get(searchTerm.toLowerCase());
    }

    public void putSearchCache(String searchTerm, ReferenceImmutableList<SchematicInfo> items) {
        searchCache.put(searchTerm.toLowerCase(), new SearchCacheEntry(items, searchTerm));
        System.out.println("Cached search results for '" + searchTerm + "' (" + items.size() + " results)");
    }

    public void clearSearchCache(String searchTerm) {
        searchCache.remove(searchTerm.toLowerCase());
        System.out.println("Cleared search cache for '" + searchTerm + "'");
    }

    // Detail cache methods
    public boolean hasValidDetailCache(String schematicId, long maxAge) {
        DetailCacheEntry entry = detailCache.get(schematicId);
        return entry != null && !entry.isExpired(maxAge);
    }

    public DetailCacheEntry getDetailCache(String schematicId) {
        return detailCache.get(schematicId);
    }

    public void putDetailCache(String schematicId, SchematicDetailInfo detail, long maxAge) {
        detailCache.put(schematicId, new DetailCacheEntry(detail));
        System.out.println("Cached detail for schematic " + schematicId);
    }

    public void clearDetailCache(String schematicId) {
        detailCache.remove(schematicId);
        System.out.println("Cleared detail cache for " + schematicId);
    }

    // New: Minemev detail cache methods
    public boolean hasValidMinemevDetailCache(String uuid, long maxAge) {
        MinemevDetailCacheEntry entry = minemevDetailCache.get(uuid);
        return entry != null && !entry.isExpired(maxAge);
    }

    public MinemevDetailCacheEntry getMinemevDetailCache(String uuid) {
        return minemevDetailCache.get(uuid);
    }

    public void putMinemevDetailCache(String uuid, MinemevPostDetailInfo detail, long maxAge) {
        minemevDetailCache.put(uuid, new MinemevDetailCacheEntry(detail));
        System.out.println("Cached Minemev detail for post " + uuid);
    }

    public void clearMinemevDetailCache(String uuid) {
        minemevDetailCache.remove(uuid);
        System.out.println("Cleared Minemev detail cache for " + uuid);
    }

    // Clear all cache
    public void clearAllCache() {
        schematicCache.clear();
        searchCache.clear();
        detailCache.clear();
        minemevDetailCache.clear();
        System.out.println("Cleared ALL cache entries");
    }

    // Clear all schematic cache (all pages)
    public void clearAllSchematicCache() {
        schematicCache.clear();
        System.out.println("Cleared ALL schematic page cache entries");
    }

    // Get cache statistics
    public String getCacheStats() {
        return String.format("Cache: %d pages, %d searches, %d details",
                schematicCache.size(), searchCache.size(), detailCache.size());
    }

    // Get cache statistics with detailed info
    public String getCacheStatsDetailed() {
        StringBuilder stats = new StringBuilder();
        stats.append(String.format("Cache: %d pages, %d searches, %d details\n",
                schematicCache.size(), searchCache.size(), detailCache.size()));

        // List cached pages
        if (!schematicCache.isEmpty()) {
            stats.append("Cached pages: ");
            for (Integer page : schematicCache.keySet()) {
                SchematicCacheEntry entry = schematicCache.get(page);
                long ageMs = System.currentTimeMillis() - entry.timestamp;
                long ageMinutes = ageMs / (60 * 1000);
                stats.append(String.format("%d(%dm old), ", page, ageMinutes));
            }
            stats.setLength(stats.length() - 2); // Remove last comma
        }

        return stats.toString();
    }

    // Debug method to check if a specific page is cached
    public boolean isPageCached(int page) {
        boolean cached = schematicCache.containsKey(page);
        System.out.println("Page " + page + " cache check: " + (cached ? "CACHED" : "NOT CACHED"));
        return cached;
    }

    // Debug method to print all cached pages
    public void debugPrintCachedPages() {
        System.out.println("=== DEBUG: Current cached pages ===");
        if (schematicCache.isEmpty()) {
            System.out.println("No pages cached");
        } else {
            for (Map.Entry<Integer, SchematicCacheEntry> entry : schematicCache.entrySet()) {
                int page = entry.getKey();
                SchematicCacheEntry cacheEntry = entry.getValue();
                long ageMs = System.currentTimeMillis() - cacheEntry.timestamp;
                long ageMinutes = ageMs / (60 * 1000);
                boolean expired = cacheEntry.isExpired(15 * 60 * 1000);
                System.out.println(String.format("Page %d: %d items, %d minutes old, %s",
                    page, cacheEntry.result.getItems().size(), ageMinutes, expired ? "EXPIRED" : "VALID"));
            }
        }
        System.out.println("=== END DEBUG ===");
    }

    // Cache entry classes
    public static class SchematicCacheEntry {
        private final LitematicHttpClient.PaginatedResult result;
        private final long timestamp;

        public SchematicCacheEntry(LitematicHttpClient.PaginatedResult result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }

        public ReferenceImmutableList<SchematicInfo> getItems() { return result.getItems(); }
        public int getTotalPages() { return result.getTotalPages(); }
        public int getTotalItems() { return result.getTotalItems(); }
        public int getCurrentPage() { return result.getCurrentPage(); }

        public boolean isExpired(long maxAge) {
            return System.currentTimeMillis() - timestamp > maxAge;
        }
    }

    public static class SearchCacheEntry {
        private final ReferenceImmutableList<SchematicInfo> items;
        private final String searchTerm;
        private final long timestamp;

        public SearchCacheEntry(ReferenceImmutableList<SchematicInfo> items, String searchTerm) {
            this.items = items;
            this.searchTerm = searchTerm;
            this.timestamp = System.currentTimeMillis();
        }

        public ReferenceImmutableList<SchematicInfo> getItems() { return items; }
        public String getSearchTerm() { return searchTerm; }

        public boolean isExpired(long maxAge) {
            return System.currentTimeMillis() - timestamp > maxAge;
        }
    }

    public static class DetailCacheEntry {
        private final SchematicDetailInfo detail;
        private final long timestamp;

        public DetailCacheEntry(SchematicDetailInfo detail) {
            this.detail = detail;
            this.timestamp = System.currentTimeMillis();
        }

        public SchematicDetailInfo getDetail() { return detail; }

        public boolean isExpired(long maxAge) {
            return System.currentTimeMillis() - timestamp > maxAge;
        }
    }

    public static class MinemevDetailCacheEntry {
        private final MinemevPostDetailInfo detail;
        private final long timestamp;

        public MinemevDetailCacheEntry(MinemevPostDetailInfo detail) {
            this.detail = detail;
            this.timestamp = System.currentTimeMillis();
        }

        public MinemevPostDetailInfo getDetail() { return detail; }

        public boolean isExpired(long maxAge) {
            return System.currentTimeMillis() - timestamp > maxAge;
        }
    }
}

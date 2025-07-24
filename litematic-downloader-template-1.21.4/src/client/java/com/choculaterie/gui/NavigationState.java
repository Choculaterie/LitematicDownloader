package com.choculaterie.gui;

/**
 * Stores navigation state for preserving user position when navigating between screens
 */
public class NavigationState {
    private static NavigationState instance = new NavigationState();
    
    // Pagination state
    private int savedCurrentPage = 1;
    private int savedTotalPages = 1;
    private int savedTotalItems = 0;
    private boolean savedIsSearchMode = false;
    private String savedSearchTerm = "";
    private String savedLastSearchedTerm = "";
    
    // Scroll state
    private int savedScrollOffset = 0;
    
    private NavigationState() {
        // Private constructor for singleton
    }
    
    public static NavigationState getInstance() {
        return instance;
    }
    
    /**
     * Save the current state from LitematicDownloaderScreen
     */
    public void saveState(int currentPage, int totalPages, int totalItems, 
                         boolean isSearchMode, String searchTerm, String lastSearchedTerm,
                         int scrollOffset) {
        this.savedCurrentPage = currentPage;
        this.savedTotalPages = totalPages;
        this.savedTotalItems = totalItems;
        this.savedIsSearchMode = isSearchMode;
        this.savedSearchTerm = searchTerm != null ? searchTerm : "";
        this.savedLastSearchedTerm = lastSearchedTerm != null ? lastSearchedTerm : "";
        this.savedScrollOffset = scrollOffset;
        
        System.out.println("Navigation state saved: page=" + currentPage + ", scroll=" + scrollOffset + 
                          ", searchMode=" + isSearchMode + ", searchTerm='" + searchTerm + "'");
    }
    
    /**
     * Apply the saved state to a LitematicDownloaderScreen instance
     */
    public void restoreState(LitematicDownloaderScreen screen) {
        screen.restoreNavigationState(savedCurrentPage, savedTotalPages, savedTotalItems,
                                    savedIsSearchMode, savedSearchTerm, savedLastSearchedTerm,
                                    savedScrollOffset);
        
        System.out.println("Navigation state restored: page=" + savedCurrentPage + ", scroll=" + savedScrollOffset + 
                          ", searchMode=" + savedIsSearchMode + ", searchTerm='" + savedSearchTerm + "'");
    }
    
    // Getters for debugging
    public int getSavedCurrentPage() { return savedCurrentPage; }
    public int getSavedScrollOffset() { return savedScrollOffset; }
    public boolean isSavedSearchMode() { return savedIsSearchMode; }
    public String getSavedSearchTerm() { return savedSearchTerm; }
}

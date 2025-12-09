package com.choculaterie.gui;

import com.choculaterie.gui.widget.CustomButton;
import com.choculaterie.gui.widget.CustomTextField;
import com.choculaterie.gui.widget.PostListWidget;
import com.choculaterie.gui.widget.PostDetailPanel;
import com.choculaterie.gui.widget.LoadingSpinner;
import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.models.MinemevSearchResponse;
import com.choculaterie.network.MinemevNetworkManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class LitematicDownloaderScreen extends Screen {
    private static final int SEARCH_BAR_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PADDING = 10;
    private static final int ITEMS_PER_PAGE = 20;

    private CustomTextField searchField;
    private PostListWidget postList;
    private PostDetailPanel detailPanel;
    private CustomButton searchButton;
    private CustomButton prevPageButton;
    private CustomButton nextPageButton;
    private CustomButton closeButton;
    private LoadingSpinner loadingSpinner;

    private int currentPage = 1; // API uses 1-based pagination
    private int totalPages = 1;
    private int totalItems = 0;
    private boolean isLoading = false;
    private String currentSearchQuery = "";
    private boolean noResultsFound = false;

    public LitematicDownloaderScreen() {
        super(Text.literal("Litematic Downloader"));
    }
    
    @Override
    protected void init() {
        super.init();

        // Calculate split layout dimensions
        int leftPanelWidth = this.width / 2;
        int rightPanelWidth = this.width - leftPanelWidth;

        int searchBarWidth = leftPanelWidth - (PADDING * 3) - 80;

        // Initialize loading spinner
        loadingSpinner = new LoadingSpinner(leftPanelWidth / 2 - 16, this.height / 2 - 16);

        // Search field (left side)
        if (this.client != null) {
            searchField = new CustomTextField(
                this.client,
                PADDING,
                PADDING,
                searchBarWidth,
                SEARCH_BAR_HEIGHT,
                Text.literal("Search")
            );
            searchField.setPlaceholder(Text.literal("Search schematics..."));
            searchField.setOnEnterPressed(this::performSearch);
            searchField.setOnClearPressed(this::performSearch);
            this.addDrawableChild(searchField);
        }

        // Search button (left side)
        searchButton = new CustomButton(
            PADDING + searchBarWidth + PADDING,
            PADDING,
            70,
            SEARCH_BAR_HEIGHT,
            Text.literal("Search"),
            button -> performSearch()
        );
        this.addDrawableChild(searchButton);

        // Post list area (left side)
        int listY = PADDING + SEARCH_BAR_HEIGHT + PADDING;
        int listHeight = this.height - listY - BUTTON_HEIGHT - PADDING * 2;

        postList = new PostListWidget(
            PADDING,
            listY,
            leftPanelWidth - PADDING * 2,
            listHeight,
            this::onPostClick
        );
        this.addDrawableChild(postList);

        // Detail panel (right side)
        detailPanel = new PostDetailPanel(
            leftPanelWidth,
            PADDING,
            rightPanelWidth - PADDING,
            this.height - PADDING * 2
        );

        // Close button (top right corner of detail panel, square)
        // Position it at the top right of the detail panel area
        int closeButtonSize = 20;
        closeButton = new CustomButton(
            this.width - PADDING - closeButtonSize,
            PADDING,
            closeButtonSize,
            closeButtonSize,
            Text.literal("X"),
            button -> this.close()
        );
        closeButton.setRenderAsXIcon(true); // Use X icon instead of text
        // Don't add to children - we'll render it manually on top

        // Pagination buttons at bottom (left side only)
        int bottomY = this.height - BUTTON_HEIGHT - PADDING;

        prevPageButton = new CustomButton(
            PADDING,
            bottomY,
            80,
            BUTTON_HEIGHT,
            Text.literal("< Previous"),
            button -> previousPage()
        );
        prevPageButton.active = false;
        this.addDrawableChild(prevPageButton);

        nextPageButton = new CustomButton(
            leftPanelWidth - PADDING - 80,
            bottomY,
            80,
            BUTTON_HEIGHT,
            Text.literal("Next >"),
            button -> nextPage()
        );
        nextPageButton.active = false;
        this.addDrawableChild(nextPageButton);

        // Perform initial search with empty query to show popular items
        performSearch();
    }

    private void performSearch() {
        if (isLoading) return;

        // Unfocus the search field (same behavior as clicking Search button)
        searchField.setFocused(false);

        currentSearchQuery = searchField.getText().trim();
        currentPage = 1; // Reset to first page on new search

        // Clear selection when searching
        detailPanel.clear();

        loadPage();
    }

    private void loadPage() {
        if (isLoading) {
            return;
        }

        isLoading = true;
        searchButton.active = false;
        prevPageButton.active = false;
        nextPageButton.active = false;
        postList.clear();

        MinemevNetworkManager.searchPosts(currentSearchQuery, "popular", 0, currentPage)
            .thenAccept(this::handleSearchResponse)
            .exceptionally(throwable -> {
                if (this.client != null) {
                    this.client.execute(() -> {
                        isLoading = false;
                        searchButton.active = true;
                        updatePaginationButtons();
                        System.err.println("Error loading posts: " + throwable.getMessage());
                    });
                }
                return null;
            });
    }

    private void handleSearchResponse(MinemevSearchResponse response) {
        if (this.client != null) {
            this.client.execute(() -> {
                totalPages = response.getTotalPages();
                totalItems = response.getTotalItems();

                // The API returns the posts for the current page directly
                MinemevPostInfo[] posts = response.getPosts();
                postList.setPosts(posts);

                isLoading = false;
                searchButton.active = true;
                updatePaginationButtons();

                // Track if no results were found
                noResultsFound = (totalItems == 0);
            });
        }
    }

    private void updatePaginationButtons() {
        prevPageButton.active = currentPage > 1;
        nextPageButton.active = currentPage < totalPages;
    }

    private void previousPage() {
        if (currentPage > 1) {
            currentPage--;
            loadPage();
        }
    }

    private void nextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            loadPage();
        }
    }

    private void onPostClick(MinemevPostInfo post) {
        System.out.println("[LitematicDownloaderScreen] onPostClick called!");
        System.out.println("[LitematicDownloaderScreen] Post: " + (post != null ? post.getTitle() : "null"));
        System.out.println("[LitematicDownloaderScreen] UUID: " + (post != null ? post.getUuid() : "null"));
        System.out.println("[LitematicDownloaderScreen] DetailPanel: " + (detailPanel != null ? "exists" : "null"));

        if (detailPanel != null && post != null) {
            detailPanel.setPost(post);
            System.out.println("[LitematicDownloaderScreen] detailPanel.setPost() called");
        } else {
            System.out.println("[LitematicDownloaderScreen] WARNING: Cannot set post - detailPanel or post is null!");
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int leftPanelWidth = this.width / 2;

        // Fill the entire screen with dark grey background
        context.fill(0, 0, this.width, this.height, 0xFF202020);

        super.render(context, mouseX, mouseY, delta);

        // Render detail panel
        detailPanel.render(context, mouseX, mouseY, delta);

        // Draw page indicator (left side)
        if (totalPages > 1 || totalItems > ITEMS_PER_PAGE) {
            String pageText = String.format("Page %d / %d (%d items)",
                currentPage,
                Math.max(totalPages, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE)),
                totalItems);
            int textWidth = this.textRenderer.getWidth(pageText);
            context.drawTextWithShadow(
                this.textRenderer,
                pageText,
                (leftPanelWidth - textWidth) / 2,
                this.height - BUTTON_HEIGHT / 2 - 4 - PADDING,
                0xFFFFFFFF
            );
        }

        // Draw loading indicator (left side)
        if (isLoading) {
            loadingSpinner.render(context, mouseX, mouseY, delta);
        }

        // Draw no results found message (left side)
        if (noResultsFound) {
            String noResultsText = "No results found :(";
            int textWidth = this.textRenderer.getWidth(noResultsText);
            context.drawTextWithShadow(
                this.textRenderer,
                noResultsText,
                (leftPanelWidth - textWidth) / 2,
                this.height / 2 + 10,
                0xFFFFFFFF
            );
        }

        // Render close button on top of everything
        if (closeButton != null) {
            closeButton.render(context, mouseX, mouseY, delta);
        }
    }


    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Check close button first (highest priority, on top)
        if (button == 0 && closeButton != null) {
            boolean isOverClose = mouseX >= closeButton.getX() &&
                                 mouseX < closeButton.getX() + closeButton.getWidth() &&
                                 mouseY >= closeButton.getY() &&
                                 mouseY < closeButton.getY() + closeButton.getHeight();
            if (isOverClose) {
                this.close();
                return true;
            }
        }

        // Forward to detail panel
        if (detailPanel != null && detailPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // Then let parent handle it (for other widgets)
        return super.mouseClicked(click, doubled);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Forward to detail panel first for scrollbar dragging
        if (detailPanel != null && detailPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        // Then let parent handle it (if Screen has this method)
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Forward to detail panel first for scrollbar release
        if (detailPanel != null && detailPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        // Then let parent handle it (if Screen has this method)
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Forward to detail panel first
        if (detailPanel != null && detailPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        // Then let parent handle it
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        super.close();
    }
}

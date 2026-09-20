package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.vanilib.gui.theme.UITheme;
import com.choculaterie.vanilib.gui.widget.CustomButton;
import com.choculaterie.vanilib.gui.widget.CustomTextField;
import com.choculaterie.gui.widget.PostListWidget;
import com.choculaterie.gui.widget.PostDetailPanel;
import com.choculaterie.gui.widget.SortFilterPanel;
import com.choculaterie.vanilib.gui.widget.LoadingSpinner;
import com.choculaterie.gui.widget.ToastManager;
import com.choculaterie.vanilib.gui.widget.ModMessageBanner;
import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.models.MinemevSearchResponse;
import com.choculaterie.vanilib.models.ModMessage;
import com.choculaterie.network.MinemevNetworkManager;
import com.choculaterie.network.ChoculaterieNetworkManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

public class LitematicDownloaderScreen extends Screen {
    private static final int SEARCH_BAR_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PADDING = 10;
    private static final int CLIPBOARD_BANNER_HEIGHT = 16;
    private static final Pattern QUICK_SHARE_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?choculaterie\\.com/qs/([A-Za-z0-9_-]+)");

    private CustomTextField searchField;
    private PostListWidget postList;
    private PostDetailPanel detailPanel;
    private SortFilterPanel sortFilterPanel;
    private CustomButton searchButton;
    private CustomButton prevPageButton;
    private CustomButton nextPageButton;
    private CustomButton folderButton;
    private CustomButton filterButton;
    private CustomButton closeButton;
    private LoadingSpinner loadingSpinner;
    private ToastManager toastManager;
    private ModMessageBanner modMessageBanner;

    private int currentPage = 1;
    private int totalPages = 1;
    private final List<MinemevPostInfo> pageBuffer = new ArrayList<>();
    private final List<MinemevPostInfo[]> fetchedPages = new ArrayList<>();
    private final Set<String> everSeen = new HashSet<>();
    private int sourcePage = 1;
    private boolean sourcesExhausted = false;
    private boolean totalKnown = false;
    private int reportedItems = 0;
    private boolean anyUnknown = false;
    private static final int MAX_FETCHES_PER_PAGE = 4;
    private int totalItems = 0;
    private boolean pendingReload = false;
    private boolean isLoading = false;
    private String currentSearchQuery = "";
    private boolean noResultsFound = false;
    private boolean showFilterPanel = false;
    private boolean initialized = false;
    private String clipboardQuickShareUrl = null;
    private boolean clipboardBannerDismissed = false;
    private BannerState bannerState = BannerState.NONE;
    private String bannerSuccessFilename = null;
    private long bannerSuccessTime = 0;
    private static final long BANNER_SUCCESS_DURATION_MS = 2500;

    private enum BannerState {
        NONE, DETECTED, DOWNLOADING, SUCCESS
    }

    public LitematicDownloaderScreen() {
        super(Component.literal("Litematic Downloader"));
    }

    @Override
    protected void init() {
        super.init();

        String previousSearchText = (searchField != null) ? searchField.getValue() : "";

        if (this.minecraft != null) {
            toastManager = new ToastManager(this.minecraft);
        }

        int leftPanelWidth = this.width / 2;
        int rightPanelWidth = this.width - leftPanelWidth;

        boolean isCompact = leftPanelWidth < 250;
        boolean isVeryCompact = leftPanelWidth < 180;

        int searchButtonWidth = isVeryCompact ? 25 : (isCompact ? 40 : 70);
        int paginationButtonWidth = isVeryCompact ? 25 : (isCompact ? 50 : 80);
        String searchLabel = isVeryCompact ? "🔍" : (isCompact ? "🔍" : "Search");
        String prevLabel = isVeryCompact ? "◀" : (isCompact ? "◀" : "< Previous");
        String nextLabel = isVeryCompact ? "▶" : (isCompact ? "▶" : "Next >");

        int searchBarWidth = Math.max(50, leftPanelWidth - (PADDING * 3) - searchButtonWidth);

        loadingSpinner = new LoadingSpinner(leftPanelWidth / 2 - 16, this.height / 2 - 16);

        if (this.minecraft != null) {
            searchField = new CustomTextField(
                    this.minecraft,
                    PADDING,
                    PADDING,
                    searchBarWidth,
                    SEARCH_BAR_HEIGHT,
                    Component.literal("Search")
            );
            searchField.setPlaceholder(Component.literal(isCompact ? "Search..." : "Search schematics..."));
            searchField.setOnEnterPressed(this::performSearch);
            searchField.setOnClearPressed(this::performSearch);
            if (!previousSearchText.isEmpty()) {
                searchField.setValue(previousSearchText);
            }
            this.addRenderableWidget(searchField);
        }

        searchButton = new CustomButton(
                PADDING + searchBarWidth + PADDING,
                PADDING,
                searchButtonWidth,
                SEARCH_BAR_HEIGHT,
                Component.literal(searchLabel),
                button -> performSearch()
        );
        this.addRenderableWidget(searchButton);

        checkClipboardForQuickShare();

        int bannerOffset = isClipboardBannerVisible() ? CLIPBOARD_BANNER_HEIGHT + 2 : 0;
        int listY = PADDING + SEARCH_BAR_HEIGHT + PADDING + bannerOffset;
        int listHeight = this.height - listY - BUTTON_HEIGHT - PADDING * 2;
        int listWidth = leftPanelWidth - PADDING * 2;

        if (postList == null) {
            postList = new PostListWidget(PADDING, listY, listWidth, listHeight, this::onPostClick);
        } else {
            postList.setDimensions(PADDING, listY, listWidth, listHeight);
        }
        this.addRenderableWidget(postList);

        int rightPanelContentWidth = rightPanelWidth - PADDING;
        int rightPanelContentHeight = this.height - PADDING * 2;

        if (detailPanel == null) {
            detailPanel = new PostDetailPanel(leftPanelWidth, PADDING, rightPanelContentWidth, rightPanelContentHeight);
        } else {
            detailPanel.setDimensions(leftPanelWidth, PADDING, rightPanelContentWidth, rightPanelContentHeight);
        }

        sortFilterPanel = new SortFilterPanel(leftPanelWidth, PADDING, rightPanelContentWidth, rightPanelContentHeight);
        sortFilterPanel.setOnSettingsChanged(this::onFilterSettingsChanged);

        int closeButtonSize = 20;
        folderButton = new CustomButton(
                this.width - PADDING - closeButtonSize * 3,
                PADDING,
                closeButtonSize,
                closeButtonSize,
                Component.literal("📁"),
                button -> openFolderPage()
        );

        filterButton = new CustomButton(
                this.width - PADDING - closeButtonSize * 2,
                PADDING,
                closeButtonSize,
                closeButtonSize,
                Component.literal("⚙"),
                button -> toggleFilterPanel()
        );

        closeButton = new CustomButton(
                this.width - PADDING - closeButtonSize,
                PADDING,
                closeButtonSize,
                closeButtonSize,
                Component.literal("X"),
                button -> this.onClose()
        );
        closeButton.setRenderAsXIcon(true);

        int bottomY = this.height - BUTTON_HEIGHT - PADDING;

        prevPageButton = new CustomButton(
                PADDING,
                bottomY,
                paginationButtonWidth,
                BUTTON_HEIGHT,
                Component.literal(prevLabel),
                button -> previousPage()
        );
        prevPageButton.active = false;
        this.addRenderableWidget(prevPageButton);

        nextPageButton = new CustomButton(
                leftPanelWidth - PADDING - paginationButtonWidth,
                bottomY,
                paginationButtonWidth,
                BUTTON_HEIGHT,
                Component.literal(nextLabel),
                button -> nextPage()
        );
        nextPageButton.active = false;
        this.addRenderableWidget(nextPageButton);

        modMessageBanner = new ModMessageBanner(0, 0, this.width);
        modMessageBanner.setOnDismiss(this::onModMessageDismissed);

        if (!initialized) {
            initialized = true;
            fetchModMessage();
            performSearch();
        } else {
            updatePaginationButtons();
        }
    }

    private void checkClipboardForQuickShare() {
        try {
            long windowHandle = Minecraft.getInstance().getWindow().handle();
            if (windowHandle == 0) return;
            String clipboard = GLFW.glfwGetClipboardString(windowHandle);
            if (clipboard != null) {
                String trimmed = clipboard.trim();
                Matcher matcher = QUICK_SHARE_PATTERN.matcher(trimmed);
                if (matcher.find() && !DownloadSettings.getInstance().isQuickShareLinkDismissed(trimmed)) {
                    clipboardQuickShareUrl = trimmed;
                    clipboardBannerDismissed = false;
                    bannerState = BannerState.DETECTED;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isClipboardBannerVisible() {
        return bannerState == BannerState.DETECTED
                || bannerState == BannerState.DOWNLOADING
                || bannerState == BannerState.SUCCESS;
    }

    private void updateListPosition() {
        if (postList == null) return;
        int leftPanelWidth = this.width / 2;
        int bannerOffset = isClipboardBannerVisible() ? CLIPBOARD_BANNER_HEIGHT + 2 : 0;
        int listY = PADDING + SEARCH_BAR_HEIGHT + PADDING + bannerOffset;
        int listHeight = this.height - listY - BUTTON_HEIGHT - PADDING * 2;
        int listWidth = leftPanelWidth - PADDING * 2;
        postList.setDimensions(PADDING, listY, listWidth, listHeight);
    }

    private void clearClipboard() {
        try {
            long windowHandle = Minecraft.getInstance().getWindow().handle();
            if (windowHandle != 0) {
                GLFW.glfwSetClipboardString(windowHandle, "");
            }
        } catch (Exception ignored) {
        }
    }

    private void fetchModMessage() {
        ChoculaterieNetworkManager.getModMessage()
            .thenAccept(message -> {
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        if (message != null && message.hasMessage() && message.id() != null) {
                            int dismissedId = DownloadSettings.getInstance().getDismissedModMessageId();
                            if (message.id() != dismissedId) {
                                modMessageBanner.setMessage(message);
                            }
                        }
                    });
                }
            })
            .exceptionally(throwable -> {
                System.err.println("[LitematicDownloaderScreen] Failed to fetch mod message: " + throwable.getMessage());
                return null;
            });
    }

    private void onModMessageDismissed(ModMessage message) {
        if (message != null && message.id() != null) {
            DownloadSettings.getInstance().setDismissedModMessageId(message.id());
        }
    }

    private boolean isMouseOverButton(CustomButton button, double mouseX, double mouseY) {
        return button != null &&
               mouseX >= button.getX() &&
               mouseX < button.getX() + button.getWidth() &&
               mouseY >= button.getY() &&
               mouseY < button.getY() + button.getHeight();
    }

    private Object getActivePanel() {
        return showFilterPanel ? sortFilterPanel : detailPanel;
    }

    private void performSearch() {
        searchField.setFocused(false);

        currentSearchQuery = searchField.getValue().trim();
        resetToFirstPage();

        Matcher matcher = QUICK_SHARE_PATTERN.matcher(currentSearchQuery);
        if (matcher.find()) {
            String code = matcher.group(1);
            handleQuickShareDownload(code);
            return;
        }

        detailPanel.clear();

        if (isLoading) {
            pendingReload = true;
            return;
        }

        loadPage();
    }

    private void handleQuickShareDownload(String code) {
        isLoading = true;
        searchButton.active = false;

        if (toastManager != null) {
            toastManager.showInfo("Downloading quick-share: " + code + "...");
        }

        ChoculaterieNetworkManager.downloadQuickShare(code)
                .thenAccept(result -> {
                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> {
                            try {
                                Path schematicsPath = Paths.get(DownloadSettings.getInstance().getAbsoluteDownloadPath());
                                File schematicsDir = schematicsPath.toFile();
                                if (!schematicsDir.exists()) {
                                    schematicsDir.mkdirs();
                                }

                                String fileName = result.filename();
                                if (!fileName.endsWith(".litematic")) {
                                    fileName += ".litematic";
                                }

                                File outputFile = new File(schematicsDir, fileName);

                                int counter = 1;
                                while (outputFile.exists()) {
                                    String baseName = fileName.substring(0, fileName.lastIndexOf(".litematic"));
                                    outputFile = new File(schematicsDir, baseName + "_" + counter + ".litematic");
                                    counter++;
                                }

                                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                    fos.write(result.data());
                                }

                                String savedName = outputFile.getName();
                                System.out.println("[QuickShare] Saved to: " + outputFile.getAbsolutePath());

                                isLoading = false;
                                searchButton.active = true;
                                searchField.setValue("");

                                if (toastManager != null) {
                                    toastManager.showSuccess("Downloaded: " + savedName);
                                }
                            } catch (Exception e) {
                                isLoading = false;
                                searchButton.active = true;
                                System.err.println("[QuickShare] Failed to save file: " + e.getMessage());
                                if (toastManager != null) {
                                    toastManager.showError("Failed to save file: " + e.getMessage());
                                }
                            }
                        });
                    }
                })
                .exceptionally(throwable -> {
                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> {
                            isLoading = false;
                            searchButton.active = true;

                            String errorMessage = throwable.getCause() != null
                                    ? throwable.getCause().getMessage()
                                    : throwable.getMessage();

                            System.err.println("[QuickShare] Download failed: " + errorMessage);
                            if (toastManager != null) {
                                toastManager.showError("Quick-share download failed: " + errorMessage);
                            }
                        });
                };
                    return null;
                });
    }

    private void performQuickShareFromBanner() {
        Matcher matcher = QUICK_SHARE_PATTERN.matcher(clipboardQuickShareUrl);
        if (!matcher.find()) return;
        String code = matcher.group(1);

        ChoculaterieNetworkManager.downloadQuickShare(code)
                .thenAccept(result -> {
                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> {
                            try {
                                Path schematicsPath = Paths.get(DownloadSettings.getInstance().getAbsoluteDownloadPath());
                                File schematicsDir = schematicsPath.toFile();
                                if (!schematicsDir.exists()) {
                                    schematicsDir.mkdirs();
                                }

                                String fileName = result.filename();
                                if (!fileName.endsWith(".litematic")) {
                                    fileName += ".litematic";
                                }

                                File outputFile = new File(schematicsDir, fileName);

                                int counter = 1;
                                while (outputFile.exists()) {
                                    String baseName = fileName.substring(0, fileName.lastIndexOf(".litematic"));
                                    outputFile = new File(schematicsDir, baseName + "_" + counter + ".litematic");
                                    counter++;
                                }

                                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                    fos.write(result.data());
                                }

                                bannerSuccessFilename = outputFile.getName();
                                bannerSuccessTime = System.currentTimeMillis();
                                bannerState = BannerState.SUCCESS;
                                searchField.setValue("");
                                System.out.println("[QuickShare] Saved to: " + outputFile.getAbsolutePath());
                            } catch (Exception e) {
                                bannerState = BannerState.NONE;
                                clipboardBannerDismissed = true;
                                updateListPosition();
                                System.err.println("[QuickShare] Failed to save file: " + e.getMessage());
                                if (toastManager != null) {
                                    toastManager.showError("Failed to save file: " + e.getMessage());
                                }
                            }
                        });
                    }
                })
                .exceptionally(throwable -> {
                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> {
                            bannerState = BannerState.NONE;
                            clipboardBannerDismissed = true;
                            updateListPosition();

                            String errorMessage = throwable.getCause() != null
                                    ? throwable.getCause().getMessage()
                                    : throwable.getMessage();

                            System.err.println("[QuickShare] Download failed: " + errorMessage);
                            if (toastManager != null) {
                                toastManager.showError("Quick-share download failed: " + errorMessage);
                            }
                        });
                    }
                    return null;
                });
    }

    private void loadPage() {
        if (isLoading) {
            return;
        }

        if (fetchedPages.size() >= currentPage) {
            showCachedPage();
            return;
        }

        isLoading = true;
        searchButton.active = false;
        prevPageButton.active = false;
        nextPageButton.active = false;
        postList.clear();
        fetchMore(0);
    }

    private void fetchMore(int attempt) {
        int pageSize = itemsPerPage();
        if (pageBuffer.size() >= pageSize || sourcesExhausted || attempt >= MAX_FETCHES_PER_PAGE) {
            finishPage();
            return;
        }

        String sort = sortFilterPanel != null ? sortFilterPanel.getSelectedSort() : "newest";
        String tag = sortFilterPanel != null ? sortFilterPanel.getTagFilter() : null;
        String excludeVendor = sortFilterPanel != null ? sortFilterPanel.getExcludedVendorsParam() : null;

        MinemevNetworkManager.searchPostsAdvanced(currentSearchQuery, sort, 1, sourcePage, tag, null, excludeVendor, pageSize)
                .thenAccept(response -> handleSearchResponse(response, attempt))
                .exceptionally(throwable -> {
                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> {
                            isLoading = false;
                            searchButton.active = true;
                            updatePaginationButtons();

                            if (pendingReload) {
                                pendingReload = false;
                                loadPage();
                                return;
                            }

                            String errorMessage = throwable.getMessage();
                            String userMessage;

                            if (errorMessage != null) {
                                if (errorMessage.contains("UnknownHostException") ||
                                    errorMessage.contains("ConnectException") ||
                                    errorMessage.contains("SocketTimeoutException") ||
                                    errorMessage.contains("NoRouteToHostException")) {
                                    userMessage = "Network error: No internet connection";
                                } else if (errorMessage.contains("HTTP error code: 404")) {
                                    userMessage = "Server error: Resource not found";
                                } else if (errorMessage.contains("HTTP error code: 500")) {
                                    userMessage = "Server error: Internal server error";
                                } else if (errorMessage.contains("HTTP error code:")) {
                                    userMessage = "Server error: " + errorMessage.substring(errorMessage.indexOf("HTTP error code:"));
                                } else if (errorMessage.contains("Failed to search posts")) {
                                    Throwable cause = throwable.getCause();
                                    if (cause != null) {
                                        String causeMsg = cause.getMessage();
                                        if (causeMsg != null && causeMsg.contains("java.net.")) {
                                            userMessage = "Network error: Cannot reach server";
                                        } else {
                                            userMessage = "Search failed: " + (causeMsg != null ? causeMsg : "Unknown error");
                                        }
                                    } else {
                                        userMessage = "Search failed: Connection error";
                                    }
                                } else {
                                    userMessage = "Search failed: " + errorMessage;
                                }
                            } else {
                                userMessage = "Search failed: Unknown error";
                            }

                            String fullError = "Error: " + errorMessage;
                            if (throwable.getCause() != null) {
                                fullError += "\nCause: " + throwable.getCause().toString();
                            }

                            System.err.println("Error loading posts: " + errorMessage);
                            if (toastManager != null) {
                                toastManager.showError(userMessage, fullError);
                            }
                        });
                    }
                    return null;
                });
    }

    private void handleSearchResponse(MinemevSearchResponse response, int attempt) {
        if (this.minecraft != null) {
            this.minecraft.execute(() -> {
                if (pendingReload) {
                    pendingReload = false;
                    isLoading = false;
                    loadPage();
                    return;
                }

                int reportedPages = response.totalPages();
                if (reportedPages < 0 || response.totalItems() < 0) {
                    anyUnknown = true;
                } else if (reportedItems == 0) {
                    reportedItems = response.totalItems();
                }
                sourcePage++;
                int added = 0;
                for (MinemevPostInfo post : response.posts()) {
                    String uuid = post.uuid();
                    if (uuid == null || uuid.isEmpty() || everSeen.add(uuid)) {
                        pageBuffer.add(post);
                        added++;
                    }
                }
                if (added == 0 || (reportedPages > 0 && sourcePage > reportedPages)) {
                    sourcesExhausted = true;
                }

                fetchMore(attempt + 1);
            });
        }
    }

    private void resetToFirstPage() {
        currentPage = 1;
        pageBuffer.clear();
        fetchedPages.clear();
        everSeen.clear();
        sourcePage = 1;
        sourcesExhausted = false;
        totalKnown = false;
        reportedItems = 0;
        anyUnknown = false;
        totalPages = 1;
        totalItems = 0;
    }

    private int itemsPerPage() {
        return sortFilterPanel != null ? sortFilterPanel.getItemsPerPage() : 20;
    }

    private void showCachedPage() {
        MinemevPostInfo[] posts = fetchedPages.get(currentPage - 1);
        if (!totalKnown) {
            boolean more = !sourcesExhausted || !pageBuffer.isEmpty();
            totalPages = fetchedPages.size() + (more ? 1 : 0);
        }
        postList.setPosts(posts);
        noResultsFound = posts.length == 0;
        updatePaginationButtons();
    }

    private void finishPage() {
        int pageSize = itemsPerPage();
        int take = Math.min(pageSize, pageBuffer.size());
        List<MinemevPostInfo> slice = new ArrayList<>(pageBuffer.subList(0, take));
        pageBuffer.subList(0, take).clear();

        while (fetchedPages.size() < currentPage - 1) {
            fetchedPages.add(new MinemevPostInfo[0]);
        }
        MinemevPostInfo[] posts = slice.toArray(new MinemevPostInfo[0]);
        if (fetchedPages.size() >= currentPage) {
            fetchedPages.set(currentPage - 1, posts);
        } else {
            fetchedPages.add(posts);
        }

        if (sourcesExhausted && pageBuffer.isEmpty()) {
            totalPages = Math.max(1, fetchedPages.size());
            totalItems = everSeen.size();
            totalKnown = true;
        } else if (!anyUnknown && reportedItems > 0) {
            totalItems = reportedItems;
            totalPages = Math.max(currentPage, (reportedItems + pageSize - 1) / pageSize);
            totalKnown = true;
        } else {
            totalPages = currentPage + 1;
            totalItems = everSeen.size();
            totalKnown = false;
        }

        isLoading = false;
        searchButton.active = true;
        postList.setPosts(posts);
        noResultsFound = posts.length == 0;
        updatePaginationButtons();
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
        if (detailPanel != null && post != null) {
            detailPanel.setPost(post);
            if (showFilterPanel) {
                showFilterPanel = false;
            }
        }
    }

    private void openFolderPage() {
        if (detailPanel != null) {
            detailPanel.closeDropdown();
        }
        if (this.minecraft != null) {
            LocalFolderPage folderPage = new LocalFolderPage(this);
            folderPage.setOnApiToggleChanged(this::refreshPostList);
            this.minecraft.gui.setScreen(folderPage);
        }
    }

    public void refreshPostList() {
        resetToFirstPage();
        loadPage();
    }

    private void toggleFilterPanel() {
        showFilterPanel = !showFilterPanel;
    }

    public void reloadAfterPluginChange() {
        resetToFirstPage();
        if (sortFilterPanel != null) {
            sortFilterPanel.refreshVendors();
        }
        if (isLoading) {
            pendingReload = true;
            return;
        }
        loadPage();
    }

    private void onFilterSettingsChanged(SortFilterPanel panel) {
        resetToFirstPage();
        if (isLoading) {
            pendingReload = true;
            return;
        }
        loadPage();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int leftPanelWidth = this.width / 2;

        context.fill(0, 0, this.width, this.height, 0xFF202020);

        boolean imageViewerOpen = detailPanel != null && detailPanel.hasImageViewerOpen();
        boolean confirmPopupOpen = detailPanel != null && detailPanel.hasConfirmPopupOpen();

        boolean mouseOverBanner = modMessageBanner != null && modMessageBanner.isVisible()
                && modMessageBanner.isMouseOver(mouseX, mouseY);

        int listMouseX = (mouseOverBanner || imageViewerOpen || confirmPopupOpen) ? -1 : mouseX;
        int listMouseY = (mouseOverBanner || imageViewerOpen || confirmPopupOpen) ? -1 : mouseY;

        int detailMouseX = (mouseOverBanner || imageViewerOpen) ? -1 : mouseX;
        int detailMouseY = (mouseOverBanner || imageViewerOpen) ? -1 : mouseY;

        super.extractRenderState(context, listMouseX, listMouseY, delta);

        if (showFilterPanel) {
            sortFilterPanel.extractRenderState(context, detailMouseX, detailMouseY, delta);
        } else {
            detailPanel.extractRenderState(context, detailMouseX, detailMouseY, delta);
        }

        if (totalPages > 1) {
            boolean isCompact = leftPanelWidth < 250;
            boolean isVeryCompact = leftPanelWidth < 180;
            int paginationButtonWidth = isVeryCompact ? 25 : (isCompact ? 50 : 80);

            int availableWidth = leftPanelWidth - PADDING - paginationButtonWidth - PADDING - paginationButtonWidth - PADDING;

            String pageText;
            String fullText = totalKnown
                    ? String.format("Page %d / %d (%d items)", currentPage, totalPages, totalItems)
                    : String.format("Page %d (%d items)", currentPage, totalItems);
            String mediumText = totalKnown
                    ? String.format("%d / %d", currentPage, totalPages)
                    : String.format("Page %d", currentPage);
            String shortText = totalKnown
                    ? String.format("%d/%d", currentPage, totalPages)
                    : String.valueOf(currentPage);

            if (this.font.width(fullText) <= availableWidth) {
                pageText = fullText;
            } else if (this.font.width(mediumText) <= availableWidth) {
                pageText = mediumText;
            } else if (this.font.width(shortText) <= availableWidth) {
                pageText = shortText;
            } else {
                pageText = null;
            }

            if (pageText != null) {
                int textWidth = this.font.width(pageText);
                context.text(
                        this.font,
                        pageText,
                        (leftPanelWidth - textWidth) / 2,
                        this.height - BUTTON_HEIGHT / 2 - 4 - PADDING,
                        0xFFFFFFFF
                );
            }
        }

        if (isLoading) {
            loadingSpinner.extractRenderState(context, listMouseX, listMouseY, delta);
        }

        if (isClipboardBannerVisible()) {
            if (bannerState == BannerState.SUCCESS
                    && System.currentTimeMillis() - bannerSuccessTime > BANNER_SUCCESS_DURATION_MS) {
                bannerState = BannerState.NONE;
                clipboardBannerDismissed = true;
                updateListPosition();
            } else {
                int bannerX = PADDING;
                int bannerY = PADDING + SEARCH_BAR_HEIGHT + 2;
                int leftPanelW = this.width / 2;
                int bannerWidth = leftPanelW - PADDING * 2;
                int textY2 = bannerY + (CLIPBOARD_BANNER_HEIGHT - 8) / 2;

                if (bannerState == BannerState.SUCCESS) {
                    context.fill(bannerX, bannerY, bannerX + bannerWidth, bannerY + CLIPBOARD_BANNER_HEIGHT, UITheme.Colors.ACCENT_GREEN_DARK);
                    context.fill(bannerX, bannerY, bannerX + 2, bannerY + CLIPBOARD_BANNER_HEIGHT, UITheme.Colors.ACCENT_GREEN);
                    String successText = "✓ Downloaded: " + bannerSuccessFilename;
                    if (this.font.width(successText) > bannerWidth - 8) {
                        successText = "✓ Downloaded";
                    }
                    context.text(this.font, successText, bannerX + 6, textY2, 0xFFFFFFFF);
                } else {
                    context.fill(bannerX, bannerY, bannerX + bannerWidth, bannerY + CLIPBOARD_BANNER_HEIGHT, 0xFF2A3A5F);
                    context.fill(bannerX, bannerY, bannerX + 2, bannerY + CLIPBOARD_BANNER_HEIGHT, 0xFF4488FF);
                    String bannerText;
                    if (bannerState == BannerState.DOWNLOADING) {
                        bannerText = "📋 Downloading quick-share...";
                    } else {
                        bannerText = "📋 Quick-share link detected, click to download";
                        if (this.font.width(bannerText) > bannerWidth - 20) {
                            bannerText = "📋 Quick-share, click to download";
                        }
                    }
                    context.text(this.font, bannerText, bannerX + 6, textY2, 0xFFFFFFFF);
                    if (bannerState == BannerState.DETECTED) {
                        String dismissText = "✕";
                        int dismissX = bannerX + bannerWidth - this.font.width(dismissText) - 4;
                        boolean hoverDismiss = mouseX >= dismissX && mouseX < bannerX + bannerWidth
                                && mouseY >= bannerY && mouseY < bannerY + CLIPBOARD_BANNER_HEIGHT;
                        context.text(this.font, dismissText, dismissX, textY2, hoverDismiss ? 0xFFFFFFFF : 0xFFAAAAAA);
                    }
                }
            }
        }

        if (noResultsFound && !isLoading) {
            String noResultsText = "No results found :(";
            int textWidth = this.font.width(noResultsText);
            context.text(
                    this.font,
                    noResultsText,
                    (leftPanelWidth - textWidth) / 2,
                    this.height / 2 + 10,
                    0xFFFFFFFF
            );
        }

        if (folderButton != null) {
            folderButton.extractRenderState(context, listMouseX, listMouseY, delta);
        }

        if (filterButton != null) {
            filterButton.extractRenderState(context, listMouseX, listMouseY, delta);
        }

        if (closeButton != null) {
            closeButton.extractRenderState(context, listMouseX, listMouseY, delta);
        }

        if (modMessageBanner != null && modMessageBanner.isVisible()) {
            modMessageBanner.extractRenderState(context, mouseX, mouseY, delta);
        }

        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            detailPanel.renderImageViewer(context, mouseX, mouseY, delta);
        }

        if (toastManager != null) {
            toastManager.render(context, delta, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            return detailPanel.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0 && toastManager != null) {
            if (toastManager.mouseClicked(mouseX, mouseY)) {
                return true;
            }
            if (toastManager.isMouseOverToast(mouseX, mouseY)) {
                return true;
            }
        }

        if (button == 0 && modMessageBanner != null && modMessageBanner.isVisible()) {
            if (modMessageBanner.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (modMessageBanner.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }

        if (button == 0 && isMouseOverButton(closeButton, mouseX, mouseY)) {
            this.onClose();
            return true;
        }

        if (button == 0 && isMouseOverButton(filterButton, mouseX, mouseY)) {
            toggleFilterPanel();
            return true;
        }

        if (button == 0 && isMouseOverButton(folderButton, mouseX, mouseY)) {
            openFolderPage();
            return true;
        }

        if (showFilterPanel) {
            if (sortFilterPanel != null && sortFilterPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        } else {
            if (detailPanel != null && detailPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (button == 0 && bannerState == BannerState.DETECTED) {
            int bannerX = PADDING;
            int bannerY = PADDING + SEARCH_BAR_HEIGHT + 2;
            int leftPanelW = this.width / 2;
            int bannerWidth = leftPanelW - PADDING * 2;
            if (mouseX >= bannerX && mouseX < bannerX + bannerWidth
                    && mouseY >= bannerY && mouseY < bannerY + CLIPBOARD_BANNER_HEIGHT) {
                String dismissText = "✕";
                int dismissX = bannerX + bannerWidth - this.font.width(dismissText) - 4;
                if (mouseX >= dismissX) {
                    DownloadSettings.getInstance().dismissQuickShareLink(clipboardQuickShareUrl);
                    bannerState = BannerState.NONE;
                    clipboardBannerDismissed = true;
                    updateListPosition();
                } else {
                    bannerState = BannerState.DOWNLOADING;
                    searchField.setValue(clipboardQuickShareUrl);
                    clearClipboard();
                    performQuickShareFromBanner();
                }
                return true;
            }
        }

        if (button == 0 && searchField != null) {
            if (searchField.isMouseOver(mouseX, mouseY)) {
                searchField.setFocused(true);
                return true;
            } else {
                searchField.setFocused(false);
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (mouseDragged(event.x(), event.y(), event.button(), dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (mouseReleased(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseReleased(event);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        Object panel = getActivePanel();
        if (panel instanceof SortFilterPanel && ((SortFilterPanel) panel).mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (panel instanceof PostDetailPanel && ((PostDetailPanel) panel).mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Object panel = getActivePanel();
        if (panel instanceof SortFilterPanel && ((SortFilterPanel) panel).mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (panel instanceof PostDetailPanel && ((PostDetailPanel) panel).mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Object panel = getActivePanel();
        if (panel instanceof SortFilterPanel && ((SortFilterPanel) panel).mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (panel instanceof PostDetailPanel && ((PostDetailPanel) panel).mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (detailPanel != null && detailPanel.hasConfirmPopupOpen()) {
            detailPanel.keyPressed(256, 0, 0);
            return false;
        }
        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            detailPanel.keyPressed(256, 0, 0);
            return false;
        }
        return super.shouldCloseOnEsc();
    }

    @Override
    public void onClose() {
        CustomTextField.restoreMinecraftCharCallback();
        super.onClose();
    }
}

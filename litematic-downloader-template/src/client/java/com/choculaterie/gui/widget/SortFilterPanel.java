package com.choculaterie.gui.widget;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.network.MinemevNetworkManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Panel widget for sorting and filtering search options
 */
public class SortFilterPanel implements Drawable, Element {
    private static final int PANEL_BG_COLOR = 0xFF252525;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int SECTION_BG_COLOR = 0xFF3A3A3A;
    private static final int PADDING = 10;

    private int x;
    private int y;
    private int width;
    private int height;

    private final MinecraftClient client;
    private double scrollOffset = 0;
    private int contentHeight = 0;
    private ScrollBar scrollBar;

    // Sort options
    private String selectedSort = "popular";
    private final String[] sortOptions = {"newest", "popular", "oldest", "downloads"};
    private final String[] sortLabels = {"Newest", "Popular", "Oldest", "Downloads"};

    // Pagination
    private int itemsPerPage = 10;
    private final int[] pageOptions = {5, 10, 20, 50};

    // Vendor filter
    private String[] availableVendors = new String[0];
    private Set<String> excludedVendors = new HashSet<>();
    private boolean isLoadingVendors = false;
    private List<ToggleButton> vendorToggles = new ArrayList<>();

    // Version filter
    private String versionFilter = "all";

    // Tag filter
    private String tagFilter = "";

    // Callback when settings change
    private Consumer<SortFilterPanel> onSettingsChanged;

    // UI Components
    private SimpleTextField tagTextField;
    private CustomButton applyButton;
    private CustomButton resetButton;

    public SortFilterPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = MinecraftClient.getInstance();
        this.scrollBar = new ScrollBar(x + width - 10, y + 30, height - 60);

        // Load saved settings
        loadSettings();

        // Initialize text field
        this.tagTextField = new SimpleTextField(client, x + PADDING, y + 100, width - PADDING * 2 - 10, 18);
        this.tagTextField.setPlaceholder("Enter tag...");
        this.tagTextField.setText(tagFilter);

        // Initialize buttons
        initButtons();

        // Load vendors
        loadVendors();
    }

    private void loadSettings() {
        DownloadSettings settings = DownloadSettings.getInstance();
        selectedSort = settings.getSortOption();
        itemsPerPage = settings.getItemsPerPage();
        tagFilter = settings.getTagFilter();
        String savedExcluded = settings.getExcludedVendors();
        if (savedExcluded != null && !savedExcluded.isEmpty()) {
            excludedVendors.addAll(Arrays.asList(savedExcluded.split(",")));
        }
    }

    private void saveSettings() {
        DownloadSettings settings = DownloadSettings.getInstance();
        settings.setSortOption(selectedSort);
        settings.setItemsPerPage(itemsPerPage);
        settings.setTagFilter(tagFilter);
        settings.setExcludedVendors(String.join(",", excludedVendors));
    }

    private void initButtons() {
        int buttonY = y + height - 30;
        int buttonWidth = (width - PADDING * 3) / 2;
        int buttonHeight = 20;

        applyButton = new CustomButton(
                x + PADDING,
                buttonY,
                buttonWidth,
                buttonHeight,
                Text.literal(width < 150 ? "✓" : "Apply"),
                btn -> {} // Click is handled in mouseClicked
        );

        resetButton = new CustomButton(
                x + PADDING * 2 + buttonWidth,
                buttonY,
                buttonWidth,
                buttonHeight,
                Text.literal(width < 150 ? "↺" : "Reset"),
                btn -> {} // Click is handled in mouseClicked
        );
    }

    public void setOnSettingsChanged(Consumer<SortFilterPanel> callback) {
        this.onSettingsChanged = callback;
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollBar = new ScrollBar(x + width - 10, y + 30, height - 60);
        if (tagTextField != null) {
            tagTextField.setPosition(x + PADDING, y + 100);
            tagTextField.setWidth(width - PADDING * 2 - 10);
        }
        // Reinitialize buttons with new dimensions
        initButtons();
        // Recreate vendor toggles
        createVendorToggles();
    }


    private void loadVendors() {
        isLoadingVendors = true;
        MinemevNetworkManager.getVendors()
            .thenAccept(vendors -> {
                if (client != null) {
                    client.execute(() -> {
                        availableVendors = vendors;
                        isLoadingVendors = false;
                        createVendorToggles();
                    });
                }
            })
            .exceptionally(throwable -> {
                if (client != null) {
                    client.execute(() -> {
                        isLoadingVendors = false;
                        System.err.println("Failed to load vendors: " + throwable.getMessage());
                    });
                }
                return null;
            });
    }

    private void createVendorToggles() {
        vendorToggles.clear();
        for (int i = 0; i < availableVendors.length; i++) {
            final String vendor = availableVendors[i];
            boolean isIncluded = !excludedVendors.contains(vendor);
            ToggleButton toggle = new ToggleButton(
                    x + PADDING,
                    0, // Y will be set during render
                    isIncluded,
                    enabled -> {
                        if (enabled) {
                            excludedVendors.remove(vendor);
                        } else {
                            excludedVendors.add(vendor);
                        }
                    }
            );
            vendorToggles.add(toggle);
        }
    }

    // Getters for current settings
    public String getSelectedSort() {
        return selectedSort;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public String getExcludedVendorsParam() {
        if (excludedVendors.isEmpty()) {
            return null;
        }
        return String.join(",", excludedVendors);
    }

    public String getTagFilter() {
        return tagFilter.isEmpty() ? null : tagFilter;
    }

    public String getVersionFilter() {
        return versionFilter.equals("all") ? null : versionFilter;
    }

    private void notifySettingsChanged() {
        if (onSettingsChanged != null) {
            onSettingsChanged.accept(this);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw panel background
        context.fill(x, y, x + width, y + height, PANEL_BG_COLOR);

        // Draw left border
        context.fill(x, y, x + 1, y + height, 0xFF555555);

        // Calculate responsive sizes
        boolean isCompact = width < 180;

        // Draw title
        String title = isCompact ? "Filters" : "Sort & Filter";
        context.drawTextWithShadow(client.textRenderer, title, x + PADDING, y + PADDING, TITLE_COLOR);

        // Enable scissor for scrolling content
        int contentStartY = y + 30;
        context.enableScissor(x + 1, contentStartY, x + width - 12, y + height - 40);

        int currentY = contentStartY - (int) scrollOffset;
        contentHeight = 0;

        // ===== SORT SECTION =====
        currentY = renderSortSection(context, mouseX, mouseY, currentY, isCompact);

        // ===== PAGINATION SECTION =====
        currentY = renderPaginationSection(context, mouseX, mouseY, currentY, isCompact);

        // ===== TAG FILTER SECTION =====
        currentY = renderTagSection(context, mouseX, mouseY, currentY, isCompact);

        // ===== VENDOR SECTION =====
        currentY = renderVendorSection(context, mouseX, mouseY, currentY, isCompact);

        context.disableScissor();

        // Update scrollbar
        int visibleHeight = height - 70;
        scrollBar.setScrollData(contentHeight, visibleHeight);
        scrollBar.render(context, mouseX, mouseY, delta);

        // Draw Apply and Reset buttons at bottom
        renderBottomButtons(context, mouseX, mouseY, delta);
    }

    private int renderSortSection(DrawContext context, int mouseX, int mouseY, int currentY, boolean isCompact) {
        // Section header
        context.drawTextWithShadow(client.textRenderer, "Sort By:", x + PADDING, currentY, LABEL_COLOR);
        currentY += 14;
        contentHeight += 14;

        // Sort options as clickable items
        int btnWidth = isCompact ? (width - PADDING * 2 - 10) : (width - PADDING * 2 - 10) / 2;
        int btnHeight = 18;
        int col = 0;

        for (int i = 0; i < sortOptions.length; i++) {
            int btnX = x + PADDING + (col * (btnWidth + 4));
            int btnY = currentY;

            boolean isSelected = sortOptions[i].equals(selectedSort);
            boolean isHovered = mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight;

            int bgColor = isSelected ? 0xFF4A7A4A : (isHovered ? 0xFF4A4A4A : SECTION_BG_COLOR);
            context.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, bgColor);

            // Border
            context.fill(btnX, btnY, btnX + btnWidth, btnY + 1, 0xFF555555);
            context.fill(btnX, btnY + btnHeight - 1, btnX + btnWidth, btnY + btnHeight, 0xFF555555);
            context.fill(btnX, btnY, btnX + 1, btnY + btnHeight, 0xFF555555);
            context.fill(btnX + btnWidth - 1, btnY, btnX + btnWidth, btnY + btnHeight, 0xFF555555);

            String label = isCompact ? sortOptions[i].substring(0, Math.min(3, sortOptions[i].length())).toUpperCase() : sortLabels[i];
            int textWidth = client.textRenderer.getWidth(label);
            context.drawTextWithShadow(client.textRenderer, label, btnX + (btnWidth - textWidth) / 2, btnY + 5, TITLE_COLOR);

            col++;
            if (col >= (isCompact ? 1 : 2)) {
                col = 0;
                currentY += btnHeight + 2;
                contentHeight += btnHeight + 2;
            }
        }
        if (col != 0) {
            currentY += btnHeight + 2;
            contentHeight += btnHeight + 2;
        }

        currentY += 8;
        contentHeight += 8;
        return currentY;
    }

    private int renderPaginationSection(DrawContext context, int mouseX, int mouseY, int currentY, boolean isCompact) {
        // Section header
        context.drawTextWithShadow(client.textRenderer, "Items per page:", x + PADDING, currentY, LABEL_COLOR);
        currentY += 14;
        contentHeight += 14;

        // Page options
        int btnWidth = isCompact ? (width - PADDING * 2 - 10) / 2 : (width - PADDING * 2 - 10) / 4;
        int btnHeight = 18;

        for (int i = 0; i < pageOptions.length; i++) {
            int btnX = x + PADDING + (i % (isCompact ? 2 : 4)) * (btnWidth + 2);
            int btnY = currentY + (i / (isCompact ? 2 : 4)) * (btnHeight + 2);

            boolean isSelected = pageOptions[i] == itemsPerPage;
            boolean isHovered = mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight;

            int bgColor = isSelected ? 0xFF4A7A4A : (isHovered ? 0xFF4A4A4A : SECTION_BG_COLOR);
            context.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, bgColor);

            String label = String.valueOf(pageOptions[i]);
            int textWidth = client.textRenderer.getWidth(label);
            context.drawTextWithShadow(client.textRenderer, label, btnX + (btnWidth - textWidth) / 2, btnY + 5, TITLE_COLOR);
        }

        int rows = isCompact ? 2 : 1;
        currentY += rows * (btnHeight + 2) + 8;
        contentHeight += rows * (btnHeight + 2) + 8;
        return currentY;
    }

    private int renderTagSection(DrawContext context, int mouseX, int mouseY, int currentY, boolean isCompact) {
        // Section header
        context.drawTextWithShadow(client.textRenderer, "Tag Filter:", x + PADDING, currentY, LABEL_COLOR);
        currentY += 14;
        contentHeight += 14;

        // Update text field position and render
        if (tagTextField != null) {
            tagTextField.setPosition(x + PADDING, currentY);
            tagTextField.setWidth(width - PADDING * 2 - 12);
            tagTextField.render(context, mouseX, mouseY, 0);
        }

        currentY += 22;
        contentHeight += 22;
        currentY += 8;
        contentHeight += 8;
        return currentY;
    }

    private int renderVendorSection(DrawContext context, int mouseX, int mouseY, int currentY, boolean isCompact) {
        // Section header
        context.drawTextWithShadow(client.textRenderer, "Vendors:", x + PADDING, currentY, LABEL_COLOR);
        currentY += 14;
        contentHeight += 14;

        if (isLoadingVendors) {
            context.drawTextWithShadow(client.textRenderer, "Loading...", x + PADDING, currentY, LABEL_COLOR);
            currentY += 14;
            contentHeight += 14;
        } else if (availableVendors.length == 0) {
            context.drawTextWithShadow(client.textRenderer, "No vendors", x + PADDING, currentY, LABEL_COLOR);
            currentY += 14;
            contentHeight += 14;
        } else {
            // Render vendor toggles with labels
            for (int i = 0; i < availableVendors.length && i < vendorToggles.size(); i++) {
                String vendor = availableVendors[i];
                ToggleButton toggle = vendorToggles.get(i);

                // Update toggle position
                toggle.setX(x + PADDING);
                toggle.setY(currentY);

                // Render toggle
                toggle.render(context, mouseX, mouseY, 0);

                // Render vendor name next to toggle
                context.drawTextWithShadow(client.textRenderer, vendor, x + PADDING + 45, currentY + 6, TITLE_COLOR);

                currentY += 24;
                contentHeight += 24;
            }
        }

        currentY += 8;
        contentHeight += 8;
        return currentY;
    }

    private void renderBottomButtons(DrawContext context, int mouseX, int mouseY, float delta) {
        // Update button positions in case of resize
        int buttonY = y + height - 30;
        int buttonWidth = (width - PADDING * 3) / 2;

        if (applyButton != null) {
            applyButton.setX(x + PADDING);
            applyButton.setY(buttonY);
            applyButton.setWidth(buttonWidth);
            applyButton.render(context, mouseX, mouseY, delta);
        }

        if (resetButton != null) {
            resetButton.setX(x + PADDING * 2 + buttonWidth);
            resetButton.setY(buttonY);
            resetButton.setWidth(buttonWidth);
            resetButton.render(context, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Check if click is within panel bounds
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            // Unfocus text field if clicking outside
            if (tagTextField != null) {
                tagTextField.setFocused(false);
            }
            return false;
        }

        // Check Apply button
        if (applyButton != null && isOverButton(applyButton, mouseX, mouseY)) {
            tagFilter = tagTextField != null ? tagTextField.getText() : "";
            saveSettings();
            notifySettingsChanged();
            return true;
        }

        // Check Reset button
        if (resetButton != null && isOverButton(resetButton, mouseX, mouseY)) {
            resetSettings();
            return true;
        }

        // Check vendor toggles
        for (int i = 0; i < vendorToggles.size(); i++) {
            ToggleButton toggle = vendorToggles.get(i);
            if (mouseX >= toggle.getX() && mouseX < toggle.getX() + toggle.getWidth()
                    && mouseY >= toggle.getY() && mouseY < toggle.getY() + toggle.getHeight()) {
                // Toggle the state
                String vendor = availableVendors[i];
                if (excludedVendors.contains(vendor)) {
                    excludedVendors.remove(vendor);
                    toggle.setToggled(true);
                } else {
                    excludedVendors.add(vendor);
                    toggle.setToggled(false);
                }
                return true;
            }
        }

        // Check scrollbar
        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            if (tagTextField != null) tagTextField.setFocused(false);
            return true;
        }

        // Check if clicked on tag text field area (SimpleTextField handles its own click via GLFW)
        if (tagTextField != null && tagTextField.isMouseOver(mouseX, mouseY)) {
            tagTextField.setFocused(true);
            return true;
        } else if (tagTextField != null) {
            tagTextField.setFocused(false);
        }

        // Check sort options
        int currentY = y + 30 + 14 - (int) scrollOffset;
        boolean isCompact = width < 180;
        int btnWidth = isCompact ? (width - PADDING * 2 - 10) : (width - PADDING * 2 - 10) / 2;
        int btnHeight = 18;

        for (int i = 0; i < sortOptions.length; i++) {
            int col = isCompact ? 0 : (i % 2);
            int row = isCompact ? i : (i / 2);
            int btnX = x + PADDING + (col * (btnWidth + 4));
            int btnY = currentY + row * (btnHeight + 2);

            if (mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight) {
                selectedSort = sortOptions[i];
                return true;
            }
        }

        // Calculate currentY after sort section
        int sortRows = isCompact ? sortOptions.length : (int) Math.ceil(sortOptions.length / 2.0);
        currentY += sortRows * (btnHeight + 2) + 8 + 14;

        // Check pagination options
        int pageBtnWidth = isCompact ? (width - PADDING * 2 - 10) / 2 : (width - PADDING * 2 - 10) / 4;
        for (int i = 0; i < pageOptions.length; i++) {
            int col = i % (isCompact ? 2 : 4);
            int row = i / (isCompact ? 2 : 4);
            int btnX = x + PADDING + col * (pageBtnWidth + 2);
            int btnY = currentY + row * (btnHeight + 2);

            if (mouseX >= btnX && mouseX < btnX + pageBtnWidth && mouseY >= btnY && mouseY < btnY + btnHeight) {
                itemsPerPage = pageOptions[i];
                return true;
            }
        }

        return false;
    }

    private boolean isOverButton(CustomButton button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - verticalAmount * 10, Math.max(0, contentHeight - (height - 70))));
            scrollBar.setScrollPercentage((float) (scrollOffset / Math.max(1, contentHeight - (height - 70))));
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            scrollOffset = scrollBar.getScrollPercentage() * Math.max(0, contentHeight - (height - 70));
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollBar.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    private void resetSettings() {
        selectedSort = "newest";
        itemsPerPage = 20;
        excludedVendors.clear();
        tagFilter = "";
        versionFilter = "all";
        if (tagTextField != null) {
            tagTextField.setText("");
        }
        // Reset all vendor toggles to ON (included)
        for (ToggleButton toggle : vendorToggles) {
            toggle.setToggled(true);
        }
        scrollOffset = 0;
        // Save reset settings
        saveSettings();
    }

    @Override
    public void setFocused(boolean focused) {
        if (tagTextField != null && !focused) {
            tagTextField.setFocused(false);
        }
    }

    @Override
    public boolean isFocused() {
        return tagTextField != null && tagTextField.isFocused();
    }
}

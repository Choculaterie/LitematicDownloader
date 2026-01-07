package com.choculaterie.gui.widget;

import com.choculaterie.gui.theme.UITheme;
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

public class SortFilterPanel implements Drawable, Element {

    private int x;
    private int y;
    private int width;
    private int height;

    private final MinecraftClient client;
    private double scrollOffset = 0;
    private int contentHeight = 0;
    private ScrollBar scrollBar;
    private String selectedSort = "popular";
    private final String[] sortOptions = {"newest", "popular", "oldest", "downloads"};
    private final String[] sortLabels = {"Newest", "Popular", "Oldest", "Downloads"};
    private int itemsPerPage = 10;
    private final int[] pageOptions = {5, 10, 20, 50};
    private String[] availableVendors = new String[0];
    private Set<String> excludedVendors = new HashSet<>();
    private boolean isLoadingVendors = false;
    private List<ToggleButton> vendorToggles = new ArrayList<>();
    private String versionFilter = "all";
    private String tagFilter = "";
    private Consumer<SortFilterPanel> onSettingsChanged;
    private CustomTextField tagTextField;
    private CustomButton applyButton;
    private CustomButton resetButton;

    public SortFilterPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = MinecraftClient.getInstance();
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.PADDING, y + 30, height - 60);
        loadSettings();
        this.tagTextField = new CustomTextField(client, x + UITheme.Dimensions.PADDING, y + 100, width - UITheme.Dimensions.PADDING * 2 - 10, 18, Text.empty());
        this.tagTextField.setPlaceholder(Text.of("Enter tag..."));
        this.tagTextField.setText(tagFilter);
        initButtons();
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
        int buttonWidth = (width - UITheme.Dimensions.PADDING * 3) / 2;

        applyButton = new CustomButton(
                x + UITheme.Dimensions.PADDING,
                y + height - UITheme.Dimensions.BUTTON_HEIGHT - UITheme.Dimensions.PADDING,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of(width < 150 ? "✓" : "Apply"),
                button -> applySettings()
        );

        resetButton = new CustomButton(
                x + UITheme.Dimensions.PADDING + buttonWidth + UITheme.Dimensions.PADDING,
                y + height - UITheme.Dimensions.BUTTON_HEIGHT - UITheme.Dimensions.PADDING,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of(width < 150 ? "↺" : "Reset"),
                button -> resetSettings()
        );
    }

    private void applySettings() {
        if (tagTextField != null) {
            tagFilter = tagTextField.getText();
        }
        saveSettings();
        notifySettingsChanged();
    }

    public void setOnSettingsChanged(Consumer<SortFilterPanel> callback) {
        this.onSettingsChanged = callback;
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.PADDING, y + 30, height - 60);
        if (tagTextField != null) {
            tagTextField.setPosition(x + UITheme.Dimensions.PADDING, y + 100);
            tagTextField.setWidth(width - UITheme.Dimensions.PADDING * 2 - 10);
        }
        initButtons();
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
                    x + UITheme.Dimensions.PADDING,
                    0,
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

    private void drawButtonBorder(DrawContext context, int x, int y, int width, int height) {
        int borderWidth = UITheme.Dimensions.BORDER_WIDTH;
        int borderColor = UITheme.Colors.BUTTON_BORDER;
        context.fill(x, y, x + width, y + borderWidth, borderColor);
        context.fill(x, y + height - borderWidth, x + width, y + height, borderColor);
        context.fill(x, y, x + borderWidth, y + height, borderColor);
        context.fill(x + width - borderWidth, y, x + width, y + height, borderColor);
    }

    private void drawCenteredButtonText(DrawContext context, String text, int x, int y, int width, int height) {
        int textWidth = client.textRenderer.getWidth(text);
        context.drawTextWithShadow(client.textRenderer, text, x + (width - textWidth) / 2, y + 5, UITheme.Colors.TEXT_PRIMARY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(x, y, x + width, y + height, UITheme.Colors.PANEL_BG_SECONDARY);
        context.fill(x, y, x + 1, y + height, UITheme.Colors.BUTTON_BORDER);
        boolean isCompact = width < 180;
        String title = isCompact ? "Filters" : "Sort & Filter";
        context.drawTextWithShadow(client.textRenderer, title, x + UITheme.Dimensions.PADDING, y + UITheme.Dimensions.PADDING, UITheme.Colors.TEXT_PRIMARY);
        int contentStartY = y + 30;
        context.enableScissor(x + 1, contentStartY, x + width - UITheme.Dimensions.SCROLLBAR_WIDTH, y + height - 40);

        int currentY = contentStartY - (int) scrollOffset;
        contentHeight = 0;
        currentY = renderSortSection(context, mouseX, mouseY, currentY, isCompact);
        currentY = renderPaginationSection(context, mouseX, mouseY, currentY, isCompact);
        currentY = renderTagSection(context, mouseX, mouseY, currentY, isCompact);
        currentY = renderVendorSection(context, mouseX, mouseY, currentY, isCompact);

        context.disableScissor();
        int visibleHeight = height - 70;
        scrollBar.setScrollData(contentHeight, visibleHeight);
        scrollBar.render(context, mouseX, mouseY, delta);
        renderBottomButtons(context, mouseX, mouseY, delta);
    }

    private int renderSortSection(DrawContext context, int mouseX, int mouseY, int currentY, boolean isCompact) {
        context.drawTextWithShadow(client.textRenderer, "Sort By:", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
        currentY += 14;
        contentHeight += 14;
        int btnWidth = isCompact ? (width - UITheme.Dimensions.PADDING * 2 - 10) : (width - UITheme.Dimensions.PADDING * 2 - 10) / 2;
        int btnHeight = 18;
        int col = 0;

        for (int i = 0; i < sortOptions.length; i++) {
            int btnX = x + UITheme.Dimensions.PADDING + (col * (btnWidth + 4));
            int btnY = currentY;

            boolean isSelected = sortOptions[i].equals(selectedSort);
            boolean isHovered = mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight;

            int bgColor = isSelected ? UITheme.Colors.TOGGLE_ON : (isHovered ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.BUTTON_BG);
            context.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, bgColor);
            drawButtonBorder(context, btnX, btnY, btnWidth, btnHeight);

            String label = isCompact ? sortOptions[i].substring(0, Math.min(3, sortOptions[i].length())).toUpperCase() : sortLabels[i];
            drawCenteredButtonText(context, label, btnX, btnY, btnWidth, btnHeight);

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
        context.drawTextWithShadow(client.textRenderer, "Items per page:", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
        currentY += 14;
        contentHeight += 14;
        int btnWidth = isCompact ? (width - UITheme.Dimensions.PADDING * 2 - 10) / 2 : (width - UITheme.Dimensions.PADDING * 2 - 10) / 4;
        int btnHeight = 18;

        for (int i = 0; i < pageOptions.length; i++) {
            int btnX = x + UITheme.Dimensions.PADDING + (i % (isCompact ? 2 : 4)) * (btnWidth + 2);
            int btnY = currentY + (i / (isCompact ? 2 : 4)) * (btnHeight + 2);

            boolean isSelected = pageOptions[i] == itemsPerPage;
            boolean isHovered = mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight;

            int bgColor = isSelected ? UITheme.Colors.TOGGLE_ON : (isHovered ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.BUTTON_BG);
            context.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, bgColor);
            drawButtonBorder(context, btnX, btnY, btnWidth, btnHeight);

            String label = String.valueOf(pageOptions[i]);
            drawCenteredButtonText(context, label, btnX, btnY, btnWidth, btnHeight);
        }

        int rows = isCompact ? 2 : 1;
        currentY += rows * (btnHeight + 2) + 8;
        contentHeight += rows * (btnHeight + 2) + 8;
        return currentY;
    }

    private int renderTagSection(DrawContext context, int mouseX, int mouseY, int currentY, boolean isCompact) {
        context.drawTextWithShadow(client.textRenderer, "Tag Filter:", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
        currentY += 14;
        contentHeight += 14;
        if (tagTextField != null) {
            tagTextField.setPosition(x + UITheme.Dimensions.PADDING, currentY);
            tagTextField.setWidth(width - UITheme.Dimensions.PADDING * 2 - UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.PADDING);
            tagTextField.render(context, mouseX, mouseY, 0);
        }

        currentY += 22;
        contentHeight += 22;
        currentY += 8;
        contentHeight += 8;
        return currentY;
    }

    private int renderVendorSection(DrawContext context, int mouseX, int mouseY, int currentY, boolean isCompact) {
        context.drawTextWithShadow(client.textRenderer, "Vendors:", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
        currentY += 14;
        contentHeight += 14;

        if (isLoadingVendors) {
            context.drawTextWithShadow(client.textRenderer, "Loading...", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
            currentY += 14;
            contentHeight += 14;
        } else if (availableVendors.length == 0) {
            context.drawTextWithShadow(client.textRenderer, "No vendors", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
            currentY += 14;
            contentHeight += 14;
        } else {
            for (int i = 0; i < availableVendors.length && i < vendorToggles.size(); i++) {
                String vendor = availableVendors[i];
                ToggleButton toggle = vendorToggles.get(i);
                toggle.setX(x + UITheme.Dimensions.PADDING);
                toggle.setY(currentY);
                toggle.render(context, mouseX, mouseY, 0);
                context.drawTextWithShadow(client.textRenderer, vendor, x + UITheme.Dimensions.PADDING + 45, currentY + 6, UITheme.Colors.TEXT_PRIMARY);

                currentY += 24;
                contentHeight += 24;
            }
        }

        currentY += 8;
        contentHeight += 8;
        return currentY;
    }

    private void renderBottomButtons(DrawContext context, int mouseX, int mouseY, float delta) {
        int buttonY = y + height - 30;
        int buttonWidth = (width - UITheme.Dimensions.PADDING * 3) / 2;

        if (applyButton != null) {
            applyButton.setX(x + UITheme.Dimensions.PADDING);
            applyButton.setY(buttonY);
            applyButton.setWidth(buttonWidth);
            applyButton.render(context, mouseX, mouseY, delta);
        }

        if (resetButton != null) {
            resetButton.setX(x + UITheme.Dimensions.PADDING * 2 + buttonWidth);
            resetButton.setY(buttonY);
            resetButton.setWidth(buttonWidth);
            resetButton.render(context, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            if (tagTextField != null) {
                tagTextField.setFocused(false);
            }
            return false;
        }
        if (applyButton != null && isOverButton(applyButton, mouseX, mouseY)) {
            tagFilter = tagTextField != null ? tagTextField.getText() : "";
            saveSettings();
            notifySettingsChanged();
            return true;
        }
        if (resetButton != null && isOverButton(resetButton, mouseX, mouseY)) {
            resetSettings();
            return true;
        }
        for (int i = 0; i < vendorToggles.size(); i++) {
            ToggleButton toggle = vendorToggles.get(i);
            if (mouseX >= toggle.getX() && mouseX < toggle.getX() + toggle.getWidth()
                    && mouseY >= toggle.getY() && mouseY < toggle.getY() + toggle.getHeight()) {
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
        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            if (tagTextField != null) tagTextField.setFocused(false);
            return true;
        }
        if (tagTextField != null && tagTextField.isMouseOver(mouseX, mouseY)) {
            tagTextField.setFocused(true);
            return true;
        } else if (tagTextField != null) {
            tagTextField.setFocused(false);
        }
        int currentY = y + 30 + 14 - (int) scrollOffset;
        boolean isCompact = width < 180;
        int btnWidth = isCompact ? (width - UITheme.Dimensions.PADDING * 2 - 10) : (width - UITheme.Dimensions.PADDING * 2 - 10) / 2;
        int btnHeight = 18;

        for (int i = 0; i < sortOptions.length; i++) {
            int col = isCompact ? 0 : (i % 2);
            int row = isCompact ? i : (i / 2);
            int btnX = x + UITheme.Dimensions.PADDING + (col * (btnWidth + 4));
            int btnY = currentY + row * (btnHeight + 2);

            if (mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight) {
                selectedSort = sortOptions[i];
                return true;
            }
        }
        int sortRows = isCompact ? sortOptions.length : (int) Math.ceil(sortOptions.length / 2.0);
        currentY += sortRows * (btnHeight + 2) + 8 + 14;
        int pageBtnWidth = isCompact ? (width - UITheme.Dimensions.PADDING * 2 - 10) / 2 : (width - UITheme.Dimensions.PADDING * 2 - 10) / 4;
        for (int i = 0; i < pageOptions.length; i++) {
            int col = i % (isCompact ? 2 : 4);
            int row = i / (isCompact ? 2 : 4);
            int btnX = x + UITheme.Dimensions.PADDING + col * (pageBtnWidth + 2);
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
        for (ToggleButton toggle : vendorToggles) {
            toggle.setToggled(true);
        }
        scrollOffset = 0;
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

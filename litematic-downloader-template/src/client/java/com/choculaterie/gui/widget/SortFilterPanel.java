package com.choculaterie.gui.widget;

import com.choculaterie.vanilib.gui.theme.ButtonStyle;
import com.choculaterie.vanilib.gui.theme.UIDraw;
import com.choculaterie.vanilib.gui.theme.UIIcon;
import com.choculaterie.vanilib.gui.widget.CustomButton;
import com.choculaterie.vanilib.gui.widget.CustomTextField;
import com.choculaterie.vanilib.gui.widget.ScrollBar;
import com.choculaterie.vanilib.gui.widget.ToggleButton;

import com.choculaterie.vanilib.gui.theme.UITheme;
import com.choculaterie.config.DownloadSettings;
import com.choculaterie.network.MinemevNetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import com.mojang.blaze3d.platform.InputConstants;

public class SortFilterPanel implements Renderable, GuiEventListener {
    private static final int PAD = UITheme.Dimensions.PADDING;
    private static final int CONTROL_H = UITheme.Dimensions.BUTTON_HEIGHT;
    private static final int GAP = 4;
    private static final int HEADER_H = 27;
    private static final int LABEL_GAP = 12;
    private static final int SECTION_GAP = 10;
    private static final int TOGGLE_ROW_H = 23;
    private static final int LINK_H = 15;

    private int x;
    private int y;
    private int width;
    private int height;

    private final Minecraft client;
    private double scrollOffset = 0;
    private int contentHeight = 0;
    private ScrollBar scrollBar;
    private String selectedSort = "popular";
    private final String[] sortOptions = {"newest", "popular", "oldest", "downloads"};
    private final String[] sortLabels = {"Newest", "Popular", "Oldest", "Downloads"};
    private int itemsPerPage = 10;
    private int[] pageOptions = computePageOptions(4);
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
    private CustomButton addSourceButton;

    private final List<int[]> sortChipRects = new ArrayList<>();
    private final List<int[]> pageChipRects = new ArrayList<>();

    public SortFilterPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = Minecraft.getInstance();
        loadSettings();
        this.tagTextField = new CustomTextField(client, innerX(), contentTop(), innerWidth(), CONTROL_H, Component.empty());
        this.tagTextField.setPlaceholder(Component.literal("Enter tag..."));
        this.tagTextField.setValue(tagFilter);
        layoutChrome();
        loadVendors();
    }

    private int boxBottom() {
        return y + height - CONTROL_H - PAD;
    }

    private int contentTop() {
        return y + HEADER_H;
    }

    private int contentBottom() {
        return boxBottom() - GAP;
    }

    private int visibleHeight() {
        return Math.max(0, contentBottom() - contentTop());
    }

    private int scrollBarX() {
        return x + width - UITheme.Dimensions.SCROLLBAR_WIDTH - 3;
    }

    private int innerX() {
        return x + PAD;
    }

    private int innerWidth() {
        return Math.max(20, scrollBarX() - GAP - innerX());
    }

    private boolean isCompact() {
        return width < 180;
    }

    private void layoutChrome() {
        this.scrollBar = new ScrollBar(scrollBarX(), contentTop(), visibleHeight());
        if (tagTextField != null) {
            tagTextField.setWidth(innerWidth());
        }

        boolean iconOnly = width < 150;
        int footerY = y + height - CONTROL_H;
        int buttonWidth = (width - PAD) / 2;

        applyButton = new CustomButton(x, footerY, buttonWidth, CONTROL_H,
                Component.literal(iconOnly ? "" : "Apply"), button -> applySettings());
        applyButton.setIcon(UIIcon.CHECK).setStyle(ButtonStyle.PRIMARY);

        resetButton = new CustomButton(x + width - buttonWidth, footerY, buttonWidth, CONTROL_H,
                Component.literal(iconOnly ? "" : "Refresh"), button -> resetSettings());
        resetButton.setIcon(UIIcon.REFRESH);

        String linkLabel = "Add plugins";
        int linkWidth = UIDraw.font().width(linkLabel) + UIIcon.SIZE + 4 + 8;
        addSourceButton = new CustomButton(0, 0, linkWidth, LINK_H, Component.literal(linkLabel), btn -> openPluginsPage());
        addSourceButton.setIcon(UIIcon.PLUS).setStyle(ButtonStyle.GHOST);
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

    private void applySettings() {
        if (tagTextField != null) {
            tagFilter = tagTextField.getValue();
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
        layoutChrome();
        createVendorToggles();
    }

    public void refreshVendors() {
        loadVendors();
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

    private static int[] computePageOptions(int vendorCount) {
        int base = vendorCount * 2;
        int cap = Math.max(base, 50 / vendorCount * vendorCount);
        return new int[]{
                base,
                Math.min(base * 2, cap),
                Math.min(base * 4, cap),
                cap
        };
    }

    private void updatePageOptions() {
        int activeVendors = Math.max(1, availableVendors.length - excludedVendors.size());
        pageOptions = computePageOptions(activeVendors);
        int closest = pageOptions[0];
        for (int opt : pageOptions) {
            if (Math.abs(opt - itemsPerPage) < Math.abs(closest - itemsPerPage)) {
                closest = opt;
            }
        }
        itemsPerPage = closest;
    }

    private void createVendorToggles() {
        updatePageOptions();
        vendorToggles.clear();
        for (int i = 0; i < availableVendors.length; i++) {
            final String vendor = availableVendors[i];
            boolean isIncluded = !excludedVendors.contains(vendor);
            ToggleButton toggle = new ToggleButton(
                    innerX(),
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int boxBottom = boxBottom();
        UIDraw.sidePanel(context, x, y, x + width, boxBottom);
        String title = isCompact() ? "Filters" : "Sort & Filter";
        UIDraw.sectionHeader(context, title, innerX(), y + 8, x + width - PAD);

        boolean inside = mouseY >= contentTop() && mouseY < contentBottom();
        int contentMouseX = inside ? mouseX : -1;
        int contentMouseY = inside ? mouseY : -1;

        context.enableScissor(x + 1, contentTop(), scrollBarX() - 1, contentBottom());
        int currentY = contentTop() - (int) scrollOffset;
        int startY = currentY;
        currentY = renderSortSection(context, contentMouseX, contentMouseY, currentY);
        currentY = renderPaginationSection(context, contentMouseX, contentMouseY, currentY);
        currentY = renderTagSection(context, contentMouseX, contentMouseY, currentY);
        currentY = renderVendorSection(context, contentMouseX, contentMouseY, currentY);
        context.disableScissor();
        contentHeight = currentY - startY;

        double maxScroll = Math.max(0, contentHeight - visibleHeight());
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        scrollBar.setScrollData(contentHeight, visibleHeight());
        scrollBar.setScrollPercentage(maxScroll > 0 ? scrollOffset / maxScroll : 0);
        scrollBar.extractRenderState(context, mouseX, mouseY, delta);

        if (scrollOffset > 0) {
            UIDraw.fadeTop(context, x + 1, contentTop(), scrollBarX() - 1, UITheme.Dimensions.FADE_HEIGHT, UITheme.Colors.PANEL_BG);
        }
        if (scrollOffset < maxScroll) {
            UIDraw.fadeBottom(context, x + 1, contentBottom(), scrollBarX() - 1, UITheme.Dimensions.FADE_HEIGHT, UITheme.Colors.PANEL_BG);
        }

        applyButton.extractRenderState(context, mouseX, mouseY, delta);
        resetButton.extractRenderState(context, mouseX, mouseY, delta);
    }

    private int renderLabel(GuiGraphicsExtractor context, String label, int currentY) {
        UIDraw.text(context, label, innerX(), currentY, UITheme.Colors.TEXT_SUBTITLE);
        return currentY + LABEL_GAP;
    }

    private int renderChipGrid(GuiGraphicsExtractor context, int mouseX, int mouseY, int currentY,
                               String[] labels, int selectedIndex, int columns, List<int[]> rects) {
        rects.clear();
        int chipWidth = (innerWidth() - GAP * (columns - 1)) / columns;
        int rows = (labels.length + columns - 1) / columns;
        for (int i = 0; i < labels.length; i++) {
            int col = i % columns;
            int row = i / columns;
            int chipX = innerX() + col * (chipWidth + GAP);
            int chipY = currentY + row * (CONTROL_H + GAP);
            boolean hovered = UIDraw.isInside(mouseX, mouseY, chipX, chipY, chipWidth, CONTROL_H);
            UIDraw.chip(context, chipX, chipY, chipWidth, CONTROL_H, labels[i], i == selectedIndex, hovered);
            rects.add(new int[]{chipX, chipY, chipWidth, CONTROL_H});
        }
        return currentY + rows * CONTROL_H + (rows - 1) * GAP + SECTION_GAP;
    }

    private int renderSortSection(GuiGraphicsExtractor context, int mouseX, int mouseY, int currentY) {
        currentY = renderLabel(context, "Sort by", currentY);
        int selected = Arrays.asList(sortOptions).indexOf(selectedSort);
        return renderChipGrid(context, mouseX, mouseY, currentY, sortLabels, selected, isCompact() ? 1 : 2, sortChipRects);
    }

    private int renderPaginationSection(GuiGraphicsExtractor context, int mouseX, int mouseY, int currentY) {
        currentY = renderLabel(context, "Items per page", currentY);
        String[] labels = new String[pageOptions.length];
        int selected = -1;
        for (int i = 0; i < pageOptions.length; i++) {
            labels[i] = String.valueOf(pageOptions[i]);
            if (pageOptions[i] == itemsPerPage) selected = i;
        }
        return renderChipGrid(context, mouseX, mouseY, currentY, labels, selected, isCompact() ? 2 : 4, pageChipRects);
    }

    private int renderTagSection(GuiGraphicsExtractor context, int mouseX, int mouseY, int currentY) {
        currentY = renderLabel(context, "Tag filter", currentY);
        if (tagTextField != null) {
            tagTextField.setPosition(innerX(), currentY);
            tagTextField.setWidth(innerWidth());
            tagTextField.extractRenderState(context, mouseX, mouseY, 0);
        }
        return currentY + CONTROL_H + SECTION_GAP;
    }

    private int renderVendorSection(GuiGraphicsExtractor context, int mouseX, int mouseY, int currentY) {
        UIDraw.text(context, "Sources", innerX(), currentY, UITheme.Colors.TEXT_SUBTITLE);
        addSourceButton.setX(innerX() + innerWidth() - addSourceButton.getWidth());
        addSourceButton.setY(currentY - (LINK_H - UITheme.Typography.CAP_HEIGHT) / 2);
        addSourceButton.extractRenderState(context, mouseX, mouseY, 0);
        currentY += LABEL_GAP + 2;

        if (isLoadingVendors || availableVendors.length == 0) {
            UIDraw.text(context, isLoadingVendors ? "Loading..." : "No sources", innerX(), currentY, UITheme.Colors.TEXT_MUTED);
            return currentY + LABEL_GAP + SECTION_GAP;
        }

        for (int i = 0; i < availableVendors.length && i < vendorToggles.size(); i++) {
            ToggleButton toggle = vendorToggles.get(i);
            toggle.setX(innerX() - 7);
            toggle.setY(currentY);
            toggle.extractRenderState(context, mouseX, mouseY, 0);
            int labelX = innerX() - 7 + toggle.getWidth() + 2;
            UIDraw.text(context, UIDraw.ellipsize(availableVendors[i], innerX() + innerWidth() - labelX),
                    labelX, UIDraw.flatContentY(currentY, toggle.getHeight()), UITheme.Colors.TEXT_PRIMARY);
            currentY += TOGGLE_ROW_H;
        }
        return currentY + SECTION_GAP;
    }

    private void openPluginsPage() {
        if (client != null && client.gui != null && client.gui.screen() != null) {
            client.gui.setScreen(new com.choculaterie.gui.PluginsPage(client.gui.screen()));
        }
    }

    private static boolean hit(int[] r, double mouseX, double mouseY) {
        return UIDraw.isInside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            if (tagTextField != null) {
                tagTextField.setFocused(false);
            }
            return false;
        }
        if (isOverButton(applyButton, mouseX, mouseY)) {
            UIDraw.playClick();
            applySettings();
            return true;
        }
        if (isOverButton(resetButton, mouseX, mouseY)) {
            UIDraw.playClick();
            resetSettings();
            return true;
        }
        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            if (tagTextField != null) tagTextField.setFocused(false);
            return true;
        }

        boolean inContent = mouseY >= contentTop() && mouseY < contentBottom();
        if (!inContent) {
            return true;
        }
        if (isOverButton(addSourceButton, mouseX, mouseY)) {
            UIDraw.playClick();
            openPluginsPage();
            return true;
        }
        for (int i = 0; i < vendorToggles.size(); i++) {
            ToggleButton toggle = vendorToggles.get(i);
            if (UIDraw.isInside(mouseX, mouseY, toggle.getX(), toggle.getY(), toggle.getWidth(), toggle.getHeight())) {
                UIDraw.playClick();
                String vendor = availableVendors[i];
                if (excludedVendors.contains(vendor)) {
                    excludedVendors.remove(vendor);
                    toggle.setToggled(true);
                } else {
                    excludedVendors.add(vendor);
                    toggle.setToggled(false);
                }
                updatePageOptions();
                saveSettings();
                notifySettingsChanged();
                return true;
            }
        }
        if (tagTextField != null && tagTextField.isMouseOver(mouseX, mouseY)) {
            tagTextField.setFocused(true);
            return true;
        } else if (tagTextField != null) {
            tagTextField.setFocused(false);
        }
        for (int i = 0; i < sortChipRects.size(); i++) {
            if (hit(sortChipRects.get(i), mouseX, mouseY)) {
                UIDraw.playClick();
                selectedSort = sortOptions[i];
                saveSettings();
                notifySettingsChanged();
                return true;
            }
        }
        for (int i = 0; i < pageChipRects.size() && i < pageOptions.length; i++) {
            if (hit(pageChipRects.get(i), mouseX, mouseY)) {
                UIDraw.playClick();
                itemsPerPage = pageOptions[i];
                saveSettings();
                notifySettingsChanged();
                return true;
            }
        }
        return true;
    }

    private boolean isOverButton(CustomButton button, double mouseX, double mouseY) {
        return button != null && UIDraw.isInside(mouseX, mouseY, button.getX(), button.getY(), button.getWidth(), button.getHeight());
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < boxBottom()) {
            double maxScroll = Math.max(0, contentHeight - visibleHeight());
            scrollOffset = Math.max(0, Math.min(scrollOffset - verticalAmount * 10, maxScroll));
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            scrollOffset = scrollBar.getScrollPercentage() * Math.max(0, contentHeight - visibleHeight());
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
            tagTextField.setValue("");
        }
        for (ToggleButton toggle : vendorToggles) {
            toggle.setToggled(true);
        }
        updatePageOptions();
        scrollOffset = 0;
        saveSettings();
        notifySettingsChanged();
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

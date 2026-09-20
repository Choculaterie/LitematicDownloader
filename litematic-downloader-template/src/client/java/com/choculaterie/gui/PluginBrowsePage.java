package com.choculaterie.gui;

import com.choculaterie.gui.widget.ToastManager;
import com.choculaterie.plugin.PluginCatalog;
import com.choculaterie.plugin.PluginRegistry;
import com.choculaterie.vanilib.gui.theme.UITheme;
import com.choculaterie.vanilib.gui.widget.CustomButton;
import com.choculaterie.vanilib.gui.widget.CustomTextField;
import com.choculaterie.vanilib.gui.widget.LoadingSpinner;
import com.choculaterie.vanilib.gui.widget.ScrollBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PluginBrowsePage extends Screen {
    private static final int PADDING = 10;
    private static final int BUTTON_SIZE = 20;
    private static final int ITEM_HEIGHT = 30;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int ACTION_WIDTH = 70;

    private final Screen parentScreen;
    private final List<PluginCatalog.Entry> entries = new ArrayList<>();
    private final List<PluginCatalog.Entry> visible = new ArrayList<>();
    private CustomTextField searchField;
    private String filter = "";
    private final List<CustomButton> actionButtons = new ArrayList<>();
    private final List<PluginCatalog.Entry> actionTargets = new ArrayList<>();
    private ScrollBar scrollBar;
    private LoadingSpinner loadingSpinner;
    private ToastManager toastManager;
    private CustomButton refreshButton;
    private int barWidth = -1;
    private int barY = -1;
    private int barLength = -1;
    private int scrollOffset = 0;
    private String status = "";
    private boolean loading = true;
    private boolean requested = false;

    public PluginBrowsePage(Screen parentScreen) {
        super(Component.literal("Get Plugins"));
        this.parentScreen = parentScreen;
    }

    private int listY() {
        return PADDING * 4 + BUTTON_SIZE + 32;
    }

    private void applyFilter() {
        visible.clear();
        String needle = filter.toLowerCase().trim();
        for (PluginCatalog.Entry entry : entries) {
            if (needle.isEmpty()
                    || entry.name().toLowerCase().contains(needle)
                    || entry.id().toLowerCase().contains(needle)
                    || String.join(" ", entry.hosts()).toLowerCase().contains(needle)) {
                visible.add(entry);
            }
        }
    }

    private int listHeight() {
        return Math.max(ITEM_HEIGHT, this.height - listY() - PADDING);
    }

    private boolean scrollBarShowing() {
        return visible.size() * ITEM_HEIGHT > listHeight();
    }

    private int panelRight() {
        return this.width - PADDING - (scrollBarShowing() ? SCROLLBAR_WIDTH + 2 : 0);
    }

    private int listRightEdge() {
        return panelRight();
    }

    private int maxVisible() {
        return Math.max(1, listHeight() / ITEM_HEIGHT);
    }

    private int getMaxScroll() {
        return Math.max(0, visible.size() - maxVisible());
    }

    @Override
    protected void init() {
        super.init();

        this.addRenderableWidget(new CustomButton(
                PADDING, PADDING, BUTTON_SIZE, BUTTON_SIZE,
                Component.literal("←"), button -> goBack()));

        if (toastManager == null && this.minecraft != null) {
            toastManager = new ToastManager(this.minecraft);
        }

        refreshButton = new CustomButton(
                this.width - PADDING - BUTTON_SIZE, PADDING, BUTTON_SIZE, BUTTON_SIZE,
                Component.literal("🔄"), button -> load(true));
        refreshButton.active = !loading;
        this.addRenderableWidget(refreshButton);

        if (searchField == null && this.minecraft != null) {
            searchField = new CustomTextField(this.minecraft, PADDING, PADDING * 2 + BUTTON_SIZE,
                    this.width - PADDING * 2, 18, Component.literal("Search"));
            searchField.setPlaceholder(Component.literal("Search plugins..."));
            searchField.setValue(filter);
            searchField.setOnChanged(() -> {
                filter = searchField.getValue();
                applyFilter();
                scrollOffset = 0;
                updateScrollBar();
            });
        }
        if (searchField != null) {
            searchField.setPosition(PADDING, PADDING * 2 + BUTTON_SIZE);
            searchField.setWidth(this.width - PADDING * 2);
            this.addRenderableWidget(searchField);
        }

        int barHeight = listHeight();
        if (scrollBar == null || barWidth != this.width || barY != listY() || barLength != barHeight) {
            scrollBar = new ScrollBar(this.width - PADDING - SCROLLBAR_WIDTH, listY(), barHeight);
            barWidth = this.width;
            barY = listY();
            barLength = barHeight;
        }
        updateScrollBar();

        if (loadingSpinner == null) {
            loadingSpinner = new LoadingSpinner(0, 0);
        }
        loadingSpinner.setPosition((PADDING + panelRight()) / 2 - loadingSpinner.getWidth() / 2,
                listY() + listHeight() / 2 - loadingSpinner.getHeight() / 2);

        if (entries.isEmpty() && loading && !requested) {
            requested = true;
            load(false);
        }
    }

    private String actionLabel(PluginCatalog.Entry entry) {
        String installed = PluginCatalog.installedVersion(entry.id());
        if (installed == null) {
            return "Install";
        }
        return PluginCatalog.updateAvailable(entry) ? "Update" : "Installed";
    }

    private void load(boolean announce) {
        loading = true;
        status = "";
        if (refreshButton != null) {
            refreshButton.active = false;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return PluginCatalog.fetch();
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((list, error) -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                loading = false;
                if (refreshButton != null) {
                    refreshButton.active = true;
                }
                entries.clear();
                if (error != null) {
                    status = "Could not reach the plugin catalogue";
                    System.err.println("[Plugin] catalogue failed: " + error.getMessage());
                    if (announce && toastManager != null) {
                        toastManager.showError("Could not reach the plugin catalogue");
                    }
                } else {
                    entries.addAll(list);
                    status = entries.isEmpty() ? "No plugins published yet" : "";
                    if (announce && toastManager != null) {
                        toastManager.showSuccess(entries.size() == 1
                                ? "1 plugin in the catalogue"
                                : entries.size() + " plugins in the catalogue");
                    }
                }
                scrollOffset = 0;
                applyFilter();
                updateScrollBar();
            });
        });
    }

    private void install(PluginCatalog.Entry entry) {
        status = "Installing " + entry.name() + "...";
        CompletableFuture.runAsync(() -> {
            try {
                PluginCatalog.install(entry);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((ignored, error) -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                if (error != null) {
                    status = "Install failed: " + error.getMessage();
                    System.err.println("[Plugin] install failed: " + error.getMessage());
                } else {
                    status = entry.name() + " installed. Enable it in Plugins.";
                }
            });
        });
    }

    private void updateScrollBar() {
        if (scrollBar == null) {
            return;
        }
        scrollBar.setScrollData(visible.size() * ITEM_HEIGHT, listHeight());
        int maxScroll = getMaxScroll();
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        scrollBar.setScrollPercentage(maxScroll > 0 ? (double) scrollOffset / maxScroll : 0);
    }

    private void goBack() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parentScreen);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF202020);

        if (!status.isEmpty()) {
            context.text(this.font, status, PADDING, listY() - 12, UITheme.Colors.TEXT_MUTED);
        }

        actionButtons.clear();
        actionTargets.clear();

        int listY = listY();
        context.fill(PADDING, listY, panelRight(), listY + listHeight(), 0xFF151515);
        context.enableScissor(PADDING, listY, panelRight(), listY + listHeight());

        int shown = Math.min(visible.size() - scrollOffset, maxVisible() + 1);
        for (int i = 0; i < shown; i++) {
            int index = scrollOffset + i;
            if (index >= visible.size()) {
                break;
            }
            PluginCatalog.Entry entry = visible.get(index);
            int itemY = listY + i * ITEM_HEIGHT;
            boolean hovered = mouseX >= PADDING && mouseX < panelRight()
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            context.fill(PADDING + 2, itemY + 2, panelRight() - 2, itemY + ITEM_HEIGHT - 2,
                    hovered ? 0xFF2A2A2A : 0xFF1A1A1A);

            String label = actionLabel(entry);
            CustomButton action = new CustomButton(
                    listRightEdge() - ACTION_WIDTH - 4, itemY + 5, ACTION_WIDTH, BUTTON_SIZE,
                    Component.literal(label), b -> install(entry));
            action.active = !"Installed".equals(label);
            action.extractRenderState(context, mouseX, mouseY, delta);
            actionButtons.add(action);
            actionTargets.add(entry);

            boolean installed = PluginCatalog.installedVersion(entry.id()) != null;
            context.text(this.font, entry.name(), PADDING + 6, itemY + 5,
                    installed ? UITheme.Colors.ACCENT_GREEN : UITheme.Colors.TEXT_PRIMARY);
            String meta = (entry.version().isEmpty() ? "" : "v" + entry.version() + "  ")
                    + String.join(", ", entry.hosts());
            context.text(this.font, trim(meta, listRightEdge() - PADDING - ACTION_WIDTH - 20),
                    PADDING + 6, itemY + 16, UITheme.Colors.TEXT_TAG);
        }
        context.disableScissor();

        if (loading && loadingSpinner != null) {
            loadingSpinner.extractRenderState(context, mouseX, mouseY, delta);
        }

        if (scrollBar != null && scrollBar.isVisible() && this.minecraft != null) {
            boolean changed = scrollBar.updateAndRender(context, mouseX, mouseY, delta,
                    Minecraft.getInstance().getWindow().handle());
            if (changed) {
                scrollOffset = (int) Math.round(scrollBar.getScrollPercentage() * getMaxScroll());
            }
        }

        super.extractRenderState(context, mouseX, mouseY, delta);

        if (toastManager != null) {
            toastManager.render(context, delta, mouseX, mouseY);
        }
    }

    private String trim(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (!result.isEmpty() && this.font.width(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(verticalAmount)));
            updateScrollBar();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (toastManager != null && toastManager.mouseClicked(click.x(), click.y())) {
            return true;
        }
        if (click.button() == 0) {
            for (int i = 0; i < actionButtons.size() && i < actionTargets.size(); i++) {
                CustomButton button = actionButtons.get(i);
                if (button.active
                        && click.x() >= button.getX() && click.x() < button.getX() + button.getWidth()
                        && click.y() >= button.getY() && click.y() < button.getY() + button.getHeight()) {
                    install(actionTargets.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void onClose() {
        goBack();
    }
}

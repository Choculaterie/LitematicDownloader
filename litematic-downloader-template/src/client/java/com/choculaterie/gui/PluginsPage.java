package com.choculaterie.gui;

import com.choculaterie.gui.widget.ToastManager;
import com.choculaterie.plugin.PluginRegistry;
import com.choculaterie.plugin.PluginSource;
import com.choculaterie.vanilib.gui.theme.UITheme;
import com.choculaterie.vanilib.gui.widget.ConfirmPopup;
import com.choculaterie.vanilib.gui.widget.CustomButton;
import com.choculaterie.vanilib.gui.widget.ScrollBar;
import com.choculaterie.vanilib.gui.widget.ToggleButton;
import com.choculaterie.vanilib.util.ListSelectionManager;
import com.choculaterie.vanilib.util.file.FileOperationsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PluginsPage extends Screen {
    private static final int PADDING = 10;
    private static final int BUTTON_SIZE = 20;
    private static final int OPEN_FOLDER_WIDTH = 90;
    private static final int ITEM_HEIGHT = 30;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int WARNING_COLOR = 0xFFFFC107;

    private static final int DELETE_WIDTH = 60;
    private static final int TOGGLE_WIDTH = 40;

    private final Screen parentScreen;
    private final List<Row> rows = new ArrayList<>();
    private ScrollBar scrollBar;
    private ToastManager toastManager;
    private int barWidth = -1;
    private int barY = -1;
    private int barLength = -1;
    private int scrollOffset = 0;
    private ConfirmPopup activePopup;
    private FileOperationsManager fileOps;
    private final ListSelectionManager selectionManager = new ListSelectionManager();
    private CustomButton deleteButton;
    private boolean wasDeleteKeyPressed = false;
    private boolean wasZKeyPressed = false;
    private boolean wasYKeyPressed = false;
    private boolean wasAKeyPressed = false;

    private record Row(PluginSource plugin, String error, String errorFile) {
        boolean isError() {
            return plugin == null;
        }

        String fileName() {
            return plugin != null ? plugin.manifest().sourceFile : errorFile;
        }
    }

    public PluginsPage(Screen parentScreen) {
        super(Component.literal("Plugins"));
        this.parentScreen = parentScreen;
    }

    private int warningY() {
        return PADDING * 4 + BUTTON_SIZE;
    }

    private int listY() {
        return warningY() + 46;
    }

    private int listHeight() {
        return Math.max(ITEM_HEIGHT, this.height - listY() - PADDING);
    }

    private boolean scrollBarShowing() {
        return rows.size() * ITEM_HEIGHT > listHeight();
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
        return Math.max(0, rows.size() - maxVisible());
    }

    @Override
    protected void init() {
        super.init();
        if (toastManager == null && this.minecraft != null) {
            toastManager = new ToastManager(this.minecraft);
        }

        buildRows();

        this.addRenderableWidget(new CustomButton(
                PADDING, PADDING, BUTTON_SIZE, BUTTON_SIZE,
                Component.literal("←"), button -> goBack()));

        int reloadX = this.width - PADDING - BUTTON_SIZE;
        this.addRenderableWidget(new CustomButton(
                reloadX, PADDING, BUTTON_SIZE, BUTTON_SIZE,
                Component.literal("🔄"), button -> {
            PluginRegistry.reload();
            scrollOffset = 0;
            this.rebuildWidgets();
            if (toastManager != null) {
                int count = PluginRegistry.all().size();
                toastManager.showSuccess(count == 1 ? "1 plugin loaded" : count + " plugins loaded");
            }
        }));

        int openFolderX = reloadX - PADDING - OPEN_FOLDER_WIDTH;
        this.addRenderableWidget(new CustomButton(
                openFolderX, PADDING, OPEN_FOLDER_WIDTH, BUTTON_SIZE,
                Component.literal("Open folder"), button ->
                Util.getPlatform().openPath(PluginRegistry.directory())));

        this.addRenderableWidget(new CustomButton(
                PADDING + BUTTON_SIZE + PADDING, PADDING, 90, BUTTON_SIZE,
                Component.literal("Get plugins"), button -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new PluginBrowsePage(this));
            }
        }));

        deleteButton = new CustomButton(
                openFolderX - PADDING - DELETE_WIDTH, PADDING, DELETE_WIDTH, BUTTON_SIZE,
                Component.literal("Delete"), button -> handleDeleteClick());
        deleteButton.active = selectionManager.hasSelection();
        this.addRenderableWidget(deleteButton);

        ensureFileOps();
        int barHeight = listHeight();
        if (scrollBar == null || barWidth != this.width || barY != listY() || barLength != barHeight) {
            scrollBar = new ScrollBar(this.width - PADDING - SCROLLBAR_WIDTH, listY(), barHeight);
            barWidth = this.width;
            barY = listY();
            barLength = barHeight;
        }
        updateScrollBar();

        int visible = Math.min(rows.size(), maxVisible());
        for (int i = 0; i < visible; i++) {
            int index = scrollOffset + i;
            if (index >= rows.size()) {
                break;
            }
            Row row = rows.get(index);
            int rowY = listY() + i * ITEM_HEIGHT;

            if (!row.isError()) {
                String id = row.plugin().id();
                this.addRenderableWidget(new ToggleButton(
                        listRightEdge() - TOGGLE_WIDTH - 4, rowY + 5,
                        PluginRegistry.isEnabled(id),
                        enabled -> PluginRegistry.setEnabled(id, enabled)));
            }
        }
    }

    private File pluginFile(Row row) {
        String name = row.fileName();
        return name == null ? null : PluginRegistry.directory().resolve(name).toFile();
    }

    private boolean isShiftHeld() {
        long w = Minecraft.getInstance().getWindow().handle();
        return w != 0 && (org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS);
    }

    private boolean isCtrlHeld() {
        long w = Minecraft.getInstance().getWindow().handle();
        return w != 0 && (org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS);
    }

    private void handleDeleteClick() {
        if (activePopup != null || !selectionManager.hasSelection()) {
            return;
        }

        List<Row> selected = selectedRows();
        if (selected.isEmpty()) {
            return;
        }

        if (isShiftHeld()) {
            deleteRows(selected);
            return;
        }

        String title;
        StringBuilder message = new StringBuilder();
        if (selected.size() == 1) {
            Row row = selected.getFirst();
            title = "Delete plugin?";
            message.append("Are you sure you want to delete \"").append(rowLabel(row)).append("\"?");
        } else {
            title = "Delete " + selected.size() + " plugins?";
            message.append("Are you sure you want to delete ").append(selected.size())
                    .append(" selected plugins?\n");
            for (int i = 0; i < selected.size(); i++) {
                message.append(i == selected.size() - 1 ? "\n└── " : "\n├── ")
                        .append(rowLabel(selected.get(i)));
            }
        }
        message.append("\n\nThey are moved to the plugins .trash folder.");

        activePopup = new ConfirmPopup(
                this,
                title,
                message.toString(),
                () -> {
                    activePopup = null;
                    deleteRows(selected);
                },
                () -> activePopup = null,
                "Delete");
    }

    private String rowLabel(Row row) {
        return row.isError() ? row.errorFile() : row.plugin().manifest().displayName();
    }

    private List<Row> selectedRows() {
        List<Row> selected = new ArrayList<>();
        for (int index : selectionManager.getSelectedIndices()) {
            if (index >= 0 && index < rows.size()) {
                selected.add(rows.get(index));
            }
        }
        return selected;
    }

    private void ensureFileOps() {
        if (fileOps != null) {
            return;
        }
        File trash = PluginRegistry.directory().resolve(".trash").toFile();
        if (!trash.exists() && !trash.mkdirs()) {
            System.err.println("[Plugin] cannot create " + trash);
            return;
        }
        fileOps = new FileOperationsManager(trash);
    }

    private void afterFileChange() {
        selectionManager.clearSelection();
        PluginRegistry.reload();
        scrollOffset = 0;
        this.rebuildWidgets();
    }

    private void handleKeyShortcuts() {
        long w = Minecraft.getInstance().getWindow().handle();
        if (w == 0) {
            return;
        }
        boolean ctrl = isCtrlHeld();
        boolean shift = isShiftHeld();

        boolean deleteDown = org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (deleteDown && !wasDeleteKeyPressed && selectionManager.hasSelection()) {
            if (shift) {
                deleteRows(selectedRows());
            } else {
                handleDeleteClick();
            }
        }
        wasDeleteKeyPressed = deleteDown;

        boolean zDown = org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_Z)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (zDown && !wasZKeyPressed && ctrl && fileOps != null) {
            fileOps.performUndo();
            afterFileChange();
        }
        wasZKeyPressed = zDown;

        boolean yDown = org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_Y)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (yDown && !wasYKeyPressed && ctrl && fileOps != null) {
            fileOps.performRedo();
            afterFileChange();
        }
        wasYKeyPressed = yDown;

        boolean aDown = org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_A)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (aDown && !wasAKeyPressed && ctrl && !rows.isEmpty()) {
            selectionManager.selectAll(rows.size());
            updateSelectionButtons();
        }
        wasAKeyPressed = aDown;
    }

    private void deleteRows(List<Row> targets) {
        ensureFileOps();
        if (fileOps == null) {
            return;
        }

        List<File> files = new ArrayList<>();
        for (Row row : targets) {
            File file = pluginFile(row);
            if (file != null && file.exists()) {
                if (!row.isError()) {
                    PluginRegistry.setEnabled(row.plugin().id(), false);
                }
                files.add(file);
            }
        }
        if (!files.isEmpty()) {
            fileOps.deleteFiles(files);
        }
        afterFileChange();
    }

    private void buildRows() {
        rows.clear();
        for (PluginSource plugin : PluginRegistry.all()) {
            rows.add(new Row(plugin, null, null));
        }
        for (Map.Entry<String, String> entry : PluginRegistry.errors().entrySet()) {
            rows.add(new Row(null, entry.getValue(), entry.getKey()));
        }
    }

    private void updateScrollBar() {
        if (scrollBar == null) {
            return;
        }
        int contentHeight = rows.size() * ITEM_HEIGHT;
        scrollBar.setScrollData(contentHeight, listHeight());
        int maxScroll = getMaxScroll();
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        scrollBar.setScrollPercentage(maxScroll > 0 ? (double) scrollOffset / maxScroll : 0);
    }

    private void goBack() {
        if (this.minecraft != null) {
            if (parentScreen instanceof LitematicDownloaderScreen screen) {
                screen.reloadAfterPluginChange();
            }
            this.minecraft.gui.setScreen(parentScreen);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (activePopup == null) {
            handleKeyShortcuts();
        }
        context.fill(0, 0, this.width, this.height, 0xFF202020);


        int y = warningY();
        context.text(this.font, "⚠ Plugins are community files, not reviewed by this mod.",
                PADDING, y, WARNING_COLOR);
        context.text(this.font, "An enabled plugin makes your game contact the sites and APIs listed",
                PADDING, y + 11, UITheme.Colors.TEXT_MUTED);
        context.text(this.font, "below it, from your IP address. Only enable plugins you trust.",
                PADDING, y + 22, UITheme.Colors.TEXT_MUTED);

        renderList(context, mouseX, mouseY, delta);

        super.extractRenderState(context, mouseX, mouseY, delta);

        if (activePopup != null) {
            activePopup.extractRenderState(context, mouseX, mouseY, delta);
        }

        if (toastManager != null) {
            toastManager.render(context, delta, mouseX, mouseY);
        }
    }

    private void renderList(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int listY = listY();
        int listHeight = listHeight();

        context.fill(PADDING, listY, panelRight(), listY + listHeight, 0xFF151515);

        if (rows.isEmpty()) {
            context.text(this.font, "No plugins installed.", PADDING + 6, listY + 8, UITheme.Colors.TEXT_MUTED);
            return;
        }

        context.enableScissor(PADDING, listY, panelRight(), listY + listHeight);
        int visible = Math.min(rows.size() - scrollOffset, maxVisible() + 1);
        for (int i = 0; i < visible; i++) {
            int index = scrollOffset + i;
            if (index >= rows.size()) {
                break;
            }
            int itemY = listY + i * ITEM_HEIGHT;
            boolean hovered = mouseX >= PADDING && mouseX < panelRight()
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean selected = selectionManager.isSelected(index);
            int bgColor = selected ? 0xFF404040 : hovered ? 0xFF2A2A2A : 0xFF1A1A1A;
            context.fill(PADDING + 2, itemY + 2, panelRight() - 2, itemY + ITEM_HEIGHT - 2, bgColor);

            Row row = rows.get(index);
            if (row.isError()) {
                context.text(this.font, trim(row.errorFile(), listRightEdge() - PADDING - 40),
                        PADDING + 6, itemY + 5, UITheme.Colors.TOAST_ACCENT_ERROR);
                context.text(this.font, trim(row.error(), listRightEdge() - PADDING - 40),
                        PADDING + 6, itemY + 16, UITheme.Colors.TEXT_MUTED);
            } else {
                PluginSource plugin = row.plugin();
                boolean enabled = PluginRegistry.isEnabled(plugin.id());
                context.text(this.font, plugin.manifest().displayName(), PADDING + 6, itemY + 5,
                        enabled ? UITheme.Colors.ACCENT_GREEN : UITheme.Colors.TEXT_MUTED);
                String hosts = String.join(", ", plugin.manifest().hosts);
                boolean anywhere = plugin.manifest().downloadsAnywhere();
                context.text(this.font,
                        trim(hosts + (anywhere ? "  + downloads from any site" : ""),
                                listRightEdge() - PADDING - 90),
                        PADDING + 6, itemY + 16,
                        anywhere ? WARNING_COLOR : UITheme.Colors.TEXT_TAG);
            }
        }
        context.disableScissor();

        if (scrollBar != null && scrollBar.isVisible() && this.minecraft != null) {
            boolean changed = scrollBar.updateAndRender(context, mouseX, mouseY, delta,
                    Minecraft.getInstance().getWindow().handle());
            if (changed) {
                int newOffset = (int) Math.round(scrollBar.getScrollPercentage() * getMaxScroll());
                if (newOffset != scrollOffset) {
                    scrollOffset = newOffset;
                    this.rebuildWidgets();
                }
            }
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
            int newOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(verticalAmount)));
            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                updateScrollBar();
                this.rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (activePopup != null) {
            return activePopup.mouseClicked(click.x(), click.y(), click.button());
        }
        if (toastManager != null && toastManager.mouseClicked(click.x(), click.y())) {
            return true;
        }
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        if (click.button() == 0 && handleListClick(click.x(), click.y())) {
            return true;
        }
        return false;
    }

    private boolean handleListClick(double mouseX, double mouseY) {
        int listY = listY();
        if (mouseX < PADDING || mouseX >= panelRight() || mouseY < listY || mouseY >= listY + listHeight()) {
            return false;
        }
        int slot = (int) ((mouseY - listY) / ITEM_HEIGHT);
        int index = scrollOffset + slot;
        if (slot < 0 || slot >= maxVisible() || index >= rows.size()) {
            selectionManager.clearSelection();
            updateSelectionButtons();
            return true;
        }
        if (isShiftHeld()) {
            selectionManager.selectRange(index);
        } else if (isCtrlHeld()) {
            selectionManager.toggleSelection(index);
        } else {
            selectionManager.selectSingle(index);
        }
        updateSelectionButtons();
        return true;
    }

    private void updateSelectionButtons() {
        if (deleteButton != null) {
            deleteButton.active = selectionManager.hasSelection();
        }
    }

    @Override
    public void onClose() {
        if (activePopup != null) {
            activePopup = null;
            return;
        }
        goBack();
    }
}

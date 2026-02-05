package com.choculaterie.gui.widget;

import com.choculaterie.gui.theme.UITheme;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class BlockReplacementPopup implements Drawable {
    private final MinecraftClient client;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private CustomTextField searchField;
    private CustomButton cancelButton;
    private CustomButton replaceButton;
    private final List<Block> allBlocks = new ArrayList<>();
    private final List<Block> filteredBlocks = new ArrayList<>();
    private int scrollOffset = 0;
    private ScrollBar scrollBar;
    private int selectedIndex = -1;

    private final String originalBlockId;
    private BiConsumer<String, String> onBlockSelected;
    private Runnable onCancel;

    private static final int ITEM_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 90;

    public BlockReplacementPopup(int screenWidth, int screenHeight, String originalBlockId) {
        this.client = MinecraftClient.getInstance();
        this.originalBlockId = originalBlockId;

        this.width = Math.min(400, screenWidth - 40);
        this.height = Math.min(500, screenHeight - 40);
        this.x = (screenWidth - width) / 2;
        this.y = (screenHeight - height) / 2;

        loadAllBlocks();
        filteredBlocks.addAll(allBlocks);

        if (client != null) {
            int searchFieldWidth = width - UITheme.Dimensions.PADDING * 2;
            searchField = new CustomTextField(
                    client,
                    x + UITheme.Dimensions.PADDING,
                    y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING,
                    searchFieldWidth,
                    UITheme.Dimensions.BUTTON_HEIGHT,
                    Text.of("Search"));
            searchField.setPlaceholder(Text.of("Search blocks..."));
            searchField.setOnChanged(() -> onSearchChanged(searchField.getText()));
            searchField.setFocused(true);
        }

        int buttonWidth = 80;
        cancelButton = new CustomButton(
                x + UITheme.Dimensions.PADDING,
                y + height - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of("Cancel"),
                btn -> {
                    if (onCancel != null) {
                        onCancel.run();
                    }
                });

        replaceButton = new CustomButton(
                x + width - UITheme.Dimensions.PADDING - buttonWidth,
                y + height - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of("Replace"),
                btn -> {
                    if (selectedIndex >= 0 && selectedIndex < filteredBlocks.size()) {
                        Block selectedBlock = filteredBlocks.get(selectedIndex);
                        String newBlockId = Registries.BLOCK.getId(selectedBlock).toString();
                        if (onBlockSelected != null) {
                            onBlockSelected.accept(originalBlockId, newBlockId);
                        }
                    }
                });

        updateScrollBar();
    }

    private void loadAllBlocks() {
        for (Identifier id : Registries.BLOCK.getIds()) {
            Block block = Registries.BLOCK.get(id);
            if (block != null && !block.asItem().equals(net.minecraft.item.Items.AIR)) {
                allBlocks.add(block);
            }
        }
        allBlocks.sort((a, b) -> {
            String nameA = Registries.BLOCK.getId(a).getPath();
            String nameB = Registries.BLOCK.getId(b).getPath();
            return nameA.compareTo(nameB);
        });
    }

    private void onSearchChanged(String search) {
        filteredBlocks.clear();
        String lowerSearch = search.toLowerCase();

        if (search.isEmpty()) {
            filteredBlocks.addAll(allBlocks);
        } else {
            for (Block block : allBlocks) {
                String blockName = Registries.BLOCK.getId(block).getPath();
                if (blockName.contains(lowerSearch)) {
                    filteredBlocks.add(block);
                }
            }
        }

        scrollOffset = 0;
        updateScrollBar();
    }

    private void updateScrollBar() {
        int listY = y + HEADER_HEIGHT;
        int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT
                - UITheme.Dimensions.PADDING;
        int scrollBarX = x + width - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH;
        scrollBar = new ScrollBar(scrollBarX, listY, listHeight);

        int contentHeight = filteredBlocks.size() * ITEM_HEIGHT;
        scrollBar.setScrollData(contentHeight, listHeight);
    }

    private int getMaxScroll() {
        int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT
                - UITheme.Dimensions.PADDING;
        int contentHeight = filteredBlocks.size() * ITEM_HEIGHT;
        return contentHeight <= listHeight ? 0 : filteredBlocks.size() - listHeight / ITEM_HEIGHT;
    }

    public void setOnBlockSelected(BiConsumer<String, String> callback) {
        this.onBlockSelected = callback;
    }

    public void setOnCancel(Runnable callback) {
        this.onCancel = callback;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(),
                UITheme.Colors.OVERLAY_BG);

        context.fill(x, y, x + width, y + height, UITheme.Colors.BUTTON_BG_DISABLED);

        context.fill(x, y, x + width, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        context.fill(x, y + height - UITheme.Dimensions.BORDER_WIDTH, x + width, y + height,
                UITheme.Colors.BUTTON_BORDER);
        context.fill(x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + height, UITheme.Colors.BUTTON_BORDER);
        context.fill(x + width - UITheme.Dimensions.BORDER_WIDTH, y, x + width, y + height,
                UITheme.Colors.BUTTON_BORDER);

        String title = "Replace: " + getSimpleBlockName(originalBlockId);
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                title,
                x + width / 2,
                y + UITheme.Dimensions.PADDING,
                UITheme.Colors.TEXT_PRIMARY);

        if (searchField != null) {
            searchField.render(context, mouseX, mouseY, delta);
        }

        int listY = y + HEADER_HEIGHT;
        int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT
                - UITheme.Dimensions.PADDING;
        int listRightEdge = x + width - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH - 2;

        context.fill(x + UITheme.Dimensions.PADDING, listY, listRightEdge, listY + listHeight, UITheme.Colors.PANEL_BG);
        context.enableScissor(x + UITheme.Dimensions.PADDING, listY, listRightEdge, listY + listHeight);

        int maxVisibleItems = (listHeight / ITEM_HEIGHT) + 1;
        for (int i = scrollOffset; i < Math.min(filteredBlocks.size(), scrollOffset + maxVisibleItems); i++) {
            Block block = filteredBlocks.get(i);
            int itemY = listY + (i - scrollOffset) * ITEM_HEIGHT;

            boolean isSelected = i == selectedIndex;
            boolean isHovered = mouseX >= x + UITheme.Dimensions.PADDING && mouseX < listRightEdge &&
                    mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

            int bgColor;
            if (isSelected) {
                bgColor = 0xFF505050;
            } else if (isHovered) {
                bgColor = 0xFF404040;
            } else {
                bgColor = UITheme.Colors.PANEL_BG;
            }
            context.fill(x + UITheme.Dimensions.PADDING + 2, itemY, listRightEdge - 2, itemY + ITEM_HEIGHT, bgColor);

            ItemStack itemStack = new ItemStack(block.asItem());
            context.drawItem(itemStack, x + UITheme.Dimensions.PADDING + 4, itemY + 4);

            String blockName = getSimpleBlockName(Registries.BLOCK.getId(block).toString());
            context.drawTextWithShadow(client.textRenderer, blockName, x + UITheme.Dimensions.PADDING + 24, itemY + 8,
                    UITheme.Colors.TEXT_PRIMARY);
        }

        context.disableScissor();

        if (scrollBar != null && scrollBar.isVisible() && client != null) {
            boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta,
                    client.getWindow().getHandle());
            if (scrollChanged) {
                int maxScroll = getMaxScroll();
                scrollOffset = (int) (scrollBar.getScrollPercentage() * maxScroll);
            }
        }

        if (replaceButton != null) {
            replaceButton.active = (selectedIndex >= 0);
        }

        if (cancelButton != null) {
            cancelButton.render(context, mouseX, mouseY, delta);
        }

        if (replaceButton != null) {
            replaceButton.render(context, mouseX, mouseY, delta);
        }
    }

    private String getSimpleBlockName(String blockId) {
        if (blockId.startsWith("minecraft:")) {
            blockId = blockId.substring("minecraft:".length());
        }
        String[] words = blockId.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty())
                result.append(" ");
            result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        return result.toString();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0)
            return true;

        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            if (onCancel != null) {
                onCancel.run();
            }
            return true;
        }

        if (searchField != null) {
            boolean clickedInField = mouseX >= searchField.getX()
                    && mouseX < searchField.getX() + searchField.getWidth() &&
                    mouseY >= searchField.getY() && mouseY < searchField.getY() + searchField.getHeight();
            searchField.setFocused(clickedInField);

            if (clickedInField) {
                return true;
            }
        }

        if (cancelButton != null) {
            boolean isOverCancel = mouseX >= cancelButton.getX() &&
                    mouseX < cancelButton.getX() + cancelButton.getWidth() &&
                    mouseY >= cancelButton.getY() &&
                    mouseY < cancelButton.getY() + cancelButton.getHeight();
            if (isOverCancel) {
                if (onCancel != null) {
                    onCancel.run();
                }
                return true;
            }
        }

        if (replaceButton != null) {
            boolean isOverReplace = mouseX >= replaceButton.getX() &&
                    mouseX < replaceButton.getX() + replaceButton.getWidth() &&
                    mouseY >= replaceButton.getY() &&
                    mouseY < replaceButton.getY() + replaceButton.getHeight();
            if (isOverReplace) {
                if (selectedIndex >= 0 && selectedIndex < filteredBlocks.size()) {
                    Block selectedBlock = filteredBlocks.get(selectedIndex);
                    String newBlockId = Registries.BLOCK.getId(selectedBlock).toString();
                    if (onBlockSelected != null) {
                        onBlockSelected.accept(originalBlockId, newBlockId);
                    }
                }
                return true;
            }
        }

        int listY = y + HEADER_HEIGHT;
        int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT
                - UITheme.Dimensions.PADDING;
        int listRightEdge = x + width - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH - 2;

        if (mouseX >= x + UITheme.Dimensions.PADDING && mouseX < listRightEdge &&
                mouseY >= listY && mouseY < listY + listHeight) {
            int clickedIndex = scrollOffset + (int) ((mouseY - listY) / ITEM_HEIGHT);
            if (clickedIndex >= 0 && clickedIndex < filteredBlocks.size()) {
                selectedIndex = clickedIndex;
                return true;
            }
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listY = y + HEADER_HEIGHT;
        int listHeight = height - HEADER_HEIGHT - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT
                - UITheme.Dimensions.PADDING;

        if (mouseX >= x && mouseX < x + width &&
                mouseY >= listY && mouseY < listY + listHeight) {
            int maxScroll = getMaxScroll();
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));

            if (scrollBar != null && maxScroll > 0) {
                scrollBar.setScrollPercentage((double) scrollOffset / maxScroll);
            }
            return true;
        }

        return false;
    }
}

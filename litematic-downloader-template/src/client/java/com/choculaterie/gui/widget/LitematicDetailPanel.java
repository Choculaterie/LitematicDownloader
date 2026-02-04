package com.choculaterie.gui.widget;

import com.choculaterie.gui.theme.UITheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Click;

import java.io.File;

public class LitematicDetailPanel implements Drawable, Element {

    private int x;
    private int y;
    private int width;
    private int height;

    private File litematicFile;
    private final MinecraftClient client;

    private CustomButton closeButton;
    private Runnable onClose;

    public LitematicDetailPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = MinecraftClient.getInstance();
        updateCloseButton();
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        updateCloseButton();
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    private void updateCloseButton() {
        int closeButtonSize = 20;
        closeButton = new CustomButton(
                x + width - closeButtonSize,
                y,
                closeButtonSize,
                closeButtonSize,
                net.minecraft.text.Text.of("X"),
                btn -> {
                    if (onClose != null) {
                        onClose.run();
                    }
                }
        );
        closeButton.setRenderAsXIcon(true);
    }

    public void setFile(File file) {
        this.litematicFile = file;
    }

    public File getFile() {
        return litematicFile;
    }

    public boolean hasFile() {
        return litematicFile != null;
    }

    public void clear() {
        this.litematicFile = null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(x, y, x + width, y + height, UITheme.Colors.PANEL_BG);

        context.fill(x, y, x + 1, y + height, UITheme.Colors.PANEL_BORDER);
        context.fill(x, y, x + width, y + 1, UITheme.Colors.PANEL_BORDER);
        context.fill(x + width - 1, y, x + width, y + height, UITheme.Colors.PANEL_BORDER);
        context.fill(x, y + height - 1, x + width, y + height, UITheme.Colors.PANEL_BORDER);

        if (closeButton != null) {
            closeButton.render(context, mouseX, mouseY, delta);
        }

        if (litematicFile == null) {
            String emptyText = "Select a litematic file";
            int textWidth = client.textRenderer.getWidth(emptyText);
            context.drawTextWithShadow(
                    client.textRenderer,
                    emptyText,
                    x + (width - textWidth) / 2,
                    y + height / 2 - 4,
                    0xFF888888
            );
            return;
        }

        int contentX = x + UITheme.Dimensions.PADDING;
        int contentY = y + UITheme.Dimensions.PADDING + 25;

        String fileName = litematicFile.getName();
        context.drawTextWithShadow(
                client.textRenderer,
                fileName,
                contentX,
                contentY,
                0xFFFFFFFF
        );
        contentY += 15;

        long sizeKB = litematicFile.length() / 1024;
        String sizeText = "Size: " + sizeKB + " KB";
        context.drawTextWithShadow(
                client.textRenderer,
                sizeText,
                contentX,
                contentY,
                0xFFAAAAAA
        );
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closeButton != null) {
            if (closeButton.mouseClicked(click, doubled)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (closeButton != null) {
            return closeButton.mouseReleased(click);
        }
        return false;
    }

    private boolean isMouseOverButton(CustomButton button, double mouseX, double mouseY) {
        return button != null &&
               mouseX >= button.getX() &&
               mouseX < button.getX() + button.getWidth() &&
               mouseY >= button.getY() &&
               mouseY < button.getY() + button.getHeight();
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }
}

package com.choculaterie.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Custom styled button for the Litematic Downloader UI
 */
public class CustomButton extends ButtonWidget {
    private static final int BUTTON_COLOR = 0xFF3A3A3A;
    private static final int BUTTON_HOVER_COLOR = 0xFF4A4A4A;
    private static final int BUTTON_DISABLED_COLOR = 0xFF2A2A2A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_DISABLED_COLOR = 0xFF888888;

    private boolean renderAsXIcon = false;
    private boolean renderAsDownloadIcon = false;

    public CustomButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    public void setRenderAsXIcon(boolean renderAsXIcon) {
        this.renderAsXIcon = renderAsXIcon;
    }

    public void setRenderAsDownloadIcon(boolean renderAsDownloadIcon) {
        this.renderAsDownloadIcon = renderAsDownloadIcon;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean isHovered = mouseX >= this.getX() && mouseY >= this.getY() &&
                mouseX < this.getX() + this.getWidth() && mouseY < this.getY() + this.getHeight();

        int color;
        int textColor;

        if (!this.active) {
            color = BUTTON_DISABLED_COLOR;
            textColor = TEXT_DISABLED_COLOR;
        } else if (isHovered) {
            color = BUTTON_HOVER_COLOR;
            textColor = TEXT_COLOR;
        } else {
            color = BUTTON_COLOR;
            textColor = TEXT_COLOR;
        }

        // Draw button background
        context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                this.getY() + this.getHeight(), color);

        // Draw border
        context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                this.getY() + 1, 0xFF555555); // Top
        context.fill(this.getX(), this.getY() + this.getHeight() - 1,
                this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xFF555555); // Bottom
        context.fill(this.getX(), this.getY(), this.getX() + 1,
                this.getY() + this.getHeight(), 0xFF555555); // Left
        context.fill(this.getX() + this.getWidth() - 1, this.getY(),
                this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xFF555555); // Right

        // Check if this is the settings icon (⚙) - keep it at original position
        String messageText = this.getMessage().getString();
        boolean isSettingsIcon = messageText.equals("⚙");
        int yOffset = isSettingsIcon ? 0 : 1;

        if (renderAsXIcon) {
            // Draw ❌ emoji
            context.drawCenteredTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    Text.literal("❌"),
                    this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2 + yOffset,
                    textColor
            );
        } else if (renderAsDownloadIcon) {
            // Draw 💾 emoji
            context.drawCenteredTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    Text.literal("💾"),
                    this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2 + yOffset,
                    textColor
            );
        } else {
            // Draw centered text
            context.drawCenteredTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    this.getMessage(),
                    this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2 + yOffset,
                    textColor
            );
        }
    }
}

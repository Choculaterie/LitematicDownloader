package com.choculaterie.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class ConfirmationScreen extends Screen {
    private final Text message;
    private final Consumer<Boolean> resultHandler;
    private final Screen parentScreen;

    public ConfirmationScreen(Text title, Text message, Consumer<Boolean> resultHandler) {
        super(title);
        this.message = message;
        this.resultHandler = resultHandler;
        this.parentScreen = MinecraftClient.getInstance().currentScreen;
    }

    @Override
    protected void init() {
        super.init();

        // Calculate message height to position buttons properly
        String[] lines = this.message.getString().split("\n");
        int messageHeight = lines.length * 10; // 10 pixels per line

        int buttonWidth = 100;
        int buttonHeight = 20;
        int gap = 10;
        int buttonsWidth = buttonWidth * 2 + gap;
        int startX = (this.width - buttonsWidth) / 2;

        // Position buttons below the message with proper spacing
        int messageEndY = this.height / 2 - 10 + messageHeight;
        int buttonY = messageEndY + 30; // 30 pixels gap below message

        // Yes button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Yes"), button -> {
            MinecraftClient.getInstance().setScreen(parentScreen);
            this.resultHandler.accept(true);
        }).dimensions(startX, buttonY, buttonWidth, buttonHeight).build());

        // No button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("No"), button -> {
            MinecraftClient.getInstance().setScreen(parentScreen);
            this.resultHandler.accept(false);
        }).dimensions(startX + buttonWidth + gap, buttonY, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);

        // Draw title and message
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 30, 0xFFFFFF);

        // Split message over multiple lines if needed
        int y = this.height / 2 - 10;
        for (String line : this.message.getString().split("\n")) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(line), this.width / 2, y, 0xFFFFFF);
            y += 10;
        }


    }
}
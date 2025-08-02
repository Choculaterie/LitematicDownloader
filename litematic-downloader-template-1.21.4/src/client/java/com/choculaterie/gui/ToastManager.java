package com.choculaterie.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.joml.Matrix3x2f;

public class ToastManager {
    private static final List<Toast> toasts = new ArrayList<>();
    private static final int TOAST_DURATION = 3000; // 3 seconds
    private static final int TOAST_HEIGHT = 20;
    private static final int TOAST_MARGIN = 10;

    public static void addToast(String message, boolean isError) {
        toasts.add(new Toast(message, isError, System.currentTimeMillis()));
    }

    public static void render(DrawContext context, int screenWidth) {
        long currentTime = System.currentTimeMillis();
        Iterator<Toast> iterator = toasts.iterator();

        // Save the current transform state
        Matrix3x2f originalMatrix = new Matrix3x2f(context.getMatrices());

        int y = TOAST_MARGIN;
        while (iterator.hasNext()) {
            Toast toast = iterator.next();

            if (currentTime - toast.creationTime > TOAST_DURATION) {
                iterator.remove();
                continue;
            }

            float alpha = 1.0f;
            long age = currentTime - toast.creationTime;
            if (age > TOAST_DURATION - 500) {
                alpha = (float)(TOAST_DURATION - age) / 500.0f;
            }

            int width = MinecraftClient.getInstance().textRenderer.getWidth(toast.message) + 20;
            int x = TOAST_MARGIN;

            // Apply alpha to colors using ColorHelper
            int textColor = ColorHelper.withAlpha(alpha, 0xFFFFFF);
            int bgColor = toast.isError ?
                    ColorHelper.withAlpha(alpha, 0xFF0000) :  // Red for errors with alpha
                    ColorHelper.withAlpha(alpha, 0x00AA00);   // Green for success with alpha

            // Draw background
            context.fill(x, y, x + width, y + TOAST_HEIGHT, bgColor);

            // Draw text with Text.literal to convert String to Text
            context.drawTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    Text.literal(toast.message),
                    x + 10,
                    y + 5,
                    textColor
            );

            y += TOAST_HEIGHT + 5;
        }

        // Restore the original transform state
        context.getMatrices().set(originalMatrix);
    }

    private static class Toast {
        final String message;
        final boolean isError;
        final long creationTime;

        Toast(String message, boolean isError, long creationTime) {
            this.message = message;
            this.isError = isError;
            this.creationTime = creationTime;
        }
    }
}

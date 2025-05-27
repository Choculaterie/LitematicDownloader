package com.choculaterie.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
        context.getMatrices().push();

        // Ensure toasts are rendered at the highest z-index
        context.getMatrices().translate(0.0, 0.0, 1000.0);

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
            int x = screenWidth - width - TOAST_MARGIN;

            // Fully opaque background colors
            int bgColor = toast.isError ?
                    0xFFFF0000 :  // Opaque red for errors
                    0xFF00AA00;   // Opaque green for success

            // Draw background with z-positioning
            context.fill(x, y, x + width, y + TOAST_HEIGHT, bgColor);

            // Draw text with Text.literal to convert String to Text
            context.drawTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    Text.literal(toast.message),
                    x + 10,
                    y + 5,
                    0xFFFFFFFF
            );

            y += TOAST_HEIGHT + 5;
        }

        // Restore the original transform state
        context.getMatrices().pop();
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
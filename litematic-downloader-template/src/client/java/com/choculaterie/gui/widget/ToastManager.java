package com.choculaterie.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages multiple toast notifications
 */
public class ToastManager {
    private final List<Toast> toasts = new ArrayList<>();
    private final MinecraftClient client;
    private static final int TOAST_SPACING = 5;
    private static final int TOP_PADDING = 10;

    public ToastManager(MinecraftClient client) {
        this.client = client;
    }

    /**
     * Show a toast notification
     */
    public void showToast(String message, Toast.Type type) {
        showToast(message, type, false, null);
    }

    /**
     * Show a toast notification with optional copy button
     */
    public void showToast(String message, Toast.Type type, boolean hasCopyButton, String copyText) {
        if (client.getWindow() == null) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // Start from top right, stack downwards
        int yPosition = TOP_PADDING;

        // Calculate Y position based on existing toasts
        for (Toast toast : toasts) {
            yPosition += toast.getHeight();
        }

        // Don't show toast if it would go off screen
        if (yPosition + 70 > screenHeight) {
            // Remove oldest toast to make room
            if (!toasts.isEmpty()) {
                toasts.remove(0);
                yPosition = TOP_PADDING;
                for (Toast toast : toasts) {
                    yPosition += toast.getHeight();
                }
            }
        }

        toasts.add(new Toast(message, type, screenWidth, yPosition, hasCopyButton, copyText, client));
    }

    /**
     * Show success toast
     */
    public void showSuccess(String message) {
        showToast(message, Toast.Type.SUCCESS);
    }

    /**
     * Show error toast
     */
    public void showError(String message) {
        showToast(message, Toast.Type.ERROR);
    }

    /**
     * Show error toast with copy button for full error details
     */
    public void showError(String message, String fullErrorText) {
        showToast(message, Toast.Type.ERROR, true, fullErrorText);
    }

    /**
     * Show info toast
     */
    public void showInfo(String message) {
        showToast(message, Toast.Type.INFO);
    }

    /**
     * Show warning toast
     */
    public void showWarning(String message) {
        showToast(message, Toast.Type.WARNING);
    }

    /**
     * Render all toasts and remove expired ones
     */
    public void render(DrawContext context, float delta, int mouseX, int mouseY) {
        if (toasts.isEmpty()) return;

        // Update mouse position for Toast buttons
        Toast.updateMousePosition(mouseX, mouseY);

        // Render and update toasts, remove expired ones
        Iterator<Toast> iterator = toasts.iterator();
        boolean toastRemoved = false;

        while (iterator.hasNext()) {
            Toast toast = iterator.next();
            boolean shouldRemove = toast.render(context, client.textRenderer);

            if (shouldRemove) {
                iterator.remove();
                toastRemoved = true;
            }
        }

        // If a toast was removed, recalculate all positions and animate shift
        if (toastRemoved && !toasts.isEmpty()) {
            int newY = TOP_PADDING;
            for (Toast toast : toasts) {
                toast.setTargetYPosition(newY);
                newY += toast.getHeight();
            }
        }
    }

    /**
     * Clear all toasts
     */
    public void clear() {
        toasts.clear();
    }

    /**
     * Check if there are any active toasts
     */
    public boolean hasToasts() {
        return !toasts.isEmpty();
    }

    /**
     * Check if mouse is hovering over any toast (to block events to elements below)
     */
    public boolean isMouseOverToast(double mouseX, double mouseY) {
        for (Toast toast : toasts) {
            if (toast.isHovering(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle mouse click on toasts (for copy and close buttons)
     * @return true if a toast handled the click
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        for (Toast toast : toasts) {
            // Check close button first
            if (toast.isCloseButtonClicked(mouseX, mouseY)) {
                toast.dismiss();
                return true;
            }

            // Check copy button
            if (toast.isCopyButtonClicked(mouseX, mouseY)) {
                // Copy to clipboard
                String textToCopy = toast.getCopyText();
                if (client.keyboard != null) {
                    client.keyboard.setClipboard(textToCopy);
                    // Show a quick success toast
                    showSuccess("Error copied to clipboard!");
                    return true;
                }
            }
        }
        return false;
    }
}


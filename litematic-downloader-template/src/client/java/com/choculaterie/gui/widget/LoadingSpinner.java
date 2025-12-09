package com.choculaterie.gui.widget;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;

/**
 * Reusable loading spinner widget - displays blocks in a circle with sequential fade effect
 */
public class LoadingSpinner implements Drawable {
    private static final int SPINNER_SIZE = 32;
    private static final int BLOCK_SIZE = 3; // Reduced from 4 to ~0.8 (20% of original), using 1 for visibility
    private static final int NUM_BLOCKS = 8;
    private static final float FADE_SPEED = 0.03f; // Much slower - reduced from 0.15f

    private int x;
    private int y;
    private float animationTime = 0.0f;

    public LoadingSpinner(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    /**
     * Update the spinner's position for the next render
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Update animation time
        animationTime += FADE_SPEED;
        if (animationTime >= NUM_BLOCKS) {
            animationTime -= NUM_BLOCKS;
        }

        // Draw blocks in a circle
        int centerX = x + SPINNER_SIZE / 2;
        int centerY = y + SPINNER_SIZE / 2;
        int radius = SPINNER_SIZE / 2 - BLOCK_SIZE;

        for (int i = 0; i < NUM_BLOCKS; i++) {
            // Calculate block position (stationary circle)
            float angle = (i * 360.0f / NUM_BLOCKS);
            double angleRad = Math.toRadians(angle);
            int blockX = (int) (centerX + Math.cos(angleRad) * radius) - BLOCK_SIZE / 2;
            int blockY = (int) (centerY + Math.sin(angleRad) * radius) - BLOCK_SIZE / 2;

            // Calculate opacity based on distance from the current animated position
            float distance = Math.abs(animationTime - i);

            // Handle wrap-around distance
            if (distance > NUM_BLOCKS / 2.0f) {
                distance = NUM_BLOCKS - distance;
            }

            // Only the closest block is fully lit, with a short trail
            float opacity;
            if (distance < 0.5f) {
                // Current block - fully lit
                opacity = 1.0f;
            } else if (distance < 1.5f) {
                // Trailing block - medium brightness
                opacity = 0.5f - (distance - 0.5f) * 0.3f;
            } else {
                // All other blocks - dim
                opacity = 0.15f;
            }

            opacity = Math.max(0.15f, Math.min(1.0f, opacity));

            // Calculate color with opacity
            int alpha = (int) (opacity * 255);
            int color = (alpha << 24) | 0x00FFFFFF; // White with varying alpha

            // Draw the block
            context.fill(blockX, blockY, blockX + BLOCK_SIZE, blockY + BLOCK_SIZE, color);
        }
    }

    public int getWidth() {
        return SPINNER_SIZE;
    }

    public int getHeight() {
        return SPINNER_SIZE;
    }
}

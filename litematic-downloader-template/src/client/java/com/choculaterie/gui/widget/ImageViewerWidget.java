package com.choculaterie.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Full-screen image viewer modal widget with carousel navigation
 */
public class ImageViewerWidget {
    private final Identifier imageTexture;
    private final int originalImageWidth;
    private final int originalImageHeight;
    private final Runnable onClose;
    private final MinecraftClient client;

    // Carousel navigation
    private final int currentImageIndex;
    private final int totalImages;
    private final Runnable onPrevious;
    private final Runnable onNext;

    private int screenWidth;
    private int screenHeight;
    private CustomButton closeButton;
    private CustomButton prevButton;
    private CustomButton nextButton;

    private static final int CLOSE_BUTTON_SIZE = 20; // Same as quit button
    private static final int CLOSE_BUTTON_MARGIN = 10;
    private static final int NAV_BUTTON_WIDTH = 25; // Same as PostDetailPanel arrow buttons
    private static final int NAV_BUTTON_HEIGHT = 16; // Same as PostDetailPanel arrow buttons
    private static final int NAV_BUTTON_SPACING = 10; // Spacing between button and indicator (same as PostDetailPanel)
    private static final int OVERLAY_COLOR = 0xE0000000; // Semi-transparent black
    private static final int SUBTITLE_COLOR = 0xFFAAAAAA; // Same as PostDetailPanel

    public ImageViewerWidget(MinecraftClient client, Identifier imageTexture,
                            int originalImageWidth, int originalImageHeight,
                            int currentImageIndex, int totalImages,
                            Runnable onPrevious, Runnable onNext, Runnable onClose) {
        this.client = client;
        this.imageTexture = imageTexture;
        this.originalImageWidth = originalImageWidth;
        this.originalImageHeight = originalImageHeight;
        this.currentImageIndex = currentImageIndex;
        this.totalImages = totalImages;
        this.onPrevious = onPrevious;
        this.onNext = onNext;
        this.onClose = onClose;

        updateLayout(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
    }

    public void updateLayout(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        // Position close button in top-right corner
        int closeX = screenWidth - CLOSE_BUTTON_SIZE - CLOSE_BUTTON_MARGIN;
        int closeY = CLOSE_BUTTON_MARGIN;

        if (closeButton == null) {
            closeButton = new CustomButton(closeX, closeY, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE,
                    Text.of("×"), btn -> onClose.run());
            closeButton.setRenderAsXIcon(true);
        } else {
            closeButton.setX(closeX);
            closeButton.setY(closeY);
            closeButton.setWidth(CLOSE_BUTTON_SIZE);
            closeButton.setHeight(CLOSE_BUTTON_SIZE);
        }

        // Only show navigation buttons if there are multiple images
        if (totalImages > 1) {
            // Calculate page indicator position (will be at bottom center)
            String indicator = String.format("%d / %d", currentImageIndex + 1, totalImages);
            int indicatorWidth = client.textRenderer.getWidth(indicator);
            int indicatorX = (screenWidth - indicatorWidth) / 2;
            int navY = screenHeight - 40; // 40px from bottom, same height as text

            // Previous button positioned left of indicator (same as PostDetailPanel)
            int prevX = indicatorX - NAV_BUTTON_WIDTH - NAV_BUTTON_SPACING;
            if (prevButton == null) {
                prevButton = new CustomButton(prevX, navY, NAV_BUTTON_WIDTH, NAV_BUTTON_HEIGHT,
                        Text.of("<"), btn -> onPrevious.run());
            } else {
                prevButton.setX(prevX);
                prevButton.setY(navY);
                prevButton.setWidth(NAV_BUTTON_WIDTH);
                prevButton.setHeight(NAV_BUTTON_HEIGHT);
            }

            // Next button positioned right of indicator (same as PostDetailPanel)
            int nextX = indicatorX + indicatorWidth + NAV_BUTTON_SPACING;
            if (nextButton == null) {
                nextButton = new CustomButton(nextX, navY, NAV_BUTTON_WIDTH, NAV_BUTTON_HEIGHT,
                        Text.of(">"), btn -> onNext.run());
            } else {
                nextButton.setX(nextX);
                nextButton.setY(navY);
                nextButton.setWidth(NAV_BUTTON_WIDTH);
                nextButton.setHeight(NAV_BUTTON_HEIGHT);
            }
        }
    }

    /**
     * Calculate scaled image dimensions to fit the screen while maintaining aspect ratio
     */
    private int[] getScaledImageDimensions() {
        if (originalImageWidth <= 0 || originalImageHeight <= 0) {
            return new int[]{screenWidth, screenHeight};
        }

        // Leave some margin around the image
        int maxWidth = screenWidth - 40;
        int maxHeight = screenHeight - 40;

        float imageAspect = (float) originalImageWidth / originalImageHeight;
        float screenAspect = (float) maxWidth / maxHeight;

        int displayWidth, displayHeight;

        if (imageAspect > screenAspect) {
            // Image is wider, fit to width
            displayWidth = Math.min(maxWidth, originalImageWidth);
            displayHeight = (int) (displayWidth / imageAspect);
        } else {
            // Image is taller, fit to height
            displayHeight = Math.min(maxHeight, originalImageHeight);
            displayWidth = (int) (displayHeight * imageAspect);
        }

        return new int[]{displayWidth, displayHeight};
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw semi-transparent overlay
        context.fill(0, 0, screenWidth, screenHeight, OVERLAY_COLOR);

        // Calculate centered image position
        int[] dimensions = getScaledImageDimensions();
        int displayWidth = dimensions[0];
        int displayHeight = dimensions[1];

        int imageX = (screenWidth - displayWidth) / 2;
        int imageY = (screenHeight - displayHeight) / 2;

        // Draw the image
        if (imageTexture != null) {
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                imageTexture,
                imageX, imageY,
                0, 0,
                displayWidth, displayHeight,
                displayWidth, displayHeight
            );
        }

        // Draw navigation buttons and page indicator if there are multiple images
        if (totalImages > 1) {
            // Calculate positions for page indicator and buttons
            String pageText = String.format("%d / %d", currentImageIndex + 1, totalImages);
            int textWidth = client.textRenderer.getWidth(pageText);
            int textX = (screenWidth - textWidth) / 2;
            int textY = screenHeight - 40 + 4; // 40px from bottom, +4 for vertical centering with 16px buttons

            // Calculate button positions (same logic as updateLayout)
            int indicatorWidth = textWidth;
            int indicatorX = textX;
            int prevBtnX = indicatorX - NAV_BUTTON_WIDTH - NAV_BUTTON_SPACING;
            int nextBtnX = indicatorX + indicatorWidth + NAV_BUTTON_SPACING;
            int navY = screenHeight - 40;

            // Draw background spanning from left button to right button, with button height
            int bgX = prevBtnX;
            int bgWidth = (nextBtnX + NAV_BUTTON_WIDTH) - prevBtnX;
            int bgY = navY;
            int bgHeight = NAV_BUTTON_HEIGHT;

            context.fill(
                bgX,
                bgY,
                bgX + bgWidth,
                bgY + bgHeight,
                0xD0000000  // Darker semi-transparent black
            );

            // Draw text with shadow
            context.drawTextWithShadow(
                client.textRenderer,
                pageText,
                textX,
                textY,
                SUBTITLE_COLOR
            );

            // Draw navigation buttons
            if (prevButton != null) {
                prevButton.render(context, mouseX, mouseY, delta);
            }
            if (nextButton != null) {
                nextButton.render(context, mouseX, mouseY, delta);
            }
        }

        // Draw close button (on top of everything)
        if (closeButton != null) {
            closeButton.render(context, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Check close button first
        if (closeButton != null) {
            boolean isOverClose = mouseX >= closeButton.getX() &&
                                 mouseX < closeButton.getX() + closeButton.getWidth() &&
                                 mouseY >= closeButton.getY() &&
                                 mouseY < closeButton.getY() + closeButton.getHeight();
            if (isOverClose) {
                onClose.run();
                return true;
            }
        }

        // Check navigation buttons if carousel is active
        if (totalImages > 1) {
            // Check previous button
            if (prevButton != null) {
                boolean isOverPrev = mouseX >= prevButton.getX() &&
                                    mouseX < prevButton.getX() + prevButton.getWidth() &&
                                    mouseY >= prevButton.getY() &&
                                    mouseY < prevButton.getY() + prevButton.getHeight();
                if (isOverPrev) {
                    onPrevious.run();
                    return true;
                }
            }

            // Check next button
            if (nextButton != null) {
                boolean isOverNext = mouseX >= nextButton.getX() &&
                                    mouseX < nextButton.getX() + nextButton.getWidth() &&
                                    mouseY >= nextButton.getY() &&
                                    mouseY < nextButton.getY() + nextButton.getHeight();
                if (isOverNext) {
                    onNext.run();
                    return true;
                }
            }
        }

        // Click anywhere else closes the viewer
        onClose.run();
        return true;
    }


    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // No special handling needed
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC key closes the viewer
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            onClose.run();
            return true;
        }

        // Arrow key navigation for carousel
        if (totalImages > 1) {
            if (keyCode == 263) { // GLFW_KEY_LEFT
                onPrevious.run();
                return true;
            } else if (keyCode == 262) { // GLFW_KEY_RIGHT
                onNext.run();
                return true;
            }
        }

        return false;
    }
}


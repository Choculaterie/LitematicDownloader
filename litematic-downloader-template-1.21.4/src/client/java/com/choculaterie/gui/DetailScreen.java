package com.choculaterie.gui;

import com.choculaterie.models.SchematicDetailInfo;
import com.choculaterie.networking.LitematicHttpClient;
import com.choculaterie.gui.ToastManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.choculaterie.config.SettingsManager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class DetailScreen extends Screen {
    private final String schematicId;
    private SchematicDetailInfo schematicDetail;
    private boolean isLoading = true;
    private String errorMessage = null;
    private Identifier coverImageTexture = null;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private int descriptionScrollPos = 0; // Tracks the scroll position of the description
    private ButtonWidget backButton;
    private ButtonWidget downloadButton;
    private Screen confirmationScreen = null;

    private long loadingStartTime = 0;

    // Add reference to the cache manager
    private static CacheManager cacheManager = LitematicDownloaderScreen.getCacheManager();
    private static final long DETAIL_CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    public DetailScreen(String schematicId) {
        super(Text.literal(""));
        this.schematicId = schematicId;
    }

    @Override
    protected void init() {
        super.init();

        // Add a back button with arrow in the top left corner
        this.backButton = ButtonWidget.builder(Text.literal("←"), button -> {
            // Check if we have saved navigation state to restore
            NavigationState navState = NavigationState.getInstance();
            if (navState.getSavedCurrentPage() > 0) {
                // Create a new LitematicDownloaderScreen with state restoration flag
                LitematicDownloaderScreen restoredScreen = new LitematicDownloaderScreen(true);
                MinecraftClient.getInstance().setScreen(restoredScreen);
            } else {
                // Fallback to normal screen if no state saved
                MinecraftClient.getInstance().setScreen(new LitematicDownloaderScreen());
            }
        }).dimensions(10, 10, 20, 20).build();
        this.addDrawableChild(this.backButton);

        // Calculate initial position for download button
        int padding = 20;
        int topMargin = 40;
        int leftSectionWidth = 256; // Image width

        // Place download button in top right of the cover image area
        this.downloadButton = ButtonWidget.builder(Text.literal("⬇"), button -> {
            if (schematicDetail != null) {
                try {
                    String fileName = schematicDetail.getName().replaceAll("[^a-zA-Z0-9.-]", "_");

                    // Get file path using SettingsManager
                    String savePath = SettingsManager.getSchematicsPath() + File.separator;
                    File potentialFile = new File(savePath + fileName + ".litematic");

                    if (potentialFile.exists()) {
                        // Show confirmation dialog
                        this.confirmationScreen = new ConfirmationScreen(
                                Text.literal("File already exists"),
                                Text.literal("The file \"" + fileName + ".litematic\" already exists. Do you want to replace it?"),
                                (confirmed) -> {
                                    if (confirmed) {
                                        downloadSchematic(fileName);
                                    }
                                    this.confirmationScreen = null;
                                }
                        );
                        MinecraftClient.getInstance().setScreen(this.confirmationScreen);
                    } else {
                        // No existing file, download directly
                        downloadSchematic(fileName);
                    }
                } catch (Exception e) {
                    this.setDownloadStatus("Failed to download schematic: " + e.getMessage(), false);
                }
            }
        }).dimensions(padding + leftSectionWidth - 30, topMargin, 20, 20).build();

        this.downloadButton.active = false; // Disable until data is loaded
        this.addDrawableChild(this.downloadButton);

        System.out.println("Loading schematic details for ID: " + schematicId);

        errorMessage = null;
        loadSchematicDetails();
    }

    private void loadSchematicDetails() {
        // Check if we have valid cached detail data
        if (cacheManager.hasValidDetailCache(schematicId, DETAIL_CACHE_DURATION_MS)) {
            CacheManager.DetailCacheEntry cachedDetail = cacheManager.getDetailCache(schematicId);
            schematicDetail = cachedDetail.getDetail();
            isLoading = false;

            // Enable download button since data is loaded
            this.downloadButton.active = true;

            // Load the cover image if available
            if (schematicDetail != null && schematicDetail.getCoverPicture() != null
                    && !schematicDetail.getCoverPicture().isEmpty()) {
                System.out.println("Cover image available (from cache), loading...");
                loadCoverImage(schematicDetail.getCoverPicture());
            }

            System.out.println("Loaded schematic details from cache for ID: " + schematicId);
            return;
        }

        // No valid cache, fetch from server
        isLoading = true;
        loadingStartTime = System.currentTimeMillis();

        // Fetch schematic details in a separate thread
        new Thread(() -> {
            try {
                SchematicDetailInfo fetchedDetail = LitematicHttpClient.fetchSchematicDetail(schematicId);
                System.out.println("Fetched schematic details from server: " + (fetchedDetail != null));

                MinecraftClient.getInstance().execute(() -> {
                    schematicDetail = fetchedDetail;
                    isLoading = false;

                    // Cache the fetched detail
                    if (schematicDetail != null) {
                        cacheManager.putDetailCache(schematicId, schematicDetail, DETAIL_CACHE_DURATION_MS);
                    }

                    // Enable download button now that data is loaded
                    this.downloadButton.active = true;

                    // Load the cover image if available
                    if (schematicDetail != null && schematicDetail.getCoverPicture() != null
                            && !schematicDetail.getCoverPicture().isEmpty()) {
                        System.out.println("Cover image available, loading...");
                        loadCoverImage(schematicDetail.getCoverPicture());
                    } else {
                        System.out.println("No cover image available");
                    }
                });
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() -> {
                    errorMessage = "Failed to load schematic details: " + e.getMessage();
                    isLoading = false;
                    System.err.println("Error loading schematic details: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        }).start();
    }

    private void drawLoadingAnimation(DrawContext context, int centerX, int centerY) {
        int radius = 12;
        int segments = 8;
        int animationDuration = 1600; // Full rotation time in ms

        // Calculate current angle based on time
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - loadingStartTime;
        float rotation = (elapsedTime % animationDuration) / (float) animationDuration;

        // Draw each segment with fading color
        for (int i = 0; i < segments; i++) {
            float angle = (float) (i * 2 * Math.PI / segments);
            angle += rotation * 2 * Math.PI; // Rotate based on time

            int x1 = centerX + (int)(Math.sin(angle) * (radius - 3));
            int y1 = centerY + (int)(Math.cos(angle) * (radius - 3));
            int x2 = centerX + (int)(Math.sin(angle) * radius);
            int y2 = centerY + (int)(Math.cos(angle) * radius);

            // Calculate color intensity based on position
            int alpha = 255 - (i * 255 / segments);
            int color = 0xFFFFFF | (alpha << 24);

            // Use fill to simulate a line
            context.fill(x1, y1, x2 + 1, y2 + 1, color);
        }
    }

    // Add this helper method to handle the actual download
    private void downloadSchematic(String fileName) {
        try {
            String filePath = LitematicHttpClient.fetchAndDownloadSchematic(
                    schematicDetail.getId(),
                    fileName
            );
            // Show relative path from schematics base folder with forward slashes
            String schematicsPath = SettingsManager.getSchematicsPath();
            String relativePath;
            if (filePath.startsWith(schematicsPath)) {
                String pathAfterBase = filePath.substring(schematicsPath.length());
                // Remove leading separator if present
                if (pathAfterBase.startsWith(File.separator)) {
                    pathAfterBase = pathAfterBase.substring(File.separator.length());
                }
                // Use the folder name from the settings instead of hardcoding "schematics"
                String folderName = new File(schematicsPath).getName();
                relativePath = folderName + "/" + pathAfterBase.replace(File.separator, "/");
            } else {
                // Fallback - just show filename
                String folderName = new File(schematicsPath).getName();
                relativePath = folderName + "/" + fileName + ".litematic";
            }
            this.setDownloadStatus("Schematic downloaded to: " + relativePath, true);
        } catch (Exception e) {
            this.setDownloadStatus("Failed to download schematic: " + e.getMessage(), false);
        }
    }

    private void loadCoverImage(String base64Image) {
        try {
            // Remove data URI prefix if present (e.g., "data:image/png;base64,")
            String base64Data = base64Image;
            String detectedFormat = "unknown";

            // Extract format from data URI if present
            if (base64Data.contains(",")) {
                String prefix = base64Data.substring(0, base64Data.indexOf(","));
                if (prefix.contains("image/")) {
                    detectedFormat = prefix.substring(prefix.indexOf("image/") + 6);
                    if (detectedFormat.contains(";")) {
                        detectedFormat = detectedFormat.substring(0, detectedFormat.indexOf(";"));
                    }
                }
                base64Data = base64Data.split(",")[1];
            }

            // Validate base64 string before decoding
            if (base64Data == null || base64Data.trim().isEmpty()) {
                System.err.println("Base64 image data is empty");
                ToastManager.addToast("Image data is empty", true);
                coverImageTexture = null;
                return;
            }

            // Decode the base64 string to binary data
            byte[] imageData;
            try {
                imageData = Base64.getDecoder().decode(base64Data);
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid base64 image data: " + e.getMessage());
                ToastManager.addToast("Invalid image data format", true);
                coverImageTexture = null;
                return;
            }

            // Validate that we have actual image data
            if (imageData.length == 0) {
                System.err.println("Decoded image data is empty");
                ToastManager.addToast("Image data is empty", true);
                coverImageTexture = null;
                return;
            }

            // Check for minimum file size (corrupted images are often very small)
            if (imageData.length < 100) {
                System.err.println("Image data too small, likely corrupted: " + imageData.length + " bytes");
                ToastManager.addToast("Image file too small (corrupted)", true);
                createAndRegisterPlaceholder("Image data too small (corrupted)");
                return;
            }

            // Detect image format from binary signature if not detected from data URI
            if (detectedFormat.equals("unknown")) {
                detectedFormat = detectImageFormat(imageData);
            }

            System.out.println("Detected image format: " + detectedFormat);

            // Generate a unique identifier for this texture
            String uniqueId = UUID.randomUUID().toString().replace("-", "");
            coverImageTexture = Identifier.of("minecraft", "textures/dynamic/" + uniqueId);

            // Try to read the image and handle potential format exceptions
            NativeImage nativeImage;
            boolean isPlaceholder = false;
            String placeholderReason = "";

            try {
                // Try to convert different formats to PNG first if needed
                byte[] processedImageData = convertImageToPng(imageData, detectedFormat);
                nativeImage = NativeImage.read(new ByteArrayInputStream(processedImageData));

                // Additional validation: check if image dimensions are reasonable
                if (nativeImage.getWidth() <= 0 || nativeImage.getHeight() <= 0) {
                    System.err.println("Invalid image dimensions: " + nativeImage.getWidth() + "x" + nativeImage.getHeight());
                    ToastManager.addToast("Image has invalid dimensions", true);
                    nativeImage.close();
                    nativeImage = createPlaceholderImage(256, 256, "Invalid dimensions");
                    isPlaceholder = true;
                    placeholderReason = "Invalid image dimensions";
                } else if (nativeImage.getWidth() > 4096 || nativeImage.getHeight() > 4096) {
                    System.err.println("Image too large: " + nativeImage.getWidth() + "x" + nativeImage.getHeight());
                    ToastManager.addToast("Image too large (" + nativeImage.getWidth() + "x" + nativeImage.getHeight() + ")", true);
                    nativeImage.close();
                    nativeImage = createPlaceholderImage(256, 256, "Image too large");
                    isPlaceholder = true;
                    placeholderReason = "Image too large (" + nativeImage.getWidth() + "x" + nativeImage.getHeight() + ")";
                }
            } catch (Exception e) {
                System.err.println("Error loading image (corrupted or unsupported format '" + detectedFormat + "'): " + e.getMessage());
                ToastManager.addToast("Could not load " + detectedFormat + " image: " + getSimpleErrorMessage(e.getMessage()), true);
                nativeImage = createPlaceholderImage(256, 256, "Image corrupted");
                isPlaceholder = true;
                placeholderReason = "Image corrupted (" + e.getMessage() + ")";
            }

            // Store image dimensions and register texture
            if (nativeImage != null) {
                imageWidth = nativeImage.getWidth();
                imageHeight = nativeImage.getHeight();

                // Register the texture with Minecraft's texture manager
                MinecraftClient.getInstance().getTextureManager().registerTexture(
                        coverImageTexture,
                        new NativeImageBackedTexture(nativeImage)
                );

                if (isPlaceholder) {
                    System.out.println("Placeholder image created due to: " + placeholderReason + " (" + imageWidth + "x" + imageHeight + ")");
                } else {
                    System.out.println("Cover image loaded successfully (" + detectedFormat + "): " + imageWidth + "x" + imageHeight);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load cover image: " + e.getMessage());
            ToastManager.addToast("Failed to load image", true);
            e.printStackTrace();
            coverImageTexture = null;
        }
    }

    // Helper method to detect image format from binary signature
    private String detectImageFormat(byte[] imageData) {
        if (imageData.length < 8) {
            return "unknown";
        }

        // PNG signature: 89 50 4E 47 0D 0A 1A 0A
        if (imageData[0] == (byte)0x89 && imageData[1] == 0x50 &&
            imageData[2] == 0x4E && imageData[3] == 0x47) {
            return "png";
        }

        // JPEG signature: FF D8 FF
        if (imageData[0] == (byte)0xFF && imageData[1] == (byte)0xD8 && imageData[2] == (byte)0xFF) {
            return "jpeg";
        }

        // GIF signature: GIF87a or GIF89a
        if (imageData.length >= 6 &&
            imageData[0] == 0x47 && imageData[1] == 0x49 && imageData[2] == 0x46) {
            return "gif";
        }

        // BMP signature: BM
        if (imageData[0] == 0x42 && imageData[1] == 0x4D) {
            return "bmp";
        }

        // WebP signature: RIFF....WEBP
        if (imageData.length >= 12 &&
            imageData[0] == 0x52 && imageData[1] == 0x49 && imageData[2] == 0x46 && imageData[3] == 0x46 &&
            imageData[8] == 0x57 && imageData[9] == 0x45 && imageData[10] == 0x42 && imageData[11] == 0x50) {
            return "webp";
        }

        return "unknown";
    }

    // Helper method to convert different image formats to PNG-compatible format
    private byte[] convertImageToPng(byte[] imageData, String format) throws Exception {
        // If it's already PNG or unknown, try as-is first
        if (format.equals("png") || format.equals("unknown")) {
            return imageData;
        }

        // For non-PNG formats, we'll try to use Java's built-in image processing
        try {
            java.awt.image.BufferedImage bufferedImage = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageData));

            if (bufferedImage == null) {
                throw new Exception("Could not decode " + format + " image");
            }

            // Convert to PNG format
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedImage, "PNG", baos);

            System.out.println("Successfully converted " + format + " to PNG format");
            return baos.toByteArray();

        } catch (Exception e) {
            System.err.println("Failed to convert " + format + " image: " + e.getMessage());
            // Fallback: try the original data anyway
            return imageData;
        }
    }

    // Helper method to simplify error messages for user display
    private String getSimpleErrorMessage(String fullError) {
        if (fullError.contains("Bad PNG Signature")) {
            return "Corrupted PNG file";
        } else if (fullError.contains("JPEG")) {
            return "JPEG format issue";
        } else if (fullError.contains("format")) {
            return "Unsupported format";
        } else if (fullError.contains("signature") || fullError.contains("Signature")) {
            return "Corrupted file";
        } else {
            // Keep it short for toast display
            return fullError.length() > 30 ? "Image corrupted" : fullError;
        }
    }

    // Helper method to create and register placeholder when image loading fails early
    private void createAndRegisterPlaceholder(String reason) {
        try {
            String uniqueId = UUID.randomUUID().toString().replace("-", "");
            coverImageTexture = Identifier.of("minecraft", "textures/dynamic/" + uniqueId);

            NativeImage nativeImage = createPlaceholderImage(256, 256, reason);
            if (nativeImage != null) {
                imageWidth = nativeImage.getWidth();
                imageHeight = nativeImage.getHeight();

                // Register the texture with Minecraft's texture manager
                MinecraftClient.getInstance().getTextureManager().registerTexture(
                        coverImageTexture,
                        new NativeImageBackedTexture(nativeImage)
                );

                System.out.println("Placeholder image created due to: " + reason + " (" + imageWidth + "x" + imageHeight + ")");
            }
        } catch (Exception e) {
            System.err.println("Failed to create placeholder: " + e.getMessage());
            coverImageTexture = null;
        }
    }

    // Helper method to create a placeholder image when original image loading fails
    private NativeImage createPlaceholderImage(int width, int height, String reason) {
        try {
            NativeImage placeholder = new NativeImage(NativeImage.Format.RGBA, width, height, false);

            // Since we can't easily manipulate pixels without access to setColor methods,
            // we'll create a basic transparent placeholder that won't crash the game
            // The placeholder will appear as a transparent square, which is better than crashing

            return placeholder;
        } catch (Exception e) {
            System.err.println("Failed to create placeholder image: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Unified method for both success and error messages
    public void setDownloadStatus(String message, boolean isSuccess) {
        ToastManager.addToast(message, !isSuccess);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep the blur background
        this.renderBackground(context, mouseX, mouseY, delta);

        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

        // Draw widgets (including buttons) - except for our custom positioned buttons
        // Save references to our special buttons so we can render them separately
        for (Element child : this.children()) {
            if (child instanceof ButtonWidget) {
                ButtonWidget button = (ButtonWidget) child;
                // Skip the back and download buttons - we'll render them later
                if (button == this.backButton || button == this.downloadButton) {
                    continue;
                }
            }

            if (child instanceof Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }

        // Handle loading state and content rendering
        if (isLoading) {
            // Draw loading animation centered in the screen
            int centerY = this.height / 2;
            drawLoadingAnimation(context, this.width / 2, centerY - 15);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Loading..."),
                    this.width / 2, centerY + 15, 0xCCCCCC);
        }  else if (errorMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(errorMessage),
                    this.width / 2, this.height / 2, 0xFF0000);
        } else if (schematicDetail != null) {
            // Define layout dimensions
            int padding = 20;
            int leftSectionWidth = 256; // Image width
            int rightSectionX = leftSectionWidth + padding * 2;
            int contentWidth = this.width - rightSectionX - padding;
            int topMargin = 40;

            // Draw the image on the left
            context.drawTexture(
                    RenderLayer::getGuiTextured,
                    coverImageTexture,
                    padding,
                    topMargin,
                    0, 0,
                    leftSectionWidth,
                    leftSectionWidth,
                    leftSectionWidth,
                    leftSectionWidth
            );

            // Draw information on the right
            int y = topMargin;

            // Title (name)
            String name = schematicDetail.getName();
            if (name != null) {
                context.drawText(this.textRenderer, name, rightSectionX, y, 0xFFFFFF, true);
                y += 15;
            }

            // Author
            String username = schematicDetail.getUsername();
            if (username != null) {
                context.drawText(this.textRenderer, "By: " + username, rightSectionX, y, 0xCCCCCC, false);
                y += 15;
            }

            // Published date
            String publishDate = schematicDetail.getPublishDate();
            if (publishDate != null) {
                String formattedDate = publishDate.split("T")[0];
                context.drawText(this.textRenderer, "Published: " + formattedDate, rightSectionX, y, 0xCCCCCC, false);
                y += 20;
            }

            // Stats (views, downloads, likes) - horizontal arrangement
            int statsY = y;
            int statsSpacing = contentWidth / 3;

            context.drawText(this.textRenderer, "Views: " + schematicDetail.getViewCount(),
                    rightSectionX, statsY, 0xFFFFFF, false);

            context.drawText(this.textRenderer, "Downloads: " + schematicDetail.getDownloadCount(),
                    rightSectionX + statsSpacing, statsY, 0xFFFFFF, false);

            y += 25;

            // Description header
            context.drawText(this.textRenderer, "Description:", rightSectionX, y, 0xFFFFFF, false);
            y += 15;

            // Store scroll area parameters for mouse handling
            this.scrollAreaX = rightSectionX;
            this.scrollAreaY = y;
            int descBoxWidth;
            this.scrollAreaWidth = descBoxWidth = contentWidth;
            int descBoxHeight;
            this.scrollAreaHeight = descBoxHeight = this.height - y - padding;

            // Description with scrolling
            String description = schematicDetail.getDescription() != null && !schematicDetail.getDescription().isEmpty()
                    ? schematicDetail.getDescription() : "No description available";

            // Use scissor to create a clipping region for scrolling
            context.enableScissor(
                    this.scrollAreaX,
                    this.scrollAreaY,
                    this.scrollAreaX + this.scrollAreaWidth,
                    this.scrollAreaY + this.scrollAreaHeight
            );

            // For simplicity, let's assume descriptionScrollPos is a class member that tracks scroll position
            int textY = this.scrollAreaY - descriptionScrollPos;

            // Draw description text, potentially line-wrapped
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(description), this.scrollAreaWidth);
            this.totalContentHeight = lines.size() * 10;

            for (OrderedText line : lines) {
                context.drawText(this.textRenderer, line, this.scrollAreaX, textY, 0xFFFFFF, false);
                textY += 10;
            }

            context.disableScissor();

            // Draw a scroll bar if needed
            if (this.totalContentHeight > this.scrollAreaHeight) {
                int scrollBarWidth = 6;
                this.scrollBarHeight = Math.max(20, this.scrollAreaHeight * this.scrollAreaHeight / this.totalContentHeight);
                this.scrollBarX = this.scrollAreaX + this.scrollAreaWidth - scrollBarWidth - 2;
                this.scrollBarY = this.scrollAreaY + (int)((float)descriptionScrollPos / (this.totalContentHeight - this.scrollAreaHeight)
                        * (this.scrollAreaHeight - this.scrollBarHeight));

                // Draw scroll bar background
                context.fill(this.scrollBarX, this.scrollAreaY,
                        this.scrollBarX + scrollBarWidth, this.scrollAreaY + this.scrollAreaHeight,
                        0x33FFFFFF);

                // Draw scroll bar handle with hover effect
                boolean isHovering = mouseX >= this.scrollBarX && mouseX <= this.scrollBarX + scrollBarWidth &&
                        mouseY >= this.scrollBarY && mouseY <= this.scrollBarY + this.scrollBarHeight;

                int scrollBarColor = isHovering || isScrolling ? 0xFFFFFFFF : 0xAAFFFFFF;
                context.fill(this.scrollBarX, this.scrollBarY,
                        this.scrollBarX + scrollBarWidth, this.scrollBarY + this.scrollBarHeight,
                        scrollBarColor);
            }
        }

        // Position back button in the top left corner
        if (backButton != null) {
            backButton.render(context, mouseX, mouseY, delta);
        }

        // Render the download button
        if (downloadButton != null) {
            downloadButton.render(context, mouseX, mouseY, delta);
        }

        ToastManager.render(context, this.width);
    }

    // Add these fields to your class
    private int scrollAreaX;
    private int scrollAreaY;
    private int scrollAreaWidth;
    private int scrollAreaHeight;
    private int scrollBarX;
    private int scrollBarY;
    private int scrollBarHeight;
    private int totalContentHeight;
    private boolean isScrolling = false;
    private int lastMouseY;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.totalContentHeight > this.scrollAreaHeight) { // Left click
            // Check if click is on scroll bar
            if (mouseX >= this.scrollBarX && mouseX <= this.scrollBarX + 6 &&
                    mouseY >= this.scrollBarY && mouseY <= this.scrollBarY + this.scrollBarHeight) {

                this.isScrolling = true;
                this.lastMouseY = (int) mouseY;
                return true;
            }

            // Check if click is in scroll area but not on the handle (jump scroll)
            if (mouseX >= this.scrollBarX && mouseX <= this.scrollBarX + 6 &&
                    mouseY >= this.scrollAreaY && mouseY <= this.scrollAreaY + this.scrollAreaHeight) {

                // Calculate new scroll position based on click location
                float clickPercent = ((float)mouseY - this.scrollAreaY) / this.scrollAreaHeight;
                this.descriptionScrollPos = (int)(clickPercent * (this.totalContentHeight - this.scrollAreaHeight));
                this.descriptionScrollPos = Math.max(0, Math.min(this.totalContentHeight - this.scrollAreaHeight, this.descriptionScrollPos));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isScrolling) {
            if (mouseY != this.lastMouseY) {
                // Calculate how far we've dragged as a percentage of the scroll area
                float dragPercentage = (float)(mouseY - this.lastMouseY) / (this.scrollAreaHeight - this.scrollBarHeight);

                // Convert that to a scroll amount
                int scrollAmount = (int)(dragPercentage * (this.totalContentHeight - this.scrollAreaHeight));

                // Update scroll position
                this.descriptionScrollPos = Math.max(0, Math.min(this.totalContentHeight - this.scrollAreaHeight,
                        this.descriptionScrollPos + scrollAmount));

                this.lastMouseY = (int) mouseY;
            }
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isScrolling) {
            this.isScrolling = false;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Only handle scrolling if mouse is in the scroll area
        if (mouseX >= this.scrollAreaX && mouseX <= this.scrollAreaX + this.scrollAreaWidth &&
                mouseY >= this.scrollAreaY && mouseY <= this.scrollAreaY + this.scrollAreaHeight) {

            if (this.totalContentHeight > this.scrollAreaHeight) {
                // Calculate scroll amount (10 pixels per mouse wheel tick)
                int scrollAmount = (int)(-verticalAmount * 20);

                // Update scroll position
                this.descriptionScrollPos = Math.max(0, Math.min(this.totalContentHeight - this.scrollAreaHeight,
                        this.descriptionScrollPos + scrollAmount));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}


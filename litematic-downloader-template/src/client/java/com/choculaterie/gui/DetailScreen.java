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
import net.minecraft.client.gl.RenderPipelines;
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
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        }).dimensions(this.width - 30, 10, 20, 20).build();

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

    private void loadCoverImage(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            System.out.println("No cover image URL provided");
            coverImageTexture = null;
            return;
        }

        System.out.println("Loading cover image from URL: " + imageUrl);

        // Load image from URL in background thread
        new Thread(() -> {
            try {
                // Encode the URL to handle spaces and special characters
                String encodedUrl = encodeImageUrl(imageUrl);
                System.out.println("Encoded URL: " + encodedUrl);

                // Create HTTP request to download the image
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(encodedUrl))
                        .GET()
                        .build();

                HttpResponse<byte[]> response = LitematicHttpClient.getClient().send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() != 200) {
                    System.err.println("Failed to load image from URL: " + response.statusCode());
                    MinecraftClient.getInstance().execute(() -> {
                        ToastManager.addToast("Failed to load image: HTTP " + response.statusCode(), true);
                        createAndRegisterPlaceholder("HTTP " + response.statusCode());
                    });
                    return;
                }

                byte[] imageData = response.body();

                // Validate that we have actual image data
                if (imageData.length == 0) {
                    System.err.println("Downloaded image data is empty");
                    MinecraftClient.getInstance().execute(() -> {
                        ToastManager.addToast("Image data is empty", true);
                        createAndRegisterPlaceholder("Image data is empty");
                    });
                    return;
                }

                // Check for minimum file size (corrupted images are often very small)
                if (imageData.length < 100) {
                    System.err.println("Image data too small, likely corrupted: " + imageData.length + " bytes");
                    MinecraftClient.getInstance().execute(() -> {
                        ToastManager.addToast("Image file too small (corrupted)", true);
                        createAndRegisterPlaceholder("Image data too small (corrupted)");
                    });
                    return;
                }

                // Detect image format from binary signature
                String detectedFormat = detectImageFormat(imageData);
                System.out.println("Detected image format: " + detectedFormat);

                // Process on main thread
                MinecraftClient.getInstance().execute(() -> {
                    try {
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
                                    new NativeImageBackedTexture(() -> "cover_image", nativeImage)
                            );

                            if (isPlaceholder) {
                                System.out.println("Placeholder image created due to: " + placeholderReason + " (" + imageWidth + "x" + imageHeight + ")");
                            } else {
                                System.out.println("Cover image loaded successfully (" + detectedFormat + "): " + imageWidth + "x" + imageHeight);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to process downloaded image: " + e.getMessage());
                        ToastManager.addToast("Failed to process image", true);
                        e.printStackTrace();
                        coverImageTexture = null;
                    }
                });

            } catch (Exception e) {
                System.err.println("Failed to download cover image: " + e.getMessage());
                MinecraftClient.getInstance().execute(() -> {
                    ToastManager.addToast("Failed to load image", true);
                    createAndRegisterPlaceholder("Download failed");
                });
                e.printStackTrace();
            }
        }).start();
    }

    // Helper method to detect image format from binary signature
    private String detectImageFormat(byte[] imageData) {
        if (imageData.length < 12) {
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

        // TIFF signature: II* (little endian) or MM* (big endian)
        if (imageData.length >= 4 &&
            ((imageData[0] == 0x49 && imageData[1] == 0x49 && imageData[2] == 0x2A && imageData[3] == 0x00) ||
             (imageData[0] == 0x4D && imageData[1] == 0x4D && imageData[2] == 0x00 && imageData[3] == 0x2A))) {
            return "tiff";
        }

        // ICO signature: 00 00 01 00
        if (imageData.length >= 4 &&
            imageData[0] == 0x00 && imageData[1] == 0x00 && imageData[2] == 0x01 && imageData[3] == 0x00) {
            return "ico";
        }

        // AVIF signature: ....ftypavif or ....ftypavis
        if (imageData.length >= 12 &&
            imageData[4] == 0x66 && imageData[5] == 0x74 && imageData[6] == 0x79 && imageData[7] == 0x70 &&
            imageData[8] == 0x61 && imageData[9] == 0x76 && imageData[10] == 0x69 &&
            (imageData[11] == 0x66 || imageData[11] == 0x73)) {
            return "avif";
        }

        // HEIF/HEIC signature: ....ftypheic or ....ftypmif1
        if (imageData.length >= 12 &&
            imageData[4] == 0x66 && imageData[5] == 0x74 && imageData[6] == 0x79 && imageData[7] == 0x70) {
            if ((imageData[8] == 0x68 && imageData[9] == 0x65 && imageData[10] == 0x69 && imageData[11] == 0x63) ||
                (imageData[8] == 0x6D && imageData[9] == 0x69 && imageData[10] == 0x66 && imageData[11] == 0x31)) {
                return "heif";
            }
        }

        // SVG signature: <svg or <?xml (for XML-based SVG)
        if (imageData.length >= 5) {
            String startString = new String(imageData, 0, Math.min(100, imageData.length)).toLowerCase();
            if (startString.contains("<svg") || (startString.startsWith("<?xml") && startString.contains("svg"))) {
                return "svg";
            }
        }

        return "unknown";
    }

    // Helper method to convert different image formats to PNG-compatible format
    private byte[] convertImageToPng(byte[] imageData, String format) throws Exception {
        // If it's already PNG or unknown, try as-is first
        if (format.equals("png") || format.equals("unknown")) {
            return imageData;
        }

        // Special handling for SVG - cannot be converted via standard Java ImageIO
        if (format.equals("svg")) {
            throw new Exception("SVG format is not supported for conversion");
        }

        // Special handling for AVIF and HEIF - not supported by standard Java ImageIO
        if (format.equals("avif") || format.equals("heif")) {
            throw new Exception(format.toUpperCase() + " format is not supported by Java ImageIO");
        }

        // For supported formats, try to use Java's built-in image processing
        try {
            // Try multiple readers for better format support
            java.awt.image.BufferedImage bufferedImage = null;

            // First try standard ImageIO
            try {
                bufferedImage = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageData));
            } catch (Exception e) {
                System.out.println("Standard ImageIO failed for " + format + ", trying alternative approach");
            }

            // If standard ImageIO failed, try with explicit format readers
            if (bufferedImage == null && !format.equals("unknown")) {
                try {
                    java.util.Iterator<javax.imageio.ImageReader> readers =
                        javax.imageio.ImageIO.getImageReadersByFormatName(format.toUpperCase());

                    if (readers.hasNext()) {
                        javax.imageio.ImageReader reader = readers.next();
                        javax.imageio.stream.ImageInputStream iis =
                            javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(imageData));
                        reader.setInput(iis);
                        bufferedImage = reader.read(0);
                        reader.dispose();
                        iis.close();
                    }
                } catch (Exception e) {
                    System.out.println("Alternative ImageIO approach failed for " + format + ": " + e.getMessage());
                }
            }

            if (bufferedImage == null) {
                throw new Exception("Could not decode " + format + " image with any available reader");
            }

            // Ensure we have RGB/RGBA format for PNG conversion
            java.awt.image.BufferedImage convertedImage;
            if (bufferedImage.getType() != java.awt.image.BufferedImage.TYPE_INT_RGB &&
                bufferedImage.getType() != java.awt.image.BufferedImage.TYPE_INT_ARGB) {

                // Convert to ARGB for maximum compatibility
                convertedImage = new java.awt.image.BufferedImage(
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB
                );

                java.awt.Graphics2D g2d = convertedImage.createGraphics();
                g2d.drawImage(bufferedImage, 0, 0, null);
                g2d.dispose();
            } else {
                convertedImage = bufferedImage;
            }

            // Convert to PNG format
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            boolean success = javax.imageio.ImageIO.write(convertedImage, "PNG", baos);

            if (!success) {
                throw new Exception("Failed to write PNG output");
            }

            System.out.println("Successfully converted " + format + " to PNG format (" +
                              bufferedImage.getWidth() + "x" + bufferedImage.getHeight() + ")");
            return baos.toByteArray();

        } catch (Exception e) {
            System.err.println("Failed to convert " + format + " image: " + e.getMessage());
            // For unsupported formats, throw the exception rather than fallback
            if (format.equals("svg") || format.equals("avif") || format.equals("heif")) {
                throw e;
            }
            // For other formats, try the original data anyway as fallback
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
                        new NativeImageBackedTexture(() -> "cover_image", nativeImage)
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
                    this.width / 2, centerY + 15, 0xFFFFFFFF);
        }  else if (errorMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(errorMessage),
                    this.width / 2, this.height / 2, 0xFFFFFFFF);
        } else if (schematicDetail != null) {
            // Define layout dimensions
            int padding = 20;
            int leftSectionWidth = 256; // Image width
            int rightSectionX = leftSectionWidth + padding * 2;
            int contentWidth = this.width - rightSectionX - padding;
            int topMargin = 40;

            // Draw the image on the left (only if we have a valid texture)
            if (coverImageTexture != null) {
                context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        coverImageTexture,
                        padding,
                        topMargin,
                        0, 0,
                        leftSectionWidth,
                        leftSectionWidth,
                        leftSectionWidth,
                        leftSectionWidth
                );
            } else {
                // Draw a placeholder rectangle when no image is available
                context.fill(
                        padding,
                        topMargin,
                        padding + leftSectionWidth,
                        topMargin + leftSectionWidth,
                        0x33FFFFFF
                );

                // Draw "No Image" text in the center of the placeholder
                String noImageText = "No Image";
                int textWidth = this.textRenderer.getWidth(noImageText);
                int textX = padding + (leftSectionWidth - textWidth) / 2;
                int textY = topMargin + (leftSectionWidth / 2) - 4;
                context.drawText(this.textRenderer, noImageText, textX, textY, 0xFFAAAAAA, false);
            }

            // Draw information on the right
            int y = topMargin;

            // Title (name)
            String name = schematicDetail.getName();
            if (name != null) {
                context.drawText(this.textRenderer, name, rightSectionX, y, 0xFFFFFFFF, true);
                y += 15;
            }

            // Author
            String username = schematicDetail.getUsername();
            if (username != null) {
                context.drawText(this.textRenderer, "By: " + username, rightSectionX, y, 0xFFD5D5D5, false);
                y += 15;
            }

            // Published date
            String publishDate = schematicDetail.getPublishDate();
            if (publishDate != null) {
                String formattedDate = publishDate.split("T")[0];
                context.drawText(this.textRenderer, "Published: " + formattedDate, rightSectionX, y, 0xFFD5D5D5, false);
                y += 20;
            }

            // Stats (views, downloads, likes) - horizontal arrangement
            int statsY = y;
            int statsSpacing = contentWidth / 3;

            context.drawText(this.textRenderer, "Views: " + schematicDetail.getViewCount(),
                    rightSectionX, statsY, 0xFFD5D5D5, false);

            context.drawText(this.textRenderer, "Downloads: " + schematicDetail.getDownloadCount(),
                    rightSectionX + statsSpacing, statsY, 0xFFD5D5D5, false);

            y += 25;

            // Description header
            context.drawText(this.textRenderer, "Description:", rightSectionX, y, 0xFFFFFFFF, false);
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

            // Clean the description: remove carriage return characters but keep newlines
            description = description.replace("\r", "");
            // Optional: clean up any remaining whitespace issues while preserving line breaks
            description = description.replaceAll("[ \\t]+", " ").trim();

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
                context.drawText(this.textRenderer, line, this.scrollAreaX, textY, 0xFFFFFFFF, false);
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
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button == 0 && this.totalContentHeight > this.scrollAreaHeight) { // Left click
            // Check if click is on scroll bar handle
            if (mouseX >= this.scrollBarX && mouseX <= this.scrollBarX + 6 &&
                    mouseY >= this.scrollBarY && mouseY <= this.scrollBarY + this.scrollBarHeight) {

                this.isScrolling = true;
                this.lastMouseY = (int) mouseY;
                return true;
            }

            // Check if click is on the scroll bar track (jump scroll)
            if (mouseX >= this.scrollBarX && mouseX <= this.scrollBarX + 6 &&
                    mouseY >= this.scrollAreaY && mouseY <= this.scrollAreaY + this.scrollAreaHeight) {

                int denom = Math.max(1, this.scrollAreaHeight);
                float clickPercent = ((float) mouseY - this.scrollAreaY) / denom;
                this.descriptionScrollPos = (int) (clickPercent * (this.totalContentHeight - this.scrollAreaHeight));
                this.descriptionScrollPos = Math.max(0, Math.min(this.totalContentHeight - this.scrollAreaHeight, this.descriptionScrollPos));
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
        double mouseY = click.y();

        if (this.isScrolling) {
            int currentMouseY = (int) mouseY;
            if (currentMouseY != this.lastMouseY) {
                int denom = Math.max(1, this.scrollAreaHeight - this.scrollBarHeight);
                float dragPercentage = (float) (currentMouseY - this.lastMouseY) / denom;
                int scrollAmount = (int) (dragPercentage * (this.totalContentHeight - this.scrollAreaHeight));

                this.descriptionScrollPos = Math.max(
                        0,
                        Math.min(this.totalContentHeight - this.scrollAreaHeight, this.descriptionScrollPos + scrollAmount)
                );

                this.lastMouseY = currentMouseY;
            }
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }


    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button == 0 && this.isScrolling) {
            this.isScrolling = false;
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Only handle scrolling if mouse is in the scroll area
        if (mouseX >= this.scrollAreaX && mouseX <= this.scrollAreaX + this.scrollAreaWidth &&
                mouseY >= this.scrollAreaY && mouseY <= this.scrollAreaY + this.scrollAreaHeight) {

            if (this.totalContentHeight > this.scrollAreaHeight) {
                // Use vertical wheel delta; fall back to horizontal if vertical is zero
                double amount = verticalAmount != 0.0 ? verticalAmount : horizontalAmount;

                // Calculate scroll amount (20 pixels per mouse wheel tick)
                int scrollAmount = (int) (-amount * 20);

                // Update scroll position (clamped)
                this.descriptionScrollPos = Math.max(
                        0,
                        Math.min(this.totalContentHeight - this.scrollAreaHeight, this.descriptionScrollPos + scrollAmount)
                );
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }


    // Helper method to properly encode URLs
    private String encodeImageUrl(String url) {
        try {
            // Parse the URL to separate components
            java.net.URL parsedUrl = new java.net.URL(url);

            // Encode only the path part to preserve the protocol and host
            String encodedPath = java.net.URLEncoder.encode(parsedUrl.getPath(), "UTF-8")
                    .replace("%2F", "/")  // Keep forward slashes as they are path separators
                    .replace("+", "%20"); // Use %20 for spaces instead of +

            // Reconstruct the URL
            return parsedUrl.getProtocol() + "://" + parsedUrl.getHost() +
                   (parsedUrl.getPort() != -1 ? ":" + parsedUrl.getPort() : "") +
                   encodedPath;
        } catch (Exception e) {
            System.err.println("Failed to encode URL, using original: " + e.getMessage());
            // Fallback: manually encode spaces if URL parsing fails
            return url.replace(" ", "%20");
        }
    }
}

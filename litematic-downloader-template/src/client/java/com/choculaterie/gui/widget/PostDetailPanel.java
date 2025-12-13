package com.choculaterie.gui.widget;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.models.MinemevFileInfo;
import com.choculaterie.models.MinemevPostDetailInfo;
import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.network.MinemevNetworkManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Panel widget for displaying post details and images on the right side
 */
public class PostDetailPanel implements Drawable, Element {
    private static final int PANEL_BG_COLOR = 0xFF252525;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int SUBTITLE_COLOR = 0xFFAAAAAA;
    private static final int TAG_BG_COLOR = 0xFF3A3A3A;
    private static final int TAG_TEXT_COLOR = 0xFFCCCCCC;
    private static final int MAX_IMAGE_SIZE = 200;
    private static final int MIN_IMAGE_SIZE = 80;
    private static final int PADDING = 10;

    private int x;
    private int y;
    private int width;
    private int height;

    private MinemevPostInfo postInfo;
    private MinemevPostDetailInfo postDetail;
    private boolean isLoadingDetails = false;
    private boolean isLoadingImage = false;

    private String[] imageUrls;
    private int currentImageIndex = 0;
    private Identifier currentImageTexture;
    private final Map<String, Identifier> imageCache = new ConcurrentHashMap<>();
    private String loadingImageUrl = null;
    private int originalImageWidth = 0;
    private int originalImageHeight = 0;
    private final Map<String, int[]> imageDimensionsCache = new ConcurrentHashMap<>();

    private final MinecraftClient client;
    private double scrollOffset = 0;
    private int contentHeight = 0;

    // Reusable spinner instance
    private final LoadingSpinner imageLoadingSpinner;

    // Carousel navigation buttons
    private CustomButton prevImageButton;
    private CustomButton nextImageButton;

    // Scrollbar for content
    private ScrollBar scrollBar;

    // Download functionality
    private CustomButton downloadButton;
    private DropdownWidget schematicDropdown;
    private MinemevFileInfo[] availableFiles;
    private boolean isLoadingFiles = false;
    private String downloadStatus = "";

    public PostDetailPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = MinecraftClient.getInstance();
        this.imageLoadingSpinner = new LoadingSpinner(0, 0); // Position will be updated in render
        int scrollBarYOffset = 30; // Adjust this value to move scrollbar down
        this.scrollBar = new ScrollBar(x + width - 8, y + scrollBarYOffset, height - scrollBarYOffset);
        this.schematicDropdown = new DropdownWidget(x, y, width - PADDING * 2, this::onSchematicSelected);
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        int scrollBarYOffset = 30; // Adjust this value to move scrollbar down
        this.scrollBar = new ScrollBar(x + width - 8, y + scrollBarYOffset, height - scrollBarYOffset);
        if (schematicDropdown != null) {
            schematicDropdown.setPosition(x + PADDING, y + PADDING);
        }
    }

    /**
     * Calculate display width for current image, respecting original aspect ratio
     * Only uses full width if the image actually needs it
     */
    private int getDisplayImageWidth() {
        int maxWidth = width - PADDING * 2 - 10; // Available width (10 for scrollbar)

        if (originalImageWidth <= 0 || originalImageHeight <= 0) {
            // No image loaded yet, use default square
            return Math.min(MAX_IMAGE_SIZE, maxWidth);
        }

        // Calculate what width we'd need to display at max height
        int widthAtMaxHeight = (int) ((float) originalImageWidth / originalImageHeight * MAX_IMAGE_SIZE);

        // Use the smaller of: original width, calculated width at max height, or max available width
        int desiredWidth = Math.min(originalImageWidth, Math.min(widthAtMaxHeight, maxWidth));

        return Math.max(MIN_IMAGE_SIZE, desiredWidth);
    }

    /**
     * Calculate display height for current image, respecting original aspect ratio
     */
    private int getDisplayImageHeight() {
        int displayWidth = getDisplayImageWidth();

        if (originalImageWidth <= 0 || originalImageHeight <= 0) {
            // No image loaded yet, use default square
            return Math.min(MAX_IMAGE_SIZE, displayWidth);
        }

        // Calculate height based on aspect ratio
        int calculatedHeight = (int) ((float) originalImageHeight / originalImageWidth * displayWidth);

        // Cap at max height
        return Math.min(MAX_IMAGE_SIZE, Math.max(MIN_IMAGE_SIZE, calculatedHeight));
    }

    /**
     * Check if panel is in compact mode (small width)
     */
    private boolean isCompactMode() {
        return width < 200;
    }

    /**
     * Update carousel button positions based on current layout
     */
    private void updateCarouselButtons(int imageNavY) {
        if (imageUrls == null || imageUrls.length <= 1) {
            prevImageButton = null;
            nextImageButton = null;
            return;
        }

        // Responsive button sizes
        boolean compact = isCompactMode();
        int btnWidth = compact ? 18 : 25;
        int btnHeight = compact ? 14 : 16;
        int btnSpacing = compact ? 5 : 10;

        String indicator = String.format("%d / %d", currentImageIndex + 1, imageUrls.length);
        int indicatorWidth = client.textRenderer.getWidth(indicator);
        int indicatorX = x + (width - indicatorWidth) / 2;

        int prevBtnX = indicatorX - btnWidth - btnSpacing;
        int nextBtnX = indicatorX + indicatorWidth + btnSpacing;

        // Create or update previous button
        if (prevImageButton == null) {
            prevImageButton = new CustomButton(prevBtnX, imageNavY, btnWidth, btnHeight,
                net.minecraft.text.Text.literal("<"), btn -> previousImage());
        } else {
            prevImageButton.setX(prevBtnX);
            prevImageButton.setY(imageNavY);
            prevImageButton.setWidth(btnWidth);
        }

        // Create or update next button
        if (nextImageButton == null) {
            nextImageButton = new CustomButton(nextBtnX, imageNavY, btnWidth, btnHeight,
                net.minecraft.text.Text.literal(">"), btn -> nextImage());
        } else {
            nextImageButton.setX(nextBtnX);
            nextImageButton.setY(imageNavY);
            nextImageButton.setWidth(btnWidth);
        }
    }

    public void setPost(MinemevPostInfo post) {
        System.out.println("[PostDetailPanel] setPost called!");
        System.out.println("[PostDetailPanel] Post: " + (post != null ? post.getTitle() : "null"));

        if (post == null) {
            System.out.println("[PostDetailPanel] Post is null, clearing panel");
            clear();
            return;
        }

        // Skip if the same post is already loaded (avoid reload on resize)
        if (this.postInfo != null && post.getUuid() != null && post.getUuid().equals(this.postInfo.getUuid())) {
            System.out.println("[PostDetailPanel] Same post already loaded, skipping reload");
            return;
        }

        System.out.println("[PostDetailPanel] Setting post: " + post.getTitle());
        System.out.println("[PostDetailPanel] UUID: " + post.getUuid());
        System.out.println("[PostDetailPanel] Vendor: " + post.getVendor());

        // Clear download state when switching to a new post
        clearDownloadState();

        this.postInfo = post;
        this.postDetail = null;
        this.isLoadingDetails = true;
        this.currentImageIndex = 0;
        this.currentImageTexture = null;
        this.originalImageWidth = 0;
        this.originalImageHeight = 0;
        this.scrollOffset = 0;

        System.out.println("[PostDetailPanel] Post info set, loading details...");

        // Load post details
        String vendor = post.getVendor() != null ? post.getVendor() : "minemev";
        String uuid = post.getUuid();

        // Strip vendor prefix if present (e.g., "minemev/uuid" -> "uuid")
        if (uuid != null && uuid.contains("/")) {
            String[] parts = uuid.split("/", 2);
            if (parts.length == 2) {
                vendor = parts[0];
                uuid = parts[1];
                System.out.println("[PostDetailPanel] Stripped vendor prefix - vendor: " + vendor + ", uuid: " + uuid);
            }
        }

        MinemevNetworkManager.getPostDetails(vendor, uuid)
            .thenAccept(this::handlePostDetailLoaded)
            .exceptionally(throwable -> {
                if (client != null) {
                    client.execute(() -> {
                        isLoadingDetails = false;
                        System.err.println("[PostDetailPanel] Failed to load post details: " + throwable.getMessage());
                        throwable.printStackTrace();
                    });
                }
                return null;
            });

        // Start loading images from postInfo if available
        if (post.getImages() != null && post.getImages().length > 0) {
            System.out.println("[PostDetailPanel] Found " + post.getImages().length + " images");
            this.imageUrls = post.getImages();
            loadImage(imageUrls[0]);
        } else if (post.getThumbnailUrl() != null && !post.getThumbnailUrl().isEmpty()) {
            System.out.println("[PostDetailPanel] Using thumbnail URL");
            this.imageUrls = new String[]{post.getThumbnailUrl()};
            loadImage(post.getThumbnailUrl());
        } else {
            System.out.println("[PostDetailPanel] No images available");
            this.imageUrls = new String[0];
        }
    }

    private void handlePostDetailLoaded(MinemevPostDetailInfo detail) {
        if (client != null) {
            client.execute(() -> {
                this.postDetail = detail;
                this.isLoadingDetails = false;

                // Update images from detail if available
                if (detail.getImages() != null && detail.getImages().length > 0) {
                    this.imageUrls = detail.getImages();
                    if (currentImageIndex >= imageUrls.length) {
                        currentImageIndex = 0;
                    }
                    // Preload all images
                    preloadImages(imageUrls);
                    // Load current image if not already loaded
                    if (currentImageTexture == null && imageUrls.length > 0) {
                        loadImage(imageUrls[currentImageIndex]);
                    }
                }
            });
        }
    }

    private void preloadImages(String[] urls) {
        for (String url : urls) {
            if (!imageCache.containsKey(url)) {
                new Thread(() -> {
                    try {
                        loadImageSync(url);
                    } catch (Exception e) {
                        System.err.println("Failed to preload image: " + url);
                    }
                }).start();
            }
        }
    }

    private void loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;

        // Check cache first
        if (imageCache.containsKey(imageUrl)) {
            currentImageTexture = imageCache.get(imageUrl);
            // Also retrieve cached dimensions
            int[] dims = imageDimensionsCache.get(imageUrl);
            if (dims != null) {
                originalImageWidth = dims[0];
                originalImageHeight = dims[1];
            }
            isLoadingImage = false;
            return;
        }

        isLoadingImage = true;
        loadingImageUrl = imageUrl;

        new Thread(() -> {
            try {
                Identifier texId = loadImageSync(imageUrl);
                if (client != null) {
                    client.execute(() -> {
                        if (imageUrl.equals(loadingImageUrl)) {
                            currentImageTexture = texId;
                            // Set dimensions from cache
                            int[] dims = imageDimensionsCache.get(imageUrl);
                            if (dims != null) {
                                originalImageWidth = dims[0];
                                originalImageHeight = dims[1];
                            }
                            isLoadingImage = false;
                        }
                    });
                }
            } catch (Exception e) {
                if (client != null) {
                    client.execute(() -> {
                        isLoadingImage = false;
                        System.err.println("Failed to load image: " + e.getMessage());
                    });
                }
            }
        }).start();
    }

    private Identifier loadImageSync(String imageUrl) throws Exception {
        // Check cache
        if (imageCache.containsKey(imageUrl)) {
            return imageCache.get(imageUrl);
        }

        String encodedUrl = encodeImageUrl(imageUrl);

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(encodedUrl))
            .GET()
            .header("User-Agent", "LitematicDownloader/1.0")
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new Exception("HTTP error: " + response.statusCode());
        }

        byte[] imageData = response.body();
        byte[] pngBytes = convertImageToPng(imageData);

        NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(pngBytes));

        // Validate dimensions
        int imgWidth = nativeImage.getWidth();
        int imgHeight = nativeImage.getHeight();

        if (imgWidth <= 0 || imgHeight <= 0) {
            nativeImage.close();
            throw new Exception("Invalid image dimensions");
        }

        if (imgWidth > 4096 || imgHeight > 4096) {
            nativeImage.close();
            throw new Exception("Image too large");
        }

        // Cache the original image dimensions
        imageDimensionsCache.put(imageUrl, new int[]{imgWidth, imgHeight});

        // Register texture on main thread
        final NativeImage finalImage = nativeImage;
        final String uniqueId = UUID.randomUUID().toString().replace("-", "");
        final Identifier texId = Identifier.of("litematicdownloader", "textures/dynamic/" + uniqueId);

        if (client != null) {
            client.execute(() -> {
                client.getTextureManager().registerTexture(
                    texId,
                    new NativeImageBackedTexture(finalImage)
                );
                imageCache.put(imageUrl, texId);
            });
        }

        // Wait a bit for texture registration
        Thread.sleep(50);

        return texId;
    }

    private String encodeImageUrl(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            String encodedPath = path.replace(" ", "%20");
            return uri.getScheme() + "://" + uri.getHost() +
                   (uri.getPort() != -1 ? ":" + uri.getPort() : "") +
                   encodedPath +
                   (uri.getQuery() != null ? "?" + uri.getQuery() : "");
        } catch (Exception e) {
            return url.replace(" ", "%20");
        }
    }

    private byte[] convertImageToPng(byte[] imageData) throws Exception {
        BufferedImage bufferedImage = null;

        // Try with ImageInputStream for auto-detection
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageData))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis);
                    bufferedImage = reader.read(0);
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            // Try fallback
        }

        // Fallback: direct read
        if (bufferedImage == null) {
            bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
        }

        if (bufferedImage == null) {
            throw new Exception("Failed to decode image");
        }

        // Convert to compatible type if needed
        if (bufferedImage.getType() != BufferedImage.TYPE_INT_RGB &&
            bufferedImage.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage converted = new BufferedImage(
                bufferedImage.getWidth(),
                bufferedImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D g2d = converted.createGraphics();
            g2d.drawImage(bufferedImage, 0, 0, null);
            g2d.dispose();
            bufferedImage = converted;
        }

        // Write as PNG
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "PNG", baos);
        return baos.toByteArray();
    }

    public void clear() {
        this.postInfo = null;
        this.postDetail = null;
        this.isLoadingDetails = false;
        this.isLoadingImage = false;
        this.currentImageTexture = null;
        this.originalImageWidth = 0;
        this.originalImageHeight = 0;
        this.imageUrls = null;
        this.currentImageIndex = 0;
        this.scrollOffset = 0;
        clearDownloadState();
    }

    private void clearDownloadState() {
        this.availableFiles = null;
        this.isLoadingFiles = false;
        this.downloadStatus = "";
        if (schematicDropdown != null) {
            schematicDropdown.close();
            schematicDropdown.setStatusMessage("");
        }
    }

    private void onDownloadButtonClick() {
        if (postInfo == null || isLoadingFiles) return;

        // Close dropdown if it's open
        if (schematicDropdown.isOpen()) {
            schematicDropdown.close();
            return;
        }

        // If files already loaded, show dropdown
        if (availableFiles != null && availableFiles.length > 0) {
            showSchematicDropdown();
            return;
        }

        // Load files from API
        isLoadingFiles = true;
        downloadStatus = "";

        String vendor = postInfo.getVendor() != null ? postInfo.getVendor() : "minemev";
        String uuid = postInfo.getUuid();

        // Strip vendor prefix if present
        if (uuid != null && uuid.contains("/")) {
            String[] parts = uuid.split("/", 2);
            if (parts.length == 2) {
                vendor = parts[0];
                uuid = parts[1];
            }
        }

        MinemevNetworkManager.getPostFiles(vendor, uuid)
            .thenAccept(files -> {
                if (client != null) {
                    client.execute(() -> {
                        availableFiles = files;
                        isLoadingFiles = false;
                        if (files != null && files.length > 0) {
                            showSchematicDropdown();
                        } else {
                            downloadStatus = "No files available";
                        }
                    });
                }
            })
            .exceptionally(throwable -> {
                if (client != null) {
                    client.execute(() -> {
                        isLoadingFiles = false;
                        // Provide more specific error message
                        String errorMsg = throwable.getMessage();
                        if (errorMsg != null && errorMsg.contains("UnknownHost")) {
                            downloadStatus = "✗ Error: No internet connection";
                        } else if (errorMsg != null && errorMsg.contains("timeout")) {
                            downloadStatus = "✗ Error: Connection timeout";
                        } else {
                            downloadStatus = "✗ Error: Failed to load file list";
                        }
                        System.err.println("Failed to load files: " + errorMsg);
                    });
                }
                return null;
            });
    }

    private void showSchematicDropdown() {
        if (availableFiles == null || availableFiles.length == 0) return;

        List<DropdownWidget.DropdownItem> items = new ArrayList<>();
        for (MinemevFileInfo file : availableFiles) {
            String displayText = file.getDefaultFileName();
            if (file.getFileSize() > 0) {
                displayText += " (" + formatFileSize(file.getFileSize()) + ")";
            }
            items.add(new DropdownWidget.DropdownItem(displayText, file));
        }

        schematicDropdown.setItems(items);
        schematicDropdown.setStatusMessage(downloadStatus); // Show current status in dropdown

        // Position dropdown below the download button
        if (downloadButton != null) {
            schematicDropdown.setPosition(
                downloadButton.getX(),
                downloadButton.getY() + downloadButton.getHeight() + 2
            );
        }

        schematicDropdown.open();
    }

    private void onSchematicSelected(DropdownWidget.DropdownItem item) {
        if (item == null || !(item.getData() instanceof MinemevFileInfo)) return;

        MinemevFileInfo file = (MinemevFileInfo) item.getData();
        // Don't close dropdown - keep it open to show download status
        downloadSchematic(file);
    }

    private void downloadSchematic(MinemevFileInfo file) {
        downloadStatus = "Downloading...";
        if (schematicDropdown != null) {
            schematicDropdown.setStatusMessage(downloadStatus);
        }

        new Thread(() -> {
            try {
                // Get download URL
                String downloadUrl = file.getDownloadUrl();
                if (downloadUrl == null || downloadUrl.isEmpty()) {
                    client.execute(() -> {
                        downloadStatus = "Invalid download URL";
                        if (schematicDropdown != null) {
                            schematicDropdown.setStatusMessage(downloadStatus);
                        }
                    });
                    System.err.println("[Download] Invalid download URL");
                    return;
                }

                System.out.println("[Download] Starting download from: " + downloadUrl);
                System.out.println("[Download] File: " + file.getDefaultFileName());

                // Encode spaces in URL (spaces are not valid in URIs)
                String encodedUrl = downloadUrl.replace(" ", "%20");

                // Download file - enable redirect following for 302 responses
                HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(encodedUrl))
                    .GET()
                    .header("User-Agent", "LitematicDownloader/1.0")
                    .build();

                System.out.println("[Download] Sending request...");
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                System.out.println("[Download] Response status: " + response.statusCode());
                System.out.println("[Download] Content length: " + response.body().length);

                if (response.statusCode() != 200) {
                    String errorMsg;
                    switch (response.statusCode()) {
                        case 404:
                            errorMsg = "✗ Error: File not found on server";
                            break;
                        case 403:
                            errorMsg = "✗ Error: Access denied";
                            break;
                        case 500:
                        case 502:
                        case 503:
                            errorMsg = "✗ Error: Server error (" + response.statusCode() + ")";
                            break;
                        case 429:
                            errorMsg = "✗ Error: Too many requests, try again later";
                            break;
                        default:
                            errorMsg = "✗ Download failed: HTTP " + response.statusCode();
                    }
                    System.err.println("[Download] " + errorMsg);
                    client.execute(() -> {
                        downloadStatus = errorMsg;
                        if (schematicDropdown != null) {
                            schematicDropdown.setStatusMessage(downloadStatus);
                        }
                    });
                    return;
                }

                // Save to schematics folder (using configured path)
                Path schematicsPath = Paths.get(DownloadSettings.getInstance().getAbsoluteDownloadPath());
                File schematicsDir = schematicsPath.toFile();
                if (!schematicsDir.exists()) {
                    boolean created = schematicsDir.mkdirs();
                    System.out.println("[Download] Created schematics directory: " + created);
                }
                System.out.println("[Download] Schematics directory: " + schematicsDir.getAbsolutePath());

                String fileName = file.getDefaultFileName();
                if (!fileName.endsWith(".litematic")) {
                    fileName += ".litematic";
                }

                File outputFile = new File(schematicsDir, fileName);

                // If file exists, add number suffix
                int counter = 1;
                while (outputFile.exists()) {
                    String baseName = fileName.substring(0, fileName.lastIndexOf(".litematic"));
                    outputFile = new File(schematicsDir, baseName + "_" + counter + ".litematic");
                    counter++;
                }

                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(response.body());
                }

                final String finalFileName = outputFile.getName();
                final String finalPath = outputFile.getAbsolutePath();
                client.execute(() -> {
                    downloadStatus = "✓ Downloaded: " + finalFileName;
                    if (schematicDropdown != null) {
                        schematicDropdown.setStatusMessage(downloadStatus);
                    }
                    System.out.println("Downloaded schematic to: " + finalPath);
                });

            } catch (Exception e) {
                client.execute(() -> {
                    // Provide more specific error messages
                    String errorMsg;
                    if (e instanceof java.net.UnknownHostException) {
                        errorMsg = "✗ Error: No internet connection";
                    } else if (e instanceof java.net.SocketTimeoutException) {
                        errorMsg = "✗ Error: Connection timeout";
                    } else if (e instanceof java.io.FileNotFoundException) {
                        errorMsg = "✗ Error: File not found";
                    } else if (e instanceof java.io.IOException && e.getMessage().contains("Permission denied")) {
                        errorMsg = "✗ Error: Cannot write to disk (permission denied)";
                    } else if (e instanceof java.io.IOException && e.getMessage().contains("No space")) {
                        errorMsg = "✗ Error: Not enough disk space";
                    } else {
                        String msg = e.getMessage();
                        if (msg != null && msg.length() > 40) {
                            msg = msg.substring(0, 37) + "...";
                        }
                        errorMsg = "✗ Error: " + (msg != null ? msg : "Unknown error");
                    }

                    downloadStatus = errorMsg;
                    if (schematicDropdown != null) {
                        schematicDropdown.setStatusMessage(downloadStatus);
                    }
                    System.err.println("Failed to download schematic: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        }).start();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Block hover effects on elements below if dropdown is open and mouse is over it
        int renderMouseX = mouseX;
        int renderMouseY = mouseY;
        if (schematicDropdown != null && schematicDropdown.isOpen() && schematicDropdown.isMouseOver(mouseX, mouseY)) {
            renderMouseX = -1;
            renderMouseY = -1;
        }

        // Draw panel background
        context.fill(x, y, x + width, y + height, PANEL_BG_COLOR);

        // Draw left border
        context.fill(x, y, x + 1, y + height, 0xFF555555);

        if (postInfo == null) {
            // Draw placeholder text
            String text = "Select a schematic to view details";
            int textWidth = client.textRenderer.getWidth(text);
            context.drawTextWithShadow(client.textRenderer, text,
                x + (width - textWidth) / 2, y + height / 2 - 4, SUBTITLE_COLOR);
            return;
        }

        // Draw download button in complete top left corner with no padding
        int downloadBtnSize = 20;
        int downloadBtnX = x;
        int downloadBtnY = y;

        if (downloadButton == null) {
            downloadButton = new CustomButton(
                downloadBtnX, downloadBtnY, downloadBtnSize, downloadBtnSize,
                Text.literal("⬇️"),
                btn -> onDownloadButtonClick()
            );
            downloadButton.setRenderAsDownloadIcon(true);
        } else {
            downloadButton.setX(downloadBtnX);
            downloadButton.setY(downloadBtnY);
            downloadButton.active = !isLoadingFiles;
        }
        downloadButton.render(context, renderMouseX, renderMouseY, delta);

        // Enable scissor for scrolling (start below the download button)
        int contentStartY = y + downloadBtnSize;
        context.enableScissor(x + 1, contentStartY, x + width, y + height);

        int currentY = contentStartY + PADDING - (int) scrollOffset;
        contentHeight = 0;

        // Calculate responsive image dimensions (respecting aspect ratio)
        int imageWidth = getDisplayImageWidth();
        int imageHeight = getDisplayImageHeight();

        // Draw image area (centered horizontally)
        int imageX = x + (width - imageWidth) / 2;
        int imageY = currentY;

        if (isLoadingImage) {
            // Draw loading spinner centered in image area using reusable instance
            context.fill(imageX, imageY, imageX + imageWidth, imageY + imageHeight, 0xFF333333);
            // Update spinner position to center it in the image area
            imageLoadingSpinner.setPosition(
                imageX + imageWidth / 2 - imageLoadingSpinner.getWidth() / 2,
                imageY + imageHeight / 2 - imageLoadingSpinner.getHeight() / 2
            );
        } else if (currentImageTexture != null) {
            // Draw image (stretch to fill)
            context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, currentImageTexture,
                imageX, imageY, 0.0f, 0.0f, imageWidth, imageHeight, imageWidth, imageHeight);
        } else {
            // Draw placeholder
            context.fill(imageX, imageY, imageX + imageWidth, imageY + imageHeight, 0xFF333333);
            String noImg = isCompactMode() ? "..." : "No image";
            int tw = client.textRenderer.getWidth(noImg);
            context.drawTextWithShadow(client.textRenderer, noImg,
                imageX + (imageWidth - tw) / 2, imageY + imageHeight / 2 - 4, SUBTITLE_COLOR);
        }

        currentY += imageHeight + PADDING;
        contentHeight += imageHeight + PADDING;

        // Draw image navigation if multiple images
        if (imageUrls != null && imageUrls.length > 1) {
            String indicator = String.format("%d / %d", currentImageIndex + 1, imageUrls.length);
            int indicatorWidth = client.textRenderer.getWidth(indicator);
            int indicatorX = x + (width - indicatorWidth) / 2;
            int btnY = currentY;

            // Update carousel button positions
            updateCarouselButtons(btnY);

            // Render navigation buttons using CustomButton widgets
            if (prevImageButton != null) {
                prevImageButton.render(context, renderMouseX, renderMouseY, delta);
            }

            // Index indicator
            context.drawTextWithShadow(client.textRenderer, indicator, indicatorX, btnY + 4, SUBTITLE_COLOR);

            // Render next button
            if (nextImageButton != null) {
                nextImageButton.render(context, renderMouseX, renderMouseY, delta);
            }

            currentY += 16 + PADDING;
            contentHeight += 16 + PADDING;
        }

        // Draw title
        String title = postInfo.getTitle() != null ? postInfo.getTitle() : "Untitled";
        drawWrappedText(context, title, x + PADDING, currentY, width - PADDING * 2, TITLE_COLOR);
        int titleHeight = getWrappedTextHeight(title, width - PADDING * 2);
        currentY += titleHeight + 8;
        contentHeight += titleHeight + 8;

        // Draw author
        String author = "By " + (postInfo.getAuthor() != null ? postInfo.getAuthor() : "Unknown");
        context.drawTextWithShadow(client.textRenderer, author, x + PADDING, currentY, SUBTITLE_COLOR);
        currentY += 12;
        contentHeight += 12;

        // Draw downloads
        String downloads = "Downloads: " + postInfo.getDownloads();
        context.drawTextWithShadow(client.textRenderer, downloads, x + PADDING, currentY, SUBTITLE_COLOR);
        currentY += 16;
        contentHeight += 16;


        // Draw tags
        String[] tags = postInfo.getTags();
        if (tags != null && tags.length > 0) {
            context.drawTextWithShadow(client.textRenderer, "Tags:", x + PADDING, currentY, SUBTITLE_COLOR);
            currentY += 12;
            contentHeight += 12;

            int tagX = x + PADDING;
            for (String tag : tags) {
                int tagWidth = client.textRenderer.getWidth(tag) + 8;
                if (tagX + tagWidth > x + width - PADDING) {
                    tagX = x + PADDING;
                    currentY += 14;
                    contentHeight += 14;
                }
                context.fill(tagX, currentY, tagX + tagWidth, currentY + 12, TAG_BG_COLOR);
                context.drawTextWithShadow(client.textRenderer, tag, tagX + 4, currentY + 2, TAG_TEXT_COLOR);
                tagX += tagWidth + 4;
            }
            currentY += 16;
            contentHeight += 16;
        }

        // Draw versions
        String[] versions = postInfo.getVersions();
        if (versions != null && versions.length > 0) {
            context.drawTextWithShadow(client.textRenderer, "Versions:", x + PADDING, currentY, SUBTITLE_COLOR);
            currentY += 12;
            contentHeight += 12;

            StringBuilder versionText = new StringBuilder();
            for (int i = 0; i < Math.min(5, versions.length); i++) {
                if (i > 0) versionText.append(", ");
                versionText.append(versions[i]);
            }
            if (versions.length > 5) {
                versionText.append("... (+").append(versions.length - 5).append(" more)");
            }
            context.drawTextWithShadow(client.textRenderer, versionText.toString(),
                x + PADDING, currentY, TAG_TEXT_COLOR);
            currentY += 16;
            contentHeight += 16;
        }

        // Draw description if detail loaded
        if (postDetail != null && postDetail.getDescription() != null && !postDetail.getDescription().isEmpty()) {
            currentY += 8;
            contentHeight += 8;
            context.drawTextWithShadow(client.textRenderer, "Description:", x + PADDING, currentY, SUBTITLE_COLOR);
            currentY += 12;
            contentHeight += 12;

            String desc = postDetail.getDescription();
            drawWrappedText(context, desc, x + PADDING, currentY, width - PADDING * 2, TAG_TEXT_COLOR);
            int descHeight = getWrappedTextHeight(desc, width - PADDING * 2);
            contentHeight += descHeight;
        } else if (isLoadingDetails) {
            currentY += 8;
            context.drawTextWithShadow(client.textRenderer, "Loading details...", x + PADDING, currentY, SUBTITLE_COLOR);
            contentHeight += 20;
        }

        contentHeight += PADDING * 2;

        context.disableScissor();

        // Render dropdown on top of everything (after scissor is disabled)
        if (schematicDropdown != null && schematicDropdown.isOpen()) {
            schematicDropdown.render(context, mouseX, mouseY, delta);
        }

        // Update and render scrollbar if content is scrollable
        if (contentHeight > height) {
            scrollBar.setScrollData(contentHeight, height);
            scrollBar.setScrollPercentage(scrollOffset / Math.max(1, contentHeight - height));

            // Render scrollbar with direct mouse handling
            if (client != null && client.getWindow() != null) {
                long windowHandle = client.getWindow().getHandle();
                if (scrollBar.updateAndRender(context, mouseX, mouseY, delta, windowHandle)) {
                    // Scroll position changed from scrollbar drag
                    double maxScroll = Math.max(0, contentHeight - height);
                    scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
                }
            } else {
                // Fallback to regular render
                scrollBar.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void drawWrappedText(DrawContext context, String text, int textX, int textY, int maxWidth, int color) {
        if (text == null || text.isEmpty()) return;

        int lineY = textY;

        // Split by newlines first to handle shift+enter line breaks
        String[] paragraphs = text.split("\\r?\\n");

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                // Empty line, just add spacing
                lineY += 10;
                continue;
            }

            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();

            for (String word : words) {
                String testLine = !line.isEmpty() ? line + " " + word : word;
                int testWidth = client.textRenderer.getWidth(testLine);

                if (testWidth > maxWidth && !line.isEmpty()) {
                    context.drawTextWithShadow(client.textRenderer, line.toString(), textX, lineY, color);
                    line = new StringBuilder(word);
                    lineY += 10;
                } else {
                    line = new StringBuilder(testLine);
                }
            }

            if (!line.isEmpty()) {
                context.drawTextWithShadow(client.textRenderer, line.toString(), textX, lineY, color);
                lineY += 10;
            }
        }
    }

    private int getWrappedTextHeight(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return 10;

        int lines = 0;

        // Split by newlines first to handle shift+enter line breaks
        String[] paragraphs = text.split("\\r?\\n");

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                // Empty line
                lines++;
                continue;
            }

            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();
            int paragraphLines = 1;

            for (String word : words) {
                String testLine = !line.isEmpty() ? line + " " + word : word;
                int testWidth = client.textRenderer.getWidth(testLine);

                if (testWidth > maxWidth && !line.isEmpty()) {
                    line = new StringBuilder(word);
                    paragraphLines++;
                } else {
                    line = new StringBuilder(testLine);
                }
            }

            lines += paragraphLines;
        }

        return Math.max(lines, 1) * 10;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check dropdown first (it can be outside panel bounds when open)
        if (schematicDropdown != null && schematicDropdown.isOpen()) {
            boolean handled = schematicDropdown.mouseClicked(mouseX, mouseY, button);
            // If clicked outside dropdown, it will close itself
            // Also block all clicks if mouse is over dropdown
            return handled || schematicDropdown.isOpen() || schematicDropdown.isMouseOver(mouseX, mouseY);
        }

        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        System.out.println("[PostDetailPanel] mouseClicked - x:" + mouseX + " y:" + mouseY + " button:" + button);

        // Check download button
        if (button == 0 && downloadButton != null && postInfo != null) {
            boolean isOverDownload = mouseX >= downloadButton.getX() &&
                                    mouseX < downloadButton.getX() + downloadButton.getWidth() &&
                                    mouseY >= downloadButton.getY() &&
                                    mouseY < downloadButton.getY() + downloadButton.getHeight();
            if (isOverDownload && downloadButton.active) {
                onDownloadButtonClick();
                return true;
            }
        }

        // Check scrollbar
        if (scrollBar != null && scrollBar.mouseClicked(mouseX, mouseY, button)) {
            System.out.println("[PostDetailPanel] Scrollbar handled the click");
            return true;
        }

        // Check image navigation buttons
        if (button == 0 && imageUrls != null && imageUrls.length > 1) {
            // Check if clicking on previous button
            if (prevImageButton != null) {
                System.out.println("[PostDetailPanel] Checking prev button - btnX:" + prevImageButton.getX() +
                                  " btnY:" + prevImageButton.getY() + " btnW:" + prevImageButton.getWidth() +
                                  " btnH:" + prevImageButton.getHeight());
                boolean isOverPrev = mouseX >= prevImageButton.getX() &&
                                    mouseX < prevImageButton.getX() + prevImageButton.getWidth() &&
                                    mouseY >= prevImageButton.getY() &&
                                    mouseY < prevImageButton.getY() + prevImageButton.getHeight();
                if (isOverPrev) {
                    System.out.println("[PostDetailPanel] Previous button clicked!");
                    previousImage();
                    return true;
                }
            }

            // Check if clicking on next button
            if (nextImageButton != null) {
                System.out.println("[PostDetailPanel] Checking next button - btnX:" + nextImageButton.getX() +
                                  " btnY:" + nextImageButton.getY() + " btnW:" + nextImageButton.getWidth() +
                                  " btnH:" + nextImageButton.getHeight());
                boolean isOverNext = mouseX >= nextImageButton.getX() &&
                                    mouseX < nextImageButton.getX() + nextImageButton.getWidth() &&
                                    mouseY >= nextImageButton.getY() &&
                                    mouseY < nextImageButton.getY() + nextImageButton.getHeight();
                if (isOverNext) {
                    System.out.println("[PostDetailPanel] Next button clicked!");
                    nextImage();
                    return true;
                }
            }
        }

        return true;
    }

    private void previousImage() {
        if (imageUrls != null && imageUrls.length > 1) {
            currentImageIndex = (currentImageIndex - 1 + imageUrls.length) % imageUrls.length;
            loadImage(imageUrls[currentImageIndex]);
        }
    }

    private void nextImage() {
        if (imageUrls != null && imageUrls.length > 1) {
            currentImageIndex = (currentImageIndex + 1) % imageUrls.length;
            loadImage(imageUrls[currentImageIndex]);
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Forward to scrollbar if it's being dragged
        if (scrollBar != null && (scrollBar.isDragging() || scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY))) {
            // Update scroll offset based on scrollbar
            double maxScroll = Math.max(0, contentHeight - height);
            scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Forward to scrollbar
        if (scrollBar != null && scrollBar.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Check dropdown first
        if (schematicDropdown != null && schematicDropdown.isOpen()) {
            if (schematicDropdown.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }

        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            double maxScroll = Math.max(0, contentHeight - height + PADDING);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 20));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Arrow key navigation for images
        if (imageUrls != null && imageUrls.length > 1) {
            if (keyCode == 263) { // Left arrow
                previousImage();
                return true;
            } else if (keyCode == 262) { // Right arrow
                nextImage();
                return true;
            }
        }
        return false;
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return false;
    }
}

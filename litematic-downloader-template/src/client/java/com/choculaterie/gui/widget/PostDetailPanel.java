package com.choculaterie.gui.widget;

import com.choculaterie.models.MinemevPostDetailInfo;
import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.network.MinemevNetworkManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
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
    private static final int IMAGE_SIZE = 200;
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

    private final MinecraftClient client;
    private double scrollOffset = 0;
    private int contentHeight = 0;

    public PostDetailPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = MinecraftClient.getInstance();
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setPost(MinemevPostInfo post) {
        System.out.println("[PostDetailPanel] setPost called!");
        System.out.println("[PostDetailPanel] Post: " + (post != null ? post.getTitle() : "null"));

        if (post == null) {
            System.out.println("[PostDetailPanel] Post is null, clearing panel");
            clear();
            return;
        }

        System.out.println("[PostDetailPanel] Setting post: " + post.getTitle());
        System.out.println("[PostDetailPanel] UUID: " + post.getUuid());
        System.out.println("[PostDetailPanel] Vendor: " + post.getVendor());

        this.postInfo = post;
        this.postDetail = null;
        this.isLoadingDetails = true;
        this.currentImageIndex = 0;
        this.currentImageTexture = null;
        this.scrollOffset = 0;

        System.out.println("[PostDetailPanel] Post info set, loading details...");

        // Load post details
        String vendor = post.getVendor() != null ? post.getVendor() : "minemev";
        MinemevNetworkManager.getPostDetails(vendor, post.getUuid())
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

        // Register texture on main thread
        final NativeImage finalImage = nativeImage;
        final String uniqueId = UUID.randomUUID().toString().replace("-", "");
        final Identifier texId = Identifier.of("litematicdownloader", "textures/dynamic/" + uniqueId);

        if (client != null) {
            client.execute(() -> {
                client.getTextureManager().registerTexture(
                    texId,
                    new NativeImageBackedTexture(() -> "minemev_image", finalImage)
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
        this.imageUrls = null;
        this.currentImageIndex = 0;
        this.scrollOffset = 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
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

        // Enable scissor for scrolling
        context.enableScissor(x + 1, y, x + width, y + height);

        int currentY = y + PADDING - (int) scrollOffset;
        contentHeight = 0;

        // Draw image area
        int imageX = x + (width - IMAGE_SIZE) / 2;
        int imageY = currentY;

        if (isLoadingImage) {
            // Draw loading spinner centered in image area
            context.fill(imageX, imageY, imageX + IMAGE_SIZE, imageY + IMAGE_SIZE, 0xFF333333);
            LoadingSpinner imgSpinner = new LoadingSpinner(
                imageX + IMAGE_SIZE / 2 - 16,
                imageY + IMAGE_SIZE / 2 - 16
            );
            imgSpinner.render(context, mouseX, mouseY, delta);
        } else if (currentImageTexture != null) {
            // Draw image using RenderPipelines.GUI_TEXTURED
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                currentImageTexture,
                imageX, imageY,
                0, 0,
                IMAGE_SIZE, IMAGE_SIZE,
                IMAGE_SIZE, IMAGE_SIZE
            );
        } else {
            // Draw placeholder
            context.fill(imageX, imageY, imageX + IMAGE_SIZE, imageY + IMAGE_SIZE, 0xFF333333);
            String noImg = "No image";
            int tw = client.textRenderer.getWidth(noImg);
            context.drawTextWithShadow(client.textRenderer, noImg,
                imageX + (IMAGE_SIZE - tw) / 2, imageY + IMAGE_SIZE / 2 - 4, SUBTITLE_COLOR);
        }

        currentY += IMAGE_SIZE + PADDING;
        contentHeight += IMAGE_SIZE + PADDING;

        // Draw image navigation if multiple images
        if (imageUrls != null && imageUrls.length > 1) {
            String indicator = String.format("%d / %d", currentImageIndex + 1, imageUrls.length);
            int indicatorWidth = client.textRenderer.getWidth(indicator);
            int indicatorX = x + (width - indicatorWidth) / 2;

            // Draw navigation buttons
            int btnWidth = 25;
            int btnHeight = 16;
            int btnY = currentY;

            // Previous button
            int prevBtnX = indicatorX - btnWidth - 10;
            boolean prevHovered = mouseX >= prevBtnX && mouseX < prevBtnX + btnWidth &&
                                  mouseY >= btnY && mouseY < btnY + btnHeight;
            context.fill(prevBtnX, btnY, prevBtnX + btnWidth, btnY + btnHeight,
                prevHovered ? 0xFF4A4A4A : 0xFF3A3A3A);
            context.drawCenteredTextWithShadow(client.textRenderer, "<",
                prevBtnX + btnWidth / 2, btnY + 4, TITLE_COLOR);

            // Index indicator
            context.drawTextWithShadow(client.textRenderer, indicator, indicatorX, btnY + 4, SUBTITLE_COLOR);

            // Next button
            int nextBtnX = indicatorX + indicatorWidth + 10;
            boolean nextHovered = mouseX >= nextBtnX && mouseX < nextBtnX + btnWidth &&
                                  mouseY >= btnY && mouseY < btnY + btnHeight;
            context.fill(nextBtnX, btnY, nextBtnX + btnWidth, btnY + btnHeight,
                nextHovered ? 0xFF4A4A4A : 0xFF3A3A3A);
            context.drawCenteredTextWithShadow(client.textRenderer, ">",
                nextBtnX + btnWidth / 2, btnY + 4, TITLE_COLOR);

            currentY += btnHeight + PADDING;
            contentHeight += btnHeight + PADDING;
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
    }

    private void drawWrappedText(DrawContext context, String text, int textX, int textY, int maxWidth, int color) {
        if (text == null || text.isEmpty()) return;

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = textY;

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
        }
    }

    private int getWrappedTextHeight(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return 10;

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lines = 1;

        for (String word : words) {
            String testLine = !line.isEmpty() ? line + " " + word : word;
            int testWidth = client.textRenderer.getWidth(testLine);

            if (testWidth > maxWidth && !line.isEmpty()) {
                line = new StringBuilder(word);
                lines++;
            } else {
                line = new StringBuilder(testLine);
            }
        }

        return lines * 10;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        // Check image navigation buttons
        if (imageUrls != null && imageUrls.length > 1) {
            int imageNavY = y + PADDING - (int) scrollOffset + IMAGE_SIZE + PADDING;
            int btnHeight = 16;
            int btnWidth = 25;

            String indicator = String.format("%d / %d", currentImageIndex + 1, imageUrls.length);
            int indicatorWidth = client.textRenderer.getWidth(indicator);
            int indicatorX = x + (width - indicatorWidth) / 2;

            int prevBtnX = indicatorX - btnWidth - 10;
            int nextBtnX = indicatorX + indicatorWidth + 10;

            // Previous button
            if (mouseX >= prevBtnX && mouseX < prevBtnX + btnWidth &&
                mouseY >= imageNavY && mouseY < imageNavY + btnHeight) {
                previousImage();
                return true;
            }

            // Next button
            if (mouseX >= nextBtnX && mouseX < nextBtnX + btnWidth &&
                mouseY >= imageNavY && mouseY < imageNavY + btnHeight) {
                nextImage();
                return true;
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

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
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

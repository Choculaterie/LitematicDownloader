package com.choculaterie.gui;

import com.choculaterie.config.SettingsManager;
import com.choculaterie.networking.LitematicHttpClient;
import com.choculaterie.networking.MinemevHttpClient;
import com.choculaterie.models.MinemevPostDetailInfo;
import com.choculaterie.models.MinemevFileInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MinemevDetailScreen extends Screen {
    private final String postUuid;
    private MinemevPostDetailInfo postDetail;
    private boolean isLoading = true;
    private String errorMessage = null;

    // UI controls
    private ButtonWidget backButton;
    private ButtonWidget downloadButton;

    // Image handling
    private Identifier coverImageTexture = null;
    private int imageWidth = 0;
    private int imageHeight = 0;

    // Scrolling description
    private int descriptionScrollPos = 0;
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
    // New: precise dragging offset for description scrollbar
    private int scrollDragOffset = 0;

    // Files list for this post
    private boolean filesLoading = false;
    private List<MinemevFileInfo> availableFiles = new ArrayList<>();
    private int filesRenderStartY = -1;
    private int filesRenderCount = 0;
    private final int fileItemHeight = 14;
    private int lastRightSectionX = 0;
    private int lastContentWidth = 0;

    // Dropdown overlay for file selection
    private boolean showFileDropdown = false;
    private List<MinemevFileInfo> fileDropdownItems = new ArrayList<>();
    private int fileDropdownX = 0;
    private int fileDropdownY = 0;
    private int fileDropdownWidth = 360;
    private int fileDropdownHeight = 0;
    private int fileDropdownScroll = 0;
    private int fileDropdownContentHeight = 0;
    // New: drag state for file dropdown scrollbar
    private boolean isDraggingFileDropdown = false;
    private int fileDropdownDragOffset = 0;

    // Loading anim
    private long loadingStartTime = 0;

    // Cache reference
    private static final CacheManager cacheManager = LitematicDownloaderScreen.getCacheManager();
    private static final long DETAIL_CACHE_DURATION_MS = 5 * 60 * 1000;

    public MinemevDetailScreen(String postUuid) {
        super(Text.literal(""));
        this.postUuid = postUuid;
    }

    @Override
    protected void init() {
        super.init();

        // Back button
        this.backButton = ButtonWidget.builder(Text.literal("←"), button -> {
            NavigationState navState = NavigationState.getInstance();
            if (navState.getSavedCurrentPage() > 0) {
                MinecraftClient.getInstance().setScreen(new LitematicDownloaderScreen(true));
            } else {
                MinecraftClient.getInstance().setScreen(new LitematicDownloaderScreen());
            }
        }).dimensions(10, 10, 20, 20).build();
        this.addDrawableChild(this.backButton);

        // Download button (top right of image area)
        this.downloadButton = ButtonWidget.builder(Text.literal("⬇"), button -> {
            if (postDetail != null) {
                // If we already loaded files and there's exactly one -> download directly
                if (!availableFiles.isEmpty() && availableFiles.size() == 1) {
                    startPerFileDownloadWithConfirm(availableFiles.get(0));
                } else {
                    // Open dropdown of files anchored to the button
                    openMinemevFileDropdown(this.width - 40, 30);
                }
            }
        }).dimensions(this.width - 30, 10, 20, 20).build();
        this.downloadButton.active = false;
        this.addDrawableChild(this.downloadButton);

        errorMessage = null;
        loadPostDetails();
    }

    private void loadPostDetails() {
        // Use cache first
        if (cacheManager.hasValidMinemevDetailCache(postUuid, DETAIL_CACHE_DURATION_MS)) {
            CacheManager.MinemevDetailCacheEntry cached = cacheManager.getMinemevDetailCache(postUuid);
            postDetail = cached.getDetail();
            isLoading = false;
            this.downloadButton.active = true;
            // Prefer thumbnail, fallback to first image
            String imgUrl = postDetail.getThumbnailUrl();
            if ((imgUrl == null || imgUrl.isEmpty()) && postDetail.getImages() != null && postDetail.getImages().length > 0) {
                imgUrl = postDetail.getImages()[0];
            }
            if (imgUrl != null && !imgUrl.isEmpty()) {
                loadCoverImage(imgUrl);
            }
            // Fetch files list in background
            loadFilesList();
            return;
        }

        isLoading = true;
        loadingStartTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                MinemevPostDetailInfo detail = MinemevHttpClient.fetchPostDetails(postUuid);
                MinecraftClient.getInstance().execute(() -> {
                    postDetail = detail;
                    isLoading = false;
                    this.downloadButton.active = true;
                    cacheManager.putMinemevDetailCache(postUuid, detail, DETAIL_CACHE_DURATION_MS);

                    String imgUrl = detail.getThumbnailUrl();
                    if ((imgUrl == null || imgUrl.isEmpty()) && detail.getImages() != null && detail.getImages().length > 0) {
                        imgUrl = detail.getImages()[0];
                    }
                    if (imgUrl != null && !imgUrl.isEmpty()) {
                        loadCoverImage(imgUrl);
                    }
                    loadFilesList();
                });
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() -> {
                    errorMessage = "Failed to load Minemev details: " + e.getMessage();
                    isLoading = false;
                });
            }
        }).start();
    }

    private void loadFilesList() {
        filesLoading = true;
        availableFiles = new ArrayList<>();
        new Thread(() -> {
            try {
                List<MinemevFileInfo> files = MinemevHttpClient.fetchPostFiles(postUuid);
                MinecraftClient.getInstance().execute(() -> {
                    availableFiles = files;
                    filesLoading = false;
                });
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() -> {
                    filesLoading = false;
                    ToastManager.addToast("Failed to load files: " + e.getMessage(), true);
                });
            }
        }).start();
    }

    private void openMinemevFileDropdown(int anchorX, int anchorY) {
        showFileDropdown = false;
        fileDropdownItems = new ArrayList<>();
        fileDropdownX = Math.min(anchorX, this.width - fileDropdownWidth - 10);
        fileDropdownY = anchorY;
        fileDropdownScroll = 0;
        // Use already loaded list if present; otherwise fetch
        if (!availableFiles.isEmpty()) {
            if (availableFiles.size() == 1) {
                // Direct download when only one file is available
                startPerFileDownloadWithConfirm(availableFiles.get(0));
                return;
            }
            fileDropdownItems = availableFiles;
            fileDropdownContentHeight = fileDropdownItems.size() * 18;
            int maxHeight = Math.min(220, this.height - fileDropdownY - 20);
            fileDropdownHeight = Math.min(fileDropdownContentHeight, maxHeight);
            showFileDropdown = true;
        } else {
            new Thread(() -> {
                try {
                    List<MinemevFileInfo> files = MinemevHttpClient.fetchPostFiles(postUuid);
                    MinecraftClient.getInstance().execute(() -> {
                        if (files == null || files.isEmpty()) {
                            ToastManager.addToast("No files available for this post", true);
                            showFileDropdown = false;
                            return;
                        }
                        if (files.size() == 1) {
                            startPerFileDownloadWithConfirm(files.get(0));
                            showFileDropdown = false;
                            return;
                        }
                        fileDropdownItems = files;
                        fileDropdownContentHeight = fileDropdownItems.size() * 18;
                        int maxHeight = Math.min(220, this.height - fileDropdownY - 20);
                        fileDropdownHeight = Math.min(fileDropdownContentHeight, maxHeight);
                        showFileDropdown = !fileDropdownItems.isEmpty();
                        if (!showFileDropdown) {
                            ToastManager.addToast("No files available for this post", true);
                        }
                    });
                } catch (Exception e) {
                    MinecraftClient.getInstance().execute(() -> {
                        ToastManager.addToast("Failed to load files: " + e.getMessage(), true);
                        showFileDropdown = false;
                    });
                }
            }).start();
        }
    }

    private void loadCoverImage(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            coverImageTexture = null;
            return;
        }
        new Thread(() -> {
            try {
                String encodedUrl = encodeImageUrl(imageUrl);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(encodedUrl))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = LitematicHttpClient.getClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    MinecraftClient.getInstance().execute(() -> {
                        ToastManager.addToast("Failed to load image: HTTP " + response.statusCode(), true);
                        createAndRegisterPlaceholder("HTTP " + response.statusCode());
                    });
                    return;
                }
                byte[] imageData = response.body();
                if (imageData.length < 100) {
                    MinecraftClient.getInstance().execute(() -> {
                        ToastManager.addToast("Image file too small (corrupted)", true);
                        createAndRegisterPlaceholder("Image too small");
                    });
                    return;
                }
                String detectedFormat = detectImageFormat(imageData);
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        String uniqueId = UUID.randomUUID().toString().replace("-", "");
                        coverImageTexture = Identifier.of("minecraft", "textures/dynamic/" + uniqueId);
                        NativeImage nativeImage;
                        boolean isPlaceholder = false;

                        try {
                            byte[] processed = convertImageToPng(imageData, detectedFormat);
                            nativeImage = NativeImage.read(new ByteArrayInputStream(processed));
                            if (nativeImage.getWidth() <= 0 || nativeImage.getHeight() <= 0) {
                                ToastManager.addToast("Image has invalid dimensions", true);
                                nativeImage.close();
                                nativeImage = createPlaceholderImage(256, 256, "Invalid dims");
                                isPlaceholder = true;
                            } else if (nativeImage.getWidth() > 4096 || nativeImage.getHeight() > 4096) {
                                ToastManager.addToast("Image too large", true);
                                nativeImage.close();
                                nativeImage = createPlaceholderImage(256, 256, "Too large");
                                isPlaceholder = true;
                            }
                        } catch (Exception e) {
                            ToastManager.addToast("Could not load " + detectedFormat + " image", true);
                            nativeImage = createPlaceholderImage(256, 256, "Unsupported");
                            isPlaceholder = true;
                        }

                        if (nativeImage != null) {
                            imageWidth = nativeImage.getWidth();
                            imageHeight = nativeImage.getHeight();
                            MinecraftClient.getInstance().getTextureManager().registerTexture(
                                    coverImageTexture,
                                    new NativeImageBackedTexture(() -> "minemev_cover_image", nativeImage)
                            );
                        }
                    } catch (Exception e) {
                        ToastManager.addToast("Failed to process image", true);
                        coverImageTexture = null;
                    }
                });
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() -> {
                    ToastManager.addToast("Failed to load image", true);
                    createAndRegisterPlaceholder("Download failed");
                });
            }
        }).start();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title (screen title area)
        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

        // Render other children except our positioned buttons
        for (Element child : this.children()) {
            if (child instanceof ButtonWidget) {
                ButtonWidget b = (ButtonWidget) child;
                if (b == this.backButton || b == this.downloadButton) continue;
            }
            if (child instanceof Drawable d) {
                d.render(context, mouseX, mouseY, delta);
            }
        }

        if (isLoading) {
            int centerY = this.height / 2;
            drawLoadingAnimation(context, this.width / 2, centerY - 15);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Loading..."), this.width / 2, centerY + 15, 0xFFFFFFFF);
        } else if (errorMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(errorMessage), this.width / 2, this.height / 2, 0xFFFFFFFF);
        } else if (postDetail != null) {
            int padding = 20;
            int leftSectionWidth = 256;
            int rightSectionX = leftSectionWidth + padding * 2;
            int contentWidth = this.width - rightSectionX - padding;
            int topMargin = 40;

            lastRightSectionX = rightSectionX;
            lastContentWidth = contentWidth;

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
                context.fill(padding, topMargin, padding + leftSectionWidth, topMargin + leftSectionWidth, 0x33FFFFFF);
                String noImageText = "No Image";
                int textWidth = this.textRenderer.getWidth(noImageText);
                int textX = padding + (leftSectionWidth - textWidth) / 2;
                int textY = topMargin + (leftSectionWidth / 2) - 4;
                context.drawText(this.textRenderer, noImageText, textX, textY, 0xFFAAAAAA, false);
            }

            int y = topMargin;
            // Title (wrapped)
            String name = postDetail.getTitle();
            if (name != null) {
                List<OrderedText> nameLines = this.textRenderer.wrapLines(Text.literal(name), contentWidth);
                for (OrderedText line : nameLines) {
                    context.drawText(this.textRenderer, line, rightSectionX, y, 0xFFFFFFFF, true);
                    y += 10;
                }
                y += 5; // spacing after title
            }
            // Author (wrapped if long)
            String username = postDetail.getAuthor();
            if (username != null) {
                List<OrderedText> authorLines = this.textRenderer.wrapLines(Text.literal("By: " + username), contentWidth);
                for (OrderedText line : authorLines) {
                    context.drawText(this.textRenderer, line, rightSectionX, y, 0xFFD5D5D5, false);
                    y += 10;
                }
                y += 5;
            }
            // Published date
            String publishDate = postDetail.getCreatedAt();
            if (publishDate != null && !publishDate.isEmpty()) {
                String formattedDate = publishDate.split("T")[0];
                context.drawText(this.textRenderer, "Published: " + formattedDate, rightSectionX, y, 0xFFD5D5D5, false);
                y += 20;
            }
            // Downloads (Minemev has no view count in details)
            context.drawText(this.textRenderer, "Downloads: " + postDetail.getDownloads(), rightSectionX, y, 0xFFD5D5D5, false);
            y += 25;

            // Versions
            String versionsJoined = joinArray(postDetail.getVersions());
            if (!versionsJoined.isEmpty()) {
                List<OrderedText> vLines = this.textRenderer.wrapLines(Text.literal("Versions: " + versionsJoined), contentWidth);
                for (OrderedText line : vLines) {
                    context.drawText(this.textRenderer, line, rightSectionX, y, 0xFFFFFFFF, false);
                    y += 10;
                }
                y += 5; // spacing after versions
            }

            // Tags
            String tagsJoined = joinArray(postDetail.getTags());
            if (!tagsJoined.isEmpty()) {
                List<OrderedText> tLines = this.textRenderer.wrapLines(Text.literal("Tags: " + tagsJoined), contentWidth);
                for (OrderedText line : tLines) {
                    context.drawText(this.textRenderer, line, rightSectionX, y, 0xFFFFFFFF, false);
                    y += 10;
                }
                y += 10; // extra spacing before description
            }

            // Files section removed per request; use the download button (top-right) which opens a dropdown when multiple files exist
            // Description header
            context.drawText(this.textRenderer, "Description:", rightSectionX, y, 0xFFFFFFFF, false);
            y += 15;

            this.scrollAreaX = rightSectionX;
            this.scrollAreaY = y;
            this.scrollAreaWidth = contentWidth;
            this.scrollAreaHeight = this.height - y - padding;

            String description = (postDetail.getDescription() != null && !postDetail.getDescription().isEmpty())
                    ? postDetail.getDescription() : "No description available";
            description = description.replace("\r", "").replaceAll("[ \t]+", " ").trim();

            context.enableScissor(this.scrollAreaX, this.scrollAreaY, this.scrollAreaX + this.scrollAreaWidth, this.scrollAreaY + this.scrollAreaHeight);
            int textY = this.scrollAreaY - descriptionScrollPos;
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(description), this.scrollAreaWidth);
            this.totalContentHeight = lines.size() * 10;
            for (OrderedText line : lines) {
                context.drawText(this.textRenderer, line, this.scrollAreaX, textY, 0xFFFFFFFF, false);
                textY += 10;
            }
            context.disableScissor();

            if (this.totalContentHeight > this.scrollAreaHeight) {
                int scrollBarWidth = 6;
                this.scrollBarHeight = Math.max(20, this.scrollAreaHeight * this.scrollAreaHeight / this.totalContentHeight);
                this.scrollBarX = this.scrollAreaX + this.scrollAreaWidth - scrollBarWidth - 2;
                this.scrollBarY = this.scrollAreaY + (int)((float)descriptionScrollPos / (this.totalContentHeight - this.scrollAreaHeight)
                        * (this.scrollAreaHeight - this.scrollBarHeight));
                context.fill(this.scrollBarX, this.scrollAreaY, this.scrollBarX + scrollBarWidth, this.scrollAreaY + this.scrollAreaHeight, 0x33FFFFFF);
                boolean isHovering = mouseX >= this.scrollBarX && mouseX <= this.scrollBarX + scrollBarWidth &&
                        mouseY >= this.scrollBarY && mouseY <= this.scrollBarY + this.scrollBarHeight;
                int scrollBarColor = isHovering || isScrolling ? 0xFFFFFFFF : 0xAAFFFFFF;
                context.fill(this.scrollBarX, this.scrollBarY, this.scrollBarX + scrollBarWidth, this.scrollBarY + this.scrollBarHeight, scrollBarColor);
            }
        }

        if (backButton != null) backButton.render(context, mouseX, mouseY, delta);
        if (downloadButton != null) downloadButton.render(context, mouseX, mouseY, delta);

        // Render dropdown overlay last
        if (showFileDropdown && !fileDropdownItems.isEmpty()) {
            renderFileDropdown(context, mouseX, mouseY);
        }

        ToastManager.render(context, this.width);
    }

    private void renderFileDropdown(DrawContext context, int mouseX, int mouseY) {
        int x1 = fileDropdownX;
        int y1 = fileDropdownY;
        int x2 = fileDropdownX + fileDropdownWidth;
        int y2 = fileDropdownY + fileDropdownHeight;
        context.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xAA000000);
        context.fill(x1, y1, x2, y2, 0xEE101010);
        context.enableScissor(x1, y1, x2, y2);

        int itemHeight = 18;
        int startIndex = Math.max(0, fileDropdownScroll / itemHeight);
        int endIndex = Math.min(fileDropdownItems.size(), startIndex + (fileDropdownHeight / itemHeight) + 1);
        int drawY = y1 - (fileDropdownScroll % itemHeight);

        for (int i = startIndex; i < endIndex; i++) {
            MinemevFileInfo f = fileDropdownItems.get(i);
            int itemTop = drawY + (i - startIndex) * itemHeight;
            boolean hovered = mouseX >= x1 && mouseX <= x2 && mouseY >= itemTop && mouseY <= itemTop + itemHeight;
            if (hovered) context.fill(x1, itemTop, x2, itemTop + itemHeight, 0x33FFFFFF);
            String name = (f.getFileName() != null && !f.getFileName().isEmpty()) ? f.getFileName() : "Unnamed";
            String sizeStr = f.getFileSize() > 0 ? (" • " + formatSize(f.getFileSize())) : "";
            String ver = joinArrayShort(f.getVersions());
            String line = trimToWidth(name, fileDropdownWidth - 20) + sizeStr + (ver.isEmpty() ? "" : (" • " + ver));
            context.drawText(this.textRenderer, line, x1 + 6, itemTop + 4, 0xFFFFFFFF, false);
        }

        context.disableScissor();

        if (fileDropdownContentHeight > fileDropdownHeight) {
            int scrollBarWidth = 6;
            int barHeight = Math.max(20, fileDropdownHeight * fileDropdownHeight / fileDropdownContentHeight);
            int barX = x2 - scrollBarWidth - 2;
            int barY = y1 + (int)((float)fileDropdownScroll / (fileDropdownContentHeight - fileDropdownHeight)
                    * (fileDropdownHeight - barHeight));
            context.fill(barX, y1, barX + scrollBarWidth, y2, 0x33FFFFFF);
            boolean isHovering = mouseX >= barX && mouseX <= barX + scrollBarWidth && mouseY >= barY && mouseY <= barY + barHeight;
            int color = isHovering ? 0xFFFFFFFF : 0xAAFFFFFF;
            context.fill(barX, barY, barX + scrollBarWidth, barY + barHeight, color);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Dropdown interaction first
        if (showFileDropdown) {
            // Handle dragging on dropdown scrollbar
            if (fileDropdownContentHeight > fileDropdownHeight && button == 0) {
                int barWidth = 6;
                int barX = fileDropdownX + fileDropdownWidth - barWidth - 2;
                int barHeight = Math.max(20, fileDropdownHeight * fileDropdownHeight / Math.max(1, fileDropdownContentHeight));
                int barY = fileDropdownY + (int)((float)fileDropdownScroll / Math.max(1, (fileDropdownContentHeight - fileDropdownHeight))
                        * (fileDropdownHeight - barHeight));
                if (mouseX >= barX && mouseX <= barX + barWidth && mouseY >= barY && mouseY <= barY + barHeight) {
                    isDraggingFileDropdown = true;
                    fileDropdownDragOffset = (int)(mouseY - barY);
                    return true;
                }
                // Click on track jumps
                if (mouseX >= barX && mouseX <= barX + barWidth && mouseY >= fileDropdownY && mouseY <= fileDropdownY + fileDropdownHeight) {
                    float clickPercent = (float)(mouseY - fileDropdownY) / Math.max(1, fileDropdownHeight);
                    fileDropdownScroll = (int)(clickPercent * (fileDropdownContentHeight - fileDropdownHeight));
                    fileDropdownScroll = Math.max(0, Math.min(fileDropdownContentHeight - fileDropdownHeight, fileDropdownScroll));
                    return true;
                }
            }
            if (handleDropdownClick(mouseX, mouseY, button)) {
                return true;
            }
        }

        // Description scrollbar interaction
        if (button == 0 && this.totalContentHeight > this.scrollAreaHeight) { // Left click only when scrollable
            int scrollBarWidth = 6;
            // Click on handle starts dragging
            if (mouseX >= this.scrollBarX && mouseX <= this.scrollBarX + scrollBarWidth &&
                    mouseY >= this.scrollBarY && mouseY <= this.scrollBarY + this.scrollBarHeight) {
                this.isScrolling = true;
                this.lastMouseY = (int) mouseY;
                this.scrollDragOffset = (int)(mouseY - this.scrollBarY);
                return true;
            }
            // Click on track jumps to position
            if (mouseX >= this.scrollBarX && mouseX <= this.scrollBarX + scrollBarWidth &&
                    mouseY >= this.scrollAreaY && mouseY <= this.scrollAreaY + this.scrollAreaHeight) {
                float clickPercent = (float)((mouseY - this.scrollAreaY)) / Math.max(1, this.scrollAreaHeight);
                this.descriptionScrollPos = (int)(clickPercent * (this.totalContentHeight - this.scrollAreaHeight));
                this.descriptionScrollPos = Math.max(0, Math.min(this.totalContentHeight - this.scrollAreaHeight, this.descriptionScrollPos));
                return true;
            }
        }

        // Removed in-page files list click handling; users should use the download button to open the files dropdown

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
        double mouseY = click.y();

        // Handle dragging for description scrollbar
        if (this.isScrolling && this.totalContentHeight > this.scrollAreaHeight) {
            int newBarTop = (int)mouseY - this.scrollDragOffset;
            int minBarTop = this.scrollAreaY;
            int maxBarTop = this.scrollAreaY + this.scrollAreaHeight - this.scrollBarHeight;
            newBarTop = Math.max(minBarTop, Math.min(maxBarTop, newBarTop));

            float percent = (float)(newBarTop - this.scrollAreaY) / Math.max(1, (this.scrollAreaHeight - this.scrollBarHeight));
            this.descriptionScrollPos = (int)(percent * (this.totalContentHeight - this.scrollAreaHeight));
            return true;
        }

        // Handle dragging for file dropdown scrollbar
        if (isDraggingFileDropdown && fileDropdownContentHeight > fileDropdownHeight) {
            int newBarTop = (int)mouseY - fileDropdownDragOffset;
            int minBarTop = fileDropdownY;
            int barHeight = Math.max(20, fileDropdownHeight * fileDropdownHeight / Math.max(1, fileDropdownContentHeight));
            int maxBarTop = fileDropdownY + fileDropdownHeight - barHeight;
            newBarTop = Math.max(minBarTop, Math.min(maxBarTop, newBarTop));

            float percent = (float)(newBarTop - fileDropdownY) / Math.max(1, (fileDropdownHeight - barHeight));
            fileDropdownScroll = (int)(percent * (fileDropdownContentHeight - fileDropdownHeight));
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        int button = click.button();
        if (button == 0) {
            if (this.isScrolling) {
                this.isScrolling = false;
                return true;
            }
            if (this.isDraggingFileDropdown) {
                this.isDraggingFileDropdown = false;
                return true;
            }
        }
        return super.mouseReleased(click);
    }

    // Re-added: handle clicks inside the files dropdown list
    private boolean handleDropdownClick(double mouseX, double mouseY, int button) {
        if (mouseX < fileDropdownX || mouseX > fileDropdownX + fileDropdownWidth ||
                mouseY < fileDropdownY || mouseY > fileDropdownY + fileDropdownHeight) {
            showFileDropdown = false;
            return false;
        }
        if (button != 0) return true;

        int itemHeight = 18;
        int relativeY = (int)(mouseY - fileDropdownY + fileDropdownScroll);
        int index = relativeY / itemHeight;
        if (index >= 0 && index < fileDropdownItems.size()) {
            MinemevFileInfo f = fileDropdownItems.get(index);
            startPerFileDownloadWithConfirm(f);
            showFileDropdown = false;
            return true;
        }
        return true;
    }

    private void startPerFileDownloadWithConfirm(MinemevFileInfo file) {
        String chosenName = sanitizeFileName(file.getFileName() != null ? file.getFileName() : postDetail.getTitle());
        if (chosenName.toLowerCase().endsWith(".litematic")) {
            chosenName = chosenName.substring(0, chosenName.length() - ".litematic".length());
        }
        String schematicsPath = SettingsManager.getSchematicsPath();
        File potentialFile = new File(schematicsPath + File.separator + chosenName + ".litematic");
        if (potentialFile.exists()) {
            final String finalName = chosenName;
            final MinemevFileInfo finalFile = file;
            ConfirmationScreen confirmationScreen = new ConfirmationScreen(
                    Text.literal("File already exists"),
                    Text.literal("The file \"" + finalName + ".litematic\" already exists. Replace it?"),
                    (confirmed) -> {
                        if (confirmed) performMinemevFileDownload(finalFile, finalName);
                        MinecraftClient.getInstance().setScreen(this);
                    }
            );
            MinecraftClient.getInstance().setScreen(confirmationScreen);
        } else {
            performMinemevFileDownload(file, chosenName);
        }
    }

    private void performMinemevFileDownload(MinemevFileInfo file, String chosenName) {
        try {
            String filenameForResolver = (file.getFileName() != null && !file.getFileName().isEmpty())
                    ? file.getFileName() : (chosenName + ".litematic");
            String resolvedUrl = MinemevHttpClient.getDownloadUrl(this.postUuid, filenameForResolver, file.getDownloadUrl());
            String filePath = LitematicHttpClient.downloadFileFromUrl(resolvedUrl, chosenName);
            String schematicsPath = SettingsManager.getSchematicsPath();
            String relativePath;
            if (filePath.startsWith(schematicsPath)) {
                String pathAfterBase = filePath.substring(schematicsPath.length());
                if (pathAfterBase.startsWith(File.separator)) {
                    pathAfterBase = pathAfterBase.substring(File.separator.length());
                }
                String folderName = new File(schematicsPath).getName();
                relativePath = folderName + "/" + pathAfterBase.replace(File.separator, "/");
            } else {
                String folderName = new File(schematicsPath).getName();
                relativePath = folderName + "/" + chosenName + ".litematic";
            }
            ToastManager.addToast("Schematic downloaded to: " + relativePath, false);
        } catch (Exception e) {
            ToastManager.addToast("Failed to download: " + e.getMessage(), true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Dropdown scroll handling
        if (showFileDropdown && mouseX >= fileDropdownX && mouseX <= fileDropdownX + fileDropdownWidth &&
                mouseY >= fileDropdownY && mouseY <= fileDropdownY + fileDropdownHeight &&
                fileDropdownContentHeight > fileDropdownHeight) {
            int delta = (int)(-verticalAmount * 20);
            fileDropdownScroll = Math.max(0, Math.min(fileDropdownContentHeight - fileDropdownHeight, fileDropdownScroll + delta));
            return true;
        }

        if (mouseX >= this.scrollAreaX && mouseX <= this.scrollAreaX + this.scrollAreaWidth &&
                mouseY >= this.scrollAreaY && mouseY <= this.scrollAreaY + this.scrollAreaHeight) {
            if (this.totalContentHeight > this.scrollAreaHeight) {
                double amount = verticalAmount != 0.0 ? verticalAmount : horizontalAmount;
                int scrollAmount = (int) (-amount * 20);
                this.descriptionScrollPos = Math.max(0, Math.min(this.totalContentHeight - this.scrollAreaHeight, this.descriptionScrollPos + scrollAmount));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void drawLoadingAnimation(DrawContext context, int centerX, int centerY) {
        int radius = 12;
        int segments = 8;
        int animationDuration = 1600; // Full rotation time in ms
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - loadingStartTime;
        float rotation = (elapsedTime % animationDuration) / (float) animationDuration;
        for (int i = 0; i < segments; i++) {
            float angle = (float) (i * 2 * Math.PI / segments);
            angle += rotation * 2 * Math.PI;
            int x1 = centerX + (int)(Math.sin(angle) * (radius - 3));
            int y1 = centerY + (int)(Math.cos(angle) * (radius - 3));
            int x2 = centerX + (int)(Math.sin(angle) * radius);
            int y2 = centerY + (int)(Math.cos(angle) * radius);
            int alpha = 255 - (i * 255 / segments);
            int color = 0xFFFFFF | (alpha << 24);
            context.fill(x1, y1, x2 + 1, y2 + 1, color);
        }
    }

    private String detectImageFormat(byte[] imageData) {
        if (imageData.length < 12) return "unknown";
        if (imageData[0] == (byte)0x89 && imageData[1] == 0x50 && imageData[2] == 0x4E && imageData[3] == 0x47) return "png";
        if (imageData[0] == (byte)0xFF && imageData[1] == (byte)0xD8 && imageData[2] == (byte)0xFF) return "jpeg";
        if (imageData.length >= 6 && imageData[0] == 0x47 && imageData[1] == 0x49 && imageData[2] == 0x46) return "gif";
        if (imageData[0] == 0x42 && imageData[1] == 0x4D) return "bmp";
        if (imageData.length >= 12 && imageData[0] == 0x52 && imageData[1] == 0x49 && imageData[2] == 0x46 && imageData[3] == 0x46 && imageData[8] == 0x57 && imageData[9] == 0x45 && imageData[10] == 0x42 && imageData[11] == 0x50) return "webp";
        if (imageData.length >= 4 && ((imageData[0] == 0x49 && imageData[1] == 0x49 && imageData[2] == 0x2A && imageData[3] == 0x00) || (imageData[0] == 0x4D && imageData[1] == 0x4D && imageData[2] == 0x00 && imageData[3] == 0x2A))) return "tiff";
        if (imageData.length >= 4 && imageData[0] == 0x00 && imageData[1] == 0x00 && imageData[2] == 0x01 && imageData[3] == 0x00) return "ico";
        if (imageData.length >= 12 && imageData[4] == 0x66 && imageData[5] == 0x74 && imageData[6] == 0x79 && imageData[7] == 0x70 && imageData[8] == 0x61 && imageData[9] == 0x76 && imageData[10] == 0x69 && (imageData[11] == 0x66 || imageData[11] == 0x73)) return "avif";
        if (imageData.length >= 12 && imageData[4] == 0x66 && imageData[5] == 0x74 && imageData[6] == 0x79 && imageData[7] == 0x70) {
            if ((imageData[8] == 0x68 && imageData[9] == 0x65 && imageData[10] == 0x69 && imageData[11] == 0x63) || (imageData[8] == 0x6D && imageData[9] == 0x69 && imageData[10] == 0x66 && imageData[11] == 0x31)) return "heif";
        }
        if (imageData.length >= 5) {
            String startString = new String(imageData, 0, Math.min(100, imageData.length)).toLowerCase();
            if (startString.contains("<svg") || (startString.startsWith("<?xml") && startString.contains("svg"))) return "svg";
        }
        return "unknown";
    }

    private byte[] convertImageToPng(byte[] imageData, String format) throws Exception {
        if (format.equals("png") || format.equals("unknown")) return imageData;
        if (format.equals("svg")) throw new Exception("SVG format is not supported for conversion");
        if (format.equals("avif") || format.equals("heif")) throw new Exception(format.toUpperCase() + " format is not supported by Java ImageIO");
        try {
            java.awt.image.BufferedImage bufferedImage = null;
            try { bufferedImage = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageData)); } catch (Exception ignored) {}
            if (bufferedImage == null && !format.equals("unknown")) {
                try {
                    java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReadersByFormatName(format.toUpperCase());
                    if (readers.hasNext()) {
                        javax.imageio.ImageReader reader = readers.next();
                        javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(imageData));
                        reader.setInput(iis);
                        bufferedImage = reader.read(0);
                        reader.dispose();
                        iis.close();
                    }
                } catch (Exception ignored) {}
            }
            if (bufferedImage == null) throw new Exception("Could not decode " + format + " image");
            java.awt.image.BufferedImage convertedImage;
            if (bufferedImage.getType() != java.awt.image.BufferedImage.TYPE_INT_RGB && bufferedImage.getType() != java.awt.image.BufferedImage.TYPE_INT_ARGB) {
                convertedImage = new java.awt.image.BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2d = convertedImage.createGraphics();
                g2d.drawImage(bufferedImage, 0, 0, null);
                g2d.dispose();
            } else {
                convertedImage = bufferedImage;
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            boolean success = javax.imageio.ImageIO.write(convertedImage, "PNG", baos);
            if (!success) throw new Exception("Failed to write PNG output");
            return baos.toByteArray();
        } catch (Exception e) {
            if (format.equals("svg") || format.equals("avif") || format.equals("heif")) throw e;
            return imageData;
        }
    }

    private void createAndRegisterPlaceholder(String reason) {
        try {
            String uniqueId = UUID.randomUUID().toString().replace("-", "");
            coverImageTexture = Identifier.of("minecraft", "textures/dynamic/" + uniqueId);
            NativeImage nativeImage = createPlaceholderImage(256, 256, reason);
            if (nativeImage != null) {
                imageWidth = nativeImage.getWidth();
                imageHeight = nativeImage.getHeight();
                MinecraftClient.getInstance().getTextureManager().registerTexture(
                        coverImageTexture,
                        new NativeImageBackedTexture(() -> "minemev_placeholder", nativeImage)
                );
            }
        } catch (Exception e) {
            coverImageTexture = null;
        }
    }

    private NativeImage createPlaceholderImage(int width, int height, String reason) {
        try {
            return new NativeImage(NativeImage.Format.RGBA, width, height, false);
        } catch (Exception e) {
            return null;
        }
    }

    private String encodeImageUrl(String url) {
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            String encodedPath = java.net.URLEncoder.encode(parsedUrl.getPath(), "UTF-8").replace("%2F", "/").replace("+", "%20");
            return parsedUrl.getProtocol() + "://" + parsedUrl.getHost() + (parsedUrl.getPort() != -1 ? ":" + parsedUrl.getPort() : "") + encodedPath;
        } catch (Exception e) {
            return url.replace(" ", "%20");
        }
    }

    private String joinArray(String[] arr) {
        if (arr == null || arr.length == 0) return "";
        java.util.List<String> items = new java.util.ArrayList<>();
        for (String s : arr) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) items.add(t);
        }
        if (items.isEmpty()) return "";
        return String.join(", ", items);
    }

    private String joinArrayShort(String[] arr) {
        if (arr == null || arr.length == 0) return "";
        java.util.List<String> items = new java.util.ArrayList<>();
        for (String s : arr) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) items.add(t);
        }
        return String.join(", ", items);
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "";
        String[] units = {"B", "KB", "MB", "GB"};
        double b = (double) bytes;
        int idx = 0;
        while (b >= 1024 && idx < units.length - 1) { b /= 1024.0; idx++; }
        return String.format(java.util.Locale.ROOT, "%.1f %s", b, units[idx]);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (this.textRenderer.getWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = this.textRenderer.getWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String next = sb.toString() + text.charAt(i);
            if (this.textRenderer.getWidth(next) + ellipsisWidth > maxWidth) break;
            sb.append(text.charAt(i));
        }
        return sb.append(ellipsis).toString();
    }

    private String sanitizeFileName(String name) {
        String n = (name == null || name.isEmpty()) ? "minemev_schematic" : name;
        n = n.replaceAll("[^a-zA-Z0-9.-]", "_");
        return n;
    }
}


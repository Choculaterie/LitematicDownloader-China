package com.choculaterie.gui.widget;

import com.choculaterie.gui.theme.UITheme;
import com.choculaterie.config.DownloadSettings;
import com.choculaterie.models.MinemevFileInfo;
import com.choculaterie.models.MinemevPostDetailInfo;
import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.network.MinemevNetworkManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

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


public class PostDetailPanel implements Drawable, Element {
    
    
    
    private static final int TAG_BG_COLOR = UITheme.Colors.BUTTON_BG;
    
    private static final int MAX_IMAGE_SIZE = 200;
    private static final int MIN_IMAGE_SIZE = 80;
    

    private int x;
    private int y;
    private int width;
    private int height;

    private MinemevPostInfo postInfo;
    private MinemevPostDetailInfo postDetail;
    private boolean isLoadingDetails = false;
    private boolean isLoadingImage = false;
    private boolean imageLoadFailed = false;
    private String imageLoadErrorMessage = null;

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

    private final LoadingSpinner imageLoadingSpinner;

    private CustomButton prevImageButton;
    private CustomButton nextImageButton;

    private ScrollBar scrollBar;

    private CustomButton downloadButton;
    private DropdownWidget schematicDropdown;
    private MinemevFileInfo[] availableFiles;
    private boolean isLoadingFiles = false;
    private String downloadStatus = "";
    private LitematicaGenWidget activeGenPopup;
    private boolean pendingGenWidgetOpen = false;

    private ImageViewerWidget imageViewer;
    private ConfirmPopup confirmPopup;
    private Screen parentScreen;

    private static class ClickableLink {
        final int x, y, width, height;
        final String url;

        ClickableLink(int x, int y, int width, int height, String url) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.url = url;
        }

        boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final List<ClickableLink> clickableLinks = new ArrayList<>();

    public PostDetailPanel(int x, int y, int width, int height, Screen parentScreen) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = MinecraftClient.getInstance();
        this.parentScreen = parentScreen;
        this.imageLoadingSpinner = new LoadingSpinner(0, 0);
        int scrollBarYOffset = 30;
        this.scrollBar = new ScrollBar(x + width - 8, y + scrollBarYOffset, height - scrollBarYOffset);
        this.schematicDropdown = new DropdownWidget(x, y, width - UITheme.Dimensions.PADDING * 2, this::onSchematicSelected);
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        int scrollBarYOffset = 30;
        this.scrollBar = new ScrollBar(x + width - 8, y + scrollBarYOffset, height - scrollBarYOffset);
        if (schematicDropdown != null) {
            schematicDropdown.setPosition(x, y);
            if (schematicDropdown.isOpen()) {
                schematicDropdown.close();
            }
        }
    }

    public void closeDropdown() {
        if (schematicDropdown != null && schematicDropdown.isOpen()) {
            schematicDropdown.close();
        }
    }

    private int getDisplayImageWidth() {
        return width - 10;
    }

    private int getDisplayImageHeight() {
        return MAX_IMAGE_SIZE;
    }

    private int getActualImageWidth() {
        if (originalImageWidth <= 0 || originalImageHeight <= 0) {
            return getDisplayImageWidth();
        }

        int containerWidth = getDisplayImageWidth();
        int containerHeight = getDisplayImageHeight();

        int widthAtContainerHeight = (int) ((float) originalImageWidth / originalImageHeight * containerHeight);

        if (widthAtContainerHeight <= containerWidth) {
            return Math.min(originalImageWidth, widthAtContainerHeight);
        } else {
            return Math.min(originalImageWidth, containerWidth);
        }
    }

    private int getActualImageHeight() {
        if (originalImageWidth <= 0 || originalImageHeight <= 0) {
            return getDisplayImageHeight();
        }

        int containerHeight = getDisplayImageHeight();

        int actualWidth = getActualImageWidth();
        int calculatedHeight = (int) ((float) originalImageHeight / originalImageWidth * actualWidth);

        return Math.min(originalImageHeight, Math.min(containerHeight, calculatedHeight));
    }

    private boolean isCompactMode() {
        return width < 200;
    }

    private void updateCarouselButtons(int imageNavY) {
        if (imageUrls == null || imageUrls.length <= 1) {
            prevImageButton = null;
            nextImageButton = null;
            return;
        }

        boolean compact = isCompactMode();
        int btnWidth = compact ? 18 : 25;
        int btnHeight = compact ? 14 : 16;
        int btnSpacing = compact ? 5 : 10;

        String indicator = String.format("%d / %d", currentImageIndex + 1, imageUrls.length);
        int indicatorWidth = client.textRenderer.getWidth(indicator);
        int indicatorX = x + (width - indicatorWidth) / 2;

        int prevBtnX = indicatorX - btnWidth - btnSpacing;
        int nextBtnX = indicatorX + indicatorWidth + btnSpacing;

        if (prevImageButton == null) {
            prevImageButton = new CustomButton(prevBtnX, imageNavY, btnWidth, btnHeight,
                    Text.of("<"), btn -> previousImage());
        } else {
            prevImageButton.setX(prevBtnX);
            prevImageButton.setY(imageNavY);
            prevImageButton.setWidth(btnWidth);
        }

        if (nextImageButton == null) {
            nextImageButton = new CustomButton(nextBtnX, imageNavY, btnWidth, btnHeight,
                    Text.of(">"), btn -> nextImage());
        } else {
            nextImageButton.setX(nextBtnX);
            nextImageButton.setY(imageNavY);
            nextImageButton.setWidth(btnWidth);
        }
    }

    public void setPost(MinemevPostInfo post) {
        System.out.println("[PostDetailPanel] setPost called!");
        System.out.println("[PostDetailPanel] Post: " + (post != null ? post.title() : "null"));

        if (post == null) {
            System.out.println("[PostDetailPanel] Post is null, clearing panel");
            clear();
            return;
        }

        if (this.postInfo != null && post.uuid() != null && post.uuid().equals(this.postInfo.uuid())) {
            System.out.println("[PostDetailPanel] Same post already loaded, skipping reload");
            return;
        }

        System.out.println("[PostDetailPanel] Setting post: " + post.title());
        System.out.println("[PostDetailPanel] UUID: " + post.uuid());
        System.out.println("[PostDetailPanel] Vendor: " + post.vendor());

        clearDownloadState();

        this.postInfo = post;
        this.postDetail = null;
        this.isLoadingDetails = true;
        this.currentImageIndex = 0;
        this.currentImageTexture = null;
        this.originalImageWidth = 0;
        this.originalImageHeight = 0;
        this.scrollOffset = 0;
        this.imageLoadFailed = false;
        this.imageLoadErrorMessage = null;
        this.pendingGenWidgetOpen = false;
        this.activeGenPopup = null;

        System.out.println("[PostDetailPanel] Post info set, loading details...");

        String vendor = post.vendor() != null ? post.vendor() : "minemev";
        String uuid = post.uuid();

        if (uuid != null && uuid.contains("/")) {
            String[] parts = uuid.split("/", 2);
            if (parts.length == 2) {
                vendor = parts[0];
                uuid = parts[1];
                System.out.println("[PostDetailPanel] Stripped vendor prefix - vendor: " + vendor + ", uuid: " + uuid);
            }
        }

        MinemevNetworkManager.getPostDetails(uuid)
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

        if (post.images() != null && post.images().length > 0) {
            System.out.println("[PostDetailPanel] Found " + post.images().length + " images");
            this.imageUrls = post.images();
            loadImage(imageUrls[0]);
        } else if (post.thumbnailUrl() != null && !post.thumbnailUrl().isEmpty()) {
            System.out.println("[PostDetailPanel] Using thumbnail URL");
            this.imageUrls = new String[]{post.thumbnailUrl()};
            loadImage(post.thumbnailUrl());
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

                if (detail.getImages() != null && detail.getImages().length > 0) {
                    this.imageUrls = detail.getImages();
                    if (currentImageIndex >= imageUrls.length) {
                        currentImageIndex = 0;
                    }
                    preloadImages(imageUrls);
                    if (currentImageTexture == null && imageUrls.length > 0) {
                        loadImage(imageUrls[currentImageIndex]);
                    }
                }

                if (pendingGenWidgetOpen && "LitematicaGen".equals(postInfo.type()) && postDetail != null) {
                    pendingGenWidgetOpen = false;
                    activeGenPopup = new LitematicaGenWidget(
                        postInfo.uuid(),
                        postInfo.title(),
                        postDetail.getConditions(),
                        postDetail.hasX(),
                        postDetail.hasY(),
                        postDetail.hasZ(),
                        () -> activeGenPopup = null
                    );
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
        System.out.println("[PostDetailPanel] loadImage called for URL: " + imageUrl);

        if (imageUrl == null || imageUrl.isEmpty()) {
            System.out.println("[PostDetailPanel] Image URL is null or empty, skipping");
            return;
        }

        if (imageCache.containsKey(imageUrl)) {
            System.out.println("[PostDetailPanel] Image already in cache, using cached version");
            currentImageTexture = imageCache.get(imageUrl);
            int[] dims = imageDimensionsCache.get(imageUrl);
            if (dims != null) {
                originalImageWidth = dims[0];
                originalImageHeight = dims[1];
                System.out.println("[PostDetailPanel] Cached image dimensions: " + originalImageWidth + "x" + originalImageHeight);
            }
            isLoadingImage = false;
            imageLoadFailed = false;
            imageLoadErrorMessage = null;
            return;
        }

        System.out.println("[PostDetailPanel] Image not in cache, starting async load");
        isLoadingImage = true;
        imageLoadFailed = false;
        imageLoadErrorMessage = null;
        loadingImageUrl = imageUrl;

        new Thread(() -> {
            try {
                System.out.println("[PostDetailPanel] Background thread started for image: " + imageUrl);
                Identifier texId = loadImageSync(imageUrl);
                if (client != null) {
                    client.execute(() -> {
                        if (imageUrl.equals(loadingImageUrl)) {
                            System.out.println("[PostDetailPanel] Setting current texture to loaded image");
                            currentImageTexture = texId;
                            int[] dims = imageDimensionsCache.get(imageUrl);
                            if (dims != null) {
                                originalImageWidth = dims[0];
                                originalImageHeight = dims[1];
                            }
                            isLoadingImage = false;
                            imageLoadFailed = false;
                            imageLoadErrorMessage = null;
                            System.out.println("[PostDetailPanel] Image load complete and applied");
                        } else {
                            System.out.println("[PostDetailPanel] Image load completed but URL no longer matches current");
                        }
                    });
                }
            } catch (java.net.ConnectException e) {
                System.err.println("[PostDetailPanel] Connection error loading image: Cannot connect to server");
                System.err.println("[PostDetailPanel] URL: " + imageUrl);
                if (client != null) {
                    client.execute(() -> {
                        isLoadingImage = false;
                        currentImageTexture = null;
                        imageLoadFailed = true;
                        imageLoadErrorMessage = "Cannot connect";
                    });
                }
            } catch (java.nio.channels.UnresolvedAddressException e) {
                System.err.println("[PostDetailPanel] Cannot resolve address for image URL: " + imageUrl);
                if (client != null) {
                    client.execute(() -> {
                        isLoadingImage = false;
                        currentImageTexture = null;
                        imageLoadFailed = true;
                        imageLoadErrorMessage = "Cannot resolve address";
                    });
                }
            } catch (java.net.UnknownHostException e) {
                System.err.println("[PostDetailPanel] Unknown host for image URL: " + imageUrl);
                if (client != null) {
                    client.execute(() -> {
                        isLoadingImage = false;
                        currentImageTexture = null;
                        imageLoadFailed = true;
                        imageLoadErrorMessage = "Unknown host";
                    });
                }
            } catch (java.net.http.HttpTimeoutException e) {
                System.err.println("[PostDetailPanel] Timeout loading image: " + imageUrl);
                if (client != null) {
                    client.execute(() -> {
                        isLoadingImage = false;
                        currentImageTexture = null;
                        imageLoadFailed = true;
                        imageLoadErrorMessage = "Timeout";
                    });
                }
            } catch (Exception e) {
                System.err.println("[PostDetailPanel] Error type: " + e.getClass().getName());
                e.printStackTrace();
                if (client != null) {
                    String errorMsg = e.getMessage();
                    if (errorMsg == null) {
                        errorMsg = e.getClass().getSimpleName();
                    }
                    if (errorMsg.length() > 30) {
                        errorMsg = errorMsg.substring(0, 27) + "...";
                    }
                    final String finalErrorMsg = errorMsg;
                    client.execute(() -> {
                        isLoadingImage = false;
                        currentImageTexture = null;
                        imageLoadFailed = true;
                        imageLoadErrorMessage = finalErrorMsg;
                        System.err.println("[PostDetailPanel] Failed to load image: " + finalErrorMsg);
                    });
                }
            }
        }).start();
    }

    private Identifier loadImageSync(String imageUrl) throws Exception {
        System.out.println("[PostDetailPanel] loadImageSync called for URL: " + imageUrl);

        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new Exception("Image URL is null or empty");
        }

        if (imageCache.containsKey(imageUrl)) {
            System.out.println("[PostDetailPanel] Image found in cache: " + imageUrl);
            return imageCache.get(imageUrl);
        }

        String encodedUrl;
        try {
            encodedUrl = encodeImageUrl(imageUrl);
            System.out.println("[PostDetailPanel] Encoded URL: " + encodedUrl);
        } catch (Exception e) {
            System.err.println("[PostDetailPanel] Failed to encode URL: " + imageUrl);
            throw new Exception("Failed to encode URL");
        }

        long startTime = System.currentTimeMillis();

        HttpClient httpClient = null;
        try {
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(encodedUrl))
                .GET()
                .header("User-Agent", "LitematicDownloader/1.0");

            if (encodedUrl.contains("static.redenmc.com")) {
                System.out.println("[PostDetailPanel] Adding Referer header for redenmc.com");
                requestBuilder.header("Referer", "https://www.minemev.com");
            }

            HttpRequest request = requestBuilder.build();
            System.out.println("[PostDetailPanel] Sending HTTP request...");

            HttpResponse<byte[]> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (java.net.ConnectException e) {
                throw new Exception("Cannot connect to server");
            } catch (java.nio.channels.UnresolvedAddressException e) {
                throw new Exception("Cannot resolve address");
            } catch (java.net.UnknownHostException e) {
                throw new Exception("Unknown host");
            } catch (java.net.http.HttpTimeoutException e) {
                throw new Exception("Request timeout");
            }

            long requestTime = System.currentTimeMillis() - startTime;
            System.out.println("[PostDetailPanel] HTTP response received: status=" + response.statusCode() + ", size=" + response.body().length + " bytes, time=" + requestTime + "ms");

            if (response.statusCode() != 200) {
                System.err.println("[PostDetailPanel] HTTP error loading image: " + response.statusCode());
                throw new Exception("HTTP error " + response.statusCode() + " for URL: " + encodedUrl);
            }

            byte[] imageData = response.body();
            if (imageData == null || imageData.length == 0) {
                throw new Exception("Empty response body from: " + encodedUrl);
            }

            System.out.println("[PostDetailPanel] Converting image to PNG format...");
            byte[] pngBytes;
            try {
                pngBytes = convertImageToPng(imageData);
                if (pngBytes == null || pngBytes.length == 0) {
                    throw new Exception("PNG conversion resulted in empty data");
                }
                System.out.println("[PostDetailPanel] PNG conversion complete, size: " + pngBytes.length + " bytes");
            } catch (Exception e) {
                System.err.println("[PostDetailPanel] Failed to convert image to PNG: " + e.getMessage());
                throw new Exception("Failed to convert image to PNG");
            }

            System.out.println("[PostDetailPanel] Creating NativeImage...");
            NativeImage nativeImage;
            try {
                nativeImage = NativeImage.read(new ByteArrayInputStream(pngBytes));
                if (nativeImage == null) {
                    throw new Exception("NativeImage.read returned null");
                }
            } catch (Exception e) {
                System.err.println("[PostDetailPanel] Failed to create NativeImage: " + e.getMessage());
                throw new Exception("Failed to create NativeImage");
            }

            int imgWidth = nativeImage.getWidth();
            int imgHeight = nativeImage.getHeight();
            System.out.println("[PostDetailPanel] Image dimensions: " + imgWidth + "x" + imgHeight);

            if (imgWidth <= 0 || imgHeight <= 0) {
                System.err.println("[PostDetailPanel] Invalid image dimensions: " + imgWidth + "x" + imgHeight);
                nativeImage.close();
                throw new Exception("Invalid image dimensions: " + imgWidth + "x" + imgHeight);
            }

            if (imgWidth > 4096 || imgHeight > 4096) {
                System.err.println("[PostDetailPanel] Image too large: " + imgWidth + "x" + imgHeight);
                nativeImage.close();
                throw new Exception("Image too large: " + imgWidth + "x" + imgHeight + " (max: 4096x4096)");
            }

            imageDimensionsCache.put(imageUrl, new int[]{imgWidth, imgHeight});

            final NativeImage finalImage = nativeImage;
            final String uniqueId = UUID.randomUUID().toString().replace("-", "");
            final Identifier texId = Identifier.of("litematicdownloader", "textures/dynamic/" + uniqueId);
            System.out.println("[PostDetailPanel] Generated texture ID: " + texId);

            if (client != null) {
                try {
                    client.execute(() -> {
                        try {
                            System.out.println("[PostDetailPanel] Registering texture on main thread: " + texId);
                            client.getTextureManager().registerTexture(
                                texId,
                                new NativeImageBackedTexture(() -> "minemev_image", finalImage)
                            );
                            imageCache.put(imageUrl, texId);
                            System.out.println("[PostDetailPanel] Texture registered and cached successfully");
                        } catch (Exception e) {
                            System.err.println("[PostDetailPanel] Failed to register texture: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    System.err.println("[PostDetailPanel] Failed to execute on client thread: " + e.getMessage());
                    nativeImage.close();
                    throw new Exception("Failed to register texture: " + e.getMessage(), e);
                }
            } else {
                System.err.println("[PostDetailPanel] Client is null, cannot register texture");
                nativeImage.close();
                throw new Exception("Minecraft client is null");
            }

            Thread.sleep(50);

            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("[PostDetailPanel] Image load complete (total time: " + totalTime + "ms)");

            return texId;
        } catch (Exception e) {
            System.err.println("[PostDetailPanel] Error in loadImageSync: " + e.getMessage());
            throw e;
        }
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
        }

        if (bufferedImage == null) {
            bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
        }

        if (bufferedImage == null) {
            throw new Exception("Failed to decode image");
        }

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
        System.out.println("[PostDetailPanel] onDownloadButtonClick called");
        if (postInfo == null) {
            System.out.println("[PostDetailPanel] postInfo is null, returning");
            return;
        }

        System.out.println("[PostDetailPanel] postInfo.type() = " + postInfo.type());
        System.out.println("[PostDetailPanel] activeGenPopup = " + activeGenPopup);

        if (activeGenPopup != null) {
            System.out.println("[PostDetailPanel] Closing existing popup");
            activeGenPopup = null;
            return;
        }

        if (schematicDropdown.isOpen()) {
            System.out.println("[PostDetailPanel] Closing dropdown");
            schematicDropdown.close();
            return;
        }

        if ("LitematicaGen".equals(postInfo.type())) {
            System.out.println("[PostDetailPanel] LitematicaGen detected");
            System.out.println("[PostDetailPanel] postDetail = " + postDetail);
            if (postDetail != null) {
                System.out.println("[PostDetailPanel] Creating LitematicaGenWidget");
                activeGenPopup = new LitematicaGenWidget(
                    postInfo.uuid(),
                    postInfo.title(),
                    postDetail.getConditions(),
                    postDetail.hasX(),
                    postDetail.hasY(),
                    postDetail.hasZ(),
                    () -> {
                        System.out.println("[PostDetailPanel] Popup onClose callback");
                        activeGenPopup = null;
                    }
                );
                System.out.println("[PostDetailPanel] Widget created: " + activeGenPopup);
                pendingGenWidgetOpen = false;
            } else {
                System.out.println("[PostDetailPanel] postDetail is null, setting pendingGenWidgetOpen");
                pendingGenWidgetOpen = true;
            }
            return;
        }

        System.out.println("[PostDetailPanel] Not LitematicaGen, checking files");

        if (isLoadingFiles) {
            System.out.println("[PostDetailPanel] Already loading files");
            return;
        }

        if (availableFiles != null && availableFiles.length > 0) {
            showSchematicDropdown();
            return;
        }

        isLoadingFiles = true;
        downloadStatus = "";

        String vendor = postInfo.vendor() != null ? postInfo.vendor() : "minemev";
        String uuid = postInfo.uuid();

        if (uuid != null && uuid.contains("/")) {
            String[] parts = uuid.split("/", 2);
            if (parts.length == 2) {
                vendor = parts[0];
                uuid = parts[1];
            }
        }

        MinemevNetworkManager.getPostFiles(uuid)
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
                        String errorMsg = throwable.getMessage();
                        Throwable cause = throwable.getCause();

                        String fullError = errorMsg;
                        if (cause != null && cause.getMessage() != null) {
                            fullError = errorMsg + " " + cause.getMessage();
                        }

                        if (fullError != null && fullError.contains("litematica gen post")) {
                            downloadStatus = "✗ This is a Litematica Gen post (files not supported)";
                        } else if (fullError != null && fullError.contains("UnknownHost")) {
                            downloadStatus = "✗ Error: No internet connection";
                        } else if (fullError != null && fullError.contains("timeout")) {
                            downloadStatus = "✗ Error: Connection timeout";
                        } else {
                            downloadStatus = "✗ Error: Failed to load file list";
                        }
                        System.err.println("[PostDetailPanel] Failed to load files: " + fullError);

                        if (schematicDropdown != null) {
                            schematicDropdown.setItems(new java.util.ArrayList<>());
                            schematicDropdown.setStatusMessage(downloadStatus);
                            if (downloadButton != null) {
                                schematicDropdown.setPosition(
                                    downloadButton.getX(),
                                    downloadButton.getY() + downloadButton.getHeight() + 2
                                );
                            }
                            schematicDropdown.open();
                        }
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
        schematicDropdown.setStatusMessage(downloadStatus);

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
        downloadSchematic(file);
    }

    private void downloadSchematic(MinemevFileInfo file) {
        downloadStatus = "Downloading...";
        if (schematicDropdown != null) {
            schematicDropdown.setStatusMessage(downloadStatus);
        }

        new Thread(() -> {
            try {
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

                String encodedUrl = downloadUrl.replace(" ", "%20");

                HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(encodedUrl))
                    .GET()
                    .header("User-Agent", "LitematicDownloader/1.0");

                if (encodedUrl.contains("static.redenmc.com")) {
                    requestBuilder.header("Referer", "https://www.minemev.com");
                }

                HttpRequest request = requestBuilder.build();

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
        clickableLinks.clear();

        int renderMouseX = mouseX;
        int renderMouseY = mouseY;
        if (activeGenPopup != null || confirmPopup != null) {
            renderMouseX = -1;
            renderMouseY = -1;
        } else if (schematicDropdown != null && schematicDropdown.isOpen() && schematicDropdown.isMouseOver(mouseX, mouseY)) {
            renderMouseX = -1;
            renderMouseY = -1;
        }

        context.fill(x, y, x + width, y + height, UITheme.Colors.PANEL_BG_SECONDARY);

        context.fill(x, y, x + 1, y + height, UITheme.Colors.BUTTON_BORDER);

        if (postInfo == null) {
            String text = "Select a schematic to view details";
            int textWidth = client.textRenderer.getWidth(text);
            context.drawTextWithShadow(client.textRenderer, text,
                x + (width - textWidth) / 2, y + height / 2 - 4, UITheme.Colors.TEXT_SUBTITLE);
            return;
        }

        int downloadBtnSize = 20;
        int downloadBtnWidth = 60;
        int downloadBtnX = x;
        int downloadBtnY = y;

        if (downloadButton == null) {
            downloadButton = new CustomButton(
                    downloadBtnX,
                    downloadBtnY,
                    downloadBtnWidth,
                    downloadBtnSize,
                    Text.of("Download"),
                    button -> onDownloadButtonClick()
            );
            downloadButton.setPrimaryColors(UITheme.Colors.TOGGLE_ON, UITheme.Colors.TOGGLE_ON_HOVER);
        } else {
            downloadButton.setX(downloadBtnX);
            downloadButton.setY(downloadBtnY);
            downloadButton.active = !isLoadingFiles;
        }
        downloadButton.render(context, renderMouseX, renderMouseY, delta);

        int contentStartY = y + downloadBtnSize;
        context.enableScissor(x + 1, contentStartY, x + width, y + height);

        int currentY = contentStartY + UITheme.Dimensions.PADDING - (int) scrollOffset;
        contentHeight = 0;

        int containerWidth = getDisplayImageWidth();
        int containerHeight = getDisplayImageHeight();

        int actualImageWidth = getActualImageWidth();
        int actualImageHeight = getActualImageHeight();

        int containerX = x + 1;
        int containerY = currentY;

        int imageX = containerX + (containerWidth - actualImageWidth) / 2;
        int imageY = containerY + (containerHeight - actualImageHeight) / 2;

        if (isLoadingImage) {
            context.fill(containerX, containerY, containerX + containerWidth, containerY + containerHeight, UITheme.Colors.CONTAINER_BG);
            imageLoadingSpinner.setPosition(
                containerX + containerWidth / 2 - imageLoadingSpinner.getWidth() / 2,
                containerY + containerHeight / 2 - imageLoadingSpinner.getHeight() / 2
            );
            imageLoadingSpinner.render(context, renderMouseX, renderMouseY, delta);
        } else if (currentImageTexture != null) {
            context.fill(containerX, containerY, containerX + containerWidth, containerY + containerHeight, UITheme.Colors.PANEL_BG);
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                currentImageTexture,
                imageX, imageY,
                0, 0,
                actualImageWidth, actualImageHeight,
                actualImageWidth, actualImageHeight
            );
        } else if (imageLoadFailed) {
            context.fill(containerX, containerY, containerX + containerWidth, containerY + containerHeight, UITheme.Colors.CONTAINER_BG);
            String errorLine1 = "Image load failed";
            String errorLine2 = imageLoadErrorMessage != null ? imageLoadErrorMessage : "Unknown error";
            int tw1 = client.textRenderer.getWidth(errorLine1);
            int tw2 = client.textRenderer.getWidth(errorLine2);
            context.drawTextWithShadow(client.textRenderer, errorLine1,
                containerX + (containerWidth - tw1) / 2, containerY + containerHeight / 2 - 10, 0xFFFF5555);
            context.drawTextWithShadow(client.textRenderer, errorLine2,
                containerX + (containerWidth - tw2) / 2, containerY + containerHeight / 2 + 2, UITheme.Colors.TEXT_SUBTITLE);
        } else {
            context.fill(containerX, containerY, containerX + containerWidth, containerY + containerHeight, UITheme.Colors.CONTAINER_BG);
            String noImg = isCompactMode() ? "..." : "No image";
            int tw = client.textRenderer.getWidth(noImg);
            context.drawTextWithShadow(client.textRenderer, noImg,
                containerX + (containerWidth - tw) / 2, containerY + containerHeight / 2 - 4, UITheme.Colors.TEXT_SUBTITLE);
        }

        currentY += containerHeight + UITheme.Dimensions.PADDING;
        contentHeight += containerHeight + UITheme.Dimensions.PADDING;

        if (imageUrls != null && imageUrls.length > 1 && activeGenPopup == null && confirmPopup == null) {
            String indicator = String.format("%d / %d", currentImageIndex + 1, imageUrls.length);
            int indicatorWidth = client.textRenderer.getWidth(indicator);
            int indicatorX = x + (width - indicatorWidth) / 2;
            int btnY = currentY;

            updateCarouselButtons(btnY);

            if (prevImageButton != null) {
                prevImageButton.render(context, renderMouseX, renderMouseY, delta);
            }

            context.drawTextWithShadow(client.textRenderer, indicator, indicatorX, btnY + 4, UITheme.Colors.TEXT_SUBTITLE);

            if (nextImageButton != null) {
                nextImageButton.render(context, renderMouseX, renderMouseY, delta);
            }

            currentY += 16 + UITheme.Dimensions.PADDING;
            contentHeight += 16 + UITheme.Dimensions.PADDING;
        }

        String title = postInfo.title() != null ? postInfo.title() : "Untitled";

        int titleMaxWidth = width - UITheme.Dimensions.PADDING * 2;
        drawWrappedText(context, title, x + UITheme.Dimensions.PADDING, currentY, titleMaxWidth, UITheme.Colors.TEXT_PRIMARY);
        int titleHeight = getWrappedTextHeight(title, titleMaxWidth);


        currentY += titleHeight + 8;
        contentHeight += titleHeight + 8;

        String author = "By " + (postInfo.author() != null ? postInfo.author() : "Unknown");
        context.drawTextWithShadow(client.textRenderer, author, x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
        currentY += 12;
        contentHeight += 12;

        String downloads = "Downloads: " + postInfo.downloads();
        context.drawTextWithShadow(client.textRenderer, downloads, x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
        currentY += 16;
        contentHeight += 16;

        String[] tags = postInfo.tags();
        if (tags != null && tags.length > 0) {
            context.drawTextWithShadow(client.textRenderer, "Tags:", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
            currentY += 12;
            contentHeight += 12;

            int tagX = x + UITheme.Dimensions.PADDING;
            for (String tag : tags) {
                int tagWidth = client.textRenderer.getWidth(tag) + 8;
                if (tagX + tagWidth > x + width - UITheme.Dimensions.PADDING) {
                    tagX = x + UITheme.Dimensions.PADDING;
                    currentY += 14;
                    contentHeight += 14;
                }
                context.fill(tagX, currentY, tagX + tagWidth, currentY + 12, TAG_BG_COLOR);
                context.drawTextWithShadow(client.textRenderer, tag, tagX + 4, currentY + 2, UITheme.Colors.TEXT_TAG);
                tagX += tagWidth + 4;
            }
            currentY += 16;
            contentHeight += 16;
        }

        String[] versions = postInfo.versions();
        if (versions != null && versions.length > 0) {
            context.drawTextWithShadow(client.textRenderer, "Versions:", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
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
                x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_TAG);
            currentY += 16;
            contentHeight += 16;
        }

        if (postDetail != null && postDetail.getDescription() != null && !postDetail.getDescription().isEmpty()) {
            currentY += 8;
            contentHeight += 8;
            context.drawTextWithShadow(client.textRenderer, "Description:", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
            currentY += 12;
            contentHeight += 12;

            String desc = postDetail.getDescription();
            drawWrappedText(context, desc, x + UITheme.Dimensions.PADDING, currentY, width - UITheme.Dimensions.PADDING * 2, UITheme.Colors.TEXT_TAG);
            int descHeight = getWrappedTextHeight(desc, width - UITheme.Dimensions.PADDING * 2);
            contentHeight += descHeight;
        } else if (isLoadingDetails) {
            currentY += 8;
            context.drawTextWithShadow(client.textRenderer, "Loading details...", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
            contentHeight += 20;
        }

        contentHeight += UITheme.Dimensions.PADDING * 2;

        context.disableScissor();

        if (schematicDropdown != null && schematicDropdown.isOpen()) {
            schematicDropdown.render(context, renderMouseX, renderMouseY, delta);
        }

        if (contentHeight > height) {
            scrollBar.setScrollData(contentHeight, height);
            scrollBar.setScrollPercentage(scrollOffset / Math.max(1, contentHeight - height));

            if (client != null && client.getWindow() != null) {
                long windowHandle = client.getWindow().getHandle();
                if (scrollBar.updateAndRender(context, renderMouseX, renderMouseY, delta, windowHandle)) {
                    double maxScroll = Math.max(0, contentHeight - height);
                    scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
                }
            } else {
                scrollBar.render(context, renderMouseX, renderMouseY, delta);
            }
        }

        if (activeGenPopup != null) {
            activeGenPopup.render(context, mouseX, mouseY, delta);
        }

        if (confirmPopup != null) {
            confirmPopup.render(context, mouseX, mouseY, delta);
        }

    }

    public boolean hasImageViewerOpen() {
        return imageViewer != null;
    }

    public boolean hasPopupOpen() {
        return activeGenPopup != null || confirmPopup != null;
    }

    public void renderImageViewer(DrawContext context, int mouseX, int mouseY, float delta) {
        if (imageViewer != null) {
            imageViewer.render(context, mouseX, mouseY, delta);
        }
        if (activeGenPopup != null) {
            activeGenPopup.render(context, mouseX, mouseY, delta);
        }
        if (confirmPopup != null) {
            confirmPopup.render(context, mouseX, mouseY, delta);
        }
    }

    private void drawWrappedText(DrawContext context, String text, int textX, int textY, int maxWidth, int color) {
        if (text == null || text.isEmpty()) return;

        int lineY = textY;
        String[] paragraphs = text.split("\\r?\\n");

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lineY += 10;
                continue;
            }

            List<TextSegment> segments = parseMarkdownLinks(paragraph);

            int currentX = textX;
            StringBuilder currentLine = new StringBuilder();
            List<TextSegment> currentLineSegments = new ArrayList<>();

            for (TextSegment segment : segments) {
                String[] words = segment.text.split(" ");

                for (int i = 0; i < words.length; i++) {
                    String word = words[i];
                    boolean isLastWord = i == words.length - 1;

                    int wordWidth = client.textRenderer.getWidth(word);
                    if (wordWidth > maxWidth) {
                        if (!currentLine.isEmpty()) {
                            renderLineWithLinks(context, currentLineSegments, textX, lineY, color);
                            currentLine = new StringBuilder();
                            currentLineSegments.clear();
                            lineY += 10;
                        }

                        StringBuilder partialWord = new StringBuilder();
                        for (char c : word.toCharArray()) {
                            String testPart = partialWord.toString() + c;
                            if (client.textRenderer.getWidth(testPart) > maxWidth && !partialWord.isEmpty()) {
                                currentLineSegments.clear();
                                currentLineSegments.add(new TextSegment(partialWord.toString(), segment.url));
                                renderLineWithLinks(context, currentLineSegments, textX, lineY, color);
                                lineY += 10;
                                partialWord = new StringBuilder();
                            }
                            partialWord.append(c);
                        }

                        if (!partialWord.isEmpty()) {
                            if (isLastWord) {
                                currentLineSegments.clear();
                                currentLineSegments.add(new TextSegment(partialWord.toString(), segment.url));
                                renderLineWithLinks(context, currentLineSegments, textX, lineY, color);
                                lineY += 10;
                                currentLine = new StringBuilder();
                                currentLineSegments.clear();
                            } else {
                                currentLine = new StringBuilder(partialWord.toString());
                                currentLineSegments.clear();
                                currentLineSegments.add(new TextSegment(partialWord + " ", segment.url));
                            }
                        }
                        continue;
                    }

                    String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
                    int testWidth = client.textRenderer.getWidth(testLine);

                    if (testWidth > maxWidth && !currentLine.isEmpty()) {
                        renderLineWithLinks(context, currentLineSegments, textX, lineY, color);
                        currentLine = new StringBuilder(word);
                        currentLineSegments.clear();
                        currentLineSegments.add(new TextSegment(word + (isLastWord ? "" : " "), segment.url));
                        lineY += 10;
                    } else {
                        if (!currentLine.isEmpty()) {
                            currentLine.append(" ");
                        }
                        currentLine.append(word);

                        TextSegment lastSegment = currentLineSegments.isEmpty() ? null : currentLineSegments.get(currentLineSegments.size() - 1);
                        boolean sameUrl = lastSegment != null &&
                                         ((lastSegment.url == null && segment.url == null) ||
                                          (lastSegment.url != null && lastSegment.url.equals(segment.url)));

                        if (!sameUrl) {
                            currentLineSegments.add(new TextSegment(word + (isLastWord ? "" : " "), segment.url));
                        } else {
                            currentLineSegments.set(currentLineSegments.size() - 1,
                                new TextSegment(lastSegment.text + " " + word + (isLastWord ? "" : " "), segment.url));
                        }
                    }
                }
            }

            if (!currentLine.isEmpty()) {
                renderLineWithLinks(context, currentLineSegments, textX, lineY, color);
                lineY += 10;
            }
        }
    }

    private static class TextSegment {
        final String text;
        final String url;

        TextSegment(String text, String url) {
            this.text = text;
            this.url = url;
        }
    }

    private List<TextSegment> parseMarkdownLinks(String text) {
        List<TextSegment> segments = new ArrayList<>();
        int lastIndex = 0;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            if (matcher.start() > lastIndex) {
                segments.add(new TextSegment(text.substring(lastIndex, matcher.start()), null));
            }
            segments.add(new TextSegment(matcher.group(1), matcher.group(2)));
            lastIndex = matcher.end();
        }

        if (lastIndex < text.length()) {
            segments.add(new TextSegment(text.substring(lastIndex), null));
        }

        if (segments.isEmpty()) {
            segments.add(new TextSegment(text, null));
        }

        return segments;
    }

    private void renderLineWithLinks(DrawContext context, List<TextSegment> segments, int startX, int y, int defaultColor) {
        int currentX = startX;

        for (TextSegment segment : segments) {
            String text = segment.text.trim();
            if (text.isEmpty()) continue;

            int segmentWidth = client.textRenderer.getWidth(text);
            int linkColor = segment.url != null ? 0xFF5599FF : defaultColor;

            context.drawTextWithShadow(client.textRenderer, text, currentX, y, linkColor);

            if (segment.url != null) {
                clickableLinks.add(new ClickableLink(currentX, y, segmentWidth, 9, segment.url));
                context.fill(currentX, y + 9, currentX + segmentWidth, y + 10, 0xFF5599FF);
            }

            currentX += client.textRenderer.getWidth(text + " ");
        }
    }

    private int getWrappedTextHeight(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return 10;

        int lines = 0;

        String[] paragraphs = text.split("\\r?\\n");

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines++;
                continue;
            }

            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();
            int paragraphLines = 1;

            for (String word : words) {
                if (client.textRenderer.getWidth(word) > maxWidth) {
                    if (!line.isEmpty()) {
                        paragraphLines++;
                        line = new StringBuilder();
                    }

                    StringBuilder longWordPart = new StringBuilder();
                    for (char c : word.toCharArray()) {
                        String testPart = longWordPart.toString() + c;
                        if (client.textRenderer.getWidth(testPart) > maxWidth && !longWordPart.isEmpty()) {
                            paragraphLines++;
                            longWordPart = new StringBuilder(String.valueOf(c));
                        } else {
                            longWordPart.append(c);
                        }
                    }
                    line = longWordPart;
                    continue;
                }

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
        if (activeGenPopup != null) {
            return activeGenPopup.mouseClicked(mouseX, mouseY, button);
        }

        if (confirmPopup != null) {
            confirmPopup.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        if (imageViewer != null) {
            return imageViewer.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0) {
            for (ClickableLink link : clickableLinks) {
                if (link.isMouseOver(mouseX, mouseY)) {
                    showLinkConfirmPopup(link.url);
                    return true;
                }
            }
        }

        if (schematicDropdown != null && schematicDropdown.isOpen()) {
            if (schematicDropdown.isMouseOver(mouseX, mouseY)) {
                boolean handled = schematicDropdown.mouseClicked(mouseX, mouseY, button);
                return handled;
            } else {
                schematicDropdown.close();
            }
        }

        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        System.out.println("[PostDetailPanel] mouseClicked - x:" + mouseX + " y:" + mouseY + " button:" + button);

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

        if (scrollBar != null && scrollBar.mouseClicked(mouseX, mouseY, button)) {
            System.out.println("[PostDetailPanel] Scrollbar handled the click");
            return true;
        }

        if (button == 0 && currentImageTexture != null && !isLoadingImage && postInfo != null) {
            int downloadBtnSize = 20;
            int contentStartY = y + downloadBtnSize;
            int currentY = contentStartY + UITheme.Dimensions.PADDING - (int) scrollOffset;

            int containerWidth = getDisplayImageWidth();
            int containerHeight = getDisplayImageHeight();
            int actualImageWidth = getActualImageWidth();
            int actualImageHeight = getActualImageHeight();

            int containerX = x + 1;
            int containerY = currentY;
            int imageX = containerX + (containerWidth - actualImageWidth) / 2;
            int imageY = containerY + (containerHeight - actualImageHeight) / 2;

            if (mouseX >= imageX && mouseX < imageX + actualImageWidth &&
                mouseY >= imageY && mouseY < imageY + actualImageHeight &&
                mouseY >= contentStartY && mouseY < y + height) {
                openImageViewer();
                return true;
            }
        }

        if (button == 0 && imageUrls != null && imageUrls.length > 1) {
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

    private void openImageViewer() {
        if (currentImageTexture != null && client != null && client.getWindow() != null) {
            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            int totalImages = (imageUrls != null) ? imageUrls.length : 1;

            imageViewer = new ImageViewerWidget(
                client,
                currentImageTexture,
                originalImageWidth,
                originalImageHeight,
                currentImageIndex,
                totalImages,
                this::previousImageInViewer,
                this::nextImageInViewer,
                this::closeImageViewer
            );
            imageViewer.updateLayout(screenWidth, screenHeight);
        }
    }

    private void previousImageInViewer() {
        if (imageUrls != null && imageUrls.length > 1) {
            currentImageIndex = (currentImageIndex - 1 + imageUrls.length) % imageUrls.length;
            loadImage(imageUrls[currentImageIndex]);
            closeImageViewer();
            openImageViewer();
        }
    }

    private void nextImageInViewer() {
        if (imageUrls != null && imageUrls.length > 1) {
            currentImageIndex = (currentImageIndex + 1) % imageUrls.length;
            loadImage(imageUrls[currentImageIndex]);
            closeImageViewer();
            openImageViewer();
        }
    }

    private void closeImageViewer() {
        imageViewer = null;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollBar != null && (scrollBar.isDragging() || scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY))) {
            double maxScroll = Math.max(0, contentHeight - height);
            scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (imageViewer != null) {
            return imageViewer.mouseReleased(mouseX, mouseY, button);
        }

        if (scrollBar != null && scrollBar.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (imageViewer != null) {
            return true;
        }

        if (schematicDropdown != null && schematicDropdown.isOpen()) {
            if (schematicDropdown.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }

        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            double maxScroll = Math.max(0, contentHeight - height + UITheme.Dimensions.PADDING);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 20));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeGenPopup != null) {
            return false;
        }

        if (confirmPopup != null) {
            return confirmPopup.keyPressed(keyCode, scanCode, modifiers);
        }

        if (imageViewer != null) {
            return imageViewer.keyPressed(keyCode, scanCode, modifiers);
        }

        if (imageUrls != null && imageUrls.length > 1) {
            if (keyCode == 263) {
                previousImage();
                return true;
            } else if (keyCode == 262) {
                nextImage();
                return true;
            }
        }
        return false;
    }

    private void showLinkConfirmPopup(String url) {
        String title = "Open External Link";
        String message = "You are about to leave the mod and open:\n\n" + url + "\n\nDo you want to continue?";

        confirmPopup = new ConfirmPopup(
            parentScreen,
            title,
            message,
            () -> {
                try {
                    Util.getOperatingSystem().open(new URI(url));
                } catch (Exception e) {
                    System.err.println("Failed to open link: " + url);
                    e.printStackTrace();
                }
                closePopup();
            },
            this::closePopup,
            "Continue"
        );
    }

    private void closePopup() {
        confirmPopup = null;
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return false;
    }
}

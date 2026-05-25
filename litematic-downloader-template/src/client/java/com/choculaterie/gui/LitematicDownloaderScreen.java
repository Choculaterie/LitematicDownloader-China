package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.gui.widget.CustomButton;
import com.choculaterie.gui.widget.CustomTextField;
import com.choculaterie.gui.widget.PostListWidget;
import com.choculaterie.gui.widget.PostDetailPanel;
import com.choculaterie.gui.widget.SortFilterPanel;
import com.choculaterie.gui.widget.LoadingSpinner;
import com.choculaterie.gui.widget.ToastManager;
import com.choculaterie.gui.widget.ModMessageBanner;
import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.models.MinemevSearchResponse;
import com.choculaterie.models.ModMessage;
import com.choculaterie.network.MinemevNetworkManager;
import com.choculaterie.network.ChoculaterieNetworkManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class LitematicDownloaderScreen extends Screen {
    private static final int SEARCH_BAR_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PADDING = 10;
    private static final int ITEMS_PER_PAGE = 20;

    private CustomTextField searchField;
    private PostListWidget postList;
    private PostDetailPanel detailPanel;
    private SortFilterPanel sortFilterPanel;
    private CustomButton searchButton;
    private CustomButton prevPageButton;
    private CustomButton nextPageButton;
    private CustomButton folderButton;
    private CustomButton filterButton;
    private CustomButton closeButton;
    private LoadingSpinner loadingSpinner;
    private ToastManager toastManager;
    private ModMessageBanner modMessageBanner;

    private int currentPage = 1;
    private int totalPages = 1;
    private int totalItems = 0;
    private boolean isLoading = false;
    private String currentSearchQuery = "";
    private boolean noResultsFound = false;
    private boolean showFilterPanel = false;
    private boolean initialized = false;

    public LitematicDownloaderScreen() {
        super(Text.of("Litematic Downloader"));
    }

    @Override
    protected void init() {
        super.init();

        String previousSearchText = (searchField != null) ? searchField.getText() : "";

        if (this.client != null) {
            toastManager = new ToastManager(this.client);
        }

        int leftPanelWidth = this.width / 2;
        int rightPanelWidth = this.width - leftPanelWidth;

        boolean isCompact = leftPanelWidth < 250;
        boolean isVeryCompact = leftPanelWidth < 180;

        int searchButtonWidth = isVeryCompact ? 25 : (isCompact ? 40 : 70);
        int paginationButtonWidth = isVeryCompact ? 25 : (isCompact ? 50 : 80);
        String searchLabel = isVeryCompact ? "🔍" : (isCompact ? "🔍" : "Search");
        String prevLabel = isVeryCompact ? "◀" : (isCompact ? "◀" : "< Previous");
        String nextLabel = isVeryCompact ? "▶" : (isCompact ? "▶" : "Next >");

        int searchBarWidth = Math.max(50, leftPanelWidth - (PADDING * 3) - searchButtonWidth);

        loadingSpinner = new LoadingSpinner(leftPanelWidth / 2 - 16, this.height / 2 - 16);

        if (this.client != null) {
            searchField = new CustomTextField(
                    this.client,
                    PADDING,
                    PADDING,
                    searchBarWidth,
                    SEARCH_BAR_HEIGHT,
                    Text.of("Search")
            );
            searchField.setPlaceholder(Text.of(isCompact ? "Search..." : "Search schematics..."));
            searchField.setOnEnterPressed(this::performSearch);
            searchField.setOnClearPressed(this::performSearch);
            if (!previousSearchText.isEmpty()) {
                searchField.setText(previousSearchText);
            }
            this.addDrawableChild(searchField);
        }

        searchButton = new CustomButton(
                PADDING + searchBarWidth + PADDING,
                PADDING,
                searchButtonWidth,
                SEARCH_BAR_HEIGHT,
                Text.of(searchLabel),
                button -> performSearch()
        );
        this.addDrawableChild(searchButton);

        int listY = PADDING + SEARCH_BAR_HEIGHT + PADDING;
        int listHeight = this.height - listY - BUTTON_HEIGHT - PADDING * 2;
        int listWidth = leftPanelWidth - PADDING * 2;

        if (postList == null) {
            postList = new PostListWidget(PADDING, listY, listWidth, listHeight, this::onPostClick);
        } else {
            postList.setDimensions(PADDING, listY, listWidth, listHeight);
        }
        this.addDrawableChild(postList);

        int rightPanelContentWidth = rightPanelWidth - PADDING;
        int rightPanelContentHeight = this.height - PADDING * 2;

        if (detailPanel == null) {
            detailPanel = new PostDetailPanel(leftPanelWidth, PADDING, rightPanelContentWidth, rightPanelContentHeight, this);
        } else {
            detailPanel.setDimensions(leftPanelWidth, PADDING, rightPanelContentWidth, rightPanelContentHeight);
        }

        sortFilterPanel = new SortFilterPanel(leftPanelWidth, PADDING, rightPanelContentWidth, rightPanelContentHeight);
        sortFilterPanel.setOnSettingsChanged(this::onFilterSettingsChanged);

        int closeButtonSize = 20;
        folderButton = new CustomButton(
                this.width - PADDING - closeButtonSize * 3,
                PADDING,
                closeButtonSize,
                closeButtonSize,
                Text.of("📁"),
                button -> openFolderPage()
        );

        filterButton = new CustomButton(
                this.width - PADDING - closeButtonSize * 2,
                PADDING,
                closeButtonSize,
                closeButtonSize,
                Text.of("⚙"),
                button -> toggleFilterPanel()
        );

        closeButton = new CustomButton(
                this.width - PADDING - closeButtonSize,
                PADDING,
                closeButtonSize,
                closeButtonSize,
                Text.of("X"),
                button -> this.close()
        );
        closeButton.setRenderAsXIcon(true);

        int bottomY = this.height - BUTTON_HEIGHT - PADDING;

        prevPageButton = new CustomButton(
                PADDING,
                bottomY,
                paginationButtonWidth,
                BUTTON_HEIGHT,
                Text.of(prevLabel),
                button -> previousPage()
        );
        prevPageButton.active = false;
        this.addDrawableChild(prevPageButton);

        nextPageButton = new CustomButton(
                leftPanelWidth - PADDING - paginationButtonWidth,
                bottomY,
                paginationButtonWidth,
                BUTTON_HEIGHT,
                Text.of(nextLabel),
                button -> nextPage()
        );
        nextPageButton.active = false;
        this.addDrawableChild(nextPageButton);

        modMessageBanner = new ModMessageBanner(0, 0, this.width);
        modMessageBanner.setOnDismiss(this::onModMessageDismissed);

        if (!initialized) {
            initialized = true;
            fetchModMessage();
            performSearch();
        } else {
            updatePaginationButtons();
        }
    }

    private void fetchModMessage() {
        ChoculaterieNetworkManager.getModMessage()
            .thenAccept(message -> {
                if (this.client != null) {
                    this.client.execute(() -> {
                        if (message != null && message.hasMessage() && message.id() != null) {
                            int dismissedId = DownloadSettings.getInstance().getDismissedModMessageId();
                            if (message.id() != dismissedId) {
                                modMessageBanner.setMessage(message);
                            }
                        }
                    });
                }
            })
            .exceptionally(throwable -> {
                System.err.println("[LitematicDownloaderScreen] Failed to fetch mod message: " + throwable.getMessage());
                return null;
            });
    }

    private void onModMessageDismissed(ModMessage message) {
        if (message != null && message.id() != null) {
            DownloadSettings.getInstance().setDismissedModMessageId(message.id());
        }
    }

    private boolean isMouseOverButton(CustomButton button, double mouseX, double mouseY) {
        return button != null &&
               mouseX >= button.getX() &&
               mouseX < button.getX() + button.getWidth() &&
               mouseY >= button.getY() &&
               mouseY < button.getY() + button.getHeight();
    }

    private Object getActivePanel() {
        return showFilterPanel ? sortFilterPanel : detailPanel;
    }

    private void performSearch() {
        if (isLoading) return;

        searchField.setFocused(false);

        currentSearchQuery = searchField.getText().trim();
        currentPage = 1;

        detailPanel.clear();

        loadPage();
    }

    private void loadPage() {
        if (isLoading) {
            return;
        }

        isLoading = true;
        searchButton.active = false;
        prevPageButton.active = false;
        nextPageButton.active = false;
        postList.clear();

        String sort = sortFilterPanel != null ? sortFilterPanel.getSelectedSort() : "downloads";

        MinemevNetworkManager.searchPostsAdvanced(currentSearchQuery, sort, 1, currentPage, null, null, null)
                .thenAccept(this::handleSearchResponse)
                .exceptionally(throwable -> {
                    if (this.client != null) {
                        this.client.execute(() -> {
                            isLoading = false;
                            searchButton.active = true;
                            updatePaginationButtons();

                            String errorMessage = throwable.getMessage();
                            String userMessage;

                            if (errorMessage != null) {
                                if (errorMessage.contains("UnknownHostException") ||
                                    errorMessage.contains("ConnectException") ||
                                    errorMessage.contains("SocketTimeoutException") ||
                                    errorMessage.contains("NoRouteToHostException")) {
                                    userMessage = "Network error: No internet connection";
                                } else if (errorMessage.contains("HTTP error code: 404")) {
                                    userMessage = "Server error: Resource not found";
                                } else if (errorMessage.contains("HTTP error code: 500")) {
                                    userMessage = "Server error: Internal server error";
                                } else if (errorMessage.contains("HTTP error code:")) {
                                    userMessage = "Server error: " + errorMessage.substring(errorMessage.indexOf("HTTP error code:"));
                                } else if (errorMessage.contains("Failed to search posts")) {
                                    Throwable cause = throwable.getCause();
                                    if (cause != null) {
                                        String causeMsg = cause.getMessage();
                                        if (causeMsg != null && causeMsg.contains("java.net.")) {
                                            userMessage = "Network error: Cannot reach server";
                                        } else {
                                            userMessage = "Search failed: " + (causeMsg != null ? causeMsg : "Unknown error");
                                        }
                                    } else {
                                        userMessage = "Search failed: Connection error";
                                    }
                                } else {
                                    userMessage = "Search failed: " + errorMessage;
                                }
                            } else {
                                userMessage = "Search failed: Unknown error";
                            }

                            String fullError = "Error: " + errorMessage;
                            if (throwable.getCause() != null) {
                                fullError += "\nCause: " + throwable.getCause().toString();
                            }

                            System.err.println("Error loading posts: " + errorMessage);
                            if (toastManager != null) {
                                toastManager.showError(userMessage, fullError);
                            }
                        });
                    }
                    return null;
                });
    }

    private void handleSearchResponse(MinemevSearchResponse response) {
        if (this.client != null) {
            this.client.execute(() -> {
                totalPages = response.totalPages();
                totalItems = response.totalItems();

                MinemevPostInfo[] posts = response.posts();
                postList.setPosts(posts);

                isLoading = false;
                searchButton.active = true;
                updatePaginationButtons();

                noResultsFound = (totalItems == 0);
            });
        }
    }

    private void updatePaginationButtons() {
        prevPageButton.active = currentPage > 1;
        nextPageButton.active = currentPage < totalPages;
    }

    private void previousPage() {
        if (currentPage > 1) {
            currentPage--;
            loadPage();
        }
    }

    private void nextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            loadPage();
        }
    }

    private void onPostClick(MinemevPostInfo post) {
        if (detailPanel != null && post != null) {
            detailPanel.setPost(post);
            if (showFilterPanel) {
                showFilterPanel = false;
            }
        }
    }

    private void openFolderPage() {
        if (detailPanel != null) {
            detailPanel.closeDropdown();
        }
        if (this.client != null) {
            this.client.setScreen(new LocalFolderPage(this));
        }
    }

    private void toggleFilterPanel() {
        showFilterPanel = !showFilterPanel;
    }

    private void onFilterSettingsChanged(SortFilterPanel panel) {
        currentPage = 1;
        loadPage();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int leftPanelWidth = this.width / 2;

        context.fill(0, 0, this.width, this.height, 0xFF202020);

        boolean imageViewerOpen = detailPanel != null && detailPanel.hasImageViewerOpen();
        boolean popupOpen = detailPanel != null && detailPanel.hasPopupOpen();

        boolean mouseOverBanner = modMessageBanner != null && modMessageBanner.isVisible()
                && modMessageBanner.isMouseOver(mouseX, mouseY);
        int effectiveMouseX = (mouseOverBanner || imageViewerOpen) ? -1 : mouseX;
        int effectiveMouseY = (mouseOverBanner || imageViewerOpen) ? -1 : mouseY;

        super.render(context, popupOpen ? -1 : effectiveMouseX, popupOpen ? -1 : effectiveMouseY, delta);

        if (showFilterPanel) {
            sortFilterPanel.render(context, effectiveMouseX, effectiveMouseY, delta);
        } else {
            detailPanel.render(context, mouseX, mouseY, delta);
        }

        if (totalPages > 1 || totalItems > ITEMS_PER_PAGE) {
            if (detailPanel == null || !detailPanel.hasPopupOpen()) {
                boolean isCompact = leftPanelWidth < 250;
                boolean isVeryCompact = leftPanelWidth < 180;
                int paginationButtonWidth = isVeryCompact ? 25 : (isCompact ? 50 : 80);

                int availableWidth = leftPanelWidth - PADDING - paginationButtonWidth - PADDING - paginationButtonWidth - PADDING;

                String pageText;
                String fullText = String.format("Page %d / %d (%d items)",
                        currentPage,
                        Math.max(totalPages, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE)),
                        totalItems);
                String mediumText = String.format("%d / %d", currentPage, Math.max(totalPages, 1));
                String shortText = String.format("%d/%d", currentPage, Math.max(totalPages, 1));

                if (this.textRenderer.getWidth(fullText) <= availableWidth) {
                    pageText = fullText;
                } else if (this.textRenderer.getWidth(mediumText) <= availableWidth) {
                    pageText = mediumText;
                } else if (this.textRenderer.getWidth(shortText) <= availableWidth) {
                    pageText = shortText;
                } else {
                    pageText = null;
                }

                if (pageText != null) {
                    int textWidth = this.textRenderer.getWidth(pageText);
                    context.drawTextWithShadow(
                            this.textRenderer,
                            pageText,
                            (leftPanelWidth - textWidth) / 2,
                            this.height - BUTTON_HEIGHT / 2 - 4 - PADDING,
                            0xFFFFFFFF
                    );
                }
            }
        }

        if (isLoading) {
            loadingSpinner.render(context, effectiveMouseX, effectiveMouseY, delta);
        }

        if (noResultsFound) {
            String noResultsText = "No results found :(";
            int textWidth = this.textRenderer.getWidth(noResultsText);
            context.drawTextWithShadow(
                    this.textRenderer,
                    noResultsText,
                    (leftPanelWidth - textWidth) / 2,
                    this.height / 2 + 10,
                    0xFFFFFFFF
            );
        }

        if (folderButton != null) {
            folderButton.render(context, effectiveMouseX, effectiveMouseY, delta);
        }

        if (filterButton != null) {
            filterButton.render(context, effectiveMouseX, effectiveMouseY, delta);
        }

        if (closeButton != null) {
            closeButton.render(context, effectiveMouseX, effectiveMouseY, delta);
        }

        if (modMessageBanner != null && modMessageBanner.isVisible()) {
            modMessageBanner.render(context, mouseX, mouseY, delta);
        }

        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            detailPanel.renderImageViewer(context, mouseX, mouseY, delta);
        }

        if (toastManager != null) {
            toastManager.render(context, delta, mouseX, mouseY);
        }
    }


    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            return detailPanel.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0 && toastManager != null) {
            if (toastManager.mouseClicked(mouseX, mouseY)) {
                return true;
            }
            if (toastManager.isMouseOverToast(mouseX, mouseY)) {
                return true;
            }
        }

        if (button == 0 && modMessageBanner != null && modMessageBanner.isVisible()) {
            if (modMessageBanner.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (modMessageBanner.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }

        if (button == 0 && isMouseOverButton(closeButton, mouseX, mouseY)) {
            this.close();
            return true;
        }

        if (button == 0 && isMouseOverButton(filterButton, mouseX, mouseY)) {
            toggleFilterPanel();
            return true;
        }

        if (button == 0 && isMouseOverButton(folderButton, mouseX, mouseY)) {
            openFolderPage();
            return true;
        }

        if (showFilterPanel) {
            if (sortFilterPanel != null && sortFilterPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        } else {
            if (detailPanel != null && detailPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (button == 0 && searchField != null) {
            if (searchField.isMouseOver(mouseX, mouseY)) {
                searchField.setFocused(true);
                return true;
            } else {
                searchField.setFocused(false);
            }
        }

        return super.mouseClicked(click, doubled);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        Object panel = getActivePanel();
        if (panel instanceof SortFilterPanel && ((SortFilterPanel) panel).mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (panel instanceof PostDetailPanel && ((PostDetailPanel) panel).mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Object panel = getActivePanel();
        if (panel instanceof SortFilterPanel && ((SortFilterPanel) panel).mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (panel instanceof PostDetailPanel && ((PostDetailPanel) panel).mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Object panel = getActivePanel();
        if (panel instanceof SortFilterPanel && ((SortFilterPanel) panel).mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (panel instanceof PostDetailPanel && ((PostDetailPanel) panel).mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (detailPanel != null && detailPanel.hasPopupOpen()) {
            detailPanel.keyPressed(256, 0, 0); // 256 = GLFW_KEY_ESCAPE
            return false;
        }
        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            detailPanel.keyPressed(256, 0, 0); // 256 = GLFW_KEY_ESCAPE
            return false;
        }
        return super.shouldCloseOnEsc();
    }

    @Override
    public void close() {
        super.close();
    }
}

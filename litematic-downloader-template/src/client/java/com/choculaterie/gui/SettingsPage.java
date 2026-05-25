package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.gui.widget.ConfirmPopup;
import com.choculaterie.gui.widget.CustomButton;
import com.choculaterie.gui.widget.CustomTextField;
import com.choculaterie.gui.widget.ToastManager;
import com.choculaterie.gui.widget.ToggleButton;
import com.choculaterie.network.MinemevNetworkManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.File;

public class SettingsPage extends Screen {
    private static final int PADDING = 10;
    private static final int BACK_BUTTON_SIZE = 20;
    private static final int LABEL_HEIGHT = 20;
    private static final int TEXT_FIELD_HEIGHT = 20;
    private static final int TOGGLE_SPACING = 25;
    private static final String TITLE = "Settings";

    private final Screen parentScreen;
    private CustomTextField downloadPathField;
    private CustomButton setPathButton;
    private CustomButton linkAccountButton;
    private CustomButton unlinkAccountButton;
    private ToastManager toastManager;
    private ConfirmPopup activePopup;

    private String pendingToastMessage;
    private boolean pendingToastSuccess;

    private boolean isAuthenticating = false;
    private String authQueryToken = null;

    public SettingsPage(Screen parentScreen) {
        super(Text.of("Settings"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        if (this.client != null) {
            toastManager = new ToastManager(this.client);
        }

        addBackButton();
        int contentY = initDownloadPathSection();
        contentY = initToastToggles(contentY);
        initAccountSection(contentY);
    }

    private void addBackButton() {
        CustomButton backButton = new CustomButton(
                PADDING, PADDING, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE,
                Text.of("←"), button -> goBack()
        );
        this.addDrawableChild(backButton);
    }

    private int initDownloadPathSection() {
        int contentY = PADDING * 4 + BACK_BUTTON_SIZE;
        boolean isVeryCompact = this.width < 300;
        int browseButtonWidth = isVeryCompact ? 50 : (this.width < 400 ? 70 : 90);
        String browseLabel = isVeryCompact ? "..." : "Browse...";

        int setButtonSize = TEXT_FIELD_HEIGHT;
        int setButtonX = this.width - PADDING * 2 - setButtonSize;
        int browseButtonX = setButtonX - PADDING - browseButtonWidth;
        int fieldWidth = Math.max(100, browseButtonX - PADDING * 3);

        if (this.client != null) {
            downloadPathField = new CustomTextField(
                    this.client, PADDING * 2, contentY + LABEL_HEIGHT,
                    fieldWidth, TEXT_FIELD_HEIGHT, Text.of("Download Path")
            );
            downloadPathField.setText(DownloadSettings.getInstance().getDownloadPath());
            downloadPathField.setPlaceholder(Text.of("Enter download path..."));
        }

        CustomButton browseButton = new CustomButton(
                browseButtonX, contentY + LABEL_HEIGHT, browseButtonWidth, TEXT_FIELD_HEIGHT,
                Text.of(browseLabel), button -> openFileDialog()
        );
        this.addDrawableChild(browseButton);

        setPathButton = new CustomButton(
                setButtonX, contentY + LABEL_HEIGHT, setButtonSize, setButtonSize,
                Text.of("✓"), button -> saveDownloadPath()
        );
        setPathButton.visible = false;
        this.addDrawableChild(setPathButton);

        return contentY;
    }

    private int initToastToggles(int contentY) {
        int toastsY = contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 30;
        DownloadSettings settings = DownloadSettings.getInstance();

        createToastToggle(0, toastsY, settings.isInfoToastsEnabled(),
                settings::setInfoToastsEnabled);
        createToastToggle(1, toastsY, settings.isSuccessToastsEnabled(),
                settings::setSuccessToastsEnabled);
        createToastToggle(2, toastsY, settings.isWarningToastsEnabled(),
                settings::setWarningToastsEnabled);
        createToastToggle(3, toastsY, settings.isErrorToastsEnabled(),
                settings::setErrorToastsEnabled);

        return toastsY + LABEL_HEIGHT + (TOGGLE_SPACING * 4);
    }

    private void createToastToggle(int index, int baseY, boolean enabled,
                                   java.util.function.Consumer<Boolean> callback) {
        ToggleButton toggle = new ToggleButton(
                PADDING * 2 + 120, baseY + LABEL_HEIGHT + (TOGGLE_SPACING * index),
                enabled, callback
        );
        this.addDrawableChild(toggle);
    }

    private void initAccountSection(int contentY) {
        int accountY = contentY + 30;

        boolean isAuthenticated = MinemevNetworkManager.isAuthenticated();

        linkAccountButton = new CustomButton(
                PADDING * 2, accountY + LABEL_HEIGHT, 120, TEXT_FIELD_HEIGHT,
                Text.of("Link Account"), button -> startDeviceFlow()
        );
        linkAccountButton.visible = !isAuthenticated && !isAuthenticating;
        this.addDrawableChild(linkAccountButton);

        unlinkAccountButton = new CustomButton(
                PADDING * 2, accountY + LABEL_HEIGHT, 120, TEXT_FIELD_HEIGHT,
                Text.of("Unlink Account"), button -> unlinkAccount()
        );
        unlinkAccountButton.visible = isAuthenticated;
        this.addDrawableChild(unlinkAccountButton);
    }

    private void startDeviceFlow() {
        System.out.println("[SettingsPage] User clicked Link Account button");

        if (isAuthenticating) {
            System.out.println("[SettingsPage] Already authenticating, ignoring click");
            if (toastManager != null) {
                toastManager.showWarning("Authentication already in progress...");
            }
            return;
        }

        if (MinemevNetworkManager.isAuthenticated()) {
            System.out.println("[SettingsPage] Already authenticated, ignoring click");
            if (toastManager != null) {
                toastManager.showInfo("Already logged in!");
            }
            return;
        }

        isAuthenticating = true;
        if (linkAccountButton != null) {
            linkAccountButton.visible = false;
        }

        System.out.println("[SettingsPage] Calling MinemevNetworkManager.startDeviceFlow()");
        MinemevNetworkManager.startDeviceFlow().thenAccept(response -> {
            System.out.println("[SettingsPage] startDeviceFlow completed successfully");
            if (this.client != null) {
                this.client.execute(() -> {
                    authQueryToken = response.queryToken;
                    System.out.println("[SettingsPage] Opening browser to: " + response.redirectUrl);
                    Util.getOperatingSystem().open(response.redirectUrl);

                    if (toastManager != null) {
                        toastManager.showInfo("Opening browser for authentication...");
                    }

                    System.out.println("[SettingsPage] Starting polling...");
                    startPolling(response.queryToken);
                });
            }
        }).exceptionally(error -> {
            System.err.println("[SettingsPage] startDeviceFlow failed with error: " + error.getMessage());
            error.printStackTrace();
            if (this.client != null) {
                this.client.execute(() -> {
                    isAuthenticating = false;
                    if (linkAccountButton != null) {
                        linkAccountButton.visible = true;
                    }
                    if (toastManager != null) {
                        toastManager.showError("Failed to start authentication: " + error.getMessage());
                    }
                });
            }
            return null;
        });
    }

    private void startPolling(String queryToken) {
        System.out.println("[SettingsPage] Starting polling with queryToken: " + queryToken);
        MinemevNetworkManager.pollForAuthorization(queryToken, 180, 5).thenAccept(response -> {
            System.out.println("[SettingsPage] Polling completed with status: " + response.status);
            if (this.client != null) {
                this.client.execute(() -> {
                    isAuthenticating = false;
                    authQueryToken = null;

                    if ("approved".equals(response.status)) {
                        System.out.println("[SettingsPage] Authorization approved for user: " + response.username);
                        if (toastManager != null) {
                            toastManager.showSuccess("Logged in as " + response.username + "!");
                        }
                        updateAccountButtons();
                    } else if ("denied".equals(response.status)) {
                        System.out.println("[SettingsPage] Authorization denied by user");
                        if (toastManager != null) {
                            toastManager.showError("Authentication denied");
                        }
                        if (linkAccountButton != null) {
                            linkAccountButton.visible = true;
                        }
                    } else if ("expired".equals(response.status) || "timeout".equals(response.status)) {
                        System.out.println("[SettingsPage] Authorization " + response.status);
                        if (toastManager != null) {
                            toastManager.showWarning("Authentication timeout");
                        }
                        if (linkAccountButton != null) {
                            linkAccountButton.visible = true;
                        }
                    }
                });
            }
        }).exceptionally(error -> {
            System.err.println("[SettingsPage] Polling failed with error: " + error.getMessage());
            error.printStackTrace();
            if (this.client != null) {
                this.client.execute(() -> {
                    isAuthenticating = false;
                    authQueryToken = null;
                    if (linkAccountButton != null) {
                        linkAccountButton.visible = true;
                    }
                    if (toastManager != null) {
                        toastManager.showError("Authentication error: " + error.getMessage());
                    }
                });
            }
            return null;
        });
    }

    private void unlinkAccount() {
        MinemevNetworkManager.clearAuthentication();
        if (toastManager != null) {
            toastManager.showSuccess("Account unlinked");
        }
        updateAccountButtons();
    }

    private void updateAccountButtons() {
        boolean isAuthenticated = MinemevNetworkManager.isAuthenticated();
        if (linkAccountButton != null) {
            linkAccountButton.visible = !isAuthenticated && !isAuthenticating;
        }
        if (unlinkAccountButton != null) {
            unlinkAccountButton.visible = isAuthenticated;
        }
    }

    private void goBack() {
        if (this.client != null) {
            this.client.setScreen(parentScreen);
        }
    }

    private void openFileDialog() {
        // Open custom directory picker screen
        if (this.client != null) {
            String startPath = DownloadSettings.getInstance().getAbsoluteDownloadPath();
            DirectoryPickerScreen picker = new DirectoryPickerScreen(
                    this,
                    startPath,
                    selectedPath -> {
                        // Update the text field with selected path
                        if (downloadPathField != null) {
                            downloadPathField.setText(selectedPath);
                        }
                        // Auto-save the selected path
                        if (selectedPath != null && !selectedPath.isEmpty()) {
                            DownloadSettings.getInstance().setDownloadPath(selectedPath);
                            System.out.println("Download path saved from directory picker: " + selectedPath);
                            pendingToastMessage = "Download path updated!";
                            pendingToastSuccess = true;
                        } else {
                            pendingToastMessage = "Failed to select folder";
                            pendingToastSuccess = false;
                        }
                    }
            );
            this.client.setScreen(picker);
        }
    }

    private void saveDownloadPath() {
        if (downloadPathField == null) return;

        String newPath = downloadPathField.getText();
        if (newPath == null || newPath.isEmpty()) return;

        String absolutePath = DownloadSettings.getInstance().getGameDirectory() + File.separator + newPath;
        File directory = new File(absolutePath);

        if (directory.exists()) {
            completeSave(newPath);
        } else {
            showCreateFolderConfirmation(newPath, directory);
        }
    }

    private void showCreateFolderConfirmation(String relativePath, File directory) {
        activePopup = new ConfirmPopup(
                this,
                "Create Folder?",
                "The folder '" + relativePath + "' does not exist.\n\nDo you want to create it?",
                () -> createFolderAndSave(directory, relativePath),
                () -> activePopup = null,
                "Create"
        );
    }

    private void createFolderAndSave(File directory, String relativePath) {
        if (directory.mkdirs()) {
            completeSave(relativePath);
        } else {
            if (toastManager != null) {
                toastManager.showError("Failed to create folder!");
            }
        }
        activePopup = null;
    }

    private void completeSave(String newPath) {
        DownloadSettings.getInstance().setDownloadPath(newPath);
        if (toastManager != null) {
            toastManager.showSuccess("Download path saved!");
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        showPendingToast();
        context.fill(0, 0, this.width, this.height, 0xFF202020);

        super.render(context, mouseX, mouseY, delta);

        int contentY = renderTitleAndDownloadSection(context, mouseX, mouseY, delta);
        contentY = renderToastTogglesSection(context, contentY);
        renderAccountSection(context, contentY);
        renderOverlays(context, mouseX, mouseY, delta);
    }

    private void showPendingToast() {
        if (pendingToastMessage == null || toastManager == null) return;

        if (pendingToastSuccess) {
            toastManager.showSuccess(pendingToastMessage);
        } else {
            toastManager.showError(pendingToastMessage);
        }
        pendingToastMessage = null;
    }

    private int renderTitleAndDownloadSection(DrawContext context, int mouseX, int mouseY, float delta) {
        int titleX = (this.width - this.textRenderer.getWidth(TITLE)) / 2;
        context.drawTextWithShadow(this.textRenderer, TITLE, titleX,
                PADDING * 2 + BACK_BUTTON_SIZE, 0xFFFFFFFF);

        int contentY = PADDING * 4 + BACK_BUTTON_SIZE;
        context.drawTextWithShadow(this.textRenderer,
                "Download Path (relative to game folder):",
                PADDING * 2, contentY, 0xFFFFFFFF);

        renderDownloadPathField(context, contentY, mouseX, mouseY, delta);
        return contentY;
    }

    private void renderDownloadPathField(DrawContext context, int contentY, int mouseX, int mouseY, float delta) {
        if (downloadPathField == null) return;

        downloadPathField.render(context, mouseX, mouseY, delta);

        String currentText = downloadPathField.getText();
        String savedPath = DownloadSettings.getInstance().getDownloadPath();
        if (setPathButton != null) {
            setPathButton.visible = !currentText.equals(savedPath);
        }

        String previewText = "Full path: " + DownloadSettings.getInstance().getAbsoluteDownloadPath();
        context.drawTextWithShadow(this.textRenderer, previewText, PADDING * 2,
                contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 5, 0xFFAAAAAA);
    }

    private int renderToastTogglesSection(DrawContext context, int contentY) {
        int toastsY = contentY + LABEL_HEIGHT + TEXT_FIELD_HEIGHT + 30;

        context.drawTextWithShadow(this.textRenderer, "Show Notifications:",
                PADDING * 2, toastsY, 0xFFFFFFFF);

        renderToastToggleLabel(context, "Info:", toastsY, 0, 0xFF2196F3);
        renderToastToggleLabel(context, "Success:", toastsY, 1, 0xFF4CAF50);
        renderToastToggleLabel(context, "Warning:", toastsY, 2, 0xFFFFC107);
        renderToastToggleLabel(context, "Error:", toastsY, 3, 0xFFFF5252);

        return toastsY + LABEL_HEIGHT + (TOGGLE_SPACING * 4);
    }

    private void renderAccountSection(DrawContext context, int contentY) {
        int accountY = contentY + 30;

        context.drawTextWithShadow(this.textRenderer, "Reden Account:",
                PADDING * 2, accountY, 0xFFFFFFFF);

        boolean isAuthenticated = MinemevNetworkManager.isAuthenticated();

        if (isAuthenticated) {
            String username = MinemevNetworkManager.getUsername();
            String statusText = "Logged in as: " + (username != null ? username : "Unknown");
            context.drawTextWithShadow(this.textRenderer, statusText,
                    PADDING * 2 + 130, accountY + LABEL_HEIGHT + 7, 0xFF4CAF50);
        } else if (isAuthenticating) {
            context.drawTextWithShadow(this.textRenderer, "Waiting for authorization...",
                    PADDING * 2 + 130, accountY + LABEL_HEIGHT + 7, 0xFFFFC107);
        } else {
            context.drawTextWithShadow(this.textRenderer, "Not logged in",
                    PADDING * 2 + 130, accountY + LABEL_HEIGHT + 7, 0xFFAAAAAA);
        }
    }

    private void renderToastToggleLabel(DrawContext context, String label, int baseY, int index, int color) {
        int y = baseY + LABEL_HEIGHT + TOGGLE_SPACING * index + 6;
        context.drawTextWithShadow(this.textRenderer, label, PADDING * 2, y, color);
    }

    private void renderOverlays(DrawContext context, int mouseX, int mouseY, float delta) {
        if (toastManager != null) {
            toastManager.render(context, delta, mouseX, mouseY);
        }
        if (activePopup != null) {
            activePopup.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (activePopup != null) {
            return activePopup.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0 && downloadPathField != null) {
            if (downloadPathField.isMouseOver(mouseX, mouseY)) {
                downloadPathField.setFocused(true);
                return true;
            } else {
                downloadPathField.setFocused(false);
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (activePopup != null) {
            activePopup.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
            return false;
        }
        return super.shouldCloseOnEsc();
    }

    @Override
    public void close() {
        if (isAuthenticating) {
            System.out.println("[SettingsPage] Screen closing while authentication in progress");
        }
        goBack();
    }

    @Override
    public void removed() {
        super.removed();
        if (isAuthenticating) {
            System.out.println("[SettingsPage] Screen removed while authentication in progress - keeping state");
        }
    }
}


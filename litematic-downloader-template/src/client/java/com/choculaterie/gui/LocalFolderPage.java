package com.choculaterie.gui;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.gui.localfolder.FileOperationsManager;
import com.choculaterie.gui.localfolder.LocalFolderSearchManager;
import com.choculaterie.gui.localfolder.LocalFolderSelectionManager;
import com.choculaterie.gui.widget.ConfirmPopup;
import com.choculaterie.gui.widget.CustomButton;
import com.choculaterie.gui.widget.CustomTextField;
import com.choculaterie.gui.widget.ScrollBar;
import com.choculaterie.gui.widget.TextInputPopup;
import com.choculaterie.gui.widget.ToastManager;
import com.choculaterie.network.ChoculaterieNetworkManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LocalFolderPage extends Screen {
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 25;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_PADDING = 2;
    private static final long COPIED_DISPLAY_DURATION = 2000;
    private static final int MAX_UNDO_HISTORY = 50;

    private final Screen parentScreen;
    private final LocalFolderSelectionManager selectionManager = new LocalFolderSelectionManager();
    private final LocalFolderSearchManager searchManager = new LocalFolderSearchManager();

    private FileOperationsManager fileOpsManager;
    private CustomButton renameButton;
    private CustomButton deleteButton;
    private ScrollBar scrollBar;
    private TextInputPopup activePopup;
    private ConfirmPopup confirmPopup;
    private ToastManager toastManager;

    private File currentDirectory;
    private File baseDirectory;
    private File trashFolder;
    private final List<FileEntry> entries = new ArrayList<>();
    private int scrollOffset = 0;

    private final List<FileAction> undoStack = new ArrayList<>();
    private final List<FileAction> redoStack = new ArrayList<>();

    private boolean isDragging = false;
    private int dragStartIndex = -1;
    private double dragStartX = 0;
    private double dragStartY = 0;
    private int dropTargetIndex = -1;
    private File dropTargetBreadcrumb = null;
    private final List<Integer> preClickSelection = new ArrayList<>();

    private final List<QuickShareButton> quickShareButtons = new ArrayList<>();
    private int uploadingIndex = -1;
    private int copiedIndex = -1;
    private long copiedTimestamp = 0;

    private final List<BreadcrumbSegment> breadcrumbSegments = new ArrayList<>();
    private CustomTextField searchField;

    private boolean wasDeleteKeyPressed = false;
    private boolean wasZKeyPressed = false;
    private boolean wasYKeyPressed = false;
    private boolean wasAKeyPressed = false;

    private boolean isSearchActive = false;

    public void setFileOpsManager(FileOperationsManager fileOpsManager) {
        this.fileOpsManager = fileOpsManager;
    }

    public FileOperationsManager getFileOpsManager() {
        return fileOpsManager;
    }

    private static class FileAction {
        enum Type { MOVE, DELETE, RENAME, CREATE_FOLDER }
        final Type type;
        final List<FileOperation> operations;

        FileAction(Type type, List<FileOperation> operations) {
            this.type = type;
            this.operations = operations;
        }

        FileAction(Type type, FileOperation operation) {
            this.type = type;
            this.operations = new ArrayList<>();
            this.operations.add(operation);
        }
    }

    private static class FileOperation {
        final File source;
        final File destination;
        final boolean wasDirectory;

        FileOperation(File source, File destination, boolean wasDirectory) {
            this.source = source;
            this.destination = destination;
            this.wasDirectory = wasDirectory;
        }
    }

    private static class BreadcrumbSegment {
        final int x;
        final int width;
        final File directory;

        BreadcrumbSegment(int x, int width, File directory) {
            this.x = x;
            this.width = width;
            this.directory = directory;
        }
    }

    private static class QuickShareButton {
        final int x;
        final int y;
        final int width;
        final int height;
        final int entryIndex;

        QuickShareButton(int x, int y, int width, int height, int entryIndex) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.entryIndex = entryIndex;
        }

        boolean isHovered(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private static class FileEntry {
        final File file;
        final boolean isDirectory;
        String relativePath;

        FileEntry(File file) {
            this.file = file;
            this.isDirectory = file.isDirectory();
            this.relativePath = null;
        }

        FileEntry(File file, String relativePath) {
            this.file = file;
            this.isDirectory = file.isDirectory();
            this.relativePath = relativePath;
        }
    }

    public LocalFolderPage(Screen parentScreen) {
        super(Text.of("Local Folder"));
        this.parentScreen = parentScreen;

        String downloadPath = DownloadSettings.getInstance().getAbsoluteDownloadPath();
        this.currentDirectory = new File(downloadPath);
        this.baseDirectory = this.currentDirectory;

        if (!this.currentDirectory.exists()) {
            this.currentDirectory.mkdirs();
        }

        this.trashFolder = new File(this.baseDirectory, ".trash");
        if (!this.trashFolder.exists()) {
            this.trashFolder.mkdirs();
        }

        this.fileOpsManager = new FileOperationsManager(this.baseDirectory, this.trashFolder);
        loadEntries();
    }

    @Override
    protected void init() {
        super.init();

        String downloadPath = DownloadSettings.getInstance().getAbsoluteDownloadPath();
        File newBaseDirectory = new File(downloadPath);

        if (!newBaseDirectory.equals(this.baseDirectory)) {
            this.baseDirectory = newBaseDirectory;
            this.currentDirectory = newBaseDirectory;
            this.trashFolder = new File(this.baseDirectory, ".trash");
            this.fileOpsManager = new FileOperationsManager(this.baseDirectory, this.trashFolder);

            if (!this.currentDirectory.exists()) {
                this.currentDirectory.mkdirs();
            }
            if (!this.trashFolder.exists()) {
                this.trashFolder.mkdirs();
            }

            selectionManager.clearSelection();
            scrollOffset = 0;
        }

        loadEntries();

        int savedScrollOffset = scrollOffset;
        String previousSearchText = (searchField != null) ? searchField.getText() : "";

        if (this.client != null) {
            toastManager = new ToastManager(this.client);
        }

        int availableWidth = this.width - PADDING * 2 - BUTTON_HEIGHT - PADDING;
        boolean isCompact = availableWidth < 550;
        boolean isVeryCompact = availableWidth < 450;

        int newFolderWidth = isVeryCompact ? 25 : (isCompact ? 70 : 100);
        int renameWidth = isVeryCompact ? 25 : (isCompact ? 50 : 70);
        int deleteWidth = isVeryCompact ? 25 : (isCompact ? 45 : 60);
        int openFolderWidth = isVeryCompact ? 25 : (isCompact ? 80 : 120);

        String newFolderLabel = isVeryCompact ? "+" : (isCompact ? "+ New" : "+ New Folder");
        String renameLabel = isVeryCompact ? "✏" : "Rename";
        String deleteLabel = isVeryCompact ? "🗑" : "Delete";
        String openFolderLabel = isVeryCompact ? "📁" : (isCompact ? "📁 Open" : "📁 Open Folder");

        int currentX = PADDING;

        this.addDrawableChild(new CustomButton(
                currentX, PADDING, BUTTON_HEIGHT, BUTTON_HEIGHT,
                Text.of("←"), button -> goBack()
        ));
        currentX += BUTTON_HEIGHT + PADDING;

        this.addDrawableChild(new CustomButton(
                currentX, PADDING, newFolderWidth, BUTTON_HEIGHT,
                Text.of(newFolderLabel), button -> openNewFolderPopup()
        ));
        currentX += newFolderWidth + PADDING;

        renameButton = new CustomButton(
                currentX, PADDING, renameWidth, BUTTON_HEIGHT,
                Text.of(renameLabel), button -> openRenamePopup()
        );
        renameButton.active = false;
        this.addDrawableChild(renameButton);
        currentX += renameWidth + PADDING;

        deleteButton = new CustomButton(
                currentX, PADDING, deleteWidth, BUTTON_HEIGHT,
                Text.of(deleteLabel), button -> handleDeleteClick()
        );
        deleteButton.active = false;
        this.addDrawableChild(deleteButton);
        currentX += deleteWidth + PADDING;

        this.addDrawableChild(new CustomButton(
                currentX, PADDING, openFolderWidth, BUTTON_HEIGHT,
                Text.of(openFolderLabel), button -> openInFileExplorer()
        ));
        currentX += openFolderWidth + PADDING;

        int settingsX = this.width - PADDING - BUTTON_HEIGHT;
        this.addDrawableChild(new CustomButton(
                settingsX, PADDING, BUTTON_HEIGHT, BUTTON_HEIGHT,
                Text.of("⚙"), button -> openSettings()
        ));

        int searchWidth = settingsX - currentX - PADDING;
        if (this.client != null && searchWidth > 30) {
            searchField = new CustomTextField(this.client, currentX, PADDING, searchWidth, BUTTON_HEIGHT, Text.of("Search"));
            searchField.setPlaceholder(Text.of("Search..."));
            searchField.setOnChanged(this::onSearchChanged);
            searchField.setOnClearPressed(this::onSearchCleared);
            if (!previousSearchText.isEmpty()) {
                searchField.setText(previousSearchText);
            }
            this.addDrawableChild(searchField);
        }

        int listY = PADDING * 3 + BUTTON_HEIGHT + 18;
        int listHeight = this.height - listY - PADDING * 2;
        scrollBar = new ScrollBar(this.width - PADDING - SCROLLBAR_WIDTH, listY, listHeight);

        int contentHeight = entries.size() * ITEM_HEIGHT;
        int maxScroll = contentHeight <= listHeight ? 0 : entries.size() - listHeight / ITEM_HEIGHT;
        scrollBar.setScrollData(contentHeight, listHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, savedScrollOffset));
        if (maxScroll > 0) {
            scrollBar.setScrollPercentage((double) scrollOffset / maxScroll);
        }
    }

    private void goBack() {
        if (this.client != null) {
            this.client.setScreen(parentScreen);
        }
    }

    private void openSettings() {
        if (this.client != null) {
            this.client.setScreen(new SettingsPage(this));
        }
    }

    private void openInFileExplorer() {
        Util.getOperatingSystem().open(currentDirectory);
    }

    private void handleQuickShare(int entryIndex) {
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return;
        }

        FileEntry entry = entries.get(entryIndex);

        if (entry.isDirectory || !entry.file.getName().toLowerCase().endsWith(".litematic")) {
            if (toastManager != null) {
                toastManager.showError("Only .litematic files can be shared");
            }
            return;
        }

        uploadingIndex = entryIndex;
        if (toastManager != null) {
            toastManager.showInfo("Uploading " + entry.file.getName() + "...");
        }

        ChoculaterieNetworkManager.uploadLitematic(entry.file)
            .thenAccept(response -> {
                String shortUrl = response.shortUrl();
                if (shortUrl == null || shortUrl.isEmpty()) {
                    if (client != null) {
                        client.execute(() -> {
                            if (toastManager != null) {
                                toastManager.showError("Upload failed: No URL returned");
                            }
                            uploadingIndex = -1;
                        });
                    }
                    System.err.println("Upload failed: No URL returned");
                    return;
                }

                if (client != null) {
                    client.execute(() -> {
                        try {
                            if (client.keyboard != null) {
                                client.keyboard.setClipboard(shortUrl);
                                if (toastManager != null) {
                                    toastManager.showSuccess("Link copied to clipboard!");
                                }
                                System.out.println("Quick share URL copied: " + shortUrl);
                                copiedIndex = entryIndex;
                                copiedTimestamp = System.currentTimeMillis();
                            } else {
                                StringSelection selection = new StringSelection(shortUrl);
                                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                                if (toastManager != null) {
                                    toastManager.showSuccess("Link copied to clipboard!");
                                }
                                System.out.println("Quick share URL copied (AWT): " + shortUrl);
                                copiedIndex = entryIndex;
                                copiedTimestamp = System.currentTimeMillis();
                            }
                        } catch (Exception e) {
                            if (toastManager != null) {
                                toastManager.showError("Failed to copy to clipboard");
                            }
                            System.err.println("Failed to copy to clipboard: " + e.getMessage());
                            e.printStackTrace();
                        }
                        uploadingIndex = -1;
                    });
                }
            })
            .exceptionally(error -> {
                String errorMessage = error.getMessage();
                String userMessage;

                if (errorMessage != null) {
                    if (errorMessage.contains("UnknownHostException") ||
                        errorMessage.contains("ConnectException") ||
                        errorMessage.contains("NoRouteToHostException")) {
                        userMessage = "Upload failed: No internet connection";
                    } else if (errorMessage.contains("SocketTimeoutException")) {
                        userMessage = "Upload failed: Connection timeout";
                    } else if (errorMessage.contains("HTTP error code: 413")) {
                        userMessage = "Upload failed: File too large";
                    } else if (errorMessage.contains("HTTP error code:")) {
                        userMessage = "Upload failed: Server error";
                    } else {
                        userMessage = "Upload failed: " + errorMessage;
                    }
                } else {
                    userMessage = "Upload failed: Unknown error";
                }

                String fullError = "Quick Share Error: " + errorMessage;
                if (error.getCause() != null) {
                    fullError += "\nCause: " + error.getCause().toString();
                }

                if (toastManager != null) {
                    toastManager.showError(userMessage, fullError);
                }
                System.err.println("Quick share failed: " + errorMessage);
                error.printStackTrace();
                uploadingIndex = -1;
                return null;
            });
    }

    private void openNewFolderPopup() {
        if (activePopup != null) {
            return;
        }

        if (searchField != null) {
            searchField.setFocused(false);
        }
        activePopup = new TextInputPopup(
                this,
                "Create New Folder",
                this::createNewFolder,
                this::closePopup
        );
    }

    private void createNewFolder(String folderName) {
        if (currentDirectory == null) {
            closePopup();
            return;
        }

        File newFolder = new File(currentDirectory, folderName);
        if (newFolder.exists()) {
            if (activePopup != null) {
                activePopup.setErrorMessage("\"" + folderName + "\" already exists in this folder");
            }
            return;
        }

        boolean success = newFolder.mkdir();
        if (success) {
            addUndoAction(new FileAction(FileAction.Type.CREATE_FOLDER, new FileOperation(null, newFolder, true)));

            System.out.println("Created folder: " + newFolder.getAbsolutePath());
            if (toastManager != null) {
                toastManager.showSuccess("Created folder \"" + folderName + "\"");
            }
            closePopup();
            loadEntries(); // Refresh the list
        } else {
            if (activePopup != null) {
                activePopup.setErrorMessage("Failed to create folder - check folder permissions");
            }
        }
    }

    private void closePopup() {
        activePopup = null;
        confirmPopup = null;
    }

    private void openRenamePopup() {
        if (activePopup != null) {
            return;
        }

        if (selectionManager.getSelectionCount() != 1) {
            return;
        }

        List<Integer> selected = selectionManager.getSelectedIndices();
        int index = selected.getFirst();
        if (index < 0 || index >= entries.size()) {
            return;
        }

        FileEntry entry = entries.get(index);
        String currentName = entry.file.getName();

        if (searchField != null) {
            searchField.setFocused(false);
        }
        activePopup = new TextInputPopup(
                this,
                "Rename",
                "Rename",
                newName -> renameFile(entry.file, newName),
                this::closePopup
        );
        activePopup.setText(currentName);
    }

    private void renameFile(File file, String newName) {
        if (file == null || newName == null || newName.trim().isEmpty()) {
            closePopup();
            return;
        }

        newName = newName.trim();

        if (newName.equals(file.getName())) {
            closePopup();
            return;
        }

        File newFile = new File(file.getParentFile(), newName);
        if (newFile.exists()) {
            if (activePopup != null) {
                activePopup.setErrorMessage("\"" + newName + "\" already exists in this folder");
            }
            return;
        }

        boolean isDirectory = file.isDirectory();
        boolean success = file.renameTo(newFile);
        if (success) {
            addUndoAction(new FileAction(FileAction.Type.RENAME, new FileOperation(file, newFile, isDirectory)));

            System.out.println("Renamed: " + file.getName() + " -> " + newName);
            if (toastManager != null) {
                toastManager.showSuccess("Renamed to \"" + newName + "\"");
            }
            closePopup();
            loadEntries();
        } else {
            if (activePopup != null) {
                activePopup.setErrorMessage("Failed to rename - file may be in use or protected");
            }
        }
    }

    private void handleDeleteClick() {
        if (activePopup != null || confirmPopup != null) {
            return;
        }

        if (!selectionManager.hasSelection()) {
            return;
        }

        long windowHandle = this.client != null ? this.client.getWindow().getHandle() : 0;
        boolean shiftHeld = false;
        if (windowHandle != 0) {
            shiftHeld = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                       org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }

        if (shiftHeld) {
            deleteSelectedFiles();
        } else {
            String message;
            String title;
            List<Integer> selected = selectionManager.getSelectedIndices();

            if (selectionManager.getSelectionCount() == 1) {
                FileEntry entry = entries.get(selected.getFirst());
                String itemType = entry.isDirectory ? "folder" : "file";
                title = "Delete " + itemType + "?";

                if (entry.isDirectory) {
                    StringBuilder messageBuilder = new StringBuilder();
                    messageBuilder.append("Are you sure you want to delete \"").append(entry.file.getName()).append("\"?\n\n");
                    messageBuilder.append("Contents:\n");
                    buildDeleteTree(entry.file, messageBuilder, "", true);
                    message = messageBuilder.toString();
                } else {
                    message = "Are you sure you want to delete \"" + entry.file.getName() + "\"?";
                }
            } else {
                title = "Delete " + selectionManager.getSelectionCount() + " items?";


                boolean hasFolders = false;
                for (int idx : selected) {
                    if (idx >= 0 && idx < entries.size() && entries.get(idx).isDirectory) {
                        hasFolders = true;
                        break;
                    }
                }

                if (hasFolders) {
                    StringBuilder messageBuilder = new StringBuilder();
                    messageBuilder.append("Are you sure you want to delete ").append(selectionManager.getSelectionCount()).append(" selected items?\n\n");
                    messageBuilder.append("Items to delete:\n");

                    List<FileEntry> selectedEntries = new ArrayList<>();
                    for (int idx : selected) {
                        if (idx >= 0 && idx < entries.size()) {
                            selectedEntries.add(entries.get(idx));
                        }
                    }
                    selectedEntries.sort((a, b) -> {
                        if (a.isDirectory && !b.isDirectory) return -1;
                        if (!a.isDirectory && b.isDirectory) return 1;
                        return a.file.getName().compareToIgnoreCase(b.file.getName());
                    });

                    for (int i = 0; i < selectedEntries.size(); i++) {
                        FileEntry entry = selectedEntries.get(i);
                        boolean isLast = (i == selectedEntries.size() - 1);
                        String connector = isLast ? "└── " : "├── ";

                        if (entry.isDirectory) {
                            File[] subFiles = entry.file.listFiles();
                            boolean isEmpty = subFiles == null || subFiles.length == 0;
                            messageBuilder.append(connector).append("📁 ").append(entry.file.getName()).append("/\n");
                            if (!isEmpty) {
                                String newIndent = isLast ? "    " : "│   ";
                                buildDeleteTree(entry.file, messageBuilder, newIndent, false);
                            }
                        } else {
                            messageBuilder.append(connector).append("📄 ").append(entry.file.getName()).append("\n");
                        }
                    }
                    message = messageBuilder.toString();
                } else {
                    message = "Are you sure you want to delete " + selectionManager.getSelectionCount() + " selected items?";
                }
            }

            if (searchField != null) {
                searchField.setFocused(false);
            }
            confirmPopup = new ConfirmPopup(
                    this,
                    title,
                    message,
                    () -> {
                        deleteSelectedFiles();
                        closePopup();
                    },
                    this::closePopup
            );
        }
    }

    private void deleteSelectedFiles() {
        if (!selectionManager.hasSelection()) {
            return;
        }

        if (!trashFolder.exists()) {
            trashFolder.mkdirs();
        }

        List<Integer> sortedIndices = new ArrayList<>(selectionManager.getSelectedIndices());
        sortedIndices.sort((a, b) -> b - a);

        int successCount = 0;
        int failCount = 0;
        List<String> failedNames = new ArrayList<>();
        List<FileOperation> successfulOperations = new ArrayList<>();

        for (int index : sortedIndices) {
            if (index >= 0 && index < entries.size()) {
                FileEntry entry = entries.get(index);
                File sourceFile = entry.file;

                String trashName = System.currentTimeMillis() + "_" + sourceFile.getName();
                File trashFile = new File(trashFolder, trashName);

                boolean success = sourceFile.renameTo(trashFile);

                if (success) {
                    successCount++;
                    successfulOperations.add(new FileOperation(sourceFile, trashFile, entry.isDirectory));
                    System.out.println("Moved to trash: " + sourceFile.getName());
                } else {
                    failCount++;
                    failedNames.add(sourceFile.getName());
                    System.err.println("Failed to delete: " + sourceFile.getAbsolutePath());
                }
            }
        }

        if (!successfulOperations.isEmpty()) {
            addUndoAction(new FileAction(FileAction.Type.DELETE, successfulOperations));
        }

        if (toastManager != null) {
            if (failCount == 0) {
                if (successCount == 1) {
                    toastManager.showSuccess("Deleted 1 item (Ctrl+Z to undo)");
                } else {
                    toastManager.showSuccess("Deleted " + successCount + " items (Ctrl+Z to undo)");
                }
            } else if (successCount == 0) {
                if (failCount == 1) {
                    toastManager.showError("Failed to delete \"" + failedNames.getFirst() + "\" - file may be in use or protected");
                } else if (failCount <= 3) {
                    toastManager.showError("Failed to delete: " + String.join(", ", failedNames) + " - files may be in use or protected");
                } else {
                    toastManager.showError("Failed to delete " + failCount + " items - files may be in use or protected");
                }
            } else {
                if (failCount == 1) {
                    toastManager.showError("Deleted " + successCount + " items, failed to delete \"" + failedNames.getFirst() + "\"");
                } else {
                    toastManager.showError("Deleted " + successCount + " items, failed to delete " + failCount + " items");
                }
            }
        }

        loadEntries();
    }

    private void performMove(File targetFolder) {
        if (!selectionManager.hasSelection() || targetFolder == null || !targetFolder.isDirectory()) {
            return;
        }

        int successCount = 0;
        int conflictCount = 0;
        int otherFailCount = 0;
        List<String> conflictNames = new ArrayList<>();
        List<String> otherFailNames = new ArrayList<>();
        List<FileOperation> successfulOperations = new ArrayList<>();

        List<Integer> sortedIndices = new ArrayList<>(selectionManager.getSelectedIndices());
        sortedIndices.sort((a, b) -> b - a);

        for (int index : sortedIndices) {
            if (index >= 0 && index < entries.size()) {
                FileEntry entry = entries.get(index);
                File sourceFile = entry.file;
                File destFile = new File(targetFolder, sourceFile.getName());

                if (destFile.exists()) {
                    conflictCount++;
                    conflictNames.add(sourceFile.getName());
                    System.err.println("Cannot move: destination already exists: " + destFile.getAbsolutePath());
                    continue;
                }

                boolean success = sourceFile.renameTo(destFile);

                if (success) {
                    successCount++;
                    successfulOperations.add(new FileOperation(sourceFile, destFile, entry.isDirectory));
                    System.out.println("Moved: " + sourceFile.getName() + " -> " + targetFolder.getName());
                } else {
                    otherFailCount++;
                    otherFailNames.add(sourceFile.getName());
                    System.err.println("Failed to move: " + sourceFile.getAbsolutePath());
                }
            }
        }

        if (!successfulOperations.isEmpty()) {
            addUndoAction(new FileAction(FileAction.Type.MOVE, successfulOperations));
        }

        int totalFailCount = conflictCount + otherFailCount;

        if (toastManager != null) {
            if (totalFailCount == 0) {
                if (successCount == 1) {
                    toastManager.showSuccess("Moved 1 item to " + targetFolder.getName());
                } else {
                    toastManager.showSuccess("Moved " + successCount + " items to " + targetFolder.getName());
                }
            } else if (successCount == 0) {
                if (conflictCount > 0 && otherFailCount == 0) {
                    if (conflictCount == 1) {
                        toastManager.showError("\"" + conflictNames.getFirst() + "\" already exists in " + targetFolder.getName());
                    } else if (conflictCount <= 3) {
                        toastManager.showError("Items already exist in " + targetFolder.getName() + ": " + String.join(", ", conflictNames));
                    } else {
                        toastManager.showError(conflictCount + " items already exist in " + targetFolder.getName());
                    }
                } else if (otherFailCount > 0 && conflictCount == 0) {
                    if (otherFailCount == 1) {
                        toastManager.showError("Failed to move \"" + otherFailNames.getFirst() + "\" - check file permissions");
                    } else {
                        toastManager.showError("Failed to move " + otherFailCount + " items - check file permissions");
                    }
                } else {
                    toastManager.showError("Failed to move " + totalFailCount + " items (" + conflictCount + " already exist, " + otherFailCount + " other errors)");
                }
            } else {
                String errorDetail;
                if (conflictCount > 0 && otherFailCount == 0) {
                    errorDetail = conflictCount + " already exist";
                } else if (otherFailCount > 0 && conflictCount == 0) {
                    errorDetail = otherFailCount + " failed";
                } else {
                    errorDetail = conflictCount + " already exist, " + otherFailCount + " failed";
                }
                toastManager.showError("Moved " + successCount + " to " + targetFolder.getName() + ", " + errorDetail);
            }
        }

        loadEntries();
    }

    private int[] countFilesRecursively(File directory) {
        int[] counts = new int[2]; // [0] = folders, [1] = files

        if (!directory.isDirectory()) {
            return counts;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return counts;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                counts[0]++; // Count this folder
                int[] subCounts = countFilesRecursively(file);
                counts[0] += subCounts[0];
                counts[1] += subCounts[1];
            } else {
                counts[1]++; // Count this file
            }
        }

        return counts;
    }

    private void buildDeleteTree(File directory, StringBuilder builder, String indent, boolean showCount) {
        if (!directory.isDirectory()) return;

        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            builder.append(indent).append("(empty)\n");
            return;
        }

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        int maxItems = 20;

        int shown = 0;
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (shown >= maxItems) {
                int remaining = files.length - shown;
                builder.append(indent).append("└── ... and ").append(remaining).append(" more item(s)\n");
                break;
            }

            boolean isLast = (i == files.length - 1) || (shown == maxItems - 1 && files.length > maxItems);
            String connector = isLast ? "└── " : "├── ";

            if (file.isDirectory()) {
                File[] subFiles = file.listFiles();
                boolean isEmpty = subFiles == null || subFiles.length == 0;
                builder.append(indent).append(connector).append("📁 ").append(file.getName()).append("/\n");

                if (!isEmpty && indent.length() < 12) { // Max 3 levels deep
                    String newIndent = indent + (isLast ? "    " : "│   ");
                    buildDeleteTree(file, builder, newIndent, false);
                }
            } else {
                builder.append(indent).append(connector).append("📄 ").append(file.getName()).append("\n");
            }
            shown++;
        }

        if (showCount && files.length > 0) {
            int[] counts = countFilesRecursively(directory);
            int totalFolders = counts[0];
            int totalFiles = counts[1];

            builder.append("\n");
            if (totalFolders > 0) {
                builder.append("Total: ").append(totalFolders).append(" folder(s), ").append(totalFiles).append(" file(s)");
            } else {
                builder.append("Total: ").append(totalFiles).append(" file(s)");
            }
        }
    }

    private boolean deleteDirectoryRecursively(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectoryRecursively(file);
                } else {
                    file.delete();
                }
            }
        }
        return directory.delete();
    }

    private void updateSelectionButtons() {
        boolean hasSelection = selectionManager.hasSelection();
        if (renameButton != null) {
            renameButton.active = selectionManager.getSelectionCount() == 1;
        }
        if (deleteButton != null) {
            deleteButton.active = hasSelection;
        }
    }

    private void selectAll() {
        if (entries.isEmpty()) {
            return;
        }
        selectionManager.selectAll(entries.size());
        updateSelectionButtons();
    }

    private void onSearchChanged() {
        if (searchField != null) {
            String newQuery = searchField.getText().trim().toLowerCase();
            if (!newQuery.equals(searchManager.getSearchQuery())) {
                searchManager.updateSearch(newQuery);
                performSearch();
            }
        }
    }

    private void onSearchCleared() {
        searchManager.clearSearch();
        loadEntries();
    }

    private void performSearch() {
        if (!searchManager.isActive()) {
            loadEntries();
            return;
        }

        entries.clear();
        selectionManager.clearSelection();
        scrollOffset = 0;

        searchRecursively(baseDirectory, "");

        entries.sort((a, b) -> {
            if (a.isDirectory && !b.isDirectory) return -1;
            if (!a.isDirectory && b.isDirectory) return 1;
            return a.file.getName().compareToIgnoreCase(b.file.getName());
        });

        updateScrollBar();
        updateSelectionButtons();
    }

    private void searchRecursively(File directory, String pathPrefix) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.getName().equals(".trash")) {
                continue;
            }

            String fileName = file.getName().toLowerCase();
            String currentPath = pathPrefix.isEmpty() ? file.getName() : pathPrefix + "/" + file.getName();

            if (fileName.contains(searchManager.getSearchQuery())) {
                String relativePath = getRelativePath(file);
                entries.add(new FileEntry(file, relativePath));
            }

            if (file.isDirectory()) {
                searchRecursively(file, currentPath);
            }
        }
    }

    private String getRelativePath(File file) {
        if (baseDirectory == null || file == null) {
            return "";
        }
        try {
            String basePath = baseDirectory.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.startsWith(basePath)) {
                String relative = filePath.substring(basePath.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                int lastSep = relative.lastIndexOf(File.separator);
                if (lastSep > 0) {
                    return relative.substring(0, lastSep);
                }
                return "";
            }
        } catch (Exception e) {
        }
        return "";
    }

    private void loadEntries() {
        entries.clear();
        selectionManager.clearSelection();

        if (searchManager.isActive()) {
            performSearch();
            return;
        }

        if (currentDirectory != null && currentDirectory.exists()) {
            File[] files = currentDirectory.listFiles();
            if (files != null) {
                Arrays.sort(files, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });

                for (File file : files) {
                    if (file.getName().equals(".trash")) {
                        continue;
                    }
                    entries.add(new FileEntry(file));
                }
            }
        }

        updateScrollBar();
        updateSelectionButtons();
    }

    private void updateScrollBar() {
        if (scrollBar != null) {
            int listY = PADDING * 3 + BUTTON_HEIGHT + 18; // Account for search field
            int listHeight = this.height - listY - PADDING * 2;
            int contentHeight = entries.size() * ITEM_HEIGHT;

            scrollBar.setScrollData(contentHeight, listHeight);

            int maxScroll = contentHeight <= listHeight ? 0 : entries.size() - listHeight / ITEM_HEIGHT;
            scrollOffset = (int)(scrollBar.getScrollPercentage() * maxScroll);
        }
    }



    private void renderBreadcrumb(DrawContext context, int mouseX, int mouseY) {
        breadcrumbSegments.clear();

        int breadcrumbY = PADDING + BUTTON_HEIGHT + PADDING + 14;
        int currentX = PADDING;

        String label = "Current: ";
        context.drawTextWithShadow(this.textRenderer, label, currentX, breadcrumbY, 0xFFAAAAAA);
        currentX += this.textRenderer.getWidth(label);

        List<File> pathSegments = new ArrayList<>();
        File dir = currentDirectory;

        try {
            String basePath = baseDirectory.getCanonicalPath();

            while (dir != null) {
                pathSegments.addFirst(dir); // Add at beginning
                String dirPath = dir.getCanonicalPath();
                if (dirPath.equals(basePath)) {
                    break; // Stop at base directory
                }
                dir = dir.getParentFile();
            }
        } catch (Exception e) {
            pathSegments.clear();
            pathSegments.add(currentDirectory);
        }

        for (int i = 0; i < pathSegments.size(); i++) {
            File segment = pathSegments.get(i);
            String segmentName;

            if (i == 0) {
                segmentName = "/";
            } else {
                if (i > 1) {
                    String separator = " / ";
                    context.drawTextWithShadow(this.textRenderer, separator, currentX, breadcrumbY, 0xFF888888);
                    currentX += this.textRenderer.getWidth(separator);
                } else {
                    String space = " ";
                    context.drawTextWithShadow(this.textRenderer, space, currentX, breadcrumbY, 0xFF888888);
                    currentX += this.textRenderer.getWidth(space);
                }
                segmentName = segment.getName();
            }

            int segmentWidth = this.textRenderer.getWidth(segmentName);

            boolean isHovered = activePopup == null &&
                              mouseX >= currentX && mouseX < currentX + segmentWidth &&
                              mouseY >= breadcrumbY - 2 && mouseY < breadcrumbY + 12;

            boolean isCurrent = (i == pathSegments.size() - 1);

            boolean isDropTarget = isDragging && dropTargetBreadcrumb != null &&
                                  dropTargetBreadcrumb.equals(segment);

            int color;
            if (isDropTarget) {
                color = 0xFF55FF55; // Green for drop target
            } else if (isCurrent) {
                color = 0xFFFFFFFF; // White for current
            } else if (isHovered) {
                color = 0xFF55AAFF; // Light blue when hovered
            } else {
                color = 0xFF88CCFF; // Blue for clickable
            }

            if (isDropTarget) {
                context.fill(currentX - 2, breadcrumbY - 2, currentX + segmentWidth + 2, breadcrumbY + 12, 0x88336633);
            }

            if (isHovered && !isCurrent) {
                context.fill(currentX, breadcrumbY + 10, currentX + segmentWidth, breadcrumbY + 11, color);
            }

            context.drawTextWithShadow(this.textRenderer, segmentName, currentX, breadcrumbY, color);

            if (!isCurrent) {
                breadcrumbSegments.add(new BreadcrumbSegment(currentX, segmentWidth, segment));
            }

            currentX += segmentWidth;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF202020);

        if (this.client != null && this.client.getWindow() != null) {
            long windowHandle = this.client.getWindow().getHandle();
            boolean searchFocused = searchField != null && searchField.isFocused();
            boolean popupActive = activePopup != null || confirmPopup != null;

            if (!searchFocused && !popupActive) {
                boolean ctrlHeld = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                                   org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                boolean shiftHeld = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                                    org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

                boolean isDeleteDown = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (isDeleteDown && !wasDeleteKeyPressed && selectionManager.hasSelection()) {
                    if (shiftHeld) {
                        deleteSelectedFiles();
                    } else {
                        handleDeleteClick();
                    }
                }
                wasDeleteKeyPressed = isDeleteDown;

                boolean isZDown = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_Z) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (isZDown && !wasZKeyPressed && ctrlHeld) {
                    performUndo();
                }
                wasZKeyPressed = isZDown;

                boolean isYDown = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_Y) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (isYDown && !wasYKeyPressed && ctrlHeld) {
                    performRedo();
                }
                wasYKeyPressed = isYDown;

                boolean isADown = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_A) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (isADown && !wasAKeyPressed && ctrlHeld) {
                    selectAll();
                }
                wasAKeyPressed = isADown;
            }
        }

        if (dragStartIndex != -1 && this.client != null && this.client.getWindow() != null) {
            long windowHandle = this.client.getWindow().getHandle();
            boolean mouseButtonPressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (mouseButtonPressed) {
                if (!isDragging) {
                    double distX = mouseX - dragStartX;
                    double distY = mouseY - dragStartY;
                    if (Math.sqrt(distX * distX + distY * distY) > 3) {
                        isDragging = true;
                        preClickSelection.clear();
                    }
                }

                if (isDragging) {
                    int listY = PADDING * 3 + BUTTON_HEIGHT + 18;
                    int listHeight = this.height - listY - PADDING * 2;
                    int listRightEdge = this.width - PADDING - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;

                    dropTargetIndex = -1;
                    dropTargetBreadcrumb = null;

                    int breadcrumbY = PADDING * 3 + BUTTON_HEIGHT + 18 - 14;
                    if (mouseY >= breadcrumbY - 2 && mouseY < breadcrumbY + 12) {
                        for (BreadcrumbSegment segment : breadcrumbSegments) {
                            if (mouseX >= segment.x && mouseX < segment.x + segment.width) {
                                dropTargetBreadcrumb = segment.directory;
                                break;
                            }
                        }
                    }

                    if (dropTargetBreadcrumb == null &&
                        mouseX >= PADDING && mouseX < listRightEdge &&
                        mouseY >= listY && mouseY < listY + listHeight) {

                        int hoveredIndex = scrollOffset + (int)((mouseY - listY) / ITEM_HEIGHT);

                        if (hoveredIndex >= 0 && hoveredIndex < entries.size()) {
                            FileEntry hoveredEntry = entries.get(hoveredIndex);
                            if (hoveredEntry.isDirectory && !selectionManager.isSelected(hoveredIndex)) {
                                dropTargetIndex = hoveredIndex;
                            }
                        }
                    }
                }
            } else {
                if (isDragging) {
                    if (dropTargetBreadcrumb != null) {
                        performMove(dropTargetBreadcrumb);
                    } else if (dropTargetIndex != -1 && dropTargetIndex < entries.size()) {
                        FileEntry targetFolder = entries.get(dropTargetIndex);
                        performMove(targetFolder.file);
                    }
                } else {
                    if (!preClickSelection.isEmpty()) {
                        int singleItem = preClickSelection.getFirst();
                        selectionManager.selectSingle(singleItem);
                        updateSelectionButtons();
                    }
                }

                isDragging = false;
                dragStartIndex = -1;
                dropTargetIndex = -1;
                dropTargetBreadcrumb = null;
                preClickSelection.clear(); // Clear saved selection
            }
        }

        boolean popupActive = activePopup != null || confirmPopup != null;
        int renderMouseX = popupActive ? -1 : mouseX;
        int renderMouseY = popupActive ? -1 : mouseY;

        super.render(context, renderMouseX, renderMouseY, delta);

        if (searchField != null && !popupActive) {
            searchField.render(context, mouseX, mouseY, delta);
        }

        if (!searchManager.isActive()) {
            renderBreadcrumb(context, mouseX, mouseY);
        } else {
            int breadcrumbY = PADDING * 3 + BUTTON_HEIGHT + 18 - 14;
            String searchInfo = "Found " + entries.size() + " result" + (entries.size() != 1 ? "s" : "") + " for \"" + searchManager.getSearchQuery() + "\"";
            context.drawTextWithShadow(this.textRenderer, searchInfo, PADDING, breadcrumbY, 0xFFAAAAFF);
        }

        int listY = PADDING * 3 + BUTTON_HEIGHT + 18;
        int listHeight = this.height - listY - PADDING * 2;
        int maxVisibleItems = (listHeight / ITEM_HEIGHT) + 1;

        int listRightEdge = this.width - PADDING - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;

        context.fill(PADDING, listY, listRightEdge, listY + listHeight, 0xFF151515);

        context.enableScissor(PADDING, listY, listRightEdge, listY + listHeight);

        quickShareButtons.clear(); // Reset button positions each frame

        for (int i = scrollOffset; i < Math.min(entries.size(), scrollOffset + maxVisibleItems); i++) {
            FileEntry entry = entries.get(i);
            int itemY = listY + (i - scrollOffset) * ITEM_HEIGHT;

            boolean isHovered = !popupActive &&
                              mouseX >= PADDING && mouseX < listRightEdge &&
                              mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean isSelected = selectionManager.isSelected(i);
            boolean isDropTarget = isDragging && entry.isDirectory && i == dropTargetIndex && !selectionManager.isSelected(i);
            boolean isBeingDragged = isDragging && selectionManager.isSelected(i);

            int bgColor;
            if (isDropTarget) {
                bgColor = 0xFF336633; // Green tint for valid drop target
            } else if (isBeingDragged) {
                bgColor = 0xFF505050; // Lighter for items being dragged
            } else if (isSelected) {
                bgColor = 0xFF404040;
            } else if (isHovered) {
                bgColor = 0xFF2A2A2A;
            } else {
                bgColor = 0xFF1A1A1A;
            }
            context.fill(PADDING + 2, itemY + 2, listRightEdge - 2, itemY + ITEM_HEIGHT - 2, bgColor);

            String icon = entry.isDirectory ? "📁" : "📄";
            String fileName = entry.file.getName();
            int textX = PADDING + 5;
            int textY = itemY + (searchManager.isActive() && entry.relativePath != null && !entry.relativePath.isEmpty() ? 4 : 8);

            context.drawTextWithShadow(this.textRenderer, icon + " ", textX, textY, 0xFFFFFFFF);
            textX += this.textRenderer.getWidth(icon + " ");

            if (searchManager.isActive()) {
                String lowerName = fileName.toLowerCase();
                int searchIndex = lowerName.indexOf(searchManager.getSearchQuery());

                if (searchIndex >= 0) {
                    String beforeMatch = fileName.substring(0, searchIndex);
                    if (!beforeMatch.isEmpty()) {
                        context.drawTextWithShadow(this.textRenderer, beforeMatch, textX, textY, 0xFFFFFFFF);
                        textX += this.textRenderer.getWidth(beforeMatch);
                    }

                    String queryLen = searchManager.getSearchQuery();
                    String matchText = fileName.substring(searchIndex, searchIndex + queryLen.length());
                    int matchWidth = this.textRenderer.getWidth(matchText);
                    context.fill(textX - 1, textY - 1, textX + matchWidth + 1, textY + 9, 0xFF4488FF); // Blue highlight bg
                    context.drawTextWithShadow(this.textRenderer, matchText, textX, textY, 0xFFFFFFFF);
                    textX += matchWidth;

                    String afterMatch = fileName.substring(searchIndex + queryLen.length());
                    if (!afterMatch.isEmpty()) {
                        context.drawTextWithShadow(this.textRenderer, afterMatch, textX, textY, 0xFFFFFFFF);
                        textX += this.textRenderer.getWidth(afterMatch);
                    }
                } else {
                    context.drawTextWithShadow(this.textRenderer, fileName, textX, textY, 0xFFFFFFFF);
                    textX += this.textRenderer.getWidth(fileName);
                }
            } else {
                context.drawTextWithShadow(this.textRenderer, fileName, textX, textY, 0xFFFFFFFF);
                textX += this.textRenderer.getWidth(fileName);
            }

            if (!entry.isDirectory) {
                long sizeKB = entry.file.length() / 1024;
                String sizeText = " (" + sizeKB + " KB)";
                context.drawTextWithShadow(this.textRenderer, sizeText, textX, textY, 0xFFAAAAAA);
            }

            if (searchManager.isActive() && entry.relativePath != null && !entry.relativePath.isEmpty()) {
                String pathDisplay = "📍 " + entry.relativePath;
                context.drawTextWithShadow(this.textRenderer, pathDisplay, PADDING + 5 + this.textRenderer.getWidth(icon + " "), itemY + 15, 0xFF888888);
            }

            if (!entry.isDirectory && entry.file.getName().toLowerCase().endsWith(".litematic")) {
                int buttonWidth = 80;
                int buttonHeight = 16;
                int buttonX = listRightEdge - buttonWidth - 5;
                int buttonY = itemY + (ITEM_HEIGHT - buttonHeight) / 2;

                quickShareButtons.add(new QuickShareButton(buttonX, buttonY, buttonWidth, buttonHeight, i));

                boolean buttonHovered = !popupActive &&
                                       mouseX >= buttonX && mouseX < buttonX + buttonWidth &&
                                       mouseY >= buttonY && mouseY < buttonY + buttonHeight;

                boolean isCopied = i == copiedIndex &&
                                  (System.currentTimeMillis() - copiedTimestamp) < COPIED_DISPLAY_DURATION;

                if (i == copiedIndex && !isCopied) {
                    copiedIndex = -1;
                }

                String buttonText;
                int buttonBgColor;
                int buttonTextColor = 0xFFFFFFFF;

                if (i == uploadingIndex) {
                    buttonText = "Uploading...";
                    buttonBgColor = 0xFF555555;
                } else if (isCopied) {
                    buttonText = "✓ Copied";
                    buttonBgColor = 0xFF44AA44; // Green to indicate success
                } else if (buttonHovered) {
                    buttonText = "📤 Share";
                    buttonBgColor = 0xFF4488FF;
                } else {
                    buttonText = "📤 Share";
                    buttonBgColor = 0xFF3366CC;
                }

                context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonBgColor);

                int borderColor = buttonHovered ? 0xFF66AAFF : 0xFF4477DD;
                context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + 1, borderColor);
                context.fill(buttonX, buttonY + buttonHeight - 1, buttonX + buttonWidth, buttonY + buttonHeight, borderColor);
                context.fill(buttonX, buttonY, buttonX + 1, buttonY + buttonHeight, borderColor);
                context.fill(buttonX + buttonWidth - 1, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, borderColor);

                int btnTextWidth = this.textRenderer.getWidth(buttonText);
                int btnTextX = buttonX + (buttonWidth - btnTextWidth) / 2;
                int btnTextY = buttonY + (buttonHeight - 8) / 2;
                context.drawTextWithShadow(this.textRenderer, buttonText, btnTextX, btnTextY, buttonTextColor);
            }
        }

        context.disableScissor();

        if (scrollBar != null && scrollBar.isVisible() && this.client != null) {
            boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta, this.client.getWindow().getHandle());

            if (scrollChanged) {
                int maxScroll = Math.max(0, entries.size() - maxVisibleItems);
                scrollOffset = (int)(scrollBar.getScrollPercentage() * maxScroll);
            }
        }

        if (activePopup != null) {
            activePopup.render(context, mouseX, mouseY, delta);
        }

        if (confirmPopup != null) {
            confirmPopup.render(context, mouseX, mouseY, delta);
        }

        if (isDragging && selectionManager.hasSelection()) {
            String dragText = selectionManager.getSelectionCount() + " item" + (selectionManager.getSelectionCount() > 1 ? "s" : "");
            int textWidth = this.textRenderer.getWidth(dragText);
            int cursorX = mouseX + 10;
            int cursorY = mouseY + 10;

            context.fill(cursorX - 2, cursorY - 2, cursorX + textWidth + 2, cursorY + this.textRenderer.fontHeight + 2, 0xCC000000);

            context.fill(cursorX - 2, cursorY - 2, cursorX + textWidth + 2, cursorY - 1, 0xFF888888);
            context.fill(cursorX - 2, cursorY + this.textRenderer.fontHeight + 1, cursorX + textWidth + 2, cursorY + this.textRenderer.fontHeight + 2, 0xFF888888);
            context.fill(cursorX - 2, cursorY - 2, cursorX - 1, cursorY + this.textRenderer.fontHeight + 2, 0xFF888888);
            context.fill(cursorX + textWidth + 1, cursorY - 2, cursorX + textWidth + 2, cursorY + this.textRenderer.fontHeight + 2, 0xFF888888);

            context.drawText(this.textRenderer, dragText, cursorX, cursorY, 0xFFFFFFFF, false);
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

        if (confirmPopup != null) {
            confirmPopup.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        if (activePopup != null) {
            activePopup.mouseClicked(mouseX, mouseY, button);
            return true; // Always consume click when popup is active
        }

        if (button == 0 && toastManager != null) {
            if (toastManager.mouseClicked(mouseX, mouseY)) {
                return true;
            }
            if (toastManager.isMouseOverToast(mouseX, mouseY)) {
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

        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        int breadcrumbY = PADDING * 3 + BUTTON_HEIGHT + 18 - 14;
        if (mouseY >= breadcrumbY - 2 && mouseY < breadcrumbY + 12) {
            for (BreadcrumbSegment segment : breadcrumbSegments) {
                if (mouseX >= segment.x && mouseX < segment.x + segment.width) {
                    if (isSearchActive) {
                        String searchQuery = "";
                        isSearchActive = false;
                        if (searchField != null) {
                            searchField.setText("");
                        }
                    }
                    currentDirectory = segment.directory;
                    loadEntries();
                    scrollOffset = 0;
                    return true;
                }
            }
        }

        int listY = PADDING * 3 + BUTTON_HEIGHT + 18;
        int listHeight = this.height - listY - PADDING * 2;
        int listRightEdge = this.width - PADDING - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;

        if (mouseX >= PADDING && mouseX < listRightEdge &&
            mouseY >= listY && mouseY < listY + listHeight) {

            for (QuickShareButton qsButton : quickShareButtons) {
                if (qsButton.isHovered(mouseX, mouseY)) {
                    if (button == 0 && uploadingIndex == -1) { // Left click and not currently uploading
                        handleQuickShare(qsButton.entryIndex);
                        return true;
                    }
                }
            }

            int clickedIndex = scrollOffset + (int)((mouseY - listY) / ITEM_HEIGHT);

            if (clickedIndex >= 0 && clickedIndex < entries.size()) {
                if (button == 0) { // Left click
                    FileEntry entry = entries.get(clickedIndex);

                    long windowHandle = this.client != null ? this.client.getWindow().getHandle() : 0;
                    boolean shiftHeld = false;
                    boolean ctrlHeld = false;
                    if (windowHandle != 0) {
                        shiftHeld = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                                   org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                        ctrlHeld = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                                  org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                    }

                    if (entry.isDirectory && selectionManager.isSelected(clickedIndex) && doubled && !shiftHeld && !ctrlHeld) {
                        if (searchManager.isActive()) {
                            searchManager.clearSearch();
                            if (searchField != null) {
                                searchField.setText("");
                            }
                        }
                        currentDirectory = entry.file;
                        loadEntries();
                        scrollOffset = 0;
                    } else if (shiftHeld) {
                        selectionManager.selectRange(clickedIndex);
                    } else if (ctrlHeld) {
                        selectionManager.toggleSelection(clickedIndex);
                    } else {
                        if (selectionManager.isSelected(clickedIndex) && selectionManager.getSelectionCount() > 1) {
                            preClickSelection.clear();
                            preClickSelection.add(clickedIndex);

                            dragStartIndex = clickedIndex;
                            dragStartX = mouseX;
                            dragStartY = mouseY;
                        } else {
                            preClickSelection.clear();
                            selectionManager.selectSingle(clickedIndex);

                            dragStartIndex = clickedIndex;
                            dragStartX = mouseX;
                            dragStartY = mouseY;
                        }
                    }
                    updateSelectionButtons();
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (confirmPopup != null) {
            if (confirmPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }

        if (activePopup != null) {
            return true;
        }

        int listY = PADDING * 3 + BUTTON_HEIGHT + 18;
        int listHeight = this.height - listY - PADDING * 2;
        int contentHeight = entries.size() * ITEM_HEIGHT;
        int maxScroll = contentHeight <= listHeight ? 0 : entries.size() - listHeight / ITEM_HEIGHT;

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)verticalAmount));

        if (scrollBar != null && maxScroll > 0) {
            scrollBar.setScrollPercentage((double)scrollOffset / maxScroll);
        }

        return true;
    }

    private void performUndo() {
        if (undoStack.isEmpty()) {
            if (toastManager != null) {
                toastManager.showInfo("Nothing to undo");
            }
            return;
        }

        FileAction action = undoStack.removeLast();
        boolean success = false;
        String actionName = "";

        switch (action.type) {
            case MOVE -> {
                actionName = "move";
                success = undoMove(action);
            }
            case DELETE -> {
                actionName = "delete";
                success = undoDelete(action);
            }
            case RENAME -> {
                actionName = "rename";
                success = undoRename(action);
            }
            case CREATE_FOLDER -> {
                actionName = "folder creation";
                success = undoCreateFolder(action);
            }
        }

        if (success) {
            redoStack.add(action);
            if (redoStack.size() > MAX_UNDO_HISTORY) {
                redoStack.removeFirst();
            }
            if (toastManager != null) {
                toastManager.showSuccess("Undid " + actionName);
            }
            loadEntries();
        } else {
            if (toastManager != null) {
                toastManager.showError("Failed to undo " + actionName);
            }
        }
    }

    private void performRedo() {
        if (redoStack.isEmpty()) {
            if (toastManager != null) {
                toastManager.showInfo("Nothing to redo");
            }
            return;
        }

        FileAction action = redoStack.removeLast();
        boolean success = false;
        String actionName = "";

        switch (action.type) {
            case MOVE -> {
                actionName = "move";
                success = redoMove(action);
            }
            case DELETE -> {
                actionName = "delete";
                success = redoDelete(action);
            }
            case RENAME -> {
                actionName = "rename";
                success = redoRename(action);
            }
            case CREATE_FOLDER -> {
                actionName = "folder creation";
                success = redoCreateFolder(action);
            }
        }

        if (success) {
            undoStack.add(action);
            if (undoStack.size() > MAX_UNDO_HISTORY) {
                undoStack.removeFirst();
            }
            if (toastManager != null) {
                toastManager.showSuccess("Redid " + actionName);
            }
            loadEntries();
        } else {
            if (toastManager != null) {
                toastManager.showError("Failed to redo " + actionName);
            }
        }
    }

    private boolean undoMove(FileAction action) {
        boolean allSuccess = true;
        for (FileOperation op : action.operations) {
            if (op.destination.exists()) {
                boolean success = op.destination.renameTo(op.source);
                if (!success) allSuccess = false;
            } else {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private boolean redoMove(FileAction action) {
        boolean allSuccess = true;
        for (FileOperation op : action.operations) {
            if (op.source.exists()) {
                boolean success = op.source.renameTo(op.destination);
                if (!success) allSuccess = false;
            } else {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private boolean undoDelete(FileAction action) {
        boolean allSuccess = true;
        for (FileOperation op : action.operations) {
            if (op.destination.exists()) {
                if (op.source.exists()) {
                    System.err.println("Cannot restore - file already exists at: " + op.source.getAbsolutePath());
                    allSuccess = false;
                    continue;
                }
                boolean success = op.destination.renameTo(op.source);
                if (!success) {
                    System.err.println("Failed to restore from trash: " + op.destination.getAbsolutePath());
                    allSuccess = false;
                } else {
                    System.out.println("Restored from trash: " + op.source.getName());
                }
            } else {
                System.err.println("Trash file not found: " + op.destination.getAbsolutePath());
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private boolean redoDelete(FileAction action) {
        boolean allSuccess = true;
        for (FileOperation op : action.operations) {
            if (op.source.exists()) {
                boolean success = op.source.renameTo(op.destination);
                if (!success) {
                    System.err.println("Failed to re-delete: " + op.source.getAbsolutePath());
                    allSuccess = false;
                } else {
                    System.out.println("Re-deleted: " + op.source.getName());
                }
            } else {
                System.err.println("File not found for re-delete: " + op.source.getAbsolutePath());
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private boolean undoRename(FileAction action) {
        if (action.operations.isEmpty()) return false;
        FileOperation op = action.operations.getFirst();
        if (op.destination.exists()) {
            return op.destination.renameTo(op.source);
        }
        return false;
    }

    private boolean redoRename(FileAction action) {
        if (action.operations.isEmpty()) return false;
        FileOperation op = action.operations.getFirst();
        if (op.source.exists()) {
            return op.source.renameTo(op.destination);
        }
        return false;
    }

    private boolean undoCreateFolder(FileAction action) {
        if (action.operations.isEmpty()) return false;
        FileOperation op = action.operations.getFirst();
        if (op.destination.exists() && op.destination.isDirectory()) {
            File[] contents = op.destination.listFiles();
            if (contents == null || contents.length == 0) {
                return op.destination.delete();
            } else {
                if (toastManager != null) {
                    toastManager.showWarning("Cannot undo - folder is not empty");
                }
                return false;
            }
        }
        return false;
    }

    private boolean redoCreateFolder(FileAction action) {
        if (action.operations.isEmpty()) return false;
        FileOperation op = action.operations.getFirst();
        if (!op.destination.exists()) {
            return op.destination.mkdir();
        }
        return false;
    }

    private void addUndoAction(FileAction action) {
        undoStack.add(action);
        if (undoStack.size() > MAX_UNDO_HISTORY) {
            FileAction removed = undoStack.removeFirst();
            if (removed.type == FileAction.Type.DELETE) {
                cleanupTrashFiles(removed);
            }
        }
        for (FileAction redoAction : redoStack) {
            if (redoAction.type == FileAction.Type.DELETE) {
                cleanupTrashFiles(redoAction);
            }
        }
        redoStack.clear();
    }

    private void cleanupTrashFiles(FileAction action) {
        if (action.type != FileAction.Type.DELETE) return;

        for (FileOperation op : action.operations) {
            if (op.destination != null && op.destination.exists()) {
                if (op.wasDirectory) {
                    deleteDirectoryRecursively(op.destination);
                } else {
                    op.destination.delete();
                }
                System.out.println("Permanently deleted from trash: " + op.destination.getName());
            }
        }
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
        if (confirmPopup != null) {
            confirmPopup.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
            return false;
        }
        return super.shouldCloseOnEsc();
    }

    @Override
    public void close() {
        goBack();
    }
}


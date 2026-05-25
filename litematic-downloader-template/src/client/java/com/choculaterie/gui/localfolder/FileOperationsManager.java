package com.choculaterie.gui.localfolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileOperationsManager {
    private final File baseDirectory;
    private final File trashFolder;
    private final List<FileAction> undoStack = new ArrayList<>();
    private final List<FileAction> redoStack = new ArrayList<>();
    private static final int MAX_UNDO_HISTORY = 50;

    public FileOperationsManager(File baseDirectory, File trashFolder) {
        this.baseDirectory = baseDirectory;
        this.trashFolder = trashFolder;
    }

    public void moveFiles(List<File> files, File targetFolder) {
        List<FileOperation> operations = new ArrayList<>();
        for (File file : files) {
            File destination = new File(targetFolder, file.getName());
            operations.add(new FileOperation(file, destination, file.isDirectory()));
            file.renameTo(destination);
        }
        recordAction(new FileAction(FileAction.Type.MOVE, operations));
    }

    public void deleteFiles(List<File> files) {
        List<FileOperation> operations = new ArrayList<>();
        for (File file : files) {
            operations.add(new FileOperation(file, new File(trashFolder, file.getName()), file.isDirectory()));
            moveToTrash(file);
        }
        recordAction(new FileAction(FileAction.Type.DELETE, operations));
    }

    public void renameFile(File file, String newName) {
        File newFile = new File(file.getParent(), newName);
        file.renameTo(newFile);
        recordAction(new FileAction(FileAction.Type.RENAME, new FileOperation(file, newFile, file.isDirectory())));
    }

    public void createFolder(String folderName, File parentDir) {
        File newFolder = new File(parentDir, folderName);
        newFolder.mkdirs();
        recordAction(new FileAction(FileAction.Type.CREATE_FOLDER,
                new FileOperation(newFolder, newFolder, true)));
    }

    public void performUndo() {
        if (undoStack.isEmpty()) return;
        FileAction action = undoStack.remove(undoStack.size() - 1);
        reverseAction(action);
        redoStack.add(action);
    }

    public void performRedo() {
        if (redoStack.isEmpty()) return;
        FileAction action = redoStack.remove(redoStack.size() - 1);
        executeAction(action);
        undoStack.add(action);
    }

    private void reverseAction(FileAction action) {
        for (FileOperation op : action.operations) {
            if (action.type == FileAction.Type.DELETE) {
                op.source.renameTo(op.destination);
            } else if (action.type == FileAction.Type.MOVE) {
                op.destination.renameTo(op.source);
            }
        }
    }

    private void executeAction(FileAction action) {
        for (FileOperation op : action.operations) {
            op.source.renameTo(op.destination);
        }
    }

    private void recordAction(FileAction action) {
        undoStack.add(action);
        redoStack.clear();
        if (undoStack.size() > MAX_UNDO_HISTORY) {
            undoStack.remove(0);
        }
    }

    private void moveToTrash(File file) {
        if (file.isDirectory()) {
            moveDirectoryToTrash(file);
        } else {
            file.renameTo(new File(trashFolder, file.getName()));
        }
    }

    private void moveDirectoryToTrash(File directory) {
        File trashDest = new File(trashFolder, directory.getName());
        directory.renameTo(trashDest);
    }

    public boolean deleteDirectoryRecursively(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectoryRecursively(file);
                }
            }
        }
        return directory.delete();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public static class FileAction {
        public enum Type { MOVE, DELETE, RENAME, CREATE_FOLDER }

        public final Type type;
        public final List<FileOperation> operations;

        public FileAction(Type type, List<FileOperation> operations) {
            this.type = type;
            this.operations = operations;
        }

        public FileAction(Type type, FileOperation operation) {
            this.type = type;
            this.operations = new ArrayList<>();
            this.operations.add(operation);
        }
    }

    public static class FileOperation {
        public final File source;
        public final File destination;
        public final boolean wasDirectory;

        public FileOperation(File source, File destination, boolean wasDirectory) {
            this.source = source;
            this.destination = destination;
            this.wasDirectory = wasDirectory;
        }
    }
}


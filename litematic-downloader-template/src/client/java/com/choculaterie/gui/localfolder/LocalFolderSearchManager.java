package com.choculaterie.gui.localfolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LocalFolderSearchManager {
    private String searchQuery = "";
    private boolean isSearchActive = false;
    private final List<FileEntry> searchResults = new ArrayList<>();

    public void updateSearch(String query) {
        this.searchQuery = query.toLowerCase();
        this.isSearchActive = !query.isEmpty();
        searchResults.clear();
    }

    public void clearSearch() {
        this.searchQuery = "";
        this.isSearchActive = false;
        searchResults.clear();
    }

    public void performSearch(File baseDirectory, File currentDirectory) {
        searchResults.clear();
        if (searchQuery.isEmpty()) {
            return;
        }
        searchRecursively(currentDirectory, "", baseDirectory);
    }

    private void searchRecursively(File directory, String pathPrefix, File baseDirectory) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            String displayPath = pathPrefix.isEmpty() ? file.getName() : pathPrefix + "/" + file.getName();

            if (file.getName().toLowerCase().contains(searchQuery)) {
                searchResults.add(new FileEntry(file, displayPath));
            }

            if (file.isDirectory()) {
                searchRecursively(file, displayPath, baseDirectory);
            }
        }
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public boolean isActive() {
        return isSearchActive;
    }

    public List<FileEntry> getResults() {
        return new ArrayList<>(searchResults);
    }

    public static class FileEntry {
        public final File file;
        public final boolean isDirectory;
        public final String relativePath;

        public FileEntry(File file, String relativePath) {
            this.file = file;
            this.isDirectory = file.isDirectory();
            this.relativePath = relativePath;
        }
    }
}


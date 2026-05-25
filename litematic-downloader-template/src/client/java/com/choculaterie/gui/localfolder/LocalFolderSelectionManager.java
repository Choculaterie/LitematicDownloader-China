package com.choculaterie.gui.localfolder;

import java.util.ArrayList;
import java.util.List;

public class LocalFolderSelectionManager {
    private final List<Integer> selectedIndices = new ArrayList<>();
    private int lastClickedIndex = -1;

    public void selectSingle(int index) {
        selectedIndices.clear();
        selectedIndices.add(index);
        lastClickedIndex = index;
    }

    public void toggleSelection(int index) {
        if (selectedIndices.contains(index)) {
            selectedIndices.remove(Integer.valueOf(index));
        } else {
            selectedIndices.add(index);
        }
        lastClickedIndex = index;
    }

    public void selectRange(int index) {
        if (lastClickedIndex == -1) {
            selectSingle(index);
            return;
        }

        selectedIndices.clear();
        int start = Math.min(lastClickedIndex, index);
        int end = Math.max(lastClickedIndex, index);
        for (int i = start; i <= end; i++) {
            selectedIndices.add(i);
        }
    }

    public void selectAll(int totalItems) {
        selectedIndices.clear();
        for (int i = 0; i < totalItems; i++) {
            selectedIndices.add(i);
        }
    }

    public void clearSelection() {
        selectedIndices.clear();
        lastClickedIndex = -1;
    }

    public boolean isSelected(int index) {
        return selectedIndices.contains(index);
    }

    public List<Integer> getSelectedIndices() {
        return new ArrayList<>(selectedIndices);
    }

    public int getSelectionCount() {
        return selectedIndices.size();
    }

    public boolean hasSelection() {
        return !selectedIndices.isEmpty();
    }

    public void saveSelection() {
        // Used for drag-drop restoration
    }

    public void restoreSelection() {
        // Used for drag-drop restoration
    }
}


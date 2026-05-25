package com.choculaterie.gui.widget;

import com.choculaterie.gui.theme.UITheme;
import com.choculaterie.config.DownloadSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class SortFilterPanel implements Drawable, Element {

    private int x;
    private int y;
    private int width;
    private int height;

    private final MinecraftClient client;
    private double scrollOffset = 0;
    private int contentHeight = 0;
    private ScrollBar scrollBar;
    private String selectedSort = "downloads";
    private final String[] sortOptions = {"downloads", "newest"};
    private final String[] sortLabels = {"Downloads", "Latest"};
    private String versionFilter = "all";
    private Consumer<SortFilterPanel> onSettingsChanged;
    private CustomButton applyButton;
    private CustomButton resetButton;

    public SortFilterPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = MinecraftClient.getInstance();
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.PADDING, y + 30, height - 60);
        loadSettings();
        initButtons();
    }

    private void loadSettings() {
        DownloadSettings settings = DownloadSettings.getInstance();
        selectedSort = settings.getSortOption();
    }

    private void saveSettings() {
        DownloadSettings settings = DownloadSettings.getInstance();
        settings.setSortOption(selectedSort);
    }

    private void initButtons() {
        int buttonWidth = (width - UITheme.Dimensions.PADDING * 3) / 2;

        applyButton = new CustomButton(
                x + UITheme.Dimensions.PADDING,
                y + height - UITheme.Dimensions.BUTTON_HEIGHT - UITheme.Dimensions.PADDING,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of(width < 150 ? "✓" : "Apply"),
                button -> applySettings()
        );

        resetButton = new CustomButton(
                x + UITheme.Dimensions.PADDING + buttonWidth + UITheme.Dimensions.PADDING,
                y + height - UITheme.Dimensions.BUTTON_HEIGHT - UITheme.Dimensions.PADDING,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of(width < 150 ? "↺" : "Reset"),
                button -> resetSettings()
        );
    }

    private void applySettings() {
        saveSettings();
        notifySettingsChanged();
    }

    public void setOnSettingsChanged(Consumer<SortFilterPanel> callback) {
        this.onSettingsChanged = callback;
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH - UITheme.Dimensions.PADDING, y + 30, height - 60);
        initButtons();
    }

    public String getSelectedSort() {
        return selectedSort;
    }


    public String getVersionFilter() {
        return versionFilter.equals("all") ? null : versionFilter;
    }

    private void notifySettingsChanged() {
        if (onSettingsChanged != null) {
            onSettingsChanged.accept(this);
        }
    }

    private void drawButtonBorder(DrawContext context, int x, int y, int width, int height) {
        int borderWidth = UITheme.Dimensions.BORDER_WIDTH;
        int borderColor = UITheme.Colors.BUTTON_BORDER;
        context.fill(x, y, x + width, y + borderWidth, borderColor);
        context.fill(x, y + height - borderWidth, x + width, y + height, borderColor);
        context.fill(x, y, x + borderWidth, y + height, borderColor);
        context.fill(x + width - borderWidth, y, x + width, y + height, borderColor);
    }

    private void drawCenteredButtonText(DrawContext context, String text, int x, int y, int width, int height) {
        int textWidth = client.textRenderer.getWidth(text);
        context.drawTextWithShadow(client.textRenderer, text, x + (width - textWidth) / 2, y + 5, UITheme.Colors.TEXT_PRIMARY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(x, y, x + width, y + height, UITheme.Colors.PANEL_BG_SECONDARY);
        context.fill(x, y, x + 1, y + height, UITheme.Colors.BUTTON_BORDER);
        boolean isCompact = width < 180;
        String title = isCompact ? "Sort" : "Sort By";
        context.drawTextWithShadow(client.textRenderer, title, x + UITheme.Dimensions.PADDING, y + UITheme.Dimensions.PADDING, UITheme.Colors.TEXT_PRIMARY);
        int contentStartY = y + 30;
        context.enableScissor(x + 1, contentStartY, x + width - UITheme.Dimensions.SCROLLBAR_WIDTH, y + height - 40);

        int currentY = contentStartY - (int) scrollOffset;
        contentHeight = 0;
        currentY = renderSortSection(context, mouseX, mouseY, currentY, isCompact);

        context.disableScissor();
        int visibleHeight = height - 70;
        scrollBar.setScrollData(contentHeight, visibleHeight);
        scrollBar.render(context, mouseX, mouseY, delta);
        renderBottomButtons(context, mouseX, mouseY, delta);
    }

    private int renderSortSection(DrawContext context, int mouseX, int mouseY, int currentY, boolean isCompact) {
        context.drawTextWithShadow(client.textRenderer, "Sort By:", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
        currentY += 14;
        contentHeight += 14;
        int btnWidth = isCompact ? (width - UITheme.Dimensions.PADDING * 2 - 10) : (width - UITheme.Dimensions.PADDING * 2 - 10) / 2;
        int btnHeight = 18;
        int col = 0;

        for (int i = 0; i < sortOptions.length; i++) {
            int btnX = x + UITheme.Dimensions.PADDING + (col * (btnWidth + 4));
            int btnY = currentY;

            boolean isSelected = sortOptions[i].equals(selectedSort);
            boolean isHovered = mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight;

            int bgColor = isSelected ? UITheme.Colors.TOGGLE_ON : (isHovered ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.BUTTON_BG);
            context.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, bgColor);
            drawButtonBorder(context, btnX, btnY, btnWidth, btnHeight);

            String label = isCompact ? sortOptions[i].substring(0, Math.min(3, sortOptions[i].length())).toUpperCase() : sortLabels[i];
            drawCenteredButtonText(context, label, btnX, btnY, btnWidth, btnHeight);

            col++;
            if (col >= (isCompact ? 1 : 2)) {
                col = 0;
                currentY += btnHeight + 2;
                contentHeight += btnHeight + 2;
            }
        }
        if (col != 0) {
            currentY += btnHeight + 2;
            contentHeight += btnHeight + 2;
        }

        currentY += 8;
        contentHeight += 8;
        return currentY;
    }

    private void renderBottomButtons(DrawContext context, int mouseX, int mouseY, float delta) {
        int buttonY = y + height - 30;
        int buttonWidth = (width - UITheme.Dimensions.PADDING * 3) / 2;

        if (applyButton != null) {
            applyButton.setX(x + UITheme.Dimensions.PADDING);
            applyButton.setY(buttonY);
            applyButton.setWidth(buttonWidth);
            applyButton.render(context, mouseX, mouseY, delta);
        }

        if (resetButton != null) {
            resetButton.setX(x + UITheme.Dimensions.PADDING * 2 + buttonWidth);
            resetButton.setY(buttonY);
            resetButton.setWidth(buttonWidth);
            resetButton.render(context, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        if (applyButton != null && isOverButton(applyButton, mouseX, mouseY)) {
            saveSettings();
            notifySettingsChanged();
            return true;
        }

        if (resetButton != null && isOverButton(resetButton, mouseX, mouseY)) {
            resetSettings();
            return true;
        }

        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int currentY = y + 30 + 14 - (int) scrollOffset;
        boolean isCompact = width < 180;
        int btnWidth = isCompact ? (width - UITheme.Dimensions.PADDING * 2 - 10) : (width - UITheme.Dimensions.PADDING * 2 - 10) / 2;
        int btnHeight = 18;

        for (int i = 0; i < sortOptions.length; i++) {
            int col = isCompact ? 0 : (i % 2);
            int row = isCompact ? i : (i / 2);
            int btnX = x + UITheme.Dimensions.PADDING + (col * (btnWidth + 4));
            int btnY = currentY + row * (btnHeight + 2);

            if (mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight) {
                selectedSort = sortOptions[i];
                return true;
            }
        }

        return false;
    }

    private boolean isOverButton(CustomButton button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - verticalAmount * 10, Math.max(0, contentHeight - (height - 70))));
            scrollBar.setScrollPercentage((float) (scrollOffset / Math.max(1, contentHeight - (height - 70))));
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            scrollOffset = scrollBar.getScrollPercentage() * Math.max(0, contentHeight - (height - 70));
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollBar.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    private void resetSettings() {
        selectedSort = "downloads";
        versionFilter = "all";
        scrollOffset = 0;
        saveSettings();
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }
}

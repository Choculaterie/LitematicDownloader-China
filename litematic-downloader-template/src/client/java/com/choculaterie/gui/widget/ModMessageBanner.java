package com.choculaterie.gui.widget;

import com.choculaterie.gui.theme.UITheme;
import com.choculaterie.models.ModMessage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;

import java.util.function.Consumer;

public class ModMessageBanner implements Drawable, Element {
    private static final int BANNER_HEIGHT = 30;
    private static final int CLOSE_BUTTON_SIZE = UITheme.Dimensions.ICON_SMALL;

    private static final int INFO_BG_COLOR = 0xE02196F3;
    private static final int WARNING_BG_COLOR = 0xE0FF9800;
    private static final int ERROR_BG_COLOR = 0xE0F44336;
    private static final int DEFAULT_BG_COLOR = 0xE03A3A3A;
    private static final int CLOSE_HOVER_COLOR = 0x40FFFFFF;

    private final MinecraftClient client;
    private int x;
    private int y;
    private int width;
    private ModMessage message;
    private Consumer<ModMessage> onDismiss;
    private boolean visible = false;

    public ModMessageBanner(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.client = MinecraftClient.getInstance();
    }

    public void setMessage(ModMessage message) {
        this.message = message;
        this.visible = message != null && message.hasMessage();
    }

    public void setOnDismiss(Consumer<ModMessage> onDismiss) {
        this.onDismiss = onDismiss;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public boolean isVisible() {
        return visible;
    }

    public int getHeight() {
        return visible ? BANNER_HEIGHT : 0;
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) {
            return false;
        }
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BANNER_HEIGHT;
    }

    private int getCloseButtonX() {
        return x + width - CLOSE_BUTTON_SIZE - UITheme.Dimensions.PADDING;
    }

    private int getCloseButtonY() {
        return y + (BANNER_HEIGHT - CLOSE_BUTTON_SIZE) / 2;
    }

    private boolean isOverCloseButton(double mouseX, double mouseY) {
        int closeX = getCloseButtonX();
        int closeY = getCloseButtonY();
        return mouseX >= closeX && mouseX < closeX + CLOSE_BUTTON_SIZE &&
               mouseY >= closeY && mouseY < closeY + CLOSE_BUTTON_SIZE;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible || message == null || !message.hasMessage()) {
            return;
        }

        int bgColor = getBackgroundColor(message.type());

        drawBackground(context, bgColor);
        drawBorder(context);
        drawMessageText(context);
        drawCloseButton(context, mouseX, mouseY);
    }

    private void drawBackground(DrawContext context, int bgColor) {
        context.fill(x, y, x + width, y + BANNER_HEIGHT, bgColor);
    }

    private void drawBorder(DrawContext context) {
        context.fill(x, y + BANNER_HEIGHT - UITheme.Dimensions.BORDER_WIDTH,
                    x + width, y + BANNER_HEIGHT, UITheme.Colors.DIVIDER);
    }

    private void drawMessageText(DrawContext context) {
        String text = message.message();
        if (text == null) return;

        int maxTextWidth = width - UITheme.Dimensions.PADDING * 2 - CLOSE_BUTTON_SIZE - UITheme.Dimensions.PADDING;
        text = truncateText(text, maxTextWidth);

        int textY = y + (BANNER_HEIGHT - UITheme.Typography.TEXT_HEIGHT) / 2;
        context.drawTextWithShadow(client.textRenderer, text, x + UITheme.Dimensions.PADDING, textY, UITheme.Colors.TEXT_PRIMARY);
    }

    private String truncateText(String text, int maxWidth) {
        if (client.textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }

        while (client.textRenderer.getWidth(text + "...") > maxWidth && text.length() > 0) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    private void drawCloseButton(DrawContext context, int mouseX, int mouseY) {
        int closeX = getCloseButtonX();
        int closeY = getCloseButtonY();

        if (isOverCloseButton(mouseX, mouseY)) {
            context.fill(closeX, closeY, closeX + CLOSE_BUTTON_SIZE, closeY + CLOSE_BUTTON_SIZE, CLOSE_HOVER_COLOR);
        }

        String closeText = "✕";
        int closeTextWidth = client.textRenderer.getWidth(closeText);
        context.drawTextWithShadow(client.textRenderer, closeText,
                closeX + (CLOSE_BUTTON_SIZE - closeTextWidth) / 2,
                closeY + (CLOSE_BUTTON_SIZE - UITheme.Typography.TEXT_HEIGHT) / 2,
                UITheme.Colors.TEXT_PRIMARY);
    }

    private int getBackgroundColor(String type) {
        if (type == null) {
            return DEFAULT_BG_COLOR;
        }
        return switch (type.toLowerCase()) {
            case "info" -> INFO_BG_COLOR;
            case "warning" -> WARNING_BG_COLOR;
            case "error" -> ERROR_BG_COLOR;
            default -> DEFAULT_BG_COLOR;
        };
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || button != 0) {
            return false;
        }

        if (isOverCloseButton(mouseX, mouseY)) {
            visible = false;
            if (onDismiss != null && message != null) {
                onDismiss.accept(message);
            }
            return true;
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


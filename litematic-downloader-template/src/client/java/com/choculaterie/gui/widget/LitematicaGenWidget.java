package com.choculaterie.gui.widget;

import com.choculaterie.gui.theme.UITheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LitematicaGenWidget implements Drawable, Element {
    private final String key;
    private final String name;
    private final Map<String, List<String>> conditions;
    private final boolean hasX;
    private final boolean hasY;
    private final boolean hasZ;
    private final Runnable onClose;
    private final MinecraftClient client;

    private CustomTextField xSizeField;
    private CustomTextField ySizeField;
    private CustomTextField zSizeField;
    private CustomButton generateButton;
    private CustomButton cancelButton;

    private int xMin = 0;
    private int xMax = Integer.MAX_VALUE;
    private int yMin = 0;
    private int yMax = Integer.MAX_VALUE;
    private int zMin = 0;
    private int zMax = Integer.MAX_VALUE;

    private int xMod = -1;
    private int xModRemainder = 0;

    private final int panelWidth = 400;
    private final int panelHeight = 260;
    private int panelX;
    private int panelY;
    private boolean wasEscapePressed;
    private boolean isDownloading = false;
    private String downloadStatusMessage = "";
    private String errorMessage = "";

    public LitematicaGenWidget(String key, String name, Map<String, List<String>> conditions,
                               boolean hasX, boolean hasY, boolean hasZ, Runnable onClose) {
        this.key = key;
        this.name = name;
        this.conditions = conditions;
        this.hasX = hasX;
        this.hasY = hasY;
        this.hasZ = hasZ;
        this.onClose = onClose;
        this.client = MinecraftClient.getInstance();

        parseConditions();
        initWidgets();
    }

    private void parseConditions() {
        Pattern minPattern = Pattern.compile("min\\((\\d+)\\)");
        Pattern maxPattern = Pattern.compile("max\\((\\d+)\\)");
        Pattern modPattern = Pattern.compile("mod\\((\\d+),(\\d+)\\)");

        if (conditions.containsKey("x")) {
            for (String condition : conditions.get("x")) {
                parseCondition(condition, minPattern, maxPattern, modPattern, "x");
            }
        }

        if (conditions.containsKey("y")) {
            for (String condition : conditions.get("y")) {
                parseCondition(condition, minPattern, maxPattern, modPattern, "y");
            }
        }

        if (conditions.containsKey("z")) {
            for (String condition : conditions.get("z")) {
                parseCondition(condition, minPattern, maxPattern, modPattern, "z");
            }
        }
    }

    private void parseCondition(String condition, Pattern minPattern, Pattern maxPattern, Pattern modPattern, String axis) {
        Matcher minMatcher = minPattern.matcher(condition);
        Matcher maxMatcher = maxPattern.matcher(condition);
        Matcher modMatcher = modPattern.matcher(condition);

        if (minMatcher.matches()) {
            int value = Integer.parseInt(minMatcher.group(1));
            switch (axis) {
                case "x" -> xMin = Math.max(xMin, value);
                case "y" -> yMin = Math.max(yMin, value);
                case "z" -> zMin = Math.max(zMin, value);
            }
        } else if (maxMatcher.matches()) {
            int value = Integer.parseInt(maxMatcher.group(1));
            switch (axis) {
                case "x" -> xMax = Math.min(xMax, value);
                case "y" -> yMax = Math.min(yMax, value);
                case "z" -> zMax = Math.min(zMax, value);
            }
        } else if (modMatcher.matches()) {
            int mod = Integer.parseInt(modMatcher.group(1));
            int remainder = Integer.parseInt(modMatcher.group(2));
            if (axis.equals("x")) {
                xMod = mod;
                xModRemainder = remainder;
            }
        }
    }

    private void initWidgets() {
        if (this.client == null || this.client.getWindow() == null) return;

        int screenWidth = this.client.getWindow().getScaledWidth();
        int screenHeight = this.client.getWindow().getScaledHeight();

        panelX = (screenWidth - panelWidth) / 2;
        panelY = (screenHeight - panelHeight) / 2;

        int fieldWidth = 200;
        int fieldHeight = 20;
        int currentY = panelY + 70;
        int fieldX = panelX + panelWidth - fieldWidth - 20;

        if (hasX) {
            xSizeField = new CustomTextField(this.client, fieldX, currentY, fieldWidth, fieldHeight, Text.of("X Size"));
            xSizeField.setPlaceholder(Text.of(getPlaceholder("x")));
            xSizeField.setText(String.valueOf(xMin));
            currentY += (xMod > 0) ? 45 : 35;
        }

        if (hasY) {
            ySizeField = new CustomTextField(this.client, fieldX, currentY, fieldWidth, fieldHeight, Text.of("Y Size"));
            ySizeField.setPlaceholder(Text.of(getPlaceholder("y")));
            ySizeField.setText(String.valueOf(yMin));
            currentY += 35;
        }

        if (hasZ) {
            zSizeField = new CustomTextField(this.client, fieldX, currentY, fieldWidth, fieldHeight, Text.of("Z Size"));
            zSizeField.setPlaceholder(Text.of(getPlaceholder("z")));
            zSizeField.setText(String.valueOf(zMin));
        }

        int buttonY = panelY + panelHeight - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT;
        int buttonWidth = (panelWidth - UITheme.Dimensions.PADDING * 3) / 2;

        cancelButton = new CustomButton(
                panelX + UITheme.Dimensions.PADDING,
                buttonY,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of("Cancel"),
                button -> close()
        );

        generateButton = new CustomButton(
                panelX + UITheme.Dimensions.PADDING * 2 + buttonWidth,
                buttonY,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of("Generate"),
                button -> generateSchematic()
        );
    }

    private String getPlaceholder(String axis) {
        return switch (axis) {
            case "x" -> String.format("%d - %d", xMin, xMax == Integer.MAX_VALUE ? 2006 : xMax);
            case "y" -> String.format("%d - %d", yMin, yMax == Integer.MAX_VALUE ? 256 : yMax);
            case "z" -> String.format("%d - %d", zMin, zMax == Integer.MAX_VALUE ? 2006 : zMax);
            default -> "Enter value";
        };
    }

    private void generateSchematic() {
        errorMessage = "";
        Map<String, Integer> sizes = new HashMap<>();

        if (hasX && xSizeField != null) {
            try {
                int xSize = Integer.parseInt(xSizeField.getText());
                if (!validateValue(xSize, xMin, xMax, xMod, xModRemainder, "X")) {
                    return;
                }
                sizes.put("x", xSize);
            } catch (NumberFormatException e) {
                showError("Invalid X size");
                return;
            }
        }

        if (hasY && ySizeField != null) {
            try {
                int ySize = Integer.parseInt(ySizeField.getText());
                if (!validateValue(ySize, yMin, yMax, -1, 0, "Y")) {
                    return;
                }
                sizes.put("y", ySize);
            } catch (NumberFormatException e) {
                showError("Invalid Y size");
                return;
            }
        }

        if (hasZ && zSizeField != null) {
            try {
                int zSize = Integer.parseInt(zSizeField.getText());
                if (!validateValue(zSize, zMin, zMax, -1, 0, "Z")) {
                    return;
                }
                sizes.put("z", zSize);
            } catch (NumberFormatException e) {
                showError("Invalid Z size");
                return;
            }
        }

        downloadLitematica(sizes);
    }

    private boolean validateValue(int value, int min, int max, int mod, int remainder, String axis) {
        if (value < min) {
            showError(axis + " must be at least " + min + ". Try " + min);
            return false;
        }
        if (value > max) {
            if (mod > 0) {
                int suggested = findClosestValid(max, mod, remainder, min, max, false);
                showError(axis + " must be at most " + max + ". Try " + suggested);
            } else {
                showError(axis + " must be at most " + max + ". Try " + max);
            }
            return false;
        }
        if (mod > 0 && value % mod != remainder) {
            int suggested = findClosestValid(value, mod, remainder, min, max, true);
            if (remainder == 0) {
                showError(value + " does not divide by " + mod + ". Try " + suggested);
            } else {
                showError(value + " does not leave " + remainder + " after being divided by " + mod + ". Try " + suggested);
            }
            return false;
        }
        return true;
    }

    private int findClosestValid(int value, int mod, int remainder, int min, int max, boolean searchBoth) {
        int currentRemainder = value % mod;
        int diff = (remainder - currentRemainder + mod) % mod;

        int higher = value + diff;
        if (diff == 0) {
            higher = value + mod;
        }

        int lower = value - (currentRemainder - remainder + mod) % mod;
        if (lower == value) {
            lower = value - mod;
        }

        if (!searchBoth) {
            if (lower >= min && lower <= max) return lower;
            if (higher >= min && higher <= max) return higher;
            return min;
        }

        boolean lowerValid = lower >= min && lower <= max;
        boolean higherValid = higher >= min && higher <= max;

        if (lowerValid && higherValid) {
            return (value - lower) <= (higher - value) ? lower : higher;
        } else if (lowerValid) {
            return lower;
        } else if (higherValid) {
            return higher;
        }

        return min;
    }

    private void showError(String message) {
        errorMessage = message;
    }

    private void downloadLitematica(Map<String, Integer> sizes) {
        isDownloading = true;
        downloadStatusMessage = "Downloading...";
        generateButton.active = false;

        com.choculaterie.network.MinemevNetworkManager.downloadGeneratedLitematica(key, sizes, name)
            .thenAccept(filePath -> {
                if (this.client != null && this.client.player != null) {
                    this.client.execute(() -> {
                        downloadStatusMessage = "Downloaded successfully!";

                        new Thread(() -> {
                            try {
                                Thread.sleep(1500);
                                this.client.execute(this::close);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }).start();
                    });
                }
                System.out.println("[LitematicaGenWidget] Downloaded to: " + filePath);
            })
            .exceptionally(throwable -> {
                if (this.client != null && this.client.player != null) {
                    this.client.execute(() -> {
                        isDownloading = false;
                        downloadStatusMessage = "Download failed!";
                        generateButton.active = true;
                    });
                }
                System.err.println("[LitematicaGenWidget] Download failed: " + throwable.getMessage());
                return null;
            });
    }

    private void close() {
        if (onClose != null) {
            onClose.run();
        }
    }

    private void handleEscapeKey() {
        if (client == null || client.getWindow() == null) return;
        if (isDownloading) return;

        long windowHandle = client.getWindow().getHandle();
        boolean escapePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;

        if (escapePressed && !wasEscapePressed) {
            close();
        }

        wasEscapePressed = escapePressed;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        handleEscapeKey();

        if (client != null && client.getWindow() != null) {
            context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), UITheme.Colors.OVERLAY_BG);
        }

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, UITheme.Colors.PANEL_BG_SECONDARY);

        int borderColor = UITheme.Colors.BUTTON_BORDER;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 1, borderColor);
        context.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, borderColor);
        context.fill(panelX, panelY, panelX + 1, panelY + panelHeight, borderColor);
        context.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, borderColor);

        if (client != null && client.textRenderer != null) {
            context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    "Generate: " + name,
                    panelX + panelWidth / 2,
                    panelY + 15,
                    UITheme.Colors.TEXT_PRIMARY
            );

            context.drawTextWithShadow(
                    client.textRenderer,
                    "Configure dimensions:",
                    panelX + 20,
                    panelY + 40,
                    UITheme.Colors.TEXT_SUBTITLE
            );

            int currentY = panelY + 70;
            int labelX = panelX + 20;

            if (hasX) {
                String xLabel = String.format("X Size (%d - %d)", xMin, xMax == Integer.MAX_VALUE ? 2006 : xMax);
                context.drawTextWithShadow(client.textRenderer, xLabel, labelX, currentY + 6, UITheme.Colors.TEXT_PRIMARY);
                if (xMod > 0) {
                    String modConstraint;
                    if (xModRemainder == 0) {
                        modConstraint = "Must be divisible by " + xMod;
                    } else {
                        modConstraint = String.format("Divided by %d must leave %d remainder", xMod, xModRemainder);
                    }
                    int fieldRightX = panelX + panelWidth - 20;
                    int constraintWidth = client.textRenderer.getWidth(modConstraint);
                    context.drawTextWithShadow(client.textRenderer, modConstraint, fieldRightX - constraintWidth, currentY - 14, 0xFFFFAA00);
                }
                currentY += (xMod > 0) ? 45 : 35;
            }

            if (hasY) {
                String yLabel = String.format("Y Size (%d - %d)", yMin, yMax == Integer.MAX_VALUE ? 256 : yMax);
                context.drawTextWithShadow(client.textRenderer, yLabel, labelX, currentY + 6, UITheme.Colors.TEXT_PRIMARY);
                currentY += 35;
            }

            if (hasZ) {
                String zLabel = String.format("Z Size (%d - %d)", zMin, zMax == Integer.MAX_VALUE ? 2006 : zMax);
                context.drawTextWithShadow(client.textRenderer, zLabel, labelX, currentY + 6, UITheme.Colors.TEXT_PRIMARY);
            }
        }

        if (xSizeField != null) {
            xSizeField.render(context, mouseX, mouseY, delta);
        }
        if (ySizeField != null) {
            ySizeField.render(context, mouseX, mouseY, delta);
        }
        if (zSizeField != null) {
            zSizeField.render(context, mouseX, mouseY, delta);
        }
        if (cancelButton != null) {
            cancelButton.active = !isDownloading;
            cancelButton.render(context, mouseX, mouseY, delta);
        }
        if (generateButton != null) {
            generateButton.render(context, mouseX, mouseY, delta);
        }

        if (!errorMessage.isEmpty() && client != null && client.textRenderer != null) {
            context.drawCenteredTextWithShadow(
                client.textRenderer,
                errorMessage,
                panelX + panelWidth / 2,
                panelY + panelHeight - 50,
                0xFFFF5555
            );
        } else if (isDownloading && !downloadStatusMessage.isEmpty() && client != null && client.textRenderer != null) {
            int statusColor = downloadStatusMessage.contains("success") ? 0xFF55FF55 :
                            downloadStatusMessage.contains("failed") ? 0xFFFF5555 : 0xFFFFFF55;
            context.drawCenteredTextWithShadow(
                client.textRenderer,
                downloadStatusMessage,
                panelX + panelWidth / 2,
                panelY + panelHeight - 50,
                statusColor
            );
        }
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOverPopup(mouseX, mouseY)) {
            close();
            return true;
        }

        if (xSizeField != null && isMouseOverWidget(xSizeField, mouseX, mouseY)) {
            xSizeField.setFocused(true);
            if (ySizeField != null) ySizeField.setFocused(false);
            if (zSizeField != null) zSizeField.setFocused(false);
            return true;
        }
        if (ySizeField != null && isMouseOverWidget(ySizeField, mouseX, mouseY)) {
            ySizeField.setFocused(true);
            if (xSizeField != null) xSizeField.setFocused(false);
            if (zSizeField != null) zSizeField.setFocused(false);
            return true;
        }
        if (zSizeField != null && isMouseOverWidget(zSizeField, mouseX, mouseY)) {
            zSizeField.setFocused(true);
            if (xSizeField != null) xSizeField.setFocused(false);
            if (ySizeField != null) ySizeField.setFocused(false);
            return true;
        }
        if (cancelButton != null && isMouseOverWidget(cancelButton, mouseX, mouseY)) {
            close();
            return true;
        }
        if (generateButton != null && isMouseOverWidget(generateButton, mouseX, mouseY)) {
            generateSchematic();
            return true;
        }
        return true;
    }

    private boolean isMouseOverPopup(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + panelWidth &&
               mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    private boolean isMouseOverWidget(Element widget, double mouseX, double mouseY) {
        if (widget instanceof CustomTextField field) {
            return mouseX >= field.getX() && mouseX < field.getX() + field.getWidth() &&
                   mouseY >= field.getY() && mouseY < field.getY() + field.getHeight();
        } else if (widget instanceof CustomButton button) {
            return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth() &&
                   mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
        }
        return false;
    }
}


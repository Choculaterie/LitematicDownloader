package com.choculaterie.gui.widget;

import com.choculaterie.gui.theme.UITheme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SchematicExportPanel {

    public static final int PANEL_HEIGHT = 128;

    private static final int PAD = 6;
    private static final int[] RESOLUTIONS = {512, 1024, 2048};

    private final SchematicRenderer renderer;
    private final Minecraft client;

    private int x, y, width;

    private final Slider pitchSlider;
    private final Slider yawSlider;
    private final Slider zoomSlider;
    private final Slider[] sliders;

    private CustomButton autoIsoButton;
    private CustomButton bgButton;
    private CustomButton saveButton;
    private CustomButton openFolderButton;
    private final CustomButton[] resButtons = new CustomButton[RESOLUTIONS.length];

    private int exportResolution = 1024;
    private boolean transparentBackground = true;

    private File litematicFile;

    private volatile boolean exporting = false;
    private volatile boolean exportRequested = false;
    private volatile String statusMessage;
    private volatile int statusColor;
    private volatile long statusClearAtNanos;

    public SchematicExportPanel(SchematicRenderer renderer) {
        this.renderer = renderer;
        this.client = Minecraft.getInstance();

        pitchSlider = new Slider("Pitch", -90f, 90f,
                renderer::getRotationX, v -> renderer.setRotationX(v),
                v -> Math.round(v) + "°");
        yawSlider = new Slider("Yaw", 0f, 360f,
                renderer::getRotationY, v -> renderer.setRotationY(v),
                v -> Math.round(v) + "°");
        zoomSlider = new Slider("Zoom", 0f, 1f,
                () -> {
                    float maxD = Math.max(8f, renderer.getFitDistance() * 2.5f);
                    float t = (renderer.getDistance() - 2f) / (maxD - 2f);
                    return 1f - clamp01(t);
                },
                t -> {
                    float maxD = Math.max(8f, renderer.getFitDistance() * 2.5f);
                    renderer.setDistance(2f + (1f - t) * (maxD - 2f));
                },
                null);
        sliders = new Slider[]{pitchSlider, yawSlider, zoomSlider};
    }

    public void setLitematicFile(File file) {
        this.litematicFile = file;
    }

    public void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        layout();
    }

    private void layout() {
        int innerX = x + PAD;
        int innerW = width - PAD * 2;

        int halfW = innerW / 2 - 3;
        autoIsoButton = new CustomButton(innerX, y + 4, halfW, 16,
                Component.literal("Auto ISO"), btn -> renderer.setAutoIsometric());
        bgButton = new CustomButton(innerX + innerW - halfW, y + 4, halfW, 16,
                Component.literal(bgLabel()), btn -> {
            transparentBackground = !transparentBackground;
            if (bgButton != null) bgButton.setMessage(Component.literal(bgLabel()));
        });

        int sliderY = y + 26;
        pitchSlider.layout(innerX, innerW, sliderY);
        yawSlider.layout(innerX, innerW, sliderY + 14);
        zoomSlider.layout(innerX, innerW, sliderY + 28);

        int resY = y + 74;
        int gap = 4;
        int resW = (innerW - gap * (RESOLUTIONS.length - 1)) / RESOLUTIONS.length;
        for (int i = 0; i < RESOLUTIONS.length; i++) {
            final int res = RESOLUTIONS[i];
            resButtons[i] = new CustomButton(innerX + i * (resW + gap), resY, resW, 15,
                    Component.literal(String.valueOf(res)), btn -> exportResolution = res);
        }

        int folderBtnSize = 18;
        saveButton = new CustomButton(innerX, y + 95, innerW - folderBtnSize - 4, 18,
                Component.literal("Save Render"), btn -> exportRequested = true);
        openFolderButton = new CustomButton(innerX + innerW - folderBtnSize, y + 95, folderBtnSize, 18,
                Component.literal("📁"), btn -> openRenderFolder());
    }

    private String bgLabel() {
        return transparentBackground ? "BG: None" : "BG: Dark";
    }

    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (exportRequested && !exporting) {
            exportRequested = false;
            beginExport();
        }

        context.fill(x, y, x + width, y + PANEL_HEIGHT, 0xF01C1C1C);
        context.fill(x, y, x + width, y + 1, UITheme.Colors.PANEL_BORDER);

        long window = GLFW.glfwGetCurrentContext();
        boolean leftDown = window != 0L
                && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        Font font = client.font;
        for (Slider slider : sliders) {
            if (slider.dragging) {
                if (!leftDown) {
                    slider.dragging = false;
                } else {
                    slider.updateFromMouse(mouseX);
                }
            }
            slider.render(context, font, mouseX, mouseY);
        }

        autoIsoButton.extractRenderState(context, mouseX, mouseY, delta);
        bgButton.extractRenderState(context, mouseX, mouseY, delta);

        for (int i = 0; i < resButtons.length; i++) {
            CustomButton btn = resButtons[i];
            btn.extractRenderState(context, mouseX, mouseY, delta);
            if (RESOLUTIONS[i] == exportResolution) {
                drawAccentBorder(context, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight());
            }
        }

        saveButton.active = !exporting;
        saveButton.setMessage(Component.literal(exporting ? "Rendering..." : "Save Render"));
        saveButton.extractRenderState(context, mouseX, mouseY, delta);
        openFolderButton.extractRenderState(context, mouseX, mouseY, delta);

        if (statusMessage != null) {
            if (System.nanoTime() > statusClearAtNanos) {
                statusMessage = null;
            } else {
                String msg = statusMessage;
                int maxW = width - PAD * 2;
                while (font.width(msg) > maxW && msg.length() > 4) {
                    msg = msg.substring(0, msg.length() - 4) + "...";
                }
                context.text(font, msg, x + (width - font.width(msg)) / 2, y + 117, statusColor);
            }
        }
    }

    private void drawAccentBorder(GuiGraphicsExtractor context, int bx, int by, int bw, int bh) {
        int accent = UITheme.Colors.TOGGLE_ON;
        context.fill(bx, by, bx + bw, by + 1, accent);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, accent);
        context.fill(bx, by, bx + 1, by + bh, accent);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, accent);
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        if (!isMouseOver(mx, my)) {
            return false;
        }

        if (autoIsoButton.mouseClicked(click, doubled)) return true;
        if (bgButton.mouseClicked(click, doubled)) return true;
        for (CustomButton btn : resButtons) {
            if (btn.mouseClicked(click, doubled)) return true;
        }
        if (saveButton.mouseClicked(click, doubled)) return true;
        if (openFolderButton.mouseClicked(click, doubled)) return true;

        if (click.button() == 0) {
            for (Slider slider : sliders) {
                if (slider.isInside(mx, my)) {
                    slider.dragging = true;
                    slider.updateFromMouse(mx);
                    return true;
                }
            }
        }
        return true;
    }

    public boolean isMouseOver(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + PANEL_HEIGHT;
    }

    private Path rendersDir() {
        return FabricLoader.getInstance().getGameDir().resolve("schematics").resolve("renders");
    }

    private void openRenderFolder() {
        try {
            Path dir = rendersDir();
            Files.createDirectories(dir);
            Util.getPlatform().openPath(dir);
        } catch (Exception e) {
            setStatus("Could not open folder", UITheme.Colors.TOAST_ACCENT_ERROR);
        }
    }

    private void beginExport() {
        if (litematicFile == null) {
            setStatus("No file loaded", UITheme.Colors.TOAST_ACCENT_ERROR);
            return;
        }
        exporting = true;
        setStatus("Rendering...", UITheme.Colors.TEXT_SUBTITLE);

        File outputDir = rendersDir().toFile();

        String base = litematicFile.getName();
        int dot = base.toLowerCase().lastIndexOf(".litematic");
        if (dot > 0) {
            base = base.substring(0, dot);
        }

        renderer.exportRender(outputDir, base, exportResolution, transparentBackground,
                file -> {
                    exporting = false;
                    setStatus("Saved " + file.getName(), UITheme.Colors.TOAST_ACCENT_SUCCESS);
                },
                error -> {
                    exporting = false;
                    setStatus("Error: " + error, UITheme.Colors.TOAST_ACCENT_ERROR);
                });
    }

    private void setStatus(String message, int color) {
        statusMessage = message;
        statusColor = color;
        statusClearAtNanos = System.nanoTime() + 6_000_000_000L;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static final class Slider {
        private final String label;
        private float min;
        private float max;
        private final Supplier<Float> getter;
        private final Consumer<Float> setter;
        private final Function<Float, String> valueFormat;

        private int labelX;
        private int trackX;
        private int trackW;
        private int rowY;
        private static final int ROW_H = 14;

        private boolean dragging = false;

        Slider(String label, float min, float max, Supplier<Float> getter, Consumer<Float> setter,
               Function<Float, String> valueFormat) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
            this.valueFormat = valueFormat;
        }

        void layout(int innerX, int innerW, int rowY) {
            this.rowY = rowY;
            this.labelX = innerX;
            int labelW = 32;
            int valueW = 34;
            this.trackX = innerX + labelW;
            this.trackW = innerW - labelW - valueW;
        }

        boolean isInside(double mx, double my) {
            return mx >= trackX - 4 && mx <= trackX + trackW + 4 && my >= rowY && my < rowY + ROW_H;
        }

        void updateFromMouse(double mx) {
            float t = clamp01((float) (mx - trackX) / Math.max(1, trackW));
            setter.accept(min + t * (max - min));
        }

        void render(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
            int centerY = rowY + ROW_H / 2;
            context.text(font, label, labelX, centerY - 4, UITheme.Colors.TEXT_MUTED);

            int trackY = centerY - 1;
            context.fill(trackX, trackY, trackX + trackW, trackY + 3, UITheme.Colors.FIELD_BG);
            context.fill(trackX, trackY, trackX + trackW, trackY + 1, UITheme.Colors.FIELD_BORDER);

            float value = getter.get();
            float t = clamp01((value - min) / (max - min));
            int knobX = trackX + Math.round(t * trackW);

            context.fill(trackX, trackY, knobX, trackY + 3, UITheme.Colors.TOGGLE_ON);

            boolean hovered = dragging || isInside(mouseX, mouseY);
            int knobColor = hovered ? UITheme.Colors.SCROLLBAR_THUMB_HOVER : UITheme.Colors.SCROLLBAR_THUMB;
            context.fill(knobX - 3, rowY + 1, knobX + 3, rowY + ROW_H - 1, knobColor);
            context.fill(knobX - 3, rowY + 1, knobX + 3, rowY + 2, UITheme.Colors.BUTTON_BORDER);

            if (valueFormat != null) {
                String text = valueFormat.apply(value);
                context.text(font, text, trackX + trackW + 34 - font.width(text), centerY - 4,
                        UITheme.Colors.TEXT_SUBTITLE);
            }
        }
    }
}

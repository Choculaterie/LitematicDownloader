package com.choculaterie.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Custom toggle button for on/off settings
 */
public class ToggleButton extends ButtonWidget {
    private static final int TOGGLE_WIDTH = 40;
    private static final int TOGGLE_HEIGHT = 20;

    private static final int TRACK_OFF_COLOR = 0xFF4A4A4A;
    private static final int TRACK_ON_COLOR = 0xFF4CAF50;
    private static final int KNOB_COLOR = 0xFFFFFFFF;
    private static final int BORDER_COLOR = 0xFF555555;

    private boolean toggled;
    private final Consumer<Boolean> onToggle;

    public ToggleButton(int x, int y, boolean initialState, Consumer<Boolean> onToggle) {
        super(x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, Text.empty(), button -> {
            ToggleButton toggle = (ToggleButton) button;
            toggle.toggled = !toggle.toggled;
            if (toggle.onToggle != null) {
                toggle.onToggle.accept(toggle.toggled);
            }
        }, DEFAULT_NARRATION_SUPPLIER);
        this.toggled = initialState;
        this.onToggle = onToggle;
    }

    public boolean isToggled() {
        return toggled;
    }

    public void setToggled(boolean toggled) {
        this.toggled = toggled;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean isHovered = mouseX >= this.getX() && mouseY >= this.getY() &&
                mouseX < this.getX() + this.getWidth() && mouseY < this.getY() + this.getHeight();

        int trackColor = toggled ? TRACK_ON_COLOR : TRACK_OFF_COLOR;
        if (isHovered) {
            // Lighten on hover
            trackColor = toggled ? 0xFF5FBF63 : 0xFF5A5A5A;
        }

        // Draw track background (rounded appearance via multiple rectangles)
        int trackY = this.getY() + 3;
        int trackHeight = this.getHeight() - 6;
        context.fill(this.getX() + 2, trackY, this.getX() + this.getWidth() - 2, trackY + trackHeight, trackColor);

        // Draw border
        context.fill(this.getX(), trackY, this.getX() + this.getWidth(), trackY + 1, BORDER_COLOR); // Top
        context.fill(this.getX(), trackY + trackHeight - 1, this.getX() + this.getWidth(), trackY + trackHeight, BORDER_COLOR); // Bottom
        context.fill(this.getX(), trackY, this.getX() + 1, trackY + trackHeight, BORDER_COLOR); // Left
        context.fill(this.getX() + this.getWidth() - 1, trackY, this.getX() + this.getWidth(), trackY + trackHeight, BORDER_COLOR); // Right

        // Draw knob
        int knobSize = trackHeight - 4;
        int knobX = toggled ? (this.getX() + this.getWidth() - knobSize - 4) : (this.getX() + 4);
        int knobY = trackY + 2;
        context.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, KNOB_COLOR);
    }
}


package com.choculaterie.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;

interface ListEntryVisual {
    int getX();

    int getY();

    int getWidth();

    int getHeight();

    void setX(int x);

    void setY(int y);

    void setWidth(int width);

    void setPressed(boolean pressed);

    void setSelected(boolean selected);

    void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta);
}

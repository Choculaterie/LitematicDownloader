package com.choculaterie.gui.widget;

import com.choculaterie.models.PromotionInfo;
import com.choculaterie.vanilib.gui.theme.UIDraw;
import com.choculaterie.vanilib.gui.theme.UIIcon;
import com.choculaterie.vanilib.gui.theme.UITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.util.List;

public class PromotionEntryWidget implements ListEntryVisual {
    private static final int LINE_HEIGHT = UITheme.Typography.LINE_HEIGHT;
    private static final int CONTENT_SPACING = 5;
    private static final int MAX_DESCRIPTION_LINES = 3;
    private static final int STRIPE_WIDTH = 2;

    private final PromotionInfo promotion;
    private final Minecraft client;
    private int x;
    private int y;
    private int width;
    private int calculatedHeight;
    private boolean isHovered;
    private boolean isPressed;

    public PromotionEntryWidget(PromotionInfo promotion, int x, int y, int width) {
        this.promotion = promotion;
        this.client = Minecraft.getInstance();
        this.x = x;
        this.y = y;
        this.width = width;
        calculateHeight();
    }

    public PromotionInfo getPromotion() {
        return promotion;
    }

    private int contentX() {
        return x + UITheme.Dimensions.PADDING + STRIPE_WIDTH;
    }

    private int contentWidth() {
        return Math.max(1, width - UITheme.Dimensions.PADDING * 2 - STRIPE_WIDTH);
    }

    private List<FormattedCharSequence> wrap(String text) {
        return client.font.split(Component.literal(text), contentWidth());
    }

    private List<FormattedCharSequence> titleLines() {
        return wrap(promotion.title());
    }

    private List<FormattedCharSequence> descriptionLines() {
        String description = promotion.description();
        if (description == null || description.isBlank()) return List.of();
        List<FormattedCharSequence> lines = wrap(description);
        return lines.size() > MAX_DESCRIPTION_LINES ? lines.subList(0, MAX_DESCRIPTION_LINES) : lines;
    }

    private void calculateHeight() {
        int h = UITheme.Dimensions.PADDING;
        h += LINE_HEIGHT + CONTENT_SPACING;
        h += titleLines().size() * LINE_HEIGHT + CONTENT_SPACING;
        int descriptionLines = descriptionLines().size();
        if (descriptionLines > 0) h += descriptionLines * LINE_HEIGHT + CONTENT_SPACING;
        h += LINE_HEIGHT;
        h += UITheme.Dimensions.PADDING;
        calculatedHeight = h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        isHovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + calculatedHeight;

        int bg = isPressed && isHovered ? UITheme.Colors.SURFACE_SELECTED
            : isHovered ? UITheme.Colors.SURFACE_HOVER : UITheme.Colors.SURFACE;
        context.fill(x, y, x + width, y + calculatedHeight, bg);
        UIDraw.outline(context, x, y, x + width, y + calculatedHeight,
            isHovered ? UITheme.Colors.SCROLLBAR_THUMB_HOVER : UITheme.Colors.PANEL_BORDER);
        context.fill(x + 1, y + 1, x + 1 + STRIPE_WIDTH, y + calculatedHeight - 1, UITheme.Colors.ACCENT_DARK);

        int left = contentX();
        int right = left + contentWidth();
        int currentY = y + UITheme.Dimensions.PADDING;

        String kicker = promotion.kicker();
        String label = kicker == null || kicker.isBlank() ? "Featured" : "Featured · " + kicker;
        UIDraw.text(context, UIDraw.ellipsize(label, contentWidth()), left, currentY, UITheme.Colors.ACCENT);
        currentY += LINE_HEIGHT + CONTENT_SPACING;

        for (FormattedCharSequence line : titleLines()) {
            UIDraw.text(context, line, left, currentY, UITheme.Colors.TEXT_HIGHLIGHT);
            currentY += LINE_HEIGHT;
        }
        currentY += CONTENT_SPACING;

        List<FormattedCharSequence> description = descriptionLines();
        for (FormattedCharSequence line : description) {
            UIDraw.text(context, line, left, currentY, UITheme.Colors.TEXT_SUBTITLE);
            currentY += LINE_HEIGHT;
        }
        if (!description.isEmpty()) currentY += CONTENT_SPACING;

        String cta = promotion.ctaText() == null || promotion.ctaText().isBlank() ? "Open" : promotion.ctaText();
        int ctaWidth = UIIcon.SIZE + 4 + client.font.width(cta);
        int ctaColor = isHovered ? UITheme.Colors.TEXT_LINK_HOVER : UITheme.Colors.TEXT_LINK;
        UIDraw.iconText(context, UIIcon.EXTERNAL, cta, right - ctaWidth, currentY, ctaColor);
        String host = host(promotion.linkUrl());
        if (host != null) {
            UIDraw.text(context, UIDraw.ellipsize(host, contentWidth() - ctaWidth - 8), left, currentY, UITheme.Colors.TEXT_MUTED);
        }
    }

    private static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return calculatedHeight;
    }

    @Override
    public void setX(int x) {
        this.x = x;
    }

    @Override
    public void setY(int y) {
        this.y = y;
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
        calculateHeight();
    }

    @Override
    public void setPressed(boolean pressed) {
        this.isPressed = pressed;
    }

    @Override
    public void setSelected(boolean selected) {
    }
}

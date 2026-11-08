package com.choculaterie.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaledImageCache {
    private static final int MAX_SOURCES = 6;
    private static final int MAX_TEXTURES = 12;
    private static final int MAX_SIDE = 3840;

    private final Map<Identifier, BufferedImage> sources = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Identifier, BufferedImage> eldest) {
            return size() > MAX_SOURCES;
        }
    };
    private final Map<String, Identifier> textures = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public synchronized void putSource(Identifier original, BufferedImage image) {
        if (original == null || image == null) return;
        sources.put(original, image);
    }

    public Identifier get(Identifier original, int width, int height) {
        if (original == null || width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE) return null;
        String key = original + "|" + width + "x" + height;
        BufferedImage source;
        synchronized (this) {
            Identifier ready = textures.get(key);
            if (ready != null) return ready;
            source = sources.get(original);
        }
        if (source == null || !pending.add(key)) return null;
        CompletableFuture.supplyAsync(() -> toPng(scale(source, width, height))).whenComplete((png, err) -> {
            if (png == null) {
                pending.remove(key);
                return;
            }
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                try {
                    NativeImage image = NativeImage.read(png);
                    String name = "scaled_" + UUID.randomUUID().toString().replace("-", "");
                    DynamicTexture texture = new DynamicTexture(() -> "litematicdownloader:" + name, image);
                    Identifier id = Identifier.parse("litematicdownloader:" + name);
                    client.getTextureManager().register(id, texture);
                    texture.upload();
                    synchronized (this) {
                        textures.put(key, id);
                        while (textures.size() > MAX_TEXTURES) {
                            String eldest = textures.keySet().iterator().next();
                            Identifier evicted = textures.remove(eldest);
                            client.getTextureManager().release(evicted);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    pending.remove(key);
                }
            });
        });
        return null;
    }

    private static BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        if (width < source.getWidth() || height < source.getHeight()) {
            g.drawImage(source.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        } else {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        }
        g.dispose();
        return out;
    }

    private static byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}

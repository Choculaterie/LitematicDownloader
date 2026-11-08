package com.choculaterie.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class LitematicaBridge {
    private static final String MAIN_MENU = "fi.dy.masa.litematica.gui.GuiMainMenu";
    private static final String GUI_PACKAGE = "fi.dy.masa.litematica.gui.";
    private static final String GUI_BASE = "fi.dy.masa.malilib.gui.GuiBase";
    private static final String BUTTON_BASE = "fi.dy.masa.malilib.gui.button.ButtonBase";
    private static final String WIDGET_BASE = "fi.dy.masa.malilib.gui.widgets.WidgetBase";
    private static final String BUTTON_GENERIC = "fi.dy.masa.malilib.gui.button.ButtonGeneric";
    private static final String BUTTON_LISTENER = "fi.dy.masa.malilib.gui.button.IButtonActionListener";
    private static final int BUTTON_GAP = 4;

    private LitematicaBridge() {}

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("litematica");
    }

    public static boolean isLitematicaScreen(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith(GUI_PACKAGE);
    }

    public static boolean openMainMenu(Minecraft client) {
        if (!isAvailable()) return false;
        try {
            Object menu = Class.forName(MAIN_MENU).getConstructor().newInstance();
            if (!(menu instanceof Screen screen)) return false;
            client.gui.setScreen(screen);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    public static boolean addButtonBesideTopRow(Screen screen, String label, String hover, Runnable action) {
        try {
            Class<?> guiBase = Class.forName(GUI_BASE);
            if (!guiBase.isInstance(screen)) return false;
            int width = Minecraft.getInstance().font.width(label) + 12;
            int height = 20;
            int x = screen.width - width - 4;
            int y = 4;
            Field field = guiBase.getDeclaredField("buttons");
            field.setAccessible(true);
            if (field.get(screen) instanceof List<?> buttons && !buttons.isEmpty()) {
                Method getX = Class.forName(WIDGET_BASE).getMethod("getX");
                Method getY = Class.forName(WIDGET_BASE).getMethod("getY");
                Method getWidth = Class.forName(WIDGET_BASE).getMethod("getWidth");
                Method getHeight = Class.forName(WIDGET_BASE).getMethod("getHeight");
                int topY = Integer.MAX_VALUE;
                for (Object b : buttons) topY = Math.min(topY, (int) getY.invoke(b));
                List<int[]> row = new ArrayList<>();
                int rowHeight = height;
                for (Object b : buttons) {
                    if ((int) getY.invoke(b) != topY) continue;
                    row.add(new int[]{(int) getX.invoke(b), (int) getWidth.invoke(b)});
                    rowHeight = (int) getHeight.invoke(b);
                }
                row.sort((l, r) -> Integer.compare(l[0], r[0]));
                int gap = BUTTON_GAP;
                int[] last = row.get(row.size() - 1);
                int rowRight = last[0] + last[1];
                if (rowRight + gap + width <= screen.width - 2) {
                    x = rowRight + gap;
                    y = topY;
                    height = rowHeight;
                }
            }
            return addButton(screen, guiBase, x, y, width, height, label, hover, action);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            return false;
        }
    }

    private static boolean addButton(Screen screen, Class<?> guiBase, int x, int y, int width, int height, String label, String hover, Runnable action) {
        try {
            Class<?> buttonBase = Class.forName(BUTTON_BASE);
            Class<?> listenerType = Class.forName(BUTTON_LISTENER);
            Object button = Class.forName(BUTTON_GENERIC)
                    .getConstructor(int.class, int.class, int.class, int.class, String.class, String[].class)
                    .newInstance(x, y, width, height, label, new String[]{hover});
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType},
                    (proxy, method, args) -> {
                        if (method.getName().equals("actionPerformedWithButton")) {
                            action.run();
                            return null;
                        }
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            case "toString" -> "LitematicDownloaderButtonListener";
                            default -> null;
                        };
                    });
            Method add = guiBase.getMethod("addButton", buttonBase, listenerType);
            add.invoke(screen, button, listener);
            return true;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            return false;
        }
    }
}

package com.choculaterie.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModKeybindings {

    public static KeyBinding openMenuKey;

    public static void register() {
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.litematic-downloader.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "key.categories.misc"
        ));
    }
}

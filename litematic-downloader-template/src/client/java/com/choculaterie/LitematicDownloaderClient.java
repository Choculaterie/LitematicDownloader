package com.choculaterie;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import com.choculaterie.gui.CacheManager;

public class LitematicDownloaderClient implements ClientModInitializer {

    private static KeyBinding keyBinding;

    @Override
    public void onInitializeClient() {
        // Initialize cache system with background preloading
        System.out.println("Starting LitematicDownloader cache initialization...");
        CacheManager.initializeAtGameStartup();

        // Register keybinding using the Category enum
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.litematic-downloader.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KeyBinding.Category.MISC
        ));

        // Register tick event to check for keybinding presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (keyBinding.wasPressed()) {
                MinecraftClient.getInstance().setScreen(new com.choculaterie.gui.LitematicDownloaderScreen());
            }
        });
    }
}

package com.choculaterie;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class LitematicDownloaderClient implements ClientModInitializer {

	private static KeyBinding keyBinding;

	@Override
	public void onInitializeClient() {
		// Register keybinding (category, key code, key name)
		keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.litematic-downloader.open_gui", // Translation key
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_L, // Default to 'L' key
				"category.litematic-downloader.keybinds" // Category translation key
		));

		// Register tick event to check for keybinding presses
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (keyBinding.wasPressed()) {
				// Open your GUI when the key is pressed
				MinecraftClient.getInstance().setScreen(new com.choculaterie.gui.LitematicDownloaderScreen());
			}
		});
	}
}
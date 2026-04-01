package com.choculaterie;

import com.choculaterie.gui.LitematicDownloaderScreen;
import com.choculaterie.keybind.ModKeybindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class LitematicDownloaderClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ModKeybindings.initialize();
		registerScreenToggleHandler();
	}

	private static void registerScreenToggleHandler() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (ModKeybindings.OPEN_MENU_KEY_BINDING.consumeClick()) {
				toggleLitematicDownloaderScreen(client);
			}
		});
	}

	private static void toggleLitematicDownloaderScreen(Minecraft client) {
		if (client.screen instanceof LitematicDownloaderScreen) {
			client.setScreen(null);
		} else {
			client.setScreen(new LitematicDownloaderScreen());
		}
	}
}

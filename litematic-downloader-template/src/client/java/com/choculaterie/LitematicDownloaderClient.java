package com.choculaterie;

import com.choculaterie.gui.LitematicDownloaderScreen;
import com.choculaterie.keybind.ModKeybindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class LitematicDownloaderClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register keybindings
		ModKeybindings.register();

		// Register tick event to check for key press
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ModKeybindings.openMenuKey.wasPressed()) {
				MinecraftClient mc = MinecraftClient.getInstance();
				// Toggle: close if already open, open if closed
				if (mc.currentScreen instanceof LitematicDownloaderScreen) {
					mc.setScreen(null);
				} else {
					mc.setScreen(new LitematicDownloaderScreen());
				}
			}
		});
	}
}

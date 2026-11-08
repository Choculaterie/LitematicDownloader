package com.choculaterie;

import com.choculaterie.gui.LitematicDownloaderScreen;
import com.choculaterie.gui.LocalFolderPage;
import com.choculaterie.keybind.LitematicDownloaderKeybindings;
import com.choculaterie.util.LitematicaBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;

public class LitematicDownloaderClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		LitematicDownloaderKeybindings.initialize();
		registerScreenToggleHandler();
		registerLitematicaSwitchButton();
	}

	private static void registerScreenToggleHandler() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (LitematicDownloaderKeybindings.OPEN_MENU_KEY_BINDING.consumeClick()) {
				toggleLitematicDownloaderScreen(client);
			}
		});
	}

	private static void registerLitematicaSwitchButton() {
		if (!LitematicaBridge.isAvailable()) return;
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!LitematicaBridge.isLitematicaScreen(screen)) return;
			LitematicaBridge.addButtonBesideTopRow(screen, "LiD", "Open the Litematic Downloader folder",
					() -> client.gui.setScreen(new LocalFolderPage(new LitematicDownloaderScreen())));
		});
	}

	private static void toggleLitematicDownloaderScreen(Minecraft client) {
		if (client.gui.screen() instanceof LitematicDownloaderScreen) {
			client.gui.setScreen(null);
		} else {
			client.gui.setScreen(new LitematicDownloaderScreen());
		}
	}
}

package com.choculaterie.keybind;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ModKeybindings {
	public static KeyMapping OPEN_MENU_KEY_BINDING;

	public static void initialize() {
		OPEN_MENU_KEY_BINDING = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.litematic-downloader.open_menu",
				GLFW.GLFW_KEY_N,
				KeyMapping.Category.MISC
			)
		);
	}

	private ModKeybindings() {}
}

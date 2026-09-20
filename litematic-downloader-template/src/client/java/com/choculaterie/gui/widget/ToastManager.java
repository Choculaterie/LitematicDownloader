package com.choculaterie.gui.widget;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.vanilib.gui.widget.Toast;
import net.minecraft.client.Minecraft;

public class ToastManager extends com.choculaterie.vanilib.gui.widget.ToastManager {

	public ToastManager(Minecraft client) {
		super(client);
	}

	@Override
	public void showToast(String message, Toast.Type type, boolean hasCopyButton, String copyText) {
		if (!isToastTypeEnabled(type)) {
			return;
		}
		super.showToast(message, type, hasCopyButton, copyText);
	}

	private boolean isToastTypeEnabled(Toast.Type type) {
		DownloadSettings settings = DownloadSettings.getInstance();
		return switch (type) {
			case SUCCESS -> settings.isSuccessToastsEnabled();
			case ERROR -> settings.isErrorToastsEnabled();
			case INFO -> settings.isInfoToastsEnabled();
			case WARNING -> settings.isWarningToastsEnabled();
		};
	}
}

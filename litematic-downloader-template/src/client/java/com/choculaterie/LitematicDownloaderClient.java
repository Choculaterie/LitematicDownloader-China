package com.choculaterie;

import com.choculaterie.gui.LitematicDownloaderScreen;
import com.choculaterie.keybind.ModKeybindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class LitematicDownloaderClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModKeybindings.register();
		registerScreenToggleHandler();
	}

	private static void registerScreenToggleHandler() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ModKeybindings.openMenuKey.wasPressed()) {
				toggleLitematicDownloaderScreen(MinecraftClient.getInstance());
			}
		});
	}

	private static void toggleLitematicDownloaderScreen(MinecraftClient client) {
		if (client.currentScreen instanceof LitematicDownloaderScreen) {
			client.setScreen(null);
		} else {
			client.setScreen(new LitematicDownloaderScreen());
		}
	}
}

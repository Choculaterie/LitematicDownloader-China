package com.choculaterie;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LitematicDownloader implements ModInitializer {
	public static final String MOD_ID = "litematic-downloader";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Litematic Downloader mod initialized");
	}
}


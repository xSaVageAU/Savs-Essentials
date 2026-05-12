package savage.essentials;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import savage.essentials.api.storage.StorageEntrypoint;
import savage.essentials.api.storage.StorageProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SavsEssentials implements ModInitializer {
	public static final String MOD_ID = "savs-essentials";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static StorageProvider storageProvider;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Sav's Essentials...");

		loadStorage();
	}

	private void loadStorage() {
		List<StorageEntrypoint> entrypoints = FabricLoader.getInstance()
				.getEntrypoints("savs-essentials:storage", StorageEntrypoint.class);

		if (entrypoints.isEmpty()) {
			LOGGER.error("No storage provider found! Sav's Essentials cannot function without a storage addon.");
			return;
		}

		// For now, we just take the first one found. 
		// Later we can add a config to choose between multiple if present.
		storageProvider = entrypoints.get(0).createProvider();
		storageProvider.init();

		LOGGER.info("Storage provider initialized: {}", storageProvider.getClass().getSimpleName());
	}

	public static StorageProvider getStorageProvider() {
		return storageProvider;
	}
}
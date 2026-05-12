package savage.essentials;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.essentials.core.EssentialsManager;
import savage.essentials.core.manager.ProfileManager;
import savage.essentials.core.manager.WarpManager;
import savage.essentials.command.HomeCommand;
import savage.essentials.command.WarpCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import savage.essentials.api.data.Profile;

public class SavsEssentials implements ModInitializer {
	public static final String MOD_ID = "savs-essentials";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Savs Essentials Implementation is initializing...");

		// Load and persist configuration
		savage.essentials.config.ConfigManager.load();

		// Initialize Core Engine with the loaded config
		EssentialsManager.getInstance().init(savage.essentials.config.ConfigManager.getConfig());

		// Set server instance for core access
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(EssentialsManager::setMinecraftServer);

		// Register Commands
		registerCommands();

		// Register Lifecycle Events
		registerEvents();

		LOGGER.info("Savs Essentials Implementation initialized.");
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			HomeCommand.register(dispatcher);
			WarpCommand.register(dispatcher);
		});
	}

	private void registerEvents() {
		ProfileManager pm = EssentialsManager.getInstance().getProfileManager();
		
		if (pm == null) return;

		// Load profile on join
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			String name = handler.player.getName().getString();
			pm.load(handler.player.getUUID(), name).thenRun(() -> {
				Profile profile = pm.getProfile(handler.player.getUUID());
				if (profile != null) {
					profile.setLastKnownName(name); // Ensure it's up to date even if loaded from file
				}
				LOGGER.debug("Loaded profile for player {}", name);
			});
		});

		// Unload and save profile on disconnect
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			pm.unload(handler.player.getUUID(), true);
			LOGGER.debug("Unloaded profile for player {}", handler.player.getName().getString());
		});
	}

	public static ProfileManager getProfileManager() {
		return EssentialsManager.getInstance().getProfileManager();
	}

	public static WarpManager getWarpManager() {
		return EssentialsManager.getInstance().getWarpManager();
	}
}
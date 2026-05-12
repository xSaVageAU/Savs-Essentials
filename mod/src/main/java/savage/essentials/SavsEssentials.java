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
import savage.essentials.command.PlayerInfoCommand;
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

		// Profile Manager Shortcut
		ProfileManager pm = EssentialsManager.getInstance().getProfileManager();

		// Load profile on join
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			String currentName = handler.player.getName().getString();
			pm.load(handler.player.getUUID(), currentName).thenRun(() -> {
				Profile profile = pm.getProfile(handler.player.getUUID());
				if (profile != null) {
					String oldName = profile.getLastKnownName();
					
					// Detect name change
					if (oldName != null && !oldName.equalsIgnoreCase(currentName)) {
						profile.addPreviousName(oldName);
						profile.setLastKnownName(currentName);
						LOGGER.info("Player {} (UUID: {}) changed name from {}", currentName, handler.player.getUUID(), oldName);
					}
				}
				LOGGER.debug("Loaded profile for player {}", currentName);
			});
		});

		// Save profile on disconnect
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			pm.save(handler.player.getUUID());
		});

		LOGGER.info("Savs Essentials Implementation initialized.");
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			HomeCommand.register(dispatcher);
			WarpCommand.register(dispatcher);
			PlayerInfoCommand.register(dispatcher);
		});
	}

	public static ProfileManager getProfileManager() {
		return EssentialsManager.getInstance().getProfileManager();
	}

	public static WarpManager getWarpManager() {
		return EssentialsManager.getInstance().getWarpManager();
	}
}
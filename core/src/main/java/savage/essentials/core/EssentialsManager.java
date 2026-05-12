package savage.essentials.core;

import savage.essentials.api.storage.StorageProvider;
import savage.essentials.core.manager.ProfileManager;
import savage.essentials.core.manager.WarpManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main orchestrator for Sav's Essentials.
 * Follows the singleton pattern similar to OpenEconomy's EconomyManager.
 */
public class EssentialsManager {
    private static final EssentialsManager INSTANCE = new EssentialsManager();
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-core");

    private StorageProvider storage;
    private ProfileManager profileManager;
    private WarpManager warpManager;

    private EssentialsManager() {}

    public static EssentialsManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the engine.
     * @param storageType The ID of the storage provider to use (e.g., "json").
     */
    public void init(String storageType) {
        // 1. Discover all registered storage addons
        StorageRegistry.discoverProviders();

        // 2. Initialize the selected storage
        try {
            this.storage = StorageRegistry.create(storageType);
            this.storage.init();
            LOGGER.info("Essentials storage initialized: {}", storage.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize storage provider: " + storageType, e);
            return;
        }

        // 3. Initialize Managers
        this.profileManager = new ProfileManager(storage);
        this.warpManager = new WarpManager(storage);

        // 4. Pre-load data
        this.warpManager.loadAll().thenRun(() -> {
            LOGGER.info("Essentials Engine ready. Loaded global warps.");
        });
    }

    public void shutdown() {
        if (storage != null) {
            storage.shutdown();
        }
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public StorageProvider getStorage() {
        return storage;
    }
}

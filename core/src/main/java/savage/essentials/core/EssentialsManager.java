package savage.essentials.core;

import savage.essentials.api.messaging.EssentialsMessaging;
import savage.essentials.api.storage.StorageProvider;
import savage.essentials.core.messaging.MessagingRegistry;
import savage.essentials.core.messaging.NoOpMessaging;
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

    private EssentialsCoreConfig config;
    private StorageProvider storage;
    private EssentialsMessaging messaging;
    private ProfileManager profileManager;
    private WarpManager warpManager;

    private EssentialsManager() {}

    public static EssentialsManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the engine.
     * @param config The engine configuration provided by the implementation.
     */
    public void init(EssentialsCoreConfig config) {
        this.config = config;
        
        // 1. Discover all registered providers
        StorageRegistry.discoverProviders();
        MessagingRegistry.discoverProviders();

        // 2. Initialize the selected storage
        try {
            this.storage = StorageRegistry.create(config.getStorageType());
            this.storage.init();
            LOGGER.info("Essentials storage initialized: {}", storage.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize storage provider: " + config.getStorageType(), e);
            return;
        }

        // 3. Initialize the selected messaging
        try {
            this.messaging = MessagingRegistry.create(config.getMessagingType());
            LOGGER.info("Essentials messaging initialized: {}", messaging.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize messaging provider: " + config.getMessagingType(), e);
            this.messaging = new NoOpMessaging();
        }

        // 4. Initialize Managers
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

    public EssentialsMessaging getMessaging() {
        return messaging;
    }

    public EssentialsCoreConfig getConfig() {
        return config;
    }

    public StorageProvider getStorage() {
        return storage;
    }
}

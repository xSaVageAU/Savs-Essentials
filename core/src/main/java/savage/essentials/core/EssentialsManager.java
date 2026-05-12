package savage.essentials.core;

import savage.essentials.api.messaging.EssentialsMessaging;
import savage.essentials.api.storage.StorageProvider;
import savage.essentials.core.messaging.MessagingRegistry;
import savage.essentials.core.messaging.NoOpMessaging;
import savage.essentials.core.manager.ProfileManager;
import savage.essentials.core.manager.TeleportManager;
import savage.essentials.core.manager.WarpManager;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main orchestrator for Sav's Essentials.
 * Follows the singleton pattern similar to OpenEconomy's EconomyManager.
 */
public class EssentialsManager {
    private static final EssentialsManager INSTANCE = new EssentialsManager();
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-core");

    private static net.minecraft.server.MinecraftServer minecraftServer;
    private EssentialsCoreConfig config;
    private StorageProvider storage;
    private EssentialsMessaging messaging;
    private ProfileManager profileManager;
    private WarpManager warpManager;
    private TeleportManager teleportManager;

    private EssentialsManager() {
    }

    public static EssentialsManager getInstance() {
        return INSTANCE;
    }

    public static net.minecraft.server.MinecraftServer getMinecraftServer() {
        return minecraftServer;
    }

    public static void setMinecraftServer(net.minecraft.server.MinecraftServer server) {
        minecraftServer = server;
    }

    /**
     * Initializes the engine.
     * 
     * @param config The engine configuration provided by the implementation.
     */
    public void init(EssentialsCoreConfig config) {
        this.config = config;

        // 1. Discover all registered providers
        StorageRegistry.discoverProviders();
        MessagingRegistry.discoverProviders();

        // 2. Initialize the selected storage
        try {
            StorageProvider rawStorage = StorageRegistry.create(config.getStorageType());
            this.storage = new savage.essentials.core.storage.AsyncStorage(rawStorage);
            this.storage.init();
            LOGGER.info("Essentials storage initialized: {}", config.getStorageType());
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
        this.profileManager = new ProfileManager(storage, messaging);
        this.warpManager = new WarpManager(storage, messaging);
        this.teleportManager = new TeleportManager();

        // Initialize synchronization listeners
        this.profileManager.init();
        this.warpManager.init();

        // 5. Pre-load data
        CompletableFuture.allOf(
                this.warpManager.loadAll(),
                this.profileManager.loadAll()).thenRun(() -> {
                    this.warpManager.markReady();
                    this.profileManager.markReady();
                    LOGGER.info("Essentials Engine ready. Loaded {} warps and {} profiles.",
                            warpManager.getWarps().size(), profileManager.getProfileCount());
                });
    }

    public void drain() {
        if (minecraftServer == null) return;
        
        LOGGER.info("Essentials Engine: Draining player data for {} players...", 
                minecraftServer.getPlayerList().getPlayers().size());
        
        for (var player : minecraftServer.getPlayerList().getPlayers()) {
            profileManager.save(player.getUUID());
        }
    }

    public void shutdown() {
        LOGGER.info("Essentials Engine: Shutting down resources...");
        if (messaging != null) {
            messaging.shutdown();
        }
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

    public TeleportManager getTeleportManager() {
        return teleportManager;
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

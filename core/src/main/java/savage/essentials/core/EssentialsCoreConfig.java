package savage.essentials.core;

/**
 * Interface for configuration values required by the essentials engine.
 * Implementations (Mods) provide these values to the core.
 */
public interface EssentialsCoreConfig {
    
    /**
     * @return The ID of the storage provider to use (e.g. "json", "nats").
     */
    String getStorageType();

    /**
     * @return The ID of the messaging provider to use (e.g. "none", "nats").
     */
    String getMessagingType();

    /**
     * @return The unique ID of this server instance, used for cross-server sync.
     */
    String getServerId();

    /**
     * @return The default maximum number of homes a player can have.
     */
    default int getDefaultMaxHomes() {
        return 5;
    }

    /**
     * @return The delay in seconds before a teleport occurs.
     */
    default int getTeleportDelaySeconds() {
        return 0;
    }

    /**
     * @return The Time-To-Live (TTL) in minutes for cached profiles.
     *         A value <= 0 indicates no eviction based on time.
     */
    default int getProfileCacheTtlMinutes() {
        return 0;
    }

    /**
     * @return The maximum number of profiles to keep in memory.
     *         A value <= 0 indicates unbounded size.
     */
    default int getProfileCacheMaxSize() {
        return 0;
    }
}

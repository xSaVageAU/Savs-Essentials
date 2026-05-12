package savage.essentials.api.storage;

/**
 * Entrypoint interface for registering a storage provider.
 * Implement this in your addon and register it in fabric.mod.json.
 */
public interface StorageEntrypoint {
    /**
     * @return The unique ID for this storage type (e.g. "json", "nats-standalone").
     */
    String getId();

    StorageProvider createProvider();
}

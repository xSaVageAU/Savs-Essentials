package savage.essentials.api.storage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for storage providers.
 * Addons should implement this to provide custom storage logic (JSON, SQL, Redis, etc.).
 */
public interface StorageProvider {
    /**
     * Called when the storage provider is initialized.
     */
    void init();

    /**
     * Called when the storage provider is shut down.
     */
    void shutdown();

    // We will add specific methods for homes, warps, etc. as we implement them.
}

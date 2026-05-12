package savage.essentials.core.manager;

import savage.essentials.api.data.Warp;
import savage.essentials.api.messaging.EssentialsMessaging;
import savage.essentials.api.storage.StorageProvider;
import savage.essentials.core.EssentialsManager;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages global warps and their persistence.
 */
public class WarpManager {
    private final StorageProvider storage;
    private final EssentialsMessaging messaging;
    private final Map<String, Warp> warps = new ConcurrentHashMap<>();

    public WarpManager(StorageProvider storage, EssentialsMessaging messaging) {
        this.storage = storage;
        this.messaging = messaging;
    }

    /**
     * Initializes the manager and subscribes to remote updates.
     */
    public void init() {
        messaging.subscribeWarp(update -> {
            // Ignore updates from ourselves
            if (update.sourceServerId().equals(EssentialsManager.getInstance().getConfig().getServerId())) {
                return;
            }

            if (update.deleted()) {
                warps.remove(update.warp().name());
            } else {
                warps.put(update.warp().name().toLowerCase(), update.warp());
            }
        });
    }

    /**
     * Loads all warps from storage into the cache.
     */
    public CompletableFuture<Void> loadAll() {
        return storage.loadWarps().thenAccept(loaded -> {
            warps.clear();
            warps.putAll(loaded);
        });
    }

    /**
     * Gets a warp by name.
     */
    public Warp getWarp(String name) {
        return warps.get(name.toLowerCase());
    }

    /**
     * Gets all registered warps.
     */
    public Collection<Warp> getWarps() {
        return Collections.unmodifiableCollection(warps.values());
    }

    /**
     * Creates or updates a warp and broadcasts the change.
     */
    public CompletableFuture<Void> setWarp(Warp warp) {
        warps.put(warp.name().toLowerCase(), warp);
        return storage.saveWarp(warp).thenRun(() -> {
            messaging.publishWarp(EssentialsManager.getInstance().getConfig().getServerId(), warp);
        });
    }

    /**
     * Deletes a warp and broadcasts the change.
     */
    public CompletableFuture<Void> deleteWarp(String name) {
        warps.remove(name.toLowerCase());
        return storage.deleteWarp(name).thenRun(() -> {
            messaging.publishWarpDelete(EssentialsManager.getInstance().getConfig().getServerId(), name);
        });
    }
}

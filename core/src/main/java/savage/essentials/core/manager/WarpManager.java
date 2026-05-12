package savage.essentials.core.manager;

import savage.essentials.api.data.Warp;
import savage.essentials.api.storage.StorageProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the global warp points.
 */
public class WarpManager {
    private final StorageProvider storage;
    private final Map<String, Warp> warpCache = new ConcurrentHashMap<>();

    public WarpManager(StorageProvider storage) {
        this.storage = storage;
    }

    /**
     * Initial load of all warps from storage.
     */
    public CompletableFuture<Void> loadAll() {
        return storage.loadWarps().thenAccept(warps -> {
            warpCache.clear();
            warpCache.putAll(warps);
        });
    }

    public Warp getWarp(String name) {
        return warpCache.get(name.toLowerCase());
    }

    public Collection<Warp> getWarps() {
        return Collections.unmodifiableCollection(warpCache.values());
    }

    /**
     * Adds or updates a warp and persists it.
     */
    public CompletableFuture<Void> setWarp(Warp warp) {
        warpCache.put(warp.name().toLowerCase(), warp);
        return storage.saveWarp(warp);
    }

    /**
     * Deletes a warp from cache and storage.
     */
    public CompletableFuture<Void> deleteWarp(String name) {
        warpCache.remove(name.toLowerCase());
        return storage.deleteWarp(name.toLowerCase());
    }

    /**
     * Updates the local cache without persisting.
     * Useful for cross-server synchronization.
     */
    public void applySync(Warp warp) {
        warpCache.put(warp.name().toLowerCase(), warp);
    }

    /**
     * Removes from local cache without persisting.
     */
    public void applySyncRemoval(String name) {
        warpCache.remove(name.toLowerCase());
    }
}

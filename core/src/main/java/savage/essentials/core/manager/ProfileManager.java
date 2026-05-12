package savage.essentials.core.manager;

import savage.essentials.api.data.Profile;
import savage.essentials.api.storage.StorageProvider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle and caching of player profiles.
 */
public class ProfileManager {
    private final StorageProvider storage;
    private final Map<UUID, Profile> profileCache = new ConcurrentHashMap<>();

    public ProfileManager(StorageProvider storage) {
        this.storage = storage;
    }

    /**
     * Gets a profile from the local cache.
     * @param uuid The player UUID.
     * @return The profile, or null if not loaded.
     */
    public Profile getProfile(UUID uuid) {
        return profileCache.get(uuid);
    }

    /**
     * Loads a profile from storage into the local cache.
     * @param uuid The player UUID.
     * @return A future containing the loaded profile.
     */
    public CompletableFuture<Profile> load(UUID uuid) {
        return storage.loadProfile(uuid).thenApply(profile -> {
            profileCache.put(uuid, profile);
            return profile;
        });
    }

    /**
     * Saves a profile from the cache to storage.
     * @param uuid The player UUID.
     * @return A future that completes when the save is done.
     */
    public CompletableFuture<Void> save(UUID uuid) {
        Profile profile = profileCache.get(uuid);
        if (profile == null) {
            return CompletableFuture.completedFuture(null);
        }
        return storage.saveProfile(profile);
    }

    /**
     * Unloads a profile from the cache, optionally saving it first.
     * @param uuid The player UUID.
     * @param save Whether to save before unloading.
     */
    public void unload(UUID uuid, boolean save) {
        if (save) {
            save(uuid).thenRun(() -> profileCache.remove(uuid));
        } else {
            profileCache.remove(uuid);
        }
    }

    /**
     * Updates the local cache with an externally provided profile.
     * This is useful for cross-server synchronization (e.g., NATS)
     * as it does NOT trigger a save back to the storage provider.
     *
     * @param profile The new profile data to apply to the cache.
     */
    public void applySync(Profile profile) {
        profileCache.put(profile.getUuid(), profile);
    }
}

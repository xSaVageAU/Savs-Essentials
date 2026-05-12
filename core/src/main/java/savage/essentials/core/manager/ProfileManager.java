package savage.essentials.core.manager;

import savage.essentials.api.data.Profile;
import savage.essentials.api.messaging.EssentialsMessaging;
import savage.essentials.api.storage.StorageProvider;
import savage.essentials.core.EssentialsManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle and caching of player profiles.
 */
public class ProfileManager {
    private final StorageProvider storage;
    private final EssentialsMessaging messaging;
    private final Map<UUID, Profile> profileCache = new ConcurrentHashMap<>();

    public ProfileManager(StorageProvider storage, EssentialsMessaging messaging) {
        this.storage = storage;
        this.messaging = messaging;
    }

    /**
     * Initializes the manager and subscribes to remote updates.
     */
    public void init() {
        messaging.subscribeProfile(update -> {
            // Ignore updates from ourselves
            if (update.sourceServerId().equals(EssentialsManager.getInstance().getConfig().getServerId())) {
                return;
            }
            
            // Update local cache with remote data
            applySync(update.playerUuid(), update.profile());
        });
    }

    /**
     * Loads all profiles from storage into the cache.
     */
    public CompletableFuture<Void> loadAll() {
        return storage.loadAllProfiles().thenAccept(profiles -> {
            profileCache.putAll(profiles);
        });
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
     * Gets a profile from the cache by name (case-insensitive).
     * @param name The player name.
     * @return The profile, or null if not found.
     */
    public Profile getProfileByName(String name) {
        for (Profile profile : profileCache.values()) {
            if (profile.getLastKnownName().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        return null;
    }

    public int getProfileCount() {
        return profileCache.size();
    }

    /**
     * Loads a profile from storage into the local cache.
     * @param uuid The player UUID.
     * @param initialName The name to use if the profile is new.
     * @return A future containing the loaded profile.
     */
    public CompletableFuture<Profile> load(UUID uuid, String initialName) {
        return storage.loadProfile(uuid).thenApply(profile -> {
            Profile result = (profile != null) ? profile : new Profile(initialName);
            profileCache.put(uuid, result);
            
            // Broadcast immediately so other servers can see this player/profile
            messaging.publishProfile(EssentialsManager.getInstance().getConfig().getServerId(), uuid, result);
            
            return result;
        });
    }

    /**
     * Saves a profile from the cache to storage and broadcasts the update.
     * @param uuid The player UUID.
     * @return A future that completes when the save is done.
     */
    public CompletableFuture<Void> save(UUID uuid) {
        Profile profile = profileCache.get(uuid);
        if (profile == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        return storage.saveProfile(uuid, profile).thenRun(() -> {
            messaging.publishProfile(EssentialsManager.getInstance().getConfig().getServerId(), uuid, profile);
        });
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
     *
     * @param uuid The player UUID.
     * @param profile The new profile data.
     */
    public void applySync(UUID uuid, Profile profile) {
        profileCache.put(uuid, profile);
    }
}

package savage.essentials.core.manager;

import savage.essentials.api.data.Profile;
import savage.essentials.api.messaging.EssentialsMessaging;
import savage.essentials.api.storage.StorageProvider;
import savage.essentials.core.EssentialsManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Manages the lifecycle and caching of player profiles.
 */
public class ProfileManager {
    private final StorageProvider storage;
    private final EssentialsMessaging messaging;
    private final AsyncCache<UUID, Profile> profileCache;
    private final java.util.Map<String, UUID> nameToUuid = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Queue<savage.essentials.api.messaging.EssentialsMessaging.ProfileUpdate> warmupQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile boolean ready = false;

    public ProfileManager(StorageProvider storage, EssentialsMessaging messaging) {
        this.storage = storage;
        this.messaging = messaging;
        this.profileCache = Caffeine.newBuilder().buildAsync();
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

            if (!ready) {
                warmupQueue.add(update);
            } else {
                applySync(update.playerUuid(), update.profile());
            }
        });
    }

    public void markReady() {
        this.ready = true;
        while (!warmupQueue.isEmpty()) {
            var update = warmupQueue.poll();
            if (update != null) {
                applySync(update.playerUuid(), update.profile());
            }
        }
    }

    /**
     * Loads all profiles from storage into the cache.
     */
    public CompletableFuture<Void> loadAll() {
        return storage.loadAllProfiles().thenAccept(profiles -> {
            profiles.forEach((uuid, profile) -> {
                profileCache.synchronous().put(uuid, profile);
                nameToUuid.put(profile.getLastKnownName().toLowerCase(), uuid);
            });
        });
    }

    /**
     * Gets a profile from the local cache.
     * 
     * @param uuid The player UUID.
     * @return The profile, or null if not loaded.
     */
    public Profile getProfile(UUID uuid) {
        return profileCache.synchronous().getIfPresent(uuid);
    }

    /**
     * Gets a profile from the cache by name (case-insensitive).
     * 
     * @param name The player name.
     * @return The profile, or null if not found.
     */
    public Profile getProfileByName(String name) {
        UUID uuid = nameToUuid.get(name.toLowerCase());
        if (uuid == null) return null;
        
        Profile profile = getProfile(uuid);
        if (profile != null && profile.getLastKnownName().equalsIgnoreCase(name)) {
            return profile;
        }
        
        // Fallback: If index is stale or null, do a quick scan (rare)
        for (var entry : profileCache.synchronous().asMap().entrySet()) {
            if (entry.getValue().getLastKnownName().equalsIgnoreCase(name)) {
                nameToUuid.put(name.toLowerCase(), entry.getKey()); // Fix index
                return entry.getValue();
            }
        }
        return null;
    }

    public int getProfileCount() {
        return (int) profileCache.synchronous().estimatedSize();
    }

    /**
     * Loads a profile from storage into the local cache.
     * 
     * @param uuid        The player UUID.
     * @param initialName The name to use if the profile is new.
     * @return A future containing the loaded profile.
     */
    public CompletableFuture<Profile> load(UUID uuid, String initialName) {
        return profileCache
                .get(uuid,
                        (k, executor) -> storage.loadProfile(uuid)
                                .thenApply(profile -> (profile != null) ? profile : new Profile(initialName)))
                .thenApply(profile -> {
                    // Broadcast immediately so other servers can see this player/profile
                    messaging.publishProfile(EssentialsManager.getInstance().getConfig().getServerId(), uuid, profile);
                    nameToUuid.put(profile.getLastKnownName().toLowerCase(), uuid);
                    return profile;
                });
    }

    /**
     * Saves a profile from the cache to storage and broadcasts the update.
     * 
     * @param uuid The player UUID.
     * @return A future that completes with true if successful.
     */
    public CompletableFuture<Boolean> save(UUID uuid) {
        Profile profile = profileCache.synchronous().getIfPresent(uuid);
        if (profile == null) {
            return CompletableFuture.completedFuture(false);
        }

        return storage.saveProfile(uuid, profile).thenApply(success -> {
            if (success) {
                messaging.publishProfile(EssentialsManager.getInstance().getConfig().getServerId(), uuid, profile);
            }
            return success;
        });
    }

    /**
     * Unloads a profile from the cache, optionally saving it first.
     * 
     * @param uuid The player UUID.
     * @param save Whether to save before unloading.
     */
    public void unload(UUID uuid, boolean save) {
        Profile profile = profileCache.synchronous().getIfPresent(uuid);
        if (profile != null) {
            nameToUuid.remove(profile.getLastKnownName().toLowerCase(), uuid);
        }
        
        if (save) {
            save(uuid).thenRun(() -> profileCache.synchronous().invalidate(uuid));
        } else {
            profileCache.synchronous().invalidate(uuid);
        }
    }

    /**
     * Updates the local cache with an externally provided profile.
     *
     * @param uuid     The player UUID.
     * @param incoming The new profile data.
     */
    public void applySync(UUID uuid, Profile incoming) {
        Profile existing = profileCache.synchronous().getIfPresent(uuid);
        if (existing != null) {
            if (existing.getRevision() >= incoming.getRevision()) {
                return; // Ignore older or duplicate updates
            }
            nameToUuid.remove(existing.getLastKnownName().toLowerCase(), uuid);
        }
        
        nameToUuid.put(incoming.getLastKnownName().toLowerCase(), uuid);
        profileCache.synchronous().put(uuid, incoming);
    }
}

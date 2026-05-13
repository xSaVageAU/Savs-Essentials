package savage.essentials.api.storage;

import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for storage providers.
 * Addons should implement this to provide custom storage logic (JSON, SQL, Redis, etc.).
 */
public interface StorageProvider {
    /**
     * @return true if this provider requires a unique serverId to function correctly.
     */
    default boolean requiresServerId() {
        return false;
    }

    /**
     * Called when the storage provider is initialized.
     */
    void init();

    /**
     * Called when the storage provider is shut down.
     */
    void shutdown();


    /**
     * Loads a player profile.
     * @param uuid The UUID of the player.
     * @return A future containing the profile, or null if none exists.
     */
    CompletableFuture<Profile> loadProfile(UUID uuid);

    /**
     * Saves a player profile.
     * @param uuid The UUID of the player.
     * @param profile The profile to save.
     * @return A future that completes with true if successful, false if rejected (e.g. version collision).
     */
    CompletableFuture<Boolean> saveProfile(UUID uuid, Profile profile);

    /**
     * Loads all global warps.
     * @return A future containing a map of warp names to Warp objects.
     */
    CompletableFuture<Map<String, Warp>> loadWarps();

    /**
     * Saves a global warp.
     * @param warp The warp to save.
     * @return A future that completes with true if successful, false if rejected (e.g. version collision).
     */
    CompletableFuture<Boolean> saveWarp(Warp warp);

    /**
     * Deletes a global warp.
     * @param name The name of the warp to delete.
     * @return A future that completes with true if successful, false if rejected.
     */
    CompletableFuture<Boolean> deleteWarp(String name);
    /**
     * Looks up a player's UUID by their name.
     * @param name The name to look up.
     * @return A future containing the UUID, or null if not found.
     */
    CompletableFuture<UUID> lookupUuidByName(String name);

    /**
     * Saves a name to UUID mapping.
     * @param name The player's name.
     * @param uuid The player's UUID.
     * @return A future that completes when the save is finished.
     */
    CompletableFuture<Void> saveNameLookup(String name, UUID uuid);
}

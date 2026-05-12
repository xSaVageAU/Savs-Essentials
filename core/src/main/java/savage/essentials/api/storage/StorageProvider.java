package savage.essentials.api.storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;

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
     * @return A future that completes when the save is done.
     */
    CompletableFuture<Void> saveProfile(UUID uuid, Profile profile);

    /**
     * Loads all global warps.
     * @return A future containing a map of warp names to Warp objects.
     */
    CompletableFuture<Map<String, Warp>> loadWarps();

    /**
     * Saves a global warp.
     * @param warp The warp to save.
     * @return A future that completes when the save is done.
     */
    CompletableFuture<Void> saveWarp(Warp warp);

    /**
     * Deletes a global warp.
     * @param name The name of the warp to delete.
     * @return A future that completes when the deletion is done.
     */
    CompletableFuture<Void> deleteWarp(String name);
}

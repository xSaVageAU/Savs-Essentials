package savage.essentials.storage.json;

import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;
import savage.essentials.api.storage.StorageProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class JsonStorageProvider implements StorageProvider {
    @Override
    public void init() {
        // Initialize JSON storage logic (create folders, etc.)
    }

    @Override
    public void shutdown() {
        // Save any pending data
    }

    @Override
    public CompletableFuture<Profile> loadProfile(UUID uuid) {
        return CompletableFuture.completedFuture(new Profile(uuid));
    }

    @Override
    public CompletableFuture<Void> saveProfile(Profile profile) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, Warp>> loadWarps() {
        return CompletableFuture.completedFuture(new HashMap<>());
    }

    @Override
    public CompletableFuture<Void> saveWarp(Warp warp) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteWarp(String name) {
        return CompletableFuture.completedFuture(null);
    }
}

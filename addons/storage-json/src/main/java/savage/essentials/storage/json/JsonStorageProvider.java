package savage.essentials.storage.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;
import savage.essentials.api.storage.StorageProvider;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class JsonStorageProvider implements StorageProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Path baseDir;
    private final Path profileDir;
    private final Path warpDir;
    private final Path usermapPath;

    public JsonStorageProvider() {
        this.baseDir = FabricLoader.getInstance().getConfigDir().resolve("savs-essentials");
        this.profileDir = baseDir.resolve("profiles");
        this.warpDir = baseDir.resolve("warps");
        this.usermapPath = baseDir.resolve("usermap.json");
    }

    @Override
    public boolean requiresServerId() {
        return false;
    }

    @Override
    public void init() {
        try {
            Files.createDirectories(profileDir);
            Files.createDirectories(warpDir);
            LOGGER.info("JSON Storage initialized at {}", baseDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create storage directories", e);
        }
    }

    @Override
    public void shutdown() {
        // No-op for JSON
    }


    private Path getProfilePath(UUID uuid) {
        String id = uuid.toString();
        // Hashed subdirectories for efficiency
        return profileDir.resolve(id.substring(0, 2)).resolve(id + ".json");
    }

    @Override
    public CompletableFuture<Profile> loadProfile(UUID uuid) {
        Path path = getProfilePath(uuid);
        if (!Files.exists(path)) {
            return CompletableFuture.completedFuture(null);
        }

        try (var reader = Files.newBufferedReader(path)) {
            return CompletableFuture.completedFuture(GSON.fromJson(reader, Profile.class));
        } catch (IOException e) {
            LOGGER.error("Failed to load profile for {}", uuid, e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Boolean> saveProfile(UUID uuid, Profile profile) {
        Path path = getProfilePath(uuid);
        saveAtomic(path, profile);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Map<String, Warp>> loadWarps() {
        Map<String, Warp> warps = new HashMap<>();
        if (!Files.exists(warpDir)) return CompletableFuture.completedFuture(warps);

        try (Stream<Path> stream = Files.list(warpDir)) {
            stream.filter(f -> f.toString().endsWith(".json")).forEach(path -> {
                try (var reader = Files.newBufferedReader(path)) {
                    Warp warp = GSON.fromJson(reader, Warp.class);
                    if (warp != null) {
                        warps.put(warp.name().toLowerCase(), warp);
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to load warp file {}", path, e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to list warps directory", e);
        }
        return CompletableFuture.completedFuture(warps);
    }

    @Override
    public CompletableFuture<Boolean> saveWarp(Warp warp) {
        Path path = warpDir.resolve(warp.name().toLowerCase() + ".json");
        saveAtomic(path, warp);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> deleteWarp(String name) {
        Path path = warpDir.resolve(name.toLowerCase() + ".json");
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.error("Failed to delete warp {}", name, e);
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<UUID> lookupUuidByName(String name) {
        if (!Files.exists(usermapPath)) return CompletableFuture.completedFuture(null);
        try (var reader = Files.newBufferedReader(usermapPath)) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> map = GSON.fromJson(reader, type);
            if (map != null) {
                String uuidStr = map.get(name.toLowerCase());
                if (uuidStr != null) return CompletableFuture.completedFuture(UUID.fromString(uuidStr));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to lookup UUID from usermap", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> saveNameLookup(String name, UUID uuid) {
        Map<String, String> map = new HashMap<>();
        if (Files.exists(usermapPath)) {
            try (var reader = Files.newBufferedReader(usermapPath)) {
                java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> existing = GSON.fromJson(reader, type);
                if (existing != null) map.putAll(existing);
            } catch (Exception e) {
                LOGGER.error("Failed to read usermap for update", e);
            }
        }
        map.put(name.toLowerCase(), uuid.toString());
        saveAtomic(usermapPath, map);
        return CompletableFuture.completedFuture(null);
    }

    private void saveAtomic(Path path, Object data) {
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            
            try (var writer = Files.newBufferedWriter(tempPath)) {
                GSON.toJson(data, writer);
            }

            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save data atomically to {}", path, e);
            try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
        }
    }
}

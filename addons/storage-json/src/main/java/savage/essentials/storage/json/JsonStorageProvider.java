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

    public JsonStorageProvider() {
        this.baseDir = FabricLoader.getInstance().getConfigDir().resolve("savs-essentials");
        this.profileDir = baseDir.resolve("profiles");
        this.warpDir = baseDir.resolve("warps");
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
        return CompletableFuture.supplyAsync(() -> {
            Path path = getProfilePath(uuid);
            if (!Files.exists(path)) {
                return null;
            }

            try (var reader = Files.newBufferedReader(path)) {
                return GSON.fromJson(reader, Profile.class);
            } catch (IOException e) {
                LOGGER.error("Failed to load profile for {}", uuid, e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveProfile(UUID uuid, Profile profile) {
        return CompletableFuture.runAsync(() -> {
            Path path = getProfilePath(uuid);
            saveAtomic(path, profile);
        });
    }

    @Override
    public CompletableFuture<Map<String, Warp>> loadWarps() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Warp> warps = new HashMap<>();
            if (!Files.exists(warpDir)) return warps;

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
            return warps;
        });
    }

    @Override
    public CompletableFuture<Void> saveWarp(Warp warp) {
        return CompletableFuture.runAsync(() -> {
            Path path = warpDir.resolve(warp.name().toLowerCase() + ".json");
            saveAtomic(path, warp);
        });
    }

    @Override
    public CompletableFuture<Void> deleteWarp(String name) {
        return CompletableFuture.runAsync(() -> {
            Path path = warpDir.resolve(name.toLowerCase() + ".json");
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                LOGGER.error("Failed to delete warp {}", name, e);
            }
        });
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

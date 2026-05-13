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

    @Override
    public CompletableFuture<Map<UUID, Profile>> loadAllProfiles() {
        Map<UUID, Profile> profiles = new HashMap<>();
        if (!Files.exists(profileDir)) return CompletableFuture.completedFuture(profiles);

        try (Stream<Path> stream = Files.walk(profileDir, 2)) {
            stream.filter(f -> f.toString().endsWith(".json")).forEach(file -> {
                try (var reader = Files.newBufferedReader(file)) {
                    Profile profile = GSON.fromJson(reader, Profile.class);
                    if (profile != null) {
                        String fileName = file.getFileName().toString();
                        UUID uuid = UUID.fromString(fileName.substring(0, fileName.length() - 5));
                        profiles.put(uuid, profile);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load profile file {}", file, e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to walk profiles directory", e);
        }
        return CompletableFuture.completedFuture(profiles);
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

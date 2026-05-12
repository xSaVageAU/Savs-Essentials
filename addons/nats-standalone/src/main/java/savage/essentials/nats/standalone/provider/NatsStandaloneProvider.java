package savage.essentials.nats.standalone.provider;

import com.github.luben.zstd.Zstd;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.nats.client.Connection;
import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;
import savage.essentials.api.messaging.EssentialsMessaging;
import savage.essentials.api.storage.StorageProvider;
import savage.essentials.nats.standalone.NatsConfig;
import savage.essentials.nats.standalone.NatsConnection;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class NatsStandaloneProvider implements StorageProvider, EssentialsMessaging {
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-nats-standalone");
    private static final Gson GSON = new GsonBuilder().create();
    private static NatsStandaloneProvider instance;

    public static synchronized NatsStandaloneProvider getInstance() {
        if (instance == null) {
            instance = new NatsStandaloneProvider();
        }
        return instance;
    }

    private KeyValue kv;
    private Consumer<ProfileUpdate> profileListener;
    private Consumer<WarpUpdate> warpListener;

    @Override
    public void init() {
        Connection conn = NatsConnection.get();
        NatsConfig config = NatsConfig.get();

        try {
            try {
                conn.keyValueManagement().create(
                        KeyValueConfiguration.builder()
                                .name(config.kvBucket)
                                .build()
                );
            } catch (Exception ignored) {
                // Bucket might already exist
            }

            this.kv = conn.keyValue(config.kvBucket);
            startWatcher();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize NATS Standalone storage", e);
        }
    }

    private void startWatcher() {
        try {
            kv.watchAll(new KeyValueWatcher() {
                @Override
                public void watch(KeyValueEntry entry) {
                    if (entry.getValue() == null) {
                        // Deletion
                        if (entry.getKey().startsWith("warps.") && warpListener != null) {
                            String name = entry.getKey().substring("warps.".length());
                            // We don't have sourceServerId for pure KV deletes without a tombstone payload, 
                            // so we'll just use a zero UUID as source to represent the backend deletion.
                            warpListener.accept(new WarpUpdate(new UUID(0, 0), name, null, true));
                        }
                        return;
                    }

                    try {
                        byte[] decompressed = decompress(entry.getValue());
                        if (entry.getKey().startsWith("profiles.") && profileListener != null) {
                            ProfileWire wire = GSON.fromJson(new String(decompressed, StandardCharsets.UTF_8), ProfileWire.class);
                            profileListener.accept(new ProfileUpdate(
                                    UUID.fromString(wire.serverId),
                                    UUID.fromString(wire.playerUuid),
                                    wire.profile
                            ));
                        } else if (entry.getKey().startsWith("warps.") && warpListener != null) {
                            WarpWire wire = GSON.fromJson(new String(decompressed, StandardCharsets.UTF_8), WarpWire.class);
                            warpListener.accept(new WarpUpdate(
                                    UUID.fromString(wire.serverId),
                                    wire.name,
                                    wire.warp,
                                    wire.deleted
                            ));
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to parse KV update for {}", entry.getKey(), e);
                    }
                }

                @Override
                public void endOfData() {}
            });
        } catch (Exception e) {
            LOGGER.error("Failed to start NATS KV watcher: {}", e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Map<UUID, Profile>> loadAllProfiles() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Profile> profiles = new HashMap<>();
            CountDownLatch latch = new CountDownLatch(1);
            try {
                var sub = kv.watchAll(new KeyValueWatcher() {
                    @Override
                    public void watch(KeyValueEntry entry) {
                        if (entry.getValue() == null || !entry.getKey().startsWith("profiles.")) return;
                        try {
                            byte[] decompressed = decompress(entry.getValue());
                            ProfileWire wire = GSON.fromJson(new String(decompressed, StandardCharsets.UTF_8), ProfileWire.class);
                            profiles.put(UUID.fromString(wire.playerUuid), wire.profile);
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void endOfData() {
                        latch.countDown();
                    }
                });

                if (!latch.await(30, TimeUnit.SECONDS)) {
                    LOGGER.warn("NATS loadAllProfiles timed out waiting for initial data");
                }
                sub.close();
            } catch (Exception e) {
                LOGGER.error("Failed to stream all profiles from NATS: {}", e.getMessage());
            }
            return profiles;
        });
    }

    @Override
    public CompletableFuture<Profile> loadProfile(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                KeyValueEntry entry = kv.get("profiles." + uuid.toString());
                if (entry == null || entry.getValue() == null) return null;
                byte[] decompressed = decompress(entry.getValue());
                ProfileWire wire = GSON.fromJson(new String(decompressed, StandardCharsets.UTF_8), ProfileWire.class);
                return wire.profile;
            } catch (Exception e) {
                LOGGER.error("Failed to load profile {}", uuid, e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> saveProfile(UUID uuid, Profile profile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String key = "profiles." + uuid.toString();
                KeyValueEntry existing = kv.get(key);
                if (existing != null && existing.getValue() != null) {
                    byte[] decompressed = decompress(existing.getValue());
                    ProfileWire existingWire = GSON.fromJson(new String(decompressed, StandardCharsets.UTF_8), ProfileWire.class);
                    if (existingWire.profile.getRevision() >= profile.getRevision()) {
                        LOGGER.debug("Skipped saving profile {} to NATS KV due to version collision", uuid);
                        return false; // Don't overwrite newer data
                    }
                }

                ProfileWire wire = new ProfileWire(
                        savage.essentials.core.EssentialsManager.getInstance().getConfig().getServerId().toString(), 
                        uuid.toString(), 
                        profile
                );
                byte[] data = compress(GSON.toJson(wire).getBytes(StandardCharsets.UTF_8));
                kv.put(key, data);
            } catch (Exception e) {
                LOGGER.error("Failed to save profile {} to KV", uuid, e);
                return false;
            }
            return true;
        });
    }

    @Override
    public CompletableFuture<Map<String, Warp>> loadWarps() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Warp> warps = new HashMap<>();
            CountDownLatch latch = new CountDownLatch(1);
            try {
                var sub = kv.watchAll(new KeyValueWatcher() {
                    @Override
                    public void watch(KeyValueEntry entry) {
                        if (entry.getValue() == null || !entry.getKey().startsWith("warps.")) return;
                        try {
                            byte[] decompressed = decompress(entry.getValue());
                            WarpWire wire = GSON.fromJson(new String(decompressed, StandardCharsets.UTF_8), WarpWire.class);
                            if (!wire.deleted && wire.warp != null) {
                                warps.put(wire.name.toLowerCase(), wire.warp);
                            }
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void endOfData() {
                        latch.countDown();
                    }
                });

                if (!latch.await(30, TimeUnit.SECONDS)) {
                    LOGGER.warn("NATS loadWarps timed out waiting for initial data");
                }
                sub.close();
            } catch (Exception e) {
                LOGGER.error("Failed to stream all warps from NATS: {}", e.getMessage());
            }
            return warps;
        });
    }

    @Override
    public CompletableFuture<Boolean> saveWarp(Warp warp) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String key = "warps." + warp.name().toLowerCase();
                KeyValueEntry existing = kv.get(key);
                if (existing != null && existing.getValue() != null) {
                    byte[] decompressed = decompress(existing.getValue());
                    WarpWire existingWire = GSON.fromJson(new String(decompressed, StandardCharsets.UTF_8), WarpWire.class);
                    if (existingWire.warp != null && existingWire.warp.revision() >= warp.revision()) {
                        LOGGER.debug("Skipped saving warp {} to NATS KV due to version collision", warp.name());
                        return false; // Don't overwrite newer data
                    }
                }

                WarpWire wire = new WarpWire(
                        savage.essentials.core.EssentialsManager.getInstance().getConfig().getServerId().toString(), 
                        warp.name(), 
                        warp, 
                        false
                );
                byte[] data = compress(GSON.toJson(wire).getBytes(StandardCharsets.UTF_8));
                kv.put(key, data);
            } catch (Exception e) {
                LOGGER.error("Failed to save warp {} to KV", warp.name(), e);
                return false;
            }
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteWarp(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                kv.delete("warps." + name.toLowerCase());
            } catch (Exception e) {
                LOGGER.error("Failed to delete warp {} from KV", name, e);
                return false;
            }
            return true;
        });
    }

    @Override
    public void publishProfile(UUID sourceServerId, UUID playerUuid, Profile profile) {
        try {
            ProfileWire wire = new ProfileWire(sourceServerId.toString(), playerUuid.toString(), profile);
            byte[] data = compress(GSON.toJson(wire).getBytes(StandardCharsets.UTF_8));
            kv.put("profiles." + playerUuid, data);
        } catch (Exception e) {
            LOGGER.error("Failed to publish profile {}", playerUuid, e);
        }
    }

    @Override
    public void publishWarp(UUID sourceServerId, Warp warp) {
        try {
            WarpWire wire = new WarpWire(sourceServerId.toString(), warp.name(), warp, false);
            byte[] data = compress(GSON.toJson(wire).getBytes(StandardCharsets.UTF_8));
            kv.put("warps." + warp.name().toLowerCase(), data);
        } catch (Exception e) {
            LOGGER.error("Failed to publish warp {}", warp.name(), e);
        }
    }

    @Override
    public void publishWarpDelete(UUID sourceServerId, String warpName) {
        try {
            WarpWire wire = new WarpWire(sourceServerId.toString(), warpName, null, true);
            byte[] data = compress(GSON.toJson(wire).getBytes(StandardCharsets.UTF_8));
            kv.put("warps." + warpName.toLowerCase(), data);
            kv.delete("warps." + warpName.toLowerCase());
        } catch (Exception e) {
            LOGGER.error("Failed to publish warp delete {}", warpName, e);
        }
    }

    @Override
    public void subscribeProfile(Consumer<ProfileUpdate> listener) {
        this.profileListener = listener;
    }

    @Override
    public void subscribeWarp(Consumer<WarpUpdate> listener) {
        this.warpListener = listener;
    }

    @Override
    public void shutdown() {
        NatsConnection.close();
    }

    private byte[] compress(byte[] data) {
        return Zstd.compress(data, 1);
    }

    private byte[] decompress(byte[] compressedData) {
        long size = Zstd.getFrameContentSize(compressedData);
        return Zstd.decompress(compressedData, (int) size);
    }

    private record ProfileWire(String serverId, String playerUuid, Profile profile) {}
    private record WarpWire(String serverId, String name, Warp warp, boolean deleted) {}
}

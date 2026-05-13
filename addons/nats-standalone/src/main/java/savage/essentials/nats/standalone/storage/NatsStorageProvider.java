package savage.essentials.nats.standalone.storage;

import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;
import savage.essentials.api.storage.StorageProvider;
import savage.essentials.nats.standalone.provider.NatsKvUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NatsStorageProvider implements StorageProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-nats-standalone");
    private KeyValue kv;

    @Override
    public boolean requiresServerId() {
        return true;
    }

    @Override
    public void init() {
        // We initialize lazily to prevent blocking the main thread before NATS connects
    }

    private KeyValue getKv() {
        if (kv == null) kv = NatsKvUtil.getKv();
        return kv;
    }


    @Override
    public CompletableFuture<Profile> loadProfile(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            NatsKvUtil.ProfileWire wire = NatsKvUtil.readFromKv(getKv(), "profiles." + uuid.toString(), NatsKvUtil.ProfileWire.class);
            return wire != null ? wire.profile() : null;
        });
    }

    @Override
    public CompletableFuture<Boolean> saveProfile(UUID uuid, Profile profile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String key = "profiles." + uuid.toString();
                NatsKvUtil.ProfileWire existingWire = NatsKvUtil.readFromKv(getKv(), key, NatsKvUtil.ProfileWire.class);
                if (existingWire != null && existingWire.profile().getRevision() >= profile.getRevision()) {
                    LOGGER.debug("Skipped saving profile {} to NATS KV due to version collision", uuid);
                    return false;
                }

                NatsKvUtil.ProfileWire wire = new NatsKvUtil.ProfileWire(savage.essentials.core.EssentialsManager.getInstance().getConfig().getServerId(), uuid.toString(), profile);
                NatsKvUtil.writeToKv(getKv(), key, wire);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to save profile {} to KV", uuid, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Warp>> loadWarps() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Warp> warps = new HashMap<>();
            CountDownLatch latch = new CountDownLatch(1);
            try {
                var sub = getKv().watchAll(new KeyValueWatcher() {
                    @Override
                    public void watch(KeyValueEntry entry) {
                        if (entry.getValue() == null || !entry.getKey().startsWith("warps.")) return;
                        try {
                            NatsKvUtil.WarpWire wire = NatsKvUtil.parseWire(entry.getValue(), NatsKvUtil.WarpWire.class);
                            if (wire != null && !wire.deleted() && wire.warp() != null) {
                                warps.put(wire.name().toLowerCase(), wire.warp());
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
                NatsKvUtil.WarpWire existingWire = NatsKvUtil.readFromKv(getKv(), key, NatsKvUtil.WarpWire.class);
                if (existingWire != null && existingWire.warp() != null && existingWire.warp().revision() >= warp.revision()) {
                    LOGGER.debug("Skipped saving warp {} to NATS KV due to version collision", warp.name());
                    return false;
                }

                NatsKvUtil.WarpWire wire = new NatsKvUtil.WarpWire(savage.essentials.core.EssentialsManager.getInstance().getConfig().getServerId(), warp.name(), warp, false);
                NatsKvUtil.writeToKv(getKv(), key, wire);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to save warp {} to KV", warp.name(), e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteWarp(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                getKv().delete("warps." + name.toLowerCase());
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to delete warp {} from KV", name, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<UUID> lookupUuidByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                KeyValueEntry entry = getKv().get("names." + name.toLowerCase());
                if (entry != null && entry.getValue() != null) {
                    return UUID.fromString(entry.getValueAsString());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to lookup UUID for name {}", name, e);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveNameLookup(String name, UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                getKv().put("names." + name.toLowerCase(), uuid.toString());
            } catch (Exception e) {
                LOGGER.error("Failed to save name lookup for {}", name, e);
            }
        });
    }

    @Override
    public void shutdown() {
        // Shared connection is closed centrally if needed, or handled by the NATS addon shutdown.
    }
}

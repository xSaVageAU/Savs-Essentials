package savage.essentials.messaging.nats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;
import savage.essentials.api.messaging.EssentialsMessaging;
import savage.essentials.messaging.nats.util.CompressionUtil;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

public class NatsMessagingProvider implements EssentialsMessaging {
    @Override
    public boolean requiresServerId() {
        return true;
    }
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-nats");
    private static final Gson GSON = new GsonBuilder().create();

    private Connection connection;
    private final String prefix;
    private Dispatcher profileDispatcher;
    private Dispatcher warpDispatcher;

    public NatsMessagingProvider() {
        this.prefix = NatsConfig.get().subjectPrefix;
    }

    private Connection getConnection() {
        if (connection == null) {
            // Wait for NATS to connect
            while (true) {
                Connection conn = savage.natsfabric.NatsManager.getInstance().getConnection();
                if (conn != null && conn.getStatus() == Connection.Status.CONNECTED) {
                    this.connection = conn;
                    break;
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for NATS connection", e);
                }
            }
        }
        return connection;
    }

    @Override
    public void publishProfile(String sourceServerId, UUID playerUuid, Profile profile) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                ProfileWire wire = new ProfileWire(sourceServerId, playerUuid.toString(), profile);
                byte[] data = GSON.toJson(wire).getBytes(StandardCharsets.UTF_8);
                getConnection().publish(prefix + ".profiles." + playerUuid, CompressionUtil.compress(data));
            } catch (Exception e) {
                LOGGER.error("Failed to publish profile update for {}", playerUuid, e);
            }
        });
    }

    @Override
    public void publishWarp(String sourceServerId, Warp warp) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                WarpWire wire = new WarpWire(sourceServerId, warp.name(), warp, false);
                byte[] data = GSON.toJson(wire).getBytes(StandardCharsets.UTF_8);
                getConnection().publish(prefix + ".warps." + warp.name(), CompressionUtil.compress(data));
            } catch (Exception e) {
                LOGGER.error("Failed to publish warp update for {}", warp.name(), e);
            }
        });
    }

    @Override
    public void publishWarpDelete(String sourceServerId, String warpName) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                WarpWire wire = new WarpWire(sourceServerId, warpName, null, true);
                byte[] data = GSON.toJson(wire).getBytes(StandardCharsets.UTF_8);
                getConnection().publish(prefix + ".warps." + warpName, CompressionUtil.compress(data));
            } catch (Exception e) {
                LOGGER.error("Failed to publish warp deletion for {}", warpName, e);
            }
        });
    }

    @Override
    public void subscribeProfile(Consumer<ProfileUpdate> listener) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            profileDispatcher = getConnection().createDispatcher(msg -> {
                try {
                    byte[] decompressed = CompressionUtil.decompress(msg.getData());
                    ProfileWire wire = GSON.fromJson(new String(decompressed, StandardCharsets.UTF_8), ProfileWire.class);
                    listener.accept(new ProfileUpdate(
                            wire.serverId,
                            UUID.fromString(wire.playerUuid),
                            wire.profile
                    ));
                } catch (Exception e) {
                    LOGGER.error("Failed to process profile update", e);
                }
            });
            profileDispatcher.subscribe(prefix + ".profiles.>");
            LOGGER.info("Subscribed to NATS profile updates on {}.profiles.>", prefix);
        });
    }

    @Override
    public void subscribeWarp(Consumer<WarpUpdate> listener) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            warpDispatcher = getConnection().createDispatcher(msg -> {
                try {
                    byte[] decompressed = CompressionUtil.decompress(msg.getData());
                    WarpWire wire = GSON.fromJson(new String(decompressed, StandardCharsets.UTF_8), WarpWire.class);
                    listener.accept(new WarpUpdate(
                            wire.serverId,
                            wire.name,
                            wire.warp,
                            wire.deleted
                    ));
                } catch (Exception e) {
                    LOGGER.error("Failed to process warp update", e);
                }
            });
            warpDispatcher.subscribe(prefix + ".warps.>");
            LOGGER.info("Subscribed to NATS warp updates on {}.warps.>", prefix);
        });
    }

    @Override
    public void shutdown() {
        if (connection != null) {
            try {
                if (profileDispatcher != null) connection.closeDispatcher(profileDispatcher);
                if (warpDispatcher != null) connection.closeDispatcher(warpDispatcher);
            } catch (IllegalStateException ignored) {
                // Dispatchers already closed by NATS-Fabric connection closure
            } catch (Exception e) {
                LOGGER.error("Error closing dispatchers", e);
            }
        }
        LOGGER.info("NATS Messaging Provider shutdown.");
    }

    private static class ProfileWire {
        String serverId;
        String playerUuid;
        Profile profile;

        ProfileWire(String serverId, String playerUuid, Profile profile) {
            this.serverId = serverId;
            this.playerUuid = playerUuid;
            this.profile = profile;
        }
    }

    private static class WarpWire {
        String serverId;
        String name;
        Warp warp;
        boolean deleted;

        WarpWire(String serverId, String name, Warp warp, boolean deleted) {
            this.serverId = serverId;
            this.name = name;
            this.warp = warp;
            this.deleted = deleted;
        }
    }
}

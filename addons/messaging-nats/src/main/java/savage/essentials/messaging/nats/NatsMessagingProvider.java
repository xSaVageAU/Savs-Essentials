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

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

public class NatsMessagingProvider implements EssentialsMessaging {
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-nats");
    private static final Gson GSON = new GsonBuilder().create();

    private final Connection connection;
    private final String prefix;
    private Dispatcher profileDispatcher;
    private Dispatcher warpDispatcher;

    public NatsMessagingProvider() {
        this.connection = NatsConnection.get();
        this.prefix = NatsConfig.get().subjectPrefix;
    }

    @Override
    public void publishProfile(UUID sourceServerId, UUID playerUuid, Profile profile) {
        try {
            ProfileWire wire = new ProfileWire(sourceServerId.toString(), playerUuid.toString(), profile);
            byte[] data = GSON.toJson(wire).getBytes(StandardCharsets.UTF_8);
            connection.publish(prefix + ".profiles." + playerUuid, data);
        } catch (Exception e) {
            LOGGER.error("Failed to publish profile update for {}", playerUuid, e);
        }
    }

    @Override
    public void publishWarp(UUID sourceServerId, Warp warp) {
        try {
            WarpWire wire = new WarpWire(sourceServerId.toString(), warp.name(), warp, false);
            byte[] data = GSON.toJson(wire).getBytes(StandardCharsets.UTF_8);
            connection.publish(prefix + ".warps." + warp.name(), data);
        } catch (Exception e) {
            LOGGER.error("Failed to publish warp update for {}", warp.name(), e);
        }
    }

    @Override
    public void publishWarpDelete(UUID sourceServerId, String warpName) {
        try {
            WarpWire wire = new WarpWire(sourceServerId.toString(), warpName, null, true);
            byte[] data = GSON.toJson(wire).getBytes(StandardCharsets.UTF_8);
            connection.publish(prefix + ".warps." + warpName, data);
        } catch (Exception e) {
            LOGGER.error("Failed to publish warp deletion for {}", warpName, e);
        }
    }

    private boolean isConnected() {
        return connection != null && connection.getStatus() == Connection.Status.CONNECTED;
    }

    @Override
    public void subscribeProfile(Consumer<ProfileUpdate> listener) {
        if (connection == null) return;
        profileDispatcher = connection.createDispatcher(msg -> {
            try {
                ProfileWire wire = GSON.fromJson(new String(msg.getData(), StandardCharsets.UTF_8), ProfileWire.class);
                listener.accept(new ProfileUpdate(
                        UUID.fromString(wire.serverId),
                        UUID.fromString(wire.playerUuid),
                        wire.profile
                ));
            } catch (Exception e) {
                LOGGER.error("Failed to process profile update", e);
            }
        });
        profileDispatcher.subscribe(prefix + ".profiles.>");
        LOGGER.info("Subscribed to NATS profile updates on {}.profiles.>", prefix);
    }

    @Override
    public void subscribeWarp(Consumer<WarpUpdate> listener) {
        if (connection == null) return;
        warpDispatcher = connection.createDispatcher(msg -> {
            try {
                WarpWire wire = GSON.fromJson(new String(msg.getData(), StandardCharsets.UTF_8), WarpWire.class);
                listener.accept(new WarpUpdate(
                        UUID.fromString(wire.serverId),
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
    }

    @Override
    public void shutdown() {
        if (profileDispatcher != null) connection.closeDispatcher(profileDispatcher);
        if (warpDispatcher != null) connection.closeDispatcher(warpDispatcher);
        NatsConnection.close();
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

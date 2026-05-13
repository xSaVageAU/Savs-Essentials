package savage.essentials.nats.standalone.messaging;

import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;
import savage.essentials.api.messaging.EssentialsMessaging;
import savage.essentials.nats.standalone.provider.NatsKvUtil;

import java.util.UUID;
import java.util.function.Consumer;

public class NatsMessagingProvider implements EssentialsMessaging {
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-nats-standalone");
    private KeyValue kv;
    private Consumer<ProfileUpdate> profileListener;
    private Consumer<WarpUpdate> warpListener;

    public NatsMessagingProvider() {
        this.kv = NatsKvUtil.getKv();
        startWatcher();
    }

    private void startWatcher() {
        try {
            kv.watchAll(new KeyValueWatcher() {
                @Override
                public void watch(KeyValueEntry entry) {
                    if (entry.getValue() == null) {
                        if (entry.getKey().startsWith("warps.") && warpListener != null) {
                            String name = entry.getKey().substring("warps.".length());
                            warpListener.accept(new WarpUpdate(new UUID(0, 0), name, null, true));
                        }
                        return;
                    }

                    try {
                        if (entry.getKey().startsWith("profiles.") && profileListener != null) {
                            NatsKvUtil.ProfileWire wire = NatsKvUtil.parseWire(entry.getValue(), NatsKvUtil.ProfileWire.class);
                            if (wire != null) profileListener.accept(new ProfileUpdate(UUID.fromString(wire.serverId()), UUID.fromString(wire.playerUuid()), wire.profile()));
                        } else if (entry.getKey().startsWith("warps.") && warpListener != null) {
                            NatsKvUtil.WarpWire wire = NatsKvUtil.parseWire(entry.getValue(), NatsKvUtil.WarpWire.class);
                            if (wire != null) warpListener.accept(new WarpUpdate(UUID.fromString(wire.serverId()), wire.name(), wire.warp(), wire.deleted()));
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
    public void publishProfile(UUID sourceServerId, UUID playerUuid, Profile profile) {
        try {
            NatsKvUtil.writeToKv(kv, "profiles." + playerUuid, new NatsKvUtil.ProfileWire(sourceServerId.toString(), playerUuid.toString(), profile));
        } catch (Exception e) {
            LOGGER.error("Failed to publish profile {}", playerUuid, e);
        }
    }

    @Override
    public void publishWarp(UUID sourceServerId, Warp warp) {
        try {
            NatsKvUtil.writeToKv(kv, "warps." + warp.name().toLowerCase(), new NatsKvUtil.WarpWire(sourceServerId.toString(), warp.name(), warp, false));
        } catch (Exception e) {
            LOGGER.error("Failed to publish warp {}", warp.name(), e);
        }
    }

    @Override
    public void publishWarpDelete(UUID sourceServerId, String warpName) {
        try {
            NatsKvUtil.writeToKv(kv, "warps." + warpName.toLowerCase(), new NatsKvUtil.WarpWire(sourceServerId.toString(), warpName, null, true));
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
        // Handled centrally
    }
}

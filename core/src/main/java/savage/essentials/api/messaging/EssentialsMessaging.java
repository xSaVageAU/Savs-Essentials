package savage.essentials.api.messaging;

import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Interface for essentials messaging implementations.
 * Handles pub/sub broadcasting of data changes across servers.
 */
public interface EssentialsMessaging {

    /**
     * Publishes a profile update to all listening servers.
     *
     * @param sourceServerId The ID of the server originating the update.
     * @param profile The updated profile data.
     */
    void publishProfile(UUID sourceServerId, Profile profile);

    /**
     * Publishes a warp update to all listening servers.
     */
    void publishWarp(UUID sourceServerId, Warp warp);

    /**
     * Publishes a warp deletion.
     */
    void publishWarpDelete(UUID sourceServerId, String warpName);

    /**
     * Subscribes to profile updates.
     */
    void subscribeProfile(Consumer<ProfileUpdate> listener);

    /**
     * Subscribes to warp updates.
     */
    void subscribeWarp(Consumer<WarpUpdate> listener);

    void shutdown();

    record ProfileUpdate(UUID sourceServerId, Profile profile) {}
    record WarpUpdate(UUID sourceServerId, Warp warp, boolean deleted) {}
}

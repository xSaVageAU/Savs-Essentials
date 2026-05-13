package savage.essentials.core.messaging;

import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;
import savage.essentials.api.messaging.EssentialsMessaging;

import java.util.UUID;
import java.util.function.Consumer;

public class NoOpMessaging implements EssentialsMessaging {
    @Override
    public void publishProfile(String sourceServerId, UUID playerUuid, Profile profile) {}

    @Override
    public void publishWarp(String sourceServerId, Warp warp) {}

    @Override
    public void publishWarpDelete(String sourceServerId, String warpName) {}

    @Override
    public void subscribeProfile(Consumer<ProfileUpdate> listener) {}

    @Override
    public void subscribeWarp(Consumer<WarpUpdate> listener) {}

    @Override
    public void shutdown() {}
}

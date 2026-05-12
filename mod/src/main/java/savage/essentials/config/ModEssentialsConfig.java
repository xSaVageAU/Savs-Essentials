package savage.essentials.config;

import savage.essentials.core.EssentialsCoreConfig;
import java.util.UUID;

public class ModEssentialsConfig implements EssentialsCoreConfig {
    
    private final UUID serverId = UUID.randomUUID(); // In a real mod, this should be persisted

    @Override
    public String getStorageType() {
        return "json";
    }

    @Override
    public String getMessagingType() {
        return "none";
    }

    @Override
    public UUID getServerId() {
        return serverId;
    }

    @Override
    public int getDefaultMaxHomes() {
        return 10;
    }

    @Override
    public int getTeleportDelaySeconds() {
        return 3;
    }
}

package savage.essentials.config;

import savage.essentials.core.EssentialsCoreConfig;
import java.util.UUID;

public class ModEssentialsConfig implements EssentialsCoreConfig {
    
    // Configurable fields with defaults
    private String storageType = "json";
    private String messagingType = "none";
    private UUID serverId = UUID.randomUUID();
    private int defaultMaxHomes = 10;
    private int teleportDelaySeconds = 3;
    private String messagePrefix = "&8[&6Essentials&8] &r";

    @Override
    public String getStorageType() {
        return storageType;
    }

    @Override
    public String getMessagingType() {
        return messagingType;
    }

    @Override
    public UUID getServerId() {
        return serverId;
    }

    @Override
    public int getDefaultMaxHomes() {
        return defaultMaxHomes;
    }

    @Override
    public int getTeleportDelaySeconds() {
        return teleportDelaySeconds;
    }

    @Override
    public String getMessagePrefix() {
        return messagePrefix;
    }
}

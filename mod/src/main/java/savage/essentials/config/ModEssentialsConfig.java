package savage.essentials.config;

import savage.essentials.core.EssentialsCoreConfig;

public class ModEssentialsConfig implements EssentialsCoreConfig {
    
    // Configurable fields with defaults
    private String storageType = "json";
    private String messagingType = "none";
    private String serverId = "";
    private int defaultMaxHomes = 10;
    private int teleportDelaySeconds = 3;
    private int profileCacheTtlMinutes = 60;
    private int profileCacheMaxSize = 10000;
    
    @Override
    public String getStorageType() {
        return storageType;
    }

    @Override
    public String getMessagingType() {
        return messagingType;
    }

    @Override
    public String getServerId() {
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
    public int getProfileCacheTtlMinutes() {
        return profileCacheTtlMinutes;
    }

    @Override
    public int getProfileCacheMaxSize() {
        return profileCacheMaxSize;
    }
}

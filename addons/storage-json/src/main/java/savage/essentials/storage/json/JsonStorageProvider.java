package savage.essentials.storage.json;

import savage.essentials.api.storage.StorageProvider;

public class JsonStorageProvider implements StorageProvider {
    @Override
    public void init() {
        // Initialize JSON storage logic (create folders, etc.)
    }

    @Override
    public void shutdown() {
        // Save any pending data
    }
}

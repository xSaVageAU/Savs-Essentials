package savage.essentials.storage.json;

import savage.essentials.api.storage.StorageEntrypoint;
import savage.essentials.api.storage.StorageProvider;

public class JsonStorageEntrypoint implements StorageEntrypoint {
    @Override
    public StorageProvider createProvider() {
        return new JsonStorageProvider();
    }
}

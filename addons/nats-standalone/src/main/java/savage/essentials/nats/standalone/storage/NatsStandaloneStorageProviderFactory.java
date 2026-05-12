package savage.essentials.nats.standalone.storage;

import savage.essentials.api.storage.StorageEntrypoint;
import savage.essentials.api.storage.StorageProvider;
import savage.essentials.nats.standalone.provider.NatsStandaloneProvider;

public class NatsStandaloneStorageProviderFactory implements StorageEntrypoint {
    @Override
    public String getId() {
        return "nats-standalone";
    }

    @Override
    public StorageProvider createProvider() {
        return NatsStandaloneProvider.getInstance();
    }
}

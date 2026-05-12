package savage.essentials.core;

import net.fabricmc.loader.api.FabricLoader;
import savage.essentials.api.storage.StorageEntrypoint;
import savage.essentials.api.storage.StorageProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry for storage implementations.
 * Discovers providers via Fabric entrypoints.
 */
public class StorageRegistry {
    private static final Map<String, Supplier<StorageProvider>> PROVIDERS = new HashMap<>();

    public static void discoverProviders() {
        FabricLoader.getInstance().getEntrypoints("savs-essentials:storage", StorageEntrypoint.class)
                .forEach(entrypoint -> {
                    // Using the class name as ID for now, or we can add a getId() to the interface
                    String id = entrypoint.getClass().getSimpleName().toLowerCase().replace("storageentrypoint", "");
                    register(id, entrypoint::createProvider);
                });
    }

    public static void register(String id, Supplier<StorageProvider> supplier) {
        PROVIDERS.put(id, supplier);
    }

    public static StorageProvider create(String id) {
        Supplier<StorageProvider> supplier = PROVIDERS.get(id.toLowerCase());
        if (supplier == null) {
            // If no specific one found, try to find ANY provider if only one is registered
            if (PROVIDERS.size() == 1) {
                return PROVIDERS.values().iterator().next().get();
            }
            throw new IllegalStateException("Storage provider '" + id + "' not found!");
        }
        return supplier.get();
    }
}

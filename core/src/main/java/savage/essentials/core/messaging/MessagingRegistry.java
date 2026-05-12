package savage.essentials.core.messaging;

import net.fabricmc.loader.api.FabricLoader;
import savage.essentials.api.messaging.EssentialsMessaging;
import savage.essentials.api.messaging.MessagingProvider;

import java.util.HashMap;
import java.util.Map;

public class MessagingRegistry {
    private static final Map<String, MessagingProvider> PROVIDERS = new HashMap<>();

    public static void discoverProviders() {
        FabricLoader.getInstance().getEntrypoints("savs-essentials:messaging", MessagingProvider.class)
                .forEach(provider -> PROVIDERS.put(provider.getId().toLowerCase(), provider));
    }

    public static EssentialsMessaging create(String type) {
        MessagingProvider provider = PROVIDERS.get(type.toLowerCase());
        if (provider == null) {
            return new NoOpMessaging();
        }
        return provider.create();
    }
}

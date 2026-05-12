package savage.essentials.nats.standalone;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsStandaloneAddon implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-nats-standalone");

    @Override
    public void onInitialize() {
        NatsConfig.get();
        LOGGER.info("Savs Essentials NATS Standalone Addon initialized.");
    }
}

package savage.essentials.messaging.nats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class NatsConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-nats");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("savs-essentials-nats.json");

    private static NatsConfig instance;

    public String url = "nats://localhost:4222";
    public String token = "";
    public String subjectPrefix = "savs.essentials";

    public static NatsConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                    instance = GSON.fromJson(reader, NatsConfig.class);
                }
            } else {
                instance = new NatsConfig();
                save();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load NATS config, using defaults", e);
            instance = new NatsConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (var writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save NATS config", e);
        }
    }
}

package savage.essentials.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-config");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("savs-essentials.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static ModEssentialsConfig config = new ModEssentialsConfig();

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                ModEssentialsConfig loaded = GSON.fromJson(reader, ModEssentialsConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load configuration!", e);
            }
        }
        
        // Always save at startup to ensure the file exists and is up-to-date
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration!", e);
        }
    }

    public static ModEssentialsConfig getConfig() {
        return config;
    }
}

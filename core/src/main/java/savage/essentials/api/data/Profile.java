package savage.essentials.api.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a player profile containing all player-specific data.
 */
public class Profile {
    private final UUID uuid;
    private final Map<String, Home> homes = new HashMap<>();
    private final Map<String, String> metadata = new HashMap<>();

    public Profile(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Map<String, Home> getHomes() {
        return homes;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setHome(String name, Location location) {
        homes.put(name.toLowerCase(), new Home(name, location));
    }

    public void removeHome(String name) {
        homes.remove(name.toLowerCase());
    }
}

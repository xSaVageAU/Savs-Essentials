package savage.essentials.api.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a player profile containing all player-specific data.
 */
public class Profile {
    private String lastKnownName;
    private final Map<String, Home> homes = new HashMap<>();
    private final Map<String, String> metadata = new HashMap<>();

    public Profile(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
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

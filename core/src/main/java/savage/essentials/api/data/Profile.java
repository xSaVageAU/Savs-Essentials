package savage.essentials.api.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a player profile containing all player-specific data.
 */
public class Profile {
    private String lastKnownName;
    private long joinedDate;
    private long revision = 0;
    private final List<String> previousNames = new ArrayList<>();
    private final Map<String, Home> homes = new HashMap<>();
    private final Map<String, String> metadata = new HashMap<>();

    public Profile(String lastKnownName) {
        this(lastKnownName, System.currentTimeMillis());
    }

    public Profile(String lastKnownName, long joinedDate) {
        this.lastKnownName = lastKnownName;
        this.joinedDate = joinedDate;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
        incrementRevision();
    }

    public long getJoinedDate() {
        return joinedDate;
    }

    public void setJoinedDate(long joinedDate) {
        this.joinedDate = joinedDate;
        incrementRevision();
    }

    public long getRevision() {
        return revision;
    }

    public void incrementRevision() {
        this.revision++;
    }

    public List<String> getPreviousNames() {
        return previousNames;
    }

    public void addPreviousName(String name) {
        if (name != null && !previousNames.contains(name)) {
            previousNames.add(name);
            incrementRevision();
        }
    }

    public Map<String, Home> getHomes() {
        return homes;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setHome(String name, Location location, java.util.UUID serverId) {
        homes.put(name.toLowerCase(), new Home(name, location, serverId));
        incrementRevision();
    }

    public void removeHome(String name) {
        if (homes.remove(name.toLowerCase()) != null) {
            incrementRevision();
        }
    }
}

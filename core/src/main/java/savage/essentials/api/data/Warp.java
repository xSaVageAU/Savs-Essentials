package savage.essentials.api.data;

/**
 * Represents a global warp point.
 */
public record Warp(String name, Location location, String serverId, long revision) {
    public Warp(String name, Location location, String serverId) {
        this(name, location, serverId, 0); // Start at 0
    }
    
    public Warp withIncrementedRevision() {
        return new Warp(name, location, serverId, revision + 1);
    }
}

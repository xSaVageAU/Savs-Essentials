package savage.essentials.api.data;

import java.util.UUID;

/**
 * Represents a global warp point.
 */
public record Warp(String name, Location location, UUID serverId, long revision) {
    public Warp(String name, Location location, UUID serverId) {
        this(name, location, serverId, System.currentTimeMillis()); // Use timestamp as initial revision
    }
    
    public Warp withIncrementedRevision() {
        return new Warp(name, location, serverId, revision + 1);
    }
}

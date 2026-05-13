package savage.essentials.api.data;

import java.util.UUID;

/**
 * Represents a global warp point.
 */
public record Warp(String name, Location location, UUID serverId, long revision) {
    public Warp(String name, Location location, UUID serverId) {
        this(name, location, serverId, 0); // Start at 0
    }
    
    public Warp withIncrementedRevision() {
        return new Warp(name, location, serverId, revision + 1);
    }
}

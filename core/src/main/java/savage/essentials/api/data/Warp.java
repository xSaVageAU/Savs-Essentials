package savage.essentials.api.data;

/**
 * Represents a global warp point.
 */
public record Warp(String name, Location location, long revision) {
    public Warp(String name, Location location) {
        this(name, location, System.currentTimeMillis()); // Use timestamp as initial revision
    }
    
    public Warp withIncrementedRevision() {
        return new Warp(name, location, revision + 1);
    }
}

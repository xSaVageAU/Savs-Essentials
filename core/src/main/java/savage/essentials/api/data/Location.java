package savage.essentials.api.data;

/**
 * Represents a location in the Minecraft world.
 */
public record Location(String dimension, double x, double y, double z, float yaw, float pitch) {
    // dimension is stored as a string (e.g., "minecraft:overworld") for easy storage.
}

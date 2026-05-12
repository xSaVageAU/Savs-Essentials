package savage.essentials.api.data;

import java.util.UUID;

/**
 * Represents a player's home point.
 */
public record Home(String name, Location location, UUID serverId) {
}

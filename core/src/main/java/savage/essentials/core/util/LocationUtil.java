package savage.essentials.core.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import savage.essentials.api.data.Location;

/**
 * Standard utility for converting between Minecraft objects and our Location record.
 * Defined in core to ensure all implementations use the same coordinate logic.
 */
public class LocationUtil {

    /**
     * Converts a ServerPlayer's current position to a Location record.
     */
    public static Location fromPlayer(ServerPlayer player) {
        // In 26.1.x, the method is identifier()
        String dim = player.level().dimension().identifier().toString();
        
        return new Location(
            dim,
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYRot(),
            player.getXRot()
        );
    }

    /**
     * Teleports a player to a Location.
     */
    public static void teleport(ServerPlayer player, Location loc) {
        Identifier dimId = Identifier.parse(loc.dimension());
        ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        
        // Robust server access from reference: cast to ServerLevel
        ServerLevel currentLevel = (ServerLevel) player.level();
        ServerLevel targetLevel = currentLevel.getServer().getLevel(dimKey);

        if (targetLevel == null) {
            targetLevel = currentLevel.getServer().overworld();
        }

        Vec3 pos = new Vec3(loc.x(), loc.y(), loc.z());
        
        // Modern 26.1.x teleportation
        TeleportTransition transition = new TeleportTransition(
            targetLevel,
            pos,
            Vec3.ZERO,
            loc.yaw(),
            loc.pitch(),
            TeleportTransition.DO_NOTHING
        );

        player.teleport(transition);
    }
}

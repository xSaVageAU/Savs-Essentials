package savage.essentials.core.manager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import savage.essentials.api.data.Location;
import savage.essentials.core.EssentialsManager;
import savage.essentials.core.util.LocationUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Manages delayed teleports and cancellation logic.
 */
public class TeleportManager {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, ScheduledFuture<?>> pendingTeleports = new ConcurrentHashMap<>();

    /**
     * Requests a teleport with a delay specified in the core config.
     */
    public void requestTeleport(ServerPlayer player, Location target) {
        int delay = EssentialsManager.getInstance().getConfig().getTeleportDelaySeconds();
        
        // Cancel any existing pending teleport for this player
        cancelPending(player.getUUID());

        if (delay <= 0) {
            LocationUtil.teleport(player, target);
            return;
        }

        player.sendSystemMessage(Component.literal("Teleporting in " + delay + " seconds... Don't move!"));
        
        // Snapshot current position to check for movement
        double startX = player.getX();
        double startZ = player.getZ();

        ScheduledFuture<?> task = SCHEDULER.schedule(() -> {
            EssentialsManager.getMinecraftServer().execute(() -> {
                // Check if player is still online and hasn't moved too much
                if (player.isRemoved()) return;

                double diffX = Math.abs(player.getX() - startX);
                double diffZ = Math.abs(player.getZ() - startZ);

                if (diffX > 0.5 || diffZ > 0.5) {
                    player.sendSystemMessage(Component.literal("Teleport cancelled due to movement!"));
                } else {
                    LocationUtil.teleport(player, target);
                    player.sendSystemMessage(Component.literal("Teleported!"));
                }
                pendingTeleports.remove(player.getUUID());
            });
        }, delay, TimeUnit.SECONDS);

        pendingTeleports.put(player.getUUID(), task);
    }

    public void cancelPending(UUID uuid) {
        ScheduledFuture<?> task = pendingTeleports.remove(uuid);
        if (task != null && !task.isDone()) {
            task.cancel(false);
        }
    }

    public void shutdown() {
        SCHEDULER.shutdown();
    }
}

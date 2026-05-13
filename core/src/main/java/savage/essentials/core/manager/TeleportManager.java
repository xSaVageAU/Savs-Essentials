package savage.essentials.core.manager;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import savage.essentials.api.data.Location;
import savage.essentials.core.EssentialsManager;
import savage.essentials.core.util.LocationUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages delayed teleports and cancellation logic using modern Virtual Threads.
 */
public class TeleportManager {
    private final Map<UUID, Thread> pendingTeleports = new ConcurrentHashMap<>();

    public void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                if (cancelPending(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("Teleport cancelled because you took damage!"));
                }
            }
            return true;
        });
    }

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
        double startY = player.getY();
        double startZ = player.getZ();

        Thread task = Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(delay * 1000L);
                
                EssentialsManager.getMinecraftServer().execute(() -> {
                    // Check if player is still online and hasn't moved too much
                    if (player.isRemoved()) return;

                    double diffX = Math.abs(player.getX() - startX);
                    double diffY = Math.abs(player.getY() - startY);
                    double diffZ = Math.abs(player.getZ() - startZ);

                    if (diffX > 0.5 || diffY > 0.5 || diffZ > 0.5) {
                        player.sendSystemMessage(Component.literal("Teleport cancelled due to movement!"));
                    } else {
                        LocationUtil.teleport(player, target);
                        player.sendSystemMessage(Component.literal("Teleported!"));
                    }
                    pendingTeleports.remove(player.getUUID());
                });
            } catch (InterruptedException e) {
                // Thread was interrupted (cancelled), so we just exit silently
            }
        });

        pendingTeleports.put(player.getUUID(), task);
    }

    public boolean cancelPending(UUID uuid) {
        Thread task = pendingTeleports.remove(uuid);
        if (task != null && task.isAlive()) {
            task.interrupt();
            return true;
        }
        return false;
    }

    public void shutdown() {
        // Interrupt all active teleport countdowns on shutdown
        for (Thread thread : pendingTeleports.values()) {
            if (thread.isAlive()) {
                thread.interrupt();
            }
        }
        pendingTeleports.clear();
    }
}

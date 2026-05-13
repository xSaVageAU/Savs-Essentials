package savage.essentials.core.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.essentials.api.data.Profile;
import savage.essentials.api.data.Warp;
import savage.essentials.api.storage.StorageProvider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * A decorator for StorageProvider that performs operations asynchronously 
 * using Virtual Threads, while guaranteeing sequential ordering per account.
 */
public class AsyncStorage implements StorageProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("savs-essentials-storage");
    private final StorageProvider delegate;
    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    private final Map<UUID, CompletableFuture<?>> pendingProfileOperations = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<?>> pendingWarpOperations = new ConcurrentHashMap<>();

    public AsyncStorage(StorageProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean requiresServerId() {
        return delegate.requiresServerId();
    }

    @Override
    public void init() {
        delegate.init();
    }

    @Override
    public CompletableFuture<Map<UUID, Profile>> loadAllProfiles() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.loadAllProfiles().join();
            } catch (Exception e) {
                LOGGER.error("Async loadAllProfiles failed", e);
                return Map.of();
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Profile> loadProfile(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.loadProfile(uuid).join();
            } catch (Exception e) {
                LOGGER.error("Async loadProfile failed for {}", uuid, e);
                return null;
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Boolean> saveProfile(UUID uuid, Profile profile) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        
        pendingProfileOperations.compute(uuid, (id, existing) -> {
            CompletableFuture<?> next = (existing == null || existing.isDone())
                    ? CompletableFuture.completedFuture(null)
                    : existing;

            CompletableFuture<Boolean> task = next.handleAsync((v, ex) -> {
                try {
                    return delegate.saveProfile(uuid, profile).join();
                } catch (Exception e) {
                    LOGGER.error("Async saveProfile failed for {}", uuid, e);
                    return false;
                }
            }, ioExecutor);

            task.whenComplete((res, ex) -> {
                if (ex != null) {
                    result.completeExceptionally(ex);
                } else {
                    result.complete(res);
                }
                pendingProfileOperations.remove(uuid, task);
            });

            return task;
        });

        return result;
    }

    @Override
    public CompletableFuture<Map<String, Warp>> loadWarps() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.loadWarps().join();
            } catch (Exception e) {
                LOGGER.error("Async loadWarps failed", e);
                return Map.of();
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Boolean> saveWarp(Warp warp) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        String key = warp.name().toLowerCase();
        
        pendingWarpOperations.compute(key, (id, existing) -> {
            CompletableFuture<?> next = (existing == null || existing.isDone())
                    ? CompletableFuture.completedFuture(null)
                    : existing;

            CompletableFuture<Boolean> task = next.handleAsync((v, ex) -> {
                try {
                    return delegate.saveWarp(warp).join();
                } catch (Exception e) {
                    LOGGER.error("Async saveWarp failed for {}", key, e);
                    return false;
                }
            }, ioExecutor);

            task.whenComplete((res, ex) -> {
                if (ex != null) {
                    result.completeExceptionally(ex);
                } else {
                    result.complete(res);
                }
                pendingWarpOperations.remove(key, task);
            });

            return task;
        });

        return result;
    }

    @Override
    public CompletableFuture<Boolean> deleteWarp(String name) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        String key = name.toLowerCase();
        
        pendingWarpOperations.compute(key, (id, existing) -> {
            CompletableFuture<?> next = (existing == null || existing.isDone())
                    ? CompletableFuture.completedFuture(null)
                    : existing;

            CompletableFuture<Boolean> task = next.handleAsync((v, ex) -> {
                try {
                    return delegate.deleteWarp(name).join();
                } catch (Exception e) {
                    LOGGER.error("Async deleteWarp failed for {}", key, e);
                    return false;
                }
            }, ioExecutor);

            task.whenComplete((res, ex) -> {
                if (ex != null) {
                    result.completeExceptionally(ex);
                } else {
                    result.complete(res);
                }
                pendingWarpOperations.remove(key, task);
            });

            return task;
        });

        return result;
    }

    @Override
    public void shutdown() {
        int pendingCount = pendingProfileOperations.size() + pendingWarpOperations.size();
        if (pendingCount > 0) {
            LOGGER.info("Flushing {} pending async storage operations...", pendingCount);
        }
        try {
            CompletableFuture<?>[] allOps = new CompletableFuture<?>[pendingProfileOperations.size() + pendingWarpOperations.size()];
            int i = 0;
            for (CompletableFuture<?> f : pendingProfileOperations.values()) allOps[i++] = f;
            for (CompletableFuture<?> f : pendingWarpOperations.values()) allOps[i++] = f;
            
            CompletableFuture.allOf(allOps).get(10, TimeUnit.SECONDS); // 10 seconds timeout
        } catch (TimeoutException e) {
            LOGGER.error("Shutdown timeout! Storage operations were still pending and may have been lost.");
        } catch (Exception e) {
            LOGGER.error("Error while flushing operations: {}", e.getMessage());
        }
        ioExecutor.shutdown();
        delegate.shutdown();
    }
}

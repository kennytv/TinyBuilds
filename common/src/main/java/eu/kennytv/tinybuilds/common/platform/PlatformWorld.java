package eu.kennytv.tinybuilds.common.platform;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public interface PlatformWorld {

    /**
     * Returns the block display with the given id, or null if it doesn't exist or isn't loaded.
     *
     * @return the block display with the given id, or null if it doesn't exist
     */
    @Nullable DisplayHandle display(UUID uuid);

    double nearestPlayerDistanceSquared(double x, double y, double z);

    boolean chunksAndEntitiesLoaded(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ);

    /**
     * Loads all chunks in the given square including their entities, completing with
     * false if loading failed.
     */
    CompletableFuture<Boolean> loadChunksWithEntities(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ);
}

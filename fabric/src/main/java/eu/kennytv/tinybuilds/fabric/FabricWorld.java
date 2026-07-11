package eu.kennytv.tinybuilds.fabric;

import eu.kennytv.tinybuilds.common.platform.DisplayHandle;
import eu.kennytv.tinybuilds.common.platform.PlatformWorld;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;

record FabricWorld(ServerLevel level) implements PlatformWorld {

    @Override
    public DisplayHandle display(final UUID uuid) {
        return this.level.getEntity(uuid) instanceof final Display.BlockDisplay display && !display.isRemoved()
            ? new FabricDisplay(display)
            : null;
    }

    @Override
    public double nearestPlayerDistanceSquared(final double x, final double y, final double z) {
        double nearest = Double.MAX_VALUE;
        for (final ServerPlayer player : this.level.players()) {
            final double dx = player.getX() - x;
            final double dy = player.getY() - y;
            final double dz = player.getZ() - z;
            nearest = Math.min(nearest, dx * dx + dy * dy + dz * dz);
        }
        return nearest;
    }

    @Override
    public boolean chunksAndEntitiesLoaded(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ) {
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!this.level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public CompletableFuture<Boolean> loadChunksWithEntities(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ) {
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                this.level.getChunk(chunkX, chunkZ);
            }
        }
        return CompletableFuture.completedFuture(true);
    }
}

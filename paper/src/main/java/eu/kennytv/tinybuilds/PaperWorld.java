package eu.kennytv.tinybuilds;

import eu.kennytv.tinybuilds.common.platform.DisplayHandle;
import eu.kennytv.tinybuilds.common.platform.PlatformWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.NumberConversions;

record PaperWorld(TinyBuildsPlugin plugin, World world) implements PlatformWorld {

    @Override
    public DisplayHandle display(final UUID uuid) {
        return this.world.getEntity(uuid) instanceof final BlockDisplay display && display.isValid()
            ? new PaperDisplay(display)
            : null;
    }

    @Override
    public double nearestPlayerDistanceSquared(final double x, final double y, final double z) {
        double nearest = Double.MAX_VALUE;
        for (final Player player : this.world.getPlayers()) {
            final double distanceSquared = NumberConversions.square(player.getX() - x)
                + NumberConversions.square(player.getY() - y)
                + NumberConversions.square(player.getZ() - z);
            nearest = Math.min(nearest, distanceSquared);
        }
        return nearest;
    }

    @Override
    public boolean chunksAndEntitiesLoaded(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ) {
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!this.world.isChunkLoaded(chunkX, chunkZ) || !this.world.getChunkAt(chunkX, chunkZ).isEntitiesLoaded()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public CompletableFuture<Boolean> loadChunksWithEntities(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ) {
        final List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                this.world.addPluginChunkTicket(chunkX, chunkZ, this.plugin);
                chunkFutures.add(this.world.getChunkAtAsync(chunkX, chunkZ));
            }
        }

        return CompletableFuture.allOf(chunkFutures.toArray(CompletableFuture[]::new)).handle((v, throwable) -> {
            try {
                if (throwable != null) {
                    this.plugin.getLogger().log(Level.SEVERE, "Failed to load chunks in " + this.world.getName(), throwable);
                    return false;
                }

                for (final CompletableFuture<Chunk> chunk : chunkFutures) {
                    chunk.join().getEntities(); // Make sure entities are loaded
                }
                return true;
            } finally {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        this.world.removePluginChunkTicket(chunkX, chunkZ, this.plugin);
                    }
                }
            }
        });
    }
}

package eu.kennytv.tinybuilds;

import com.sk89q.worldedit.math.BlockVector3;
import eu.kennytv.tinybuilds.common.mesh.BlockGrid;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

final class SelectionBlockGrid implements BlockGrid {
    private final BlockData[] blocks;
    private final boolean[] occluding;
    private final boolean[] mergeExcluded;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    SelectionBlockGrid(final World world, final BlockVector3 min, final BlockVector3 max, final Set<Material> excludedFromStretching) {
        this.sizeX = max.x() - min.x() + 1;
        this.sizeY = max.y() - min.y() + 1;
        this.sizeZ = max.z() - min.z() + 1;
        final int volume = this.sizeX * this.sizeY * this.sizeZ;
        this.blocks = new BlockData[volume];
        this.occluding = new boolean[volume];
        this.mergeExcluded = new boolean[volume];

        for (int x = 0; x < this.sizeX; x++) {
            for (int y = 0; y < this.sizeY; y++) {
                for (int z = 0; z < this.sizeZ; z++) {
                    final Block block = world.getBlockAt(min.x() + x, min.y() + y, min.z() + z);
                    final Material type = block.getType();
                    if (type.isAir()) {
                        continue;
                    }

                    final int index = this.index(x, y, z);
                    this.blocks[index] = block.getBlockData();
                    this.occluding[index] = type.isOccluding();
                    this.mergeExcluded[index] = excludedFromStretching.contains(type);
                }
            }
        }
    }

    BlockData blockData(final int x, final int y, final int z) {
        return this.blocks[this.index(x, y, z)];
    }

    @Override
    public int sizeX() {
        return this.sizeX;
    }

    @Override
    public int sizeY() {
        return this.sizeY;
    }

    @Override
    public int sizeZ() {
        return this.sizeZ;
    }

    @Override
    public Object blockKey(final int x, final int y, final int z) {
        return this.blocks[this.index(x, y, z)];
    }

    @Override
    public boolean isOccluding(final int x, final int y, final int z) {
        return this.occluding[this.index(x, y, z)];
    }

    @Override
    public boolean isMergeExcluded(final int x, final int y, final int z) {
        return this.mergeExcluded[this.index(x, y, z)];
    }

    private int index(final int x, final int y, final int z) {
        return (x * this.sizeY + y) * this.sizeZ + z;
    }
}

package eu.kennytv.tinybuilds.fabric;

import com.sk89q.worldedit.math.BlockVector3;
import eu.kennytv.tinybuilds.common.mesh.BlockGrid;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class LevelBlockGrid implements BlockGrid {
    private final BlockState[] states;
    private final boolean[] mergeExcluded;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    LevelBlockGrid(final ServerLevel level, final BlockVector3 min, final BlockVector3 max, final Set<Block> excludedFromStretching) {
        this.sizeX = max.x() - min.x() + 1;
        this.sizeY = max.y() - min.y() + 1;
        this.sizeZ = max.z() - min.z() + 1;
        final int volume = this.sizeX * this.sizeY * this.sizeZ;
        this.states = new BlockState[volume];
        this.mergeExcluded = new boolean[volume];

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < this.sizeX; x++) {
            for (int y = 0; y < this.sizeY; y++) {
                for (int z = 0; z < this.sizeZ; z++) {
                    final BlockState state = level.getBlockState(pos.set(min.x() + x, min.y() + y, min.z() + z));
                    if (state.isAir()) {
                        continue;
                    }

                    final int index = this.index(x, y, z);
                    this.states[index] = state;
                    this.mergeExcluded[index] = excludedFromStretching.contains(state.getBlock());
                }
            }
        }
    }

    BlockState state(final int x, final int y, final int z) {
        return this.states[this.index(x, y, z)];
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
        return this.states[this.index(x, y, z)];
    }

    @Override
    public boolean isOccluding(final int x, final int y, final int z) {
        final BlockState state = this.states[this.index(x, y, z)];
        return state != null && state.canOcclude();
    }

    @Override
    public boolean isMergeExcluded(final int x, final int y, final int z) {
        return this.mergeExcluded[this.index(x, y, z)];
    }

    private int index(final int x, final int y, final int z) {
        return (x * this.sizeY + y) * this.sizeZ + z;
    }
}

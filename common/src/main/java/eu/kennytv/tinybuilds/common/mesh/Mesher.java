package eu.kennytv.tinybuilds.common.mesh;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a block grid into a minimal-ish set of boxes: hidden blocks are culled
 * entirely and groups of visible blocks are merged.
 */
public final class Mesher {

    private Mesher() {
    }

    public static List<MergedBox> mesh(final BlockGrid grid, final int maxMergeSize) {
        final boolean[][][] visible = VisibilityScan.scan(grid);
        final boolean[][][] considered = new boolean[grid.sizeX()][grid.sizeY()][grid.sizeZ()];
        final List<MergedBox> boxes = new ArrayList<>();

        for (int x = 0; x < grid.sizeX(); x++) {
            for (int y = 0; y < grid.sizeY(); y++) {
                for (int z = 0; z < grid.sizeZ(); z++) {
                    if (considered[x][y][z]) {
                        continue;
                    }

                    // Skip air and blocks that can't be seen from outside the selection
                    final Object key = grid.blockKey(x, y, z);
                    if (key == null || !visible[x][y][z]) {
                        continue;
                    }

                    if (maxMergeSize > 1 && !grid.isMergeExcluded(x, y, z)) {
                        boxes.add(grow(grid, visible, considered, key, x, y, z, maxMergeSize));
                    } else {
                        considered[x][y][z] = true;
                        boxes.add(new MergedBox(x, y, z, 1, 1, 1));
                    }
                }
            }
        }
        return boxes;
    }

    /**
     * Greedily grows a box of identical blocks, one layer at a time and
     * alternating between the axes, until no side can grow anymore.
     */
    private static MergedBox grow(
        final BlockGrid grid, final boolean[][][] visible, final boolean[][][] considered,
        final Object key, final int x, final int y, final int z, final int maxMergeSize
    ) {
        int sizeX = 1;
        int sizeY = 1;
        int sizeZ = 1;
        boolean grew = true;
        while (grew) {
            grew = false;
            if (sizeX < maxMergeSize && canGrowX(grid, visible, considered, key, x, y, z, sizeX, sizeY, sizeZ)) {
                sizeX++;
                grew = true;
            }
            if (sizeY < maxMergeSize && canGrowY(grid, visible, considered, key, x, y, z, sizeY, sizeX, sizeZ)) {
                sizeY++;
                grew = true;
            }
            if (sizeZ < maxMergeSize && canGrowZ(grid, visible, considered, key, x, y, z, sizeZ, sizeX, sizeY)) {
                sizeZ++;
                grew = true;
            }
        }

        for (int dx = 0; dx < sizeX; dx++) {
            for (int dy = 0; dy < sizeY; dy++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    considered[x + dx][y + dy][z + dz] = true;
                }
            }
        }
        return new MergedBox(x, y, z, sizeX, sizeY, sizeZ);
    }

    private static boolean canGrowX(
        final BlockGrid grid, final boolean[][][] visible, final boolean[][][] considered,
        final Object key, final int x, final int y, final int z, final int newX, final int sizeY, final int sizeZ
    ) {
        for (int dy = 0; dy < sizeY; dy++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                if (!canMerge(grid, visible, considered, key, x + newX, y + dy, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canGrowY(
        final BlockGrid grid, final boolean[][][] visible, final boolean[][][] considered,
        final Object key, final int x, final int y, final int z, final int newY, final int sizeX, final int sizeZ
    ) {
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                if (!canMerge(grid, visible, considered, key, x + dx, y + newY, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canGrowZ(
        final BlockGrid grid, final boolean[][][] visible, final boolean[][][] considered,
        final Object key, final int x, final int y, final int z, final int newZ, final int sizeX, final int sizeY
    ) {
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dy = 0; dy < sizeY; dy++) {
                if (!canMerge(grid, visible, considered, key, x + dx, y + dy, z + newZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canMerge(
        final BlockGrid grid, final boolean[][][] visible, final boolean[][][] considered,
        final Object key, final int x, final int y, final int z
    ) {
        if (x >= grid.sizeX() || y >= grid.sizeY() || z >= grid.sizeZ()) {
            return false;
        }
        if (considered[x][y][z]) {
            return false;
        }

        // Hidden blocks can be absorbed into any box regardless of their type:
        // whatever the box renders at their position is enclosed and invisible either
        // way, and this lets shells grow straight through mixed interiors
        if (!visible[x][y][z]) {
            return true;
        }

        // Exact key equality keeps directional blocks from merging into a wrong orientation
        return key.equals(grid.blockKey(x, y, z));
    }
}

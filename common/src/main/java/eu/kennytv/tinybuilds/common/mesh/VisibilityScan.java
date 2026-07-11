package eu.kennytv.tinybuilds.common.mesh;

/**
 * Computes which blocks of a selection can actually be seen from outside of it.
 * The outside space (including a one-block border around the selection) is flood
 * filled through non-occluding blocks; only blocks touched by that space are
 * visible. Unlike a plain six-neighbor occlusion check, this also culls blocks
 * that only face sealed interiors, like inner room walls, furniture, or the
 * lining of enclosed air pockets.
 */
public final class VisibilityScan {

    private VisibilityScan() {
    }

    public static boolean[][][] scan(final BlockGrid grid) {
        final int sizeX = grid.sizeX();
        final int sizeY = grid.sizeY();
        final int sizeZ = grid.sizeZ();
        final int paddedX = sizeX + 2;
        final int paddedY = sizeY + 2;
        final int paddedZ = sizeZ + 2;
        final int layerSize = paddedX * paddedY;

        final boolean[] occluding = new boolean[paddedX * paddedY * paddedZ];
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    occluding[(x + 1) + (y + 1) * paddedX + (z + 1) * layerSize] = grid.isOccluding(x, y, z);
                }
            }
        }

        final boolean[] reachable = new boolean[occluding.length];
        final int[] stack = new int[occluding.length];
        int stackSize = 0;
        reachable[0] = true;
        stack[stackSize++] = 0;
        while (stackSize > 0) {
            final int index = stack[--stackSize];
            final int x = index % paddedX;
            final int y = index / paddedX % paddedY;
            final int z = index / layerSize;
            if (x > 0) {
                stackSize = visit(index - 1, occluding, reachable, stack, stackSize);
            }
            if (x < paddedX - 1) {
                stackSize = visit(index + 1, occluding, reachable, stack, stackSize);
            }
            if (y > 0) {
                stackSize = visit(index - paddedX, occluding, reachable, stack, stackSize);
            }
            if (y < paddedY - 1) {
                stackSize = visit(index + paddedX, occluding, reachable, stack, stackSize);
            }
            if (z > 0) {
                stackSize = visit(index - layerSize, occluding, reachable, stack, stackSize);
            }
            if (z < paddedZ - 1) {
                stackSize = visit(index + layerSize, occluding, reachable, stack, stackSize);
            }
        }

        // Check if it's on the outside (given non-occluding blocks)
        final boolean[][][] visible = new boolean[sizeX][sizeY][sizeZ];
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    final int index = (x + 1) + (y + 1) * paddedX + (z + 1) * layerSize;
                    visible[x][y][z] = reachable[index]
                        || reachable[index - 1] || reachable[index + 1]
                        || reachable[index - paddedX] || reachable[index + paddedX]
                        || reachable[index - layerSize] || reachable[index + layerSize];
                }
            }
        }
        return visible;
    }

    private static int visit(final int index, final boolean[] occluding, final boolean[] reachable, final int[] stack, final int stackSize) {
        if (reachable[index] || occluding[index]) {
            return stackSize;
        }

        reachable[index] = true;
        stack[stackSize] = index;
        return stackSize + 1;
    }
}

package eu.kennytv.tinybuilds.common.mesh;

public interface BlockGrid {

    int sizeX();

    int sizeY();

    int sizeZ();

    /**
     * A stable identity for merge equality: two cells with equal (non-null) keys look
     * identical and may be merged into one stretched display. Null marks air.
     */
    Object blockKey(int x, int y, int z);

    /**
     * Whether the block fully occludes its neighbors' faces, used for the outside
     * visibility scan.
     */
    boolean isOccluding(int x, int y, int z);

    /**
     * Whether the block must not initiate or join a merged box, e.g. because its
     * model would visibly repeat or deform when stretched.
     */
    boolean isMergeExcluded(int x, int y, int z);
}

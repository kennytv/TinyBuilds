package eu.kennytv.tinybuilds.common.mesh;

/**
 * A box of merged, identical blocks in grid-local coordinates.
 * The block at the origin cell defines the appearance of the whole box.
 */
public record MergedBox(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
}

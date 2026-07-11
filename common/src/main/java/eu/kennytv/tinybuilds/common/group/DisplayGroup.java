package eu.kennytv.tinybuilds.common.group;

import java.util.Set;
import java.util.UUID;

/**
 * A placed group of block displays.
 * The world key is the platform's already parsed world identifier (world UUID on
 * Paper, dimension resource key on Fabric), created once at load or place time.
 */
public record DisplayGroup(
    Object worldKey, double centerX, double centerY, double centerZ,
    Set<UUID> entities, double radius, float speed
) {

    public DisplayGroup withSpeed(final float speed) {
        return new DisplayGroup(this.worldKey, this.centerX, this.centerY, this.centerZ, this.entities, this.radius, speed);
    }

    public boolean shouldRotate() {
        return this.speed > 0;
    }
}

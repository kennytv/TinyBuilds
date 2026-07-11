package eu.kennytv.tinybuilds.common.rotation;

import eu.kennytv.tinybuilds.common.group.DisplayGroup;
import eu.kennytv.tinybuilds.common.platform.DisplayHandle;
import eu.kennytv.tinybuilds.common.platform.Platform;
import eu.kennytv.tinybuilds.common.platform.PlatformWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RotatingGroup {
    private static final int STATE_CHECK_INTERVAL = 20;
    private static final double BASE_OBSERVER_DISTANCE = 64;

    private final DisplayGroup group;
    private final Platform platform;
    // Copied per entity before composing, so reusing it across updates is safe
    private final Quaternionf rotationScratch = new Quaternionf();
    private final int configuredUpdateInterval;
    private int updateInterval;
    private double radius;
    private double observerDistanceSquared = BASE_OBSERVER_DISTANCE * BASE_OBSERVER_DISTANCE;
    private List<TrackedDisplay> trackedDisplays;
    private double angle;
    private int ticksSinceUpdate;
    private int ticksSinceStateCheck = STATE_CHECK_INTERVAL;
    private boolean playerNearby;

    public RotatingGroup(final DisplayGroup group, final Platform platform) {
        this.group = group;
        this.platform = platform;
        this.configuredUpdateInterval = platform.rotationUpdateInterval();
        this.updateInterval = RotationTiming.initialInterval(group.speed(), this.configuredUpdateInterval);
        // Stagger groups so multiple rotations don't burst their packets on the same tick
        this.ticksSinceUpdate = Math.floorMod(group.hashCode(), this.updateInterval);
    }

    public void tick() {
        this.ticksSinceStateCheck++;
        if (++this.ticksSinceUpdate < this.updateInterval) {
            return;
        }

        if (this.ticksSinceStateCheck >= STATE_CHECK_INTERVAL) {
            this.ticksSinceStateCheck = 0;
            this.checkState();
        }
        if (!this.playerNearby || this.trackedDisplays == null) {
            return;
        }

        this.ticksSinceUpdate = 0;
        this.angle += (double) this.group.speed() * this.updateInterval;
        if (this.angle > Math.PI * 2) {
            this.angle -= Math.PI * 2;
        }

        final double cos = Math.cos(this.angle);
        final double sin = Math.sin(this.angle);
        final Quaternionf rotation = this.rotationScratch.rotationY((float) -this.angle);
        for (final TrackedDisplay tracked : this.trackedDisplays) {
            final DisplayHandle display = tracked.display();
            if (display.removed()) {
                // Unloaded or removed; recapture the group once it is fully available again
                this.trackedDisplays = null;
                return;
            }

            // "Move" entities via interpolation
            final double rotatedX = tracked.offsetX() * cos - tracked.offsetZ() * sin;
            final double rotatedZ = tracked.offsetX() * sin + tracked.offsetZ() * cos;
            final Vector3f translation = new Vector3f(
                (float) (this.group.centerX() + rotatedX - tracked.posX()),
                tracked.translationY(),
                (float) (this.group.centerZ() + rotatedZ - tracked.posZ())
            );

            // Interpolate over one tick more than the update period to keep transitions smooth
            display.updatePose(
                translation,
                new Quaternionf(rotation).mul(tracked.initialRotation()),
                tracked.scale(),
                tracked.rightRotation(),
                this.updateInterval + 1
            );
        }
    }

    private void checkState() {
        // Check for nearby players every once in a while
        final PlatformWorld world = this.platform.world(this.group.worldKey());
        if (world == null) {
            this.playerNearby = false;
            return;
        }

        if (this.trackedDisplays == null) {
            this.resolveDisplays(world);
            if (this.trackedDisplays == null) {
                this.playerNearby = false;
                return;
            }
        }

        final double distanceSquared = world.nearestPlayerDistanceSquared(this.group.centerX(), this.group.centerY(), this.group.centerZ());
        this.playerNearby = distanceSquared <= this.observerDistanceSquared;
        if (this.playerNearby) {
            this.updateInterval = RotationTiming.intervalFor(this.radius, this.group.speed(), Math.sqrt(distanceSquared), this.configuredUpdateInterval);
        }
    }

    private void resolveDisplays(final PlatformWorld world) {
        final List<TrackedDisplay> displays = new ArrayList<>(this.group.entities().size());
        double maxOffsetSquared = 0;
        for (final UUID uuid : this.group.entities()) {
            final DisplayHandle display = world.display(uuid);
            if (display == null) {
                // Don't rotate while parts of the group are not loaded
                return;
            }

            final Vector3f translation = display.poseTranslation();
            final double offsetX = display.x() + translation.x() - this.group.centerX();
            final double offsetZ = display.z() + translation.z() - this.group.centerZ();
            maxOffsetSquared = Math.max(maxOffsetSquared, offsetX * offsetX + offsetZ * offsetZ);
            displays.add(new TrackedDisplay(
                display,
                offsetX,
                offsetZ,
                translation.y(),
                display.x(),
                display.z(),
                display.poseLeftRotation(),
                display.poseScale(),
                display.poseRightRotation()
            ));
        }

        this.radius = Math.sqrt(maxOffsetSquared);

        // Players just outside the group's edge should count
        final double observerDistance = BASE_OBSERVER_DISTANCE + this.radius;
        this.observerDistanceSquared = observerDistance * observerDistance;

        // The rendered blocks orbit up to two radiuses away from their entity position,
        // so inflate the culling boxes to keep frustum culling correct while rotating
        for (final TrackedDisplay tracked : displays) {
            final Vector3f scale = tracked.scale();
            tracked.display().setCullingBox(
                (float) (4 * this.radius) + 2 * Math.max(scale.x(), scale.z()),
                tracked.translationY() < 0 ? 0 : scale.y() + tracked.translationY()
            );
        }

        this.trackedDisplays = displays;
        this.angle = 0;
    }

    private record TrackedDisplay(
        DisplayHandle display,
        double offsetX, double offsetZ, float translationY,
        double posX, double posZ,
        Quaternionf initialRotation, Vector3f scale, Quaternionf rightRotation
    ) {
    }
}

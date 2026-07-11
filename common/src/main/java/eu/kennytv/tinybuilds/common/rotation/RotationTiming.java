package eu.kennytv.tinybuilds.common.rotation;

/**
 * Chooses how often rotating groups should send transformation updates.
 * Client lerping makes rotation go inward slightly, giving a bit of a bobbing effect.
 * Keep this effect invisible based on distance.
 */
public final class RotationTiming {

    public static final double MAX_STEP_RADIANS = Math.PI / 4;
    private static final double DEVIATION_PER_BLOCK_DISTANCE = 0.001;

    private RotationTiming() {
    }

    public static int initialInterval(final double speedPerTick, final int configuredMax) {
        return clampInterval(MAX_STEP_RADIANS, speedPerTick, configuredMax);
    }

    public static int intervalFor(final double radius, final double speedPerTick, final double observerDistanceToCenter, final int configuredMax) {
        // Pick the update interval so the deviation of the outermost blocks stays effectively invisible
        final double blockDistance = Math.max(1, observerDistanceToCenter - radius);
        final double allowedDeviation = blockDistance * DEVIATION_PER_BLOCK_DISTANCE;
        final double maxStep = radius <= allowedDeviation
            ? MAX_STEP_RADIANS
            : Math.min(MAX_STEP_RADIANS, Math.sqrt(8 * allowedDeviation / radius));
        return clampInterval(maxStep, speedPerTick, configuredMax);
    }

    private static int clampInterval(final double maxStepRadians, final double speedPerTick, final int configuredMax) {
        return Math.clamp((int) (maxStepRadians / speedPerTick), 1, Math.max(1, configuredMax));
    }
}

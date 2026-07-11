package eu.kennytv.tinybuilds.common.group;

import eu.kennytv.tinybuilds.common.platform.DisplayHandle;
import org.joml.Vector3f;

@FunctionalInterface
public interface DisplaySpawner {

    DisplayHandle spawn(double x, double y, double z, Object blockKey, Vector3f scaleVector);
}

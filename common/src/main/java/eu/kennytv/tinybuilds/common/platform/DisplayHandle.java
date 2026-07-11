package eu.kennytv.tinybuilds.common.platform;

import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public interface DisplayHandle {

    UUID uuid();

    double x();

    double z();

    boolean removed();

    Vector3f poseTranslation();

    Quaternionf poseLeftRotation();

    Vector3f poseScale();

    Quaternionf poseRightRotation();

    void updatePose(Vector3f translation, Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation, int interpolationDuration);

    void setCullingBox(float width, float height);

    void remove();
}

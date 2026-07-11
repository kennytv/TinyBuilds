package eu.kennytv.tinybuilds;

import eu.kennytv.tinybuilds.common.platform.DisplayHandle;
import java.util.UUID;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

record PaperDisplay(BlockDisplay handle) implements DisplayHandle {

    @Override
    public UUID uuid() {
        return this.handle.getUniqueId();
    }

    @Override
    public double x() {
        return this.handle.getX();
    }

    @Override
    public double z() {
        return this.handle.getZ();
    }

    @Override
    public boolean removed() {
        return !this.handle.isValid();
    }

    @Override
    public Vector3f poseTranslation() {
        return new Vector3f(this.handle.getTransformation().getTranslation());
    }

    @Override
    public Quaternionf poseLeftRotation() {
        return new Quaternionf(this.handle.getTransformation().getLeftRotation());
    }

    @Override
    public Vector3f poseScale() {
        return new Vector3f(this.handle.getTransformation().getScale());
    }

    @Override
    public Quaternionf poseRightRotation() {
        return new Quaternionf(this.handle.getTransformation().getRightRotation());
    }

    @Override
    public void updatePose(final Vector3f translation, final Quaternionf leftRotation, final Vector3f scale, final Quaternionf rightRotation, final int interpolationDuration) {
        this.handle.setInterpolationDuration(interpolationDuration);
        this.handle.setInterpolationDelay(0);
        this.handle.setTransformation(new Transformation(translation, leftRotation, scale, rightRotation));
    }

    @Override
    public void setCullingBox(final float width, final float height) {
        this.handle.setDisplayWidth(width);
        this.handle.setDisplayHeight(height);
    }

    @Override
    public void remove() {
        this.handle.remove();
    }
}

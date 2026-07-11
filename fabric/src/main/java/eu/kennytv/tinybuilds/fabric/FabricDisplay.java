package eu.kennytv.tinybuilds.fabric;

import com.mojang.math.Transformation;
import eu.kennytv.tinybuilds.common.platform.DisplayHandle;
import java.util.UUID;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

record FabricDisplay(Display.BlockDisplay handle) implements DisplayHandle {

    @Override
    public UUID uuid() {
        return this.handle.getUUID();
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
        return this.handle.isRemoved();
    }

    @Override
    public Vector3f poseTranslation() {
        return new Vector3f(this.transformation().translation());
    }

    @Override
    public Quaternionf poseLeftRotation() {
        return new Quaternionf(this.transformation().leftRotation());
    }

    @Override
    public Vector3f poseScale() {
        return new Vector3f(this.transformation().scale());
    }

    @Override
    public Quaternionf poseRightRotation() {
        return new Quaternionf(this.transformation().rightRotation());
    }

    private Transformation transformation() {
        return Display.createTransformation(this.handle.getEntityData());
    }

    @Override
    public void updatePose(final Vector3f translation, final Quaternionf leftRotation, final Vector3f scale, final Quaternionf rightRotation, final int interpolationDuration) {
        this.handle.setTransformationInterpolationDuration(interpolationDuration);
        this.handle.setTransformationInterpolationDelay(0);
        this.handle.setTransformation(new Transformation(translation, leftRotation, scale, rightRotation));
    }

    @Override
    public void setCullingBox(final float width, final float height) {
        this.handle.setWidth(width);
        this.handle.setHeight(height);
    }

    @Override
    public void remove() {
        this.handle.discard();
    }
}

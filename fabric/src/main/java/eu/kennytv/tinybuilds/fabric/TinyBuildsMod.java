package eu.kennytv.tinybuilds.fabric;

import com.mojang.math.Transformation;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.regions.Region;
import eu.kennytv.tinybuilds.common.group.GroupManager;
import eu.kennytv.tinybuilds.common.group.GroupStorage;
import eu.kennytv.tinybuilds.common.platform.Platform;
import eu.kennytv.tinybuilds.common.platform.PlatformWorld;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TinyBuildsMod implements ModInitializer, Platform {
    public static final String GROUP_TAG_PREFIX = "tinybuilds.";
    private static final Vector3f ZERO_VECTOR = new Vector3f(0, 0, 0);
    private static final Quaternionf NO_ROTATION = new Quaternionf(new AxisAngle4f(0, 0, 0, 1));

    private TinyBuildsConfig config;
    private GroupStorage storage;
    private GroupManager manager;
    private MinecraftServer server;

    @Override
    public void onInitialize() {
        final Path configDir = FabricLoader.getInstance().getConfigDir().resolve("tinybuilds");
        this.config = TinyBuildsConfig.load(configDir.resolve("config.json"));
        this.manager = new GroupManager(this);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.getRoot().addChild(TinyBuildsCommand.create(this)));

        // Store them per save
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            this.server = server;
            this.storage = new JsonGroupStorage(server.getWorldPath(LevelResource.DATA).resolve("tinybuilds_groups.json"));
            this.manager.load();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> this.server = null);
        ServerTickEvents.END_SERVER_TICK.register(server -> this.manager.tick());

        if (this.config.removeOrphanedDisplays) {
            // Remove display entities that claim to belong to a group but aren't part
            // of it (anymore), e.g. left over from a crash before the group file was written
            ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                if (!(entity instanceof final Display.BlockDisplay display)) {
                    return;
                }

                for (final String tag : display.entityTags()) {
                    if (tag.startsWith(GROUP_TAG_PREFIX)
                        && !this.manager.isKnownGroupEntity(tag.substring(GROUP_TAG_PREFIX.length()), display.getUUID())) {
                        display.discard();
                        return;
                    }
                }
            });
        }
    }

    public GroupManager manager() {
        return this.manager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PlatformWorld world(final Object worldKey) {
        if (this.server == null) {
            return null;
        }

        final ServerLevel level = this.server.getLevel((ResourceKey<Level>) worldKey);
        return level != null ? new FabricWorld(level) : null;
    }

    @Override
    public GroupStorage storage() {
        return this.storage;
    }

    @Override
    public int rotationUpdateInterval() {
        return this.config.rotationUpdateIntervalTicks;
    }

    @Override
    public long maxSelectionVolume() {
        return this.config.maxSelectionVolume;
    }

    @Override
    public int maxDisplayEntities() {
        return this.config.maxDisplayEntities;
    }

    public void place(final ServerPlayer player, final String groupName, final float scale, final int maxMergeSize) {
        final ServerLevel level = player.level();

        final LocalSession session = WorldEdit.getInstance().getSessionManager().get(FabricAdapter.get().fromNativePlayer(player));
        final Region selection;
        try {
            selection = session.getSelection(FabricAdapter.get().fromNativeWorld(level));
        } catch (final IncompleteRegionException e) {
            player.sendSystemMessage(Component.literal("Incomplete WorldEdit selection."));
            return;
        }

        this.manager.place(
            new FabricPlayer(player), groupName, scale, maxMergeSize,
            selection, level.dimension(),
            () -> new LevelBlockGrid(level, selection.getMinimumPoint(), selection.getMaximumPoint(), this.config.excludedFromStretching),
            (x, y, z, blockKey, scaleVector) -> {
                final Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.COMMAND);
                display.setPos(x, y, z);
                display.setBlockState((BlockState) blockKey);
                display.setTransformation(new Transformation(ZERO_VECTOR, NO_ROTATION, scaleVector, NO_ROTATION));
                display.setTransformationInterpolationDelay(Integer.MAX_VALUE);
                display.setViewRange(this.config.displayViewRange);
                // A zero-sized culling box (the default) disables frustum culling
                // entirely, so give the client one matching the scaled block extent
                display.setWidth(2 * Math.max(scaleVector.x(), scaleVector.z()));
                display.setHeight(scaleVector.y());
                level.addFreshEntity(display);
                display.addTag(GROUP_TAG_PREFIX + groupName);
                return new FabricDisplay(display);
            }
        );
    }
}

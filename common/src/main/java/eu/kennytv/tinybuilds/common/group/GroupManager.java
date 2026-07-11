package eu.kennytv.tinybuilds.common.group;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import eu.kennytv.tinybuilds.common.mesh.BlockGrid;
import eu.kennytv.tinybuilds.common.mesh.MergedBox;
import eu.kennytv.tinybuilds.common.mesh.Mesher;
import eu.kennytv.tinybuilds.common.platform.DisplayHandle;
import eu.kennytv.tinybuilds.common.platform.Platform;
import eu.kennytv.tinybuilds.common.platform.PlatformPlayer;
import eu.kennytv.tinybuilds.common.platform.PlatformWorld;
import eu.kennytv.tinybuilds.common.rotation.RotatingGroup;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.joml.Vector3f;

public final class GroupManager {
    private static final Pattern GROUP_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");

    private final Map<String, DisplayGroup> groups = new HashMap<>();
    private final Map<String, RotatingGroup> rotatingGroups = new HashMap<>();
    private final Platform platform;

    public GroupManager(final Platform platform) {
        this.platform = platform;
    }

    public void load() {
        this.groups.clear();
        this.rotatingGroups.clear();
        this.groups.putAll(this.platform.storage().load());
        for (final Map.Entry<String, DisplayGroup> entry : this.groups.entrySet()) {
            if (entry.getValue().shouldRotate()) {
                this.rotatingGroups.put(entry.getKey(), new RotatingGroup(entry.getValue(), this.platform));
            }
        }
    }

    public Set<String> groupNames() {
        return this.groups.keySet();
    }

    public boolean isKnownGroupEntity(final String groupName, final UUID uuid) {
        final DisplayGroup group = this.groups.get(groupName);
        return group != null && group.entities().contains(uuid);
    }

    public void tick() {
        for (final RotatingGroup group : this.rotatingGroups.values()) {
            group.tick();
        }
    }

    public void setRotation(final PlatformPlayer sender, final String groupName, final float speed) {
        final DisplayGroup group = this.groups.get(groupName);
        if (group == null) {
            sender.sendMessage("<red>No group with that name exists.");
            return;
        }

        final DisplayGroup updated = group.withSpeed(speed / 20);
        this.groups.put(groupName, updated);
        if (updated.shouldRotate()) {
            this.rotatingGroups.put(groupName, new RotatingGroup(updated, this.platform));
        } else {
            this.rotatingGroups.remove(groupName);
        }

        this.saveGroups();
        sender.sendMessage("Set rotation speed for group <green>" + groupName + "<white> to <green>" + speed + "<white>.");
    }

    public CompletableFuture<Boolean> removeGroup(final PlatformPlayer sender, final String groupName) {
        final DisplayGroup group = this.groups.get(groupName);
        if (group == null) {
            sender.sendMessage("<red>No group with that name exists.");
            return CompletableFuture.completedFuture(false);
        }

        final PlatformWorld world = this.platform.world(group.worldKey());
        if (world == null) {
            sender.sendMessage("<red>The world of group <green>" + groupName + "<red> is not loaded.");
            return CompletableFuture.completedFuture(false);
        }

        final int minChunkX = ((int) Math.floor(group.centerX() - group.radius())) >> 4;
        final int maxChunkX = ((int) Math.floor(group.centerX() + group.radius())) >> 4;
        final int minChunkZ = ((int) Math.floor(group.centerZ() - group.radius())) >> 4;
        final int maxChunkZ = ((int) Math.floor(group.centerZ() + group.radius())) >> 4;

        // If everything is already loaded, remove the group right away
        if (world.chunksAndEntitiesLoaded(minChunkX, maxChunkX, minChunkZ, maxChunkZ)) {
            this.removeLoadedGroup(sender, groupName, group, world);
            return CompletableFuture.completedFuture(true);
        }

        // Load all chunks within the group's radius first, so arbitrarily large
        // groups can be removed without anyone standing near them
        return world.loadChunksWithEntities(minChunkX, maxChunkX, minChunkZ, maxChunkZ).thenApply(success -> {
            if (!success) {
                sender.sendMessage("<red>Failed to load the chunks of group <green>" + groupName + "<red>.");
                return false;
            }

            this.removeLoadedGroup(sender, groupName, group, world);
            return true;
        });
    }

    private void removeLoadedGroup(final PlatformPlayer sender, final String groupName, final DisplayGroup group, final PlatformWorld world) {
        int missing = 0;
        for (final UUID uuid : group.entities()) {
            final DisplayHandle display = world.display(uuid);
            if (display != null) {
                display.remove();
            } else {
                missing++;
            }
        }

        this.groups.remove(groupName);
        this.rotatingGroups.remove(groupName);
        this.saveGroups();
        if (missing > 0) {
            // Most likely manually killed
            sender.sendMessage("Removed group <green>" + groupName + "<white> (" + missing + " displays were already gone or will be cleaned up on chunk load).");
        } else {
            sender.sendMessage("Removed group <green>" + groupName + "<white>.");
        }
    }

    public void place(
        final PlatformPlayer player, final String groupName, final float scale, final int maxMergeSize,
        final Region selection, final Object worldKey,
        final Supplier<BlockGrid> gridFactory, final DisplaySpawner spawner
    ) {
        if (!GROUP_NAME_PATTERN.matcher(groupName).matches()) {
            player.sendMessage("<red>Group names may only contain letters, numbers, dashes, and underscores.");
            return;
        }
        if (scale <= 0) {
            player.sendMessage("<red>Scale must be greater than 0");
            return;
        }
        if (maxMergeSize <= 0) {
            player.sendMessage("<red>Blocks per display must be greater than 0");
            return;
        }
        if (selection.getVolume() > this.platform.maxSelectionVolume()) {
            player.sendMessage("<red>Selection is too large (" + selection.getVolume() + " blocks, maximum is " + this.platform.maxSelectionVolume() + ").");
            return;
        }

        // Capture the destination before a possibly delayed removal of the old group
        final double destinationCenterX = player.x() - 0.5;
        final double destinationCenterY = player.y();
        final double destinationCenterZ = player.z() - 0.5;

        if (this.groups.containsKey(groupName)) {
            this.removeGroup(player, groupName).thenAccept(success -> {
                if (success) {
                    this.placeNow(player, groupName, scale, maxMergeSize, selection, worldKey, gridFactory, spawner,
                        destinationCenterX, destinationCenterY, destinationCenterZ);
                }
            });
            return;
        }

        this.placeNow(player, groupName, scale, maxMergeSize, selection, worldKey, gridFactory, spawner,
            destinationCenterX, destinationCenterY, destinationCenterZ);
    }

    private void placeNow(
        final PlatformPlayer player, final String groupName, final float scale, final int maxMergeSize,
        final Region selection, final Object worldKey,
        final Supplier<BlockGrid> gridFactory, final DisplaySpawner spawner,
        final double destinationCenterX, final double destinationCenterY, final double destinationCenterZ
    ) {
        final BlockVector3 min = selection.getMinimumPoint();
        final BlockVector3 center = selection.getCenter().toBlockPoint();

        final BlockGrid grid = gridFactory.get();
        final List<MergedBox> boxes = Mesher.mesh(grid, maxMergeSize);
        if (boxes.isEmpty()) {
            player.sendMessage("<red>No blocks found in the selection.");
            return;
        }
        if (boxes.size() > this.platform.maxDisplayEntities()) {
            player.sendMessage("<red>The selection would need " + boxes.size() + " display entities (maximum is "
                + this.platform.maxDisplayEntities() + "); increase the merge size or the max-display-entities config value.");
            return;
        }

        final Set<UUID> entities = new LinkedHashSet<>();
        double radius = 0;
        for (final MergedBox box : boxes) {
            final double destinationX = destinationCenterX + (min.x() + box.x() - center.x()) * scale;
            final double destinationY = destinationCenterY + (min.y() + box.y() - center.y()) * scale;
            final double destinationZ = destinationCenterZ + (min.z() + box.z() - center.z()) * scale;
            final Object blockKey = grid.blockKey(box.x(), box.y(), box.z());
            final Vector3f scaleVector = new Vector3f(scale * box.sizeX(), scale * box.sizeY(), scale * box.sizeZ());

            final DisplayHandle display = spawner.spawn(destinationX, destinationY, destinationZ, blockKey, scaleVector);
            entities.add(display.uuid());
            radius = Math.max(radius, Math.max(
                Math.abs(display.x() - destinationCenterX),
                Math.abs(display.z() - destinationCenterZ)
            ));
        }

        final DisplayGroup group = new DisplayGroup(
            worldKey,
            destinationCenterX,
            destinationCenterY,
            destinationCenterZ,
            entities,
            radius,
            0
        );
        this.groups.put(groupName, group);
        this.rotatingGroups.remove(groupName);
        this.saveGroups();
        player.sendMessage("<white>Placed and saved " + entities.size() + " block displays as group <green>" + groupName + "<white>.");
    }

    private void saveGroups() {
        this.platform.storage().save(this.groups);
    }
}

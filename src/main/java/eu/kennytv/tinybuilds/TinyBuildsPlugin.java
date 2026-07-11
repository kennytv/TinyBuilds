package eu.kennytv.tinybuilds;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import eu.kennytv.tinybuilds.command.TinyBuildsCommand;
import eu.kennytv.tinybuilds.listener.OrphanedDisplayListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TinyBuildsPlugin extends JavaPlugin {
    private static final Vector3f ZERO_VECTOR = new Vector3f(0, 0, 0);
    private static final AxisAngle4f ZERO_AXIS = new AxisAngle4f(0, 0, 0, 1);
    private static final Pattern GROUP_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");

    private final Map<String, DisplayEntityGroup> entityGroups = new HashMap<>();
    private final Map<String, RotatingEntityGroup> rotatingEntityGroups = new HashMap<>();
    private final Set<Material> excludedFromStretching = EnumSet.noneOf(Material.class);
    private NamespacedKey groupKey;
    private File groupsFile;
    private int rotationUpdateInterval;
    private float displayViewRange;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        for (final String name : this.getConfig().getStringList("excluded-from-stretching")) {
            final Material material = Material.matchMaterial(name);
            if (material != null) {
                this.excludedFromStretching.add(material);
            } else {
                this.getLogger().warning("Unknown material in excluded-from-stretching: " + name);
            }
        }

        this.rotationUpdateInterval = Math.max(1, this.getConfig().getInt("rotation-update-interval-ticks", 10));
        this.displayViewRange = (float) this.getConfig().getDouble("display-view-range", 1.0);

        this.groupKey = new NamespacedKey(this, "group");
        this.groupsFile = new File(this.getDataFolder(), "groups.yml");
        this.loadGroups();

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            event.registrar().register(TinyBuildsCommand.create(this), "Manage block display groups"));

        if (this.getConfig().getBoolean("remove-orphaned-displays", true)) {
            this.getServer().getPluginManager().registerEvents(new OrphanedDisplayListener(this), this);
        }

        this.getServer().getScheduler().runTaskTimer(this, () -> {
            for (final RotatingEntityGroup group : this.rotatingEntityGroups.values()) {
                group.tick();
            }
        }, 1L, 1L);
    }

    public NamespacedKey groupKey() {
        return this.groupKey;
    }

    public Set<String> groupNames() {
        return this.entityGroups.keySet();
    }

    public boolean isKnownGroupEntity(final String groupName, final UUID uuid) {
        final DisplayEntityGroup group = this.entityGroups.get(groupName);
        return group != null && group.entities().contains(uuid);
    }

    public void setRotation(final Player sender, final String groupName, final float speed) {
        final DisplayEntityGroup group = this.entityGroups.get(groupName);
        if (group == null) {
            sender.sendRichMessage("<red>No group with that name exists.");
            return;
        }

        final DisplayEntityGroup updated = group.withSpeed(speed / 20);
        this.entityGroups.put(groupName, updated);
        if (updated.shouldRotate()) {
            this.rotatingEntityGroups.put(groupName, new RotatingEntityGroup(updated, this.rotationUpdateInterval));
        } else {
            this.rotatingEntityGroups.remove(groupName);
        }

        this.saveGroups();
        sender.sendRichMessage("Set rotation speed for group <green>" + groupName + "<white> to <green>" + speed + "<white>.");
    }

    public CompletableFuture<Boolean> removeDisplayGroup(final Player sender, final String groupName) {
        final DisplayEntityGroup entityGroup = this.entityGroups.get(groupName);
        if (entityGroup == null) {
            sender.sendRichMessage("<red>No group with that name exists.");
            return CompletableFuture.completedFuture(false);
        }

        final World world = getServer().getWorld(entityGroup.worldId());
        if (world == null) {
            sender.sendRichMessage("<red>The world of group <green>" + groupName + "<red> is not loaded.");
            return CompletableFuture.completedFuture(false);
        }

        final int minChunkX = ((int) Math.floor(entityGroup.centerX() - entityGroup.radius())) >> 4;
        final int maxChunkX = ((int) Math.floor(entityGroup.centerX() + entityGroup.radius())) >> 4;
        final int minChunkZ = ((int) Math.floor(entityGroup.centerZ() - entityGroup.radius())) >> 4;
        final int maxChunkZ = ((int) Math.floor(entityGroup.centerZ() + entityGroup.radius())) >> 4;

        // If everything is already loaded, remove the group right away
        if (allChunksLoaded(world, minChunkX, maxChunkX, minChunkZ, maxChunkZ)) {
            this.removeLoadedGroup(sender, groupName, entityGroup, world);
            return CompletableFuture.completedFuture(true);
        }

        // Load all chunks within the group's radius first, so arbitrarily large
        // groups can be removed without anyone standing near them.
        final List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                world.addPluginChunkTicket(chunkX, chunkZ, this);
                chunkFutures.add(world.getChunkAtAsync(chunkX, chunkZ));
            }
        }

        return CompletableFuture.allOf(chunkFutures.toArray(CompletableFuture[]::new)).handle((v, throwable) -> {
            try {
                if (throwable != null) {
                    this.getLogger().log(Level.SEVERE, "Failed to load chunks of group " + groupName, throwable);
                    sender.sendRichMessage("<red>Failed to load the chunks of group <green>" + groupName + "<red>.");
                    return false;
                }

                // Entity loading can trail chunk loading; getEntities() forces it
                for (final CompletableFuture<Chunk> chunk : chunkFutures) {
                    chunk.join().getEntities();
                }

                this.removeLoadedGroup(sender, groupName, entityGroup, world);
                return true;
            } finally {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        world.removePluginChunkTicket(chunkX, chunkZ, this);
                    }
                }
            }
        });
    }

    private static boolean allChunksLoaded(final World world, final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ) {
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ) || !world.getChunkAt(chunkX, chunkZ).isEntitiesLoaded()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void removeLoadedGroup(
        final Player sender, final String groupName,
        final DisplayEntityGroup entityGroup, final World world
    ) {
        int missing = 0;
        for (final UUID uuid : entityGroup.entities()) {
            if (world.getEntity(uuid) instanceof final BlockDisplay display) {
                display.remove();
            } else {
                missing++;
            }
        }

        this.entityGroups.remove(groupName);
        this.rotatingEntityGroups.remove(groupName);
        this.saveGroups();
        if (missing > 0) {
            // Most likely killed manually
            sender.sendRichMessage("Removed group <green>" + groupName + "<white> (" + missing + " displays were already gone or will be cleaned up on chunk load).");
        } else {
            sender.sendRichMessage("Removed group <green>" + groupName + "<white>.");
        }
    }

    public void spawnBlockDisplays(final Player player, final String groupName, final float scale, final int maxMergeSize) {
        if (!GROUP_NAME_PATTERN.matcher(groupName).matches()) {
            player.sendRichMessage("<red>Group names may only contain letters, numbers, dashes, and underscores.");
            return;
        }
        if (scale <= 0) {
            player.sendRichMessage("<red>Scale must be greater than 0");
            return;
        }
        if (maxMergeSize <= 0) {
            player.sendRichMessage("<red>Blocks per display must be greater than 0");
            return;
        }

        final Location destinationCenter = player.getLocation().subtract(0.5, 0, 0.5);
        final World world = destinationCenter.getWorld();

        // Get the WE selection
        final LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        final Region selection;
        try {
            selection = session.getSelection(BukkitAdapter.adapt(world));
        } catch (final IncompleteRegionException e) {
            player.sendRichMessage("<red>Incomplete WorldEdit selection.");
            return;
        }

        final long maxVolume = this.getConfig().getLong("max-selection-volume", 100_000L);
        if (selection.getVolume() > maxVolume) {
            player.sendRichMessage("<red>Selection is too large (" + selection.getVolume() + " blocks, maximum is " + maxVolume + ").");
            return;
        }

        // Remove the old group first; this may need to load its chunks, so the
        // actual placement continues once the removal has gone through
        if (this.entityGroups.containsKey(groupName)) {
            this.removeDisplayGroup(player, groupName).thenAccept(success -> {
                if (success) {
                    this.placeBlockDisplays(player, groupName, scale, maxMergeSize, selection, destinationCenter, world);
                }
            });
            return;
        }

        this.placeBlockDisplays(player, groupName, scale, maxMergeSize, selection, destinationCenter, world);
    }

    private void placeBlockDisplays(
        final Player player, final String groupName, final float scale, final int maxMergeSize,
        final Region selection, final Location destinationCenter, final World world
    ) {
        // Go through all of the blocks
        final BlockVector3 min = selection.getMinimumPoint();
        final BlockVector3 max = selection.getMaximumPoint();
        final BlockVector3 center = selection.getCenter().toBlockPoint();

        final int maxEntities = this.getConfig().getInt("max-display-entities", 2000);
        final List<BlockDisplay> spawned = new ArrayList<>();
        final boolean[][][] visible = computeVisibleBlocks(world, min, max);
        final boolean[][][] considered = new boolean[max.x() - min.x() + 1][max.y() - min.y() + 1][max.z() - min.z() + 1];

        for (int x = min.x(); x <= max.x(); x++) {
            final int relativeX = x - center.x();
            final double destinationX = destinationCenter.x() + relativeX * scale;
            for (int y = min.y(); y <= max.y(); y++) {
                final int relativeY = y - center.y();
                final double destinationY = destinationCenter.y() + relativeY * scale;
                for (int z = min.z(); z <= max.z(); z++) {
                    if (considered[x - min.x()][y - min.y()][z - min.z()]) {
                        continue;
                    }

                    final int relativeZ = z - center.z();
                    final double destinationZ = destinationCenter.z() + relativeZ * scale;

                    final Block block = world.getBlockAt(x, y, z);

                    // Don't create display entities for air or blocks that can't be seen from the outside
                    final Material type = block.getType();
                    if (type.isAir() || !visible[x - min.x()][y - min.y()][z - min.z()]) {
                        continue;
                    }

                    final Vector3f scaleVector;
                    if (maxMergeSize > 1 && !this.excludedFromStretching.contains(type)) {
                        scaleVector = this.checkAndScale(x, y, z, min, max, block, considered, visible, scale, maxMergeSize);
                    } else {
                        scaleVector = new Vector3f(scale, scale, scale);
                    }

                    if (spawned.size() >= maxEntities) {
                        spawned.forEach(BlockDisplay::remove);
                        player.sendRichMessage("<red>The selection would need more than " + maxEntities
                            + " display entities; increase the merge size or the max-display-entities config value.");
                        return;
                    }

                    final BlockDisplay displayEntity = world.spawn(new Location(world, destinationX, destinationY, destinationZ), BlockDisplay.class, display -> {
                        display.setBlock(block.getBlockData());
                        display.setTransformation(new Transformation(ZERO_VECTOR, ZERO_AXIS, scaleVector, ZERO_AXIS));
                        display.setInterpolationDelay(Integer.MAX_VALUE);
                        display.setViewRange(this.displayViewRange);
                        // A zero-sized culling box (the default) disables frustum culling
                        // entirely, so give the client one matching the scaled block extent
                        display.setDisplayWidth(2 * Math.max(scaleVector.x(), scaleVector.z()));
                        display.setDisplayHeight(scaleVector.y());
                        display.getPersistentDataContainer().set(this.groupKey, PersistentDataType.STRING, groupName);
                    });
                    spawned.add(displayEntity);
                }
            }
        }

        if (spawned.isEmpty()) {
            player.sendRichMessage("<red>No blocks found in the selection.");
            return;
        }

        final Set<UUID> entities = new LinkedHashSet<>();
        double radius = 0;
        for (final BlockDisplay display : spawned) {
            entities.add(display.getUniqueId());
            radius = Math.max(radius, Math.max(
                Math.abs(display.getX() - destinationCenter.x()),
                Math.abs(display.getZ() - destinationCenter.z())
            ));
        }

        final DisplayEntityGroup group = new DisplayEntityGroup(
            world.getUID(),
            destinationCenter.x(),
            destinationCenter.y(),
            destinationCenter.z(),
            entities,
            radius,
            0
        );
        this.entityGroups.put(groupName, group);
        this.rotatingEntityGroups.remove(groupName);
        this.saveGroups();
        player.sendRichMessage("<white>Placed and saved " + entities.size() + " block displays as group <green>" + groupName + "<white>.");
    }

    private Vector3f checkAndScale(
        final int x,
        final int y,
        final int z,
        final BlockVector3 min,
        final BlockVector3 max,
        final Block block,
        final boolean[][][] considered,
        final boolean[][][] visible,
        final float scale,
        final int maxMergeSize
    ) {
        // Greedily grow a 3D box of the same blocks, one layer at a time,
        // alternating between the axes until no side can grow anymore
        final BlockData blockData = block.getBlockData();
        int sizeX = 1;
        int sizeY = 1;
        int sizeZ = 1;
        boolean grew = true;
        while (grew) {
            grew = false;
            if (sizeX < maxMergeSize && this.canGrowX(block, blockData, min, max, considered, visible, sizeX, sizeY, sizeZ)) {
                sizeX++;
                grew = true;
            }
            if (sizeY < maxMergeSize && this.canGrowY(block, blockData, min, max, considered, visible, sizeY, sizeX, sizeZ)) {
                sizeY++;
                grew = true;
            }
            if (sizeZ < maxMergeSize && this.canGrowZ(block, blockData, min, max, considered, visible, sizeZ, sizeX, sizeY)) {
                sizeZ++;
                grew = true;
            }
        }

        for (int dx = 0; dx < sizeX; dx++) {
            for (int dy = 0; dy < sizeY; dy++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    considered[x + dx - min.x()][y + dy - min.y()][z + dz - min.z()] = true;
                }
            }
        }
        return new Vector3f(scale * sizeX, scale * sizeY, scale * sizeZ);
    }

    private boolean canGrowX(
        final Block block, final BlockData data, final BlockVector3 min, final BlockVector3 max,
        final boolean[][][] considered, final boolean[][][] visible, final int newX, final int sizeY, final int sizeZ
    ) {
        for (int dy = 0; dy < sizeY; dy++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                if (!this.canMerge(block, data, min, max, considered, visible, newX, dy, dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canGrowY(
        final Block block, final BlockData data, final BlockVector3 min, final BlockVector3 max,
        final boolean[][][] considered, final boolean[][][] visible, final int newY, final int sizeX, final int sizeZ
    ) {
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                if (!this.canMerge(block, data, min, max, considered, visible, dx, newY, dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canGrowZ(
        final Block block, final BlockData data, final BlockVector3 min, final BlockVector3 max,
        final boolean[][][] considered, final boolean[][][] visible, final int newZ, final int sizeX, final int sizeY
    ) {
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dy = 0; dy < sizeY; dy++) {
                if (!this.canMerge(block, data, min, max, considered, visible, dx, dy, newZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canMerge(
        final Block block,
        final BlockData data,
        final BlockVector3 min,
        final BlockVector3 max,
        final boolean[][][] considered,
        final boolean[][][] visible,
        final int offsetX, final int offsetY, final int offsetZ
    ) {
        final int x = block.getX() + offsetX;
        final int y = block.getY() + offsetY;
        final int z = block.getZ() + offsetZ;
        if (x > max.x() || y > max.y() || z > max.z()) {
            return false;
        }
        if (considered[x - min.x()][y - min.y()][z - min.z()]) {
            return false;
        }

        // Hidden blocks can be absorbed into any box to allow for surface merges.
        if (!visible[x - min.x()][y - min.y()][z - min.z()]) {
            return true;
        }

        // Make sure the block data matches so different directional blocks don't merge
        final Block relative = block.getRelative(offsetX, offsetY, offsetZ);
        return relative.getType() == data.getMaterial() && relative.getBlockData().equals(data);
    }

    private static boolean[][][] computeVisibleBlocks(final World world, final BlockVector3 min, final BlockVector3 max) {
        final int sizeX = max.x() - min.x() + 1;
        final int sizeY = max.y() - min.y() + 1;
        final int sizeZ = max.z() - min.z() + 1;
        final int paddedX = sizeX + 2;
        final int paddedY = sizeY + 2;
        final int paddedZ = sizeZ + 2;
        final int layerSize = paddedX * paddedY;

        final boolean[] occluding = new boolean[paddedX * paddedY * paddedZ];
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    occluding[(x + 1) + (y + 1) * paddedX + (z + 1) * layerSize] =
                        world.getBlockAt(min.x() + x, min.y() + y, min.z() + z).getType().isOccluding();
                }
            }
        }

        final boolean[] reachable = new boolean[occluding.length];
        final int[] stack = new int[occluding.length];
        int stackSize = 0;
        reachable[0] = true;
        stack[stackSize++] = 0;
        while (stackSize > 0) {
            final int index = stack[--stackSize];
            final int x = index % paddedX;
            final int y = index / paddedX % paddedY;
            final int z = index / layerSize;
            if (x > 0) {
                stackSize = visit(index - 1, occluding, reachable, stack, stackSize);
            }
            if (x < paddedX - 1) {
                stackSize = visit(index + 1, occluding, reachable, stack, stackSize);
            }
            if (y > 0) {
                stackSize = visit(index - paddedX, occluding, reachable, stack, stackSize);
            }
            if (y < paddedY - 1) {
                stackSize = visit(index + paddedX, occluding, reachable, stack, stackSize);
            }
            if (z > 0) {
                stackSize = visit(index - layerSize, occluding, reachable, stack, stackSize);
            }
            if (z < paddedZ - 1) {
                stackSize = visit(index + layerSize, occluding, reachable, stack, stackSize);
            }
        }

        // Check if it's on the outside (given non-occluding blocks)
        final boolean[][][] visible = new boolean[sizeX][sizeY][sizeZ];
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    final int index = (x + 1) + (y + 1) * paddedX + (z + 1) * layerSize;
                    visible[x][y][z] = reachable[index]
                        || reachable[index - 1] || reachable[index + 1]
                        || reachable[index - paddedX] || reachable[index + paddedX]
                        || reachable[index - layerSize] || reachable[index + layerSize];
                }
            }
        }
        return visible;
    }

    private static int visit(final int index, final boolean[] occluding, final boolean[] reachable, final int[] stack, final int stackSize) {
        if (reachable[index] || occluding[index]) {
            return stackSize;
        }

        reachable[index] = true;
        stack[stackSize] = index;
        return stackSize + 1;
    }

    private void loadGroups() {
        this.entityGroups.clear();
        this.rotatingEntityGroups.clear();
        if (!this.groupsFile.exists()) {
            return;
        }

        final ConfigurationSection groups = YamlConfiguration.loadConfiguration(this.groupsFile).getConfigurationSection("groups");
        if (groups == null) {
            return;
        }

        for (final String name : groups.getKeys(false)) {
            final ConfigurationSection section = groups.getConfigurationSection(name);
            if (section == null) {
                continue;
            }

            final DisplayEntityGroup group = new DisplayEntityGroup(
                UUID.fromString(section.getString("world", "")),
                section.getDouble("center-x"),
                section.getDouble("center-y"),
                section.getDouble("center-z"),
                section.getStringList("entities").stream().map(UUID::fromString).collect(Collectors.toCollection(LinkedHashSet::new)),
                section.getDouble("radius"),
                (float) section.getDouble("speed")
            );
            this.entityGroups.put(name, group);
            if (group.shouldRotate()) {
                this.rotatingEntityGroups.put(name, new RotatingEntityGroup(group, this.rotationUpdateInterval));
            }
        }
    }

    private void saveGroups() {
        final YamlConfiguration config = new YamlConfiguration();
        for (final Map.Entry<String, DisplayEntityGroup> entry : this.entityGroups.entrySet()) {
            final DisplayEntityGroup group = entry.getValue();
            final ConfigurationSection section = config.createSection("groups." + entry.getKey());
            section.set("world", group.worldId().toString());
            section.set("center-x", group.centerX());
            section.set("center-y", group.centerY());
            section.set("center-z", group.centerZ());
            section.set("speed", group.speed());
            section.set("entities", group.entities().stream().map(UUID::toString).toList());
            section.set("radius", group.radius());
        }

        try {
            config.save(this.groupsFile);
        } catch (final IOException e) {
            this.getLogger().log(Level.SEVERE, "Failed to save groups.yml" + e);
        }
    }

    private record DisplayEntityGroup(
        UUID worldId, double centerX, double centerY, double centerZ,
        Set<UUID> entities, double radius, float speed
    ) {

        public DisplayEntityGroup withSpeed(final float speed) {
            return new DisplayEntityGroup(this.worldId, this.centerX, this.centerY, this.centerZ, this.entities, this.radius, speed);
        }

        public boolean shouldRotate() {
            return this.speed > 0;
        }
    }

    private static final class RotatingEntityGroup {
        private static final double MAX_STEP_RADIANS = Math.PI / 4;

        // Client lerping makes rotation go inward slightly, giving a bit of a bobbing effect.
        // Keep this effect invisible based on distance.
        private static final double DEVIATION_PER_BLOCK_DISTANCE = 0.001;

        private static final int STATE_CHECK_INTERVAL = 20;
        private static final double BASE_OBSERVER_DISTANCE = 64;

        private final DisplayEntityGroup group;
        private final int configuredUpdateInterval;
        private int updateInterval;
        private double radius;
        private double observerDistanceSquared = BASE_OBSERVER_DISTANCE * BASE_OBSERVER_DISTANCE;
        private List<TrackedDisplay> trackedDisplays;
        private double angle;
        private int ticksSinceUpdate;
        private int ticksSinceStateCheck = STATE_CHECK_INTERVAL;
        private boolean playerNearby;

        RotatingEntityGroup(final DisplayEntityGroup group, final int configuredUpdateInterval) {
            this.group = group;
            this.configuredUpdateInterval = configuredUpdateInterval;
            this.updateInterval = this.clampUpdateInterval(MAX_STEP_RADIANS);
            // Stagger groups so multiple rotations don't burst their packets on the same tick
            this.ticksSinceUpdate = Math.floorMod(group.hashCode(), this.updateInterval);
        }

        private int clampUpdateInterval(final double maxStepRadians) {
            return Math.clamp((int) (maxStepRadians / this.group.speed()), 1, this.configuredUpdateInterval);
        }

        public void tick() {
            this.ticksSinceStateCheck++;
            if (++this.ticksSinceUpdate < this.updateInterval) {
                return;
            }

            final World world = Bukkit.getWorld(this.group.worldId());
            if (world == null) {
                return;
            }

            if (this.ticksSinceStateCheck >= STATE_CHECK_INTERVAL) {
                // Check for nearby players every once in a while
                this.ticksSinceStateCheck = 0;
                if (this.trackedDisplays == null) {
                    this.resolveDisplays(world);
                }

                if (this.trackedDisplays == null) {
                    this.playerNearby = false;
                } else {
                    final double distanceSquared = this.nearestObserverDistanceSquared(world);
                    this.playerNearby = distanceSquared <= this.observerDistanceSquared;
                    if (this.playerNearby) {
                        this.updateInterval = this.updateIntervalFor(Math.sqrt(distanceSquared));
                    }
                }
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
            final Quaternionf rotation = new Quaternionf().rotationY((float) -this.angle);
            for (final TrackedDisplay tracked : this.trackedDisplays) {
                final BlockDisplay display = tracked.display();
                if (!display.isValid()) {
                    // Unloaded or removed; recapture the group once it is fully available again
                    this.trackedDisplays = null;
                    return;
                }

                // "Move" entities via interpolated transformations
                final double rotatedX = tracked.offsetX() * cos - tracked.offsetZ() * sin;
                final double rotatedZ = tracked.offsetX() * sin + tracked.offsetZ() * cos;
                final Vector3f translation = new Vector3f(
                    (float) (this.group.centerX() + rotatedX - tracked.posX()),
                    tracked.translationY(),
                    (float) (this.group.centerZ() + rotatedZ - tracked.posZ())
                );

                // Interpolate over one tick more than the update period to make transitions seamless (unless they have massive ping fluctuation)
                display.setInterpolationDuration(this.updateInterval + 1);
                display.setInterpolationDelay(0);
                display.setTransformation(new Transformation(
                    translation,
                    new Quaternionf(rotation).mul(tracked.initialRotation()),
                    tracked.scale(),
                    tracked.rightRotation()
                ));
            }
        }

        /**
         * Caches the group's display entities along with their base state,
         * so ticking doesn't need any UUID lookups.
         */
        private void resolveDisplays(final World world) {
            final List<TrackedDisplay> displays = new ArrayList<>(this.group.entities().size());
            double maxOffsetSquared = 0;
            for (final UUID uuid : this.group.entities()) {
                if (!(world.getEntity(uuid) instanceof final BlockDisplay display) || !display.isValid()) {
                    // Don't rotate while parts of the group are not loaded
                    return;
                }

                final Transformation transformation = display.getTransformation();
                final Vector3f translation = transformation.getTranslation();
                final double offsetX = display.getX() + translation.x() - this.group.centerX();
                final double offsetZ = display.getZ() + translation.z() - this.group.centerZ();
                maxOffsetSquared = Math.max(maxOffsetSquared, offsetX * offsetX + offsetZ * offsetZ);
                displays.add(new TrackedDisplay(
                    display,
                    offsetX,
                    offsetZ,
                    translation.y(),
                    display.getX(),
                    display.getZ(),
                    new Quaternionf(transformation.getLeftRotation()),
                    new Vector3f(transformation.getScale()),
                    new Quaternionf(transformation.getRightRotation())
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
                tracked.display().setDisplayWidth((float) (4 * this.radius) + 2 * Math.max(scale.x(), scale.z()));
                tracked.display().setDisplayHeight(tracked.translationY() < 0 ? 0 : scale.y() + tracked.translationY());
            }

            this.trackedDisplays = displays;
            this.angle = 0;
        }

        private int updateIntervalFor(final double observerDistanceToCenter) {
            // Pick the update interval so the deviation of the outermost blocks stays effectively invisible
            final double blockDistance = Math.max(1, observerDistanceToCenter - this.radius);
            final double allowedDeviation = blockDistance * DEVIATION_PER_BLOCK_DISTANCE;
            final double maxStep = this.radius <= allowedDeviation
                ? MAX_STEP_RADIANS
                : Math.min(MAX_STEP_RADIANS, Math.sqrt(8 * allowedDeviation / this.radius));
            return this.clampUpdateInterval(maxStep);
        }

        private double nearestObserverDistanceSquared(final World world) {
            double nearest = Double.MAX_VALUE;
            for (final Player player : world.getPlayers()) {
                final double distanceSquared = NumberConversions.square(player.getX() - this.group.centerX())
                    + NumberConversions.square(player.getY() - this.group.centerY())
                    + NumberConversions.square(player.getZ() - this.group.centerZ());
                nearest = Math.min(nearest, distanceSquared);
            }
            return nearest;
        }

        private record TrackedDisplay(
            BlockDisplay display,
            double offsetX, double offsetZ, float translationY,
            double posX, double posZ,
            Quaternionf initialRotation, Vector3f scale, Quaternionf rightRotation
        ) {
        }
    }
}

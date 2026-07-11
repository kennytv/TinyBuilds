package eu.kennytv.tinybuilds;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import eu.kennytv.tinybuilds.command.TinyBuildsCommand;
import eu.kennytv.tinybuilds.common.group.GroupManager;
import eu.kennytv.tinybuilds.common.group.GroupStorage;
import eu.kennytv.tinybuilds.common.platform.Platform;
import eu.kennytv.tinybuilds.common.platform.PlatformWorld;
import eu.kennytv.tinybuilds.listener.OrphanedDisplayListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class TinyBuildsPlugin extends JavaPlugin implements Platform {
    private static final Vector3f ZERO_VECTOR = new Vector3f(0, 0, 0);
    private static final AxisAngle4f ZERO_AXIS = new AxisAngle4f(0, 0, 0, 1);

    private final Set<Material> excludedFromStretching = EnumSet.noneOf(Material.class);
    private GroupManager manager;
    private GroupStorage storage;
    private NamespacedKey groupKey;
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

        this.displayViewRange = (float) this.getConfig().getDouble("display-view-range", 1.0);

        this.groupKey = new NamespacedKey(this, "group");
        this.storage = new YamlGroupStorage(this.getLogger(), new File(this.getDataFolder(), "groups.yml"));
        this.manager = new GroupManager(this);
        this.manager.load();

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            event.registrar().register(TinyBuildsCommand.create(this), "Manage block display groups"));

        if (this.getConfig().getBoolean("remove-orphaned-displays", true)) {
            this.getServer().getPluginManager().registerEvents(new OrphanedDisplayListener(this), this);
        }

        this.getServer().getScheduler().runTaskTimer(this, () -> this.manager.tick(), 1L, 1L);
    }

    public GroupManager manager() {
        return this.manager;
    }

    public NamespacedKey groupKey() {
        return this.groupKey;
    }

    @Override
    public PlatformWorld world(final Object worldKey) {
        final World world = this.getServer().getWorld((UUID) worldKey);
        return world != null ? new PaperWorld(this, world) : null;
    }

    @Override
    public GroupStorage storage() {
        return this.storage;
    }

    @Override
    public int rotationUpdateInterval() {
        return Math.max(1, this.getConfig().getInt("rotation-update-interval-ticks", 10));
    }

    @Override
    public long maxSelectionVolume() {
        return this.getConfig().getLong("max-selection-volume", 100_000L);
    }

    @Override
    public int maxDisplayEntities() {
        return this.getConfig().getInt("max-display-entities", 2000);
    }

    public void place(final Player player, final String groupName, final float scale, final int maxMergeSize) {
        final World world = player.getWorld();

        final LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        final Region selection;
        try {
            selection = session.getSelection(BukkitAdapter.adapt(world));
        } catch (final IncompleteRegionException e) {
            player.sendRichMessage("<red>Incomplete WorldEdit selection.");
            return;
        }

        this.manager.place(
            new PaperPlayer(player), groupName, scale, maxMergeSize,
            selection, world.getUID(),
            () -> new SelectionBlockGrid(world, selection.getMinimumPoint(), selection.getMaximumPoint(), this.excludedFromStretching),
            (x, y, z, blockKey, scaleVector) -> {
                final BlockDisplay display = world.spawn(new Location(world, x, y, z), BlockDisplay.class, spawned -> {
                    spawned.setBlock((BlockData) blockKey);
                    spawned.setTransformation(new Transformation(ZERO_VECTOR, ZERO_AXIS, scaleVector, ZERO_AXIS));
                    spawned.setInterpolationDelay(Integer.MAX_VALUE);
                    spawned.setViewRange(this.displayViewRange);
                    // A zero-sized culling box (the default) disables frustum culling
                    // entirely, so give the client one matching the scaled block extent
                    spawned.setDisplayWidth(2 * Math.max(scaleVector.x(), scaleVector.z()));
                    spawned.setDisplayHeight(scaleVector.y());
                    spawned.getPersistentDataContainer().set(this.groupKey, PersistentDataType.STRING, groupName);
                });
                return new PaperDisplay(display);
            }
        );
    }
}

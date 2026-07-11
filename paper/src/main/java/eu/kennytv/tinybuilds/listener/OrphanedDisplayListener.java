package eu.kennytv.tinybuilds.listener;

import eu.kennytv.tinybuilds.TinyBuildsPlugin;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

public final class OrphanedDisplayListener implements Listener {
    private final TinyBuildsPlugin plugin;

    public OrphanedDisplayListener(final TinyBuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(final EntitiesLoadEvent event) {
        for (final Entity entity : event.getEntities()) {
            if (!(entity instanceof final BlockDisplay display)) {
                continue;
            }

            final String groupName = display.getPersistentDataContainer().get(this.plugin.groupKey(), PersistentDataType.STRING);
            if (groupName != null && !this.plugin.manager().isKnownGroupEntity(groupName, display.getUniqueId())) {
                display.remove();
            }
        }
    }
}

package eu.kennytv.tinybuilds;

import eu.kennytv.tinybuilds.common.group.DisplayGroup;
import eu.kennytv.tinybuilds.common.group.GroupStorage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class YamlGroupStorage implements GroupStorage {
    private final Logger logger;
    private final File file;

    YamlGroupStorage(final Logger logger, final File file) {
        this.logger = logger;
        this.file = file;
    }

    @Override
    public Map<String, DisplayGroup> load() {
        final Map<String, DisplayGroup> result = new HashMap<>();
        if (!this.file.exists()) {
            return result;
        }

        final ConfigurationSection groups = YamlConfiguration.loadConfiguration(this.file).getConfigurationSection("groups");
        if (groups == null) {
            return result;
        }

        for (final String name : groups.getKeys(false)) {
            final ConfigurationSection section = groups.getConfigurationSection(name);
            if (section == null) {
                continue;
            }

            try {
                final UUID worldKey = UUID.fromString(section.getString("world", ""));
                result.put(name, new DisplayGroup(
                    worldKey,
                    section.getDouble("center-x"),
                    section.getDouble("center-y"),
                    section.getDouble("center-z"),
                    section.getStringList("entities").stream().map(UUID::fromString).collect(Collectors.toCollection(LinkedHashSet::new)),
                    section.getDouble("radius"),
                    (float) section.getDouble("speed")
                ));
            } catch (final IllegalArgumentException e) {
                this.logger.warning("Skipping invalid display entity group '" + name + "': " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public void save(final Map<String, DisplayGroup> groups) {
        final YamlConfiguration config = new YamlConfiguration();
        for (final Map.Entry<String, DisplayGroup> entry : groups.entrySet()) {
            final DisplayGroup group = entry.getValue();
            final ConfigurationSection section = config.createSection("groups." + entry.getKey());
            section.set("world", group.worldKey().toString());
            section.set("center-x", group.centerX());
            section.set("center-y", group.centerY());
            section.set("center-z", group.centerZ());
            section.set("speed", group.speed());
            section.set("entities", group.entities().stream().map(UUID::toString).toList());
            section.set("radius", group.radius());
        }

        try {
            config.save(this.file);
        } catch (final IOException e) {
            this.logger.log(Level.SEVERE, "Failed to save groups.yml", e);
        }
    }
}

package eu.kennytv.tinybuilds.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.kennytv.tinybuilds.common.group.DisplayGroup;
import eu.kennytv.tinybuilds.common.group.GroupStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JsonGroupStorage implements GroupStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("TinyBuilds");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    JsonGroupStorage(final Path file) {
        this.file = file;
    }

    @Override
    public Map<String, DisplayGroup> load() {
        final Map<String, DisplayGroup> result = new HashMap<>();
        if (!Files.exists(this.file)) {
            return result;
        }

        try {
            final JsonObject json = GSON.fromJson(Files.readString(this.file), JsonObject.class);
            final JsonObject groupsJson = json.getAsJsonObject("groups");
            for (final String name : groupsJson.keySet()) {
                final JsonObject groupJson = groupsJson.getAsJsonObject(name);
                final Set<UUID> entities = new LinkedHashSet<>();
                for (final JsonElement element : groupJson.getAsJsonArray("entities")) {
                    entities.add(UUID.fromString(element.getAsString()));
                }

                result.put(name, new DisplayGroup(
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(groupJson.get("world").getAsString())),
                    groupJson.get("center-x").getAsDouble(),
                    groupJson.get("center-y").getAsDouble(),
                    groupJson.get("center-z").getAsDouble(),
                    entities,
                    groupJson.get("radius").getAsDouble(),
                    groupJson.get("speed").getAsFloat()
                ));
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.error("Failed to load groups.json", e);
        }
        return result;
    }

    @Override
    public void save(final Map<String, DisplayGroup> groups) {
        final JsonObject groupsJson = new JsonObject();
        for (final Map.Entry<String, DisplayGroup> entry : groups.entrySet()) {
            final DisplayGroup group = entry.getValue();
            final JsonObject groupJson = new JsonObject();
            groupJson.addProperty("world", ((ResourceKey<?>) group.worldKey()).identifier().toString());
            groupJson.addProperty("center-x", group.centerX());
            groupJson.addProperty("center-y", group.centerY());
            groupJson.addProperty("center-z", group.centerZ());
            groupJson.addProperty("radius", group.radius());
            groupJson.addProperty("speed", group.speed());
            final JsonArray entities = new JsonArray();
            for (final UUID uuid : group.entities()) {
                entities.add(uuid.toString());
            }
            groupJson.add("entities", entities);
            groupsJson.add(entry.getKey(), groupJson);
        }

        final JsonObject json = new JsonObject();
        json.add("groups", groupsJson);
        try {
            Files.createDirectories(this.file.getParent());
            Files.writeString(this.file, GSON.toJson(json));
        } catch (final IOException e) {
            LOGGER.error("Failed to save groups.json", e);
        }
    }
}

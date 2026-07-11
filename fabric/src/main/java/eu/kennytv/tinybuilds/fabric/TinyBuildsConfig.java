package eu.kennytv.tinybuilds.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TinyBuildsConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("TinyBuilds");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    final Set<Block> excludedFromStretching;
    final int rotationUpdateIntervalTicks;
    final long maxSelectionVolume;
    final int maxDisplayEntities;
    final float displayViewRange;
    final boolean removeOrphanedDisplays;

    private TinyBuildsConfig(final JsonObject json) {
        this.excludedFromStretching = new LinkedHashSet<>();
        for (final JsonElement element : json.getAsJsonArray("excluded-from-stretching")) {
            final Identifier id = Identifier.tryParse(element.getAsString());
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                this.excludedFromStretching.add(BuiltInRegistries.BLOCK.getValue(id));
            } else {
                LOGGER.warn("Unknown block in excluded-from-stretching: {}", element.getAsString());
            }
        }
        this.rotationUpdateIntervalTicks = Math.max(1, json.get("rotation-update-interval-ticks").getAsInt());
        this.maxSelectionVolume = json.get("max-selection-volume").getAsLong();
        this.maxDisplayEntities = json.get("max-display-entities").getAsInt();
        this.displayViewRange = json.get("display-view-range").getAsFloat();
        this.removeOrphanedDisplays = json.get("remove-orphaned-displays").getAsBoolean();
    }

    static TinyBuildsConfig load(final Path path) {
        try {
            final JsonObject defaults = defaultConfig();
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(defaults));
                return new TinyBuildsConfig(defaults);
            }

            final JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);
            // Fill in any missing options with their defaults
            for (final String key : defaults.keySet()) {
                if (!json.has(key)) {
                    json.add(key, defaults.get(key));
                }
            }
            return new TinyBuildsConfig(json);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to load TinyBuilds config", e);
        }
    }

    private static JsonObject defaultConfig() {
        final JsonObject json = new JsonObject();
        final JsonArray excluded = new JsonArray();
        excluded.add("minecraft:oak_door");
        json.add("excluded-from-stretching", excluded);
        json.addProperty("rotation-update-interval-ticks", 10);
        json.addProperty("max-selection-volume", 500_000L);
        json.addProperty("max-display-entities", 10_000);
        json.addProperty("display-view-range", 1.0f);
        json.addProperty("remove-orphaned-displays", true);
        return json;
    }
}

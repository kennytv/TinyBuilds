package eu.kennytv.tinybuilds.fabric;

import eu.kennytv.tinybuilds.common.platform.PlatformPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public record FabricPlayer(ServerPlayer handle) implements PlatformPlayer {

    @Override
    public double x() {
        return this.handle.getX();
    }

    @Override
    public double y() {
        return this.handle.getY();
    }

    @Override
    public double z() {
        return this.handle.getZ();
    }

    @Override
    public void sendMessage(final String message) {
        // Strip MiniMessage formatting tags
        this.handle.sendSystemMessage(Component.literal(message.replaceAll("<[^>]*>", "")));
    }
}

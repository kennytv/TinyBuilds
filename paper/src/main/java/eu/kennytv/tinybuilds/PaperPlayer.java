package eu.kennytv.tinybuilds;

import eu.kennytv.tinybuilds.common.platform.PlatformPlayer;
import org.bukkit.entity.Player;

public record PaperPlayer(Player handle) implements PlatformPlayer {

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
        this.handle.sendRichMessage(message);
    }
}

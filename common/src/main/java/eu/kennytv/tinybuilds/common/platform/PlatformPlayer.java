package eu.kennytv.tinybuilds.common.platform;

public interface PlatformPlayer {

    double x();

    double y();

    double z();

    /**
     * Sends a MiniMessage-formatted message; platforms without rich message support
     * strip the tags.
     */
    void sendMessage(String message);
}

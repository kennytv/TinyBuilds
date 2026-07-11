package eu.kennytv.tinybuilds.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import eu.kennytv.tinybuilds.PaperPlayer;
import eu.kennytv.tinybuilds.TinyBuildsPlugin;
import eu.kennytv.tinybuilds.common.command.TinyBuildCommands;
import eu.kennytv.tinybuilds.common.group.GroupManager;
import eu.kennytv.tinybuilds.common.platform.PlatformPlayer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

public final class TinyBuildsCommand implements TinyBuildCommands.Handler<CommandSourceStack> {
    private final TinyBuildsPlugin plugin;

    private TinyBuildsCommand(final TinyBuildsPlugin plugin) {
        this.plugin = plugin;
    }

    public static LiteralCommandNode<CommandSourceStack> create(final TinyBuildsPlugin plugin) {
        return TinyBuildCommands.create(new TinyBuildsCommand(plugin));
    }

    @Override
    public boolean hasPermission(final CommandSourceStack source) {
        return source.getSender().hasPermission("tinybuilds.command");
    }

    @Override
    public PlatformPlayer player(final CommandSourceStack source) {
        return source.getExecutor() instanceof final Player player ? new PaperPlayer(player) : null;
    }

    @Override
    public void sendErrorMessage(final CommandSourceStack source, final String message) {
        source.getSender().sendRichMessage("<red>" + message);
    }

    @Override
    public void place(final PlatformPlayer player, final String groupName, final float scale, final int maxMergeSize) {
        this.plugin.place(((PaperPlayer) player).handle(), groupName, scale, maxMergeSize);
    }

    @Override
    public GroupManager manager() {
        return this.plugin.manager();
    }
}

package eu.kennytv.tinybuilds.fabric;

import com.mojang.brigadier.tree.LiteralCommandNode;
import eu.kennytv.tinybuilds.common.command.TinyBuildCommands;
import eu.kennytv.tinybuilds.common.group.GroupManager;
import eu.kennytv.tinybuilds.common.platform.PlatformPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;

final class TinyBuildsCommand implements TinyBuildCommands.Handler<CommandSourceStack> {
    private final TinyBuildsMod mod;

    private TinyBuildsCommand(final TinyBuildsMod mod) {
        this.mod = mod;
    }

    static LiteralCommandNode<CommandSourceStack> create(final TinyBuildsMod mod) {
        return TinyBuildCommands.create(new TinyBuildsCommand(mod));
    }

    @Override
    public boolean hasPermission(final CommandSourceStack source) {
        return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
    }

    @Override
    public PlatformPlayer player(final CommandSourceStack source) {
        return source.getEntity() instanceof final ServerPlayer player ? new FabricPlayer(player) : null;
    }

    @Override
    public void sendErrorMessage(final CommandSourceStack source, final String message) {
        source.sendFailure(Component.literal(message));
    }

    @Override
    public void place(final PlatformPlayer player, final String groupName, final float scale, final int maxMergeSize) {
        this.mod.place(((FabricPlayer) player).handle(), groupName, scale, maxMergeSize);
    }

    @Override
    public GroupManager manager() {
        return this.mod.manager();
    }
}

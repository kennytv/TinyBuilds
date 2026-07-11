package eu.kennytv.tinybuilds.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import eu.kennytv.tinybuilds.TinyBuildsPlugin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

public final class TinyBuildsCommand {
    private final TinyBuildsPlugin plugin;

    private TinyBuildsCommand(final TinyBuildsPlugin plugin) {
        this.plugin = plugin;
    }

    public static LiteralCommandNode<CommandSourceStack> create(final TinyBuildsPlugin plugin) {
        final TinyBuildsCommand command = new TinyBuildsCommand(plugin);
        return Commands.literal("tinybuilds")
            .requires(source -> source.getSender().hasPermission("tinybuilds.command"))
            .then(Commands.literal("place")
                .then(Commands.argument("group", StringArgumentType.word())
                    .then(Commands.argument("scale", FloatArgumentType.floatArg())
                        .executes(context -> command.place(context, 1))
                        .then(Commands.argument("maxMergeSize", IntegerArgumentType.integer(1))
                            .executes(context -> command.place(context, IntegerArgumentType.getInteger(context, "maxMergeSize")))))))
            .then(Commands.literal("remove")
                .then(Commands.argument("group", StringArgumentType.word())
                    .suggests(command::suggestGroups)
                    .executes(command::remove)))
            .then(Commands.literal("rotation")
                .then(Commands.argument("group", StringArgumentType.word())
                    .suggests(command::suggestGroups)
                    .then(Commands.argument("speed", FloatArgumentType.floatArg())
                        .executes(command::rotation))))
            .build();
    }

    private int place(final CommandContext<CommandSourceStack> context, final int maxMergeSize) {
        final Player player = requirePlayer(context);
        if (player == null) {
            return 0;
        }

        this.plugin.spawnBlockDisplays(
            player,
            StringArgumentType.getString(context, "group"),
            FloatArgumentType.getFloat(context, "scale"),
            maxMergeSize
        );
        return Command.SINGLE_SUCCESS;
    }

    private int remove(final CommandContext<CommandSourceStack> context) {
        final Player player = requirePlayer(context);
        if (player == null) {
            return 0;
        }

        this.plugin.removeDisplayGroup(player, StringArgumentType.getString(context, "group"));
        return Command.SINGLE_SUCCESS;
    }

    private int rotation(final CommandContext<CommandSourceStack> context) {
        final Player player = requirePlayer(context);
        if (player == null) {
            return 0;
        }

        this.plugin.setRotation(player, StringArgumentType.getString(context, "group"), FloatArgumentType.getFloat(context, "speed"));
        return Command.SINGLE_SUCCESS;
    }

    private static Player requirePlayer(final CommandContext<CommandSourceStack> context) {
        if (context.getSource().getExecutor() instanceof final Player player) {
            return player;
        }

        context.getSource().getSender().sendRichMessage("<red>This command can only be used by players.");
        return null;
    }

    private CompletableFuture<Suggestions> suggestGroups(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        final String input = builder.getRemainingLowerCase();
        for (final String name : this.plugin.groupNames()) {
            if (name.toLowerCase(Locale.ENGLISH).startsWith(input)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
}

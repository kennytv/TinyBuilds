package eu.kennytv.tinybuilds.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import eu.kennytv.tinybuilds.common.group.GroupManager;
import eu.kennytv.tinybuilds.common.platform.PlatformPlayer;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public final class TinyBuildCommands {

    private TinyBuildCommands() {
    }

    public interface Handler<S> {

        boolean hasPermission(S source);

        /**
         * Returns the player behind the command source, or null if it isn't a player.
         */
        @Nullable PlatformPlayer player(S source);

        void sendErrorMessage(S source, String message);

        void place(PlatformPlayer player, String groupName, float scale, int maxMergeSize);

        GroupManager manager();
    }

    public static <S> LiteralCommandNode<S> create(final Handler<S> handler) {
        return LiteralArgumentBuilder.<S>literal("tinybuilds")
            .requires(handler::hasPermission)
            .then(LiteralArgumentBuilder.<S>literal("place")
                .then(RequiredArgumentBuilder.<S, String>argument("group", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<S, Float>argument("scale", FloatArgumentType.floatArg())
                        .executes(context -> place(handler, context, 1))
                        .then(RequiredArgumentBuilder.<S, Integer>argument("maxMergeSize", IntegerArgumentType.integer(1))
                            .executes(context -> place(handler, context, IntegerArgumentType.getInteger(context, "maxMergeSize")))))))
            .then(LiteralArgumentBuilder.<S>literal("remove")
                .then(RequiredArgumentBuilder.<S, String>argument("group", StringArgumentType.word())
                    .suggests((context, builder) -> suggestGroups(handler, builder))
                    .executes(context -> {
                        final PlatformPlayer player = requirePlayer(handler, context);
                        if (player != null) {
                            handler.manager().removeGroup(player, StringArgumentType.getString(context, "group"));
                        }
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(LiteralArgumentBuilder.<S>literal("rotation")
                .then(RequiredArgumentBuilder.<S, String>argument("group", StringArgumentType.word())
                    .suggests((context, builder) -> suggestGroups(handler, builder))
                    .then(RequiredArgumentBuilder.<S, Float>argument("speed", FloatArgumentType.floatArg())
                        .executes(context -> {
                            final PlatformPlayer player = requirePlayer(handler, context);
                            if (player != null) {
                                handler.manager().setRotation(player, StringArgumentType.getString(context, "group"), FloatArgumentType.getFloat(context, "speed"));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))))
            .build();
    }

    private static <S> int place(final Handler<S> handler, final CommandContext<S> context, final int maxMergeSize) {
        final PlatformPlayer player = requirePlayer(handler, context);
        if (player != null) {
            handler.place(player, StringArgumentType.getString(context, "group"), FloatArgumentType.getFloat(context, "scale"), maxMergeSize);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> PlatformPlayer requirePlayer(final Handler<S> handler, final CommandContext<S> context) {
        final PlatformPlayer player = handler.player(context.getSource());
        if (player == null) {
            handler.sendErrorMessage(context.getSource(), "This command can only be used by players.");
        }
        return player;
    }

    private static CompletableFuture<Suggestions> suggestGroups(final Handler<?> handler, final SuggestionsBuilder builder) {
        final String input = builder.getRemainingLowerCase();
        for (final String name : handler.manager().groupNames()) {
            if (name.toLowerCase(Locale.ENGLISH).startsWith(input)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
}

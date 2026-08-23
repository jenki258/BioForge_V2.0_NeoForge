package net.jenkimods.bioforge.item.guide;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.jenkimods.bioforge.api.guide.ResearchJournalRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

public final class ResearchJournalCommand {
    private ResearchJournalCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bioforge")
                .then(Commands.literal("researchtablet")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("pages")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .then(pageArgument(true))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .then(pageArgument(false))))
                                .then(Commands.literal("add_all")
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(context -> changeAll(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "players"),
                                                        true))))
                                .then(Commands.literal("remove_all")
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(context -> changeAll(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "players"),
                                                        false)))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<
            CommandSourceStack, String> pageArgument(boolean add) {
        return Commands.argument("page", StringArgumentType.word())
                .suggests((context, builder) -> {
                    ResearchJournalRegistry.pages().forEach(page ->
                            builder.suggest(page.id().toString()));
                    return builder.buildFuture();
                })
                .executes(context -> changePage(
                        context.getSource(),
                        EntityArgument.getPlayers(context, "players"),
                        StringArgumentType.getString(context, "page"), add));
    }

    private static int changePage(CommandSourceStack source,
                                  Collection<ServerPlayer> players,
                                  String rawPage, boolean add) {
        ResourceLocation pageId = ResourceLocation.tryParse(rawPage);
        boolean exists = pageId != null && ResearchJournalRegistry.pages().stream()
                .anyMatch(page -> page.id().equals(pageId));
        if (!exists) {
            source.sendFailure(Component.translatable(
                    "command.bioforge.researchtablet.unknown_page", rawPage));
            return 0;
        }
        int changed = 0;
        for (ServerPlayer player : players) {
            changed += add
                    ? ResearchJournalProgress.unlockPages(player, List.of(pageId))
                    : ResearchJournalProgress.lockPages(player, List.of(pageId));
        }
        int result = changed;
        source.sendSuccess(() -> Component.translatable(
                add ? "command.bioforge.researchtablet.add"
                        : "command.bioforge.researchtablet.remove",
                pageId, players.size(), result), true);
        return changed;
    }

    private static int changeAll(CommandSourceStack source,
                                 Collection<ServerPlayer> players,
                                 boolean add) {
        int changed = 0;
        for (ServerPlayer player : players) {
            changed += add ? ResearchJournalProgress.unlockAll(player)
                    : ResearchJournalProgress.lockAll(player);
        }
        int result = changed;
        source.sendSuccess(() -> Component.translatable(
                add ? "command.bioforge.researchtablet.add_all"
                        : "command.bioforge.researchtablet.remove_all",
                players.size(), result), true);
        return changed;
    }
}

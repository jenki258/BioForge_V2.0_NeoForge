package net.jenkimods.bioforge.infection.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.naming.StrainNameNetworkHandler;
import net.jenkimods.bioforge.infection.naming.StrainNameStore;
import net.jenkimods.bioforge.infection.naming.StrainNamingManager;
import net.jenkimods.bioforge.vaccine.StrainFingerprint;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class StrainCommand {
    private static final int MAX_NAME_LENGTH = 48;

    private StrainCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bioforge")
                .then(Commands.literal("strain")
                        .then(Commands.literal("current")
                                .executes(StrainCommand::current))
                        .then(Commands.literal("list")
                                .requires(source -> source.hasPermission(2))
                                .executes(StrainCommand::list))
                        .then(Commands.literal("rename")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("fingerprint", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            StrainNameStore.get(context.getSource().getLevel())
                                                    .entries().forEach(entry ->
                                                            builder.suggest(entry.fingerprint()));
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("name",
                                                        StringArgumentType.greedyString())
                                                .executes(StrainCommand::rename))))));
    }

    private static int current(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.translatable(
                    "command.bioforge.strain.players_only"));
            return 0;
        }
        InfectionData infection = InfectionCapability.get(player);
        if (infection == null || !infection.isInfected()
                || infection.getPathogenId() == null) {
            context.getSource().sendFailure(Component.translatable(
                    "command.bioforge.strain.not_infected"));
            return 0;
        }
        String fingerprint = fingerprint(infection);
        String displayName = StrainNamingManager.displayName(
                player.serverLevel(), fingerprint);
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.bioforge.strain.current", displayName, fingerprint), false);
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> context) {
        String fingerprint = StringArgumentType.getString(context, "fingerprint")
                .toUpperCase(Locale.ROOT);
        String name = sanitizeName(StringArgumentType.getString(context, "name"));
        if (name == null) {
            context.getSource().sendFailure(Component.translatable(
                    "command.bioforge.strain.invalid_name", MAX_NAME_LENGTH));
            return 0;
        }
        StrainNameStore store = StrainNameStore.get(context.getSource().getLevel());
        if (store.isNameTaken(name, fingerprint)) {
            context.getSource().sendFailure(Component.translatable(
                    "command.bioforge.strain.duplicate_name", name));
            return 0;
        }
        if (!store.rename(fingerprint, name)) {
            context.getSource().sendFailure(Component.translatable(
                    "command.bioforge.strain.unknown", fingerprint));
            return 0;
        }
        StrainNameNetworkHandler.syncAll(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.bioforge.strain.renamed", fingerprint, name), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        StrainNameStore store = StrainNameStore.get(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.bioforge.strain.list_header", store.entries().size()), false);
        for (StrainNameStore.Entry entry : store.entries()) {
            String name = entry.isNamed() ? entry.name()
                    : Component.translatable("command.bioforge.strain.unnamed").getString();
            String researcher = entry.researcherName().isBlank() ? "-"
                    : entry.researcherName();
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.bioforge.strain.list_entry", entry.fingerprint(), name,
                    researcher), false);
        }
        return store.entries().size();
    }

    private static String fingerprint(InfectionData infection) {
        return StrainFingerprint.ofPayload(StrainData.buildFrom(infection).toPayload());
    }

    private static String sanitizeName(String input) {
        if (input == null) return null;
        String stripped = ChatFormatting.stripFormatting(input.replace('§', ' '));
        if (stripped == null) return null;
        String cleaned = stripped.replaceAll("[\\p{Cntrl}]", " ")
                .trim().replaceAll("\\s+", " ");
        return cleaned.isBlank() || cleaned.length() > MAX_NAME_LENGTH
                ? null : cleaned;
    }
}

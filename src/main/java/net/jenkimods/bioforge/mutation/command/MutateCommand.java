package net.jenkimods.bioforge.mutation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.mutation.MutationDefinition;
import net.jenkimods.bioforge.mutation.MutationLoader;
import net.jenkimods.bioforge.mutation.MutationManager;
import net.jenkimods.bioforge.mutation.network.MutationNetworkHandler;
import net.jenkimods.bioforge.mutation.network.MutationSlotPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class MutateCommand {
    private MutateCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var mutationId = Commands.argument("mutation_id", ResourceLocationArgument.id())
                .suggests((context, builder) -> suggestMutationIds(builder, false));
        var removableMutationId = Commands.argument("mutation_id", ResourceLocationArgument.id())
                .suggests((context, builder) -> suggestMutationIds(builder, true));

        dispatcher.register(Commands.literal("bioforge")
                .then(Commands.literal("mutate")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("info")
                                .then(mutationId.executes(context -> executeInfo(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "mutation_id").toString()))))
                        .then(Commands.literal("definitions")
                                .executes(context -> executeDefinitions(context.getSource(), null))
                                .then(Commands.argument("pathogen", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (PathogenType pathogen : PathogenType.values()) {
                                                builder.suggest(pathogen.name().toLowerCase(Locale.ROOT));
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> executeDefinitions(
                                                context.getSource(),
                                                parsePathogen(StringArgumentType.getString(context, "pathogen"))))))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.literal("apply")
                                        .then(Commands.argument("mutation_id", ResourceLocationArgument.id())
                                                .suggests((context, builder) -> suggestMutationIds(builder, false))
                                                .executes(context -> executeApply(
                                                        context,
                                                        EntityArgument.getEntities(context, "targets"),
                                                        ResourceLocationArgument.getId(context, "mutation_id").toString(),
                                                        false))
                                                .then(Commands.literal("force")
                                                        .executes(context -> executeApply(
                                                                context,
                                                                EntityArgument.getEntities(context, "targets"),
                                                                ResourceLocationArgument.getId(context, "mutation_id").toString(),
                                                                true)))))

                                .then(Commands.literal("add")
                                        .then(Commands.argument("mutation_id", ResourceLocationArgument.id())
                                                .suggests((context, builder) -> suggestMutationIds(builder, false))
                                                .executes(context -> executeApply(
                                                        context,
                                                        EntityArgument.getEntities(context, "targets"),
                                                        ResourceLocationArgument.getId(context, "mutation_id").toString(),
                                                        false))))
                                .then(Commands.literal("remove")
                                        .then(removableMutationId.executes(context -> executeRemove(
                                                context.getSource(),
                                                EntityArgument.getEntities(context, "targets"),
                                                ResourceLocationArgument.getId(context, "mutation_id").toString()))))
                                .then(Commands.literal("random")
                                        .executes(context -> executeRandom(
                                                context,
                                                EntityArgument.getEntities(context, "targets"),
                                                1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> executeRandom(
                                                        context,
                                                        EntityArgument.getEntities(context, "targets"),
                                                        IntegerArgumentType.getInteger(context, "count")))))
                                .then(Commands.literal("list")
                                        .executes(context -> executeList(
                                                context.getSource(),
                                                EntityArgument.getEntities(context, "targets"))))
                                .then(Commands.literal("clear")
                                        .executes(context -> executeClear(
                                                context.getSource(),
                                                EntityArgument.getEntities(context, "targets"))))
                                .then(Commands.literal("refresh")
                                        .executes(context -> executeRefresh(
                                                context.getSource(),
                                                EntityArgument.getEntities(context, "targets")))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestMutationIds(com.mojang.brigadier.suggestion.SuggestionsBuilder builder, boolean includeDisabled) {
        for (MutationDefinition definition : MutationLoader.INSTANCE.getAllMutations()) {

            if (!definition.hidden() && (includeDisabled || definition.enabled())) {
                builder.suggest(definition.id());
            }
        }
        return builder.buildFuture();
    }

    private static int executeApply(CommandContext<CommandSourceStack> context,
                                    Collection<? extends Entity> entities,
                                    String mutationId, boolean force) {
        MutationDefinition definition = MutationLoader.INSTANCE.getMutation(mutationId).orElse(null);
        if (definition == null) {
            context.getSource().sendFailure(Component.translatable(
                    "command.bioforge.mutate.unknown", mutationId));
            return 0;
        }

        int applied = 0;
        int failed = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living)) {
                failed++;
                continue;
            }
            InfectionData data = InfectionCapability.get(living);
            MutationManager.ApplyResult result =
                    MutationManager.applyMutation(definition, data, living, force);
            if (result == MutationManager.ApplyResult.APPLIED) {
                applied++;
            } else {
                failed++;
                sendApplyFailure(context.getSource(), living, definition, result);
            }
        }

        int finalApplied = applied;
        int finalFailed = failed;
        context.getSource().sendSuccess(() -> Component.translatable(
                finalFailed > 0 ? "command.bioforge.mutate.applied_skipped"
                        : "command.bioforge.mutate.applied",
                definition.id(), finalApplied, finalFailed), true);
        return applied;
    }

    private static void sendApplyFailure(CommandSourceStack source, LivingEntity target,
                                         MutationDefinition definition,
                                         MutationManager.ApplyResult result) {
        String reasonKey = switch (result) {
            case NOT_INFECTED -> "command.bioforge.mutate.failure.not_infected";
            case DISABLED -> "command.bioforge.mutate.failure.disabled";
            case ALREADY_PRESENT -> "command.bioforge.mutate.failure.already_present";
            case INCOMPATIBLE -> "command.bioforge.mutate.failure.incompatible";
            case MISSING_REQUIREMENT -> "command.bioforge.mutate.failure.missing_requirement";
            case CONFLICT -> "command.bioforge.mutate.failure.conflict";
            case INVALID_EFFECT -> "command.bioforge.mutate.failure.invalid_effect";
            case IMMUNE -> "command.bioforge.mutate.failure.immune";
            default -> "command.bioforge.mutate.failure.generic";
        };
        source.sendFailure(Component.translatable("command.bioforge.mutate.failure",
                target.getDisplayName(), definition.id(), Component.translatable(reasonKey)));
    }

    private static int executeRandom(CommandContext<CommandSourceStack> context,
                                     Collection<? extends Entity> entities, int countPerEntity) {
        int applied = 0;
        String fallbackMutationId = null;
        Map<ServerPlayer, String> playerAnimations = new LinkedHashMap<>();
        Random random = new Random();

        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living)) continue;
            InfectionData data = InfectionCapability.get(living);
            if (data == null || !data.isInfected()) continue;

            for (int index = 0; index < countPerEntity; index++) {
                String mutationId = MutationManager.getRandomMutationId(data, random);
                if (mutationId == null) break;
                MutationManager.ApplyResult result = MutationManager.applyMutation(data, living, mutationId);
                if (result == MutationManager.ApplyResult.APPLIED) {
                    applied++;
                    if (living instanceof ServerPlayer player) {
                        playerAnimations.put(player, mutationId);
                    } else {
                        fallbackMutationId = mutationId;
                    }
                }
            }
        }



        for (Map.Entry<ServerPlayer, String> animation : playerAnimations.entrySet()) {
            MutationNetworkHandler.sendToPlayer(
                    MutationSlotPacket.forMutation(animation.getValue()), animation.getKey());
        }
        if (fallbackMutationId != null
                && context.getSource().getEntity() instanceof ServerPlayer sourcePlayer
                && !playerAnimations.containsKey(sourcePlayer)) {
            MutationNetworkHandler.sendToPlayer(
                    MutationSlotPacket.forMutation(fallbackMutationId), sourcePlayer);
        }

        int finalApplied = applied;
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.bioforge.mutate.random", finalApplied), true);
        return applied;
    }

    private static int executeRemove(CommandSourceStack source,
                                     Collection<? extends Entity> entities,
                                     String mutationId) {
        String canonicalId = MutationLoader.INSTANCE.getMutation(mutationId)
                .map(MutationDefinition::id)
                .orElse(mutationId);
        int removed = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living)) continue;
            InfectionData data = InfectionCapability.get(living);
            if (MutationManager.removeMutation(data, living, canonicalId)) removed++;
        }
        int finalRemoved = removed;
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.mutate.removed", canonicalId, finalRemoved), true);
        return removed;
    }

    private static int executeList(CommandSourceStack source,
                                   Collection<? extends Entity> entities) {
        int mutationCount = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living)) continue;
            InfectionData data = InfectionCapability.get(living);
            if (data == null || !data.isInfected()) {
                source.sendFailure(Component.translatable(
                        "command.bioforge.mutate.target_not_infected", living.getDisplayName()));
                continue;
            }
            List<String> mutations = List.copyOf(data.getSymptoms().getMutations());
            mutationCount += mutations.size();
            source.sendSuccess(() -> Component.translatable(
                    mutations.isEmpty() ? "command.bioforge.mutate.list.empty"
                            : "command.bioforge.mutate.list.entry",
                    living.getDisplayName(), String.join(", ", mutations)), false);
        }
        return mutationCount;
    }

    private static int executeClear(CommandSourceStack source,
                                    Collection<? extends Entity> entities) {
        int entitiesChanged = 0;
        int mutationsRemoved = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living)) continue;
            InfectionData data = InfectionCapability.get(living);
            int removed = MutationManager.clearMutations(data, living);
            if (removed > 0) {
                entitiesChanged++;
                mutationsRemoved += removed;
            }
        }
        int finalEntitiesChanged = entitiesChanged;
        int finalMutationsRemoved = mutationsRemoved;
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.mutate.cleared",
                finalMutationsRemoved, finalEntitiesChanged), true);
        return mutationsRemoved;
    }

    private static int executeRefresh(CommandSourceStack source,
                                      Collection<? extends Entity> entities) {
        int refreshed = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living)) continue;
            InfectionData data = InfectionCapability.get(living);
            if (data == null || !data.isInfected() || data.getSymptoms().getMutations().isEmpty()) continue;
            MutationManager.refreshContinuousEffects(data, living);
            refreshed++;
        }
        int finalRefreshed = refreshed;
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.mutate.refreshed", finalRefreshed), true);
        return refreshed;
    }

    private static int executeInfo(CommandSourceStack source, String mutationId) {
        MutationDefinition definition = MutationLoader.INSTANCE.getMutation(mutationId).orElse(null);
        if (definition == null) {
            source.sendFailure(Component.translatable(
                    "command.bioforge.mutate.unknown", mutationId));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(definition.nameKey())
                .append(Component.literal(" [" + definition.id() + "]"))
                .withStyle(definition.enabled() ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.translatable(definition.descriptionKey()), false);
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.mutate.info.stats",
                definition.pathogens().toString(),
                Component.translatable("mutation.rarity." + definition.rarity()),
                definition.weight(),
                Component.translatable("gui.bioforge.vaccine_maker.correction."
                        + definition.enabled()),
                Component.translatable("gui.bioforge.vaccine_maker.correction."
                        + definition.hidden())), false);
        if (!definition.requiredMutations().isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.bioforge.mutate.info.requires",
                    String.join(", ", definition.requiredMutations())), false);
        }
        if (!definition.conflictingMutations().isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.bioforge.mutate.info.conflicts",
                    String.join(", ", definition.conflictingMutations())), false);
        }
        for (int index = 0; index < definition.effects().size(); index++) {
            MutationDefinition.Effect effect = definition.effects().get(index);
            int displayIndex = index + 1;
            source.sendSuccess(() -> Component.translatable(
                    "command.bioforge.mutate.info.effect",
                    displayIndex,
                    effect.trigger().name().toLowerCase(Locale.ROOT),
                    effect.type(),
                    effect.target().isEmpty() ? "-" : effect.target(),
                    effect.operation()), false);
        }
        for (MutationDefinition.Interaction interaction : definition.interactions()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.bioforge.mutate.info.interaction",
                    interaction.id(),
                    String.join(", ", interaction.withMutations()),
                    Component.translatable(interaction.requireAll()
                            ? "command.bioforge.mutate.info.all"
                            : "command.bioforge.mutate.info.any"),
                    interaction.grantMutations().isEmpty() ? "-"
                            : String.join(", ", interaction.grantMutations()),
                    interaction.removeMutations().isEmpty() ? "-"
                            : String.join(", ", interaction.removeMutations()),
                    interaction.effectModifiers().size(),
                    interaction.effects().size()), false);
        }
        return definition.effects().size() + definition.interactions().size();
    }

    private static int executeDefinitions(CommandSourceStack source, PathogenType pathogen) {
        List<MutationDefinition> definitions = (pathogen == null
                ? MutationLoader.INSTANCE.getAllMutations()
                : MutationLoader.INSTANCE.getMutationsForPathogen(pathogen)).stream()
                .filter(MutationDefinition::enabled)
                .filter(definition -> !definition.hidden())
                .toList();
        Component text = definitions.isEmpty()
                ? Component.translatable("command.bioforge.mutate.definitions.empty")
                : Component.translatable("command.bioforge.mutate.definitions",
                        definitions.stream().map(MutationDefinition::id)
                                .reduce((left, right) -> left + ", " + right).orElse(""));
        source.sendSuccess(() -> text, false);
        return definitions.size();
    }

    private static PathogenType parsePathogen(String name) {
        try {
            return PathogenType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

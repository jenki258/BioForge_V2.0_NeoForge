package net.jenkimods.bioforge.infection.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.infection.*;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.jenkimods.bioforge.mutation.MutationDefinition;
import net.jenkimods.bioforge.mutation.MutationLoader;
import net.jenkimods.bioforge.mutation.MutationManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;
import java.util.stream.Collectors;

public class InfectCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bioforge")
                .then(Commands.literal("infect")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("infected", BoolArgumentType.bool())
                                        .executes(ctx -> execute(ctx.getSource(),
                                                toLiving(EntityArgument.getEntities(ctx, "targets")),
                                                BoolArgumentType.getBool(ctx, "infected"),
                                                PathogenType.UNIVERSAL,
                                                Set.of(InfectionType.CONTACT_BASED),
                                                false,
                                                List.of()))
                                        .then(Commands.argument("pathogen", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (PathogenType pt : PathogenType.values())
                                                        builder.suggest(pt.name().toLowerCase());
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("persistent", BoolArgumentType.bool())
                                                        .executes(ctx -> execute(ctx.getSource(),
                                                                toLiving(EntityArgument.getEntities(ctx, "targets")),
                                                                BoolArgumentType.getBool(ctx, "infected"),
                                                                PathogenType.fromName(StringArgumentType.getString(ctx, "pathogen")),
                                                                Set.of(InfectionType.CONTACT_BASED),
                                                                BoolArgumentType.getBool(ctx, "persistent"),
                                                                List.of()))
                                                        .then(Commands.argument("infectionType", StringArgumentType.string())
                                                                .suggests((ctx, builder) -> {
                                                                    PathogenType pt = PathogenType.fromName(
                                                                            StringArgumentType.getString(ctx, "pathogen"));
                                                                    for (InfectionType it : pt.getAllowedTransmissions()) {
                                                                        if (BioForgeServerConfig.isTransmissionEnabled(it)) {
                                                                            builder.suggest(it.name().toLowerCase());
                                                                        }
                                                                    }
                                                                    return builder.buildFuture();
                                                                })
                                                                .executes(ctx -> execute(ctx.getSource(),
                                                                        toLiving(EntityArgument.getEntities(ctx, "targets")),
                                                                        BoolArgumentType.getBool(ctx, "infected"),
                                                                        PathogenType.fromName(StringArgumentType.getString(ctx, "pathogen")),
                                                                        parseTypes(StringArgumentType.getString(ctx, "infectionType")),
                                                                        BoolArgumentType.getBool(ctx, "persistent"),
                                                                        List.of()))
                                                                .then(Commands.literal("symptoms")
                                                                        .then(Commands.argument("pairs", StringArgumentType.greedyString())
                                                                                .executes(ctx -> executeWithSymptoms(
                                                                                        ctx.getSource(),
                                                                                        toLiving(EntityArgument.getEntities(ctx, "targets")),
                                                                                        BoolArgumentType.getBool(ctx, "infected"),
                                                                                        PathogenType.fromName(StringArgumentType.getString(ctx, "pathogen")),
                                                                                        parseTypes(StringArgumentType.getString(ctx, "infectionType")),
                                                                                        BoolArgumentType.getBool(ctx, "persistent"),
                                                                                        StringArgumentType.getString(ctx, "pairs"),
                                                                                        List.of())))
                                                                )
                                                                .then(Commands.literal("mutations")
                                                                        .then(Commands.argument("mutation_ids", StringArgumentType.greedyString())
                                                                                .suggests((ctx, builder) -> {
                                                                                    for (MutationDefinition def : MutationLoader.INSTANCE.getAllMutations()) {
                                                                                        if (def.enabled()) builder.suggest(def.id());
                                                                                    }
                                                                                    return builder.buildFuture();
                                                                                })
                                                                                .executes(ctx -> executeWithSymptoms(
                                                                                        ctx.getSource(),
                                                                                        toLiving(EntityArgument.getEntities(ctx, "targets")),
                                                                                        BoolArgumentType.getBool(ctx, "infected"),
                                                                                        PathogenType.fromName(StringArgumentType.getString(ctx, "pathogen")),
                                                                                        parseTypes(StringArgumentType.getString(ctx, "infectionType")),
                                                                                        BoolArgumentType.getBool(ctx, "persistent"),
                                                                                        "",
                                                                                        parseMutationIds(StringArgumentType.getString(ctx, "mutation_ids"))))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("cure")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> cure(ctx.getSource(),
                                        toLiving(EntityArgument.getEntities(ctx, "targets")))))
                )
        );
    }

    private static Collection<LivingEntity> toLiving(Collection<? extends Entity> entities) {
        return entities.stream()
                .filter(e -> e instanceof LivingEntity)
                .map(e -> (LivingEntity) e)
                .collect(Collectors.toList());
    }

    private static Set<InfectionType> parseTypes(String raw) {
        Set<InfectionType> set = EnumSet.noneOf(InfectionType.class);
        for (String part : raw.split(",")) {
            part = part.trim();
            if (!part.isEmpty()) {
                InfectionType type = InfectionType.fromName(part);
                if (BioForgeServerConfig.isTransmissionEnabled(type)) set.add(type);
            }
        }
        return set;
    }

    private static List<String> parseMutationIds(String raw) {
        List<String> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            part = part.trim();
            if (!part.isEmpty()) ids.add(part);
        }
        return ids;
    }

    private static Map<String, String> parseSymptomPairs(String greedy) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String token : greedy.split("\\s+")) {
            String[] kv = token.split("=", 2);
            if (kv.length == 2) map.put(kv[0], kv[1]);
        }
        return map;
    }

    private static int execute(CommandSourceStack source, Collection<LivingEntity> targets,
                               boolean infected, PathogenType pathogenType, Set<InfectionType> types,
                               boolean persistent, List<String> mutationIds) {
        for (InfectionType t : types) {
            if (!BioForgeServerConfig.isTransmissionEnabled(t) || !pathogenType.allows(t)) {
                source.sendFailure(Component.translatable("command.bioforge.infect.incompatible",
                        pathogenType.name(), t.name()));
                return 0;
            }
        }
        for (LivingEntity entity : targets) {
            if (infected && InfectionInvulnerability.isEnabled(entity)) {
                if (entity instanceof ServerPlayer player) InfectionInvulnerability.ensureCured(player);
                source.sendFailure(Component.translatable("command.bioforge.infectionimmune.blocked",
                        entity.getDisplayName()));
                continue;
            }
            InfectionData data = InfectionCapability.get(entity);
            if (data == null) continue;
            MutationManager.clearMutations(data, entity);
            data.clearInfection();
            if (entity instanceof ServerPlayer player) {
                InfectionStore.get(player.serverLevel()).clearInfection(player.getUUID());
            }
            if (infected) {
                data.setInfected(true);
                data.setPathogenType(pathogenType);
                for (InfectionType t : types) data.addInfectionType(t);
                BioForgeSymptoms.applyDefaultSymptoms(data);


                if (mutationIds != null && !mutationIds.isEmpty()) {
                    for (String id : mutationIds) {
                        MutationManager.applyMutation(data, entity, id);
                    }
                }
                if (persistent && entity instanceof ServerPlayer player) {
                    Map<String, Object> symptomMap = new LinkedHashMap<>();
                    for (Map.Entry<String, SymptomKey<?>> e : BioForgeSymptoms.getEnabledSymptomKeys().entrySet()) {
                        symptomMap.put(e.getKey(), data.getSymptom(e.getValue()));
                    }
                    List<String> muts = new ArrayList<>(data.getSymptoms().getMutations());
                    InfectionStore.get(player.serverLevel()).setInfection(player.getUUID(),
                            new InfectionStore.InfectionRecord(true, true, pathogenType, new ArrayList<>(types),
                                    symptomMap, muts));
                }
                final String name = entity.getDisplayName().getString();
                source.sendSuccess(() -> Component.translatable("command.bioforge.infect.success", name,
                        pathogenType.name(), types.toString()), true);
            } else {
                final String name = entity.getDisplayName().getString();
                source.sendSuccess(() -> Component.translatable("command.bioforge.infect.cleared", name), true);
            }
            if (entity instanceof ServerPlayer player) {
                InfectionEventHandler.syncToClient(player, data);
            }
        }
        return targets.size();
    }

    private static int executeWithSymptoms(CommandSourceStack source, Collection<LivingEntity> targets,
                                           boolean infected, PathogenType pathogenType, Set<InfectionType> types,
                                           boolean persistent, String symptomsString, List<String> mutationIds) {
        for (InfectionType t : types) {
            if (!BioForgeServerConfig.isTransmissionEnabled(t) || !pathogenType.allows(t)) {
                source.sendFailure(Component.translatable("command.bioforge.infect.incompatible",
                        pathogenType.name(), t.name()));
                return 0;
            }
        }
        Map<String, String> symptomPairs = parseSymptomPairs(symptomsString);
        for (LivingEntity entity : targets) {
            if (infected && InfectionInvulnerability.isEnabled(entity)) {
                if (entity instanceof ServerPlayer player) InfectionInvulnerability.ensureCured(player);
                source.sendFailure(Component.translatable("command.bioforge.infectionimmune.blocked",
                        entity.getDisplayName()));
                continue;
            }
            InfectionData data = InfectionCapability.get(entity);
            if (data == null) continue;
            MutationManager.clearMutations(data, entity);
            data.clearInfection();
            if (entity instanceof ServerPlayer player) {
                InfectionStore.get(player.serverLevel()).clearInfection(player.getUUID());
            }
            if (infected) {
                data.setInfected(true);
                data.setPathogenType(pathogenType);
                for (InfectionType t : types) data.addInfectionType(t);

                for (Map.Entry<String, String> pair : symptomPairs.entrySet()) {
                    SymptomKey<?> key = BioForgeSymptoms.getEnabledSymptomKeys().get(pair.getKey());
                    if (key != null) {
                        Object value = parseSymptomValue(pair.getValue(), key);
                        if (value != null) {
                            data.getSymptoms().set((SymptomKey) key, value);
                        }
                    }
                }


                if (mutationIds != null && !mutationIds.isEmpty()) {
                    for (String id : mutationIds) {
                        MutationManager.applyMutation(data, entity, id);
                    }
                }
                if (persistent && entity instanceof ServerPlayer player) {
                    Map<String, Object> symptomMap = new LinkedHashMap<>();
                    for (Map.Entry<String, SymptomKey<?>> e : BioForgeSymptoms.getEnabledSymptomKeys().entrySet()) {
                        symptomMap.put(e.getKey(), data.getSymptom(e.getValue()));
                    }
                    List<String> muts = new ArrayList<>(data.getSymptoms().getMutations());
                    InfectionStore.get(player.serverLevel()).setInfection(player.getUUID(),
                            new InfectionStore.InfectionRecord(true, true, pathogenType, new ArrayList<>(types),
                                    symptomMap, muts));
                }
                final String name = entity.getDisplayName().getString();
                source.sendSuccess(() -> Component.translatable("command.bioforge.infect.success", name,
                        pathogenType.name(), types.toString()), true);
            } else {
                final String name = entity.getDisplayName().getString();
                source.sendSuccess(() -> Component.translatable("command.bioforge.infect.cleared", name), true);
            }
            if (entity instanceof ServerPlayer player) {
                InfectionEventHandler.syncToClient(player, data);
            }
        }
        return targets.size();
    }

    private static Object parseSymptomValue(String string, SymptomKey<?> key) {
        Class<?> type = key.getType();
        try {
            if (type.isEnum()) {
                return Enum.valueOf((Class<Enum>) type, string);
            } else if (type == Boolean.class) {
                return Boolean.valueOf(string);
            } else if (type == Float.class) {
                return Float.valueOf(string);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static int cure(CommandSourceStack source, Collection<LivingEntity> targets) {
        for (LivingEntity entity : targets) {
            InfectionData data = InfectionCapability.get(entity);
            if (data == null) continue;
            MutationManager.clearMutations(data, entity);
            data.clearInfection();
            if (entity instanceof ServerPlayer player) {
                InfectionStore.get(player.serverLevel()).clearInfection(player.getUUID());
            }
            final String name = entity.getDisplayName().getString();
            source.sendSuccess(() -> Component.translatable("command.bioforge.cure.success", name), true);
            if (entity instanceof ServerPlayer player) {
                InfectionEventHandler.syncToClient(player, data);
            }
        }
        return targets.size();
    }
}
